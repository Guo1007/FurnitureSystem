package gcy.system.security;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import gcy.system.entity.dto.Result;
import gcy.system.entity.dto.UserDTO;
import gcy.system.utils.RedisConstants;
import gcy.system.utils.UserHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Token 认证过滤器，基于 Spring Security 的 OncePerRequestFilter，
 * 从请求头中提取 Bearer Token，校验 Redis 中的用户登录信息并设置认证上下文。
 * <p>
 * 主要流程：
 * <ol>
 *   <li>从 Authorization 请求头中提取 Bearer Token</li>
 *   <li>在 Redis 中查找对应的用户信息</li>
 *   <li>若找到则续期 Token 过期时间，并设置 Spring Security 认证上下文及用户线程持有者</li>
 *   <li>请求处理完成后清除用户线程持有者，避免内存泄漏</li>
 * </ol>
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Component
public class TokenAuthFilter extends OncePerRequestFilter {

    /**
     * 不过滤异步分发（async dispatch），确保异步请求完成时 SecurityContext 仍然有效。
     * 否则流式响应（如 AI SSE）结束时 AuthorizationFilter 会因缺少认证信息而抛出 Access Denied。
     *
     * @return false，表示不对异步分发请求进行过滤，确保异步上下文中的认证信息不被清空
     */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 构造 TokenAuthFilter，注入 StringRedisTemplate 用于操作 Redis 中的用户 Token 数据。
     *
     * @param stringRedisTemplate Spring Data Redis 的字符串模板，用于读写 Redis 中的登录用户信息
     */
    public TokenAuthFilter(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 执行 Token 认证过滤逻辑：从请求头中提取 Bearer Token，
     * 在 Redis 中校验用户信息，设置认证上下文，并在请求完成后清理线程持有者。
     *
     * @param request     HTTP 请求对象，用于获取 Authorization 请求头
     * @param response    HTTP 响应对象，传递给后续过滤器链
     * @param filterChain 过滤器链，用于继续执行后续过滤器和目标资源
     * @throws ServletException 若过滤器处理过程中发生 Servlet 相关异常
     * @throws IOException      若过滤器处理过程中发生 I/O 异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        try {
            String header = request.getHeader("authorization");
            if (header == null || !header.startsWith("Bearer ")) {
                filterChain.doFilter(request, response);
                return;
            }

            String token = header.substring(7);
            String hashKey = RedisConstants.LOGIN_USER_KEY + token;
            Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(hashKey);

            if (userMap.isEmpty()) {
                // 区分被踢下线与登录过期：Hash 不存在，但存在被踢标记 → 强制下线
                if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(RedisConstants.LOGIN_KICKED_KEY + token))) {
                    writeUnauthorized(response, "您的账号已在其他设备登录，您已被强制下线");
                    return;
                }
                filterChain.doFilter(request, response);
                return;
            }

            // 从 userMap 里拿 userId
            Object userIdObj = userMap.get("id");

            // 【双保险】校验 token 还在不在用户的 Set 里
            if (userIdObj != null) {
                try {
                    Long userId = Long.parseLong(userIdObj.toString());
                    String setKey = RedisConstants.LOGIN_USER_TOKENS_SET + userId;

                    Boolean isMember = stringRedisTemplate.opsForSet().isMember(setKey, token);

                    if (!Boolean.TRUE.equals(isMember)) {
                        // 不在 Set 里 → 被踢下线了 → 顺手把残留 Hash 清掉
                        stringRedisTemplate.delete(hashKey);
                        // 日志不记录完整 token（token 即 Redis 键，泄露可会话劫持），只记录 userId
                        logger.warn("Token 不在用户的有效 Set 中，判定为已被踢下线，userId=" + userId);
                        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(RedisConstants.LOGIN_KICKED_KEY + token))) {
                            writeUnauthorized(response, "您的账号已在其他设备登录，您已被强制下线");
                        } else {
                            filterChain.doFilter(request, response);
                        }
                        return;
                    }

                    // 【同步续 TTL】给 Set Key 也续上过期时间
                    stringRedisTemplate.expire(setKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.SECONDS);
                } catch (NumberFormatException e) {
                    logger.warn("userMap.id 解析失败，跳过 Set 校验，userIdStr=" + userIdObj);
                }
            }

            // 给 Hash Key 续期
            stringRedisTemplate.expire(hashKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.SECONDS);

            UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
            TokenAuthentication auth = new TokenAuthentication(userDTO, token);
            SecurityContextHolder.getContext().setAuthentication(auth);

            UserHolder.saveUser(userDTO, token);

        } catch (Exception e) {
            logger.error("Token 认证异常", e);
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            UserHolder.removeUser();
        }
    }

    /**
     * 直接写出 401 未认证响应（带业务提示）。
     * 用于被踢下线场景下，向被挤下线的设备返回明确的错误信息。
     *
     * @param response HTTP 响应对象
     * @param msg      返回给客户端的提示文案
     */
    private void writeUnauthorized(HttpServletResponse response, String msg) throws IOException {
        response.setStatus(401);
        response.setContentType("application/json;charset=utf-8");
        response.getWriter().write(JSONUtil.toJsonStr(Result.fail(401, msg)));
    }
}
