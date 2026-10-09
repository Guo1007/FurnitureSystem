package gcy.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import gcy.system.entity.dto.Result;
import gcy.system.entity.dto.SendNotificationFormDTO;
import gcy.system.entity.pojo.Notification;

/**
 * 通知服务接口。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
public interface INotificationService extends IService<Notification> {

    /**
     * 管理员发送通知。
     */
    Result sendNotification(SendNotificationFormDTO dto);

    /**
     * 分页查询当前登录用户的通知列表。
     */
    Result getUserNotifications(Integer current, Integer size);

    /**
     * 查询当前登录用户的未读通知数量。
     */
    Result getUnreadCount();

    /**
     * 将指定通知标记为已读。
     */
    Result markAsRead(Long notificationId);

    /**
     * 将当前登录用户的所有未读通知标记为已读。
     */
    Result markAllAsRead();

    /**
     * 管理员分页查询全部通知，type 为空时查全部类型。
     */
    Result getAllNotifications(Integer current, Integer size, String type);

    /**
     * 管理员更新指定通知。
     */
    Result updateNotification(Long id, SendNotificationFormDTO dto);

    /**
     * 管理员删除指定通知。
     */
    Result deleteNotification(Long id);

    /**
     * 删除当前用户自己视角下的通知记录，不影响其他用户的数据。
     */
    Result deleteMyNotification(Long notificationId);
}
