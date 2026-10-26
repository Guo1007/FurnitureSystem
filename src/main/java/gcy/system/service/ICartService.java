package gcy.system.service;

import gcy.system.entity.dto.CartAddDTO;
import gcy.system.entity.dto.Result;

/**
 * 购物车服务。
 *
 * @author 郭名城
 * @date 2026-10-23
 */
public interface ICartService {

    /** 查询当前用户购物车，含实时价格、库存与失效标记。 */
    Result list(Long userId);

    /** 加购；同一 SKU 已存在时累加数量。 */
    Result addItem(Long userId, CartAddDTO dto);

    /** 修改数量，数量需在 1 与实时库存之间。 */
    Result updateQuantity(Long userId, Long id, Integer quantity);

    /** 修改结算勾选状态。 */
    Result updateChecked(Long userId, Long id, Integer checked);

    /** 删除单项；仅能删自己的。 */
    Result removeItem(Long userId, Long id);

    /** 清空当前用户购物车。 */
    Result clear(Long userId);
}
