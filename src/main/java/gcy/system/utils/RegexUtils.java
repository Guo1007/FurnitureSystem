package gcy.system.utils;

import cn.hutool.core.util.StrUtil;

/**
 * 正则表达式工具类。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
public class RegexUtils {

    /**
     * 校验手机号是否无效；为空视为无效。
     */
    public static boolean isPhoneInvalid(String phone) {
        return mismatch(phone, RegexPatterns.PHONE_REGEX);
    }

    /**
     * 校验邮箱格式是否有效。
     */
    public static boolean isEmailValid(String email) {
        return !mismatch(email, RegexPatterns.EMAIL_REGEX);
    }

    /**
     * 校验密码格式是否有效。
     */
    public static boolean isPasswordValid(String password) {
        return !mismatch(password, RegexPatterns.PASSWORD_REGEX);
    }

    /**
     * 字符串为空或与正则不匹配时返回 true。
     */
    private static boolean mismatch(String str, String regex) {
        if (StrUtil.isBlank(str)) {
            return true;
        }
        return !str.matches(regex);
    }

}
