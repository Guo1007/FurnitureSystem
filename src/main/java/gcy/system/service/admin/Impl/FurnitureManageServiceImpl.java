package gcy.system.service.admin.Impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import gcy.system.entity.dto.Result;
import gcy.system.entity.dto.admin.AdminFurnitureFormDTO;
import gcy.system.entity.pojo.Favorite;
import gcy.system.entity.pojo.Furniture;
import gcy.system.entity.pojo.Notification;
import gcy.system.entity.pojo.Sku;
import gcy.system.exception.BusinessException;
import gcy.system.mapper.FavoriteMapper;
import gcy.system.mapper.FurnitureMapper;
import gcy.system.mapper.NotificationMapper;
import gcy.system.mapper.SkuMapper;
import gcy.system.service.Impl.FurnitureServiceImpl;
import gcy.system.service.admin.IFurnitureManageService;
import gcy.system.utils.RedisConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;

/**
 * 管理员家具管理服务实现类
 * <p>
 * 提供管理员后台对家具商品的增删改查功能，包括分页查询、新增、编辑和删除家具。
 * 在编辑和删除操作中会同步维护 Redis 缓存数据。
 * </p>
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FurnitureManageServiceImpl extends ServiceImpl<FurnitureMapper, Furniture>
        implements IFurnitureManageService {

    private final FurnitureMapper furnitureMapper;

    private final SkuMapper skuMapper;

    private final StringRedisTemplate stringRedisTemplate;

    private final NotificationMapper notificationMapper;

    private final FavoriteMapper favoriteMapper;

    /**
     * 分页查询家具列表
     * <p>
     * 支持按家具分类ID、家具名称（模糊查询）、库存状态和品牌进行筛选，返回分页结果。
     * 库存状态筛选逻辑委托给 {@link FurnitureServiceImpl#applyStockStatusFilter} 处理。
     * </p>
     *
     * @param current     当前页码
     * @param size        每页条数
     * @param typeId      家具分类ID，可为空（为空时不筛选分类）
     * @param fName       家具名称关键词，可为空（为空时不筛选名称）
     * @param stockStatus 库存状态筛选条件，可为空（为空时不筛选库存状态）
     * @param brand       品牌名称，可为空（为空时不筛选品牌）
     * @return 包含分页数据的 {@link Result} 对象，分页记录为 {@link Furniture} 列表
     */
    @Override
    public Result getFurnitureList(Integer current, Integer size, Long typeId, String fName,
                                   String stockStatus, String brand) {
        Page<Furniture> page = new Page<>(current, size);
        LambdaQueryWrapper<Furniture> wrapper = new LambdaQueryWrapper<>();
        if (typeId != null) {
            wrapper.eq(Furniture::getTypeId, typeId);
        }
        if (StrUtil.isNotBlank(fName)) {
            wrapper.like(Furniture::getFName, fName);
        }
        if (StrUtil.isNotBlank(brand)) {
            wrapper.eq(Furniture::getBrand, brand);
        }
        FurnitureServiceImpl.applyStockStatusFilter(wrapper, stockStatus);
        Page<Furniture> result = furnitureMapper.selectPage(page, wrapper);
        return Result.ok(result);
    }

    /**
     * 新增家具
     * <p>
     * 将管理员提交的家具表单数据转换为 {@link Furniture} 实体并保存到数据库。
     * 该方法在事务中执行，保存失败时抛出业务异常。
     * </p>
     *
     * @param dto 管理员提交的家具表单数据，包含名称、图标、分类、品牌等字段
     * @return 操作成功的 {@link Result} 对象
     * @throws BusinessException 当家具保存失败时抛出，提示用户联系系统管理人员
     */
    @Override
    @Transactional
    public Result addFurniture(AdminFurnitureFormDTO dto) {
        if (dto == null) {
            return Result.fail("请输入完整的新增家具信息！");
        }
        Furniture furniture = BeanUtil.copyProperties(dto, Furniture.class);
        boolean success = save(furniture);
        if (!success) {
            throw new BusinessException("添加家具失败，请联系系统管理人员！");
        }
        log.info("管理员添加家具: furnitureId={}, name={}", furniture.getId(), furniture.getFName());
        return Result.ok();
    }

    /**
     * 编辑家具信息
     * <p>
     * 根据传入的家具表单数据更新数据库中已有家具记录。如果家具存在关联的SKU，则从SKU汇总计算总库存和最低价格；
     * 否则直接使用表单中的库存和价格数据。更新成功后删除对应的Redis缓存，确保缓存一致性。
     * 该方法在事务中执行。
     * </p>
     *
     * @param dto 管理员提交的家具表单数据，必须包含家具ID
     * @return 修改成功时返回成功提示的 {@link Result} 对象；参数错误或家具不存在时返回失败信息
     * @throws BusinessException 当数据库更新失败时抛出，提示用户联系系统管理人员
     */
    @Override
    @Transactional
    public Result editFurniture(AdminFurnitureFormDTO dto) {
        if (dto == null || dto.getId() == null) {
            return Result.fail("请求参数错误");
        }
        if (furnitureMapper.selectCount(
                new LambdaQueryWrapper<Furniture>().eq(Furniture::getId, dto.getId())) == 0) {
            return Result.fail("家具不存在，无法修改");
        }
        LambdaUpdateWrapper<Furniture> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Furniture::getId, dto.getId());
        wrapper.set(Furniture::getFName, dto.getFName());
        wrapper.set(Furniture::getFIcon, dto.getFIcon());
        wrapper.set(Furniture::getTypeId, dto.getTypeId());
        wrapper.set(Furniture::getBrand, dto.getBrand());

        Long skuCount = skuMapper.selectCount(
                new LambdaQueryWrapper<Sku>()
                        .eq(Sku::getFurnitureId, dto.getId()));
        if (skuCount > 0) {
            int totalStock = skuMapper.sumStockByFurnitureId(dto.getId());
            BigDecimal minPrice = skuMapper.minPriceByFurnitureId(dto.getId());
            wrapper.set(Furniture::getStock, totalStock);
            if (minPrice != null && minPrice.compareTo(BigDecimal.ZERO) > 0) {
                wrapper.set(Furniture::getPrice, minPrice);
            }
        } else {
            wrapper.set(Furniture::getStock, dto.getStock());
            wrapper.set(Furniture::getPrice, dto.getPrice());
        }

        wrapper.set(Furniture::getIsRecommended, dto.getIsRecommended() != null ? dto.getIsRecommended() : 0);
        if (StrUtil.isNotBlank(dto.getIntro())) {
            wrapper.set(Furniture::getIntro, dto.getIntro());
        }
        if (dto.getImages() != null) {
            wrapper.set(Furniture::getImages, dto.getImages());
        }
        if (dto.getDescription() != null) {
            wrapper.set(Furniture::getDescription, dto.getDescription());
        }
        boolean success = furnitureMapper.update(null, wrapper) > 0;
        if (success) {
            evictFurnitureCacheAfterCommit(dto.getId());
            return Result.okMsg("修改成功");
        } else {
            throw new BusinessException("修改失败，请系统联系管理人员！");
        }
    }

    /**
     * 事务提交之后再失效家具详情缓存。
     * <p>
     * 若在事务提交前删除缓存，并发的用户请求会在缓存未命中时读到「尚未提交」的旧值并回写缓存，
     * 事务提交后缓存中便长期保留旧价格/旧库存（本项目的读缓存会由逻辑过期+物理 TTL 兜底，
     * 但回种的旧值仍会持续误导用户）。因此删除动作必须排在提交之后。
     * </p>
     *
     * @param furnitureId 家具 ID
     */
    private void evictFurnitureCacheAfterCommit(Long furnitureId) {
        if (furnitureId == null) {
            return;
        }
        String key = RedisConstants.CACHE_FURNITURE_KEY + furnitureId;
        Runnable evict = () -> {
            try {
                stringRedisTemplate.delete(key);
            } catch (Exception e) {
                log.warn("提交后失效家具缓存失败: furnitureId={}", furnitureId, e);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict.run();
                }
            });
        } else {
            evict.run();
        }
    }

    /**
     * 删除家具
     * <p>
     * 根据家具ID删除家具记录。删除成功后会清理通知表中对该家具的引用（将goodsId置为null），
     * 并删除对应的Redis缓存。该方法在事务中执行。
     * </p>
     *
     * @param furnitureId 要删除的家具ID
     * @return 删除成功时返回成功提示的 {@link Result} 对象；删除失败时返回失败信息
     */
    @Override
    @Transactional
    public Result deleteFurniture(Long furnitureId) {
        int rows = furnitureMapper.deleteById(furnitureId);
        if (rows > 0) {
            // 级联清理该商品的收藏记录，避免收藏表残留脏数据
            favoriteMapper.delete(
                    new LambdaQueryWrapper<Favorite>()
                            .eq(Favorite::getFurnitureId, furnitureId));
            // 清理通知中的商品引用
            notificationMapper.update(null,
                    new LambdaUpdateWrapper<Notification>()
                            .set(Notification::getGoodsId, null)
                            .eq(Notification::getGoodsId, furnitureId));
            evictFurnitureCacheAfterCommit(furnitureId);
            log.info("管理员删除家具: furnitureId={}", furnitureId);
            return Result.ok();
        }
        return Result.fail("删除失败！");
    }

}
