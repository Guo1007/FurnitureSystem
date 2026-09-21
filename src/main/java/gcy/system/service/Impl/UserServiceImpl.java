package gcy.system.service.Impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.useragent.UserAgent;
import cn.hutool.http.useragent.UserAgentUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import gcy.system.entity.dto.*;
import gcy.system.entity.pojo.IconReviewLog;
import gcy.system.entity.pojo.NicknameReviewLog;
import gcy.system.entity.pojo.User;
import gcy.system.exception.BusinessException;
import gcy.system.integration.EmailService;
import gcy.system.mapper.IconReviewLogMapper;
import gcy.system.mapper.NicknameReviewLogMapper;
import gcy.system.mapper.UserMapper;
import gcy.system.service.IUserService;
import gcy.system.utils.PasswordUtil;
import gcy.system.utils.RedisConstants;
import gcy.system.utils.RegexUtils;
import gcy.system.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static gcy.system.utils.RedisConstants.*;

/**
 * 用户服务实现类，提供用户注册、登录、登出、密码管理、个人信息修改等核心业务逻辑的实现。
 * <p>
 * 该类继承 MyBatis-Plus 的 ServiceImpl，基于 UserMapper 进行数据库操作，
 * 同时整合 Redis 缓存管理用户登录态与验证码，通过 EmailService 发送邮件验证码。
 * </p>
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    private static final String USER_NAME_PREFIX = "user_";

    private final StringRedisTemplate stringRedisTemplate;

    private final EmailService emailService;

    private final RocketMQTemplate rocketMQTemplate;

    private final NicknameReviewLogMapper nicknameReviewLogMapper;

    private final IconReviewLogMapper iconReviewLogMapper;

    private static final DefaultRedisScript<String> GET_AND_DEL_SCRIPT;

    static {
        GET_AND_DEL_SCRIPT = new DefaultRedisScript<>();
        GET_AND_DEL_SCRIPT.setResultType(String.class);
        GET_AND_DEL_SCRIPT.setScriptText(
                "local val = redis.call('GET', KEYS[1]) " +
                        "if val then redis.call('DEL', KEYS[1]) end " +
                        "return val");
    }

    /**
     * 验证码类型枚举，用于区分登录、注册、重置密码三种场景，每种场景对应不同的 Redis 键前缀。
     *
     * @author 郭名城
     * @date 2026-07-30
     */
    private enum CodeType {
        /**
         * 登录验证码
         */
        LOGIN(LOGIN_CODE_KEY),
        /**
         * 注册验证码
         */
        REGISTER(REGISTER_CODE_KEY),
        /**
         * 重置密码验证码
         */
        RESET_PASSWORD(RESET_PASSWORD_CODE_KEY),
        /**
         * 修改邮箱验证码
         */
        UPDATE_EMAIL(UPDATE_EMAIL_CODE_KEY);
        private final String keyPrefix;

        CodeType(String prefix) {
            this.keyPrefix = prefix;
        }

        /**
         * 根据账号拼接 Redis 缓存键。
         *
         * @param account 用户账号（邮箱或手机号）
         * @return 拼接后的 Redis 缓存键
         */
        public String getKey(String account) {
            return keyPrefix + account;
        }
    }

    /**
     * 判断给定的账号是否为邮箱格式（包含@符号即为邮箱）。
     *
     * @param account 用户输入的账号字符串
     * @return 如果是邮箱格式返回 true，否则返回 false
     */
    private static boolean isEmail(String account) {
        return account != null && account.contains("@");
    }

    /**
     * 向指定账号发送验证码，根据账号类型（邮箱或手机号）选择不同的发送方式。
     * <p>
     * 该方法会先校验账号格式，生成6位随机验证码并存入 Redis（设置有效期），
     * 若为邮箱则调用邮件服务发送验证码，若为手机号则仅记录日志。
     * </p>
     *
     * @param account 接收验证码的账号（邮箱或手机号）
     * @param type    验证码类型，决定 Redis 键前缀和有效期
     * @return 操作结果，成功返回 Result.ok()
     */
    private Result sendCode(String account, CodeType type) {
        Assert.isTrue(StrUtil.isNotBlank(account), "账号不能为空");
        String code = RandomUtil.randomNumbers(6);
        if (isEmail(account)) {
            Assert.isTrue(RegexUtils.isEmailValid(account), "邮箱格式有误！");
        } else {
            Assert.isTrue(!RegexUtils.isPhoneInvalid(account), "手机号格式有误！");
        }
        Long ttl = type == CodeType.LOGIN ? LOGIN_CODE_TTL : REGISTER_CODE_TTL;
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(type.getKey(account), code, ttl, TimeUnit.MINUTES);
        Assert.isTrue(Boolean.TRUE.equals(success), "操作过于频繁，请稍后再试");
        if (isEmail(account)) {
            String action;
            if (type == CodeType.LOGIN) {
                action = "登录";
            } else if (type == CodeType.REGISTER) {
                action = "注册";
            } else if (type == CodeType.RESET_PASSWORD) {
                action = "重置密码";
            } else {
                action = "修改邮箱";
            }
            emailService.sendVerifyCode(account, code, action, ttl);
        } else {
            log.debug("{}验证码发送成功", type.name());
        }
        return Result.ok();
    }

    /**
     * 发送注册验证码到用户邮箱。
     * <p>
     * 从注册表单 DTO 中提取邮箱地址，调用通用的验证码发送逻辑，标记为注册类型。
     * </p>
     *
     * @param dto 注册表单数据传输对象，包含邮箱等注册信息
     * @return 操作结果，成功返回 Result.ok()
     */
    @Override
    public Result sendRegisterCode(RegisterFormDTO dto) {
        return sendCode(dto.getEmail(), CodeType.REGISTER);
    }

    /**
     * 发送登录验证码到用户账号（邮箱或手机号）。
     * <p>
     * 从登录表单 DTO 中提取账号，调用通用的验证码发送逻辑，标记为登录类型。
     * </p>
     *
     * @param dto 登录表单数据传输对象，包含账号信息
     * @return 操作结果，成功返回 Result.ok()
     */
    @Override
    public Result sendLoginCode(LoginFormDTO dto) {
        // 验证码登录仅支持邮箱（未接入短信服务，手机号验证码无法下发）
        Assert.isTrue(isEmail(dto.getAccount()), "验证码登录仅支持邮箱账号，手机号请使用密码登录");
        return sendCode(dto.getAccount(), CodeType.LOGIN);
    }

    /**
     * 发送修改邮箱验证码到目标新邮箱。
     * <p>
     * 校验新邮箱格式且未被其他账号绑定，通过后向新邮箱发送验证码，供修改邮箱时校验归属。
     * </p>
     *
     * @param email 目标新邮箱
     * @return 操作结果，成功返回 Result.ok()
     */
    @Override
    public Result sendUpdateEmailCode(String email) {
        Assert.isTrue(StrUtil.isNotBlank(email), "请输入新邮箱");
        Assert.isTrue(RegexUtils.isEmailValid(email), "邮箱格式有误！");
        // 唯一性预检需含逻辑删除记录，与改绑时的检查保持一致
        if (baseMapper.selectIdByEmail(email) != null) {
            return Result.fail("该邮箱已被其他账号绑定");
        }
        return sendCode(email, CodeType.UPDATE_EMAIL);
    }

    /**
     * 发送重置密码验证码到注册邮箱。
     * <p>
     * 校验邮箱是否已注册且已设置密码，校验通过后调用通用验证码发送逻辑。
     * </p>
     *
     * @param dto 重置密码表单数据传输对象，包含邮箱信息
     * @return 操作结果，成功返回 Result.ok()
     */
    @Override
    public Result sendResetCode(ResetPasswordFormDTO dto) {
        String email = dto.getEmail();
        Assert.isTrue(StrUtil.isNotBlank(email), "请输入邮箱");
        Assert.isTrue(RegexUtils.isEmailValid(email), "邮箱格式有误！");
        User user = query().eq("email", email).one();
        Assert.notNull(user, "该邮箱未注册");
        Assert.isTrue(StrUtil.isNotBlank(user.getPassWord()), "该账户未设置密码，请使用验证码登录后设置");
        return sendCode(email, CodeType.RESET_PASSWORD);
    }

    /**
     * 重置用户密码。
     * <p>
     * 校验邮箱、验证码、新密码格式及两次密码一致性，验证码通过 Lua 脚本原子性地从 Redis 读取并删除。
     * 密码重置成功后清除该用户的所有登录态，强制其重新登录。
     * </p>
     *
     * @param dto 重置密码表单数据传输对象，包含邮箱、验证码、新密码和确认密码
     * @return 操作结果，成功返回包含提示信息的 Result
     */
    @Override
    @Transactional
    public Result resetPassword(ResetPasswordFormDTO dto) {
        String email = dto.getEmail();
        String code = dto.getCode();
        String newPassword = dto.getNewPassword();
        String confirmPassword = dto.getConfirmPassword();
        Assert.isTrue(StrUtil.isNotBlank(email), "邮箱不能为空");
        Assert.isTrue(RegexUtils.isEmailValid(email), "邮箱格式有误！");
        Assert.isTrue(!StrUtil.isBlank(code), "请输入验证码");
        Assert.isTrue(RegexUtils.isPasswordValid(newPassword), "密码格式错误！");
        Assert.isTrue(newPassword.equals(confirmPassword), "两次密码不一致");
        String cacheCode = stringRedisTemplate.execute(GET_AND_DEL_SCRIPT,
                Collections.singletonList(CodeType.RESET_PASSWORD.getKey(email)));
        Assert.isTrue(!StrUtil.isBlank(cacheCode), "验证码已过期或未发送");
        Assert.isTrue(code.equals(cacheCode), "验证码错误");
        User user = query().eq("email", email).one();
        Assert.notNull(user, "用户不存在");
        user.setPassWord(PasswordUtil.encode(newPassword));
        boolean success = updateById(user);
        Assert.isTrue(success, "重置密码失败，请稍后重试");
        // 清除该用户所有登录态，强制重新登录
        clearAllLoginStates(user.getId());
        log.info("用户 [{}] 重置密码成功，已清理全部设备登录态", user.getId());
        return Result.okMsg("密码重置成功");
    }

    /**
     * 用户登录，支持验证码登录和密码登录两种方式。
     * <p>
     * 根据请求参数中是否提供验证码或密码，分别调用验证码登录流程或密码登录流程。
     * 验证码登录在用户不存在时会自动创建账号，密码登录则要求用户必须已注册且设置了密码。
     * 登录成功后将用户信息存入 Redis 并返回 Token。
     * </p>
     *
     * @param loginFormDTO 登录表单数据传输对象，包含账号、验证码和密码
     * @return 操作结果，成功返回包含 Token 的 Result
     * @throws IllegalArgumentException 当未提供验证码且未提供密码时抛出
     */
    @Override
    public Result login(LoginFormDTO loginFormDTO) {
        Assert.notNull(loginFormDTO, "请求参数不能为空");
        String account = loginFormDTO.getAccount();
        String code = loginFormDTO.getCode();
        String passWord = loginFormDTO.getPassWord();
        Assert.isTrue(StrUtil.isNotBlank(account), "请输入邮箱或手机号");
        if (StrUtil.isNotBlank(code)) {
            // 验证码登录仅支持邮箱（未接入短信服务，手机号验证码无法下发）
            if (!isEmail(account)) {
                return Result.fail("验证码登录仅支持邮箱账号，手机号请使用密码登录");
            }
            return loginByCode(account, code);
        } else if (StrUtil.isNotBlank(passWord)) {
            return loginByPwd(account, passWord);
        } else {
            throw new IllegalArgumentException("请输入验证码或密码");
        }
    }

    /**
     * 用户登出，清除 Redis 中的用户登录信息。
     * <p>
     * 从 UserHolder 中获取当前用户和 Token，删除 Redis 中对应的用户缓存和 Token 映射，
     * 最后清除本地线程变量中的用户信息。
     * </p>
     *
     * @return 操作结果，成功返回 Result.ok()
     */
    @Override
    public Result logout() {
        UserDTO user = UserHolder.getUser();
        String token = UserHolder.getToken();
        if (StrUtil.isNotBlank(token)) {
            // 1. 删掉 token 对应的 Hash
            stringRedisTemplate.delete(LOGIN_USER_KEY + token);
        }
        if (user != null && user.getId() != null) {
            Long userId = user.getId();
            String setKey = LOGIN_USER_TOKENS_SET + userId;
            // 2. 把当前 token 从 Set 里移除，其他设备不受影响
            if (StrUtil.isNotBlank(token)) {
                stringRedisTemplate.opsForSet().remove(setKey, token);
            }
        }
        UserHolder.removeUser();
        log.info("用户退出登录成功，userId={}", user != null ? user.getId() : null);
        return Result.ok();
    }

    /**
     * 用户注册，通过邮箱验证码完成账号注册。
     * <p>
     * 校验邮箱格式、密码格式及一致性、验证码有效性，检查邮箱是否已被注册，
     * 通过后创建用户记录（自动生成随机用户名），将加密后的密码存入数据库。
     * </p>
     *
     * @param registerFormDTO 注册表单数据传输对象，包含邮箱、密码、确认密码和验证码
     * @return 操作结果，成功返回包含提示信息的 Result
     */
    @Override
    @Transactional
    public Result register(RegisterFormDTO registerFormDTO) {
        Assert.notNull(registerFormDTO, "请输入完整信息！");
        String email = registerFormDTO.getEmail();
        String code = registerFormDTO.getCode();
        String password = registerFormDTO.getPassword();
        String confirmPassword = registerFormDTO.getConfirmPassword();
        Assert.isTrue(RegexUtils.isEmailValid(email), "邮箱格式有误！");
        Assert.isTrue(RegexUtils.isPasswordValid(password), "密码格式错误！");
        Assert.isTrue(!StrUtil.isBlank(password), "密码不能为空！");
        Assert.isTrue(!StrUtil.isBlank(confirmPassword), "确认密码不能为空！");
        Assert.isTrue(password.equals(confirmPassword), "两次密码不一致！");
        Assert.isTrue(!StrUtil.isBlank(code), "请输入邮箱验证码！");
        String cacheCode = stringRedisTemplate.execute(GET_AND_DEL_SCRIPT,
                Collections.singletonList(CodeType.REGISTER.getKey(email)));
        Assert.isTrue(!StrUtil.isBlank(cacheCode), "验证码已过期或未发送");
        Assert.isTrue(code.equals(cacheCode), "验证码错误");
        // 邮箱唯一性检查需含逻辑删除记录，避免已注销账号仍占用唯一索引导致插入冲突
        Assert.isTrue(baseMapper.selectIdByEmail(email) == null, "该邮箱已被注册，请直接登录");
        String nickName = RandomUtil.randomString(10);
        String userName = USER_NAME_PREFIX + nickName;
        User user = new User();
        user.setEmail(email);
        user.setUserName(userName);
        user.setPassWord(PasswordUtil.encode(password));
        user.setCreateTime(LocalDateTime.now());
        save(user);
        log.info("用户注册成功: userId={}, email={}", user.getId(), email);
        return Result.okMsg("注册成功");
    }

    /**
     * 修改当前登录用户的密码。
     * <p>
     * 如果用户已设置过密码，则需要提供旧密码进行验证；如果用户之前使用验证码登录且未设置密码，
     * 则可直接设置新密码。密码修改成功后清除该用户的登录态，强制重新登录。
     * </p>
     *
     * @param dto 密码修改表单数据传输对象，包含旧密码、新密码和确认密码
     * @return 操作结果，成功返回包含提示信息的 Result
     * @throws BusinessException 当密码为空、两次密码不一致、密码格式错误或旧密码验证失败时抛出
     */
    @Override
    @Transactional
    public Result updatePassword(PasswordFormDTO dto) {
        UserDTO userDTO = UserHolder.getUser();
        String newPassword = dto.getNewPassword();
        String confirmPassword = dto.getConfirmPassword();
        if (newPassword == null || confirmPassword == null) {
            throw new BusinessException("密码不能为空");
        }
        if (!(newPassword.equals(confirmPassword))) {
            throw new BusinessException("两次密码输入不一致！");
        }
        Assert.isTrue(RegexUtils.isPasswordValid(dto.getNewPassword()), "新密码格式错误！");
        String oldPassword = dto.getOldPassword();
        // 账号维度：改密码旧密码错误同样计入失败次数（与登录共用锁定机制）
        String account = resolveAccount(userDTO);
        if (isAccountLocked(account)) {
            throw new BusinessException("认证失败次数过多，账号已锁定，请5分钟后重试");
        }
        // 从 DB 重新查询密码，不再依赖 Redis 缓存中的 UserDTO（缓存中 passWord 已为 null）
        User dbUser = getById(userDTO.getId());
        Assert.notNull(dbUser, "用户不存在");
        String dbPassword = dbUser.getPassWord();
        if (StrUtil.isNotBlank(oldPassword)) {
            if (StrUtil.isBlank(dbPassword)) {
                throw new BusinessException("该账户未设置密码，无需输入旧密码！");
            }
            if (!PasswordUtil.matches(oldPassword, dbPassword)) {
                long remain = recordLoginFail(account);
                if (remain == 0) {
                    throw new BusinessException("认证失败次数过多，账号已锁定，请5分钟后重试");
                }
                throw new BusinessException("旧密码输入错误，还可尝试 " + remain + " 次");
            }
        } else {
            if (StrUtil.isNotBlank(dbPassword)) {
                throw new BusinessException("请输入旧密码！");
            }
        }
        clearLoginFail(account);
        String password = PasswordUtil.encode(dto.getNewPassword());
        // 只更新密码字段，避免用缓存中的旧资料覆盖用户在其他设备改过的邮箱/昵称等
        User user = new User();
        user.setId(userDTO.getId());
        user.setPassWord(password);
        boolean success = updateById(user);
        if (!success) {
            throw new BusinessException("设置失败，请稍后重试或反馈！");
        }
        // 清理所有设备的登录态（Set 遍历删除）
        clearAllLoginStates(userDTO.getId());
        log.info("用户 [{}] 修改密码成功，已清理全部设备登录态", userDTO.getId());
        return Result.okMsg("密码修改成功，请重新登录");
    }

    /**
     * 更新当前登录用户的个人信息（如邮箱、昵称等）。
     * <p>
     * 仅更新 DTO 中非空的字段，不允许通过此方法修改管理员标识。
     * 更新邮箱时会检查是否已被其他账号绑定。更新成功后将最新用户信息同步到 Redis 缓存。
     * </p>
     *
     * @param updateFormDTO 用户信息更新表单数据传输对象，包含可选的邮箱、昵称等字段
     * @return 操作结果，成功返回 Result.ok()
     * @throws BusinessException 当用户未登录、邮箱已被占用或更新失败时抛出
     */
    @Override
    @Transactional
    public Result updateUser(UpdateFormDTO updateFormDTO) {
        Long userId = UserHolder.getUser().getId();
        String token = UserHolder.getToken();
        if (userId == null) {
            throw new BusinessException("请检查登录状态，可尝试重新登录！");
        }
        // 查询当前用户数据，用于比对昵称和头像是否变更
        User dbUser = getById(userId);
        Assert.notNull(dbUser, "用户不存在");

        User user = new User();
        user.setId(userId);
        BeanUtil.copyProperties(updateFormDTO, user, CopyOptions.create()
                .setIgnoreNullValue(true));
        user.setIsAdmin(null);

        // 昵称变更：走 AI 审核
        String newNickname = updateFormDTO.getUserName();
        boolean nicknameChanged = StrUtil.isNotBlank(newNickname) && !newNickname.equals(dbUser.getUserName());
        if (nicknameChanged) {
            user.setUserName(dbUser.getUserName()); // 保留旧昵称，审核通过后再更新
            user.setPendingNickname(newNickname);
            user.setNicknameReviewStatus(1); // 待AI审核
        }

        // 头像变更：走人工审核
        String newIcon = updateFormDTO.getIcon();
        boolean iconChanged = StrUtil.isNotBlank(newIcon) && !newIcon.equals(dbUser.getIcon());
        if (iconChanged) {
            user.setIcon(dbUser.getIcon()); // 保留旧头像，审核通过后再更新
            user.setPendingIcon(newIcon);
            user.setIconReviewStatus(1); // 待审核
        }

        if (StrUtil.isNotBlank(updateFormDTO.getEmail()) && !updateFormDTO.getEmail().equals(dbUser.getEmail())) {
            Long existId = baseMapper.selectIdByEmail(updateFormDTO.getEmail());
            if (existId != null && !existId.equals(userId)) {
                throw new BusinessException("该邮箱已被其他账号绑定");
            }
            String emailCode = updateFormDTO.getEmailCode();
            Assert.isTrue(StrUtil.isNotBlank(emailCode), "请输入新邮箱验证码");
            String cacheCode = stringRedisTemplate.execute(GET_AND_DEL_SCRIPT,
                    Collections.singletonList(UPDATE_EMAIL_CODE_KEY + updateFormDTO.getEmail()));
            Assert.isTrue(!StrUtil.isBlank(cacheCode), "验证码已过期或未发送");
            Assert.isTrue(emailCode.equals(cacheCode), "验证码错误");
        }
        boolean success = updateById(user);
        if (!success) {
            throw new BusinessException("更新失败，请尝试重启系统！");
        }

        // 写入审核记录
        if (nicknameChanged) {
            NicknameReviewLog log = new NicknameReviewLog();
            log.setUserId(userId);
            log.setOldNickname(dbUser.getUserName());
            log.setNewNickname(newNickname);
            log.setStatus(1);
            nicknameReviewLogMapper.insert(log);
        }
        if (iconChanged) {
            IconReviewLog log = new IconReviewLog();
            log.setUserId(userId);
            log.setOldIcon(dbUser.getIcon());
            log.setNewIcon(newIcon);
            log.setStatus(1);
            iconReviewLogMapper.insert(log);
        }

        // 昵称变更：发送 AI 审核消息（事务提交后再发，避免消费者读到未提交数据）
        if (nicknameChanged) {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        sendNicknameReviewMq(userId, newNickname);
                    }
                });
            } else {
                sendNicknameReviewMq(userId, newNickname);
            }
        }

        User updatedUser = getById(userId);
        UserDTO userDTO = BeanUtil.copyProperties(updatedUser, UserDTO.class);
        userDTO.setPassWord(null);
        userDTO.setHasPassword(StrUtil.isNotBlank(updatedUser.getPassWord()));
        saveUserToRedis(userDTO, token);
        String setKey = LOGIN_USER_TOKENS_SET + userId;
        stringRedisTemplate.expire(setKey, LOGIN_USER_TTL, TimeUnit.SECONDS);
        return Result.ok();
    }

    /**
     * 发送昵称 AI 审核消息到 RocketMQ。
     */
    private void sendNicknameReviewMq(Long userId, String nickname) {
        try {
            Map<String, Object> msg = new HashMap<>();
            msg.put("userId", userId);
            msg.put("nickname", nickname);
            rocketMQTemplate.convertAndSend("nickname-review-topic", JSONUtil.toJsonStr(msg));
            log.info("昵称审核消息已发送: userId={}, nickname={}", userId, nickname);
        } catch (Exception e) {
            log.error("发送昵称审核消息失败: userId={}", userId, e);
        }
    }

    /**
     * 注销当前登录用户的账号（不可逆）。
     * <p>
     * 逻辑删除账号并置空手机号/邮箱，释放唯一索引占用，号码/邮箱可被重新注册；
     * 同时清理该用户全部登录态使其立即失效。历史订单、评价等数据保留（逻辑删除仅标记用户记录）。
     * </p>
     *
     * @return 操作结果，包含成功状态及提示信息
     */
    @Override
    @Transactional
    public Result deactivate() {
        UserDTO userDTO = UserHolder.getUser();
        if (userDTO == null || userDTO.getId() == null) {
            throw new BusinessException("请先登录");
        }
        Long userId = userDTO.getId();
        // 逻辑删除 + 置空手机号/邮箱，释放唯一索引占用，号码/邮箱可复用
        int rows = baseMapper.logicDeleteAndRelease(userId);
        if (rows == 0) {
            throw new BusinessException("账号不存在或已注销");
        }
        // 清理该用户全部登录态（含当前 token），使其立即失效
        clearAllLoginStates(userId);
        UserHolder.removeUser();
        log.info("用户 [{}] 主动注销账号", userId);
        return Result.okMsg("账号已注销，期待再次相遇");
    }

    /**
     * 将用户 DTO 数据转为 Map 存入 Redis Hash，统一收敛 beanToMap 逻辑。
     * <p>
     * 将 UserDTO 的所有非空字段转换为字符串后存储到 Redis Hash 结构中，
     * 并设置过期时间。调用方需确保 userDTO 中的敏感字段（如密码）已在复制时被忽略。
     * </p>
     *
     * @param userDTO 需要缓存到 Redis 的用户数据传输对象
     * @param token   当前用户的登录 Token，用于拼接 Redis 缓存键
     */
    private void saveUserToRedis(UserDTO userDTO, String token) {
        // 修问题#1：确保密码哈希不写入 Redis
        userDTO.setPassWord(null);

        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> {
                            if (fieldValue == null) return null;
                            return fieldValue.toString();
                        }));

        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, userMap);
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.SECONDS);
    }

    /**
     * 为已认证用户生成 Token 并存入 Redis，返回登录成功结果。
     * <p>
     * 将用户实体转为 UserDTO，生成 UUID Token，将用户信息存入 Redis Hash，
     * 同时建立 Token 与用户 ID 的双向映射关系。
     * </p>
     *
     * @param user 已通过验证的用户实体对象
     * @return 操作结果，成功返回包含 Token 字符串的 Result
     */
    private Result getAndReturnToken(User user) {
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        userDTO.setHasPassword(StrUtil.isNotBlank(user.getPassWord()));
        String token = UUID.randomUUID(true).toString();
        String clientType = resolveClientType();

        Long userId = user.getId();
        String setKey = LOGIN_USER_TOKENS_SET + userId;

        // 同端互踢：登录前先清理该用户同类型端（PC/MOBILE）的旧会话，不同端可并存
        evictSameClientTypeTokens(setKey, clientType, token);

        saveUserToRedis(userDTO, token);
        // 端类型写入登录态 Hash，供同端互踢识别
        stringRedisTemplate.opsForHash().put(LOGIN_USER_KEY + token, LOGIN_CLIENT_TYPE_FIELD, clientType);

        // 把新 token SADD 进用户的 Token Set
        stringRedisTemplate.opsForSet().add(setKey, token);
        // 给 Set Key 也设置同样的 TTL
        stringRedisTemplate.expire(setKey, LOGIN_USER_TTL, TimeUnit.SECONDS);

        log.info("用户登录成功: userId={}, email={}, clientType={}", user.getId(), user.getEmail(), clientType);
        return Result.ok(token);
    }

    /**
     * 从当前请求 User-Agent 解析端类型（PC / MOBILE），解析失败视为 PC。
     */
    private String resolveClientType() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return "PC";
        }
        String ua = attrs.getRequest().getHeader("User-Agent");
        if (StrUtil.isBlank(ua)) {
            return "PC";
        }
        try {
            UserAgent agent = UserAgentUtil.parse(ua);
            return agent.isMobile() ? "MOBILE" : "PC";
        } catch (Exception e) {
            log.warn("端类型识别失败，按PC处理。User-Agent={}", ua);
            return "PC";
        }
    }

    /**
     * 清理指定用户下与当前登录端类型相同的旧会话 token（同端互踢）。
     * 未记录端类型的历史 token 不处理，避免误踢已有会话。
     *
     * @param setKey     用户 Token Set 的 key
     * @param clientType 本次登录的端类型（PC / MOBILE）
     * @param newToken   本次登录的新 token（跳过，不误删自身）
     */
    private void evictSameClientTypeTokens(String setKey, String clientType, String newToken) {
        Set<String> tokens = stringRedisTemplate.opsForSet().members(setKey);
        if (tokens == null || tokens.isEmpty()) {
            return;
        }
        for (String t : tokens) {
            if (StrUtil.equals(t, newToken)) {
                continue;
            }
            Object type = stringRedisTemplate.opsForHash().get(LOGIN_USER_KEY + t, LOGIN_CLIENT_TYPE_FIELD);
            if (type != null && clientType.equals(String.valueOf(type))) {
                stringRedisTemplate.delete(LOGIN_USER_KEY + t);
                // 写入被踢标记，供过滤器区分「登录已过期」与「被强制下线」
                stringRedisTemplate.opsForValue().set(LOGIN_KICKED_KEY + t, "1", LOGIN_KICKED_TTL, TimeUnit.SECONDS);
                stringRedisTemplate.opsForSet().remove(setKey, t);
                log.info("同端互踢已下线旧会话: token={}, clientType={}", t, clientType);
            }
        }
    }

    /**
     * 通过验证码进行登录，验证码正确且用户不存在时自动创建新账号。
     * <p>
     * 使用 Lua 脚本原子性地从 Redis 读取并删除验证码进行校验，
     * 根据账号查找用户，若不存在则自动注册并直接登录。
     * </p>
     *
     * @param account 用户账号（邮箱或手机号）
     * @param code    用户输入的验证码
     * @return 操作结果，成功返回包含 Token 的 Result
     */
    private Result loginByCode(String account, String code) {
        // 账号被锁定则直接拒绝
        if (isAccountLocked(account)) {
            return Result.fail("认证失败次数过多，账号已锁定，请5分钟后重试");
        }
        String cacheCode = stringRedisTemplate.execute(GET_AND_DEL_SCRIPT,
                Collections.singletonList(LOGIN_CODE_KEY + account));
        if (StrUtil.isBlank(cacheCode)) {
            return Result.fail("验证码已过期或未发送");
        }
        if (!code.equals(cacheCode)) {
            long remain = recordLoginFail(account);
            return Result.fail(remain == 0
                    ? "认证失败次数过多，账号已锁定，请5分钟后重试"
                    : "验证码错误，还可尝试 " + remain + " 次");
        }
        User user = lookupUser(account);
        if (user == null) {
            // 账号可能曾注册后被逻辑删除，唯一索引残留会导致建号冲突，需拦截
            Long occupyId = isEmail(account)
                    ? baseMapper.selectIdByEmail(account)
                    : baseMapper.selectIdByPhone(account);
            if (occupyId != null) {
                return Result.fail("该账号已注销，如需使用请联系管理员");
            }
            user = createUserWithAccount(account);
        }
        clearLoginFail(account);
        return getAndReturnToken(user);
    }

    /**
     * 通过密码进行登录，校验账号是否存在、密码是否匹配。
     * <p>
     * 要求用户已注册且已设置密码，密码需与数据库中存储的加密密码匹配。
     * </p>
     *
     * @param account  用户账号（邮箱或手机号）
     * @param password 用户输入的明文密码
     * @return 操作结果，成功返回包含 Token 的 Result
     */
    private Result loginByPwd(String account, String password) {
        // 账号被锁定则直接拒绝
        if (isAccountLocked(account)) {
            return Result.fail("认证失败次数过多，账号已锁定，请5分钟后重试");
        }
        User user = lookupUser(account);
        if (user == null) {
            // 账号不存在无密码可破解，不计数不锁定
            return Result.fail("用户不存在，请先注册");
        }
        if (StrUtil.isBlank(user.getPassWord())) {
            // 账号未设置密码（验证码登录），无试密码破解场景，不计数
            return Result.fail("该用户密码为空，请使用验证码登录后设置密码！");
        }
        if (!PasswordUtil.matches(password, user.getPassWord())) {
            long remain = recordLoginFail(account);
            return Result.fail(remain == 0
                    ? "认证失败次数过多，账号已锁定，请5分钟后重试"
                    : "密码错误，还可尝试 " + remain + " 次");
        }
        clearLoginFail(account);
        return getAndReturnToken(user);
    }

    /**
     * 根据账号（邮箱或手机号）从数据库中查找用户。
     * <p>
     * 如果账号包含@符号则按邮箱查询，否则按手机号查询。
     * </p>
     *
     * @param account 用户账号（邮箱或手机号）
     * @return 查找到的用户实体，若不存在则返回 null
     */
    private User lookupUser(String account) {
        if (isEmail(account)) {
            return query().eq("email", account).one();
        } else {
            return query().eq("phone", account).one();
        }
    }

    /**
     * 根据账号信息为新用户创建默认账号（用于首次验证码登录自动注册）。
     * <p>
     * 生成随机用户名（前缀为 user_），根据账号类型设置邮箱或手机号字段，
     * 创建时间设为当前时间，保存到数据库后返回用户实体。
     * </p>
     *
     * @param account 用户账号（邮箱或手机号）
     * @return 新创建并已保存到数据库的用户实体
     */
    private User createUserWithAccount(String account) {
        String nickName = RandomUtil.randomString(10);
        String userName = USER_NAME_PREFIX + nickName;
        User user = new User();
        user.setUserName(userName);
        user.setCreateTime(LocalDateTime.now());
        if (isEmail(account)) {
            user.setEmail(account);
        } else {
            user.setPhone(account);
        }
        save(user);
        return user;
    }

    // ==================== 认证失败锁定 ====================

    /**
     * 判断账号是否处于锁定状态（锁定 key 存在即锁定）。
     *
     * @param account 账号（邮箱/手机号）
     * @return 已锁定返回 true
     */
    private boolean isAccountLocked(String account) {
        if (StrUtil.isBlank(account)) return false;
        return stringRedisTemplate.hasKey(LOGIN_LOCK_KEY + account);
    }

    /**
     * 记录一次认证失败，达到上限则锁定账号。
     * <p>
     * 失败计数 key 带 5 分钟 TTL；达到 {@link RedisConstants#LOGIN_FAIL_LIMIT} 次后
     * 写入锁定 key（同样 5 分钟过期），锁定期间不再累计。
     * </p>
     *
     * @param account 账号（邮箱/手机号）
     */
    private long recordLoginFail(String account) {
        if (StrUtil.isBlank(account)) return LOGIN_FAIL_LIMIT;
        String failKey = LOGIN_FAIL_KEY + account;
        Long count = stringRedisTemplate.opsForValue().increment(failKey);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(failKey, LOGIN_LOCK_TTL, TimeUnit.SECONDS);
        }
        if (count != null && count >= LOGIN_FAIL_LIMIT) {
            stringRedisTemplate.opsForValue().set(LOGIN_LOCK_KEY + account, "1", LOGIN_LOCK_TTL, TimeUnit.SECONDS);
            log.warn("账号认证失败次数过多已锁定: account={}, count={}", account, count);
            return 0L;
        }
        return LOGIN_FAIL_LIMIT - (count == null ? 1L : count);
    }

    /**
     * 认证成功后清除失败计数与锁定标记。
     *
     * @param account 账号（邮箱/手机号）
     */
    private void clearLoginFail(String account) {
        if (StrUtil.isBlank(account)) return;
        stringRedisTemplate.delete(LOGIN_FAIL_KEY + account);
        stringRedisTemplate.delete(LOGIN_LOCK_KEY + account);
    }

    /**
     * 清理指定用户的全部登录态（遍历其 Token 集合，一个不漏）。
     * 修改密码、重置密码、注销及管理端编辑/删除用户均复用此逻辑。
     *
     * @param userId 目标用户ID
     */
    @Override
    public void clearAllLoginStates(Long userId) {
        String setKey = LOGIN_USER_TOKENS_SET + userId;
        Set<String> allTokens = stringRedisTemplate.opsForSet().members(setKey);
        if (allTokens != null && !allTokens.isEmpty()) {
            for (String t : allTokens) {
                stringRedisTemplate.delete(LOGIN_USER_KEY + t);
            }
        }
        stringRedisTemplate.delete(setKey);
    }

    /**
     * 从用户信息中解析账号（优先邮箱，其次手机号，最后用 ID）。
     *
     * @param user 用户信息
     * @return 账号标识
     */
    private String resolveAccount(UserDTO user) {
        if (user == null) return null;
        if (StrUtil.isNotBlank(user.getEmail())) return user.getEmail();
        if (StrUtil.isNotBlank(user.getPhone())) return user.getPhone();
        return user.getId() != null ? String.valueOf(user.getId()) : null;
    }
}
