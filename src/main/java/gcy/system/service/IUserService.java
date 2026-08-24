package gcy.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import gcy.system.entity.dto.*;
import gcy.system.entity.pojo.User;

/**
 * 用户服务接口，提供用户注册、登录、密码管理及个人信息维护等核心业务操作。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
public interface IUserService extends IService<User> {

    /**
     * 发送注册验证码，用于新用户注册时的身份校验。
     *
     * @param registerFormDTO 注册表单数据，包含用户邮箱、验证码等必要信息
     * @return 操作结果，包含成功状态及提示信息
     */
    Result sendRegisterCode(RegisterFormDTO registerFormDTO);

    /**
     * 发送登录验证码，用于已有用户登录时的身份校验。
     *
     * @param loginFormDTO 登录表单数据，包含用户账号等必要信息
     * @return 操作结果，包含成功状态及提示信息
     */
    Result sendLoginCode(LoginFormDTO loginFormDTO);

    /**
     * 用户登录，根据登录表单数据进行身份认证并生成登录令牌。
     *
     * @param loginFormDTO 登录表单数据，包含账号、验证码或密码等登录凭证
     * @return 操作结果，包含登录令牌及用户基本信息
     */
    Result login(LoginFormDTO loginFormDTO);

    /**
     * 用户登出，清除当前登录状态及令牌信息。
     *
     * @return 操作结果，包含成功状态及提示信息
     */
    Result logout();

    /**
     * 用户注册，根据注册表单数据创建新用户账号。
     *
     * @param registerFormDTO 注册表单数据，包含邮箱、密码、确认密码等注册信息
     * @return 操作结果，包含成功状态及新用户基本信息
     */
    Result register(RegisterFormDTO registerFormDTO);

    /**
     * 发送重置密码验证码，用于用户忘记密码时进行身份校验。
     *
     * @param dto 重置密码表单数据，包含用户邮箱等必要信息
     * @return 操作结果，包含成功状态及提示信息
     */
    Result sendResetCode(ResetPasswordFormDTO dto);

    /**
     * 发送修改邮箱验证码到目标新邮箱。
     * <p>
     * 修改邮箱前需向新邮箱发送验证码并校验，证明新邮箱归属于当前用户，防止账号被他人改绑接管。
     * </p>
     *
     * @param email 目标新邮箱
     * @return 操作结果，包含成功状态及提示信息
     */
    Result sendUpdateEmailCode(String email);

    /**
     * 重置密码，在验证码校验通过后将用户密码更新为新密码。
     *
     * @param dto 重置密码表单数据，包含邮箱、验证码及新密码
     * @return 操作结果，包含成功状态及提示信息
     */
    Result resetPassword(ResetPasswordFormDTO dto);

    /**
     * 修改密码，在旧密码校验通过后将用户密码更新为新密码。
     *
     * @param dto 密码表单数据，包含旧密码及新密码
     * @return 操作结果，包含成功状态及提示信息
     */
    Result updatePassword(PasswordFormDTO dto);

    /**
     * 更新用户个人信息，如昵称、头像等非敏感信息。
     *
     * @param updateFormDTO 用户更新表单数据，包含待修改的字段信息
     * @return 操作结果，包含成功状态及更新后的用户信息
     */
    Result updateUser(UpdateFormDTO updateFormDTO);

    /**
     * 注销当前登录用户的账号（不可逆）。
     * <p>
     * 注销即逻辑删除并释放手机号/邮箱的唯一索引占用，账号不可再登录；
     * 历史订单、评价等数据保留，号码/邮箱可被重新注册使用。
     * </p>
     *
     * @return 操作结果，包含成功状态及提示信息
     */
    Result deactivate();

    /**
     * 清理指定用户的全部登录态（遍历其 Redis Token 集合删除对应 Hash，并删除集合本身）。
     * 供修改密码、重置密码、注销、管理员编辑/删除用户等场景统一调用。
     *
     * @param userId 目标用户ID
     */
    void clearAllLoginStates(Long userId);

}
