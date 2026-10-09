package gcy.system.service.admin;

import gcy.system.entity.dto.Result;
import gcy.system.entity.dto.UserSimpleDTO;

import java.util.List;

/**
 * 管理员通知设置服务接口。
 * <p>
 * 通知配置按功能独立维护（新订单、售后退款、库存预警），各有独立邮件开关与接收管理员列表。
 *
 * @author 郭名城
 * @date 2026-08-08
 */
public interface INotifySettingService {

    /**
     * 获取所有功能的通知配置及可选的管理员列表。
     */
    Result getSetting();

    /**
     * 保存指定功能的通知配置（开关 + 接收管理员ID列表）。
     *
     * @param notifyType 通知类型：new_order-新订单、refund-售后退款、stock_alert-库存预警
     */
    Result saveSetting(String notifyType, Boolean enabled, List<Long> adminIds);

    /**
     * 查询所有管理员（is_admin=1）的简易信息（id、用户名、邮箱）。
     */
    List<UserSimpleDTO> listAdmins();
}
