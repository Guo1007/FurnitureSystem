package gcy.system.controller.admin;

import gcy.system.aspect.OperationLog;
import gcy.system.entity.dto.Result;
import gcy.system.service.admin.ICommentManageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 评论管理控制器
 * <p>
 * 提供后台管理系统中评论、追评和审核评论的查询、审核、删除等管理功能。
 * 基础路径: /admin/comment
 * </p>
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Tag(name = "评论管理", description = "评论管理相关接口")
@RestController
@RequestMapping("/admin/comment")
@RequiredArgsConstructor
public class CommentManageController {

    private final ICommentManageService commentManageService;

    /**
     * 分页获取所有评论列表
     *
     * @param current 当前页码，默认值为1
     * @param size    每页显示条数，默认值为10
     * @return 包含分页评论数据的统一响应结果
     */
    @Operation(summary = "分页获取所有评论列表")
    @GetMapping("/list")
    public Result getAllComments(@Parameter(description = "当前页码") @RequestParam(defaultValue = "1") Integer current,
                                 @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") Integer size,
                                 @Parameter(description = "状态筛选，逗号分隔") @RequestParam(required = false) String statuses) {
        return commentManageService.getAllComments(current, size, statuses);
    }

    /**
     * 审核通过指定评论
     *
     * @param id 要审核通过的评论ID
     * @return 包含操作结果的统一响应结果
     */
    @OperationLog("审核通过评论")
    @Operation(summary = "审核通过指定评论")
    @PutMapping("/approve/{id}")
    public Result approveComment(@Parameter(description = "评论ID") @PathVariable Long id) {
        return commentManageService.approveComment(id);
    }

    /**
     * 驳回指定评论
     *
     * @param id 要驳回的评论ID
     * @return 包含操作结果的统一响应结果
     */
    @OperationLog("驳回评论")
    @Operation(summary = "驳回指定评论")
    @PutMapping("/reject/{id}")
    public Result rejectComment(@Parameter(description = "评论ID") @PathVariable Long id,
                                @Parameter(description = "请求体") @Valid @RequestBody RejectRequest request) {
        return commentManageService.rejectComment(id, request.getRejectReason());
    }

    /**
     * 分页获取所有追评列表
     *
     * @param current 当前页码，默认值为1
     * @param size    每页显示条数，默认值为10
     * @return 包含分页追评数据的统一响应结果
     */
    @Operation(summary = "分页获取所有追评列表")
    @GetMapping("/append/list")
    public Result getAllAppends(@Parameter(description = "当前页码") @RequestParam(defaultValue = "1") Integer current,
                                @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") Integer size,
                                @Parameter(description = "状态筛选，逗号分隔") @RequestParam(required = false) String statuses) {
        return commentManageService.getAllAppends(current, size, statuses);
    }

    /**
     * 审核通过指定追评
     *
     * @param id 要审核通过的追评ID
     * @return 包含操作结果的统一响应结果
     */
    @OperationLog("审核通过追评")
    @Operation(summary = "审核通过指定追评")
    @PutMapping("/append/approve/{id}")
    public Result approveAppend(@Parameter(description = "追评ID") @PathVariable Long id) {
        return commentManageService.approveAppend(id);
    }

    /**
     * 驳回指定追评
     *
     * @param id 要驳回的追评ID
     * @return 包含操作结果的统一响应结果
     */
    @OperationLog("驳回追评")
    @Operation(summary = "驳回指定追评")
    @PutMapping("/append/reject/{id}")
    public Result rejectAppend(@Parameter(description = "追评ID") @PathVariable Long id,
                               @Parameter(description = "请求体") @Valid @RequestBody RejectRequest request) {
        return commentManageService.rejectAppend(id, request.getRejectReason());
    }

    /**
     * 分页获取所有审核评论列表
     *
     * @param current 当前页码，默认值为1
     * @param size    每页显示条数，默认值为10
     * @return 包含分页审核评论数据的统一响应结果
     */
    @Operation(summary = "分页获取所有审核评论列表")
    @GetMapping("/review-comment/list")
    public Result getAllReviewComments(@Parameter(description = "当前页码") @RequestParam(defaultValue = "1") Integer current,
                                       @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") Integer size,
                                       @Parameter(description = "状态筛选，逗号分隔") @RequestParam(required = false) String statuses) {
        return commentManageService.getAllReviewComments(current, size, statuses);
    }

