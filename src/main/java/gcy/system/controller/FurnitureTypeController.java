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
 * 家具类型控制器
 * <p>
 * 处理与家具类型相关的HTTP请求，提供家具类型数据的查询接口。
 * 基础请求路径为 /furniture_type。
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
     * 获取所有家具类型列表
     * <p>
     * 通过GET请求访问 /furniture_type/list 路径，
     * 调用服务层查询所有可用的家具类型数据并返回。
     *
     * @return 包含家具类型列表的Result对象，其中封装了查询结果的状态与数据
     */
    @Operation(summary = "获取所有家具类型列表")
    @GetMapping("/list")
    public Result getTypeList() {
        return furnitureTypeService.queryFurnitureTypeList();
    }

}
