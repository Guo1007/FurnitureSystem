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
 * 家具管理控制器
 * <p>提供家具列表查询、热销排行、品牌筛选、家具详情及规格查询等接口。
 * 所有接口均以 {@code /furniture} 为根路径，返回统一的 {@link Result} 对象。</p>
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
     * 分页查询家具列表
     * <p>支持按类型、关键词、库存状态、品牌进行筛选，支持排序和推荐过滤。</p>
     *
     * @param typeId        家具类型ID，可选，用于按类型筛选
     * @param current       当前页码，默认值为1
     * @param size          每页条数，默认值为10
     * @param keyword       搜索关键词，可选，用于模糊搜索家具名称
     * @param stockStatus   库存状态，可选，用于按库存状态筛选
     * @param brand         品牌名称，可选，用于按品牌筛选
     * @param sortBy        排序字段，可选，指定按哪个字段排序
     * @param sortOrder     排序方式，可选，asc 升序 / desc 降序
     * @param isRecommended 是否推荐，可选，1表示只查询推荐家具
     * @return 包含分页家具列表数据的统一响应结果
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
     * 查询热销家具排行
     * <p>返回销量最高的前N款家具。</p>
     *
     * @param limit 返回的家具数量上限，默认值为8
     * @return 包含热销家具列表数据的统一响应结果
     */
    @Operation(summary = "查询热销家具排行")
    @GetMapping("/top-selling")
    public Result topSelling(@Parameter(description = "返回数量上限") @RequestParam(defaultValue = "8") Integer limit) {
        return furnitureService.getTopSelling(limit);
    }

    /**
     * 查询家具品牌列表
     * <p>可按家具类型筛选，返回该类型下所有可用的品牌。</p>
     *
     * @param typeId 家具类型ID，可选，不传则返回所有类型的品牌
     * @return 包含品牌列表数据的统一响应结果
     */
    @Operation(summary = "查询家具品牌列表")
    @GetMapping("/brands")
    public Result getFurnitureBrands(@Parameter(description = "家具类型ID") @RequestParam(required = false) Long typeId) {
        return furnitureService.getFurnitureBrandsByTypeId(typeId);
    }

    /**
     * 根据ID查询家具详情
     * <p>通过路径变量传入家具ID，返回该家具的完整信息。</p>
     *
     * @param id 家具ID，必填，通过URL路径传入
     * @return 包含家具详情数据的统一响应结果
     */
    @Operation(summary = "根据ID查询家具详情")
    @GetMapping("/{id}")
    public Result queryFurnitureById(@Parameter(description = "家具ID") @PathVariable Long id) {
        return furnitureService.queryFurnitureById(id);
    }

    /**
     * 查询家具的可用规格和SKU
     * <p>根据家具ID返回该家具所有可用的规格组合及对应的SKU信息。</p>
     *
     * @param id 家具ID，必填，通过URL路径传入
     * @return 包含规格和SKU列表数据的统一响应结果
     */
    @Operation(summary = "查询家具的可用规格和SKU")
    @GetMapping("/{id}/specs")
    public Result getFurnitureSpecs(@Parameter(description = "家具ID") @PathVariable Long id) {
        return specService.getAvailableSpecAndSku(id);
    }

}
