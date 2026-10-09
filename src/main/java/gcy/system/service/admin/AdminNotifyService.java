package gcy.system.service.admin;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import gcy.system.entity.dto.StockAlertItem;
import gcy.system.entity.pojo.AdminNotifySetting;
import gcy.system.entity.pojo.User;
import gcy.system.integration.EmailService;
import gcy.system.mapper.AdminNotifySettingMapper;
import gcy.system.mapper.UserMapper;
import gcy.system.service.admin.Impl.NotifySettingServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 管理员通知服务：统一封装按通知类型读取配置、校验开关、解析收件人、发送邮件的流程。
 * <p>
 * 发送失败仅记录日志，不影响主业务流程。
 *
 * @author 郭名城
 * @date 2026-08-12
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminNotifyService {

    private final AdminNotifySettingMapper adminNotifySettingMapper;

    private final UserMapper userMapper;

    private final EmailService emailService;

    /**
     * 解析指定通知类型下应接收通知的管理员邮箱（仅 isAdmin=1 且已绑定邮箱）。
     * <p>
     * 返回 null 表示配置缺失或开关未开启，调用方应直接跳过；返回空列表表示暂无有效收件人。
     */
    private List<String> resolveAdminEmails(String notifyType) {
        AdminNotifySetting setting = adminNotifySettingMapper.selectOne(
                new LambdaQueryWrapper<AdminNotifySetting>()
                        .eq(AdminNotifySetting::getNotifyType, notifyType));
        if (setting == null) {
            log.warn("未找到通知配置（notifyType={}），可能未执行 admin_notify_setting 迁移，跳过", notifyType);
            return null;
        }
        if (setting.getEnabled() == null || setting.getEnabled() != 1) {
            log.debug("该功能通知未开启，跳过: {}", notifyType);
            return null;
        }
        List<Long> adminIds = NotifySettingServiceImpl.parseIds(setting.getAdminIds());
        if (adminIds.isEmpty()) {
            log.debug("未配置接收管理员，跳过: {}", notifyType);
            return null;
        }
        List<User> admins = userMapper.selectByIds(adminIds);
        if (admins == null || admins.isEmpty()) {
            return List.of();
        }
        return admins.stream()
                .filter(a -> a.getIsAdmin() != null && a.getIsAdmin() == 1 && StrUtil.isNotBlank(a.getEmail()))
                .map(User::getEmail)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 发送普通管理员通知邮件（新订单、退款申请等）；开关未开启或配置缺失时静默跳过。
     */
    public void sendNotification(String notifyType, String subject, String content) {
        try {
            List<String> emails = resolveAdminEmails(notifyType);
            if (emails == null || emails.isEmpty()) {
                return;
            }
            emailService.sendNotificationBatch(emails, subject, content);
            log.info("管理员通知已发送: {}, 收件人 {} 位", subject, emails.size());
        } catch (Exception e) {
            log.error("发送管理员通知失败: {}", subject, e);
        }
    }

    /**
     * 发送库存预警邮件，受后台「库存预警」配置控制。
     *
     * @param displayItems 邮件内展示的库存不足商品列表（已截取前 15 条）
     */
    public void sendStockAlert(List<StockAlertItem> displayItems, int totalCount) {
        try {
            List<String> emails = resolveAdminEmails(NotifySettingServiceImpl.TYPE_STOCK_ALERT);
            if (emails == null || emails.isEmpty()) {
                return;
            }
            for (String email : emails) {
                emailService.sendStockAlertEmail(email, "库存预警", displayItems, totalCount);
            }
            log.info("库存预警邮件已发送, 收件人 {} 位", emails.size());
        } catch (Exception e) {
            log.error("发送库存预警邮件失败", e);
        }
    }
}