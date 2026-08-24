package gcy.system.controller;


import cn.hutool.core.bean.BeanUtil;
import gcy.system.aspect.OperationLog;
import gcy.system.entity.dto.*;
import gcy.system.integration.OssService;
import gcy.system.service.IUserService;
import gcy.system.utils.UserHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户控制器，处理与用户相关的HTTP请求，包括注册、登录、登出、密码管理、信息更新和头像上传等功能。
 * <p>
 * 所有接口均以 "/user" 为根路径。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Tag(name = "用户", description = "用户相关接口")
@Slf4j
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final IUserService userService;

    private final OssService ossService;

    /**
     * 发送注册验证码。
     * <p>
     * POST /user/r_code
     *
     * @param registerFormDTO 注册表单数据，包含用户注册所需信息（如邮箱等）
     * @return 包含发送结果的 {@link Result} 对象
     */
    @Operation(summary = "发送注册验证码")
    @PostMapping("/r_code")
    public Result sendRegisterCode(@Parameter(description = "请求体") @RequestBody RegisterFormDTO registerFormDTO) {
        return userService.sendRegisterCode(registerFormDTO);
    }

    /**
     * 发送登录验证码。
     * <p>
     * POST /user/code
     *
     * @param loginFormDTO 登录表单数据，包含用户登录所需信息（如账号等）
     * @return 包含发送结果的 {@link Result} 对象
     */
    @Operation(summary = "发送登录验证码")
    @PostMapping("/code")
    public Result sendLoginCode(@Parameter(description = "请求体") @RequestBody LoginFormDTO loginFormDTO) {
        return userService.sendLoginCode(loginFormDTO);
    }

    /**
     * 发送修改邮箱验证码。
     * <p>
     * POST /user/email-code
     * 校验新邮箱格式且未被其他账号绑定后，向新邮箱发送验证码，供修改邮箱时校验归属。
     *
     * @param dto 用户信息更新表单数据，包含目标新邮箱（email 字段）
     * @return 包含发送结果的 {@link Result} 对象
     */
    @Operation(summary = "发送修改邮箱验证码")
    @PostMapping("/email-code")
    public Result sendUpdateEmailCode(@Parameter(description = "请求体") @RequestBody UpdateFormDTO dto) {
        return userService.sendUpdateEmailCode(dto.getEmail());
    }

    @Operation(summary = "发送重置密码验证码")
    @PostMapping("/reset-code")
    public Result sendResetCode(@Parameter(description = "请求体") @RequestBody ResetPasswordFormDTO dto) {
        return userService.sendResetCode(dto);
    }

    /**
     * 重置密码。
     * <p>
     * POST /user/reset-password
     *
     * @param dto 重置密码表单数据，包含新密码及验证码等必要信息
     * @return 包含操作结果的 {@link Result} 对象
     */
    @OperationLog("重置密码")
    @Operation(summary = "重置密码")
    @PostMapping("/reset-password")
    public Result resetPassword(@Parameter(description = "请求体") @RequestBody ResetPasswordFormDTO dto) {
        return userService.resetPassword(dto);
    }

    /**
     * 用户登录。
     * <p>
     * POST /user/login
     *
     * @param loginFormDTO 登录表单数据，包含登录凭证（如账号、验证码或密码等）
     * @return 包含登录结果的 {@link Result} 对象，登录成功时通常包含用户信息和令牌
     */
    @OperationLog("用户登录")
    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result login(@Parameter(description = "请求体") @RequestBody LoginFormDTO loginFormDTO) {
        return userService.login(loginFormDTO);
    }

    /**
     * 用户登出。
     * <p>
     * POST /user/logout
     *
     * @return 包含登出结果的 {@link Result} 对象
     */
    @OperationLog("用户登出")
    @Operation(summary = "用户登出")
    @PostMapping("/logout")
    public Result logout() {
        return userService.logout();
    }

    /**
     * 注销当前登录用户的账号（不可逆）。
     * <p>
     * POST /user/deactivate
     * 注销后账号无法登录，历史订单与评价数据保留；绑定的手机号/邮箱会被释放，可被重新注册使用。
     * 前端需二次确认后调用。
     *
     * @return 包含注销结果的 {@link Result} 对象
     */
    @OperationLog("注销账号")
    @Operation(summary = "注销账号")
    @PostMapping("/deactivate")
    public Result deactivate() {
        return userService.deactivate();
    }

    /**
     * 用户注册。
     * <p>
     * POST /user/register
     *
     * @param registerFormDTO 注册表单数据，包含邮箱、密码等注册所需信息
     * @return 包含注册结果的 {@link Result} 对象
     */
    @OperationLog("用户注册")
    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public Result register(@Parameter(description = "请求体") @RequestBody RegisterFormDTO registerFormDTO) {
        return userService.register(registerFormDTO);
    }

    /**
     * 获取当前登录用户的信息。
     * <p>
     * GET /user/me
     *
     * @return 包含当前用户信息的 {@link Result} 对象，其中包含用户基本信息和是否有密码的标志
     */
    @OperationLog("获取当前用户信息")
    @Operation(summary = "获取当前登录用户信息")
    @GetMapping("/me")
    public Result me() {
        UserDTO user = UserHolder.getUser();
        UserDTO copy = BeanUtil.copyProperties(user, UserDTO.class);
        return Result.ok(copy);
    }

    /**
     * 修改当前登录用户的密码。
     * <p>
     * PUT /user/password
     *
     * @param dto 密码修改表单数据，包含旧密码和新密码
     * @return 包含操作结果的 {@link Result} 对象
     */
    @OperationLog("修改密码")
    @Operation(summary = "修改密码")
    @PutMapping("/password")
    public Result updatePassword(@Parameter(description = "请求体") @RequestBody PasswordFormDTO dto) {
        return userService.updatePassword(dto);
    }

    /**
     * 更新当前登录用户的个人信息。
     * <p>
     * PUT /user/update
     *
     * @param dto 用户信息更新表单数据，包含需要修改的用户字段（如昵称、头像等）
     * @return 包含操作结果的 {@link Result} 对象
     */
    @OperationLog("更新个人信息")
    @Operation(summary = "更新个人信息")
    @PutMapping("/update")
    public Result updateUser(@Parameter(description = "请求体") @RequestBody UpdateFormDTO dto) {
        return userService.updateUser(dto);
    }

    /**
     * 上传用户头像。
     * <p>
     * POST /user/upload/avatar
     *
     * @param file 用户上传的头像文件，通过表单字段 "file" 提交
     * @return 包含头像文件访问路径的 {@link Result} 对象
     */
    @Operation(summary = "上传用户头像")
    @PostMapping("/upload/avatar")
    public Result uploadAvatar(@Parameter(description = "头像文件") @RequestParam("file") MultipartFile file) {
        String path = ossService.uploadAvatar(file);
        return Result.ok(path);
    }

}
