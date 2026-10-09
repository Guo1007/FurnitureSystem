package gcy.system.utils;

import gcy.system.entity.pojo.Coupon;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 优惠券模板的纯函数工具：有效期推算 + 展示文案。
 * <p>
 * 抽出来共用，避免管理端再抄一份导致领取与定向发放两条路径的规则分叉。
 *
 * @author 郭名城
 * @date 2026-10-09
 */
public final class CouponUtil {

    private static final DateTimeFormatter MM_DD = DateTimeFormatter.ofPattern("MM-dd");

    private CouponUtil() {
    }

    /**
     * 推算券的过期时间；领取与定向发放共用，保证同一口径。
     * <p>
     * {@code validType=2}（相对有效期）取 baseTime + validDays，否则取固定 validEnd。
     *
     * @param baseTime 起算时间：领取传领取时刻，定向发放传发放时刻
     * @return 固定有效期且未配 validEnd 时为 null（视为不限）
     */
    public static LocalDateTime resolveExpireTime(Coupon c, LocalDateTime baseTime) {
        if (c.getValidType() != null && c.getValidType() == 2 && c.getValidDays() != null) {
            return baseTime.plusDays(c.getValidDays());
        }
        return c.getValidEnd();
    }

    /**
     * 券面额文案：折扣券给「8折」，满减/无门槛券给「¥20」。
     */
    public static String amountText(Coupon c) {
        if (c.getType() == null) {
            return "";
        }
        if (c.getType() == 2 && c.getDiscount() != null) {
            String s = c.getDiscount().multiply(BigDecimal.TEN).setScale(1, RoundingMode.HALF_UP)
                    .stripTrailingZeros().toPlainString();
            return s + "折";
        }
        if (c.getAmount() != null) {
            return "¥" + c.getAmount().stripTrailingZeros().toPlainString();
        }
        return "";
    }

    /**
     * 适用范围文案。
     */
    public static String scopeText(Coupon c) {
        return c.getScope() != null && c.getScope() == 1 ? "分类券" : "全场";
    }

    /**
     * 使用门槛文案，如「满100可用」「无门槛」。
     */
    public static String thresholdText(Coupon c) {
        BigDecimal threshold = c.getMinThreshold() == null ? BigDecimal.ZERO : c.getMinThreshold();
        if (threshold.compareTo(BigDecimal.ZERO) <= 0) {
            return "无门槛";
        }
        return "满" + threshold.stripTrailingZeros().toPlainString() + "可用";
    }

    /**
     * 有效期文案。
     *
     * @param fromLabel 起算说法：公开领取传「领取后」，定向发放传「发放后」，空则默认「领取后」
     */
    public static String validText(Coupon c, String fromLabel) {
        String from = (fromLabel == null || fromLabel.isBlank()) ? "领取后" : fromLabel;
        if (c.getValidType() != null && c.getValidType() == 2 && c.getValidDays() != null) {
            return from + " " + c.getValidDays() + " 天有效";
        }
        return c.getValidEnd() != null ? "至 " + c.getValidEnd().format(MM_DD) : "不限";
    }
}
