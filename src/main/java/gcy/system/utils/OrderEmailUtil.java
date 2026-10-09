package gcy.system.utils;

import cn.hutool.core.util.StrUtil;
import gcy.system.entity.pojo.Order;
import gcy.system.entity.pojo.User;
import gcy.system.integration.EmailService;
import gcy.system.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * 订单状态邮件发送工具类。
 * 封装"查询订单用户并发送订单状态邮件"的逻辑，供用户端与管理端订单服务复用。
 *
 * @author 郭名城
 * @date 2026-08-11
 */
@Slf4j
public final class OrderEmailUtil {

    private OrderEmailUtil() {
    }

    /**
     * 发送订单状态通知邮件；用户邮箱为空则跳过，发送失败仅记日志不影响主流程。
     *
     * @param refundRemark 退款原因/处理备注，非退款场景传 null
     */
    public static void sendOrderStatus(EmailService emailService, UserMapper userMapper,
                                       Order order, String title, String content,
                                       String statusIcon, String refundRemark) {
        try {
            User user = userMapper.selectById(order.getUserId());
            if (user != null && StrUtil.isNotBlank(user.getEmail())) {
                emailService.sendOrderStatusEmail(user.getEmail(), order.getId(), title, content,
                        statusIcon, order.getTotalPrice().toString(), user.getUserName(),
                        refundRemark);
            }
        } catch (Exception e) {
            log.error("发送订单状态邮件失败: orderId={}", order.getId(), e);
        }
    }
}
