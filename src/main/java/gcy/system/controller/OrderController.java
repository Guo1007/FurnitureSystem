package gcy.system.controller;

import gcy.system.aspect.OperationLog;
import gcy.system.entity.dto.CartFormDTO;
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
 * <p>
 * 提供订单的创建、查询列表、查看详情、支付、取消、确认收货及删除等功能。
 * 所有接口均返回统一的 {@link Result} 响应格式。
 * </p>
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
     * <p>
     * 接收购物车表单数据，调用订单服务生成新的订单记录。
     * </p>
     *
     * @param dto 购物车表单数据传输对象，包含用户选购的商品信息
     * @return 包含创建结果的统一响应对象
     */
    @OperationLog("创建订单")
    @Operation(summary = "创建订单")
    @PostMapping("/create")
    public Result createOrder(@Parameter(description = "请求体") @Valid @RequestBody CartFormDTO dto) {
        return orderService.createOrder(dto);
    }

    /**
     * 获取当前用户的订单列表，支持分页查询和状态筛选。
     *
     * @param page   页码，默认为第1页
     * @param size   每页条数，默认为10条
     * @param status 状态筛选（可选，支持逗号分隔多状态，如 "6,7,8"）
     * @return 包含当前用户分页订单数据的统一响应对象
     */
    @Operation(summary = "获取当前用户订单列表")
    @GetMapping("/list")
    public Result getOrderList(@Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
                               @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") Integer size,
                               @Parameter(description = "状态筛选(逗号分隔)") @RequestParam(required = false) String status) {
        return orderService.getOrderByUserId(page.longValue(), size.longValue(), status);
    }

    /**
     * 根据订单ID获取订单详情。
     *
     * @param orderId 订单ID，用于标识唯一订单
     * @return 包含指定订单详细信息的统一响应对象
     */
    @Operation(summary = "获取订单详情")
    @GetMapping("/detail/{orderId}")
    public Result getOrderDetail(@Parameter(description = "订单ID") @PathVariable Long orderId) {
        return orderItemService.getOrderDetail(orderId);
    }

    /**
     * 支付指定订单。
     * <p>
     * 将订单状态变更为已支付。
     * </p>
     *
     * @param orderId 待支付的订单ID
     * @return 包含支付操作结果的统一响应对象
     */
    @OperationLog("支付订单")
    @Operation(summary = "支付订单")
    @PutMapping("/pay/{orderId}")
    public Result payOrder(@Parameter(description = "订单ID") @PathVariable Long orderId) {
        return orderService.payOrderById(orderId);
    }

    /**
     * 取消指定订单。
     *
     * @param orderId 待取消的订单ID
     * @return 包含取消操作结果的统一响应对象
     */
    @OperationLog("取消订单")
    @Operation(summary = "取消订单")
    @PutMapping("/cancel/{orderId}")
    public Result cancelOrder(@Parameter(description = "订单ID") @PathVariable Long orderId) {
        return orderService.cancelOrder(orderId);
    }

    /**
     * 确认收货。
     * <p>
     * 将订单状态变更为已完成，表示用户已收到商品。
     * </p>
     *
     * @param orderId 待确认收货的订单ID
     * @return 包含确认收货操作结果的统一响应对象
     */
    @OperationLog("确认收货")
    @Operation(summary = "确认收货")
    @PutMapping("/confirm/{orderId}")
    public Result confirmReceipt(@Parameter(description = "订单ID") @PathVariable Long orderId) {
        return orderService.confirmReceipt(orderId);
    }

    /**
     * 删除指定订单。
     *
     * @param orderId 待删除的订单ID
     * @return 包含删除操作结果的统一响应对象
     */
    @OperationLog("删除订单")
    @Operation(summary = "删除订单")
    @DeleteMapping("/{orderId}")
    public Result deleteOrder(@Parameter(description = "订单ID") @PathVariable Long orderId) {
        return orderService.deleteMyOrder(orderId);
    }

    /**
     * 用户申请退款。
     * <p>
     * 已支付/已发货/已完成/已评价的订单均可申请，申请后订单进入退款审核流程。
     * </p>
     *
     * @param dto 退款申请请求体，包含订单ID和退款原因
     * @return 包含申请结果的统一响应对象
     */
    @OperationLog("申请退款")
    @Operation(summary = "申请退款")
    @PostMapping("/refund/apply")
    public Result applyRefund(@Parameter(description = "请求体") @Valid @RequestBody RefundApplyDTO dto) {
        Long userId = UserHolder.getUser().getId();
        return orderService.applyRefund(dto.getOrderId(), dto.getRefundReason(), userId);
    }

}
