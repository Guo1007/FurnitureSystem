package gcy.system.security;

import cn.hutool.json.JSONUtil;
import gcy.system.entity.dto.Result;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 安全配置类。
 * <p>
 * 配置了系统的认证与授权策略：禁用 CSRF、禁用 Session、使用无状态 Token 认证；
 * 定义了各 URL 路径的访问权限规则；注册了密码加密器、认证管理器、认证入口点和
 * 访问拒绝处理器等核心安全 Bean。
 * </p>
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final TokenAuthFilter tokenAuthFilter;

    private final AnonymousEndpointMatcher anonymousEndpointMatcher;

    /**
     * 构造器注入 Token 认证过滤器与匿名接口匹配器。
     *
     * @param tokenAuthFilter          Token 认证过滤器，用于在每次请求中校验用户身份
     * @param anonymousEndpointMatcher 匿名接口匹配器，扫描 {@link Anonymous} 注解放行公开接口
     */
    public SecurityConfig(TokenAuthFilter tokenAuthFilter, AnonymousEndpointMatcher anonymousEndpointMatcher) {
        this.tokenAuthFilter = tokenAuthFilter;
        this.anonymousEndpointMatcher = anonymousEndpointMatcher;
    }

    /**
     * 配置安全过滤链，定义请求的认证与授权规则。
     * <p>
     * 采用"默认全拦截、注解显式放行"的策略：
     * <ul>
     *   <li>禁用 CSRF 和 Session，采用无状态 Token 认证模式；</li>
     *   <li>OPTIONS 预检请求、标注了 {@link Anonymous} 注解的接口、监控与文档接口放行；</li>
     *   <li>管理后台接口（/admin/**）仅允许 ADMIN 角色访问；</li>
     *   <li>AI 相关接口需要已认证用户；</li>
     *   <li>其余所有请求均需认证——新增接口默认被拦截，需公开时用 {@link Anonymous} 注解读取。</li>
     * </ul>
     * </p>
     *
     * @param http HttpSecurity 对象，由 Spring 容器注入
     * @return 构建完成的 SecurityFilterChain 安全过滤链
     * @throws Exception 配置过程中可能抛出的异常
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // 扫描 @Anonymous 注解的公开接口，统一放行
                        .requestMatchers(anonymousEndpointMatcher).permitAll()
                        .requestMatchers(
                                "/doc.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/webjars/**"
                        ).permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/ai/**").authenticated()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(tokenAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler())
                )
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable);

        return http.build();
    }

    /**
     * 注册密码编码器 Bean，使用 BCrypt 算法对密码进行加密。
     *
     * @return BCryptPasswordEncoder 实例，用于密码的加密与匹配校验
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 注册认证管理器 Bean，从认证配置中获取默认的 AuthenticationManager。
     *
     * @param authenticationConfiguration Spring Security 的认证配置对象
     * @return 默认的 AuthenticationManager 认证管理器实例
     * @throws Exception 获取认证管理器过程中可能抛出的异常
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    /**
     * 注册 UserDetailsService Bean。
     * <p>
     * 由于本系统采用纯 Token 认证方式，不依赖传统的用户名/密码认证，
     * 因此该 Bean 始终抛出 UnsupportedOperationException，防止误用。
     * </p>
     *
     * @return 一个始终拒绝用户名密码认证的 UserDetailsService 实现
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UnsupportedOperationException("本系统使用 Token 认证，不支持用户名密码自动认证");
        };
    }

    /**
     * 注册认证入口点 Bean，处理未认证（401）请求。
     * <p>
     * 当用户未登录或 Token 失效时，返回 JSON 格式的错误信息，
     * 而非 Spring Security 默认的重定向到登录页面。
     * </p>
     *
     * @return 返回 401 状态码和 JSON 错误信息的 AuthenticationEntryPoint 实例
     */
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(401);
            response.setContentType("application/json;charset=utf-8");
            response.getWriter().write(JSONUtil.toJsonStr(Result.fail(401, "登录已过期，请重新登录")));
        };
    }

    /**
     * 注册访问拒绝处理器 Bean，处理权限不足（403）请求。
     * <p>
     * 当已认证用户尝试访问无权限资源时，返回 JSON 格式的错误信息，
     * 而非 Spring Security 默认的错误页面。
     * </p>
     *
     * @return 返回 403 状态码和 JSON 错误信息的 AccessDeniedHandler 实例
     */
    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            response.setStatus(403);
            response.setContentType("application/json;charset=utf-8");
            response.getWriter().write(JSONUtil.toJsonStr(Result.fail(403, "暂无权限访问该页面")));
        };
    }
}
