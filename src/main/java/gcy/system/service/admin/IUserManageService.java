package gcy.system.service.admin;

import com.baomidou.mybatisplus.extension.service.IService;
import gcy.system.entity.dto.Result;
import gcy.system.entity.dto.admin.AdminResetPasswordDTO;
import gcy.system.entity.dto.admin.CreateUserDTO;
import gcy.system.entity.dto.admin.EditUserFormDTO;
import gcy.system.entity.pojo.User;

/**
 * 用户管理服务接口。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
public interface IUserManageService extends IService<User> {

    /**
     * 管理员创建用户（普通用户/管理员），密码加密存储；校验手机号与邮箱唯一性（userName 非唯一索引，不校验）。
     */
    Result createUser(CreateUserDTO dto);

    /**
     * 分页查询用户列表，按手机号、邮箱、是否管理员筛选，条件为空不参与过滤。
     */
    Result getUserList(Integer current, Integer size,
                       String phone, String email, Integer isAdmin);

    /**
     * 编辑用户信息。
     */
    Result editUser(EditUserFormDTO dto);

    /**
     * 管理员为指定用户设置新密码，重置后清理该用户全部登录态使其重新登录。
     */
    Result resetPassword(AdminResetPasswordDTO dto);

    /**
     * 根据用户ID删除用户。
     */
    Result deleteUserById(Long userId);

    /**
     * 按关键词对用户名、手机号模糊搜索，返回用户简要信息列表。
     */
    Result getSimpleUserList(String keyword);

}
