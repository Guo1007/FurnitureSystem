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
 * 支付控制器：预下单与支付宝异步回调。
 * <p>
 * 回调由支付宝服务器发起、无法携带登录凭证，故以 {@link Anonymous} 放行匿名访问。
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
     * 预下单：为指定订单生成支付宝付款页面，成功时 data 为支付宝返回的付款表单 HTML。
     */
    @OperationLog("发起支付")
    @Operation(summary = "预下单（生成支付宝付款页面）")
    @PostMapping("/prepay/{orderId}")
    public Result prepay(@Parameter(description = "订单ID") @PathVariable Long orderId) {
        Long userId = UserHolder.getUser().getId();
        return paymentService.createPay(orderId, userId);
    }

    /**
     * 主动向支付宝查单，兜底异步通知延迟/丢失导致的订单状态不一致。
     *
     * @return data 为 true 表示已支付，false 表示仍待支付
     */
    @OperationLog("查询支付状态")
    @Operation(summary = "主动查询订单支付状态")
    @GetMapping("/status/{orderId}")
    public Result queryStatus(@Parameter(description = "订单ID") @PathVariable Long orderId) {
        Long userId = UserHolder.getUser().getId();
        return paymentService.queryPayStatus(orderId, userId);
    }

    /**
     * 支付宝异步回调。匿名放行，返回固定文本 success/failure 供支付宝解析；
     * 验签、幂等、更新订单等处理见 {@link IPaymentService#handleNotify}。
     */
    @Anonymous
    @Operation(summary = "支付宝异步回调", hidden = true)
    @PostMapping("/notify")
    public String notify(@Parameter(hidden = true) HttpServletRequest request) {
        return paymentService.handleNotify(request);
    }

}