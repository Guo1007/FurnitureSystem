package gcy.system.utils;

/**
 * 正则表达式常量工具类。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
public abstract class RegexPatterns {
    /**
     * 中国大陆手机号正则，支持主流运营商号段。
     */
    public static final String PHONE_REGEX = "^1([38][0-9]|4[579]|5[0-35-9]|6[2567]|7[0-8]|9[0-9])\\d{8}$";
    /**
     * 邮箱正则。
     */
    public static final String EMAIL_REGEX = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";
    /**
     * 密码正则：6-32 位，需同时包含大小写字母和数字。
     */
    public static final String PASSWORD_REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)[a-zA-Z\\d]{6,32}$";

}
