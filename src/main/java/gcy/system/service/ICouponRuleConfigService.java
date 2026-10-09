package gcy.system.service;

import gcy.system.entity.dto.Result;
import gcy.system.entity.vo.CouponRuleVO;

/**
 * 优惠券叠加规则配置服务，为下单流程提供最大叠加张数与总抵扣上限比例。
 * <p>
 * 读取走 Redis 缓存，保存时失效缓存；缓存缺失或异常回落内置默认值，
 * 使配置表尚未创建时下单流程仍可用。
 *
 * @author 郭名城
 * @date 2026-09-30
 */
public interface ICouponRuleConfigService {

    /**
     * 查询全部规则配置（供管理端展示）。
     */
    Result listRules();

    /**
     * 保存规则配置。
     */
    Result saveRule(String ruleKey, String ruleValue, Boolean enabled);

    /**
     * 单笔订单最多可叠加的优惠券张数；未配置、未启用或读取异常时返回 {@link #DEFAULT_MAX_STACK_COUNT}。
     */
    int getMaxStackCount();

    /**
     * 多张券叠加时总抵扣占商品总额的上限比例（0~1 的小数）；
     * 未配置、未启用或读取异常时返回 {@link #DEFAULT_MAX_DISCOUNT_RATIO}（如 0.8 表示最多抵扣 80%）。
     */
    java.math.BigDecimal getMaxDiscountRatio();

    /**
     * 用户端选券弹窗用的规则快照，含 maxStackCount、maxDiscountRatio。
     */
    java.util.Map<String, Object> rulesForUser();

    /**
     * 最大叠加张数默认值（配置缺失时的兜底）。
     */
    int DEFAULT_MAX_STACK_COUNT = 3;

    /**
     * 总抵扣上限比例默认值（配置缺失时的兜底）：0.2 表示最多抵扣 20%，即用户实付不低于商品总额的 80%。
     */
    java.math.BigDecimal DEFAULT_MAX_DISCOUNT_RATIO = new java.math.BigDecimal("0.2");
}
