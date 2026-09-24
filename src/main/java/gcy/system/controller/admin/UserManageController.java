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
 * 用户管理控制器
 * <p>
 * 提供管理员对系统用户的查询、编辑、删除等管理功能。
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
     * 新增用户
     * <p>
     * 管理员创建新用户账号，可指定用户类型（普通用户/管理员）。
     * 手机号和邮箱会进行唯一性校验，用户名不校验唯一性。
     * </p>
     *
     * @param dto 新增用户表单数据传输对象
     * @return 表示操作结果的统一返回结果
     */
    @OperationLog("新增用户")
    @Operation(summary = "新增用户")
    @PostMapping("/create")
    public Result createUser(@Parameter(description = "请求体") @RequestBody @Valid CreateUserDTO dto) {
        return userManageService.createUser(dto);
    }

    /**
     * 分页获取用户列表
     * <p>
     * 支持按手机号、邮箱、管理员身份等条件进行筛选查询。
     *
     * @param current 当前页码，默认为 1
     * @param size    每页显示条数，默认为 10
     * @param phone   手机号筛选条件（可选）
     * @param email   邮箱筛选条件（可选）
     * @param isAdmin 是否为管理员筛选条件（可选）：1 表示管理员，0 表示普通用户
     * @return 包含分页用户列表数据的统一返回结果
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
     * 编辑用户信息
     * <p>
     * 根据传入的用户表单数据更新指定用户的信息。
     *
     * @param dto 用户编辑表单数据传输对象，包含要修改的用户 ID 及新的用户信息
     * @return 表示操作结果的统一返回结果
     */
    @OperationLog("编辑用户")
    @Operation(summary = "编辑用户信息")
    @PutMapping("/edit")
    public Result editUser(@Parameter(description = "请求体") @Valid @RequestBody EditUserFormDTO dto) {
        return userManageService.editUser(dto);
    }

    /**
     * 重置用户密码
     * <p>
     * 管理员为指定用户设置新密码，被重置的用户需重新登录。
     * </p>
     *
     * @param dto 重置密码表单数据传输对象，包含用户ID和新密码
     * @return 表示操作结果的统一返回结果
     */
    @OperationLog("重置密码")
    @Operation(summary = "重置用户密码")
    @PutMapping("/reset-password")
    public Result resetPassword(@Parameter(description = "请求体") @RequestBody @Valid AdminResetPasswordDTO dto) {
        return userManageService.resetPassword(dto);
    }

    /**
     * 根据用户 ID 删除用户
     *
     * @param id 要删除的用户唯一标识 ID
     * @return 表示删除操作结果的统一返回结果
     */
    @OperationLog("删除用户")
    @Operation(summary = "删除用户")
    @DeleteMapping("/delete/{id}")
    public Result deleteUser(@Parameter(description = "用户ID") @PathVariable Long id) {
        return userManageService.deleteUserById(id);
    }

    /**
     * 获取精简用户列表
     * <p>
     * 用于下拉选择框等场景，支持按关键字模糊匹配用户名等信息。
     *
     * @param keyword 搜索关键字（可选），用于模糊匹配用户名或其他标识信息
     * @return 包含精简用户列表数据的统一返回结果
     */
    @Operation(summary = "获取精简用户列表")
    @GetMapping("/simple")
    public Result getSimpleUserList(@Parameter(description = "搜索关键字") @RequestParam(required = false) String keyword) {
        return userManageService.getSimpleUserList(keyword);
    }

}
