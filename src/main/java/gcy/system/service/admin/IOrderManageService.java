package gcy.system.service.admin;

import com.baomidou.mybatisplus.extension.service.IService;
import gcy.system.entity.dto.Result;
import gcy.system.entity.pojo.Order;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * 订单管理服务接口。
 * <p>
 * 提供订单的后台管理功能，包括订单列表查询、发货操作、订单导出，
 * 以及待发货数量统计等业务能力。
 * </p>
 *
 * @author 郭名城
 * @date 2026-07-30
 */
public interface IOrderManageService extends IService<Order> {

    /**
     * 分页查询订单列表。
     * <p>
     * 支持按用户ID、订单状态、联系电话、收货人姓名等条件进行筛选，
     * 返回分页后的订单数据。
     * </p>
     *
     * @param current   当前页码
     * @param size      每页记录数
     * @param userId    用户ID，用于按用户筛选订单，可为null表示不筛选
     * @param status    订单状态，用于按状态筛选订单，可为null表示不筛选
     * @param phone     联系电话，用于按电话模糊匹配订单，可为null表示不筛选
     * @param consignee 收货人姓名，用于按收货人模糊匹配订单，可为null表示不筛选
     * @return 包含分页订单列表及分页信息的Result对象
     */
    Result getOrderList(Integer current, Integer size, Integer userId,
                        String status, String phone, String consignee);

    /**
     * 根据订单ID执行发货操作。
     * <p>
     * 将指定订单的状态更新为已发货，完成订单的发货处理流程。
     * </p>
     *
     * @param id 要发货的订单ID
     * @return 包含发货操作结果的Result对象
     */
    Result shipOrderById(Long id);

    /**
     * 导出订单数据并写入到输出流。
     * <p>
     * 将所有订单数据以CSV或Excel格式写入指定的PrintWriter，
     * 供用户下载或导出。
     * </p>
     *
     * @param writer 用于写入导出数据的PrintWriter输出流
     * @throws IOException 当写入输出流发生I/O错误时抛出
     */
    void exportOrders(PrintWriter writer) throws IOException;

    /**
     * 获取当前待发货的订单数量。
     * <p>
     * 统计系统中所有状态为待发货的订单总数，用于后台管理面板的
     * 待处理事项提示。
     * </p>
     *
     * @return 包含待发货订单数量的Result对象
     */
    Result getPendingShipCount();

    /**
     * 删除单个订单（仅允许已完结订单）。
     * <p>
     * 已取消(4)/已完成(3)/已评价(5)/已退款(8) 为已完结订单，可删除且不动库存；
     * 待支付(0)/已支付(1)/已发货(2) 为在途订单，仍占用库存，拒绝删除并提示走取消/退款流程。
     * </p>
     *
     * @param orderId 订单ID
     * @return 包含删除结果的Result对象
     */
    Result deleteOrderById(Long orderId);

    /**
     * 批量删除订单（仅允许已完结订单）。
     * <p>
     * 逐条校验状态，仅删除已完结订单，在途订单跳过并返回被跳过的数量。
     * </p>
     *
     * @param ids 订单ID列表
     * @return 包含删除结果的Result对象
     */
    Result batchDeleteOrders(java.util.List<Long> ids);

    /**
     * 管理员同意退款申请，将订单状态由申请退款中(6)更新为退款审核中(7)。
     *
     * @param orderId 订单ID
     * @return 包含操作结果的Result对象
     */
    Result approveRefund(Long orderId);

    /**
     * 管理员拒绝退款申请，将订单状态恢复到退款前的原状态。
     *
     * @param orderId 订单ID
     * @param remark  拒绝原因备注
     * @return 包含操作结果的Result对象
     */
    Result rejectRefund(Long orderId, String remark);

    /**
     * 管理员审核退款。
     * <p>
     * 审核通过时订单变为已退款(8)并恢复库存、扣回销量；
     * 审核不通过时订单恢复到退款前的原状态。
     * </p>
     *
     * @param orderId 订单ID
     * @param passed  审核是否通过
     * @param remark  审核备注（不通过时必填）
     * @return 包含操作结果的Result对象
     */
    Result auditRefund(Long orderId, Boolean passed, String remark);

    /**
     * 获取待处理退款的数量（申请退款中 + 退款审核中）。
     *
     * @return 包含待处理退款数量的Result对象
     */
    Result getPendingRefundCount();

    /**
     * 获取退款各状态数量统计（待处理、已退款、全部）。
     *
     * @return 包含退款各状态数量的Result对象
     */
    Result getRefundStatusCounts();

}
