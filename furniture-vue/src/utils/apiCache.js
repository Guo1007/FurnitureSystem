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
 */

const store = new Map(); // key -> { at: number, promise: Promise }

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

  const promise = fetcher().catch((err) => {
    // 失败不缓存：清掉本次记录，保证下次能重新发起
    const cur = store.get(key);
    if (cur && cur.promise === promise) store.delete(key);
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
