package gcy.system.controller.admin;

import gcy.system.aspect.OperationLog;
import gcy.system.entity.dto.Result;
import gcy.system.entity.dto.admin.FurnitureSpecDTO;
import gcy.system.service.ISpecService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 家具规格管理控制器
 * <p>
 * 提供后台管理系统中家具规格和SKU的查询与保存接口，
 * 路径前缀为 /admin/spec。
 * </p>
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Tag(name = "家具规格管理", description = "家具规格管理相关接口")
@RestController
@RequestMapping("/admin/spec")
@RequiredArgsConstructor
public class SpecController {

    private final ISpecService specService;

    /**
     * 根据家具ID查询规格和SKU信息
     * <p>
     * 通过 GET 请求访问 /admin/spec/{furnitureId}，
     * 返回指定家具的所有规格选项和对应的SKU数据。
     * </p>
     *
     * @param furnitureId 家具的唯一标识ID
     * @return 包含规格和SKU信息的统一响应结果
     */
    @Operation(summary = "根据家具ID查询规格和SKU信息")
    @GetMapping("/{furnitureId}")
    public Result getSpecAndSku(@Parameter(description = "家具ID") @PathVariable Long furnitureId) {
        return specService.getSpecAndSkuByFurnitureId(furnitureId);
    }

    /**
     * 保存家具的规格和SKU信息
     * <p>
     * 通过 POST 请求访问 /admin/spec/save，
     * 接收完整的规格与SKU数据传输对象并持久化到数据库。
     * </p>
     *
     * @param dto 家具规格数据传输对象，包含规格选项和SKU的完整信息
     * @return 保存操作结果的统一响应结果
     */
    @OperationLog("保存规格和SKU")
    @Operation(summary = "保存家具的规格和SKU信息")
    @PostMapping("/save")
    public Result saveSpecAndSku(@Parameter(description = "请求体") @Valid @RequestBody FurnitureSpecDTO dto) {
        return specService.saveSpecAndSku(dto);
    }
}
