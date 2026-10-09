package gcy.system.controller.admin;

import gcy.system.aspect.OperationLog;
import gcy.system.entity.dto.RefundAuditDTO;
import gcy.system.entity.dto.RefundHandleDTO;
import gcy.system.entity.dto.Result;
import gcy.system.entity.pojo.Payment;
import gcy.system.service.IPaymentService;
import gcy.system.service.admin.IOrderManageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

/**
 * 订单管理控制器。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Tag(name = "订单管理", description = "订单管理相关接口")
@RestController
@RequestMapping("/admin/order")
@RequiredArgsConstructor
public class OrderManageController {

    private final IOrderManageService orderManageService;

    private final IPaymentService paymentService;

    /**
     * 分页查询订单列表，支持用户、状态、收货人手机号与姓名筛选。
     */
    @Operation(summary = "分页查询订单列表")
    @GetMapping("/list")
    public Result getOrderList(@Parameter(description = "当前页码") @RequestParam(defaultValue = "1") Integer current,
                               @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") Integer size,
                               @Parameter(description = "用户ID") @RequestParam(required = false) Integer userId,
                               @Parameter(description = "订单状态(支持逗号分隔)") @RequestParam(required = false) String status,
                               @Parameter(description = "收货人手机号") @RequestParam(required = false) String phone,
                               @Parameter(description = "收货人姓名") @RequestParam(required = false) String consignee) {
        return orderManageService.getOrderList(current, size, userId, status, phone, consignee);
    }

    /**
     * 订单发货。
     */
    @OperationLog("订单发货")
    @Operation(summary = "订单发货")
    @PutMapping("/ship/{orderId}")
    public Result shipOrderById(@Parameter(description = "订单ID") @PathVariable Long orderId) {
        return orderManageService.shipOrderById(orderId);
    }

    /**
     * 导出订单数据为 CSV 文件。
     */
    @Operation(summary = "导出订单数据为CSV")
    @GetMapping("/export")
    public void exportOrders(HttpServletResponse response) throws IOException {
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment;filename=orders.csv");
        response.setCharacterEncoding("UTF-8");
        PrintWriter w = response.getWriter();
        orderManageService.exportOrders(w);
    }

    /**
     * 获取待发货订单数量。
     */
    @Operation(summary = "获取待发货订单数量")
    @GetMapping("/pending-count")
    public Result getPendingCount() {
        return orderManageService.getPendingShipCount();
    }

    /**
     * 删除单个订单。
     */
    @OperationLog("删除订单")
    @Operation(summary = "删除订单")
    @DeleteMapping("/{orderId}")
    public Result deleteOrder(@Parameter(description = "订单ID") @PathVariable Long orderId) {
        return orderManageService.deleteOrderById(orderId);
    }

    /**
     * 批量删除订单。
     */
    @OperationLog("批量删除订单")
    @Operation(summary = "批量删除订单")
    @DeleteMapping("/batch")
    public Result batchDelete(@Parameter(description = "请求体") @RequestBody List<Long> ids) {
        return orderManageService.batchDeleteOrders(ids);
    }

    @OperationLog("查看支付流水")
    @Operation(summary = "查询订单支付流水")
    @GetMapping("/{orderId}/payment")
    public Result getOrderPayment(@Parameter(description = "订单ID") @PathVariable Long orderId) {
        return Result.ok(paymentService.lambdaQuery()
                .eq(Payment::getOrderId, orderId)
                .orderByDesc(Payment::getId)
                .list());
    }

    /**
     * 同意退款申请，订单状态由申请退款中(6)改为退款审核中(7)。
     */
    @OperationLog("同意退款")
    @Operation(summary = "同意退款申请")
    @PutMapping("/refund/approve/{orderId}")
    public Result approveRefund(@Parameter(description = "订单ID") @PathVariable Long orderId) {
        return orderManageService.approveRefund(orderId);
    }

    /**
     * 拒绝退款申请，恢复到退款前原状态并记录拒绝原因。
     */
    @OperationLog("拒绝退款")
    @Operation(summary = "拒绝退款申请")
    @PutMapping("/refund/reject/{orderId}")
    public Result rejectRefund(@Parameter(description = "订单ID") @PathVariable Long orderId,
                               @Parameter(description = "请求体") @Valid @RequestBody RefundHandleDTO dto) {
        return orderManageService.rejectRefund(orderId, dto.getRemark());
    }

    /**
     * 审核退款：通过则订单变已退款(8)并恢复库存，不通过则恢复到退款前原状态。
     */
    @OperationLog("退款审核")
    @Operation(summary = "管理员审核退款")
    @PutMapping("/refund/audit")
    public Result auditRefund(@Parameter(description = "请求体") @Valid @RequestBody RefundAuditDTO dto) {
        return orderManageService.auditRefund(dto.getOrderId(), dto.getPassed(), dto.getRemark());
    }

    /**
     * 获取待处理退款数量（申请退款中+退款审核中）。
     */
    @Operation(summary = "获取待处理退款数量")
    @GetMapping("/refund/pending-count")
    public Result getPendingRefundCount() {
        return orderManageService.getPendingRefundCount();
    }

    /**
     * 获取退款各状态数量（待处理、已退款、全部），用于售后处理页各页签展示。
     */
    @Operation(summary = "获取退款状态数量统计")
    @GetMapping("/refund/status-counts")
    public Result getRefundStatusCounts() {
        return orderManageService.getRefundStatusCounts();
    }

}
