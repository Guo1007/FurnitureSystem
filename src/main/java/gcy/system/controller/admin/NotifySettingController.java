package gcy.system.controller.admin;

import gcy.system.aspect.OperationLog;
import gcy.system.entity.dto.Result;
import gcy.system.service.admin.INotifySettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理员通知设置控制器，按通知类型（新订单/售后退款/库存预警）独立配置邮件通知开关与接收人。
 *
 * @author 郭名城
 * @date 2026-08-08
 */
@Tag(name = "通知设置", description = "管理员邮件通知设置")
@RestController
@RequestMapping("/admin/notify-setting")
@RequiredArgsConstructor
public class NotifySettingController {

    private final INotifySettingService notifySettingService;

    /**
     * 获取所有功能的通知配置及可选管理员列表。
     */
    @Operation(summary = "获取管理员通知配置")
    @GetMapping
    public Result getSetting() {
        return notifySettingService.getSetting();
    }

    /**
     * 保存指定功能的开关与接收管理员列表。
     */
    @OperationLog("保存通知设置")
    @Operation(summary = "保存管理员通知配置")
    @PutMapping
    public Result saveSetting(@Parameter(description = "请求体") @Valid @RequestBody SaveSettingDTO dto) {
        return notifySettingService.saveSetting(dto.getNotifyType(), dto.getEnabled(), dto.getAdminIds());
    }

    /**
     * 保存配置请求体。
     */
    @Data
    public static class SaveSettingDTO {
        /**
         * 通知类型：new_order-新订单、refund-售后退款、stock_alert-库存预警
         */
        @NotBlank(message = "通知类型不能为空")
        private String notifyType;
        /**
         * 是否开启通知
         */
        @NotNull(message = "开关状态不能为空")
        private Boolean enabled;
        /**
         * 接收通知的管理员ID列表
         */
        private List<Long> adminIds;
    }

}
