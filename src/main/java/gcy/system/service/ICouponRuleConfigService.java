package gcy.system.service;

import gcy.system.entity.dto.Result;
import gcy.system.entity.vo.CouponRuleVO;

/**
 * 优惠券叠加规则配置服务。
 * <p>
 * 供管理端读取与保存规则，并为下单流程提供「最大叠加张数」与「总抵扣上限比例」。
 * 规则读取以 Redis 缓存为主，保存时失效缓存；缓存缺失或异常时回落到内置默认值，
 * 保证配置表尚未创建时下单流程不受影响。
 * </p>
 *
 * @author 郭名城
 * @date 2026-09-30
 */
public interface ICouponRuleConfigService {

    /**
     * 查询全部规则配置（供管理端展示）。
     *
     * @return 包含规则列表的结果对象
     */
    Result listRules();

    /**
     * 保存规则配置。
     *
     * @param ruleKey   规则标识
     * @param ruleValue 规则值
     * @param enabled   是否启用
     * @return 保存结果
     */
    Result saveRule(String ruleKey, String ruleValue, Boolean enabled);

    /**
     * 获取单笔订单最多可叠加的优惠券张数。
     * 未配置、未启用或读取异常时返回默认值 {@link #DEFAULT_MAX_STACK_COUNT}。
     *
     * @return 最大叠加张数
     */
    int getMaxStackCount();

    /**
     * 获取多张券叠加时总抵扣占商品总额的上限比例（0~1 之间的小数）。
     * 未配置、未启用或读取异常时返回默认值 {@link #DEFAULT_MAX_DISCOUNT_RATIO}。
     *
     * @return 抵扣上限比例，如 0.8 表示最多抵扣商品总额的 80%
     */
    java.math.BigDecimal getMaxDiscountRatio();

    /**
     * 供用户端选券弹窗使用的叠加规则快照。
     *
     * @return 包含 maxStackCount、maxDiscountRatio 的 Map
     */
    java.util.Map<String, Object> rulesForUser();

    /**
     * 最大叠加张数默认值（配置缺失时的兜底）。
     */
    int DEFAULT_MAX_STACK_COUNT = 3;

    /**
     * 总抵扣上限比例默认值（配置缺失时的兜底）：0.8 表示最多抵扣 80%。
     */
    java.math.BigDecimal DEFAULT_MAX_DISCOUNT_RATIO = new java.math.BigDecimal("0.8");
}
