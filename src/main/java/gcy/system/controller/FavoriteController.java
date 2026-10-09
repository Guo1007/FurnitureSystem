package gcy.system.controller;

import gcy.system.entity.dto.Result;
import gcy.system.service.IFavoriteService;
import gcy.system.utils.UserHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 收藏控制器。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Tag(name = "收藏管理", description = "收藏管理相关接口")
@RestController
@RequestMapping("/favorite")
@RequiredArgsConstructor
public class FavoriteController {

    private final IFavoriteService favoriteService;

    /**
     * 分页查询当前用户的收藏列表。
     */
    @Operation(summary = "获取当前用户的收藏列表")
    @GetMapping("/list")
    public Result list(@Parameter(description = "当前页码") @RequestParam(defaultValue = "1") Integer current,
                       @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") Integer size) {
        Long userId = UserHolder.getUser().getId();
        return favoriteService.getFavoritesByUserId(userId, current, size);
    }

    /**
     * 查询当前用户是否已收藏指定家具。
     */
    @Operation(summary = "检查指定家具是否已被收藏")
    @GetMapping("/check/{furnitureId}")
    public Result check(@Parameter(description = "家具ID") @PathVariable Long furnitureId) {
        Long userId = UserHolder.getUser().getId();
        return favoriteService.checkFavorite(userId, furnitureId);
    }

    /**
     * 切换指定家具的收藏状态：未收藏则收藏，已收藏则取消。
     */
    @Operation(summary = "切换指定家具的收藏状态")
    @PostMapping("/toggle/{furnitureId}")
    public Result toggle(@Parameter(description = "家具ID") @PathVariable Long furnitureId) {
        Long userId = UserHolder.getUser().getId();
        return favoriteService.toggleFavorite(userId, furnitureId);
    }

}
