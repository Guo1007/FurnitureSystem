package gcy.system.controller;

import gcy.system.entity.dto.CartAddDTO;
import gcy.system.entity.dto.Result;
import gcy.system.service.ICartService;
import gcy.system.utils.UserHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 购物车控制器，接口均需登录。
 *
 * @author 郭名城
 * @date 2026-10-23
 */
@Tag(name = "购物车", description = "购物车相关接口")
@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {

    private final ICartService cartService;

    /**
     * 获取购物车列表。
     */
    @Operation(summary = "获取购物车列表")
    @GetMapping
    public Result list() {
        return cartService.list(UserHolder.getUser().getId());
    }

    /**
     * 加入购物车。
     */
    @Operation(summary = "加入购物车")
    @PostMapping
    public Result add(@Valid @RequestBody CartAddDTO dto) {
        return cartService.addItem(UserHolder.getUser().getId(), dto);
    }

    /**
     * 修改购物车项数量。
     */
    @Operation(summary = "修改购物车项数量")
    @PutMapping("/{id}/quantity")
    public Result updateQuantity(@Parameter(description = "购物车项ID") @PathVariable Long id,
                                 @Parameter(description = "数量") @RequestParam Integer quantity) {
        return cartService.updateQuantity(UserHolder.getUser().getId(), id, quantity);
    }

    /**
     * 修改结算勾选状态。
     */
    @Operation(summary = "修改结算勾选状态")
    @PutMapping("/{id}/checked")
    public Result updateChecked(@Parameter(description = "购物车项ID") @PathVariable Long id,
                                @Parameter(description = "勾选状态：1勾选，0取消") @RequestParam Integer checked) {
        return cartService.updateChecked(UserHolder.getUser().getId(), id, checked);
    }

    /**
     * 删除购物车单项。
     */
    @Operation(summary = "删除购物车单项")
    @DeleteMapping("/{id}")
    public Result remove(@Parameter(description = "购物车项ID") @PathVariable Long id) {
        return cartService.removeItem(UserHolder.getUser().getId(), id);
    }

    /**
     * 清空购物车。
     */
    @Operation(summary = "清空购物车")
    @DeleteMapping
    public Result clear() {
        return cartService.clear(UserHolder.getUser().getId());
    }
}
