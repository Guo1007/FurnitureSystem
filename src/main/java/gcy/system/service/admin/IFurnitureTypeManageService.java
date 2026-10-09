package gcy.system.service.admin;

import com.baomidou.mybatisplus.extension.service.IService;
import gcy.system.entity.dto.Result;
import gcy.system.entity.dto.admin.AdminFurnitureTypeFormDTO;
import gcy.system.entity.pojo.FurnitureType;

/**
 * 家具类型管理服务接口。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
public interface IFurnitureTypeManageService extends IService<FurnitureType> {

    /**
     * 添加新的家具类型。
     */
    Result addFurnitureType(AdminFurnitureTypeFormDTO dto);

    /**
     * 编辑已有的家具类型。
     *
     * @param dto 表单数据，必须携带要编辑的记录 ID
     */
    Result editFurnitureType(AdminFurnitureTypeFormDTO dto);

    /**
     * 删除指定的家具类型。
     */
    Result deleteFurnitureType(Long id);

    /**
     * 根据ID获取单个家具类型的详细信息。
     */
    Result getFurnitureTypeById(Long id);

    /**
     * 分页查询家具类型列表，支持按名称模糊搜索。
     *
     * @param typeName 为空时返回全部
     */
    Result getFurnitureTypeList(Integer current, Integer size, String typeName);
}
