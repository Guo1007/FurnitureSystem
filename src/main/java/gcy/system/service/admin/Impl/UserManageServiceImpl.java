package gcy.system.service.admin.Impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import gcy.system.entity.dto.Result;
import gcy.system.entity.dto.UserSimpleDTO;
import gcy.system.entity.dto.admin.AdminResetPasswordDTO;
import gcy.system.entity.dto.admin.CreateUserDTO;
import gcy.system.entity.dto.admin.EditUserFormDTO;
import gcy.system.entity.pojo.User;
import gcy.system.entity.vo.UserVO;
import gcy.system.exception.BusinessException;
import gcy.system.mapper.UserMapper;
import gcy.system.service.IUserService;
import gcy.system.service.admin.IUserManageService;
import gcy.system.utils.PasswordUtil;
import gcy.system.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 用户管理服务实现类。
 * 编辑、删除、重置密码后清理该用户的 Redis 登录态，使其重新登录生效。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserManageServiceImpl extends ServiceImpl<UserMapper, User>
        implements IUserManageService {

    private final UserMapper userMapper;

    private final IUserService userService;

    /**
     * 新增用户：手机号与邮箱至少填写一项，唯一性按实际填写的项校验（userName 非唯一索引，不校验）。
     * 新账号尚无登录态，无需清理 Redis。
     */
    @Override
    @Transactional
    public Result createUser(CreateUserDTO dto) {
        if (dto == null) {
            return Result.fail("请完善用户信息！");
        }

        if (StrUtil.isBlank(dto.getPhone()) && StrUtil.isBlank(dto.getEmail())) {
            return Result.fail("手机号和邮箱至少填写一项！");
        }

        // 含逻辑删除记录，避免唯一索引冲突
        if (StrUtil.isNotBlank(dto.getPhone()) && userMapper.selectIdByPhone(dto.getPhone()) != null) {
            return Result.fail("该手机号已被注册！");
        }

        // 含逻辑删除记录，避免唯一索引冲突
        if (StrUtil.isNotBlank(dto.getEmail()) && userMapper.selectIdByEmail(dto.getEmail()) != null) {
            return Result.fail("该邮箱已被注册！");
        }

        User user = new User();
        user.setUserName(dto.getUserName());
        user.setPhone(StrUtil.isBlank(dto.getPhone()) ? null : dto.getPhone());
        user.setEmail(StrUtil.isBlank(dto.getEmail()) ? null : dto.getEmail());
        user.setPassWord(PasswordUtil.encode(dto.getPassword()));
        user.setIsAdmin(dto.getIsAdmin() != null ? dto.getIsAdmin() : 0);
        user.setCreateTime(LocalDateTime.now());

        boolean success = save(user);
        if (!success) {
            throw new BusinessException("新增用户失败，请稍后重试！");
        }

        log.info("管理员 [{}] 新增了用户 [{}]（{}）",
                UserHolder.getUser().getId(), user.getId(),
                user.getIsAdmin() == 1 ? "管理员" : "普通用户");
        return Result.okMsg("新增用户成功");
    }

    /**
     * 分页查询用户列表，支持手机号 / 邮箱模糊搜索与管理员身份筛选。
     */
    @Override
    public Result getUserList(Integer current, Integer size, String phone, String email, Integer isAdmin) {
        Page<User> page = new Page<>(current, size);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(phone)) {
            wrapper.like(User::getPhone, phone);
        }
        if (StrUtil.isNotBlank(email)) {
            wrapper.like(User::getEmail, email);
        }
        if (isAdmin != null) {
            wrapper.eq(User::getIsAdmin, isAdmin);
        }
        wrapper.orderByAsc(User::getCreateTime);
        Page<User> userPage = userMapper.selectPage(page, wrapper);
        List<UserVO> voList = new ArrayList<>();
        for (User user : userPage.getRecords()) {
            UserVO vo = new UserVO();
            vo.setId(user.getId());
            vo.setUserName(user.getUserName());
            vo.setPhone(user.getPhone());
            vo.setEmail(user.getEmail());
            vo.setIsAdmin(user.getIsAdmin());
            vo.setAddress(user.getAddress());
            vo.setCreateTime(user.getCreateTime());
            voList.add(vo);
        }
        Page<UserVO> resultPage = new Page<>();
        resultPage.setCurrent(userPage.getCurrent());
        resultPage.setSize(userPage.getSize());
        resultPage.setTotal(userPage.getTotal());
        resultPage.setRecords(voList);
        return Result.ok(resultPage);

    }

    /**
     * 修改用户身份；改后清理该用户 Redis 登录态，需重新登录生效。
     */
    @Override
    @Transactional
    public Result editUser(EditUserFormDTO dto) {
        if (dto == null) {
            return Result.fail("请完善修改信息！");
        }
        if (Objects.equals(dto.getId(), UserHolder.getUser().getId())) {
            return Result.fail("不允许修改本人信息！");
        }
        if (dto.getIsAdmin() == null) {
            return Result.fail("请选择用户身份！");
        }
        User user = getById(dto.getId());
        if (user == null) {
            return Result.fail("用户不存在！");
        }
        user.setIsAdmin(dto.getIsAdmin());
        boolean success = updateById(user);
        if (!success) {
            throw new BusinessException("修改用户失败，请稍后重试！");
        }
        userService.clearAllLoginStates(dto.getId());
        log.warn("用户 [{}] 身份被修改为 {}，已清理全部登录态", dto.getId(), dto.getIsAdmin() == 1 ? "管理员" : "普通用户");
        return Result.okMsg("修改成功，用户需重新登录");
    }

    @Override
    @Transactional
    public Result resetPassword(AdminResetPasswordDTO dto) {
        if (dto == null || dto.getId() == null) {
            return Result.fail("参数错误！");
        }
        if (StrUtil.isBlank(dto.getNewPassword())) {
            return Result.fail("请输入重置后的密码！");
        }
        User user = getById(dto.getId());
        if (user == null) {
            return Result.fail("用户不存在！");
        }
        user.setPassWord(PasswordUtil.encode(dto.getNewPassword()));
        boolean success = updateById(user);
        if (!success) {
            throw new BusinessException("重置密码失败，请稍后重试！");
        }
        userService.clearAllLoginStates(dto.getId());
        log.warn("用户 [{}] 密码被管理员重置，已清理全部登录态", dto.getId());
        return Result.okMsg("密码重置成功，用户需重新登录");
    }

    /**
     * 逻辑删除用户，同步置空 phone/email 释放唯一索引，使账号可被复用；删除后清理其 Redis 登录态。
     */
    @Override
    @Transactional
    public Result deleteUserById(Long userId) {
        User user = getById(userId);
        if (user == null) {
            return Result.fail("用户不存在！");
        }
        Long id = UserHolder.getUser().getId();
        if (Objects.equals(id, userId)) {
            return Result.fail("请勿删除自己！");
        }
        // 逻辑删除 + 置空 phone/email，释放唯一索引，允许被删账号的号码/邮箱复用
        int rows = userMapper.logicDeleteAndRelease(userId);
        if (rows == 0) {
            throw new BusinessException("删除用户失败，请稍后重试！");
        }
        userService.clearAllLoginStates(userId);
        log.info("用户 [{}] 被删除（已释放手机号/邮箱占用），已清理 Redis 全部登录态", userId);
        return Result.okMsg("删除成功");
    }

    /**
     * 简易用户列表：按用户名或邮箱模糊搜索，最多返回 200 条。
     */
    @Override
    public Result getSimpleUserList(String keyword) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(User::getId, User::getUserName, User::getEmail);
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.and(w -> w.like(User::getUserName, keyword)
                    .or().like(User::getEmail, keyword));
        }
        wrapper.orderByDesc(User::getCreateTime);
        wrapper.last("LIMIT 200");
        List<User> users = userMapper.selectList(wrapper);
        List<UserSimpleDTO> list = users.stream()
                .map(u -> new UserSimpleDTO(u.getId(), u.getUserName(), u.getEmail()))
                .collect(java.util.stream.Collectors.toList());
        return Result.ok(list);
    }

}
