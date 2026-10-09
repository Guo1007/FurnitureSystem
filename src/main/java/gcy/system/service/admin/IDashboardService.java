package gcy.system.service.admin;

import gcy.system.entity.dto.Result;

/**
 * 后台管理仪表盘服务接口。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
public interface IDashboardService {

    /**
     * 获取仪表盘概览统计数据
     */
    Result getStats();

    /**
     * 获取订单趋势数据
     */
    Result getOrderTrend();

    /**
     * 获取低库存预警列表
     */
    Result getLowStock();

    /**
     * 获取热门家具排行榜
     */
    Result getTopFurniture();
}
