package gcy.system.controller;

import gcy.system.entity.dto.Result;
import gcy.system.service.INotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 通知管理控制器，接口均基于当前登录用户操作。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Tag(name = "通知", description = "通知相关接口")
@RestController
@RequestMapping("/notification")
@RequiredArgsConstructor
public class NotificationController {

    private final INotificationService notificationService;

    /**
     * 分页查询当前用户的通知列表。
     */
    @Operation(summary = "分页查询当前用户的通知列表")
    @GetMapping("/list")
    public Result list(@Parameter(description = "当前页码") @RequestParam(defaultValue = "1") Integer current,
                       @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") Integer size) {
        return notificationService.getUserNotifications(current, size);
    }

    /**
     * 获取当前用户的未读通知数量。
     */
    @Operation(summary = "获取当前用户的未读通知数量")
    @GetMapping("/unread-count")
    public Result unreadCount() {
        return notificationService.getUnreadCount();
    }

    /**
     * 将指定通知标记为已读。
     */
    @Operation(summary = "将指定通知标记为已读")
    @PutMapping("/read/{id}")
    public Result markRead(@Parameter(description = "通知ID") @PathVariable Long id) {
        return notificationService.markAsRead(id);
    }

    /**
     * 将当前用户的所有未读通知标记为已读。
     */
    @Operation(summary = "将当前用户的所有未读通知标记为已读")
    @PutMapping("/read-all")
    public Result markAllRead() {
        return notificationService.markAllAsRead();
    }

    /**
     * 删除当前用户的一条通知记录。
     */
    @Operation(summary = "删除当前用户的一条通知记录")
    @DeleteMapping("/{id}")
    public Result deleteMyNotification(@Parameter(description = "通知ID") @PathVariable Long id) {
        return notificationService.deleteMyNotification(id);
    }
}
