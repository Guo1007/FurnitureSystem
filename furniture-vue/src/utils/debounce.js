/**
 * 轻量防抖。项目此前没有任何 debounce/throttle 工具，这里补一个最小实现。
 *
 * 场景：选券弹窗里用户会连点勾选，而每次勾选都要向后端试算一次金额。
 * 不防抖就会连发一串请求，既浪费也会让界面数字来回跳。
 *
 * 返回的函数带一个 `cancel()`，供组件卸载时清掉未触发的定时器，
 * 避免定时器在组件销毁后才跑到、拿着已失效的引用去发请求。
 *
 * @param {Function} fn 要防抖的函数
 * @param {number} wait 等待毫秒数
 * @returns {Function & { cancel: () => void }}
 */
export function debounce(fn, wait = 250) {
  let timer = null;

  const wrapped = (...args) => {
    if (timer) clearTimeout(timer);
    timer = setTimeout(() => {
      timer = null;
      fn(...args);
    }, wait);
  };

  wrapped.cancel = () => {
    if (timer) {
      clearTimeout(timer);
      timer = null;
    }
  };

  return wrapped;
}
