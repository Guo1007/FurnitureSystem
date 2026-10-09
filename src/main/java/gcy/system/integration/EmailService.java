package gcy.system.integration;

import gcy.system.utils.CouponGrantScene;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 邮件发送服务类，负责系统内所有邮件通知的构建与发送。
 * 该类封装了 JavaMailSender 与 Thymeleaf 模板引擎的调用逻辑，
 * 提供验证码邮件、订单状态通知、系统通知及库存预警等多种邮件类型的异步发送能力。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    private final TemplateEngine templateEngine;

    @Value("${spring.mail.username}")
    private String from;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 发送HTML格式的邮件（通用内部方法）。
     * 通过 JavaMailSender 构建 MIME 消息，设置发件人、收件人、主题及HTML正文内容，
     * 使用 UTF-8 编码确保中文正常显示，并在发送成功后记录日志。
     * 注意：本方法仅由同类中已标注 {@code @Async} 的方法调用，Spring AOP 不拦截同类内部调用，
     * 因此此处不标注 {@code @Async}（外层方法已异步），避免误导。
     *
     * @param to      收件人邮箱地址
     * @param subject 邮件主题（会自动添加"家具商城 - "前缀）
     * @param html    邮件的HTML正文内容
     */
    public void sendHtmlEmail(String to, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject("家具商城 - " + subject);
            helper.addInline("siteLogo", new ClassPathResource("static/favicon.png"));
            helper.setText(html, true);
            mailSender.send(message);
            log.info("HTML邮件已发送至 {}", to);
        } catch (MessagingException e) {
            log.error("HTML邮件发送失败: {}", to, e);
        }
    }

    /**
     * 发送验证码邮件。
     * 使用 Thymeleaf 模板引擎将验证码、操作类型、有效期等信息填充到邮件模板中，
     * 生成HTML内容后调用通用发送方法完成投递。
     *
     * @param to            收件人邮箱地址
     * @param code          验证码字符串
     * @param action        验证码对应的操作描述（如"注册"、"找回密码"）
     * @param expireMinutes 验证码有效分钟数
     */
    @Async
    public void sendVerifyCode(String to, String code, String action, Long expireMinutes) {
        Context context = new Context();
        context.setVariable("code", code);
        context.setVariable("action", action);
        context.setVariable("expireMinutes", expireMinutes);
        context.setVariable("sendTime", LocalDateTime.now().format(FORMATTER));

        String html = templateEngine.process("email/verify-code", context);
        sendHtmlEmail(to, "验证码", html);
    }

    /**
     * 发送订单状态通知邮件。
     * 根据订单的当前状态（如已下单、已发货、已完成等）构建对应的通知内容，
     * 通过 Thymeleaf 模板引擎渲染邮件HTML，包含订单编号、状态图标、状态颜色、
     * 总价及用户名等信息，最终通过通用发送方法完成投递。
     *
     * @param to         收件人邮箱地址
     * @param orderId    订单编号
     * @param title      邮件标题（即订单状态标题）
     * @param content    订单状态描述内容
     * @param statusIcon 订单状态对应的图标标识
     * @param totalPrice 订单总价字符串
     * @param userName   收件人用户名
     */
    @Async
    public void sendOrderStatusEmail(String to, Long orderId, String title, String content,
                                     String statusIcon,
                                     String totalPrice, String userName,
                                     String refundRemark) {
        Context context = new Context();
        context.setVariable("orderId", orderId);
        context.setVariable("title", title);
        context.setVariable("content", content);
        context.setVariable("statusIcon", statusIcon);
        context.setVariable("totalPrice", totalPrice);
        context.setVariable("userName", userName);
        context.setVariable("refundRemark", refundRemark);
        context.setVariable("sendTime", LocalDateTime.now().format(FORMATTER));

        String html = templateEngine.process("email/order-status", context);
        sendHtmlEmail(to, title, html);
    }

    /**
     * 发送系统通知邮件。
     * 将系统通知的标题和内容通过 Thymeleaf 模板引擎渲染为HTML格式邮件，
     * 并在模板中注入当前发送时间，最终通过通用发送方法完成投递。
     *
     * @param to      收件人邮箱地址
     * @param title   通知标题
     * @param content 通知正文内容
     */
    @Async
    public void sendNotificationEmail(String to, String title, String content) {
        Context context = new Context();
        context.setVariable("title", title);
        context.setVariable("content", content);
        context.setVariable("sendTime", LocalDateTime.now().format(FORMATTER));

        String html = templateEngine.process("email/notification", context);
        sendHtmlEmail(to, title, html);
    }

    /**
     * 定向发放优惠券的通知邮件。
     * <p>
     * 三套模板按「场景」分语气（见 {@link CouponGrantScene}）：补偿要克制诚恳、
     * 关怀可以温暖、通用为中性。模板名由场景编码拼出，所以场景**必须经过白名单**
     * （{@link CouponGrantScene#fromCode}），否则就成了模板路径注入。
     * </p>
     * <p>
     * 券的面额 / 门槛 / 有效期由调用方从券模板的真实字段生成后传进来，
     * <b>模板里不要写死任何具体金额</b> —— 否则换一张券文案就错了。
     * </p>
     *
     * @param to            收件人邮箱
     * @param userName      收件人昵称
     * @param scene         场景编码：compensation / care / general（非法值回落 general）
     * @param couponName    券名称
     * @param amountText    面额文案，如「¥20」「8折」
     * @param thresholdText 门槛文案，如「满100可用」
     * @param validText     有效期文案，如「发放后 30 天有效」
     * @param remark        发放原因，可为空
     */
    @Async
    public void sendCouponGrantEmail(String to, String userName, String scene, String couponName,
                                     String amountText, String thresholdText, String validText,
                                     String remark) {
        CouponGrantScene grantScene = CouponGrantScene.fromCode(scene);

        Context context = new Context();
        context.setVariable("userName", userName);
        context.setVariable("couponName", couponName);
        context.setVariable("amountText", amountText);
        context.setVariable("thresholdText", thresholdText);
        context.setVariable("validText", validText);
        // 空串不传，模板里用 th:if 判断是否渲染这一块
        context.setVariable("remark", (remark == null || remark.isBlank()) ? null : remark);
        context.setVariable("sendTime", LocalDateTime.now().format(FORMATTER));

        String html = templateEngine.process("email/coupon-" + grantScene.getCode(), context);
        sendHtmlEmail(to, grantScene.getTitle(), html);
    }

    /**
     * 批量发送系统通知邮件（如向全体用户群发）。
     * <p>
     * 在一个异步任务内构建多封 MimeMessage 并一次 send 发送，
     * 复用同一条 SMTP 连接，避免逐封发送产生大量连接与异步任务。
     * </p>
     *
     * @param emails  收件人邮箱列表
     * @param title   通知标题
     * @param content 通知正文内容
     */
    @Async
    public void sendNotificationBatch(List<String> emails, String title, String content) {
        if (emails == null || emails.isEmpty()) {
            return;
        }
        Context context = new Context();
        context.setVariable("title", title);
        context.setVariable("content", content);
        context.setVariable("sendTime", LocalDateTime.now().format(FORMATTER));
        String html = templateEngine.process("email/notification", context);
        try {
            List<MimeMessage> messages = new ArrayList<>(emails.size());
            for (String to : emails) {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(from);
                helper.setTo(to);
                helper.setSubject("家具商城 - " + title);
                helper.addInline("siteLogo", new ClassPathResource("static/favicon.png"));
                helper.setText(html, true);
                messages.add(message);
            }
            mailSender.send(messages.toArray(new MimeMessage[0]));
            log.info("通知邮件批量发送完成，共 {} 封", emails.size());
        } catch (MessagingException e) {
            log.error("通知邮件批量发送失败，收件人 {} 位", emails.size(), e);
        }
    }

    /**
     * 发送库存预警邮件。
     * 当商品库存低于预设阈值时触发，将预警标题和库存不足的商品列表通过
     * Thymeleaf 模板引擎渲染为HTML格式邮件，并注入当前发送时间，
     * 最终通过通用发送方法完成投递。
     *
     * @param to    收件人邮箱地址
     * @param title 预警标题
     * @param items 库存不足的商品列表数据
     */
    @Async
    public void sendStockAlertEmail(String to, String title, Object items, int totalCount) {
        Context context = new Context();
        context.setVariable("title", title);
        context.setVariable("items", items);
        context.setVariable("totalCount", totalCount);
        context.setVariable("sendTime", LocalDateTime.now().format(FORMATTER));

        String html = templateEngine.process("email/stock-alert", context);
        sendHtmlEmail(to, title, html);
    }
}
