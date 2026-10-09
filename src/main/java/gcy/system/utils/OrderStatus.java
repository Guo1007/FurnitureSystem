package gcy.system.utils;

import lombok.Getter;

/**
 * 订单状态枚举：code 用于数据库存储，desc 用于前端展示。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Getter
public enum OrderStatus {

    PENDING_PAYMENT(0, "待支付"),

    PAID(1, "已支付"),

    SHIPPED(2, "已发货"),

    COMPLETED(3, "已完成"),

    CANCELLED(4, "已取消"),

    REVIEWED(5, "已评价"),

    REFUND_APPLYING(6, "申请退款中"),

    REFUND_AUDITING(7, "退款审核中"),

    REFUNDED(8, "已退款");

    /**
     * 订单状态编码
     */
    private final int code;

    /**
     * 订单状态中文描述
     */
    private final String desc;

    /**
     * 构造函数。
     */
    OrderStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 按编码取状态，编码无效时抛 IllegalArgumentException。
     */
    public static OrderStatus fromCode(int code) {
        for (OrderStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("无效的订单状态码: " + code);
    }
}