    /**
     * 审核通过指定审核评论
     *
     * @param id 要审核通过的审核评论ID
     * @return 包含操作结果的统一响应结果
     */
    @OperationLog("审核通过评论回复")
    @Operation(summary = "审核通过指定审核评论")
    @PutMapping("/review-comment/approve/{id}")
    public Result approveReviewComment(@Parameter(description = "审核评论ID") @PathVariable Long id) {
        return commentManageService.approveReviewComment(id);
    }

    /**
     * 驳回指定审核评论
     *
     * @param id 要驳回的审核评论ID
     * @return 包含操作结果的统一响应结果
     */
    @OperationLog("驳回评论回复")
    @Operation(summary = "驳回指定审核评论")
    @PutMapping("/review-comment/reject/{id}")
    public Result rejectReviewComment(@Parameter(description = "审核评论ID") @PathVariable Long id,
                                      @Parameter(description = "请求体") @Valid @RequestBody RejectRequest request) {
        return commentManageService.rejectReviewComment(id, request.getRejectReason());
    }

    /**
     * 获取待审核的评论数量
     *
     * @return 包含待审核数量的统一响应结果
     */
    @Operation(summary = "获取待审核评论数量")
    @GetMapping("/pending-count")
    public Result getPendingCount() {
        return commentManageService.getPendingCount();
    }

    /**
     * 获取各状态数量统计
     * <p>
     * 返回评价、追评、评价回复三种类型各状态（全部/待审核/已通过/已拒绝）的数量，
     * 用于管理端 Tab 标签的计数显示。
     * </p>
     *
     * @return 包含各状态数量统计的统一响应结果
     */
    @Operation(summary = "获取各状态数量统计")
    @GetMapping("/status-counts")
    public Result getStatusCounts() {
        return commentManageService.getStatusCounts();
    }

    /**
     * 删除指定评论
     *
     * @param id 要删除的评论ID
     * @return 包含操作结果的统一响应结果
     */
    @OperationLog("删除评论")
    @Operation(summary = "删除指定评论")
    @DeleteMapping("/{id}")
    public Result deleteComment(@Parameter(description = "评论ID") @PathVariable Long id) {
        return commentManageService.deleteComment(id);
    }

    /**
     * 批量删除评论
     *
     * @param ids 要删除的评论ID列表，通过请求体JSON数组传递
     * @return 包含操作结果的统一响应结果
     */
    @OperationLog("批量删除评论")
    @Operation(summary = "批量删除评论")
    @DeleteMapping("/batch")
    public Result batchDeleteComments(@Parameter(description = "评论ID列表") @RequestBody List<Long> ids) {
        return commentManageService.batchDeleteComments(ids);
    }

    /**
     * 删除指定追评
     *
     * @param id 要删除的追评ID
     * @return 包含操作结果的统一响应结果
     */
    @OperationLog("删除追评")
    @Operation(summary = "删除指定追评")
    @DeleteMapping("/append/{id}")
    public Result deleteAppend(@Parameter(description = "追评ID") @PathVariable Long id) {
        return commentManageService.deleteAppend(id);
    }

    /**
     * 批量删除追评
     *
     * @param ids 要删除的追评ID列表，通过请求体JSON数组传递
     * @return 包含操作结果的统一响应结果
     */
    @OperationLog("批量删除追评")
    @Operation(summary = "批量删除追评")
    @DeleteMapping("/append/batch")
    public Result batchDeleteAppends(@Parameter(description = "追评ID列表") @RequestBody List<Long> ids) {
        return commentManageService.batchDeleteAppends(ids);
    }

    /**
     * 删除指定审核评论
     *
     * @param id 要删除的审核评论ID
     * @return 包含操作结果的统一响应结果
     */
    @OperationLog("删除评论回复")
    @Operation(summary = "删除指定审核评论")
    @DeleteMapping("/review-comment/{id}")
    public Result deleteReviewComment(@Parameter(description = "审核评论ID") @PathVariable Long id) {
        return commentManageService.deleteReviewComment(id);
    }

    /**
     * 批量删除审核评论
     *
     * @param ids 要删除的审核评论ID列表，通过请求体JSON数组传递
     * @return 包含操作结果的统一响应结果
     */
    @OperationLog("批量删除评论回复")
    @Operation(summary = "批量删除审核评论")
    @DeleteMapping("/review-comment/batch")
    public Result batchDeleteReviewComments(@Parameter(description = "审核评论ID列表") @RequestBody List<Long> ids) {
        return commentManageService.batchDeleteReviewComments(ids);
    }

    /**
     * 驳回请求体，包含拒绝原因。
     */
    @Data
    public static class RejectRequest {
        /**
         * 拒绝原因
         */
        @NotBlank(message = "拒绝原因不能为空")
        private String rejectReason;
    }
}
