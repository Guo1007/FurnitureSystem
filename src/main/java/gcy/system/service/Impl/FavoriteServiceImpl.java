package gcy.system.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import gcy.system.entity.dto.Result;
import gcy.system.entity.pojo.Favorite;
import gcy.system.entity.vo.FavoriteVO;
import gcy.system.exception.BusinessException;
import gcy.system.mapper.FavoriteMapper;
import gcy.system.mapper.FurnitureMapper;
import gcy.system.service.IFavoriteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户收藏服务实现类。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl extends ServiceImpl<FavoriteMapper, Favorite> implements IFavoriteService {

    private final FavoriteMapper favoriteMapper;

    private final FurnitureMapper furnitureMapper;

    /**
     * 分页查询用户收藏列表，参数为 null 时默认第 1 页、每页 10 条。
     */
    @Override
    public Result getFavoritesByUserId(Long userId, Integer current, Integer size) {
        Page<FavoriteVO> page = new Page<>(current != null ? current : 1, size != null ? size : 10);
        Page<FavoriteVO> result = favoriteMapper.selectFavoritesWithFurniturePage(userId, page);
        return Result.ok(result);
    }

    /**
     * 查询用户是否已收藏指定家具。
     */
    @Override
    public Result checkFavorite(Long userId, Long furnitureId) {
        boolean exists = favoriteMapper.existsByUserIdAndFurnitureId(userId, furnitureId);
        return Result.ok(exists);
    }

    /**
     * 切换收藏状态：已收藏则取消并返回 false，未收藏则校验家具存在后收藏并返回 true。
     * 依赖数据库唯一索引防并发重复插入，捕获 DuplicateKeyException 后视为已收藏。
     */
    @Override
    @Transactional
    public Result toggleFavorite(Long userId, Long furnitureId) {
        LambdaQueryWrapper<Favorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Favorite::getUserId, userId)
                .eq(Favorite::getFurnitureId, furnitureId);
        Favorite existing = favoriteMapper.selectOne(wrapper);

        if (existing != null) {
            favoriteMapper.deleteById(existing.getId());
            return Result.ok(false);
        }
        if (furnitureMapper.selectById(furnitureId) == null) {
            throw new BusinessException("商品不存在或已下架");
        }
        Favorite fav = new Favorite();
        fav.setUserId(userId);
        fav.setFurnitureId(furnitureId);
        try {
            favoriteMapper.insert(fav);
            return Result.ok(true);
        } catch (DuplicateKeyException e) {
            // 并发点击：数据库唯一索引已阻止重复插入，直接当作已收藏
            log.debug("重复收藏被唯一索引拦截: userId={}, furnitureId={}", userId, furnitureId);
            return Result.ok(true);
        }
    }
}
