package gcy.system.service.admin.Impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import gcy.system.entity.dto.Result;
import gcy.system.entity.dto.UserSimpleDTO;
import gcy.system.entity.pojo.AdminNotifySetting;
import gcy.system.entity.pojo.User;
import gcy.system.entity.vo.admin.NotifyConfigVO;
import gcy.system.mapper.AdminNotifySettingMapper;
import gcy.system.mapper.UserMapper;
import gcy.system.service.admin.INotifySettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理员通知设置服务实现。
 *
 * @author 郭名城
 * @date 2026-08-08
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotifySettingServiceImpl implements INotifySettingService {

    /**
     * 通知类型：新订单
     */
    public static final String TYPE_NEW_ORDER = "new_order";

    /**
     * 通知类型：售后退款
     */
    public static final String TYPE_REFUND = "refund";

    /**
     * 通知类型：库存预警
     */
    public static final String TYPE_STOCK_ALERT = "stock_alert";

    /**
     * 全部支持的通知类型
     */
    public static final List<String> ALL_TYPES = List.of(TYPE_NEW_ORDER, TYPE_REFUND, TYPE_STOCK_ALERT);

    private final AdminNotifySettingMapper adminNotifySettingMapper;

    private final UserMapper userMapper;

    @Override
    public Result getSetting() {
        List<AdminNotifySetting> settings = adminNotifySettingMapper.selectList(null);
        Map<String, AdminNotifySetting> byType = settings.stream()
                .filter(s -> s.getNotifyType() != null)
                .collect(Collectors.toMap(AdminNotifySetting::getNotifyType, Function.identity(), (a, b) -> a));
        List<NotifyConfigVO> configs = ALL_TYPES.stream().map(type -> {
            AdminNotifySetting setting = byType.get(type);
            return new NotifyConfigVO(
                    type,
                    setting != null && setting.getEnabled() != null && setting.getEnabled() == 1,
                    parseIds(setting != null ? setting.getAdminIds() : null));
        }).collect(Collectors.toList());
        Map<String, Object> data = new HashMap<>();
        data.put("configs", configs);
        data.put("admins", listAdmins());
        return Result.ok(data);
    }

    @Override
    @Transactional
    public Result saveSetting(String notifyType, Boolean enabled, List<Long> adminIds) {
        if (notifyType == null || !ALL_TYPES.contains(notifyType)) {
            return Result.fail("未知的通知类型");
        }
        AdminNotifySetting setting = new AdminNotifySetting();
        setting.setNotifyType(notifyType);
        setting.setEnabled(Boolean.TRUE.equals(enabled) ? 1 : 0);
        setting.setAdminIds(adminIds == null || adminIds.isEmpty()
                ? null
                : adminIds.stream().map(String::valueOf).collect(Collectors.joining(",")));
        // 原子 upsert（利用 notify_type 唯一索引），并发保存不会触发唯一索引冲突
        adminNotifySettingMapper.upsertByNotifyType(setting);
        log.info("通知配置已保存: type={}, enabled={}, adminIds={}", notifyType, enabled, adminIds);
        return Result.ok();
    }

    @Override
    public List<UserSimpleDTO> listAdmins() {
        List<User> admins = userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .eq(User::getIsAdmin, 1));
        return admins.stream()
                .map(u -> new UserSimpleDTO(u.getId(), u.getUserName(), u.getEmail()))
                .collect(Collectors.toList());
    }

    /**
     * 解析逗号分隔的管理员ID字符串。
     */
    public static List<Long> parseIds(String adminIds) {
        if (StrUtil.isBlank(adminIds)) {
            return List.of();
        }
        return Arrays.stream(adminIds.split(","))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .map(Long::parseLong)
                .collect(Collectors.toList());
    }
}
