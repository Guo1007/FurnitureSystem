package gcy.system.entity.pojo;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付流水实体类，映射数据库 {@code `payment`} 表，记录每次支付请求的详情，
 * 用于对接支付宝异步回调时实现验签、金额核对与幂等处理。
 *
 * @author 郭名城
 * @date 2026-09-22
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("payment")
public class Payment {

    /**
     * 支付流水主键ID
     */
    private Long id;

    /**
     * 对应订单ID
     */
    private Long orderId;

    /**
     * 下单用户ID
     */
    private Long userId;

    /**
     * 商户业务单号（支付宝 out_trade_no，唯一）
     */
    private String payNo;

    /**
     * 支付宝交易号（回调后回填）
     */
    private String tradeNo;

    /**
     * 支付金额（元）
     */
    private BigDecimal totalAmount;

    /**
     * 支付渠道（如 alipay）
     */
    private String channel = "alipay";

    /**
     * 状态：0-待支付，1-已支付，2-已关闭
     */
    private Integer status = 0;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 支付完成时间
     */
    private LocalDateTime payTime;

    /**
     * 逻辑删除标记（0-未删除，1-已删除）
     */
    @TableLogic
    private Integer deleted = 0;

}