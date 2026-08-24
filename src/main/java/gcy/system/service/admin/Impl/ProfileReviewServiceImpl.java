package gcy.system.service.admin.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import gcy.system.entity.dto.Result;
import gcy.system.entity.dto.SendNotificationFormDTO;
import gcy.system.entity.pojo.IconReviewLog;
import gcy.system.entity.pojo.NicknameReviewLog;
import gcy.system.entity.pojo.User;
import gcy.system.mapper.IconReviewLogMapper;
import gcy.system.mapper.NicknameReviewLogMapper;
import gcy.system.mapper.UserMapper;
import gcy.system.service.INotificationService;
import gcy.system.service.admin.IProfileReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static gcy.system.utils.RedisConstants.LOGIN_USER_KEY;
import static gcy.system.utils.RedisConstants.LOGIN_USER_TOKENS_SET;

/**
 * 用户资料审核服务实现。
 *
 * @author 郭名城
 * @date 2026-08-18
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileReviewServiceImpl implements IProfileReviewService {

    private final UserMapper userMapper;

    private final NicknameReviewLogMapper nicknameReviewLogMapper;

    private final IconReviewLogMapper iconReviewLogMapper;

    private final INotificationService notificationService;

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public Result getNicknameList(Integer page, Integer size, String status) {
        LambdaQueryWrapper<NicknameReviewLog> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) {
            List<Integer> statusList = Arrays.stream(status.split(","))
                    .map(Integer::parseInt).collect(Collectors.toList());
            wrapper.in(NicknameReviewLog::getStatus, statusList);
        }
        wrapper.orderByDesc(NicknameReviewLog::getCreateTime);

        Page<NicknameReviewLog> pageResult = nicknameReviewLogMapper.selectPage(new Page<>(page, size), wrapper);
        List<Map<String, Object>> records = new ArrayList<>();
        for (NicknameReviewLog log : pageResult.getRecords()) {
            User user = userMapper.selectById(log.getUserId());
            Map<String, Object> item = new HashMap<>();
            item.put("logId", log.getId());
            item.put("userId", log.getUserId());
            item.put("userName", user != null ? user.getUserName() : "-");
            item.put("pendingNickname", log.getNewNickname());
            item.put("reviewStatus", log.getStatus());
            item.put("aiRejectReason", log.getAiRejectReason());
            item.put("manualRejectReason", log.getManualRejectReason());
            records.add(item);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("records", records);
        data.put("total", pageResult.getTotal());
        return Result.ok(data);
    }

    @Override
    public Result getIconList(Integer page, Integer size, String status) {
        LambdaQueryWrapper<IconReviewLog> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) {
            List<Integer> statusList = Arrays.stream(status.split(","))
                    .map(Integer::parseInt).collect(Collectors.toList());
            wrapper.in(IconReviewLog::getStatus, statusList);
        }
        wrapper.orderByDesc(IconReviewLog::getCreateTime);

        Page<IconReviewLog> pageResult = iconReviewLogMapper.selectPage(new Page<>(page, size), wrapper);
        List<Map<String, Object>> records = new ArrayList<>();
        for (IconReviewLog log : pageResult.getRecords()) {
            User user = userMapper.selectById(log.getUserId());
            Map<String, Object> item = new HashMap<>();
            item.put("logId", log.getId());
            item.put("userId", log.getUserId());
            item.put("userName", user != null ? user.getUserName() : "-");
            item.put("pendingIcon", log.getNewIcon());
            item.put("reviewStatus", log.getStatus());
            records.add(item);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("records", records);
        data.put("total", pageResult.getTotal());
        return Result.ok(data);
    }

    @Override
    @Transactional
    public Result approveNickname(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) return Result.fail("用户不存在");
        if (user.getNicknameReviewStatus() != 1 && user.getNicknameReviewStatus() != 3) {
            return Result.fail("该用户无待审核的昵称");
        }

        String newNickname = user.getPendingNickname();
        int originalStatus = user.getNicknameReviewStatus();

        // 更新 user 表
        user.setUserName(newNickname);
        user.setPendingNickname(null);
        user.setNicknameReviewStatus(0);
        userMapper.updateById(user);

        // 更新 log 表
        nicknameReviewLogMapper.update(null, new LambdaUpdateWrapper<NicknameReviewLog>()
                .eq(NicknameReviewLog::getUserId, userId)
                .eq(NicknameReviewLog::getStatus, originalStatus)
                .set(NicknameReviewLog::getStatus, 0)
                .set(NicknameReviewLog::getAiRejectReason, null)
                .set(NicknameReviewLog::getManualRejectReason, null));

        sendNotification(userId, "昵称审核通过", "您的新昵称「" + newNickname + "」已通过审核");
        refreshUserCache(userMapper.selectById(userId));
        log.info("昵称审核通过: userId={}, nickname={}", userId, newNickname);
        return Result.okMsg("昵称审核通过");
    }

    @Override
    @Transactional
    public Result rejectNickname(Long userId, String reason) {
        User user = userMapper.selectById(userId);
        if (user == null) return Result.fail("用户不存在");
        if (user.getNicknameReviewStatus() != 1 && user.getNicknameReviewStatus() != 3) {
            return Result.fail("该用户无待审核的昵称");
        }

        int originalStatus = user.getNicknameReviewStatus();

        // 更新 user 表
        user.setPendingNickname(null);
        user.setNicknameReviewStatus(2);
        userMapper.updateById(user);

        // 更新 log 表
        nicknameReviewLogMapper.update(null, new LambdaUpdateWrapper<NicknameReviewLog>()
                .eq(NicknameReviewLog::getUserId, userId)
                .eq(NicknameReviewLog::getStatus, originalStatus)
                .set(NicknameReviewLog::getStatus, 2)
                .set(NicknameReviewLog::getManualRejectReason, reason));

        String msg = "您的新昵称未通过审核";
        if (reason != null && !reason.isEmpty()) {
            msg += "，原因：" + reason;
        }
        sendNotification(userId, "昵称审核未通过", msg);
        refreshUserCache(userMapper.selectById(userId));
        log.info("昵称审核拒绝: userId={}, reason={}", userId, reason);
        return Result.okMsg("已拒绝昵称修改");
    }

    @Override
    @Transactional
    public Result approveIcon(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) return Result.fail("用户不存在");
        if (user.getIconReviewStatus() != 1) {
            return Result.fail("该用户无待审核的头像");
        }

        String newIcon = user.getPendingIcon();

        // 更新 user 表
        user.setIcon(newIcon);
        user.setPendingIcon(null);
        user.setIconReviewStatus(0);
        userMapper.updateById(user);

        // 更新 log 表
        iconReviewLogMapper.update(null, new LambdaUpdateWrapper<IconReviewLog>()
                .eq(IconReviewLog::getUserId, userId)
                .eq(IconReviewLog::getStatus, 1)
                .set(IconReviewLog::getStatus, 0));

        sendNotification(userId, "头像审核通过", "您的新头像已通过审核");
        refreshUserCache(userMapper.selectById(userId));
        log.info("头像审核通过: userId={}", userId);
        return Result.okMsg("头像审核通过");
    }

    @Override
    @Transactional
    public Result rejectIcon(Long userId, String reason) {
        User user = userMapper.selectById(userId);
        if (user == null) return Result.fail("用户不存在");
        if (user.getIconReviewStatus() != 1) {
            return Result.fail("该用户无待审核的头像");
        }

        // 更新 user 表
        user.setPendingIcon(null);
        user.setIconReviewStatus(2);
        userMapper.updateById(user);

        // 更新 log 表
        iconReviewLogMapper.update(null, new LambdaUpdateWrapper<IconReviewLog>()
                .eq(IconReviewLog::getUserId, userId)
                .eq(IconReviewLog::getStatus, 1)
                .set(IconReviewLog::getStatus, 2)
                .set(IconReviewLog::getManualRejectReason, reason));

        String msg = "您的新头像未通过审核";
        if (reason != null && !reason.isEmpty()) {
            msg += "，原因：" + reason;
        }
        sendNotification(userId, "头像审核未通过", msg);
        refreshUserCache(userMapper.selectById(userId));
        log.info("头像审核拒绝: userId={}, reason={}", userId, reason);
        return Result.okMsg("已拒绝头像修改");
    }

    @Override
    public Result getPendingCount() {
        long nicknameCount = nicknameReviewLogMapper.selectCount(new LambdaQueryWrapper<NicknameReviewLog>()
                .in(NicknameReviewLog::getStatus, 1, 3));
        long iconCount = iconReviewLogMapper.selectCount(new LambdaQueryWrapper<IconReviewLog>()
                .eq(IconReviewLog::getStatus, 1));
        Map<String, Object> data = new HashMap<>();
        data.put("nicknameCount", nicknameCount);
        data.put("iconCount", iconCount);
        return Result.ok(data);
    }

    @Override
    public Result getStatusCounts() {
        Map<String, Object> nickname = Map.of(
                "all", nicknameReviewLogMapper.selectCount(null),
                "pending", nicknameReviewLogMapper.selectCount(new LambdaQueryWrapper<NicknameReviewLog>().eq(NicknameReviewLog::getStatus, 1)),
                "approved", nicknameReviewLogMapper.selectCount(new LambdaQueryWrapper<NicknameReviewLog>().eq(NicknameReviewLog::getStatus, 0)),
                "rejected", nicknameReviewLogMapper.selectCount(new LambdaQueryWrapper<NicknameReviewLog>().eq(NicknameReviewLog::getStatus, 2)),
                "reReview", nicknameReviewLogMapper.selectCount(new LambdaQueryWrapper<NicknameReviewLog>().eq(NicknameReviewLog::getStatus, 3)));
        Map<String, Object> icon = Map.of(
                "all", iconReviewLogMapper.selectCount(null),
                "pending", iconReviewLogMapper.selectCount(new LambdaQueryWrapper<IconReviewLog>().eq(IconReviewLog::getStatus, 1)),
                "approved", iconReviewLogMapper.selectCount(new LambdaQueryWrapper<IconReviewLog>().eq(IconReviewLog::getStatus, 0)),
                "rejected", iconReviewLogMapper.selectCount(new LambdaQueryWrapper<IconReviewLog>().eq(IconReviewLog::getStatus, 2)));
        return Result.ok(Map.of("nickname", nickname, "icon", icon));
    }

    private void sendNotification(Long userId, String title, String content) {
        try {
            SendNotificationFormDTO dto = new SendNotificationFormDTO();
            dto.setUserId(userId);
            dto.setTitle(title);
            dto.setContent(content);
            dto.setType("profile_review");
            notificationService.sendNotification(dto);
        } catch (Exception e) {
            log.error("发送审核通知失败: userId={}", userId, e);
        }
    }

    /**
     * 刷新用户 Redis 缓存，让用户端立即看到最新的昵称/头像和审核状态。
     */
    private void refreshUserCache(User user) {
        try {
            Long userId = user.getId();
            String setKey = LOGIN_USER_TOKENS_SET + userId;
            Set<String> tokens = stringRedisTemplate.opsForSet().members(setKey);
            if (tokens == null || tokens.isEmpty()) return;

            Map<String, String> userMap = new HashMap<>();
            userMap.put("id", String.valueOf(user.getId()));
            userMap.put("userName", user.getUserName() != null ? user.getUserName() : "");
            userMap.put("icon", user.getIcon() != null ? user.getIcon() : "");
            userMap.put("phone", user.getPhone() != null ? user.getPhone() : "");
            userMap.put("email", user.getEmail() != null ? user.getEmail() : "");
            userMap.put("isAdmin", String.valueOf(user.getIsAdmin()));
            userMap.put("nicknameReviewStatus", String.valueOf(user.getNicknameReviewStatus()));
            userMap.put("pendingNickname", user.getPendingNickname() != null ? user.getPendingNickname() : "");
            userMap.put("iconReviewStatus", String.valueOf(user.getIconReviewStatus()));
            userMap.put("pendingIcon", user.getPendingIcon() != null ? user.getPendingIcon() : "");

            for (String token : tokens) {
                stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, userMap);
            }
        } catch (Exception e) {
            log.error("刷新用户缓存失败: userId={}", user.getId(), e);
        }
    }
}