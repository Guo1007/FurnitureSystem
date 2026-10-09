package gcy.system.controller.admin;


import gcy.system.aspect.OperationLog;
import gcy.system.entity.dto.Result;
import gcy.system.entity.dto.admin.AdminResetPasswordDTO;
import gcy.system.entity.dto.admin.CreateUserDTO;
import gcy.system.entity.dto.admin.EditUserFormDTO;
import gcy.system.service.admin.IUserManageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户管理控制器。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Tag(name = "用户管理", description = "用户管理相关接口")
@RestController
@RequestMapping("/admin/user")
@RequiredArgsConstructor
public class UserManageController {

    private final IUserManageService userManageService;

    /**
     * 新增用户：手机号、邮箱校验唯一性，用户名不校验。
     */
    @OperationLog("新增用户")
    @Operation(summary = "新增用户")
    @PostMapping("/create")
    public Result createUser(@Parameter(description = "请求体") @RequestBody @Valid CreateUserDTO dto) {
        return userManageService.createUser(dto);
    }

    /**
     * 分页获取用户列表。
     */
    @Operation(summary = "分页获取用户列表")
    @GetMapping("/list")
    public Result getUserList(@Parameter(description = "当前页码") @RequestParam(defaultValue = "1") Integer current,
                              @Parameter(description = "每页显示条数") @RequestParam(defaultValue = "10") Integer size,
                              @Parameter(description = "手机号筛选") @RequestParam(required = false) String phone,
                              @Parameter(description = "邮箱筛选") @RequestParam(required = false) String email,
                              @Parameter(description = "是否管理员") @RequestParam(required = false) Integer isAdmin) {
        return userManageService.getUserList(current, size, phone, email, isAdmin);
    }

    /**
     * 编辑用户信息。
     */
    @OperationLog("编辑用户")
    @Operation(summary = "编辑用户信息")
    @PutMapping("/edit")
    public Result editUser(@Parameter(description = "请求体") @Valid @RequestBody EditUserFormDTO dto) {
        return userManageService.editUser(dto);
    }

    /**
     * 重置用户密码，重置后该用户需重新登录。
     */
    @OperationLog("重置密码")
    @Operation(summary = "重置用户密码")
    @PutMapping("/reset-password")
    public Result resetPassword(@Parameter(description = "请求体") @RequestBody @Valid AdminResetPasswordDTO dto) {
        return userManageService.resetPassword(dto);
    }

    /**
     * 删除用户。
     */
    @OperationLog("删除用户")
    @Operation(summary = "删除用户")
    @DeleteMapping("/delete/{id}")
    public Result deleteUser(@Parameter(description = "用户ID") @PathVariable Long id) {
        return userManageService.deleteUserById(id);
    }

    /**
     * 获取精简用户列表，用于下拉选择框等场景。
     */
    @Operation(summary = "获取精简用户列表")
    @GetMapping("/simple")
    public Result getSimpleUserList(@Parameter(description = "搜索关键字") @RequestParam(required = false) String keyword) {
        return userManageService.getSimpleUserList(keyword);
    }

}
