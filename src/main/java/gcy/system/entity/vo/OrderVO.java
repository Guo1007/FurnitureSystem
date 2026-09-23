package gcy.system.entity.vo;

import cn.hutool.core.bean.BeanUtil;
import com.fasterxml.jackson.annotation.JsonFormat;
import gcy.system.entity.pojo.Order;
import gcy.system.entity.pojo.OrderItem;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 订单视图对象，用于向前端展示订单的完整信息，包括订单基本信息、收货人信息、时间节点以及订单明细列表。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderVO {

    /**
     * 订单ID
     */
    private String id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名（管理端展示用）
     */
    private String userName;

    /**
     * 订单总价
     */
    private BigDecimal totalPrice;

    /**
     * 使用的优惠券模板ID（未用为空）
     */
    private Long couponId;

    /**
     * 优惠金额
     */
    private BigDecimal couponDiscount;

    /**
     * 订单状态
     */
    private int status;

    /**
     * 收货人姓名
     */
    private String consignee;

    /**
     * 收货人联系电话
     */
    private String phone;

    /**
     * 收货地址
     */
    private String address;

    /**
     * 订单备注
     */
    private String remark;

    /**
     * 订单创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /**
     * 支付时间
     */
    private LocalDateTime payTime;

    /**
     * 支付渠道（如 alipay）
     */
    private String channel;

    /**
     * 支付宝交易号（trade_no）
     */
    private String tradeNo;

    /**
     * 发货时间
     */
    private LocalDateTime shipTime;

    /**
     * 订单明细列表
     */
    private List<OrderItemVO> itemList;

    /**
     * 退款原因
     */
    private String refundReason;

    /**
     * 退款前原状态（拒绝/审核不通过时恢复）
     */
    private Integer refundPrevStatus;

    /**
     * 退款申请时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime refundApplyTime;

    /**
     * 管理员同意退款时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime refundApproveTime;

    /**
     * 退款审核完成时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime refundAuditTime;

    /**
     * 管理员处理备注（拒绝/审核不通过原因）
     */
    private String refundHandleRemark;

    /**
     * 将订单实体和订单明细列表转换为订单视图对象。
     *
     * @param order 订单实体
     * @param items 订单明细列表
     * @return 组装完成的订单视图对象
     */
    public static OrderVO from(Order order, List<OrderItem> items) {
        OrderVO vo = new OrderVO();
        BeanUtil.copyProperties(order, vo);
        vo.setId(String.valueOf(order.getId()));
        vo.setItemList(items.stream().map(OrderItemVO::from).collect(Collectors.toList()));
        return vo;
    }
}