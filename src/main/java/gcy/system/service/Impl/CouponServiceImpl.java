package gcy.system.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

        List<CouponItemVO> result = coupons.stream()
                .map(c -> toClaimableVO(c, userClaimed.getOrDefault(c.getId(), 0L), regDays))
                .filter(Objects::nonNull) // 过滤掉不满足人群的券
                .collect(Collectors.toList());

        return Result.ok(result);
    }

    private CouponItemVO toClaimableVO(Coupon c, Long userClaimedCount, int regDays) {
        int state = resolveState(c, userClaimedCount, regDays, getClaimedCount(c.getId()));
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
        vo.setClaimedCount((int) getClaimedCount(c.getId()));
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
        if (c.getTotalCount() != null && claimedCount >= c.getTotalCount()) {
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
        List<UserCouponVO> vos = list.stream()
                .map(uc -> {
                    // 未用但已过期的实时纠正
                    if (uc.getStatus() == 0 && uc.getExpireTime() != null && uc.getExpireTime().isBefore(now)) {
                        uc.setStatus(2);
                        userCouponMapper.update(null, new LambdaUpdateWrapper<UserCoupon>()
                                .eq(UserCoupon::getId, uc.getId())
                                .set(UserCoupon::getStatus, 2));
                    }
                    Coupon c = couponMap.get(uc.getCouponId());
                    UserCouponVO vo = new UserCouponVO();
                    vo.setUserCouponId(uc.getId());
                    vo.setCouponId(uc.getCouponId());
                    vo.setName(c != null ? c.getName() : "已下架");
                    vo.setType(c != null ? c.getType() : null);
                    vo.setAmountText(c != null ? amountText(c) : "");
                    vo.setScopeText(c != null ? scopeText(c) : "全场");
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