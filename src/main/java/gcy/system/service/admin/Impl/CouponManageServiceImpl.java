package gcy.system.service.admin.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import gcy.system.entity.dto.CouponGrantDTO;
import gcy.system.entity.dto.Result;
import gcy.system.entity.dto.SendNotificationFormDTO;
import gcy.system.entity.dto.admin.AdminCouponFormDTO;
import gcy.system.entity.pojo.Coupon;
import gcy.system.entity.pojo.User;
import gcy.system.entity.pojo.UserCoupon;
import gcy.system.integration.EmailService;
import gcy.system.mapper.CouponMapper;
import gcy.system.mapper.UserMapper;
import gcy.system.service.INotificationService;
import gcy.system.service.admin.ICouponManageService;
import gcy.system.utils.AfterCommit;
import gcy.system.utils.CouponGrantScene;
import gcy.system.utils.CouponUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static gcy.system.utils.RedisConstants.COUPON_COUNT_KEY;

/**
 * 管理端优惠券服务实现。
 *
 * @author 郭名城
 * @date 2026-09-20
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponManageServiceImpl implements ICouponManageService {

    private final CouponMapper couponMapper;

    private final StringRedisTemplate stringRedisTemplate;

    private final UserMapper userMapper;

    private final INotificationService notificationService;

    private final EmailService emailService;

    @Override
    public Result page(Integer current, Integer size, String name, Integer issueType) {
        Page<Coupon> page = couponMapper.selectPage(new Page<>(current, size),
                new LambdaQueryWrapper<Coupon>()
                        .like(name != null && !name.isEmpty(), Coupon::getName, name)
                        .eq(issueType != null, Coupon::getIssueType, issueType)
                        .orderByDesc(Coupon::getId));
        return Result.ok(page.getRecords(), page.getTotal());
    }

    @Override
    public Result add(AdminCouponFormDTO dto) {
        Coupon c = new Coupon();
        copyForm(dto, c);
        c.setCreateTime(LocalDateTime.now());
        c.setUpdateTime(LocalDateTime.now());
        couponMapper.insert(c);
        // 初始化领取计数（不存在则不覆盖）
        stringRedisTemplate.opsForValue().setIfAbsent(COUPON_COUNT_KEY + c.getId(), "0");
        return Result.okMsg("新增成功");
    }

    @Override
    public Result update(AdminCouponFormDTO dto) {
        Coupon exist = couponMapper.selectById(dto.getId());
        if (exist == null) {
            return Result.fail("优惠券不存在");
        }
        Coupon c = new Coupon();
        copyForm(dto, c);
        c.setCreateTime(exist.getCreateTime());
        c.setUpdateTime(LocalDateTime.now());
        couponMapper.updateById(c);
        stringRedisTemplate.opsForValue().setIfAbsent(COUPON_COUNT_KEY + c.getId(), "0");
        return Result.okMsg("修改成功");
    }

    @Override
    public Result delete(Long id) {
        Coupon exist = couponMapper.selectById(id);
        if (exist == null) {
            return Result.fail("优惠券不存在");
        }
        couponMapper.deleteById(id);
        return Result.okMsg("删除成功");
    }

    @Override
    public Result toggleStatus(Long id) {
        Coupon exist = couponMapper.selectById(id);
        if (exist == null) {
            return Result.fail("优惠券不存在");
        }
        int newStatus = exist.getStatus() != null && exist.getStatus() == 1 ? 0 : 1;
        couponMapper.update(null, new LambdaUpdateWrapper<Coupon>()
                .eq(Coupon::getId, id)
                .set(Coupon::getStatus, newStatus));
        return Result.okMsg(newStatus == 1 ? "已启用" : "已停用");
    }

    @Override
    public Result info(Long id) {
        return Result.ok(couponMapper.selectById(id));
    }

    private void copyForm(AdminCouponFormDTO dto, Coupon c) {
        c.setId(dto.getId());
        c.setName(dto.getName());
        c.setType(dto.getType());
        c.setMinThreshold(dto.getMinThreshold() == null ? java.math.BigDecimal.ZERO : dto.getMinThreshold());
        c.setAmount(dto.getAmount());
        c.setDiscount(dto.getDiscount());
        c.setCapAmount(dto.getCapAmount());
        c.setScope(dto.getScope() == null ? 0 : dto.getScope());
        c.setTypeId(dto.getTypeId());
        c.setTotalCount(dto.getTotalCount());
        c.setPerUserLimit(dto.getPerUserLimit() == null ? 1 : dto.getPerUserLimit());
        c.setClaimStart(dto.getClaimStart());
        c.setClaimEnd(dto.getClaimEnd());
        c.setValidType(dto.getValidType());
        c.setValidStart(dto.getValidStart());
        c.setValidEnd(dto.getValidEnd());
        c.setValidDays(dto.getValidDays());
        c.setTargetType(dto.getTargetType() == null ? 0 : dto.getTargetType());
        c.setTargetDays(dto.getTargetDays());
        c.setStackable(dto.getStackable() == null ? 0 : dto.getStackable());
        c.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        // 缺省为「公开领取」
        c.setIssueType(dto.getIssueType() == null ? 1 : dto.getIssueType());
    }

    // ==================== 定向发放 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result grantCoupons(CouponGrantDTO dto) {
        // ① 券校验：必须是「定向发放」类型的券
        Coupon coupon = couponMapper.selectById(dto.getCouponId());
        if (coupon == null) {
            return Result.fail("优惠券不存在");
        }
        if (coupon.getIssueType() == null || coupon.getIssueType() != 2) {
            return Result.fail("只能发放「定向发放」类型的券。请先在券模板里把发放方式改为定向发放，或另建一张定向券");
        }
        if (coupon.getStatus() != null && coupon.getStatus() == 0) {
            return Result.fail("该优惠券已停用");
        }

        // ② 用户去重 + 存在性校验（防止给已注销的用户ID发券）
        List<Long> rawIds = dto.getUserIds() == null ? List.of() : dto.getUserIds();
        List<Long> userIds = rawIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (userIds.isEmpty()) {
            return Result.fail("请至少选择一个用户");
        }
        List<User> users = userMapper.selectByIds(userIds);
        if (users.isEmpty()) {
            return Result.fail("所选用户均不存在");
        }

        // ③ 批量生成领取记录
        int quantity = (dto.getQuantity() == null || dto.getQuantity() < 1) ? 1 : dto.getQuantity();
        LocalDateTime now = LocalDateTime.now();
        // 有效期与「用户自己领」用同一套规则推算，两条路径的过期时间口径必须一致
        LocalDateTime expireTime = CouponUtil.resolveExpireTime(coupon, now);

        List<UserCoupon> rows = new ArrayList<>(users.size() * quantity);
        for (User u : users) {
            for (int i = 0; i < quantity; i++) {
                UserCoupon uc = new UserCoupon();
                uc.setUserId(u.getId());
                uc.setCouponId(coupon.getId());
                uc.setStatus(0);
                uc.setGotTime(now);
                uc.setExpireTime(expireTime);
                // source=1：标记为管理员发放，这是日后查「补偿过谁」的依据
                uc.setSource(1);
                rows.add(uc);
            }
        }
        // 一条批量插入完成，不要逐条 insert
        Db.saveBatch(rows);

        // ④ 通知与邮件放在事务提交后：二者是「提交了才该发生」的副作用，放事务内一旦回滚
        //    会出现「用户没拿到券却收到通知」。代价是提交后进程挂掉会漏发通知，用户仍能在「我的卡券」看到券，可接受。
        CouponGrantScene scene = CouponGrantScene.fromCode(dto.getScene());
        boolean sendNotification = !Boolean.FALSE.equals(dto.getSendNotification());
        boolean sendEmail = Boolean.TRUE.equals(dto.getSendEmail());

        String amountText = CouponUtil.amountText(coupon);
        String thresholdText = CouponUtil.thresholdText(coupon);
        // 用户没有「领取」动作，时间口径文案用「发放后」
        String validText = CouponUtil.validText(coupon, "发放后");
        String content = buildGrantNotice(scene, coupon, amountText, thresholdText, validText, dto.getRemark());

        List<User> targets = List.copyOf(users);
        Long couponId = coupon.getId();
        String couponName = coupon.getName();
        String remark = dto.getRemark();

        AfterCommit.run(() -> {
            if (sendNotification) {
                for (User u : targets) {
                    try {
                        SendNotificationFormDTO n = new SendNotificationFormDTO();
                        n.setUserId(u.getId());
                        n.setTitle(scene.getTitle());
                        n.setContent(content);
                        // promotion 类型前端已有「促销通知」标签与专属图标，无需改映射
                        n.setType("promotion");
                        // 邮件由下面自行发送：站内通知那套走的是通用模板，无法按场景分语气
                        n.setSendEmail(false);
                        notificationService.sendNotification(n);
                    } catch (Exception e) {
                        log.warn("定向发券站内通知失败: userId={}, couponId={}", u.getId(), couponId, e);
                    }
                }
            }
            if (sendEmail) {
                for (User u : targets) {
                    if (u.getEmail() == null || u.getEmail().isBlank()) {
                        continue;
                    }
                    try {
                        emailService.sendCouponGrantEmail(u.getEmail(), u.getUserName(), scene.getCode(),
                                couponName, amountText, thresholdText, validText, remark);
                    } catch (Exception e) {
                        log.warn("定向发券邮件发送失败: userId={}, couponId={}", u.getId(), couponId, e);
                    }
                }
            }
        });

        log.info("定向发放优惠券: couponId={}, 用户数={}, 每人张数={}, scene={}, 站内通知={}, 邮件={}",
                couponId, users.size(), quantity, scene.getCode(), sendNotification, sendEmail);

        String msg = "已向 " + users.size() + " 位用户各发放 " + quantity + " 张";
        if (users.size() < userIds.size()) {
            msg += "（跳过 " + (userIds.size() - users.size()) + " 个不存在的用户）";
        }
        return Result.okMsg(msg);
    }

    /**
     * 拼装站内通知正文。语气由场景决定（见 {@link CouponGrantScene}），券信息一律取自券模板真实字段。
     */
    private String buildGrantNotice(CouponGrantScene scene, Coupon coupon, String amountText,
                                    String thresholdText, String validText, String remark) {
        StringBuilder sb = new StringBuilder();
        sb.append(scene.getNotifyIntro()).append("\n");
        sb.append("券名称：").append(coupon.getName()).append("\n");
        sb.append("面额：").append(amountText).append("\n");
        sb.append("使用门槛：").append(thresholdText).append("\n");
        sb.append("有效期：").append(validText).append("\n");
        if (remark != null && !remark.isBlank()) {
            sb.append("说明：").append(remark).append("\n");
        }
        sb.append("可在「个人中心 - 我的卡券」中查看使用。");
        return sb.toString();
    }
}