package gcy.system.controller;

import gcy.system.entity.dto.Result;
import gcy.system.entity.dto.UserDTO;
import gcy.system.entity.pojo.CommentAppend;
import gcy.system.entity.pojo.GoodsComment;
import gcy.system.integration.OssService;
import gcy.system.security.Anonymous;
import gcy.system.service.ICommentService;
import gcy.system.utils.UserHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 商品评论控制器
 * <p>
 * 提供评论相关的 REST API，包括评论的增删查、追评管理、以及评论图片和视频的上传功能。
 * 所有评论操作均基于当前登录用户进行权限校验。
 * </p>
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Tag(name = "评论", description = "评论相关接口")
@RestController
@RequestMapping("/comment")
@RequiredArgsConstructor
public class CommentController {

    private final ICommentService commentService;

    private final OssService ossService;

    /**
     * 根据商品ID获取评论列表（分页）
     * <p>
     * GET /comment/list/{goodsId}?current=1&size=10
     * </p>
     *
     * @param goodsId 商品ID
     * @param current 当前页码，默认值为1
     * @param size    每页条数，默认值为10
     * @return 包含分页评论数据的 Result 对象
     */
    @Operation(summary = "根据商品ID获取评论列表")
    @Anonymous
    @GetMapping("/list/{goodsId}")
    public Result list(@Parameter(description = "商品ID") @PathVariable Long goodsId,
                       @Parameter(description = "当前页码") @RequestParam(defaultValue = "1") Integer current,
                       @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") Integer size) {
        // 游客可浏览评论：未登录时 userId 传 0，仅返回审核通过的公开评论
        UserDTO user = UserHolder.getUser();
        Long userId = user != null ? user.getId() : 0L;
        return commentService.getCommentsByGoodsId(goodsId, userId, current, size);
    }

    /**
     * 根据订单ID获取评论列表
     * <p>
     * GET /comment/list/order/{orderId}
     * </p>
     *
     * @param orderId 订单ID
     * @return 包含该订单下所有评论数据的 Result 对象
     */
    @Operation(summary = "根据订单ID获取评论列表")
    @GetMapping("/list/order/{orderId}")
    public Result listByOrderId(@Parameter(description = "订单ID") @PathVariable Long orderId) {
        // 仅登录用户可查看自己订单下的评价
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail(401, "登录已过期，请重新登录");
        }
        return commentService.getCommentsByOrderId(orderId, user.getId());
    }

    /**
     * 新增商品评论
     * <p>
     * POST /comment/add
     * </p>
     *
     * @param comment 评论实体对象，包含评论内容、商品ID、评分等信息
     * @return 包含新增评论结果的 Result 对象
     */
    @Operation(summary = "新增商品评论")
    @PostMapping("/add")
    public Result add(@Parameter(description = "请求体") @Valid @RequestBody GoodsComment comment) {
        Long userId = UserHolder.getUser().getId();
        return commentService.addComment(comment, userId);
    }

    /**
     * 追加评论（追评）
     * <p>
     * POST /comment/append
     * </p>
     *
     * @param append 追评实体对象，包含原评论ID和追加内容
     * @return 包含追评操作结果的 Result 对象
     */
    @Operation(summary = "追加评论")
    @PostMapping("/append")
    public Result append(@Parameter(description = "请求体") @Valid @RequestBody CommentAppend append) {
        Long userId = UserHolder.getUser().getId();
        return commentService.appendComment(append, userId);
    }

    /**
     * 删除评论
     * <p>
     * DELETE /comment/{commentId}
     * </p>
     *
     * @param commentId 要删除的评论ID
     * @return 包含删除操作结果的 Result 对象
     */
    @Operation(summary = "删除评论")
    @DeleteMapping("/{commentId}")
    public Result delete(@Parameter(description = "评论ID") @PathVariable Long commentId) {
        Long userId = UserHolder.getUser().getId();
        return commentService.deleteComment(commentId, userId);
    }

    /**
     * 删除追评
     * <p>
     * DELETE /comment/append/{appendId}
     * </p>
     *
     * @param appendId 要删除的追评ID
     * @return 包含删除操作结果的 Result 对象
     */
    @Operation(summary = "删除追评")
    @DeleteMapping("/append/{appendId}")
    public Result deleteAppend(@Parameter(description = "追评ID") @PathVariable Long appendId) {
        Long userId = UserHolder.getUser().getId();
        return commentService.deleteAppend(appendId, userId);
    }

    /**
     * 删除评论回复
     * <p>
     * DELETE /comment/review/{reviewId}
     * </p>
     *
     * @param reviewId 要删除的回复ID
     * @return 包含删除操作结果的 Result 对象
     */
    @Operation(summary = "删除评论回复")
    @DeleteMapping("/review/{reviewId}")
    public Result deleteReview(@Parameter(description = "回复ID") @PathVariable Long reviewId) {
        Long userId = UserHolder.getUser().getId();
        return commentService.deleteReview(reviewId, userId);
    }

    /**
     * 上传评论图片
     * <p>
     * POST /comment/upload/image
     * </p>
     *
     * @param file 上传的图片文件（MultipartFile）
     * @return 包含图片访问URL的 Result 对象
     */
    @Operation(summary = "上传评论图片")
    @PostMapping("/upload/image")
    public Result uploadImage(@Parameter(description = "图片文件") @RequestParam("file") MultipartFile file) {
        String url = ossService.upload(file, "comment/image");
        return Result.ok(url);
    }

    /**
     * 上传评论视频
     * <p>
     * POST /comment/upload/video
     * </p>
     *
     * @param file 上传的视频文件（MultipartFile）
     * @return 包含视频访问URL的 Result 对象
     */
    @Operation(summary = "上传评论视频")
    @PostMapping("/upload/video")
    public Result uploadVideo(@Parameter(description = "视频文件") @RequestParam("file") MultipartFile file) {
        String url = ossService.upload(file, "comment/video");
        return Result.ok(url);
    }
}
