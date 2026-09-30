/**
 * 优惠券抵扣估算工具。
 *
 * 这里的算法与后端 OrderServiceImpl#calcCouponsDiscount / #resolveCouponBase
 * 保持一一对应，供「选券弹窗」与「购物车页/抽屉」共用，避免三处各写一份导致
 * 前端展示金额与后端实际扣减对不上。后端仍是最终裁决方。
 */

/** 金额保留两位，规避浮点累积误差 */
const money = (v) => Math.round((Number(v) || 0) * 100) / 100;

/** 是否为「指定分类券」（对齐后端 resolveCouponBase 的判定） */
export const isScoped = (c) => Number(c?.scope) === 1 && c?.typeId != null;

/**
 * 读取某分类的参与结算小计。
 * 购物车为本地存储，历史数据可能没有 typeId，此时回退为整单金额，
 * 避免把分类券全部误判为不可用。
 *
 * @param {Object|null} subTotals 分类小计 { [typeId]: 金额 }
 * @param {number} totalAmount 整单金额
 * @param {boolean} unknown 是否存在分类未知的购物车项
 */
export function subTotalOf(typeId, subTotals, totalAmount, unknown = false) {
  if (typeId == null) return totalAmount;
  const m = subTotals || {};
  const v = m[String(typeId)] ?? m[typeId];
  if (v == null) return unknown ? totalAmount : 0;
  return Number(v) || 0;
}

/** 该券的适用基数：分类券取分类小计，全场券取整单金额 */
export function couponBase(c, totalAmount, subTotals, unknown = false) {
  return isScoped(c)
    ? subTotalOf(c.typeId, subTotals, totalAmount, unknown)
    : totalAmount;
}

/** 单张券在给定基数上的抵扣（对齐后端 calcCouponDiscount） */
export function discountOf(c, base) {
  let d = 0;
  if (Number(c?.type) === 2 && c?.discount) {
    d = base * (1 - Number(c.discount));
    if (c.capAmount && d > Number(c.capAmount)) d = Number(c.capAmount);
  } else if (c?.amount) {
    d = Number(c.amount);
  }
  return money(Math.min(Math.max(0, d), Math.max(0, base)));
}

/**
 * 组合抵扣（对齐后端 calcCouponsDiscount）。
 * 分类券只扣该分类剩余额度，全场券扣整单剩余额度，两个池各自递减；
 * 最后受「不超过商品总额」与「总抵扣比例上限」两道封顶。
 *
 * @param {Array} coupons 已选券列表（顺序影响结果，与后端一致按传入顺序）
 * @param {number} totalAmount 商品总额
 * @param {Object} subTotals 分类小计
 * @param {number} maxRatio 总抵扣上限比例（0~1）
 * @param {boolean} unknown 是否存在分类未知的购物车项
 */
export function calcTotalDiscount(
  coupons,
  totalAmount,
  subTotals,
  maxRatio = 0.8,
  unknown = false,
) {
  const list = Array.isArray(coupons) ? coupons.filter(Boolean) : [];
  if (!list.length) return 0;

  const remainByType = {};
  for (const c of list) {
    if (isScoped(c)) {
      remainByType[String(c.typeId)] = subTotalOf(
        c.typeId,
        subTotals,
        totalAmount,
        unknown,
      );
    }
  }

  let remainTotal = totalAmount;
  let total = 0;
  for (const c of list) {
    const key = String(c.typeId);
    const base = isScoped(c) ? remainByType[key] ?? 0 : remainTotal;
    const d = discountOf(c, base);
    total += d;
    if (isScoped(c)) remainByType[key] = Math.max(0, base - d);
    remainTotal = Math.max(0, remainTotal - d);
  }

  total = Math.min(total, totalAmount);
  const ratio = Number(maxRatio);
  if (Number.isFinite(ratio) && ratio > 0 && ratio <= 1) {
    const cap = money(totalAmount * ratio);
    if (total > cap) total = cap;
  }
  return money(Math.max(0, total));
}

/**
 * 组合内顺序对结果有影响，枚举排列取抵扣最大的顺序。
 *
 * 典型例子（总额 200，满减 100 券 + 8 折券）：
 *   满减→折扣 = 100 + 100×0.2 = 120
 *   折扣→满减 = 200×0.2 + 100 = 140
 * 折扣券对基数敏感，作用在大基数上收益更高，所以顺序必须参与择优。
 *
 * 最多 5 张（120 种排列），开销可忽略；超过 5 张直接返回原顺序，避免阶乘爆炸。
 */
function bestPermutation(list, calc) {
  if (!Array.isArray(list) || list.length < 2 || list.length > 5) return list;
  let best = list;
  let bestVal = calc(list);
  const walk = (rest, cur) => {
    if (!rest.length) {
      const v = calc(cur);
      if (v > bestVal) {
        bestVal = v;
        best = cur;
      }
      return;
    }
    for (let i = 0; i < rest.length; i++) {
      walk([...rest.slice(0, i), ...rest.slice(i + 1)], [...cur, rest[i]]);
    }
  };
  walk(list, []);
  return best;
}

