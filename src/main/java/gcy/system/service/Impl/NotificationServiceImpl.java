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
import gcy.system.utils.AfterCommit;
import gcy.system.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 通知服务实现类。
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
     * 发送通知；sendEmail 为 true 时追加邮件：指定目标用户则单发，否则群发所有已绑定邮箱用户。
     * 邮件发送失败不影响通知的保存结果。
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
                AfterCommit.run(() ->
                        emailService.sendNotificationEmail(target.getEmail(), dto.getTitle(), dto.getContent()));
            } else {
                // 只投影 email 一列：selectList 会拉整张 User 实体（含头像、简介等大字段），
                // 用户量大时有 OOM 风险，且发生在事务内会拉长事务时间。
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
                AfterCommit.run(() -> sendBatchInChunks(emails, dto.getTitle(), dto.getContent()));
                log.info("通知邮件已排入提交后群发，覆盖 {} 位用户", total);
                return Result.okMsg("通知已保存，邮件将在提交后发送给 " + total + " 位用户");
            }
        }
        return Result.okMsg("发送成功");
    }

    /**
     * 分页查询当前用户可见的通知（本人专属或全局），排除其已删除的，并标注已读状态。
     */
    @Override
    public Result getUserNotifications(Integer current, Integer size) {
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();

        Set<Long> deletedIds = getDeletedNotificationIds(userId);

        Page<Notification> page = new Page<>(current, size);
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.eq(Notification::getUserId, userId)
                .or().isNull(Notification::getUserId));
        if (!deletedIds.isEmpty()) {
            wrapper.notIn(Notification::getId, deletedIds);
        }
        wrapper.orderByDesc(Notification::getCreateTime);
        Page<Notification> result = page(page, wrapper);

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
     * 查询当前页通知中该用户已读（未删除）的通知 ID。
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
     * 查询该用户已删除的通知 ID 集合（全量，用于排除）。
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
     * 查询当前用户的未读通知数（可见通知数减已读数，不计已删除的）。
     */
    @Override
    public Result getUnreadCount() {
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();

        try {
            // 单条 SQL 数未读数：可见范围（本人或全体）AND 无「已读」关联行 AND 无「已删除」关联行。
            // 替代「拉全量可见ID 再 count 已读」两步：后者 user_id=? OR IS NULL 使 user_id 索引失效走全表扫，
            // notIn(deletedIds) 随删除量膨胀，且全量 ID 要拉进 JVM。
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
     * 标记通知为已读；仅可操作本人可见的通知，经 upsert 写入 user_notification。
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

        if (notification.getUserId() != null && !notification.getUserId().equals(userId)) {
            return Result.fail("无权操作该通知");
        }

        upsertUserNotification(userId, notificationId, true, false);
        return Result.ok();
    }

    /**
     * 将当前用户所有未读通知批量标记为已读；已有记录批量更新，缺失记录批量插入并兜底并发冲突。
     */
    @Override
    @Transactional
    public Result markAllAsRead() {
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();

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
            return Result.ok();
        }

        LambdaQueryWrapper<UserNotification> readWrapper = new LambdaQueryWrapper<>();
        readWrapper.eq(UserNotification::getUserId, userId)
                .eq(UserNotification::getIsRead, 1)
                .eq(UserNotification::getIsDeleted, 0)
                .in(UserNotification::getNotificationId, allNotificationIds);
        Set<Long> readIds = userNotificationMapper.selectList(readWrapper).stream()
                .map(UserNotification::getNotificationId)
                .collect(Collectors.toSet());

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
     * 软删除当前用户的一条通知；仅可操作本人可见的通知，经 upsert 置为已删除，不物理删除。
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

        if (notification.getUserId() != null && !notification.getUserId().equals(userId)) {
            return Result.fail("无权操作该通知");
        }

        upsertUserNotification(userId, notificationId, null, true);
        return Result.okMsg("已删除");
    }

    /**
     * 经 uk_notification_user 唯一索引 upsert 用户通知状态：先查后插，插入撞唯一键（并发）时回退为更新。
     *
     * @param isRead    为 null 表示不修改已读状态
     * @param isDeleted 为 null 表示不修改删除状态
     */
    private void upsertUserNotification(Long userId, Long notificationId, Boolean isRead, Boolean isDeleted) {
        LocalDateTime now = LocalDateTime.now();

        LambdaQueryWrapper<UserNotification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserNotification::getUserId, userId)
                .eq(UserNotification::getNotificationId, notificationId);
        UserNotification existing = userNotificationMapper.selectOne(wrapper);

        if (existing != null) {
            doUpdateUserNotification(existing.getId(), isRead, isDeleted, now);
            return;
        }

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
     * 更新 user_notification：按入参选择性更新已读/删除状态，标记已读时同步设置阅读时间。
     *
     * @param isRead    为 null 表示不修改已读状态
     * @param isDeleted 为 null 表示不修改删除状态
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
     * 管理后台分页查询所有通知（不按用户过滤），可按类型筛选，并附带目标用户用户名。
     *
     * @param type 为 null 或空串时查询所有类型
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
     * 管理后台更新通知：覆盖标题、内容、类型（缺省 system）与目标用户ID。
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
     * 管理后台物理删除通知。
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
