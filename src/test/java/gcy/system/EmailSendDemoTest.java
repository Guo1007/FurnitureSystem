package gcy.system;

import gcy.system.integration.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 邮件发送实测（看效果用）。
 * <p>
 * 注意：本测试会通过真实 SMTP 发邮件，需要正确配置
 * spring.mail.username / spring.mail.password（或环境变量 MAIL_USERNAME / MAIL_PASSWORD）。
 * 默认收件人是发件人自己，若要发给其他邮箱，请设置环境变量 EMAIL_TEST_RECEIVER。
 * </p>
 * <p>
 * 各 sendXxx 方法均为 @Async 异步执行，因此调用后需留出等待时间让邮件真正投递；
 * 邮件由本机网络与 QQ 邮箱投递速度决定，收到时间可能略有延迟。
 * </p>
 */
@Slf4j
@SpringBootTest
class EmailSendDemoTest {

    @Autowired
    private EmailService emailService;

    @Value("${spring.mail.username:}")
    private String from;

    @Test
    @DisplayName("触发全部邮件发送，验证品牌区与底部免责声明效果")
    void sendAllEmails() throws InterruptedException {
        // 收件人：优先取环境变量 EMAIL_TEST_RECEIVER，否则发给发件人自己
        String receiver = System.getenv("EMAIL_TEST_RECEIVER");
        if (receiver == null || receiver.isBlank()) {
            receiver = from;
        }
        log.info("==== 邮件收件人: {} ====", receiver);

        if (receiver == null || receiver.isBlank()) {
            throw new IllegalStateException(
                    "未配置收件人：请设置 spring.mail.username 或环境变量 EMAIL_TEST_RECEIVER");
        }

        // 1. 验证码邮件
        emailService.sendVerifyCode(receiver, "123456", "注册", 5L);
        waitForAsync(3000);

        // 2. 订单状态邮件（含退款备注场景）
        emailService.sendOrderStatusEmail(receiver, 10001L, "订单已发货",
                "您的订单已由家具商城发出，正在配送途中，请注意查收。",
                "📦", "1288.00", "张三", null);
        waitForAsync(3000);

        // 3. 系统通知邮件
        emailService.sendNotificationEmail(receiver, "新春家具大促",
                "家具商城春季焕新活动已开启，全场满减，欢迎选购喜欢的家具。");
        waitForAsync(3000);

        // 4. 库存预警邮件
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Map<String, Object> item = new HashMap<>();
            item.put("fName", "实木餐桌 " + (i + 1) + "号");
            item.put("stock", 2 + i);
            items.add(item);
        }
        emailService.sendStockAlertEmail(receiver, "库存预警", items, 3);
        waitForAsync(3000);

        // 5. 批量系统通知（单收件人测试模板效果）
        List<String> batch = List.of(receiver);
        emailService.sendNotificationBatch(batch, "订单配送提醒",
                "您的订单预计今日送达，请保持电话畅通，方便快递员与您联系。");
        waitForAsync(5000);

        log.info("==== 全部邮件已触发，请前往收件箱查看效果 ====");
    }

    /**
     * 等待异步邮件任务投递，给 SMTP 发送留出时间。
     */
    private void waitForAsync(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }
}