/**
 * 优惠券**展示**工具。
 *
 * ⚠️ 这里已经没有、也不要再放任何抵扣算法。
 *
 * 抵扣与最优组合的计算已经整体搬到后端（`POST /order/estimate`，实现在
 * `OrderServiceImpl#estimate`），前端通过 `useCouponEstimate()` 取结果。
 * 这么做的原因是一个真实踩过的坑：算法原先在 JS 和 Java 里各写了一遍，
 * 两边必须逐字对齐、却没有任何东西保证这一点 —— 改了一边忘了另一边，
 * 用户就会看到「前端显示省 ¥240、下单只省 ¥60」。
 * 现在前端不再持有第二份实现，这类 bug 从结构上不可能再出现。
 *
 * 本文件只保留与金额无关的纯展示工具。
 */

/**
 * 是否为折扣券（type=2）。
 * 只用于交互展示（如「折扣券每单限 1 张」的提示逻辑），不参与任何金额计算。
 */
export const isDiscount = (c) => Number(c?.type) === 2;

/** 取数字，null/undefined/空串/非有限数一律回落默认值（Number(null)===0 会算错日期） */
const numOr = (v, dflt) => {
  if (v === null || v === undefined || v === "") return dflt;
  const n = Number(v);
  return Number.isFinite(n) ? n : dflt;
};

/**
 * 把后端返回的时间解析成 Date，解析不出来一律返回 null。
 *
 * 后端 LocalDateTime 目前序列化成 ISO 字符串（"2026-10-31T00:00:00"），
 * 但项目里其它页面还兼容着 Jackson 默认的数组格式，这里两种都兜住。
 *
 * 注意：**绝不能返回 Invalid Date** —— 它是 truthy，`if (!d)` 拦不住，
 * 一旦漏出去就会渲染成 "NaN.NaN.NaN"。
 */
const parseTime = (t) => {
  if (t === null || t === undefined || t === "") return null;

  // 已是 Date 对象
  if (t instanceof Date) return Number.isNaN(t.getTime()) ? null : t;

  // Jackson 默认的 LocalDateTime 数组：[2026,10,31,0,0,0]，尾部 0 可能被省略
  if (Array.isArray(t)) {
    // 注意：null/"" 经 Number() 会变成 0，必须显式排除，否则会算出 1899 年
    const [y, mo, d, h, mi, s] = t;
    const year = numOr(y, null);
    if (year === null || year <= 0) return null;
    return new Date(
      year,
      (numOr(mo, 1) || 1) - 1,
      numOr(d, 1) || 1,
      numOr(h, 0),
      numOr(mi, 0),
      Math.floor(numOr(s, 0)),
    );
  }

  // 毫秒/秒 时间戳
  if (typeof t === "number") {
    if (!Number.isFinite(t)) return null;
    return new Date(t < 1e12 ? t * 1000 : t);
  }

  if (typeof t === "string") {
    const s = t.trim();
    if (!s) return null;
    // 1) 原样交给 Date：ISO 8601（带 T）走这条
    let d = new Date(s);
    if (!Number.isNaN(d.getTime())) return d;
    // 2) 兼容 "2026-10-31 00:00:00"（部分浏览器不认空格分隔）
    d = new Date(s.replace(/-/g, "/"));
    if (!Number.isNaN(d.getTime())) return d;
    // 3) 兼容纯日期 "2026-10-31"
    d = new Date(s.slice(0, 10).replace(/-/g, "/"));
    return Number.isNaN(d.getTime()) ? null : d;
  }

  return null;
};

/** 统一的到期日展示：yyyy.MM.dd，解析不出来就不显示 */
export const formatDay = (t) => {
  const d = parseTime(t);
  if (!d || Number.isNaN(d.getTime())) return "";
  return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, "0")}.${String(
    d.getDate(),
  ).padStart(2, "0")}`;
};
