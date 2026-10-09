package gcy.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import gcy.system.entity.dto.*;
import gcy.system.entity.pojo.User;

/**
 * 用户服务接口。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
public interface IUserService extends IService<User> {

    /**
     * 发送注册验证码。
     */
    Result sendRegisterCode(RegisterFormDTO registerFormDTO);

    /**
     * 发送登录验证码。
     */
    Result sendLoginCode(LoginFormDTO loginFormDTO);

    /**
     * 用户登录，认证通过后签发登录令牌。
     */
    Result login(LoginFormDTO loginFormDTO);

    /**
     * 用户登出，清除当前登录态。
     */
    Result logout();

    /**
     * 用户注册。
     */
    Result register(RegisterFormDTO registerFormDTO);

    /**
     * 发送重置密码验证码。
     */
    Result sendResetCode(ResetPasswordFormDTO dto);

    /**
     * 发送修改邮箱验证码到目标新邮箱：需校验新邮箱归属，防止账号被改绑接管。
     */
    Result sendUpdateEmailCode(String email);

    /**
     * 重置密码：验证码校验通过后更新为新密码。
     */
    Result resetPassword(ResetPasswordFormDTO dto);

    /**
     * 修改密码：旧密码校验通过后更新为新密码。
     */
    Result updatePassword(PasswordFormDTO dto);

    /**
     * 更新用户个人信息（昵称、头像等非敏感字段）。
     */
    Result updateUser(UpdateFormDTO updateFormDTO);

    /**
     * 注销当前账号（不可逆）：逻辑删除并释放手机号/邮箱唯一索引，账号不可再登录；
     * 历史订单、评价保留，号码/邮箱可被重新注册使用。
     */
    Result deactivate();

    /**
     * 清理指定用户的全部登录态，供改密、重置密码、注销、管理员编辑/删除用户等场景统一调用。
     */
    void clearAllLoginStates(Long userId);

}
