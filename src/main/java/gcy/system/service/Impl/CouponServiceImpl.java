package gcy.system.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import gcy.system.entity.dto.Result;
import gcy.system.entity.pojo.Coupon;
import gcy.system.entity.pojo.User;
import gcy.system.entity.pojo.UserCoupon;
import gcy.system.entity.vo.CouponItemVO;
import gcy.system.entity.vo.UserCouponVO;
import gcy.system.mapper.CouponMapper;
import gcy.system.mapper.UserCouponMapper;
import gcy.system.mapper.UserMapper;
import gcy.system.service.ICouponService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static gcy.system.utils.RedisConstants.COUPON_COUNT_KEY;
import static gcy.system.utils.RedisConstants.COUPON_USER_COUNT_KEY;

/**
 * 用户端优惠券服务实现。
 * <p>
 * 领取采用 Redis Lua 原子扣减：在单条脚本内完成「查已领数量 → 校验上限/总量 → 自增」，
 * 避免并发超发；写库失败时回滚 Redis 计数作为兜底。
 * </p>
 *
 * @author 郭名城
 * @date 2026-09-20
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponServiceImpl implements ICouponService {

    private final CouponMapper couponMapper;

    private final UserCouponMapper userCouponMapper;

    private final UserMapper userMapper;

    private final StringRedisTemplate stringRedisTemplate;

    private static final DateTimeFormatter MM_DD = DateTimeFormatter.ofPattern("MM-dd");

    /**
     * 领取 Lua 脚本：返回 1 成功，-1 已领完，-2 已达用户限制。
     */
    private static final String CLAIM_LUA =
            "local count = tonumber(redis.call('GET', KEYS[1]) or '0');" +
                    "local total = tonumber(ARGV[1]);" +
                    "if total > 0 and count >= total then return -1 end;" +
                    "local uc = tonumber(redis.call('GET', KEYS[2]) or '0');" +
                    "local limit = tonumber(ARGV[2]);" +
                    "if uc >= limit then return -2 end;" +
                    "redis.call('SET', KEYS[1], count + 1);" +
                    "redis.call('SET', KEYS[2], uc + 1);" +
                    "return 1;";

    /**
     * 领取 Lua 脚本对象（静态块初始化，与项目内 GET_AND_DEL_SCRIPT 同惯例）
     */
    private static final DefaultRedisScript<Long> CLAIM_SCRIPT;

    static {
        CLAIM_SCRIPT = new DefaultRedisScript<>(CLAIM_LUA, Long.class);
    }

    // ==================== 可领券列表 ====================

    @Override
    public Result getClaimableList(Long userId) {
        List<Coupon> coupons = couponMapper.selectList(new LambdaQueryWrapper<Coupon>()
                .eq(Coupon::getStatus, 1)
                .orderByAsc(Coupon::getClaimStart)
                .orderByDesc(Coupon::getId));

        int regDays = regDaysOf(userId);
        // 用户已领各券数量（couponId -> 已领数），一次批量查询避免 N+1
        Map<Long, Long> userClaimed = coupons.isEmpty() ? Map.of()
                : userCouponMapper.selectList(new LambdaQueryWrapper<UserCoupon>()
                        .eq(UserCoupon::getUserId, userId)
                        .in(UserCoupon::getCouponId,
                                coupons.stream().map(Coupon::getId).collect(Collectors.toList())))
                .stream()
                .collect(Collectors.groupingBy(UserCoupon::getCouponId, Collectors.counting()));

        // 全局已领数量：一次 multiGet + 一次 GROUP BY 回源，替代此前每券 2~3 次 Redis GET
        // （30 张券原本要 60~90 次往返，Redis 被清空时更退化成 30 次 COUNT(*)）
        Map<Long, Long> claimed = batchClaimedCount(coupons);

        List<CouponItemVO> result = coupons.stream()
                .map(c -> toClaimableVO(c, userClaimed.getOrDefault(c.getId(), 0L), regDays,
                        claimed.getOrDefault(c.getId(), 0L)))
                .filter(Objects::nonNull) // 过滤掉不满足人群的券
                .collect(Collectors.toList());

        return Result.ok(result);
    }

    private CouponItemVO toClaimableVO(Coupon c, Long userClaimedCount, int regDays, long claimedCount) {
        int state = resolveState(c, userClaimedCount, regDays, claimedCount);
        if (state == 4) {
            return null; // 不满足人群，隐藏
        }
        CouponItemVO vo = new CouponItemVO();
        vo.setId(c.getId());
        vo.setName(c.getName());
        vo.setType(c.getType());
        vo.setTypeText(typeText(c.getType()));
        vo.setAmountText(amountText(c));
        vo.setMinThreshold(c.getMinThreshold());
        vo.setScopeText(scopeText(c));
        vo.setValidText(validText(c));
        vo.setPerUserLimit(c.getPerUserLimit());
        vo.setClaimedCount((int) claimedCount);
        vo.setState(state);
        vo.setStateText(stateText(state, c));
        return vo;
    }

    /**
     * 状态判定。4=人群不满足(隐藏)，0=可领，1=未开始，2=已结束/已领完，3=已领取。
     */
    private int resolveState(Coupon c, long userClaimedCount, int regDays, long claimedCount) {
        if (!matchTarget(c, regDays)) {
            return 4;
        }
        // totalCount <= 0 视为「不限量」，与领取 Lua 脚本的 total > 0 判定保持一致。
        // 此前这里写成 claimedCount >= totalCount，totalCount=0 时会误判为「已领完」，
        // 与 Lua 侧「不限量可继续领」的语义自相矛盾。
        long totalCount = c.getTotalCount() == null ? 0L : c.getTotalCount().longValue();
        if (totalCount > 0 && claimedCount >= totalCount) {
            return 2; // 已领完
        }
        if (userClaimedCount >= c.getPerUserLimit()) {
            return 3;
        }
        LocalDateTime now = LocalDateTime.now();
        if (c.getClaimStart() != null && now.isBefore(c.getClaimStart())) {
            return 1;
        }
        if (c.getClaimEnd() != null && now.isAfter(c.getClaimEnd())) {
            return 2;
        }
        return 0;
    }

    private boolean matchTarget(Coupon c, int regDays) {
        if (c.getTargetType() == null || c.getTargetType() == 0) {
            return true;
        }
        int targetDays = c.getTargetDays() == null ? 0 : c.getTargetDays();
        if (c.getTargetType() == 1) {
            return regDays <= targetDays;       // 新用户
        }
        return regDays > targetDays;             // 老用户
    }

    // ==================== 我的券 ====================

    @Override
    public Result getMyCoupons(Long userId, Integer status) {
        LambdaQueryWrapper<UserCoupon> wrapper = new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getUserId, userId)
                .orderByDesc(UserCoupon::getGotTime);
        if (status != null) {
            wrapper.eq(UserCoupon::getStatus, status);
        }
        List<UserCoupon> list = userCouponMapper.selectList(wrapper);
        if (list.isEmpty()) {
            return Result.ok(list);
        }
        List<Long> couponIds = list.stream().map(UserCoupon::getCouponId).distinct().collect(Collectors.toList());
        Map<Long, Coupon> couponMap = couponMapper.selectBatchIds(couponIds).stream()
                .collect(Collectors.toMap(Coupon::getId, c -> c));

        LocalDateTime now = LocalDateTime.now();
        // 过期纠正：先收集 id，一条 IN(...) 批量 UPDATE，避免读接口里逐条写库
        // （原实现在 stream 内对每张过期券发一条 UPDATE，N 张过期券 = N 次往返，且方法无事务）
        Set<Long> expiredIds = list.stream()
                .filter(uc -> uc.getStatus() == 0 && uc.getExpireTime() != null && uc.getExpireTime().isBefore(now))
                .map(UserCoupon::getId)
                .collect(Collectors.toSet());
        if (!expiredIds.isEmpty()) {
            userCouponMapper.update(null, new LambdaUpdateWrapper<UserCoupon>()
                    .in(UserCoupon::getId, expiredIds)
                    .set(UserCoupon::getStatus, 2));
        }

        List<UserCouponVO> vos = list.stream()
                .map(uc -> {
                    // 同步内存状态，保证本次返回给前端的就是纠正后的状态
                    if (expiredIds.contains(uc.getId())) {
                        uc.setStatus(2);
                    }
                    Coupon c = couponMap.get(uc.getCouponId());
                    UserCouponVO vo = new UserCouponVO();
                    vo.setUserCouponId(uc.getId());
                    vo.setCouponId(uc.getCouponId());
                    vo.setName(c != null ? c.getName() : "已下架");
                    vo.setType(c != null ? c.getType() : null);
                    vo.setAmountText(c != null ? amountText(c) : "");
                    vo.setScopeText(c != null ? scopeText(c) : "全场");
                    vo.setScope(c != null ? c.getScope() : 0);
                    vo.setTypeId(c != null ? c.getTypeId() : null);
                    vo.setMinThreshold(c != null ? c.getMinThreshold() : BigDecimal.ZERO);
                    vo.setAmount(c != null ? c.getAmount() : null);
                    vo.setDiscount(c != null ? c.getDiscount() : null);
                    vo.setCapAmount(c != null ? c.getCapAmount() : null);
                    vo.setStackable(c != null && c.getStackable() != null ? c.getStackable() : 0);
                    vo.setStatus(uc.getStatus());
                    vo.setStatusText(statusText(uc.getStatus()));
                    vo.setExpireTime(uc.getExpireTime());
                    vo.setGotTime(uc.getGotTime());
                    vo.setUseTime(uc.getUseTime());
                    return vo;
                })
                .collect(Collectors.toList());
        return Result.ok(vos);
    }

    // ==================== 领取 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result claim(Long userId, Long couponId) {
        Coupon c = couponMapper.selectById(couponId);
        if (c == null || c.getDeleted() != null && c.getDeleted() == 1) {
            return Result.fail("优惠券不存在");
        }
        if (c.getStatus() != null && c.getStatus() == 0) {
            return Result.fail("优惠券已停用");
        }
        // 领取时间窗
        LocalDateTime now = LocalDateTime.now();
        if (c.getClaimStart() != null && now.isBefore(c.getClaimStart())) {
            return Result.fail("不在领取时间范围内");
        }
        if (c.getClaimEnd() != null && now.isAfter(c.getClaimEnd())) {
            return Result.fail("领取已结束");
        }
        // 人群
        if (!matchTarget(c, regDaysOf(userId))) {
            return Result.fail(targetText(c));
        }

        // Redis Lua 原子扣减
        String countKey = COUPON_COUNT_KEY + couponId;
        String userKey = COUPON_USER_COUNT_KEY + couponId + ":" + userId;
        // Redis 计数缺失时回种 DB 已领数，避免 Redis 被清空后超发
        if (stringRedisTemplate.opsForValue().get(countKey) == null) {
            long dbCount = userCouponMapper.selectCount(new LambdaQueryWrapper<UserCoupon>()
                    .eq(UserCoupon::getCouponId, couponId));
            stringRedisTemplate.opsForValue().setIfAbsent(countKey, String.valueOf(dbCount));
        }
        // 用户限领计数同样需要回种：只回种 countKey 的话，Redis 重启后
        // 每人限领计数从 0 重新开始，「每人限领 N 张」会退化成无限领。
        if (stringRedisTemplate.opsForValue().get(userKey) == null) {
            long dbUserCount = userCouponMapper.selectCount(new LambdaQueryWrapper<UserCoupon>()
                    .eq(UserCoupon::getCouponId, couponId)
                    .eq(UserCoupon::getUserId, userId));
            stringRedisTemplate.opsForValue().setIfAbsent(userKey, String.valueOf(dbUserCount));
        }
        Long total = c.getTotalCount() == null ? 0L : c.getTotalCount().longValue();
        Long limit = c.getPerUserLimit() == null ? 1L : c.getPerUserLimit().longValue();
        Long res = stringRedisTemplate.execute(CLAIM_SCRIPT, List.of(countKey, userKey),
                String.valueOf(total), String.valueOf(limit));
        if (res == null || res != 1L) {
            return Result.fail(res == null || res == -1L ? "已被领取完" : "已达每人限领次数");
        }

        // 写库（领取记录）
        UserCoupon uc = new UserCoupon();
        uc.setUserId(userId);
        uc.setCouponId(couponId);
        uc.setStatus(0);
        uc.setGotTime(now);
        uc.setExpireTime(resolveExpireTime(c, now));
        try {
            userCouponMapper.insert(uc);
        } catch (Exception e) {
            // 写表失败 → 回滚 Redis 计数兜底，避免计数虚高
            log.error("领券写库失败，回滚Redis计数: couponId={}, userId={}", couponId, userId, e);
            stringRedisTemplate.opsForValue().decrement(countKey);
            stringRedisTemplate.opsForValue().decrement(userKey);
            return Result.fail("领取失败，请稍后重试");
        }
        return Result.okMsg("领取成功");
    }

    private LocalDateTime resolveExpireTime(Coupon c, LocalDateTime now) {
        if (c.getValidType() != null && c.getValidType() == 2 && c.getValidDays() != null) {
            return now.plusDays(c.getValidDays());
        }
        return c.getValidEnd();
    }

    // ==================== 工具方法 ====================

    private long getClaimedCount(Long couponId) {
        String v = stringRedisTemplate.opsForValue().get(COUPON_COUNT_KEY + couponId);
        if (v != null) {
            return Long.parseLong(v);
        }
        // Redis 缺失时回退到 DB 计数并种seed进 Redis
        long count = userCouponMapper.selectCount(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getCouponId, couponId));
        stringRedisTemplate.opsForValue().setIfAbsent(COUPON_COUNT_KEY + couponId, String.valueOf(count));
        return count;
    }

    /**
     * 批量取「各券已领数量」：一次 Redis multiGet 拿齐，缺失的用一条 GROUP BY 回源后回种。
     * <p>
     * 替代此前每券单独 GET（列表页每张券还要调 2~3 次），30 张券从 60~90 次往返降到 1~2 次。
     * </p>
     */
    private Map<Long, Long> batchClaimedCount(List<Coupon> coupons) {
        Map<Long, Long> result = new HashMap<>();
        if (coupons == null || coupons.isEmpty()) {
            return result;
        }
        List<Long> ids = coupons.stream().map(Coupon::getId).distinct().collect(Collectors.toList());
        List<String> keys = ids.stream().map(id -> COUPON_COUNT_KEY + id).collect(Collectors.toList());

        List<String> values = null;
        try {
            values = stringRedisTemplate.opsForValue().multiGet(keys);
        } catch (Exception e) {
            log.warn("优惠券已领计数批量读取失败，回退逐条读取: {}", e.getMessage());
        }

        List<Long> missed = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            String v = values == null ? null : values.get(i);
            if (v != null) {
                try {
                    result.put(ids.get(i), Long.parseLong(v));
                    continue;
                } catch (NumberFormatException ignore) {
                    // 脏值当缺失处理
                }
            }
            missed.add(ids.get(i));
        }
        if (missed.isEmpty()) {
            return result;
        }

        // 单次 GROUP BY 回源，替代 N 次 COUNT(*)
        Map<Long, Long> fromDb = new HashMap<>();
        try {
            QueryWrapper<UserCoupon> qw = new QueryWrapper<UserCoupon>()
                    .select("coupon_id", "COUNT(*) AS cnt")
                    .in("coupon_id", missed)
                    .groupBy("coupon_id");
            for (Map<String, Object> row : userCouponMapper.selectMaps(qw)) {
                Object cid = row.get("coupon_id");
                Object cnt = row.get("cnt");
                if (cid instanceof Number && cnt instanceof Number) {
                    fromDb.put(((Number) cid).longValue(), ((Number) cnt).longValue());
                }
            }
        } catch (Exception e) {
            log.warn("优惠券已领计数批量回源失败: {}", e.getMessage());
        }

        for (Long id : missed) {
            long count = fromDb.getOrDefault(id, 0L);
            result.put(id, count);
            // 回种（只在缺失时写），与 getClaimedCount 单条路径语义一致
            try {
                stringRedisTemplate.opsForValue().setIfAbsent(COUPON_COUNT_KEY + id, String.valueOf(count));
            } catch (Exception e) {
                log.warn("优惠券已领计数回种失败: couponId={}, {}", id, e.getMessage());
            }
        }
        return result;
    }

    private int regDaysOf(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || user.getCreateTime() == null) {
            return 0;
        }
        long days = ChronoUnit.DAYS.between(user.getCreateTime().toLocalDate(), LocalDateTime.now().toLocalDate());
        return (int) Math.max(0, days);
    }

    private String typeText(Integer type) {
        if (type != null && type == 2) return "折扣券";
        if (type != null && type == 3) return "无门槛券";
        return "满减券";
    }

    private String amountText(Coupon c) {
        if (c.getType() == null) return "";
        if (c.getType() == 2 && c.getDiscount() != null) {
            String s = c.getDiscount().multiply(BigDecimal.TEN).setScale(1, RoundingMode.HALF_UP)
                    .stripTrailingZeros().toPlainString();
            return s + "折";
        }
        if (c.getAmount() != null) {
            return "¥" + c.getAmount().stripTrailingZeros().toPlainString();
        }
        return "";
    }

    private String scopeText(Coupon c) {
        return c.getScope() != null && c.getScope() == 1 ? "分类券" : "全场";
    }

    private String validText(Coupon c) {
        if (c.getValidType() != null && c.getValidType() == 2 && c.getValidDays() != null) {
            return "领取后 " + c.getValidDays() + " 天有效";
        }
        return c.getValidEnd() != null ? "至 " + c.getValidEnd().format(MM_DD) : "不限";
    }

    private String stateText(int state, Coupon c) {
        switch (state) {
            case 1:
                return "即将开始";
            case 2:
                return c.getTotalCount() != null && getClaimedCount(c.getId()) >= c.getTotalCount()
                        ? "已领完" : "已结束";
            case 3:
                return "已领取";
            default:
                return "可领";
        }
    }

    private String statusText(Integer status) {
        if (status != null && status == 1) return "已使用";
        if (status != null && status == 2) return "已过期";
        return "未使用";
    }

    private String targetText(Coupon c) {
        if (c.getTargetType() != null && c.getTargetType() == 1) return "仅新用户可领取";
        if (c.getTargetType() != null && c.getTargetType() == 2) return "仅老用户可领取";
        return "不符合领取条件";
    }
}