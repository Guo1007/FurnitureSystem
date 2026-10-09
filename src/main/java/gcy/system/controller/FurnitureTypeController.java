package gcy.system.controller;

import gcy.system.entity.dto.Result;
import gcy.system.security.Anonymous;
import gcy.system.service.IFurnitureTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 家具类型公开查询接口。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Tag(name = "家具类型", description = "家具类型相关接口")
@RestController
@RequestMapping("/furniture_type")
@Anonymous
@RequiredArgsConstructor
public class FurnitureTypeController {

    private final IFurnitureTypeService furnitureTypeService;

    /**
     * 获取所有家具类型列表。
     */
    @Operation(summary = "获取所有家具类型列表")
    @GetMapping("/list")
    public Result getTypeList() {
        return furnitureTypeService.queryFurnitureTypeList();
    }

}
