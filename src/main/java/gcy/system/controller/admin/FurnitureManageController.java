package gcy.system.controller.admin;

import gcy.system.aspect.OperationLog;
import gcy.system.entity.dto.Result;
import gcy.system.entity.dto.admin.AdminFurnitureFormDTO;
import gcy.system.exception.BusinessException;
import gcy.system.integration.OssService;
import gcy.system.service.admin.IFurnitureManageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 家具管理控制器。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Tag(name = "家具管理", description = "家具管理相关接口")
@RestController
@RequestMapping("/admin/furniture")
@RequiredArgsConstructor
public class FurnitureManageController {

    private final IFurnitureManageService furnitureManageService;

    private final OssService ossService;

    /**
     * 分页查询家具列表。
     */
    @Operation(summary = "分页查询家具列表")
    @GetMapping("/list")
    public Result getFurnitureList(@Parameter(description = "当前页码") @RequestParam(defaultValue = "1") Integer current,
                                   @Parameter(description = "每页显示条数") @RequestParam(defaultValue = "10") Integer size,
                                   @Parameter(description = "家具类型ID") @RequestParam(required = false) Long typeId,
                                   @Parameter(description = "家具名称") @RequestParam(required = false) String fName,
                                   @Parameter(description = "库存状态") @RequestParam(required = false) String stockStatus,
                                   @Parameter(description = "品牌") @RequestParam(required = false) String brand) {
        return furnitureManageService.getFurnitureList(current, size, typeId, fName, stockStatus, brand);
    }

    /**
     * 新增家具。
     */
    @OperationLog("新增商品")
    @Operation(summary = "新增家具")
    @PostMapping("/add")
    public Result addFurniture(@Parameter(description = "请求体") @RequestBody @Valid AdminFurnitureFormDTO dto) {
        return furnitureManageService.addFurniture(dto);
    }

    /**
     * 编辑家具。
     */
    @OperationLog("编辑商品")
    @Operation(summary = "编辑家具")
    @PutMapping("/edit")
    public Result editFurniture(@Parameter(description = "请求体") @RequestBody @Valid AdminFurnitureFormDTO dto) {
        return furnitureManageService.editFurniture(dto);
    }

    /**
     * 上传家具图片至 OSS，返回访问 URL。
     */
    @Operation(summary = "上传家具图片")
    @PostMapping("/upload")
    public Result uploadFurnitureImage(@Parameter(description = "图片文件") @RequestParam("file") MultipartFile file) {
        try {
            String url = ossService.upload(file, "furniture");
            return Result.ok(url);
        } catch (Exception e) {
            throw new BusinessException("上传失败：" + e.getMessage());
        }
    }


    /**
     * 删除家具。
     */
    @OperationLog("删除商品")
    @Operation(summary = "删除家具")
    @DeleteMapping("/delete/{id}")
    public Result deleteFurniture(@Parameter(description = "家具ID") @PathVariable Long id) {
        return furnitureManageService.deleteFurniture(id);
    }

}
