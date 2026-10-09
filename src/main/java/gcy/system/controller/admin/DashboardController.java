package gcy.system.controller.admin;

import gcy.system.entity.dto.Result;
import gcy.system.service.admin.IDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台仪表盘控制器。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Tag(name = "仪表盘", description = "仪表盘相关接口")
@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final IDashboardService dashboardService;

    /**
     * 获取概览统计：订单数、销售额、用户数等关键指标。
     */
    @Operation(summary = "获取仪表盘概览统计数据")
    @GetMapping("/stats")
    public Result stats() {
        return dashboardService.getStats();
    }

    /**
     * 获取订单趋势数据。
     */
    @Operation(summary = "获取订单趋势数据")
    @GetMapping("/order-trend")
    public Result orderTrend() {
        return dashboardService.getOrderTrend();
    }

    /**
     * 获取库存低于预警线的家具列表。
     */
    @Operation(summary = "获取低库存预警数据")
    @GetMapping("/low-stock")
    public Result lowStock() {
        return dashboardService.getLowStock();
    }

    /**
     * 获取热门家具排行数据。
     */
    @Operation(summary = "获取热门家具排行数据")
    @GetMapping("/top-furniture")
    public Result topFurniture() {
        return dashboardService.getTopFurniture();
    }
}
