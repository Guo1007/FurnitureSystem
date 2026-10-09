package gcy.system.service.admin.Impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import gcy.system.entity.dto.Result;
import gcy.system.entity.dto.admin.AdminFurnitureTypeFormDTO;
import gcy.system.entity.pojo.Furniture;
import gcy.system.entity.pojo.FurnitureType;
import gcy.system.exception.BusinessException;
import gcy.system.mapper.FurnitureMapper;
import gcy.system.mapper.FurnitureTypeMapper;
import gcy.system.service.admin.IFurnitureTypeManageService;
import gcy.system.utils.AfterCommit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static gcy.system.utils.RedisConstants.CACHE_FURNITURE_TYPE_KEY;

/**
 * 家具类型管理服务实现类。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Service
@RequiredArgsConstructor
public class FurnitureTypeManageServiceImpl extends ServiceImpl<FurnitureTypeMapper, FurnitureType> implements IFurnitureTypeManageService {

    private final FurnitureMapper furnitureMapper;

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 清除家具类型列表缓存，删除动作推迟到事务提交之后。
     * <p>
     * 提交前删除会让并发请求在缓存未命中时读到未提交的旧数据并回写缓存，导致旧值长期残留；
     * 当前无事务时立即删除。
     */
    private void clearTypeCache() {
        AfterCommit.run(() -> stringRedisTemplate.delete(CACHE_FURNITURE_TYPE_KEY));
    }

    /**
     * 新增家具类型。
     */
    @Override
    @Transactional
    public Result addFurnitureType(AdminFurnitureTypeFormDTO dto) {
        FurnitureType type = BeanUtil.toBean(dto, FurnitureType.class);
        boolean success = this.save(type);
        if (success) {
            clearTypeCache();
        }
        return success ? Result.okMsg("添加成功") : Result.fail("添加失败");
    }

    /**
     * 编辑家具类型。
     */
    @Override
    @Transactional
    public Result editFurnitureType(AdminFurnitureTypeFormDTO dto) {
        if (dto.getId() == null) {
            return Result.fail("ID 不能为空");
        }
        FurnitureType type = BeanUtil.toBean(dto, FurnitureType.class);
        boolean success = this.updateById(type);
        if (success) {
            clearTypeCache();
        }
        return success ? Result.okMsg("更新成功") : Result.fail("更新失败");
    }

    /**
     * 删除家具类型；该分类下存在商品时拒绝删除，提示先迁移或删除商品。
     */
    @Override
    @Transactional
    public Result deleteFurnitureType(Long id) {
        Long furnitureCount = furnitureMapper.selectCount(
                new LambdaQueryWrapper<Furniture>().eq(Furniture::getTypeId, id));
        if (furnitureCount > 0) {
            throw new BusinessException("该分类下有 " + furnitureCount + " 件商品，请先迁移或删除商品后再操作");
        }
        boolean success = this.removeById(id);
        if (success) {
            clearTypeCache();
        }
        return success ? Result.okMsg("删除成功") : Result.fail("删除失败");
    }

    /**
     * 查询家具类型详情。
     */
    @Override
    public Result getFurnitureTypeById(Long id) {
        FurnitureType type = this.getById(id);
        if (type == null) {
            return Result.fail("数据不存在");
        }
        AdminFurnitureTypeFormDTO dto = BeanUtil.toBean(type, AdminFurnitureTypeFormDTO.class);
        return Result.ok(dto);
    }

    /**
     * 分页查询家具类型，typeName 非空时按名称模糊匹配。
     */
    @Override
    public Result getFurnitureTypeList(Integer current, Integer size, String typeName) {
        Page<FurnitureType> page = new Page<>(current, size);
        LambdaQueryWrapper<FurnitureType> queryWrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(typeName)) {
            queryWrapper.like(FurnitureType::getName, typeName);
        }
        Page<FurnitureType> resultPage = page(page, queryWrapper);
        if (resultPage.getTotal() == 0) {
            return Result.fail("暂未发现任何数据！");
        }
        return Result.ok(resultPage);
    }
}