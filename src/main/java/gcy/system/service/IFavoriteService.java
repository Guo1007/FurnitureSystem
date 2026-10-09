package gcy.system.service;

import gcy.system.entity.dto.Result;

/**
 * 用户收藏服务。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
public interface IFavoriteService {

    /**
     * 分页查询指定用户的收藏列表。
     */
    Result getFavoritesByUserId(Long userId, Integer current, Integer size);

    /**
     * 查询用户是否已收藏某个家具。
     */
    Result checkFavorite(Long userId, Long furnitureId);

    /**
     * 切换收藏状态：未收藏则收藏，已收藏则取消。
     */
    Result toggleFavorite(Long userId, Long furnitureId);
}
