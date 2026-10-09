package gcy.system.utils;

/**
 * 定向发放优惠券的「场景」，决定邮件模板与站内通知口吻。
 * <p>
 * 三套语气刻意不同，不要合并：补偿场景用户可能正在气头上，文案禁止营销腔与庆祝用语；关怀可温暖，通用为中性通知。
 * <p>
 * {@code code} 用于拼邮件模板名 {@code email/coupon-{code}}，只能经 {@link #fromCode} 白名单取值，
 * 否则前端传入 {@code ../../xxx} 会构成模板路径注入。
 *
 * @author 郭名城
 * @date 2026-10-09
 */
public enum CouponGrantScene {

    /**
     * 补偿：用户投诉、订单出问题、服务失误后的补偿。
     */
    COMPENSATION("compensation", "补偿", "优惠券补偿通知",
            "很抱歉给您带来了不便，我们为您补发了一张优惠券，感谢您的理解与支持。"),

    /**
     * 关怀：老客户回馈、节日问候。
     */
    CARE("care", "关怀", "优惠券关怀通知",
            "感谢您一直以来的支持与信任，特意为您准备了一张优惠券。"),

    /**
     * 通用：其它定向发放，纯通知。
     */
    GENERAL("general", "通用", "优惠券发放通知",
            "您收到一张优惠券，请在有效期内使用。");

    /**
     * 场景编码。用于拼邮件模板名 {@code email/coupon-{code}}。
     */
    private final String code;

    /**
     * 中文名，用于后台表单展示。
     */
    private final String label;

    /**
     * 邮件主题与站内通知标题共用的文案。
     * 放这里是为了让「某场景该用什么措辞」只有一处定义。
     */
    private final String title;

    /**
     * 站内通知的开场白。
     * <p>
     * 邮件模板里那段更长的正文是各自独立写的（媒介不同、篇幅不同），
     * 但<b>语气必须与本字段保持一致</b> —— 尤其补偿场景，两边都不能出现庆祝用语。
     * </p>
     */
    private final String notifyIntro;

    CouponGrantScene(String code, String label, String title, String notifyIntro) {
        this.code = code;
        this.label = label;
        this.title = title;
        this.notifyIntro = notifyIntro;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public String getTitle() {
        return title;
    }

    public String getNotifyIntro() {
        return notifyIntro;
    }

    /**
     * 按编码取场景（白名单）。取不到或为空时回落 {@link #GENERAL}。
     * <p>
     * 刻意不抛异常也不返回 null：发放本身不该因为一个场景参数写错就失败，
     * 退化成「通用语气」是安全的降级。
     * </p>
     */
    public static CouponGrantScene fromCode(String code) {
        if (code == null || code.isBlank()) {
            return GENERAL;
        }
        String trimmed = code.trim();
        for (CouponGrantScene s : values()) {
            if (s.code.equalsIgnoreCase(trimmed)) {
                return s;
            }
        }
        return GENERAL;
    }
}
