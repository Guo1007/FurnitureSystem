package gcy.system.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import gcy.system.entity.dto.Result;
import gcy.system.entity.pojo.CouponRuleConfig;
import gcy.system.entity.vo.CouponRuleVO;
import gcy.system.mapper.CouponRuleConfigMapper;
import gcy.system.service.ICouponRuleConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 优惠券叠加规则配置服务实现。
 * <p>
 * 规则以 Redis Hash 缓存（key = {@code coupon:rule:config}），保存时整键失效。
 * 下单是高频路径，不能每次查库；而配置变更频率极低，缓存收益明显。
 * 缓存或配置表缺失时一律回落到内置默认值，绝不因读不到配置而阻断下单。
 * </p>
 *
 * @author 郭名城
 * @date 2026-09-30
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponRuleConfigServiceImpl
        extends ServiceImpl<CouponRuleConfigMapper, CouponRuleConfig> implements ICouponRuleConfigService {

    /**
     * 规则缓存在 Redis 中的 key。
     */
    private static final String RULE_CACHE_KEY = "coupon:rule:config";

    /**
     * 规则缓存有效期（分钟）。配置变更时会主动失效，此处仅作兜底。
     */
    private static final long RULE_CACHE_TTL_MINUTES = 60L;

    /**
     * 规则标识：单笔订单最多可叠加使用的优惠券张数。
     */
    private static final String KEY_MAX_STACK_COUNT = "max_stack_count";

    /**
     * 规则标识：多张券叠加时总抵扣占商品总额的上限百分比。
     */
    private static final String KEY_MAX_DISCOUNT_RATIO = "max_discount_ratio";

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 读取全部规则（缓存优先），返回 ruleKey -> CouponRuleConfig。
     * 任何异常都降级为空 Map，由调用方使用默认值。
     */
    private Map<String, CouponRuleConfig> loadRuleMap() {
        Map<String, CouponRuleConfig> result = new LinkedHashMap<>();
        try {
            Map<Object, Object> cached = stringRedisTemplate.opsForHash().entries(RULE_CACHE_KEY);
            if (cached != null && !cached.isEmpty()) {
                for (Map.Entry<Object, Object> e : cached.entrySet()) {
                    String encoded = String.valueOf(e.getValue());
                    int sep = encoded.indexOf('|');
                    CouponRuleConfig c = new CouponRuleConfig();
                    c.setRuleKey(String.valueOf(e.getKey()));
                    if (sep >= 0) {
                        c.setEnabled("1".equals(encoded.substring(0, sep)) ? 1 : 0);
                        c.setRuleValue(encoded.substring(sep + 1));
                    } else {
                        c.setEnabled(1);
                        c.setRuleValue(encoded);
                    }
                    result.put(c.getRuleKey(), c);
                }
                return result;
            }
        } catch (Exception e) {
            log.warn("读取优惠券规则缓存失败，降级查库", e);
        }
        try {
            List<CouponRuleConfig> list = list(new LambdaQueryWrapper<CouponRuleConfig>());
            Map<String, String> toCache = new LinkedHashMap<>();
            for (CouponRuleConfig c : list) {
                if (c.getRuleKey() == null) {
                    continue;
                }
                result.put(c.getRuleKey(), c);
                toCache.put(c.getRuleKey(), encode(c));
            }
            if (!toCache.isEmpty()) {
                try {
                    stringRedisTemplate.opsForHash().putAll(RULE_CACHE_KEY, toCache);
                    stringRedisTemplate.expire(RULE_CACHE_KEY, RULE_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
                } catch (Exception e) {
                    log.warn("写入优惠券规则缓存失败（不影响本次使用）", e);
                }
            }
        } catch (Exception e) {
            // 表不存在等场景：静默回落默认值，下单不受影响
            log.warn("读取优惠券规则配置失败，将使用默认规则", e);
        }
        return result;
    }

    /**
     * 把「是否启用」与规则值一起编码进缓存：{@code 1|80} 表示启用且值为 80。
     */
    private String encode(CouponRuleConfig c) {
        boolean on = c.getEnabled() == null || c.getEnabled() == 1;
        return (on ? "1" : "0") + "|" + (c.getRuleValue() == null ? "" : c.getRuleValue());
    }

    /**
     * 取某条规则的生效值：存在且启用则返回解析后的整数，否则返回 null（表示使用默认值）。
     */
    private Integer effectiveIntValue(Map<String, CouponRuleConfig> map, String key, int min, int max) {
        if (map == null) {
            return null;
        }
        CouponRuleConfig c = map.get(key);
        if (c == null || c.getEnabled() == null || c.getEnabled() != 1) {
            return null;
        }
        String v = c.getRuleValue();
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            int n = Integer.parseInt(v.trim());
            if (n < min || n > max) {
                log.warn("优惠券规则 {} 取值 {} 超出范围 {}~{}，回落默认值", key, n, min, max);
                return null;
            }
            return n;
        } catch (NumberFormatException e) {
            log.warn("优惠券规则 {} 取值 {} 非整数，回落默认值", key, v);
            return null;
        }
    }

    @Override
    public Result listRules() {
        List<CouponRuleVO> vos = new ArrayList<>();
        Map<String, CouponRuleConfig> map;
        try {
            // 展示用直接读库，保证看到的是最新值（缓存可能尚未失效）
            map = new LinkedHashMap<>();
            for (CouponRuleConfig c : list(new LambdaQueryWrapper<CouponRuleConfig>())) {
                if (c.getRuleKey() != null) {
                    map.put(c.getRuleKey(), c);
                }
            }
        } catch (Exception e) {
            log.warn("读取优惠券规则配置失败，返回空列表（前端将展示默认值）", e);
            map = new LinkedHashMap<>();
        }
        vos.add(buildVO(map, KEY_MAX_STACK_COUNT, "最大叠加张数", "单笔订单最多可同时使用的优惠券张数",
                String.valueOf(DEFAULT_MAX_STACK_COUNT), 1, 10, "张"));
        vos.add(buildVO(map, KEY_MAX_DISCOUNT_RATIO, "总抵扣上限",
                "多张券叠加时，总抵扣金额占商品总额的上限百分比；设为 100 表示不限制",
                String.valueOf(DEFAULT_MAX_DISCOUNT_RATIO.multiply(new BigDecimal("100")).intValue()),
                1, 100, "%"));
        return Result.ok(vos);
    }

    private CouponRuleVO buildVO(Map<String, CouponRuleConfig> map, String key, String name, String remark,
                                 String defaultValue, int min, int max, String unit) {
        CouponRuleConfig c = map.get(key);
        String value = (c != null && c.getRuleValue() != null) ? c.getRuleValue() : defaultValue;
        Boolean enabled = c == null ? Boolean.TRUE : (c.getEnabled() != null && c.getEnabled() == 1);
        String realRemark = (c != null && c.getRemark() != null && !c.getRemark().isBlank())
                ? c.getRemark() : remark;
        return new CouponRuleVO(key, name, value, enabled, realRemark, min, max, unit);
    }

    @Override
    @Transactional
    public Result saveRule(String ruleKey, String ruleValue, Boolean enabled) {
        if (ruleKey == null || ruleKey.isBlank()) {
            return Result.fail("规则标识不能为空");
        }
        if (!KEY_MAX_STACK_COUNT.equals(ruleKey) && !KEY_MAX_DISCOUNT_RATIO.equals(ruleKey)) {
            return Result.fail("不支持的规则标识：" + ruleKey);
        }
        if (ruleValue == null || ruleValue.isBlank()) {
            return Result.fail("规则值不能为空");
        }
        int n;
        try {
            n = Integer.parseInt(ruleValue.trim());
        } catch (NumberFormatException e) {
            return Result.fail("规则值必须为整数");
        }
        if (KEY_MAX_STACK_COUNT.equals(ruleKey)) {
            if (n < 1 || n > 10) {
                return Result.fail("最大叠加张数需在 1~10 之间");
            }
        } else {
            if (n < 1 || n > 100) {
                return Result.fail("总抵扣上限需在 1~100 之间（100 表示不限制）");
            }
        }
        int on = Boolean.TRUE.equals(enabled) ? 1 : 0;
        CouponRuleConfig exist = getOne(new LambdaQueryWrapper<CouponRuleConfig>()
                .eq(CouponRuleConfig::getRuleKey, ruleKey).last("LIMIT 1"));
        if (exist == null) {
            CouponRuleConfig c = new CouponRuleConfig();
            c.setRuleKey(ruleKey);
            c.setRuleValue(String.valueOf(n));
            c.setEnabled(on);
            save(c);
        } else {
            update(null, new LambdaUpdateWrapper<CouponRuleConfig>()
                    .eq(CouponRuleConfig::getRuleKey, ruleKey)
                    .set(CouponRuleConfig::getRuleValue, String.valueOf(n))
                    .set(CouponRuleConfig::getEnabled, on));
        }
        // 配置已变更，立即失效缓存（事务提交后再删更稳妥，此处配置读低频，直接删即可）
        try {
            stringRedisTemplate.delete(RULE_CACHE_KEY);
        } catch (Exception e) {
            log.warn("失效优惠券规则缓存失败", e);
        }
        log.info("保存优惠券规则: ruleKey={}, value={}, enabled={}", ruleKey, n, on);
        return Result.ok();
    }

    @Override
    public int getMaxStackCount() {
        Integer v = effectiveIntValue(loadRuleMap(), KEY_MAX_STACK_COUNT, 1, 10);
        return v == null ? DEFAULT_MAX_STACK_COUNT : v;
    }

    @Override
    public BigDecimal getMaxDiscountRatio() {
        Integer v = effectiveIntValue(loadRuleMap(), KEY_MAX_DISCOUNT_RATIO, 1, 100);
        int percent = v == null ? DEFAULT_MAX_DISCOUNT_RATIO.multiply(new BigDecimal("100")).intValue() : v;
        return new BigDecimal(percent).divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
    }

    @Override
    public Map<String, Object> rulesForUser() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("maxStackCount", getMaxStackCount());
        m.put("maxDiscountRatio", getMaxDiscountRatio().doubleValue());
        return m;
    }
}
