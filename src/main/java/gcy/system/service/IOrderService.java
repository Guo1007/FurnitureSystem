package gcy.system.service;


import com.baomidou.mybatisplus.extension.service.IService;
import gcy.system.entity.dto.CartFormDTO;
import gcy.system.entity.dto.Result;
import gcy.system.entity.pojo.Order;


/**
 * 订单服务接口，定义订单相关的业务操作，包括创建订单、查询订单、支付、取消、确认收货及删除等功能。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
public interface IOrderService extends IService<Order> {

    /**
     * 根据购物车表单数据创建订单。
     *
     * @param dto 购物车表单数据传输对象，包含用户选购的商品信息
     * @return 包含创建结果的操作结果对象
     */
    Result createOrder(CartFormDTO dto);

    /**
     * 根据用户ID分页查询该用户的订单列表。
     *
     * @param current 当前页码
     * @param size    每页显示的订单数量
     * @param status  订单状态筛选（可选，支持逗号分隔多状态，如 "6,7,8"；为空时不筛选）
     * @return 包含分页订单数据的操作结果对象
     */
    Result getOrderByUserId(Long current, Long size, String status);

    /**
     * 用户申请退款。
     *
     * @param orderId      订单ID
     * @param refundReason 退款原因
     * @param userId       当前操作用户ID
     * @return 包含申请结果的操作结果对象
     */
    Result applyRefund(Long orderId, String refundReason, Long userId);

    /**
     * 恢复指定订单占用的库存（SKU库存 + 家具总库存），并同步更新Redis缓存。
     * <p>
     * 供退款审核通过和订单取消等场景复用。
     * </p>
     *
     * @param orderId 订单ID
     * @throws gcy.system.exception.BusinessException 当商品不存在或库存恢复失败时抛出
     */
    void restoreStock(Long orderId);

    /**
     * 根据订单ID执行支付操作。
     *
     * @param id 待支付的订单ID
     * @return 包含支付结果的操作结果对象
     */
    Result payOrderById(Long id);

    /**
     * 支付成功确认订单（支付宝异步回调触发）。
     * <p>
     * 与 {@link #payOrderById(Long)} 不同，该方法不依赖当前登录用户，
     * 仅供支付网关回调校验通过后调用，使用 CAS 乐观锁将待支付订单更新为已支付。
     * </p>
     *
     * @param orderId 待确认的订单ID
     * @return 包含确认结果的操作结果对象
     */
    Result confirmPaid(Long orderId);

    /**
     * 根据订单ID取消指定的订单。
     *
     * @param id 待取消的订单ID
     * @return 包含取消结果的操作结果对象
     */
    Result cancelOrder(Long id);

    /**
     * 根据订单ID取消已超时的订单。
     *
     * @param id 超时未支付的订单ID
     * @return 包含取消结果的操作结果对象
     */
    Result cancelTimeoutOrder(Long id);

    /**
     * 根据订单ID确认收货。
     *
     * @param id 待确认收货的订单ID
     * @return 包含确认收货结果的操作结果对象
     */
    Result confirmReceipt(Long id);

    /**
     * 根据订单ID删除当前用户的订单记录。
     *
     * @param id 待删除的订单ID
     * @return 包含删除结果的操作结果对象
     */
    Result deleteMyOrder(Long id);

    /**
     * 归还指定订单已使用的优惠券（退款成功等场景）。
     *
     * @param orderId 订单ID
     */
    void returnCouponForOrder(Long orderId);

}
