package gcy.system.utils;

import gcy.system.entity.dto.UserDTO;

/**
 * 用户上下文持有者工具类。
 * 基于 ThreadLocal 在当前线程内保存登录用户信息与令牌，避免参数逐层传递。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
public class UserHolder {
    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();

    private static final ThreadLocal<String> tokenTl = new ThreadLocal<>();

    /**
     * 保存当前登录用户信息与令牌到当前线程。
     */
    public static void saveUser(UserDTO user, String token) {
        tl.set(user);
        tokenTl.set(token);
    }

    /**
     * 获取当前线程保存的用户信息，未保存返回 null。
     */
    public static UserDTO getUser() {
        return tl.get();
    }

    /**
     * 获取当前线程保存的认证令牌，未保存返回 null。
     */
    public static String getToken() {
        return tokenTl.get();
    }

    /**
     * 清除当前线程 ThreadLocal 中保存的用户信息与令牌，防止内存泄漏。
     */
    public static void removeUser() {
        tl.remove();
        tokenTl.remove();
    }
}
