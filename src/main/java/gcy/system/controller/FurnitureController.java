package gcy.system.controller;

import gcy.system.entity.dto.Result;
import gcy.system.security.Anonymous;
import gcy.system.service.IFurnitureService;
import gcy.system.service.ISpecService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 家具管理控制器。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Tag(name = "家具管理", description = "家具管理相关接口")
@RestController
@RequestMapping("/furniture")
@Anonymous
@RequiredArgsConstructor
public class FurnitureController {

    private final IFurnitureService furnitureService;

    private final ISpecService specService;

    /**
     * 分页查询家具列表，支持类型、关键词、库存状态、品牌筛选及排序和推荐过滤。
     */
    @Operation(summary = "分页查询家具列表")
    @GetMapping("/list")
    public Result list(
            @Parameter(description = "家具类型ID") @RequestParam(required = false) Long typeId,
            @Parameter(description = "当前页码") @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "搜索关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "库存状态") @RequestParam(required = false) String stockStatus,
            @Parameter(description = "品牌名称") @RequestParam(required = false) String brand,
            @Parameter(description = "排序字段") @RequestParam(required = false) String sortBy,
            @Parameter(description = "排序方式(asc/desc)") @RequestParam(required = false) String sortOrder,
            @Parameter(description = "是否推荐") @RequestParam(required = false) Integer isRecommended) {
        return furnitureService.getFurnitureByType(typeId, current, size, keyword, stockStatus, brand, sortBy, sortOrder, isRecommended);
    }

    /**
     * 查询热销家具排行。
     */
    @Operation(summary = "查询热销家具排行")
    @GetMapping("/top-selling")
    public Result topSelling(@Parameter(description = "返回数量上限") @RequestParam(defaultValue = "8") Integer limit) {
        return furnitureService.getTopSelling(limit);
    }

    /**
     * 查询家具品牌列表，可按类型筛选。
     */
    @Operation(summary = "查询家具品牌列表")
    @GetMapping("/brands")
    public Result getFurnitureBrands(@Parameter(description = "家具类型ID") @RequestParam(required = false) Long typeId) {
        return furnitureService.getFurnitureBrandsByTypeId(typeId);
    }

    /**
     * 根据 ID 查询家具详情。
     */
    @Operation(summary = "根据ID查询家具详情")
    @GetMapping("/{id}")
    public Result queryFurnitureById(@Parameter(description = "家具ID") @PathVariable Long id) {
        return furnitureService.queryFurnitureById(id);
    }

    /**
     * 查询家具的可用规格和 SKU。
     */
    @Operation(summary = "查询家具的可用规格和SKU")
    @GetMapping("/{id}/specs")
    public Result getFurnitureSpecs(@Parameter(description = "家具ID") @PathVariable Long id) {
        return specService.getAvailableSpecAndSku(id);
    }

}
