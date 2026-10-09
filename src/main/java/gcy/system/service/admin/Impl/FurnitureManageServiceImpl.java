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
import gcy.system.utils.AfterCommit;
import gcy.system.utils.RedisConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 管理员家具管理服务实现类，编辑/删除后同步失效 Redis 家具缓存。
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
     * 分页查询家具列表，支持分类、名称（模糊）、库存状态与品牌筛选。
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
     * 新增家具；保存失败抛 {@link BusinessException}。
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
     * 编辑家具信息；有 SKU 时库存与价格以 SKU 汇总为准（库存求和、取最低价）覆盖表单值。
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
     * 事务提交后失效家具详情缓存。
     * <p>
     * 提交前删除，并发请求会在缓存未命中时读到未提交的旧值并回写，缓存长期保留旧价格/旧库存。
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
        AfterCommit.run(evict);
    }

    /**
     * 删除家具，级联清理收藏记录、置空通知中的商品引用并失效缓存。
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
