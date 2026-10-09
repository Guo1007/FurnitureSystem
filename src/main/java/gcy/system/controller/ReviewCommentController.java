package gcy.system.controller;

import gcy.system.entity.dto.Result;
import gcy.system.entity.dto.UserDTO;
import gcy.system.entity.pojo.ReviewComment;
import gcy.system.security.Anonymous;
import gcy.system.service.IReviewCommentService;
import gcy.system.utils.UserHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 评审评论控制器，列表接口匿名可访问，其余需登录。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Tag(name = "评审评论", description = "评审评论相关接口")
@RestController
@RequestMapping("/review-comment")
@RequiredArgsConstructor
public class ReviewCommentController {

    private final IReviewCommentService reviewCommentService;

    /**
     * 查询指定评审的评论列表，返回父评论与子回复的层级结构，并标记每条评论是否属于当前用户。
     */
    @Operation(summary = "根据评审ID查询评论列表")
    @Anonymous
    @GetMapping("/list/{reviewId}")
    public Result list(@Parameter(description = "评审ID") @PathVariable Long reviewId) {
        // 游客可浏览评论：未登录时 userId 传 0，仅返回审核通过的公开评论
        UserDTO user = UserHolder.getUser();
        Long userId = user != null ? user.getId() : 0L;
        return reviewCommentService.getCommentsByReviewId(reviewId, userId);
    }

    /**
     * 新增评论或回复（parentId 非空即回复）。
     */
    @Operation(summary = "新增评论")
    @PostMapping("/add")
    public Result add(@Parameter(description = "请求体") @Valid @RequestBody ReviewComment comment) {
        Long userId = UserHolder.getUser().getId();
        return reviewCommentService.addComment(comment, userId);
    }

    /**
     * 删除评论，仅评论作者本人可删。
     */
    @Operation(summary = "删除评论")
    @DeleteMapping("/{commentId}")
    public Result delete(@Parameter(description = "评论ID") @PathVariable Long commentId) {
        Long userId = UserHolder.getUser().getId();
        return reviewCommentService.deleteComment(commentId, userId);
    }
}
