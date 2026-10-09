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
 * 家具规格管理控制器。
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
     */
    @Operation(summary = "根据家具ID查询规格和SKU信息")
    @GetMapping("/{furnitureId}")
    public Result getSpecAndSku(@Parameter(description = "家具ID") @PathVariable Long furnitureId) {
        return specService.getSpecAndSkuByFurnitureId(furnitureId);
    }

    /**
     * 保存家具的规格和SKU信息
     */
    @OperationLog("保存规格和SKU")
    @Operation(summary = "保存家具的规格和SKU信息")
    @PostMapping("/save")
    public Result saveSpecAndSku(@Parameter(description = "请求体") @Valid @RequestBody FurnitureSpecDTO dto) {
        return specService.saveSpecAndSku(dto);
    }
}