/**
 * 求解「最优券组合」，返回选中的券对象数组。
 *
 * 「不可叠加券与任何其它券互斥」这一约束决定了合法组合只有两种形态：
 *   ① 单独使用一张不可叠加券
 *   ② 使用若干张可叠加券（张数 ≤ maxStackCount）
 * 两种形态各求最优再比较，即可覆盖全部合法组合。
 *
 * 算法是**带排序启发的贪心 + 小规模排列择优**，不是穷举：先按「折扣券优先、
 * 同类按单张抵扣降序」排序，逐个贪心加入（被比例封顶吃掉的直接放弃），
 * 最后对选中的组合做排列择优以修正顺序差异。
 * 券数量少且张数上限小，这一步已经能拿到最优解；后端下单时仍会重新裁决。
 *
 * @returns {Array} 选中的券对象数组（可能为空，表示没有能带来优惠的券）
 */
export function pickBestCoupons({
  coupons = [],
  totalAmount = 0,
  subTotals = {},
  maxStackCount = 3,
  maxRatio = 0.8,
  unknown = false,
} = {}) {
  const list = Array.isArray(coupons) ? coupons.filter(Boolean) : [];
  if (!list.length || totalAmount <= 0) return [];

  const calc = (arr) =>
    calcTotalDiscount(arr, totalAmount, subTotals, maxRatio, unknown);
  const usable = list.filter(
    (c) => !couponReason(c, totalAmount, subTotals, unknown),
  );
  const stackableOf = (c) => Number(c?.stackable) === 1;
  const singleOf = (c) =>
    discountOf(c, couponBase(c, totalAmount, subTotals, unknown));

  let best = [];
  let bestVal = 0;

  // 形态①：单独使用一张不可叠加券
  for (const c of usable) {
    if (stackableOf(c)) continue;
    const v = calc([c]);
    if (v > bestVal) {
      bestVal = v;
      best = [c];
    }
  }

  // 形态②：可叠加券组合
  // 排序：折扣券优先（折扣应作用于尽可能大的基数），同类按单张抵扣降序
  const ranked = usable.filter(stackableOf).sort((a, b) => {
    const da = Number(a.type) === 2 ? 0 : 1;
    const db = Number(b.type) === 2 ? 0 : 1;
    if (da !== db) return da - db;
    return singleOf(b) - singleOf(a);
  });
  let picked = [];
  for (const c of ranked) {
    if (picked.length >= maxStackCount) break;
    const next = [...picked, c];
    if (calc(next) > calc(picked)) picked = next;
  }
  picked = bestPermutation(picked, calc);
  const v = calc(picked);
  if (v > bestVal) {
    bestVal = v;
    best = picked;
  }

  return best;
}

/**
 * 判断单张券是否可用，返回不可用原因（可用则返回空串）。
 *
 * @param {Object} c 券对象（UserCouponVO）
 * @param {number} totalAmount 商品总额
 * @param {Object} subTotals 分类小计
 * @param {boolean} unknown 是否存在分类未知的购物车项
 * @param {number} now 当前时间戳
 */
export function couponReason(
  c,
  totalAmount,
  subTotals,
  unknown = false,
  now = Date.now(),
) {
  if (Number(c?.status) !== 0) return c?.statusText || "不可用";

  // 到期时间解析不出来时不判过期，交给后端兜底，避免误杀可用券
  const exp = parseTime(c?.expireTime);
  if (exp && !Number.isNaN(exp.getTime()) && exp.getTime() < now) return "已过期";

  // 分类券：本单没有该分类商品时不可用（对齐后端 itemTypeIds 校验）
  if (isScoped(c) && !unknown && subTotalOf(c.typeId, subTotals, totalAmount, unknown) <= 0) {
    return "本单无该分类商品";
  }

  // 门槛按「适用基数」判断：分类券看分类小计，全场券看整单
  const basis = couponBase(c, totalAmount, subTotals, unknown);
  const threshold = Number(c?.minThreshold) || 0;
  if (threshold > basis) {
    return `差 ¥${formatMoney(Math.ceil(threshold - basis))} 可用`;
  }
  return "";
}

/** 从购物车项中汇总分类小计 */
export function buildSubTotals(items = []) {
  const map = {};
  let unknown = false;
  for (const it of items) {
    const amount = (Number(it?.price) || 0) * (Number(it?.quantity) || 0);
    const tid = it?.typeId ?? it?.type_id ?? null;
    if (tid == null) {
      unknown = true;
      continue;
    }
    const key = String(tid);
    map[key] = (map[key] || 0) + amount;
  }
  return { map, unknown };
}

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
const formatDay = (t) => {
  const d = parseTime(t);
  if (!d || Number.isNaN(d.getTime())) return "";
  return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, "0")}.${String(
    d.getDate(),
  ).padStart(2, "0")}`;
};

const formatMoney = (v) =>
  (Number(v) || 0).toLocaleString("zh-CN", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });

export { parseTime, formatDay };
