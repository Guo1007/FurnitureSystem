package gcy.system.entity.pojo;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单实体类，映射数据库 {@code `order`} 表，记录用户订单的完整信息，
 * 包括商品总价、收件人、物流状态、支付/发货/收货时间等。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "`order`")
public class Order {

    /**
     * 订单主键ID
     */
    private Long id;

    /**
     * 下单用户ID
     */
    private Long userId;

    /**
     * 订单总金额
     */
    private BigDecimal totalPrice;

    /**
     * 使用的优惠券模板ID（未使用为空）
     */
    private Long couponId;

    /**
     * 优惠金额（使用优惠券时抵扣的金额）
     */
    private BigDecimal couponDiscount;

    /**
     * 订单状态（如待支付、已支付、已发货、已收货等）
     */
    private int status;

    /**
     * 收件人姓名
     */
    private String consignee;

    /**
     * 收件人联系电话
     */
    private String phone;

    /**
     * 收件地址
     */
    private String address;

    /**
     * 订单备注
     */
    private String remark;

    /**
     * 订单创建时间
     */
    private LocalDateTime createTime;

    /**
     * 支付完成时间
     */
    private LocalDateTime payTime;

    /**
     * 发货时间
     */
    private LocalDateTime shipTime;

    /**
     * 收货/签收时间
     */
    private LocalDateTime receiveTime;

    /**
     * 订单明细列表（非数据库字段，用于前端展示）
     */
    @TableField(exist = false)
    private List<OrderItem> itemList;

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
    private LocalDateTime refundApplyTime;

    /**
     * 管理员同意退款时间
     */
    private LocalDateTime refundApproveTime;

    /**
     * 退款审核完成时间
     */
    private LocalDateTime refundAuditTime;

    /**
     * 管理员处理备注（拒绝/审核不通过原因）
     */
    private String refundHandleRemark;

    /**
     * 用户端逻辑删除标记（0-未删除，1-已删除）
     */
    private Integer userDeleted = 0;

    /**
     * 系统逻辑删除标记（0-未删除，1-已删除），由 MyBatis-Plus 自动处理
     */
    @TableLogic
    private Integer deleted = 0;

}
