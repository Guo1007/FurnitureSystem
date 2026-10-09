package gcy.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import gcy.system.entity.dto.Result;
import gcy.system.entity.pojo.Furniture;

/**
 * 家具服务接口。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
public interface IFurnitureService extends IService<Furniture> {

    /**
     * 根据家具ID查询单个家具的详细信息。
     */
    Result queryFurnitureById(Long id);

    /**
     * 按类型分页查询家具，支持名称、库存状态、品牌筛选与排序。
     *
     * @param fName         家具名称，模糊匹配
     * @param isRecommended 1 表示仅推荐，0 或 null 不限制
     */
    Result getFurnitureByType(Long typeId, Integer current, Integer size,
                              String fName, String stockStatus, String brand,
                              String sortBy, String sortOrder,
                              Integer isRecommended);

    /**
     * 获取销量最高的若干家具（按销量降序）。
     */
    Result getTopSelling(Integer limit);

    /**
     * 获取指定类型下所有可用的品牌列表。
     */
    Result getFurnitureBrandsByTypeId(Long id);

}
