package gcy.system.controller;


import cn.hutool.core.bean.BeanUtil;
import gcy.system.aspect.OperationLog;
import gcy.system.entity.dto.*;
import gcy.system.integration.OssService;
import gcy.system.security.Anonymous;
import gcy.system.service.IUserService;
import gcy.system.utils.UserHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户控制器，处理用户注册、登录与资料相关接口。
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
     */
    @Operation(summary = "发送注册验证码")
    @Anonymous
    @PostMapping("/r_code")
    public Result sendRegisterCode(@Parameter(description = "请求体") @Valid @RequestBody RegisterFormDTO registerFormDTO) {
        return userService.sendRegisterCode(registerFormDTO);
    }

    /**
     * 发送登录验证码。
     */
    @Operation(summary = "发送登录验证码")
    @Anonymous
    @PostMapping("/code")
    public Result sendLoginCode(@Parameter(description = "请求体") @Valid @RequestBody LoginFormDTO loginFormDTO) {
        return userService.sendLoginCode(loginFormDTO);
    }

    /**
     * 向新邮箱发送验证码，发送前校验邮箱格式且该邮箱未被其他账号绑定。
     */
    @Operation(summary = "发送修改邮箱验证码")
    @PostMapping("/email-code")
    public Result sendUpdateEmailCode(@Parameter(description = "请求体") @RequestBody UpdateFormDTO dto) {
        return userService.sendUpdateEmailCode(dto.getEmail());
    }

    @Operation(summary = "发送重置密码验证码")
    @Anonymous
    @PostMapping("/reset-code")
    public Result sendResetCode(@Parameter(description = "请求体") @Valid @RequestBody ResetPasswordFormDTO dto) {
        return userService.sendResetCode(dto);
    }

    /**
     * 重置密码。
     */
    @OperationLog("重置密码")
    @Operation(summary = "重置密码")
    @Anonymous
    @PostMapping("/reset-password")
    public Result resetPassword(@Parameter(description = "请求体") @Valid @RequestBody ResetPasswordFormDTO dto) {
        return userService.resetPassword(dto);
    }

    /**
     * 用户登录。
     */
    @OperationLog("用户登录")
    @Operation(summary = "用户登录")
    @Anonymous
    @PostMapping("/login")
    public Result login(@Parameter(description = "请求体") @Valid @RequestBody LoginFormDTO loginFormDTO) {
        return userService.login(loginFormDTO);
    }

    /**
     * 用户登出。
     */
    @OperationLog("用户登出")
    @Operation(summary = "用户登出")
    @PostMapping("/logout")
    public Result logout() {
        return userService.logout();
    }

    /**
     * 注销当前账号（不可逆）。注销后无法登录，历史订单与评价保留，绑定的手机号/邮箱释放可被重新注册；前端需二次确认。
     */
    @OperationLog("注销账号")
    @Operation(summary = "注销账号")
    @PostMapping("/deactivate")
    public Result deactivate() {
        return userService.deactivate();
    }

    /**
     * 用户注册。
     */
    @OperationLog("用户注册")
    @Operation(summary = "用户注册")
    @Anonymous
    @PostMapping("/register")
    public Result register(@Parameter(description = "请求体") @Valid @RequestBody RegisterFormDTO registerFormDTO) {
        return userService.register(registerFormDTO);
    }

    /**
     * 获取当前登录用户信息，含是否已设置密码的标志。
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
     */
    @OperationLog("修改密码")
    @Operation(summary = "修改密码")
    @PutMapping("/password")
    public Result updatePassword(@Parameter(description = "请求体") @Valid @RequestBody PasswordFormDTO dto) {
        return userService.updatePassword(dto);
    }

    /**
     * 更新当前登录用户的个人信息。
     */
    @OperationLog("更新个人信息")
    @Operation(summary = "更新个人信息")
    @PutMapping("/update")
    public Result updateUser(@Parameter(description = "请求体") @RequestBody UpdateFormDTO dto) {
        return userService.updateUser(dto);
    }

    /**
     * 上传用户头像，返回文件访问路径。
     */
    @Operation(summary = "上传用户头像")
    @PostMapping("/upload/avatar")
    public Result uploadAvatar(@Parameter(description = "头像文件") @RequestParam("file") MultipartFile file) {
        String path = ossService.uploadAvatar(file);
        return Result.ok(path);
    }

}
