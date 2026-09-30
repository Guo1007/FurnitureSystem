package gcy.system.service.Impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import gcy.system.entity.dto.Result;
import gcy.system.entity.dto.SendNotificationFormDTO;
import gcy.system.entity.dto.UserDTO;
import gcy.system.entity.pojo.Notification;
import gcy.system.entity.pojo.User;
import gcy.system.entity.pojo.UserNotification;
import gcy.system.entity.vo.NotificationVO;
import gcy.system.integration.EmailService;
import gcy.system.mapper.NotificationMapper;
import gcy.system.mapper.UserMapper;
import gcy.system.mapper.UserNotificationMapper;
import gcy.system.service.INotificationService;
import gcy.system.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 通知服务实现类，负责通知的发送、查询、已读标记、删除等核心业务逻辑。
 * 通过组合 UserMapper、UserNotificationMapper 和 EmailService 完成通知的
 * 持久化、用户关联状态管理以及邮件发送功能。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl extends ServiceImpl<NotificationMapper, Notification>
        implements INotificationService {

    private final UserMapper userMapper;

    private final UserNotificationMapper userNotificationMapper;

    private final EmailService emailService;

    /**
     * 发送通知。将通知内容持久化到数据库，并根据 DTO 中的 sendEmail 标志决定是否
     * 同时发送邮件通知。如果指定了目标用户则单独发送邮件，否则向所有已绑定邮箱的
     * 用户群发邮件。邮件发送失败不影响通知的保存结果。
     *
     * @param dto 发送通知的表单数据，包含标题、内容、类型、目标用户ID以及是否发送邮件等字段
     * @return 发送结果，包含操作状态和提示信息
     */
    @Override
    @Transactional
    public Result sendNotification(SendNotificationFormDTO dto) {
        Notification notification = new Notification();
        notification.setUserId(dto.getUserId());
        notification.setTitle(dto.getTitle());
        notification.setContent(dto.getContent());
        notification.setType(dto.getType() != null ? dto.getType() : "system");
        notification.setCreateTime(LocalDateTime.now());
        save(notification);

        if (Boolean.TRUE.equals(dto.getSendEmail())) {
            if (dto.getUserId() != null) {
                User target = userMapper.selectById(dto.getUserId());
                if (target == null) {
                    return Result.okMsg("通知已保存，但目标用户不存在，邮件未发送");
                }
                if (StrUtil.isBlank(target.getEmail())) {
                    return Result.okMsg("通知已保存，但该用户（" + target.getUserName() + "）未绑定邮箱，邮件未发送");
                }
                runAfterCommit(() ->
                        emailService.sendNotificationEmail(target.getEmail(), dto.getTitle(), dto.getContent()));
            } else {
                // 只投影 email 一列：原实现 selectList 会把整张 User 实体（含头像、简介等大字段）
                // 拉进 JVM，用户量上万时有 OOM 风险，且查询发生在事务内会拉长事务时间。
                List<Object> rows = userMapper.selectObjs(new LambdaQueryWrapper<User>()
                        .select(User::getEmail)
                        .isNotNull(User::getEmail)
                        .ne(User::getEmail, ""));
                List<String> emails = rows.stream()
                        .filter(Objects::nonNull)
                        .map(String::valueOf)
                        .filter(StrUtil::isNotBlank)
                        .distinct()
                        .collect(Collectors.toList());
                if (emails.isEmpty()) {
                    return Result.okMsg("通知已保存，但系统中没有已绑定邮箱的用户，邮件未发送");
                }
                // 邮件发送放到事务提交之后：SMTP 是慢 IO，不该占用数据库事务
                int total = emails.size();
                runAfterCommit(() -> sendBatchInChunks(emails, dto.getTitle(), dto.getContent()));
                log.info("通知邮件已排入提交后群发，覆盖 {} 位用户", total);
                return Result.okMsg("通知已保存，邮件将在提交后发送给 " + total + " 位用户");
            }
        }
        return Result.okMsg("发送成功");
    }

    /**
     * 分页查询当前用户的通知列表。仅返回该用户可见的通知（指定发给该用户的通知以及
     * 全局通知），同时排除用户已删除的通知，并标注每条通知的已读状态。
     *
     * @param current 当前页码，从 1 开始
     * @param size    每页记录数
     * @return 分页封装的通知列表，每条记录包含通知基本信息和已读状态
     */
    @Override
    public Result getUserNotifications(Integer current, Integer size) {
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();

        // 查询用户删除的通知ID集合
        Set<Long> deletedIds = getDeletedNotificationIds(userId);

        Page<Notification> page = new Page<>(current, size);
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.eq(Notification::getUserId, userId)
                .or().isNull(Notification::getUserId));
        // 排除用户已删除的通知
        if (!deletedIds.isEmpty()) {
            wrapper.notIn(Notification::getId, deletedIds);
        }
        wrapper.orderByDesc(Notification::getCreateTime);
        Page<Notification> result = page(page, wrapper);

        // 查询当前用户已读的通知ID集合
        List<Long> readNotificationIds = getReadNotificationIds(userId, result.getRecords());

        List<NotificationVO> voList = result.getRecords().stream()
                .map(n -> {
                    NotificationVO vo = BeanUtil.copyProperties(n, NotificationVO.class);
                    vo.setIsRead(readNotificationIds.contains(n.getId()));
                    return vo;
                })
                .collect(Collectors.toList());

        Page<NotificationVO> voPage = new Page<>();
        BeanUtil.copyProperties(result, voPage, "records");
        voPage.setRecords(voList);
        return Result.ok(voPage);
    }

    /**
     * 查询当前页通知中用户已读的通知ID。
     * 遍历传入的通知列表，在 user_notification 表中查找当前用户已标记为已读
     * 且未删除的记录，返回对应的通知ID列表。
     *
     * @param userId        当前用户ID
     * @param notifications 当前页的通知记录列表
     * @return 已读通知的ID列表，若无已读记录则返回空列表
     */
    private List<Long> getReadNotificationIds(Long userId, List<Notification> notifications) {
        if (notifications.isEmpty()) {
            return List.of();
        }
        List<Long> notificationIds = notifications.stream()
                .map(Notification::getId)
                .collect(Collectors.toList());
        LambdaQueryWrapper<UserNotification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserNotification::getUserId, userId)
                .eq(UserNotification::getIsRead, 1)
                .eq(UserNotification::getIsDeleted, 0)
                .in(UserNotification::getNotificationId, notificationIds);
        return userNotificationMapper.selectList(wrapper).stream()
                .map(UserNotification::getNotificationId)
                .collect(Collectors.toList());
    }

    /**
     * 查询用户已删除的通知ID集合（全量，用于排除）。
     * 从 user_notification 表中查出当前用户所有标记为已删除的通知ID，
     * 用于在查询通知列表时过滤掉这些通知。
     *
     * @param userId 当前用户ID
     * @return 该用户已删除的通知ID集合，无记录时返回空集合
     */
    private Set<Long> getDeletedNotificationIds(Long userId) {
        LambdaQueryWrapper<UserNotification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserNotification::getUserId, userId)
                .eq(UserNotification::getIsDeleted, 1)
                .select(UserNotification::getNotificationId);
        return userNotificationMapper.selectList(wrapper).stream()
                .map(UserNotification::getNotificationId)
                .collect(Collectors.toSet());
    }

    /**
     * 获取当前用户的未读通知数量。
     * 计算用户可见的所有通知总数减去已读通知数，得到未读数量。
     * 已删除的通知不参与计数。
     *
     * @return 包含未读通知数量的结果对象
     */
    @Override
    public Result getUnreadCount() {
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();

        try {
            // 单条 SQL 直接数出未读数：
            // 可见范围（本人或全体）AND 无「已读」关联行 AND 无「已删除」关联行。
            // 原实现是「拉全量可见ID → 再 count 已读」两步，
            // 其中 user_id=? OR user_id IS NULL 会让 user_id 索引失效走全表扫，
            // 且 notIn(deletedIds) 随用户删除量无限膨胀、全量 ID 还要拉进 JVM。
            LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<Notification>()
                    .and(w -> w.eq(Notification::getUserId, userId)
                            .or().isNull(Notification::getUserId))
                    .apply("NOT EXISTS (SELECT 1 FROM user_notification un_read" +
                            " WHERE un_read.notification_id = notification.id" +
                            " AND un_read.user_id = {0}" +
                            " AND un_read.is_read = 1 AND un_read.is_deleted = 0)", userId)
                    .apply("NOT EXISTS (SELECT 1 FROM user_notification un_del" +
                            " WHERE un_del.notification_id = notification.id" +
                            " AND un_del.user_id = {0}" +
                            " AND un_del.is_deleted = 1)", userId);
            return Result.ok(count(wrapper));
        } catch (Exception e) {
            // SQL 万一与库结构不匹配时不影响功能，回退到原来的两步算法
            log.warn("未读数单条SQL失败，回退旧算法: {}", e.getMessage());
            return Result.ok(countUnreadLegacy(userId));
        }
    }

    /**
     * 把慢 IO（邮件发送）推迟到事务提交之后执行，避免 SMTP 耗时把数据库事务拖长。
     * 无事务时（例如单元测试直接调用）立即执行。
     */
    private void runAfterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    /**
     * 分批群发：每批 500 封，单批失败不影响其余批次（避免一次异常导致全量重投）。
     */
    private void sendBatchInChunks(List<String> emails, String title, String content) {
        int chunkSize = 500;
        for (int i = 0; i < emails.size(); i += chunkSize) {
            List<String> chunk = emails.subList(i, Math.min(i + chunkSize, emails.size()));
            try {
                emailService.sendNotificationBatch(chunk, title, content);
            } catch (Exception e) {
                log.error("通知邮件群发第 {} 批失败，共 {} 封: {}", (i / chunkSize) + 1, chunk.size(), e.getMessage());
            }
        }
    }

    /**
     * 旧的两步算法，仅在单条 SQL 异常时兜底使用。
     */
    private long countUnreadLegacy(Long userId) {
        Set<Long> deletedIds = getDeletedNotificationIds(userId);

        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.eq(Notification::getUserId, userId)
                .or().isNull(Notification::getUserId));
        if (!deletedIds.isEmpty()) {
            wrapper.notIn(Notification::getId, deletedIds);
        }
        wrapper.select(Notification::getId);
        List<Long> allNotificationIds = list(wrapper).stream()
                .map(Notification::getId)
                .collect(Collectors.toList());

        if (allNotificationIds.isEmpty()) {
            return 0L;
        }

        LambdaQueryWrapper<UserNotification> readWrapper = new LambdaQueryWrapper<>();
        readWrapper.eq(UserNotification::getUserId, userId)
                .eq(UserNotification::getIsRead, 1)
                .eq(UserNotification::getIsDeleted, 0)
                .in(UserNotification::getNotificationId, allNotificationIds);
        long readCount = userNotificationMapper.selectCount(readWrapper);

        return allNotificationIds.size() - readCount;
    }

    /**
     * 将指定通知标记为已读。
     * 首先校验通知是否存在以及当前用户是否有权操作该通知（仅允许标记自己可见的
     * 通知为已读），然后通过 upsert 机制更新或插入 user_notification 记录。
     *
     * @param notificationId 要标记为已读的通知ID
     * @return 操作结果，成功返回 ok，失败返回错误信息
     */
    @Override
    @Transactional
    public Result markAsRead(Long notificationId) {
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();

        Notification notification = getById(notificationId);
        if (notification == null) {
            return Result.fail("通知不存在");
        }

        // 校验通知归属：仅允许标记自己可见的通知为已读
        if (notification.getUserId() != null && !notification.getUserId().equals(userId)) {
            return Result.fail("无权操作该通知");
        }

        // upsert：有记录则更新，无记录则插入
        upsertUserNotification(userId, notificationId, true, false);
        return Result.ok();
    }

    /**
     * 将当前用户的所有未读通知批量标记为已读。
     * 先获取用户可见的全部通知，排除已删除的通知，再过滤出尚未已读的通知，
     * 然后分两批处理：对已有 user_notification 记录的通知执行批量更新，
     * 对尚无记录的通知执行批量插入。插入操作中包含并发冲突兜底处理。
     *
     * @return 操作结果
     */
    @Override
    @Transactional
    public Result markAllAsRead() {
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();

        // 查询用户已删除的通知ID
        Set<Long> deletedIds = getDeletedNotificationIds(userId);

        // 查询用户可见的所有通知
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.eq(Notification::getUserId, userId)
                .or().isNull(Notification::getUserId));
        if (!deletedIds.isEmpty()) {
            wrapper.notIn(Notification::getId, deletedIds);
        }
        wrapper.select(Notification::getId);
        List<Long> allNotificationIds = list(wrapper).stream()
                .map(Notification::getId)
                .collect(Collectors.toList());

        if (allNotificationIds.isEmpty()) {
            return Result.ok();
        }

        // 查询已读的通知ID
        LambdaQueryWrapper<UserNotification> readWrapper = new LambdaQueryWrapper<>();
        readWrapper.eq(UserNotification::getUserId, userId)
                .eq(UserNotification::getIsRead, 1)
                .eq(UserNotification::getIsDeleted, 0)
                .in(UserNotification::getNotificationId, allNotificationIds);
        Set<Long> readIds = userNotificationMapper.selectList(readWrapper).stream()
                .map(UserNotification::getNotificationId)
                .collect(Collectors.toSet());

        // 批量标记未读通知为已读
        List<Long> unreadIds = allNotificationIds.stream()
                .filter(id -> !readIds.contains(id))
                .collect(Collectors.toList());

        if (unreadIds.isEmpty()) {
            return Result.ok();
        }

        LocalDateTime now = LocalDateTime.now();

        // 一次查询获取所有已有记录，避免循环内逐条 selectOne（N+1）
        LambdaQueryWrapper<UserNotification> existingWrapper = new LambdaQueryWrapper<>();
        existingWrapper.eq(UserNotification::getUserId, userId)
                .in(UserNotification::getNotificationId, unreadIds);
        Map<Long, UserNotification> existingMap = userNotificationMapper.selectList(existingWrapper).stream()
                .collect(Collectors.toMap(UserNotification::getNotificationId, un -> un, (a, b) -> a));

        // 批量更新已有记录
        List<Long> existingUnreadIds = unreadIds.stream()
                .filter(existingMap::containsKey)
                .collect(Collectors.toList());
        if (!existingUnreadIds.isEmpty()) {
            LambdaUpdateWrapper<UserNotification> batchUpdate = new LambdaUpdateWrapper<>();
            batchUpdate.eq(UserNotification::getUserId, userId)
                    .in(UserNotification::getNotificationId, existingUnreadIds)
                    .set(UserNotification::getIsRead, 1)
                    .set(UserNotification::getIsDeleted, 0)
                    .set(UserNotification::getReadTime, now)
                    .set(UserNotification::getUpdateTime, now);
            userNotificationMapper.update(null, batchUpdate);
        }

        // 批量插入新记录
        List<Long> missingIds = unreadIds.stream()
                .filter(id -> !existingMap.containsKey(id))
                .toList();
        if (!missingIds.isEmpty()) {
            List<UserNotification> batch = missingIds.stream().map(nid -> {
                UserNotification un = new UserNotification();
                un.setUserId(userId);
                un.setNotificationId(nid);
                un.setIsRead(1);
                un.setIsDeleted(0);
                un.setReadTime(now);
                un.setUpdateTime(now);
                return un;
            }).toList();

            // 逐条插入并兜底并发冲突（uk_notification_user）
            for (UserNotification un : batch) {
                try {
                    userNotificationMapper.insert(un);
                } catch (DuplicateKeyException e) {
                    log.debug("markAllAsRead 并发冲突: userId={}, notificationId={}", userId, un.getNotificationId());
                    LambdaUpdateWrapper<UserNotification> fallback = new LambdaUpdateWrapper<>();
                    fallback.eq(UserNotification::getUserId, userId)
                            .eq(UserNotification::getNotificationId, un.getNotificationId())
                            .set(UserNotification::getIsRead, 1)
                            .set(UserNotification::getReadTime, now)
                            .set(UserNotification::getUpdateTime, now);
                    userNotificationMapper.update(null, fallback);
                }
            }
        }
        return Result.ok();
    }

    /**
     * 删除当前用户的一条通知（软删除）。
     * 校验通知是否存在以及当前用户是否有权操作该通知，然后通过 upsert 机制
     * 将该通知标记为已删除状态，用户侧不再可见，但通知本身不会被物理删除。
     *
     * @param notificationId 要删除的通知ID
     * @return 操作结果，包含成功或失败的提示信息
     */
    @Override
    @Transactional
    public Result deleteMyNotification(Long notificationId) {
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();

        Notification notification = getById(notificationId);
        if (notification == null) {
            return Result.fail("通知不存在");
        }

        // 校验通知归属
        if (notification.getUserId() != null && !notification.getUserId().equals(userId)) {
            return Result.fail("无权操作该通知");
        }

        // upsert 为已删除状态
        upsertUserNotification(userId, notificationId, null, true);
        return Result.okMsg("已删除");
    }

    /**
     * 插入或更新用户通知状态（已读/未读/删除）。
     * 利用 user_notification 表的 uk_notification_user 唯一索引做 upsert。
     * 先查后插：如果已有记录则直接更新；如果无记录则尝试插入，
     * 插入失败（并发冲突导致 DuplicateKeyException）时回退为更新。
     *
     * @param userId         用户ID
     * @param notificationId 通知ID
     * @param isRead         是否已读，为 null 表示不修改已读状态
     * @param isDeleted      是否已删除，为 null 表示不修改删除状态
     */
    private void upsertUserNotification(Long userId, Long notificationId, Boolean isRead, Boolean isDeleted) {
        LocalDateTime now = LocalDateTime.now();

        // 先查是否存在
        LambdaQueryWrapper<UserNotification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserNotification::getUserId, userId)
                .eq(UserNotification::getNotificationId, notificationId);
        UserNotification existing = userNotificationMapper.selectOne(wrapper);

        if (existing != null) {
            // 已有记录：直接更新
            doUpdateUserNotification(existing.getId(), isRead, isDeleted, now);
            return;
        }

        // 无记录：尝试插入
        UserNotification un = new UserNotification();
        un.setUserId(userId);
        un.setNotificationId(notificationId);
        un.setIsRead(isRead != null && isRead ? 1 : 0);
        un.setIsDeleted(isDeleted != null && isDeleted ? 1 : 0);
        un.setReadTime(isRead != null && isRead ? now : null);
        un.setUpdateTime(now);

        try {
            userNotificationMapper.insert(un);
        } catch (DuplicateKeyException e) {
            // 并发下另一线程已插入，回退为查询并更新
            log.debug("upsert 并发冲突，回退为更新: userId={}, notificationId={}", userId, notificationId);
            existing = userNotificationMapper.selectOne(wrapper);
            if (existing != null) {
                doUpdateUserNotification(existing.getId(), isRead, isDeleted, now);
            }
        }
    }

    /**
     * 更新已有的 user_notification 记录。
     * 根据传入的参数选择性更新已读状态和删除状态，并同步更新时间戳。
     * 当标记为已读时会同时设置阅读时间。
     *
     * @param id        要更新的 user_notification 记录主键ID
     * @param isRead    是否已读，为 null 表示不修改已读状态
     * @param isDeleted 是否已删除，为 null 表示不修改删除状态
     * @param now       当前时间，用于设置更新时间及阅读时间
     */
    private void doUpdateUserNotification(Long id, Boolean isRead, Boolean isDeleted, LocalDateTime now) {
        LambdaUpdateWrapper<UserNotification> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(UserNotification::getId, id);
        if (isRead != null) {
            updateWrapper.set(UserNotification::getIsRead, isRead ? 1 : 0);
            if (isRead) {
                updateWrapper.set(UserNotification::getReadTime, now);
            }
        }
        if (isDeleted != null) {
            updateWrapper.set(UserNotification::getIsDeleted, isDeleted ? 1 : 0);
        }
        updateWrapper.set(UserNotification::getUpdateTime, now);
        userNotificationMapper.update(null, updateWrapper);
    }

    /**
     * 管理后台分页查询所有通知（不对用户进行过滤）。
     * 支持按通知类型筛选，并在每条通知中附带目标用户的用户名信息，
     * 便于管理员查看所有通知的完整列表。
     *
     * @param current 当前页码，从 1 开始
     * @param size    每页记录数
     * @param type    通知类型筛选条件，为 null 或空字符串时查询所有类型
     * @return 分页封装的通知列表，包含用户名等展示信息
     */
    @Override
    public Result getAllNotifications(Integer current, Integer size, String type) {
        Page<Notification> page = new Page<>(current, size);
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Notification::getCreateTime);
        if (StrUtil.isNotBlank(type)) {
            wrapper.eq(Notification::getType, type);
        }
        Page<Notification> result = page(page, wrapper);

        List<Long> targetUserIds = result.getRecords().stream()
                .map(Notification::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> userNameMap = Map.of();
        if (!targetUserIds.isEmpty()) {
            List<User> users = userMapper.selectByIds(targetUserIds);
            userNameMap = users.stream()
                    .collect(Collectors.toMap(User::getId, User::getUserName, (a, b) -> a));
        }

        Map<Long, String> finalUserNameMap = userNameMap;
        List<NotificationVO> voList = result.getRecords().stream()
                .map(n -> {
                    NotificationVO vo = BeanUtil.copyProperties(n, NotificationVO.class);
                    vo.setUserId(n.getUserId());
                    if (n.getUserId() != null) {
                        vo.setUserName(finalUserNameMap.getOrDefault(n.getUserId(), "未知用户"));
                    }
                    return vo;
                })
                .collect(Collectors.toList());

        Page<NotificationVO> voPage = new Page<>();
        BeanUtil.copyProperties(result, voPage, "records");
        voPage.setRecords(voList);
        return Result.ok(voPage);
    }

    /**
     * 管理后台更新通知内容。
     * 根据通知ID查找已有通知，校验其是否存在，然后使用 DTO 中的新数据
     * 覆盖标题、内容、类型和目标用户ID等字段并保存更新。
     *
     * @param id  要更新的通知ID
     * @param dto 包含新标题、内容、类型及目标用户ID的表单数据
     * @return 操作结果，成功返回成功提示，失败返回错误信息
     */
    @Override
    @Transactional
    public Result updateNotification(Long id, SendNotificationFormDTO dto) {
        Notification notification = getById(id);
        if (notification == null) {
            return Result.fail("通知不存在");
        }
        notification.setTitle(dto.getTitle());
        notification.setContent(dto.getContent());
        notification.setType(dto.getType() != null ? dto.getType() : "system");
        notification.setUserId(dto.getUserId());
        updateById(notification);
        return Result.okMsg("修改成功");
    }

    /**
     * 管理后台物理删除一条通知。
     * 根据通知ID查找已有通知，校验其是否存在，然后执行物理删除操作。
     *
     * @param id 要删除的通知ID
     * @return 操作结果，成功返回成功提示，失败返回错误信息
     */
    @Override
    @Transactional
    public Result deleteNotification(Long id) {
        Notification notification = getById(id);
        if (notification == null) {
            return Result.fail("通知不存在");
        }
        removeById(id);
        return Result.okMsg("删除成功");
    }

}
