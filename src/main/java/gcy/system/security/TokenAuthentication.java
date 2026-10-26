package gcy.system.security;

import gcy.system.entity.dto.UserDTO;
import lombok.Getter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.io.Serial;
import java.util.Collections;

/**
 * 自定义Spring Security认证令牌，用于在请求上下文中传递已认证的用户信息。
 * <p>
 * 该令牌封装了用户数据传输对象（{@link UserDTO}）和原始令牌字符串，
 * 并根据用户是否为管理员自动分配相应的权限。
 * </p>
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Getter
public class TokenAuthentication extends AbstractAuthenticationToken {

    /**
     * 序列化版本号
     */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 当前认证用户的数据传输对象
     */
    private final UserDTO userDTO;

    /**
     * 原始认证令牌字符串
     */
    private final String token;

    /**
     * 构造一个已认证的TokenAuthentication实例。
     * <p>
     * 根据用户的isAdmin字段自动分配权限：
     * 若isAdmin为1，则赋予ROLE_ADMIN权限；
     * 否则不赋予任何特殊权限。
     * 构造完成后即标记为已认证状态。
     * </p>
     *
     * @param userDTO 包含用户信息的UserDTO对象，用于获取用户详情及权限判定
     * @param token   客户端传递的原始令牌字符串，作为认证凭证
     */
    public TokenAuthentication(UserDTO userDTO, String token) {
        super(userDTO.getIsAdmin() != null && userDTO.getIsAdmin() == 1
                ? Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN"))
                : Collections.emptyList());
        this.userDTO = userDTO;
        this.token = token;
        setAuthenticated(true);
    }

    /**
     * 获取当前认证令牌的凭证信息。
     *
     * @return 原始令牌字符串，即客户端传递的认证凭证
     */
    @Override
    public Object getCredentials() {
        return token;
    }

    /**
     * 获取当前认证令牌的主体（Principal）信息。
     *
     * @return 包含当前用户详细信息的UserDTO对象
     */
    @Override
    public Object getPrincipal() {
        return userDTO;
    }

}
