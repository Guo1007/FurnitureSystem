package gcy.system.entity.pojo;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 优惠券叠加规则配置实体。
 * <p>
 * 按规则标识独立一行（rule_key），用于把下单时的券叠加限制从代码中抽离为后台可配置项，
 * 避免调整规则需要改代码重新发版。当前支持：
 * </p>
 * <ul>
 *   <li>{@code max_stack_count} —— 单笔订单最多可叠加使用的优惠券张数</li>
 *   <li>{@code max_discount_ratio} —— 多张券叠加时总抵扣占商品总额的上限百分比</li>
 * </ul>
 *
 * @author 郭名城
 * @date 2026-09-30
 */
@Data
@NoArgsConstructor
@TableName("coupon_rule_config")
public class CouponRuleConfig {

    /**
     * 配置ID（自增主键）
     */
    private Long id;

    /**
     * 规则标识：max_stack_count-最大叠加张数、max_discount_ratio-总抵扣上限百分比
     */
    private String ruleKey;

    /**
     * 规则值（字符串存储，按规则语义解析）
     */
    private String ruleValue;

    /**
     * 是否启用该限制(0否1是)
     */
    private Integer enabled;

    /**
     * 规则说明（后台展示用）
     */
    private String remark;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
