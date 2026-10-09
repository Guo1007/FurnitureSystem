package gcy.system.controller.admin;

import gcy.system.aspect.OperationLog;
import gcy.system.entity.dto.Result;
import gcy.system.entity.dto.admin.AdminFurnitureTypeFormDTO;
import gcy.system.exception.BusinessException;
import gcy.system.integration.OssService;
import gcy.system.service.admin.IFurnitureTypeManageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 家具类型管理控制器
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Tag(name = "家具类型管理", description = "家具类型管理相关接口")
@RestController
@Validated
@RequestMapping("/admin/furniture_type")
@RequiredArgsConstructor
public class FurnitureTypeManageController {

    private final IFurnitureTypeManageService furnitureTypeManageService;

    private final OssService ossService;

    /**
     * 新增家具类型
     */
    @OperationLog("新增分类")
    @Operation(summary = "新增家具类型")
    @PostMapping("/add")
    public Result addFurnitureType(@Parameter(description = "请求体") @Valid @RequestBody AdminFurnitureTypeFormDTO dto) {
        return furnitureTypeManageService.addFurnitureType(dto);
    }

    /**
     * 编辑家具类型
     */
    @OperationLog("编辑分类")
    @Operation(summary = "编辑家具类型")
    @PutMapping("/update")
    public Result editFurnitureType(@Parameter(description = "请求体") @Valid @RequestBody AdminFurnitureTypeFormDTO dto) {
        return furnitureTypeManageService.editFurnitureType(dto);
    }

    /**
     * 上传家具类型图标至 OSS，返回可访问的 URL。
     */
    @Operation(summary = "上传家具类型图标")
    @PostMapping("/upload")
    public Result uploadTypeIcon(@Parameter(description = "图标文件") @RequestParam("file") MultipartFile file) {
        try {
            String url = ossService.upload(file, "type");
            return Result.ok(url);
        } catch (Exception e) {
            throw new BusinessException("上传失败：" + e.getMessage());
        }
    }

    /**
     * 删除家具类型
     */
    @OperationLog("删除分类")
    @Operation(summary = "删除家具类型")
    @DeleteMapping("/delete/{id}")
    public Result deleteFurnitureType(@Parameter(description = "家具类型ID") @PathVariable Long id) {
        return furnitureTypeManageService.deleteFurnitureType(id);
    }

    /**
     * 获取家具类型详情
     */
    @Operation(summary = "获取家具类型详情")
    @GetMapping("/info/{id}")
    public Result getFurnitureTypeInfo(@Parameter(description = "家具类型ID") @PathVariable Long id) {
        return furnitureTypeManageService.getFurnitureTypeById(id);
    }

    /**
     * 分页查询家具类型列表，支持按名称模糊筛选。
     */
    @Operation(summary = "分页查询家具类型列表")
    @GetMapping("/list")
    public Result getFurnitureTypeList(@Parameter(description = "当前页码") @RequestParam(defaultValue = "1") Integer current,
                                       @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") Integer size,
                                       @Parameter(description = "名称搜索关键字") @RequestParam(required = false) String name) {
        return furnitureTypeManageService.getFurnitureTypeList(current, size, name);
    }
}
