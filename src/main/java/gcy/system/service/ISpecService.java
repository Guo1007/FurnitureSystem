package gcy.system.service;

import gcy.system.entity.dto.Result;
import gcy.system.entity.dto.admin.FurnitureSpecDTO;

/**
 * 家具规格与SKU服务接口。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
public interface ISpecService {

    /**
     * 根据家具ID获取该家具关联的所有规格及SKU信息。
     */
    Result getSpecAndSkuByFurnitureId(Long furnitureId);

    /**
     * 保存规格及SKU数据；已存在则更新，否则新增。
     */
    Result saveSpecAndSku(FurnitureSpecDTO dto);

    /**
     * 获取指定家具当前可用的规格及SKU；与 {@link #getSpecAndSkuByFurnitureId(Long)} 的区别是仅返回可用状态。
     */
    Result getAvailableSpecAndSku(Long furnitureId);
}