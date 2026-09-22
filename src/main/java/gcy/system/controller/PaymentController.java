package gcy.system.controller;

import gcy.system.aspect.OperationLog;
import gcy.system.entity.dto.Result;
import gcy.system.security.Anonymous;
import gcy.system.service.IPaymentService;
import gcy.system.utils.UserHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 支付控制器，处理支付宝支付相关接口。
 * <p>
 * 提供预下单（生成付款页面）与支付异步回调接口。回调接口需放行匿名访问，
 * 由 {@link Anonymous} 注解标记，支付宝服务器无需携带登录凭证即可回调。
 * </p>
 *
 * @author 郭名城
 * @date 2026-09-22
 */
@Tag(name = "支付", description = "支付相关接口")
@RestController
@RequestMapping("/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final IPaymentService paymentService;

    /**
     * 预下单：为指定订单生成支付宝付款页面。
     *
     * @param orderId 待支付订单ID
     * @return Result 成功时 data 为支付宝返回的付款表单 HTML
     */
    @OperationLog("发起支付")
    @Operation(summary = "预下单（生成支付宝付款页面）")
    @PostMapping("/prepay/{orderId}")
    public Result prepay(@Parameter(description = "订单ID") @PathVariable Long orderId) {
        Long userId = UserHolder.getUser().getId();
        return paymentService.createPay(orderId, userId);
    }

    /**
     * 支付宝异步回调接口（支付宝服务器主动调用）。
     * <p>
     * 该接口匿名放行，返回固定文本 success/failure 供支付宝解析；
     * 详细业务处理（验签、幂等、更新订单）见 {@link IPaymentService#handleNotify}。
     * </p>
     *
     * @param request HTTP 请求，包含支付宝回传的参数与签名
     * @return success / failure
     */
    @Anonymous
    @Operation(summary = "支付宝异步回调", hidden = true)
    @PostMapping("/notify")
    public String notify(@Parameter(hidden = true) HttpServletRequest request) {
        return paymentService.handleNotify(request);
    }

}