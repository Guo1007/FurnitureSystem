package gcy.system.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import gcy.system.entity.dto.Result;
import gcy.system.entity.dto.UserDTO;
import gcy.system.entity.pojo.Order;
import gcy.system.entity.pojo.OrderItem;
import gcy.system.entity.pojo.Payment;
import gcy.system.entity.vo.OrderVO;
import gcy.system.mapper.OrderItemMapper;
import gcy.system.mapper.OrderMapper;
import gcy.system.mapper.PaymentMapper;
import gcy.system.service.IOrderItemService;
import gcy.system.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;


/**
 * 订单项服务实现类，提供订单详情查询等业务逻辑的具体实现。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Service
@RequiredArgsConstructor
public class OrderItemServiceImpl extends ServiceImpl<OrderItemMapper, OrderItem> implements IOrderItemService {

    private final OrderMapper orderMapper;

    private final PaymentMapper paymentMapper;

    /**
     * 根据订单ID获取订单详情，包含订单基本信息及其关联的订单项列表。
     * 实现逻辑：先查询订单并校验是否存在，再校验当前登录用户是否为订单所属用户，
     * 校验通过后查询该订单下的所有订单项，组装为OrderVO后返回成功结果。
     *
     * @param orderId 订单ID，用于查询指定订单及其关联的订单项
     * @return Result 包含订单详情数据的结果对象；若订单不存在或无权限查看则返回失败结果
     */
    @Override
    public Result getOrderDetail(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            return Result.fail("订单不存在");
        }
        UserDTO user = UserHolder.getUser();
        if (!order.getUserId().equals(user.getId())) {
            return Result.fail("无权查看该订单");
        }
        List<OrderItem> items = list(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
        OrderVO vo = OrderVO.from(order, items);
        // 回填支付信息（渠道 + 支付宝交易号），供前端展示
        Payment pay = paymentMapper.selectOne(new LambdaQueryWrapper<Payment>()
                .eq(Payment::getOrderId, orderId)
                .orderByDesc(Payment::getId)
                .last("LIMIT 1"));
        if (pay != null) {
            vo.setChannel(pay.getChannel());
            vo.setTradeNo(pay.getTradeNo());
        }
        return Result.ok(vo);
    }

}
