package gcy.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import gcy.system.entity.dto.Result;
import gcy.system.entity.pojo.FurnitureType;

/**
 * 家具类型服务接口。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
public interface IFurnitureTypeService extends IService<FurnitureType> {

    /**
     * 查询全部家具类型。
     */
    Result queryFurnitureTypeList();

}
