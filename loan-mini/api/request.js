/**
 * 小程序统一请求封装（uni.request Promise 化 + 拦截器）。
 *
 * 约定（对齐 design-three-terminal.md §7.3 / §7.4）：
 * - 请求头固定携带 `X-Client-Type: MINI_APP`，登录后自动注入 `Authorization: Bearer <token>`
 * - 响应统一 `Result<T> { code, message, data, traceUuid }`：code=0 成功 → 仅返回 data
 * - code=2000（未登录）/ HTTP 401 → 清 token 并跳转落地页（防重入）
 *
 * baseURL 说明（2026-08-28 三端本地联调）：
 * - H5：留空走 vite devServer 代理 (/api/... → 网关 http://localhost:8088/loan/api/...)
 * - 微信开发者工具/真机：必须为绝对 URL（wx.request 不支持相对路径）
 *   微信开发者工具 → 详情 → 本地设置 → 勾选"不校验合法域名"才能用 http://localhost
 * - 生产环境：替换为配置了 https 合法域名的地址
 */
let BASE_URL = '';
// #ifndef H5
BASE_URL = 'http://localhost:8088/loan';
// #endif

/** 登录令牌在 storage 中的 key（与 store/user.js 共用） */
const TOKEN_KEY = 'loan_token';

/** 开发模式模拟角色在 storage 中的 key（配合后端 dev 开关使用） */
const DEV_ROLE_KEY = 'loan_dev_role';

/** 落地页路径 */
const LANDING_PAGE = '/pages/index/index';

/** 防重复跳转标记 */
let redirecting = false;

/**
 * 读取本地 token。
 *
 * @returns {string} token 或空串
 */
export function getToken() {
  try {
    return uni.getStorageSync(TOKEN_KEY) || '';
  } catch (e) {
    return '';
  }
}

/**
 * 清除本地 token（登录失效时调用）。
 */
export function clearToken() {
  try {
    uni.removeStorageSync(TOKEN_KEY);
  } catch (e) {
    /* storage 异常忽略 */
  }
}

/**
 * 未登录兜底：清 token 后重定向到落地页。
 */
function redirectToLanding() {
  if (redirecting) return;
  redirecting = true;
  uni.reLaunch({
    url: LANDING_PAGE,
    complete() {
      redirecting = false;
    },
  });
}

/**
 * 拼接请求 URL。
 *
 * @param {string} path 接口路径（/api/...）
 * @returns {string} 完整 URL
 */
function buildUrl(path) {
  if (/^https?:\/\//.test(path)) {
    return path;
  }
  return `${BASE_URL}${path}`;
}

/**
 * 统一请求（Promise 封装）。
 *
 * @param {Object} options
 * @param {string} options.url        接口路径
 * @param {string} [options.method]   请求方法，默认 GET
 * @param {Object} [options.data]     请求体 / 查询参数
 * @param {Object} [options.header]   额外请求头
 * @param {boolean} [options.showError] 失败是否弹 toast，默认 true
 * @param {number} [options.timeout]  超时毫秒，默认 15000
 * @returns {Promise<any>} 成功 resolve Result.data，失败 reject Error
 */
/**
 * 读取开发模式下选定的模拟角色（用于多身份菜单/权限调试）。
 *
 * 说明：仅当后端开启 `loan.dev.role-override.enabled` 时该头才会生效，
 * 生产构建请务必关闭该开关，避免越权风险。
 *
 * @returns {string} 角色编码，未设置返回空串
 */
export function getDevRole() {
  try {
    return uni.getStorageSync(DEV_ROLE_KEY) || '';
  } catch (e) {
    return '';
  }
}

function request({ url, method = 'GET', data = {}, header = {}, showError = true, timeout = 15000 }) {
  const token = getToken();
  const devRole = getDevRole();
  const headers = {
    'X-Client-Type': 'MINI_APP',
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    // 开发态角色模拟：后端需开启 dev 开关才生效
    ...(devRole ? { 'X-Dev-Role': devRole } : {}),
    ...header,
  };

  return new Promise((resolve, reject) => {
    uni.request({
      url: buildUrl(url),
      method,
      data,
      header: headers,
      timeout,
      success: (res) => {
        const { statusCode, data: body } = res;

        // HTTP 401：token 失效，清 token 回落地页
        if (statusCode === 401) {
          clearToken();
          redirectToLanding();
          const err = new Error('登录已过期，请重新登录');
          err.code = 2000;
          reject(err);
          return;
        }

        const bodyObj = body && typeof body === 'object' ? body : {};
        const code = bodyObj.code === undefined ? -1 : bodyObj.code;

        // 成功：code=0，解包 data
        if (statusCode === 200 && code === 0) {
          resolve(bodyObj.data);
          return;
        }

        // 业务未登录：清 token 回落地页
        if (code === 2000) {
          clearToken();
          redirectToLanding();
        }

        // 系统内部错误（code=5000）或 HTTP 5xx：用户友好提示，不暴露技术细节
        const isServerError = code === 5000 || statusCode >= 500;
        const friendlyMsg = isServerError
          ? '服务繁忙，请稍后再试'
          : (bodyObj.message || `请求失败（${statusCode}）`);

        const err = new Error(friendlyMsg);
        err.code = code;
        err.traceUuid = bodyObj.traceUuid;
        if (showError && code !== 2000) {
          // 系统异常：仅在 dev 环境 console 详细日志，UI 提示用友好文案
          if (isServerError) {
            console.error('[request] server error', statusCode, bodyObj);
          }
          uni.showToast({ title: friendlyMsg, icon: 'none', duration: 2500 });
        }
        reject(err);
      },
      fail: (err) => {
        const e = new Error(err.errMsg || '网络异常，请稍后重试');
        e.code = -1;
        if (showError) {
          uni.showToast({ title: e.message, icon: 'none', duration: 2500 });
        }
        reject(e);
      },
    });
  });
}

/**
 * GET 请求快捷方法。
 *
 * @param {string} url 接口路径
 * @param {Object} [data] 查询参数
 * @param {Object} [options] 额外配置（同 request）
 * @returns {Promise<any>}
 */
export function requestGet(url, data = {}, options = {}) {
  return request({ url, method: 'GET', data, ...options });
}

/**
 * POST 请求快捷方法。
 *
 * @param {string} url 接口路径
 * @param {Object} [data] 请求体
 * @param {Object} [options] 额外配置（同 request）
 * @returns {Promise<any>}
 */
export function requestPost(url, data = {}, options = {}) {
  return request({ url, method: 'POST', data, ...options });
}

/**
 * PUT 请求快捷方法（C9 产品编辑等）。
 *
 * @param {string} url 接口路径
 * @param {Object} [data] 请求体
 * @param {Object} [options] 额外配置（同 request）
 * @returns {Promise<any>}
 */
export function requestPut(url, data = {}, options = {}) {
  return request({ url, method: 'PUT', data, ...options });
}

export default request;
export { BASE_URL, TOKEN_KEY, DEV_ROLE_KEY };
