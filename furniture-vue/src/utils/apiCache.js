// apiCache.js
/**
 * 轻量「同请求合并 + 短时缓存」工具。
 *
 * 解决场景：AppHeader 与 HomeView 在首屏同时挂载，都会请求 /furniture_type/list，
 * 导致同一个接口被打两次。分类这类数据变动频率极低，短时间缓存即可。
 *
 * 两条保证：
 * 1. 并发合并（in-flight dedup）：缓存未命中时并发发起的多个调用共享同一个 Promise，
 *    只会真正发出一次网络请求。
 * 2. 失败不缓存：请求失败时立即清空缓存，下一次调用会重新发起，不会把错误「缓存住」。
 *    「失败」包含两种形态——网络/HTTP 层 reject，以及**业务层失败**（见 isBusinessFailure）。
 */

const store = new Map(); // key -> { at: number, promise: Promise }

/**
 * 判定一个「已 resolve 的响应」是否其实是业务失败。
 *
 * 为什么需要这一步：`api/request.js` 的响应拦截器对**非 401 的业务错误**
 * （`code !== 200 && success !== true`）是 `return res` —— 即 resolve 而非 reject，
 * 让调用组件的 else 分支自己去展示 msg。所以只挂 `.catch` 拦不住业务错误，
 * 形如 `{code: 500}` 的响应会被当作成功结果缓存整个 TTL，
 * 期间切换路由、重新挂载组件都不会重试（模块级 Map 不随组件销毁），
 * 用户只能整页刷新。分类列表最典型：抖一次，导航栏就空 60 秒。
 *
 * blob 响应需排除：拦截器对 `responseType === "blob"` 直接 `return response.data`，
 * 拿到的不是统一响应体，没有 code 字段，靠 `"code" in res` 天然排除。
 */
const isBusinessFailure = (res) => {
  if (res == null || typeof res !== "object") return false;
  if (!("code" in res)) return false;
  return !(res.code === 200 || res.code === "200" || res.success === true);
};

/** 只清掉「仍是本次发起的那条」记录，避免误删并发期间被别人重建的缓存 */
const dropIfCurrent = (key, promise) => {
  const cur = store.get(key);
  if (cur && cur.promise === promise) store.delete(key);
};

/**
 * 发起一个可被合并/缓存的请求。
 * @param {string} key 缓存键，同一 key 视为同一请求
 * @param {number} ttl 缓存有效期（毫秒）
 * @param {() => Promise<any>} fetcher 真正发起请求的函数
 * @param {boolean} clone 是否对返回结果做浅拷贝，避免多个调用方共享同一对象引用
 * @returns {Promise<any>}
 */
export function cachedRequest(key, ttl, fetcher, clone = true) {
  const now = Date.now();
  const hit = store.get(key);

  if (hit && now - hit.at < ttl) {
    return clone ? hit.promise.then((res) => shallowCopy(res)) : hit.promise;
  }

  const promise = fetcher()
    .then((res) => {
      // 业务失败同样不缓存（拦截器是 resolve 返回的，只有 .catch 拦不到）
      if (isBusinessFailure(res)) dropIfCurrent(key, promise);
      return res;
    })
    .catch((err) => {
      // 失败不缓存：清掉本次记录，保证下次能重新发起
      dropIfCurrent(key, promise);
      return Promise.reject(err);
    });

  store.set(key, { at: now, promise });

  // 首个调用方不需要额外拷贝，直接返回（但为了行为一致，仍走一次拷贝）
  return clone ? promise.then((res) => shallowCopy(res)) : promise;
}

/**
 * 让某个 key 的缓存失效（后台增删改分类后调用，避免前端读到旧数据）。
 * @param {string} key
 */
export function invalidateCache(key) {
  store.delete(key);
}

/** 清空全部缓存 */
export function clearApiCache() {
  store.clear();
}

function shallowCopy(res) {
  if (res == null || typeof res !== "object") return res;
  const copy = { ...res };
  if (Array.isArray(copy.data)) copy.data = [...copy.data];
  return copy;
}
