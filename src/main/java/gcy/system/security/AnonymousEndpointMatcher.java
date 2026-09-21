package gcy.system.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.condition.PathPatternsRequestCondition;
import org.springframework.web.servlet.mvc.condition.PatternsRequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.*;

/**
 * 匿名接口匹配器。
 * <p>
 * 启动时扫描 Spring MVC 的 {@link RequestMappingHandlerMapping}，
 * 收集所有标注了 {@link Anonymous} 注解（类级或方法级）的接口路径与方法，
 * 组装成一组 {@link PathPatternRequestMatcher}。在安全配置中调用该匹配器即可实现
 * "默认全拦截、注解显式放行"的策略。
 * </p>
 * <p>
 * 新增公开接口只需在控制器上加 {@link Anonymous} 注解，启动时会被自动扫描到，
 * 无需再维护手写路径白名单。
 * </p>
 *
 * @author 郭名城
 * @date 2026-08-24
 */
@Component
public class AnonymousEndpointMatcher implements RequestMatcher, SmartInitializingSingleton {

    private final RequestMappingHandlerMapping handlerMapping;

    /**
     * 收集到的匿名接口匹配器列表。
     */
    private final List<RequestMatcher> matchers = new ArrayList<>();

    /**
     * 用于去重，避免同一个 路径+方法 被重复添加。
     */
    private final Set<String> seen = new HashSet<>();

    public AnonymousEndpointMatcher(RequestMappingHandlerMapping handlerMapping) {
        this.handlerMapping = handlerMapping;
    }

    /**
     * 在容器所有单例初始化完成后，扫描并收集标注了 {@link Anonymous} 的接口。
     */
    @Override
    public void afterSingletonsInstantiated() {
        Map<RequestMappingInfo, HandlerMethod> handlerMethods = handlerMapping.getHandlerMethods();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMethods.entrySet()) {
            RequestMappingInfo info = entry.getKey();
            HandlerMethod handlerMethod = entry.getValue();
            if (!isAnonymous(handlerMethod)) {
                continue;
            }
            Set<String> paths = extractPaths(info);
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            for (String path : paths) {
                addMatcher(path, methods);
            }
        }
    }

    /**
     * 判断某个处理器方法（或其声明类）是否标注了 {@link Anonymous}。
     */
    private boolean isAnonymous(HandlerMethod handlerMethod) {
        return handlerMethod.getMethod().isAnnotationPresent(Anonymous.class)
                || handlerMethod.getBeanType().isAnnotationPresent(Anonymous.class);
    }

    /**
     * 提取请求映射的路径集合，兼容 PathPattern 与 AntPathMatcher 两种条件。
     */
    private Set<String> extractPaths(RequestMappingInfo info) {
        Set<String> paths = new HashSet<>();
        PathPatternsRequestCondition pathPatterns = info.getPathPatternsCondition();
        if (pathPatterns != null) {
            pathPatterns.getPatterns().forEach(p -> paths.add(p.getPatternString()));
        }
        PatternsRequestCondition patterns = info.getPatternsCondition();
        if (patterns != null) {
            paths.addAll(patterns.getPatterns());
        }
        return paths;
    }

    /**
     * 按 路径+方法 添加匹配器；未指定方法（methods 为空）则表示任意方法。
     */
    private void addMatcher(String path, Set<RequestMethod> methods) {
        if (methods.isEmpty()) {
            add(path, null);
            return;
        }
        for (RequestMethod method : methods) {
            add(path, method.name());
        }
    }

    private void add(String path, String httpMethod) {
        String key = (httpMethod == null ? "ANY" : httpMethod) + ":" + path;
        if (!seen.add(key)) {
            return;
        }
        PathPatternRequestMatcher.Builder builder = PathPatternRequestMatcher.withDefaults();
        if (httpMethod != null) {
            matchers.add(builder.matcher(HttpMethod.valueOf(httpMethod), path));
        } else {
            matchers.add(builder.matcher(path));
        }
    }

    @Override
    public boolean matches(HttpServletRequest request) {
        for (RequestMatcher matcher : matchers) {
            if (matcher.matches(request)) {
                return true;
            }
        }
        return false;
    }
}