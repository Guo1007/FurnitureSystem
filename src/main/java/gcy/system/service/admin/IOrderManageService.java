package gcy.system.service.admin;

import com.baomidou.mybatisplus.extension.service.IService;
import gcy.system.entity.dto.Result;
import gcy.system.entity.pojo.Order;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * 订单管理服务接口。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
public interface IOrderManageService extends IService<Order> {

    /**
     * 分页查询订单列表，各筛选条件为 null 时不过滤。
     */
    Result getOrderList(Integer current, Integer size, Integer userId,
                        String status, String phone, String consignee);

    /**
     * 订单发货。
     */
    Result shipOrderById(Long id);

    /**
     * 导出全部订单数据（CSV/Excel 格式）写入输出流。
     *
     * @throws IOException 写入输出流发生 I/O 错误时抛出
     */
    void exportOrders(PrintWriter writer) throws IOException;

    /**
     * 统计待发货订单数量。
     */
    Result getPendingShipCount();

    /**
     * 删除单个订单，仅限已完结订单。
     * <p>
     * 已取消(4)/已完成(3)/已评价(5)/已退款(8) 可删除且不动库存；
     * 待支付(0)/已支付(1)/已发货(2) 在途仍占用库存，拒绝删除并提示走取消/退款流程。
     */
    Result deleteOrderById(Long orderId);

    /**
     * 批量删除订单，仅限已完结订单；在途订单跳过并返回跳过数量。
     */
    Result batchDeleteOrders(java.util.List<Long> ids);

    /**
     * 同意退款申请：订单状态由申请退款中(6)更新为退款审核中(7)。
     */
    Result approveRefund(Long orderId);

    /**
     * 拒绝退款申请：订单恢复到退款前的原状态。
     */
    Result rejectRefund(Long orderId, String remark);

    /**
     * 审核退款：通过则订单变为已退款(8)并恢复库存、扣回销量，不通过则恢复到退款前的原状态。
     *
     * @param remark 审核备注，不通过时必填
     */
    Result auditRefund(Long orderId, Boolean passed, String remark);

    /**
     * 统计待处理退款数量（申请退款中 + 退款审核中）。
     */
    Result getPendingRefundCount();

    /**
     * 统计退款各状态数量（待处理、已退款、全部）。
     */
    Result getRefundStatusCounts();
}
