package gcy.system.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import gcy.system.utils.CouponUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
        // 定向发放券（issue_type=1）不进领券中心，也不参与公开限量/限领计数。
        List<Coupon> coupons = couponMapper.selectList(new LambdaQueryWrapper<Coupon>()
                .eq(Coupon::getStatus, 1)
                .eq(Coupon::getIssueType, 1)
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

        // 全局已领数量：一次 multiGet + 一次 GROUP BY 回源
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
        return Result.ok(toUserCouponVOs(list));
    }

    @Override
    public Result getMyCouponsPage(Long userId, Integer status, Integer type, Long current, Long size) {
        long pageNo = (current != null && current > 0) ? current : 1L;
        long pageSize = (size != null && size > 0) ? size : 10L;

        // 券类型在券模板 coupon 上而非 user_coupon，按类型过滤需先取该类型模板ID；
        // 模板由后台维护、数量很小，IN 列表可控。
        List<Long> typeCouponIds = null;
        if (type != null) {
            typeCouponIds = couponMapper.selectList(new LambdaQueryWrapper<Coupon>()
                            .select(Coupon::getId)
                            .eq(Coupon::getType, type))
                    .stream().map(Coupon::getId).collect(Collectors.toList());
            if (typeCouponIds.isEmpty()) {
                // 该类型下一张券模板都没有，必定是空页，不必再查一次
                Page<UserCouponVO> emptyPage = new Page<>(pageNo, pageSize, 0);
                emptyPage.setRecords(new ArrayList<>());
                return Result.ok(emptyPage);
            }
        }

        LambdaQueryWrapper<UserCoupon> wrapper = new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getUserId, userId)
                .orderByDesc(UserCoupon::getGotTime);
        if (status != null) {
            wrapper.eq(UserCoupon::getStatus, status);
        }
        if (typeCouponIds != null) {
            wrapper.in(UserCoupon::getCouponId, typeCouponIds);
        }

        Page<UserCoupon> result = userCouponMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        // 分页元信息沿用 count 查询的结果，只替换 records 为 VO
        Page<UserCouponVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(toUserCouponVOs(result.getRecords()));
        return Result.ok(voPage);
    }

    /**
     * 领取记录补全为展示 VO：关联券模板、纠正过期状态。
     * 全量（选券弹窗）与分页（我的卡券）共用，避免同一张券的展示字段或状态不一致。
     */
    private List<UserCouponVO> toUserCouponVOs(List<UserCoupon> list) {
        if (list == null || list.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> couponIds = list.stream().map(UserCoupon::getCouponId).distinct().collect(Collectors.toList());
        Map<Long, Coupon> couponMap = couponMapper.selectByIds(couponIds).stream()
                .collect(Collectors.toMap(Coupon::getId, c -> c));

        LocalDateTime now = LocalDateTime.now();
        // 过期纠正：先收集 id，一条 IN(...) 批量 UPDATE，避免读接口里逐条写库
        Set<Long> expiredIds = list.stream()
                .filter(uc -> uc.getStatus() == 0 && uc.getExpireTime() != null && uc.getExpireTime().isBefore(now))
                .map(UserCoupon::getId)
                .collect(Collectors.toSet());
        if (!expiredIds.isEmpty()) {
            userCouponMapper.update(null, new LambdaUpdateWrapper<UserCoupon>()
                    .in(UserCoupon::getId, expiredIds)
                    .set(UserCoupon::getStatus, 2));
        }

        return list.stream()
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
        // 定向券不接受主动领取：列表已过滤，此处为第二道防线，防止直接拿 ID 调接口领取。
        if (c.getIssueType() != null && c.getIssueType() == 2) {
            return Result.fail("该优惠券不支持领取");
        }
        LocalDateTime now = LocalDateTime.now();
        if (c.getClaimStart() != null && now.isBefore(c.getClaimStart())) {
            return Result.fail("不在领取时间范围内");
        }
        if (c.getClaimEnd() != null && now.isAfter(c.getClaimEnd())) {
            return Result.fail("领取已结束");
        }
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
        // 用户限领计数同样要回种：否则 Redis 重启后限领计数归零，「每人限领 N 张」退化为无限领。
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
        // 统一走 CouponUtil：定向发放路径复用同一套过期时间规则，避免两处漂移
        return CouponUtil.resolveExpireTime(c, now);
    }

    // ==================== 工具方法 ====================

    private long getClaimedCount(Long couponId) {
        String v = stringRedisTemplate.opsForValue().get(COUPON_COUNT_KEY + couponId);
        if (v != null) {
            return Long.parseLong(v);
        }
        // Redis 缺失时回退 DB 计数并回种
        long count = userCouponMapper.selectCount(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getCouponId, couponId));
        stringRedisTemplate.opsForValue().setIfAbsent(COUPON_COUNT_KEY + couponId, String.valueOf(count));
        return count;
    }

    /**
     * 批量取各券已领数量：一次 multiGet 拿齐，缺失的用一条 GROUP BY 回源后回种。
     */
    private Map<Long, Long> batchClaimedCount(List<Coupon> coupons) {
        Map<Long, Long> result = new HashMap<>();
        if (coupons == null || coupons.isEmpty()) {
            return result;
        }
        List<Long> ids = coupons.stream().map(Coupon::getId).distinct().toList();
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
        return CouponUtil.amountText(c);
    }

    private String scopeText(Coupon c) {
        return CouponUtil.scopeText(c);
    }

    private String validText(Coupon c) {
        // 起算说法用「领取后」：这是给「我的卡券」页看的，用户确实是领来的
        return CouponUtil.validText(c, "领取后");
    }

    private String stateText(int state, Coupon c) {
        return switch (state) {
            case 1 -> "即将开始";
            case 2 -> c.getTotalCount() != null && getClaimedCount(c.getId()) >= c.getTotalCount()
                    ? "已领完" : "已结束";
            case 3 -> "已领取";
            default -> "可领";
        };
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