// furniture.js
import request from "./request";
import { cachedRequest, invalidateCache } from "@/utils/apiCache";

// 分类列表变动频率极低，且 AppHeader（布局，常驻）与 HomeView（首屏）会同时请求，
// 这里做 60s 短时缓存 + 并发合并，避免同一个接口一次首屏被打两遍。
const TYPE_LIST_KEY = "furniture_type:list";
const TYPE_LIST_TTL = 60 * 1000;

export const getFurnitureTypeList = () => {
  return cachedRequest(TYPE_LIST_KEY, TYPE_LIST_TTL, () =>
    request({
      url: "/furniture_type/list",
      method: "get",
    })
  );
};

/** 后台新增/修改/删除分类后调用，防止前端读到缓存里的旧分类 */
export const invalidateFurnitureTypeCache = () => {
  invalidateCache(TYPE_LIST_KEY);
};

export function getFurnitureByTypeId(params) {
  if (typeof params === "number" || typeof params === "string") {
    const typeId = params;
    const current = arguments[1] || 1;
    const size = arguments[2] || 10;
    return request({
      url: "/furniture/list",
      method: "get",
      params: { typeId, current, size },
    });
  }

  return request({
    url: "/furniture/list",
    method: "get",
    params,
  });
}

export const getFurnitureById = (id) => {
  return request({
    url: `/furniture/${id}`,
    method: "get",
  });
};

export const getFurnitureBrands = (typeId) => {
  return request({
    url: "/furniture/brands",
    method: "get",
    params: { typeId },
  });
};

/** 查询商品的规格+SKU（客户端展示用） */
export const getFurnitureSpecs = (id) => {
  return request({
    url: `/furniture/${id}/specs`,
    method: "get",
  });
};
