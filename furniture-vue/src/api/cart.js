import request from "./request";

/**
 * 购物车接口封装。
 * <p>
 * 全部需要登录：未登录访问会返回 401，调用方应先判断登录态再发起请求，
 * 不要靠 401 来驱动「请先登录」的提示。
 */

/** 购物车列表，每项含实时价格/库存与是否可购买的标记 */
export const getCartList = () => request.get("/cart");

/**
 * 加入购物车；同一 SKU 已存在时服务端累加数量。
 *
 * @param {{ furnitureId: number, skuId?: number, quantity?: number }} data
 */
export const addCartItem = (data) => request.post("/cart", data);

/** 修改数量 */
export const updateCartItemQuantity = (id, quantity) =>
  request.put(`/cart/${id}/quantity`, null, { params: { quantity } });

/** 修改结算勾选状态 */
export const updateCartItemChecked = (id, checked) =>
  request.put(`/cart/${id}/checked`, null, { params: { checked } });

/** 删除单项 */
export const removeCartItem = (id) => request.delete(`/cart/${id}`);

/** 清空购物车 */
export const clearCart = () => request.delete("/cart");
