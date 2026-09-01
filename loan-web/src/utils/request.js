import axios from 'axios';
import { ElMessage } from 'element-plus';
import { KEYS, getStorage, removeStorage } from '@/utils/storage';

/**
 * Axios 统一请求封装（token / traceUuid / 错误处理，前端工具类）。
 *
 * <p>错误处理策略（401 治理）：
 * <ul>
 *   <li>业务 code=2000 或 HTTP 401 → 清凭据并跳登录（并发去重，只跳一次）；</li>
 *   <li>axios 主动取消的请求（路由切换/组件卸载）→ 静默，不打扰用户；</li>
 *   <li>请求超时 → 友好提示（节流，3 秒内同文案只弹一次）；</li>
 *   <li>其余业务/网络错误 → 统一节流弹窗，避免多请求并发失败连弹一串。</li>
 * </ul>
 */
const request = axios.create({
  baseURL: '/loan',
  timeout: 30000,
});

/** 错误提示节流：同一文案 3 秒内只弹一次（防并发失败连弹） */
const MESSAGE_THROTTLE_MS = 3000;
const lastShownAt = new Map();

function showThrottled(message, type = 'error') {
  const now = Date.now();
  const last = lastShownAt.get(message) || 0;
  if (now - last < MESSAGE_THROTTLE_MS) return;
  lastShownAt.set(message, now);
  if (type === 'warning') {
    ElMessage.warning(message);
  } else {
    ElMessage.error(message);
  }
}

/** 未登录统一处理：清凭据 → SPA 跳登录（并发 401 只跳一次），redirect 带回原页面 */
function redirectToLogin() {
  if (window.__loan_login_redirecting__) return;
  window.__loan_login_redirecting__ = true;
  removeStorage(KEYS.TOKEN);
  removeStorage(KEYS.USER);
  showThrottled('登录已过期，请重新登录');
  import('@/router').then(({ default: router }) => {
    router
      .push({ path: '/login', query: { redirect: window.location.pathname + window.location.search } })
      .finally(() => {
        window.__loan_login_redirecting__ = false;
      });
  });
}

// 请求拦截：注入 token / 端标识（X-Client-Type）与 traceUuid
request.interceptors.request.use(
  (config) => {
    const token = getStorage(KEYS.TOKEN);
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    // 端标识：网关按 X-Client-Type 校验接口可用端（WEB / MINI_APP）
    config.headers['X-Client-Type'] = 'WEB';
    return config;
  },
  (error) => Promise.reject(error),
);

// 响应拦截：只看 code，非 0 统一提示；401/2000 清 token 走 SPA 跳转（避免与路由守卫互相踢皮球死循环）
request.interceptors.response.use(
  (response) => {
    const res = response.data;
    if (res && res.code !== 0) {
      if (res.code === 2000) {
        redirectToLogin();
        return Promise.reject(new Error(res.message || '未登录或会话已过期'));
      }
      showThrottled(res.message || '请求失败');
      return Promise.reject(new Error(res.message));
    }
    return res;
  },
  (error) => {
    // 1) 路由切换/组件卸载时主动取消的请求：静默，不弹错
    if (axios.isCancel(error)) {
      return Promise.reject(error);
    }
    // 2) HTTP 401（网关或后端强校验返回）：与业务 2000 同路处理
    if (error.response && error.response.status === 401) {
      redirectToLogin();
      return Promise.reject(error);
    }
    // 2.5) HTTP 403（无权限）：提示并跳 /403（Layout 内子路由，保留侧栏，T2；避免页内空表+toast 循环）
    if (error.response && error.response.status === 403) {
      showThrottled(error.response?.data?.message || '当前角色无权执行该操作', 'warning');
      import('@/router').then(({ default: router }) => {
        if (router.currentRoute.value.path !== '/403') {
          router.push({ path: '/403', query: { from: router.currentRoute.value.fullPath } });
        }
      });
      return Promise.reject(error);
    }
    // 3) 超时：友好提示（节流）
    if (error.code === 'ECONNABORTED' || /timeout/i.test(error.message || '')) {
      showThrottled('请求超时，请稍后重试', 'warning');
      return Promise.reject(error);
    }
    // 4) 其余网络/服务端错误：后端有 message 用后端，否则网络异常（节流）
    const serverMsg = error.response?.data?.message;
    showThrottled(serverMsg || '网络异常，请稍后重试');
    return Promise.reject(error);
  },
);

export default request;
