package gcy.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import gcy.system.entity.dto.Result;
import gcy.system.entity.pojo.Payment;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 支付服务接口，封装支付宝支付的核心业务：预下单与异步回调处理。
 *
 * @author 郭名城
 * @date 2026-09-22
 */
public interface IPaymentService extends IService<Payment> {

    /**
     * 预下单：为指定订单创建支付页面（用户跳转支付宝完成付款）。
     *
     * @return 成功时 data 为支付宝返回的自动提交 HTML 表单
     */
    Result createPay(Long orderId, Long userId);

    /**
     * 处理支付宝异步回调通知（验签 + 金额核对 + 幂等 + 更新订单状态）。
     *
     * @return 透传给支付宝的文本结果：success / failure
     */
    String handleNotify(HttpServletRequest request);

    /**
     * 主动查询订单支付状态（对账兜底）。
     * <p>
     * 调用支付宝 {@code alipay.trade.query} 查真实交易状态，支付宝侧已成功则本地落库并确认支付，
     * 规避异步通知延迟/丢失导致的订单状态不一致。
     * </p>
     *
     * @return data 为 true 表示已支付，false 表示仍待支付
     */
    Result queryPayStatus(Long orderId, Long userId);

}