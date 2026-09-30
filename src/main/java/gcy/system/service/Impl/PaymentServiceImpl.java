package gcy.system.service.Impl;

import com.alipay.api.AlipayClient;
import com.alipay.api.AlipayConstants;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import gcy.system.config.AlipayProperties;
import gcy.system.entity.dto.Result;
import gcy.system.entity.pojo.Order;
import gcy.system.entity.pojo.Payment;
import gcy.system.mapper.PaymentMapper;
import gcy.system.service.IOrderService;
import gcy.system.service.IPaymentService;
import gcy.system.utils.OrderStatus;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 支付服务实现类，负责支付宝电脑网站支付的预下单与异步回调处理。
 * <p>
 * 预下单生成商户外单号并回调支付网关获取付款页面；回调接口负责验签、
 * 金额核对、幂等去重，并在到账后更新支付流水与订单状态。
 * </p>
 *
 * @author 郭名城
 * @date 2026-09-22
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl extends ServiceImpl<PaymentMapper, Payment> implements IPaymentService {

    private final AlipayClient alipayClient;

    private final AlipayProperties alipayProperties;

    private final IOrderService orderService;

    /**
     * 事务管理器。用于显式界定回调处理的事务边界——
     * 本类内方法互调不会经过 Spring 代理，@Transactional 会失效，故使用事务模板。
     */
    private final PlatformTransactionManager txManager;

    /**
     * 预下单：校验订单归属与状态后，生成支付流水并调用支付宝电脑网站支付接口，
     * 返回可自动提交的付款 HTML 表单。
     *
     * @param orderId 待支付订单ID
     * @param userId  当前操作用户ID
     * @return Result 成功时 data 为支付宝返回的付款表单 HTML
     */
    @Override
    public Result createPay(Long orderId, Long userId) {
        Order order = orderService.getById(orderId);
        if (order == null) {
            return Result.fail("订单不存在！");
        }
        if (!order.getUserId().equals(userId)) {
            return Result.fail("无权支付该订单！");
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT.getCode()) {
            if (order.getStatus() == OrderStatus.PAID.getCode()
                    || order.getStatus() == OrderStatus.SHIPPED.getCode()) {
                return Result.fail("订单已支付，无需重复支付！");
            }
            return Result.fail("订单状态异常，无法支付！");
        }

        // 复用已存在的待支付流水，避免重复插入触发唯一键冲突
        Payment payment = lambdaQuery()
                .eq(Payment::getOrderId, orderId)
                .eq(Payment::getStatus, 0)
                .last("LIMIT 1")
                .one();
        if (payment == null) {
            payment = new Payment();
            payment.setOrderId(orderId);
            payment.setUserId(userId);
            payment.setPayNo("GD" + orderId + System.currentTimeMillis());
            payment.setTotalAmount(order.getTotalPrice());
            payment.setStatus(0);
            save(payment);
        }

        try {
            AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
            request.setNotifyUrl(alipayProperties.getNotifyUrl());
            request.setBizContent("{" +
                    "\"out_trade_no\":\"" + payment.getPayNo() + "\"," +
                    "\"total_amount\":\"" + payment.getTotalAmount() + "\"," +
                    "\"subject\":\"家具商城-订单#" + orderId + "\"," +
                    "\"product_code\":\"FAST_INSTANT_TRADE_PAY\"" +
                    "}");
            AlipayTradePagePayResponse response = alipayClient.pageExecute(request);
            if (response.isSuccess()) {
                log.info("支付宝预下单成功: orderId={}, payNo={}, notifyUrl={}",
                        orderId, payment.getPayNo(), alipayProperties.getNotifyUrl());
                return Result.ok(response.getBody());
            }
            log.warn("支付宝预下单失败: orderId={}, code={}, msg={}, subMsg={}",
                    orderId, response.getCode(), response.getMsg(), response.getSubMsg());
            return Result.fail("支付失败：" + (response.getSubMsg() != null ? response.getSubMsg() : response.getMsg()));
        } catch (Exception e) {
            log.error("支付宝预下单异常: orderId={}", orderId, e);
            return Result.fail("支付服务异常，请稍后重试");
        }
    }

    /**
     * 处理支付宝异步回调。
     * <p>
     * 流程：验签 → 仅处理成功/完成的交易 → 按 out_trade_no 定位流水 →
     * 幂等去重 → 金额核对 → 更新流水与订单状态。返回 "success" 告知支付宝不再重发。
     * <p>
     * 「更新流水」与「确认订单」在同一事务内完成：若订单确认失败（如订单已被超时任务取消），
     * 流水更新一并回滚并返回 "failure"，让支付宝按既定节奏重投，避免出现
     * 「钱已收、流水已付、订单仍待支付」的资损中间态。
     * </p>
     *
     * @param request HTTP 请求，包含支付宝回传的参数
     * @return success / failure（订单确认失败时返回 failure 触发重试）
     */
    @Override
    public String handleNotify(HttpServletRequest request) {
        try {
            Map<String, String> params = new HashMap<>();
            request.getParameterMap().forEach((key, values) ->
                    params.put(key, values != null && values.length > 0 ? values[0] : ""));

            // 回调入口：记录收到的关键参数（不含签名明文）
            log.info("支付宝回调入口: keys={}, out_trade_no={}, trade_status={}, total_amount={}, app_id={}",
                    params.keySet(),
                    params.get("out_trade_no"),
                    params.get("trade_status"),
                    params.get("total_amount"),
                    params.get("app_id"));

            // 1. 验签（RSA2）
            boolean signVerified = AlipaySignature.rsaCheckV1(
                    params, alipayProperties.getAlipayPublicKey(),
                    AlipayConstants.CHARSET_UTF8, AlipayConstants.SIGN_TYPE_RSA2);
            log.info("支付宝回调验签结果: signVerified={}", signVerified);
            if (!signVerified) {
                log.warn("支付宝回调验签失败");
                return "failure";
            }

            // 1.1 校验 app_id：确认回调确实来自本应用，防止其它商户的回调被本系统接受。
            // 验签已能证明来源，此处为纵深防御；未配置 app-id 时跳过以免阻断沙箱调试。
            String expectAppId = alipayProperties.getAppId();
            String actualAppId = params.get("app_id");
            if (expectAppId != null && !expectAppId.isBlank() && !expectAppId.equals(actualAppId)) {
                log.warn("支付宝回调 app_id 不匹配: 期望={}, 实际={}", expectAppId, actualAppId);
                return "failure";
            }

            String outTradeNo = params.get("out_trade_no");
            String tradeStatus = params.get("trade_status");
            if (outTradeNo == null || (!"TRADE_SUCCESS".equals(tradeStatus) && !"TRADE_FINISHED".equals(tradeStatus))) {
                log.info("支付宝回调非最终到账状态，忽略: trade_status={}", tradeStatus);
                return "success"; // 非最终到账状态（如待支付）可安全忽略
            }

            Payment payment = lambdaQuery().eq(Payment::getPayNo, outTradeNo).one();
            if (payment == null) {
                log.warn("支付宝回调找不到对应支付流水: payNo={}", outTradeNo);
                return "failure";
            }
            log.info("支付宝回调命中流水: paymentId={}, orderId={}, payNo={}, payStatus={}",
                    payment.getId(), payment.getOrderId(), outTradeNo, payment.getStatus());

            // 2. 幂等：已支付直接返回成功，避免重复处理
            if (payment.getStatus() != null && payment.getStatus() == 1) {
                log.info("支付宝回调幂等命中，已支付，直接返回 success: payNo={}", outTradeNo);
                return "success";
            }

            // 3. 金额核对（以服务端订单金额为准）
            String totalAmountParam = params.get("total_amount");
            if (totalAmountParam == null || totalAmountParam.isBlank()) {
                log.warn("支付宝回调缺少 total_amount: payNo={}", outTradeNo);
                return "failure";
            }
            BigDecimal notifyAmount;
            try {
                notifyAmount = new BigDecimal(totalAmountParam);
            } catch (NumberFormatException e) {
                log.warn("支付宝回调 total_amount 格式非法: payNo={}, total_amount={}", outTradeNo, totalAmountParam);
                return "failure";
            }
            log.info("支付宝回调金额核对: 通知={}, 应有={}", notifyAmount, payment.getTotalAmount());
            if (notifyAmount.compareTo(payment.getTotalAmount()) != 0) {
                log.warn("支付宝回调金额不匹配: payNo={}, 应={}, 实={}",
                        outTradeNo, payment.getTotalAmount(), notifyAmount);
                return "failure";
            }

            // 4. 更新支付流水 + 5. 确认订单已支付（两者同一事务，要么都成，要么都回滚）
            String tradeNo = params.get("trade_no");
            Boolean confirmed = new TransactionTemplate(txManager).execute(status -> {
                // CAS：只有流水仍为待支付(0)的实例可推进，避免并发回调重复处理
                boolean claimed = lambdaUpdate()
                        .set(Payment::getStatus, 1)
                        .set(Payment::getTradeNo, tradeNo)
                        .set(Payment::getPayTime, LocalDateTime.now())
                        .eq(Payment::getId, payment.getId())
                        .eq(Payment::getStatus, 0)
                        .update();
                if (!claimed) {
                    log.info("支付宝回调并发命中，流水已被其它实例处理: paymentId={}", payment.getId());
                    return Boolean.TRUE;
                }
                log.info("支付宝回调已更新支付流水为已支付: paymentId={}", payment.getId());

                Result confirmResult = orderService.confirmPaid(payment.getOrderId());
                if (confirmResult == null || !Boolean.TRUE.equals(confirmResult.getSuccess())) {
                    // 订单可能已被超时任务取消。此处必须回滚流水并让支付宝重投，
                    // 否则会出现「钱已收、流水已付、订单仍待支付」的资损中间态。
                    log.error("支付宝回调确认订单失败，回滚流水并等待重投: orderId={}, result={}",
                            payment.getOrderId(), confirmResult == null ? "null" : confirmResult.getSuccess());
                    status.setRollbackOnly();
                    return Boolean.FALSE;
                }
                return Boolean.TRUE;
            });

            if (!Boolean.TRUE.equals(confirmed)) {
                // 返回 failure 让支付宝按 1min/4h/24h 等节奏重投；
                // 若订单已确定无法置为已支付（如已取消），需人工介入退款。
                log.error("支付宝回调未确认成功，返回 failure 触发重试: orderId={}, payNo={}",
                        payment.getOrderId(), outTradeNo);
                return "failure";
            }
            log.info("支付宝回调处理成功: orderId={}, payNo={}", payment.getOrderId(), outTradeNo);
            return "success";
        } catch (Exception e) {
            log.error("支付宝回调处理异常", e);
            return "failure";
        }
    }

    /**
     * 主动查询订单支付状态（对账兜底）。
     * <p>
     * 订单仍在待支付时，主动用支付宝 {@code alipay.trade.query} 查询该订单商户单号的
     * 真实交易状态；若支付宝已扣款成功，则本地更新流水并确认订单已支付，返回 true。
     * </p>
     *
     * @param orderId 待查订单ID
     * @param userId  当前操作用户ID
     * @return Result.data 为 true 表示已支付，false 表示仍待支付
     */
    @Override
    public Result queryPayStatus(Long orderId, Long userId) {
        Order order = orderService.getById(orderId);
        if (order == null) {
            return Result.fail("订单不存在！");
        }
        if (!order.getUserId().equals(userId)) {
            return Result.fail("无权查看该订单！");
        }
        // 非待支付状态：已支付类状态返回 true，其余返回 false
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT.getCode()) {
            boolean paid = order.getStatus() == OrderStatus.PAID.getCode()
                    || order.getStatus() == OrderStatus.SHIPPED.getCode()
                    || order.getStatus() == OrderStatus.COMPLETED.getCode()
                    || order.getStatus() == OrderStatus.REVIEWED.getCode()
                    || order.getStatus() == OrderStatus.REFUNDED.getCode();
            return Result.ok(paid);
        }

        // 取该订单最近一条支付流水（含 payNo）
        Payment payment = lambdaQuery()
                .eq(Payment::getOrderId, orderId)
                .orderByDesc(Payment::getId)
                .last("LIMIT 1")
                .one();
        if (payment == null) {
            return Result.ok(false);
        }

        try {
            AlipayTradeQueryRequest req = new AlipayTradeQueryRequest();
            req.setBizContent("{\"out_trade_no\":\"" + payment.getPayNo() + "\"}");
            AlipayTradeQueryResponse resp = alipayClient.execute(req);
            if (resp.isSuccess()) {
                String tradeStatus = resp.getTradeStatus();
                log.info("主动查单成功: orderId={}, payNo={}, tradeStatus={}, tradeNo={}",
                        orderId, payment.getPayNo(), tradeStatus, resp.getTradeNo());
                if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
                    // 金额核对，避免查询到异常交易
                    String totalAmt = resp.getTotalAmount();
                    BigDecimal amt = totalAmt == null ? BigDecimal.ZERO : new BigDecimal(totalAmt);
                    if (amt.compareTo(payment.getTotalAmount()) != 0) {
                        log.warn("主动查单金额不匹配: orderId={}, 应={}, 实={}",
                                orderId, payment.getTotalAmount(), amt);
                        return Result.ok(false);
                    }
                    // 更新流水为已支付并回填交易号
                    lambdaUpdate()
                            .set(Payment::getStatus, 1)
                            .set(Payment::getTradeNo, resp.getTradeNo())
                            .set(Payment::getPayTime, LocalDateTime.now())
                            .eq(Payment::getId, payment.getId())
                            .update();
                    // 确认订单已支付（CAS 幂等）
                    orderService.confirmPaid(orderId);
                    return Result.ok(true);
                }
            } else {
                // 用户还没在支付宝侧付款时，支付宝固定返回 ACQ.TRADE_NOT_EXIST。
                // 前端支付页每几秒轮询一次，若这里打 WARN/ERROR，一次未付款的支付
                // 就会刷出几十条"错误"日志，把真正的故障淹没。故按 subCode 区分：
                // 交易不存在 = 正常中间态（debug），其余才是真的异常（warn）。
                if ("ACQ.TRADE_NOT_EXIST".equals(resp.getSubCode())) {
                    log.debug("主动查单：交易尚未创建（用户未付款）: orderId={}, payNo={}",
                            orderId, payment.getPayNo());
                } else {
                    log.warn("主动查单未成功: orderId={}, payNo={}, code={}, subMsg={}",
                            orderId, payment.getPayNo(), resp.getCode(), resp.getSubMsg());
                }
            }
        } catch (Exception e) {
            // 支付宝沙箱网关偶发返回 404 HTML（非 JSON），SDK 抛 AlipayApiException。
            // 属于外部偶发，且轮询会重试，不必按 ERROR 打完整堆栈刷屏。
            log.warn("主动查单异常（外部网关，可重试）: orderId={}, msg={}",
                    orderId, e.getMessage());
        }
        return Result.ok(false);
    }

}