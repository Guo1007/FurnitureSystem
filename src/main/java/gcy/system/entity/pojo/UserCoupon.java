package gcy.system.entity.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户领取优惠券记录实体，映射 user_coupon 表。
 *
 * @author 郭名城
 * @date 2026-09-20
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_coupon")
public class UserCoupon {

    /**
     * 记录ID，自增主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 优惠券模板ID
     */
    private Long couponId;

    /**
     * 状态：0-未用，1-已用，2-已过期
     */
    private Integer status;

    /**
     * 过期时间（领券时按有效期模式算好，可空）
     */
    private LocalDateTime expireTime;

    /**
     * 领取时间
     */
    private LocalDateTime gotTime;

    /**
     * 使用时间（可空）
     */
    private LocalDateTime useTime;

    /**
     * 使用的订单ID（可空）
     */
    private Long orderId;
}