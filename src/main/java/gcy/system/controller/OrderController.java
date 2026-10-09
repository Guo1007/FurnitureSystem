package gcy.system.controller;

import gcy.system.aspect.OperationLog;
import gcy.system.entity.dto.CartFormDTO;
import gcy.system.entity.dto.OrderEstimateDTO;
import gcy.system.entity.dto.RefundApplyDTO;
import gcy.system.entity.dto.Result;
import gcy.system.service.IOrderItemService;
import gcy.system.service.IOrderService;
import gcy.system.utils.UserHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 订单控制器，处理订单相关的HTTP请求。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Tag(name = "订单", description = "订单相关接口")
@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {

    private final IOrderService orderService;

    private final IOrderItemService orderItemService;

    /**
     * 创建订单。
     */
    @OperationLog("创建订单")
    @Operation(summary = "创建订单")
    @PostMapping("/create")
    public Result createOrder(@Parameter(description = "请求体") @Valid @RequestBody CartFormDTO dto) {
        return orderService.createOrder(dto);
    }

    /**
     * 下单试算：只算钱，不下单。
     * <p>
     * 前端各处取金额均走此接口，抵扣算法只在此实现；
     * 无副作用，不加 {@code @OperationLog}（该注解用于写操作）。
     */
    @Operation(summary = "下单试算（算价/选券预览）")
    @PostMapping("/estimate")
    public Result estimate(@Parameter(description = "请求体") @Valid @RequestBody OrderEstimateDTO dto) {
        return orderService.estimate(dto);
    }

    /**
     * 分页查询当前用户订单，status 支持逗号分隔多状态（如 "6,7,8"）。
     */
    @Operation(summary = "获取当前用户订单列表")
    @GetMapping("/list")
    public Result getOrderList(@Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
                               @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") Integer size,
                               @Parameter(description = "状态筛选(逗号分隔)") @RequestParam(required = false) String status) {
        return orderService.getOrderByUserId(page.longValue(), size.longValue(), status);
    }

    /**
     * 查询订单详情。
     */
    @Operation(summary = "获取订单详情")
    @GetMapping("/detail/{orderId}")
    public Result getOrderDetail(@Parameter(description = "订单ID") @PathVariable Long orderId) {
        return orderItemService.getOrderDetail(orderId);
    }

    /**
     * 取消订单。
     */
    @OperationLog("取消订单")
    @Operation(summary = "取消订单")
    @PutMapping("/cancel/{orderId}")
    public Result cancelOrder(@Parameter(description = "订单ID") @PathVariable Long orderId) {
        return orderService.cancelOrder(orderId);
    }

    /**
     * 确认收货。
     */
    @OperationLog("确认收货")
    @Operation(summary = "确认收货")
    @PutMapping("/confirm/{orderId}")
    public Result confirmReceipt(@Parameter(description = "订单ID") @PathVariable Long orderId) {
        return orderService.confirmReceipt(orderId);
    }

    /**
     * 删除订单。
     */
    @OperationLog("删除订单")
    @Operation(summary = "删除订单")
    @DeleteMapping("/{orderId}")
    public Result deleteOrder(@Parameter(description = "订单ID") @PathVariable Long orderId) {
        return orderService.deleteMyOrder(orderId);
    }

    /**
     * 申请退款：已支付/已发货/已完成/已评价的订单均可申请，申请后进入退款审核流程。
     */
    @OperationLog("申请退款")
    @Operation(summary = "申请退款")
    @PostMapping("/refund/apply")
    public Result applyRefund(@Parameter(description = "请求体") @Valid @RequestBody RefundApplyDTO dto) {
        Long userId = UserHolder.getUser().getId();
        return orderService.applyRefund(dto.getOrderId(), dto.getRefundReason(), userId);
    }

    /**
     * 撤销退款申请：仅「申请退款中」可撤销，回退到申请前状态；管理员已受理的不可撤销。
     */
    @OperationLog("撤销退款申请")
    @Operation(summary = "撤销退款申请")
    @PostMapping("/refund/cancel/{orderId}")
    public Result cancelRefund(@Parameter(description = "订单ID") @PathVariable Long orderId) {
        Long userId = UserHolder.getUser().getId();
        return orderService.cancelRefund(orderId, userId);
    }

}
