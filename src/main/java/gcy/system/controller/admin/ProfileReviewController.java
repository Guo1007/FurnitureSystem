package gcy.system.controller.admin;

import gcy.system.aspect.OperationLog;
import gcy.system.entity.dto.Result;
import gcy.system.service.admin.IProfileReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户资料审核控制器。
 *
 * @author 郭名城
 * @date 2026-08-18
 */
@Tag(name = "用户资料审核", description = "用户昵称和头像审核接口")
@RestController
@RequestMapping("/admin/profile-review")
@RequiredArgsConstructor
public class ProfileReviewController {

    private final IProfileReviewService profileReviewService;

    @Operation(summary = "分页获取昵称审核列表")
    @GetMapping("/nickname/list")
    public Result nicknameList(@Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
                               @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") Integer size,
                               @Parameter(description = "状态筛选，逗号分隔") @RequestParam(required = false) String status) {
        return profileReviewService.getNicknameList(page, size, status);
    }

    @Operation(summary = "分页获取头像审核列表")
    @GetMapping("/icon/list")
    public Result iconList(@Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
                           @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") Integer size,
                           @Parameter(description = "状态筛选，逗号分隔") @RequestParam(required = false) String status) {
        return profileReviewService.getIconList(page, size, status);
    }

    @OperationLog("审核通过昵称")
    @Operation(summary = "审核通过昵称")
    @PutMapping("/nickname/approve/{userId}")
    public Result approveNickname(@Parameter(description = "用户ID") @PathVariable Long userId) {
        return profileReviewService.approveNickname(userId);
    }

    @OperationLog("拒绝昵称修改")
    @Operation(summary = "拒绝昵称修改")
    @PutMapping("/nickname/reject/{userId}")
    public Result rejectNickname(@Parameter(description = "用户ID") @PathVariable Long userId,
                                 @Parameter(description = "请求体") @Valid @RequestBody RejectRequest request) {
        return profileReviewService.rejectNickname(userId, request.getReason());
    }

    @OperationLog("审核通过头像")
    @Operation(summary = "审核通过头像")
    @PutMapping("/icon/approve/{userId}")
    public Result approveIcon(@Parameter(description = "用户ID") @PathVariable Long userId) {
        return profileReviewService.approveIcon(userId);
    }

    @OperationLog("拒绝头像修改")
    @Operation(summary = "拒绝头像修改")
    @PutMapping("/icon/reject/{userId}")
    public Result rejectIcon(@Parameter(description = "用户ID") @PathVariable Long userId,
                             @Parameter(description = "请求体") @Valid @RequestBody RejectRequest request) {
        return profileReviewService.rejectIcon(userId, request.getReason());
    }

    @Operation(summary = "获取待审核数量")
    @GetMapping("/pending-count")
    public Result pendingCount() {
        return profileReviewService.getPendingCount();
    }

    @Operation(summary = "获取昵称/头像审核各状态数量统计")
    @GetMapping("/status-counts")
    public Result statusCounts() {
        return profileReviewService.getStatusCounts();
    }

    @Data
    public static class RejectRequest {
        @NotBlank(message = "拒绝原因不能为空")
        private String reason;
    }
}