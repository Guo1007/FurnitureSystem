package gcy.system.service.admin;

import com.baomidou.mybatisplus.extension.service.IService;
import gcy.system.entity.dto.Result;
import gcy.system.entity.dto.admin.AdminFurnitureFormDTO;
import gcy.system.entity.pojo.Furniture;

/**
 * 家具管理服务接口。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
public interface IFurnitureManageService extends IService<Furniture> {

    /**
     * 分页查询家具列表，支持按家具类型、名称、库存状态和品牌筛选。
     */
    Result getFurnitureList(Integer current, Integer size, Long typeId, String fName,
                            String stockStatus, String brand);

    /**
     * 新增家具。
     */
    Result addFurniture(AdminFurnitureFormDTO dto);

    /**
     * 编辑已有家具信息。
     */
    Result editFurniture(AdminFurnitureFormDTO dto);

    /**
     * 根据家具ID删除家具。
     */
    Result deleteFurniture(Long furnitureId);

}
