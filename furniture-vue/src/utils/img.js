const IMAGE_BASE = "https://gmc-1007.oss-cn-beijing.aliyuncs.com";

/** 商品图兜底占位图（public/images 下真实存在，别删） */
export const DEFAULT_FURNITURE_IMG = "/images/default-furniture.png";

/**
 * 构造可用的图片 URL
 * @param {string} path  - 图片路径（可能是相对路径或完整 URL）
 * @param {string} fallback - 当 path 为空时的默认图
 * @returns {string} 完整的可访问图片 URL
 */
export function imgUrl(path, fallback = "") {
  if (!path) return fallback;
  if (path.startsWith("http")) return path;
  if (path.startsWith("/uploads/")) {
    path = path.replace("/uploads", "");
  }
  return IMAGE_BASE + path;
}

/**
 * `<img>` 加载失败时兜底到占位图。
 * <p>
 * 必须先判断当前 src 是否已经是兜底图：重复给 src 赋同一个值，浏览器也会重新发起
 * 一次请求；一旦兜底图本身也取不到，"赋值 → 再失败 → 再赋值"就会变成无限刷请求。
 * （dev 环境下不存在的路径会被 Vite 回落成 index.html，表现为一直 304）
 *
 * @param {Event} e img 的 error 事件
 */
export function handleImgError(e) {
  const target = e && e.target;
  if (!target || (target.src || "").endsWith(DEFAULT_FURNITURE_IMG)) return;
  target.src = DEFAULT_FURNITURE_IMG;
}
