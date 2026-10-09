package gcy.system.service;


import com.baomidou.mybatisplus.extension.service.IService;
import gcy.system.entity.dto.CartFormDTO;
import gcy.system.entity.dto.OrderEstimateDTO;
import gcy.system.entity.dto.Result;
import gcy.system.entity.pojo.Order;


/**
 * 订单服务接口。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
public interface IOrderService extends IService<Order> {

    /**
     * 创建订单。
     */
    Result createOrder(CartFormDTO dto);

    /**
     * 下单试算：只算钱，不下单、不扣库存、不核销券。
     * <p>
     * 与 {@link #createOrder} 共用同一段算价逻辑，保证试算与实收一致；
     * 前端优惠金额一律以本接口为准，不保留抵扣算法。
     */
    Result estimate(OrderEstimateDTO dto);

    /**
     * 分页查询指定用户的订单列表。
     *
     * @param status 订单状态筛选，支持逗号分隔多状态（如 "6,7,8"）；为空时不筛选
     */
    Result getOrderByUserId(Long current, Long size, String status);

    /**
     * 用户申请退款。
     */
    Result applyRefund(Long orderId, String refundReason, Long userId);

    /**
     * 用户撤销退款申请，订单回退到申请退款前的状态。
     */
    Result cancelRefund(Long orderId, Long userId);

    /**
     * 恢复订单占用的库存（SKU库存 + 家具总库存）并同步更新 Redis 缓存，供退款审核、订单取消复用。
     *
     * @throws gcy.system.exception.BusinessException 商品不存在或库存恢复失败时抛出
     */
    void restoreStock(Long orderId);

    /**
     * 支付成功确认订单（支付宝异步回调触发）。
     * <p>
     * 不依赖当前登录用户，仅供支付网关验签与金额核对通过后调用，用 CAS 乐观锁将待支付更新为已支付。
     * 订单迁移至「已支付」必须以 payment 表已成交流水或支付宝回调为前提，不得绕过支付网关直接改单。
     */
    Result confirmPaid(Long orderId);

    /**
     * 取消订单。
     */
    Result cancelOrder(Long id);

    /**
     * 取消超时未支付的订单。
     */
    Result cancelTimeoutOrder(Long id);

    /**
     * 确认收货。
     */
    Result confirmReceipt(Long id);

    /**
     * 自动确认收货，供定时任务调用：不校验操作者身份，仅要求订单状态为已发货。
     * <p>
     * 避免发货后用户不点确认，订单永久停留在已发货而不结算、不能评价、售后窗口无法关闭。
     */
    Result autoConfirmReceipt(Long orderId);

    /**
     * 删除当前用户的订单记录。
     */
    Result deleteMyOrder(Long id);

    /**
     * 归还订单已使用的优惠券，用于退款成功等场景。
     */
    void returnCouponForOrder(Long orderId);

}
