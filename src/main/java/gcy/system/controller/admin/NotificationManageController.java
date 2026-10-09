package gcy.system.controller.admin;

import gcy.system.aspect.OperationLog;
import gcy.system.entity.dto.Result;
import gcy.system.entity.dto.SendNotificationFormDTO;
import gcy.system.service.INotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 通知管理控制器。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Tag(name = "通知管理", description = "通知管理相关接口")
@RestController
@RequestMapping("/admin/notification")
@RequiredArgsConstructor
public class NotificationManageController {

    private final INotificationService notificationService;

    /**
     * 向指定用户或用户组发送通知。
     */
    @OperationLog("发送通知")
    @Operation(summary = "发送通知")
    @PostMapping("/send")
    public Result sendNotification(@Parameter(description = "请求体") @RequestBody @Valid SendNotificationFormDTO dto) {
        return notificationService.sendNotification(dto);
    }

    /**
     * 分页查询通知列表，可按类型筛选。
     */
    @Operation(summary = "分页查询通知列表")
    @GetMapping("/list")
    public Result list(@Parameter(description = "当前页码") @RequestParam(defaultValue = "1") Integer current,
                       @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") Integer size,
                       @Parameter(description = "通知类型") @RequestParam(required = false) String type) {
        return notificationService.getAllNotifications(current, size, type);
    }

    /**
     * 根据 ID 更新通知内容。
     */
    @OperationLog("更新通知")
    @Operation(summary = "更新通知")
    @PutMapping("/update/{id}")
    public Result update(@Parameter(description = "通知ID") @PathVariable Long id, @Parameter(description = "请求体") @RequestBody @Valid SendNotificationFormDTO dto) {
        return notificationService.updateNotification(id, dto);
    }

    /**
     * 根据 ID 删除单条通知。
     */
    @OperationLog("删除通知")
    @Operation(summary = "删除单条通知")
    @DeleteMapping("/delete/{id}")
    public Result delete(@Parameter(description = "通知ID") @PathVariable Long id) {
        return notificationService.deleteNotification(id);
    }

    /**
     * 根据 ID 列表批量删除通知。
     */
    @OperationLog("批量删除通知")
    @Operation(summary = "批量删除通知")
    @DeleteMapping("/batch")
    public Result batchDelete(@Parameter(description = "通知ID列表") @RequestBody List<Long> ids) {
        boolean success = notificationService.removeByIds(ids);
        return success ? Result.okMsg("批量删除成功") : Result.fail("批量删除失败");
    }
}
