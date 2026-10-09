package gcy.system.utils;


import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码加密工具类，基于 Spring Security 的 BCryptPasswordEncoder 做单向哈希加密。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
public class PasswordUtil {

    private static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    /**
     * 对明文密码进行 BCrypt 加密。
     */
    public static String encode(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    /**
     * 校验明文密码与密文是否匹配。
     */
    public static boolean matches(String rawPassword, String encodedPassword) {
        return encoder.matches(rawPassword, encodedPassword);
    }

}
