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
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

/**
 * 订单管理控制器
 * <p>
 * 提供订单的查询、发货、导出、删除等后台管理接口，
 * 所有接口均挂载在 /admin/order 路径下。
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
     * 分页查询订单列表
     * <p>
     * 支持按用户ID、订单状态、收货人手机号、收货人姓名等条件进行筛选。
     *
     * @param current   当前页码，默认值为 1
     * @param size      每页大小，默认值为 10
     * @param userId    用户ID，可选，用于按用户筛选订单
     * @param status    订单状态，可选，用于按状态筛选订单
     * @param phone     收货人手机号，可选，用于模糊查询
     * @param consignee 收货人姓名，可选，用于模糊查询
     * @return 包含分页订单列表数据的结果对象
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
     * 根据订单ID执行发货操作
     *
     * @param orderId 要发货的订单ID，通过路径变量传入
     * @return 包含发货操作结果的结果对象
     */
    @OperationLog("订单发货")
    @Operation(summary = "订单发货")
    @PutMapping("/ship/{orderId}")
    public Result shipOrderById(@Parameter(description = "订单ID") @PathVariable Long orderId) {
        return orderManageService.shipOrderById(orderId);
    }

    /**
     * 导出订单数据为CSV文件
     * <p>
     * 设置响应头为CSV格式，将订单数据以CSV形式写入HTTP响应输出流，
     * 触发浏览器自动下载 orders.csv 文件。
     *
     * @param response HTTP 响应对象，用于设置响应头并输出CSV文件流
     * @throws IOException 向响应流中写入数据时可能抛出的IO异常
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
     * 获取待发货订单数量
     *
     * @return 包含待发货订单总数统计的结果对象
     */
    @Operation(summary = "获取待发货订单数量")
    @GetMapping("/pending-count")
    public Result getPendingCount() {
        return orderManageService.getPendingShipCount();
    }

    /**
     * 根据订单ID删除单个订单
     *
     * @param orderId 要删除的订单ID，通过路径变量传入
     * @return 删除成功时返回包含成功提示的结果对象，否则返回包含失败提示的结果对象
     */
    @OperationLog("删除订单")
    @Operation(summary = "删除订单")
    @DeleteMapping("/{orderId}")
    public Result deleteOrder(@Parameter(description = "订单ID") @PathVariable Long orderId) {
        return orderManageService.deleteOrderById(orderId);
    }

    /**
     * 批量删除订单
     *
     * @param ids 要删除的订单ID列表，通过请求体以JSON数组格式传入
     * @return 批量删除成功时返回包含成功提示的结果对象，否则返回包含失败提示的结果对象
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
     * 管理员同意退款申请
     * <p>
     * 将订单状态由申请退款中(6)更新为退款审核中(7)。
     *
     * @param orderId 订单ID
     * @return 包含操作结果的结果对象
     */
    @OperationLog("同意退款")
    @Operation(summary = "同意退款申请")
    @PutMapping("/refund/approve/{orderId}")
    public Result approveRefund(@Parameter(description = "订单ID") @PathVariable Long orderId) {
        return orderManageService.approveRefund(orderId);
    }

    /**
     * 管理员拒绝退款申请
     * <p>
     * 将订单状态恢复到退款前的原状态，并记录拒绝原因。
     *
     * @param orderId 订单ID
     * @param dto     退款处理请求体，包含拒绝原因
     * @return 包含操作结果的结果对象
     */
    @OperationLog("拒绝退款")
    @Operation(summary = "拒绝退款申请")
    @PutMapping("/refund/reject/{orderId}")
    public Result rejectRefund(@Parameter(description = "订单ID") @PathVariable Long orderId,
                               @Parameter(description = "请求体") @RequestBody RefundHandleDTO dto) {
        return orderManageService.rejectRefund(orderId, dto.getRemark());
    }

    /**
     * 管理员审核退款
     * <p>
     * 审核通过时订单变为已退款(8)并恢复库存；审核不通过时恢复到退款前原状态。
     *
     * @param dto 退款审核请求体，包含订单ID、审核结果和备注
     * @return 包含操作结果的结果对象
     */
    @OperationLog("退款审核")
    @Operation(summary = "管理员审核退款")
    @PutMapping("/refund/audit")
    public Result auditRefund(@Parameter(description = "请求体") @RequestBody RefundAuditDTO dto) {
        return orderManageService.auditRefund(dto.getOrderId(), dto.getPassed(), dto.getRemark());
    }

    /**
     * 获取待处理退款数量（申请退款中 + 退款审核中）
     *
     * @return 包含待处理退款数量统计的结果对象
     */
    @Operation(summary = "获取待处理退款数量")
    @GetMapping("/refund/pending-count")
    public Result getPendingRefundCount() {
        return orderManageService.getPendingRefundCount();
    }

    /**
     * 获取退款各状态数量统计
     * <p>
     * 返回待处理（申请退款中+退款审核中）、已退款、全部退款的数量，
     * 用于售后处理页各页签的数量展示。
     *
     * @return 包含退款各状态数量统计的结果对象
     */
    @Operation(summary = "获取退款状态数量统计")
    @GetMapping("/refund/status-counts")
    public Result getRefundStatusCounts() {
        return orderManageService.getRefundStatusCounts();
    }

}
