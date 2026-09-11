package gcy.system.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 匿名放行注解。
 * <p>
 * 标注在 Controller 类或方法上，表示该接口无需登录即可访问。
 * 安全配置采用"默认全拦截、注解显式放行"的策略：
 * 所有接口默认都需要认证（或管理员角色），只有标注了该注解的接口才会被放行。
 * </p>
 * <p>
 * 新增需要公开访问的接口时，只需在对应方法（或类）上加该注解，
 * 无需再修改 {@link SecurityConfig} 中的路径白名单。
 * </p>
 *
 * @author 郭名城
 * @date 2026-08-24
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Anonymous {

}