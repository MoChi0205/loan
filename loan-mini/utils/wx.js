/**
 * 微信能力封装（uni 内置 API Promise 化，不引入 npm 依赖）。
 *
 * - isH5Env()：判断当前是否为 H5（浏览器）宿主环境
 * - wxLogin()：uni.login 取 wx code（P0-1 主通道凭证），H5 环境降级为 mock code
 * - uploadImage()：uni.uploadFile 封装（P0-3 认证材料上传预留）
 */
import { getToken } from '../api/request';

/**
 * H5 开发预览环境使用的固定 mock code。
 *
 * 后端开启 `wechat.mock=true` 时会跳过真实 jscode2session，
 * 直接按 code 哈希生成稳定的 mock openid，因此任意非空 code 均可完成登录链路。
 *
 * @type {string}
 */
export const H5_MOCK_CODE = 'h5_dev_mock_code';

/**
 * 判断当前运行环境是否为 H5（浏览器）。
 *
 * 说明：H5 宿主没有微信 JS-SDK，`uni.login({ provider: 'weixin' })` 会直接 fail/reject，
 * 因此登录链路必须降级。这里优先使用 uni-app 条件编译常量（编译期裁剪，零运行时开销），
 * 条件编译不可用时再回退到运行时宿主探测。
 *
 * @returns {boolean} true 表示当前是 H5/浏览器环境
 */
export function isH5Env() {
  // #ifdef H5
  return true;
  // #endif
  // #ifndef H5
  // 回退探测 1：uni 运行时平台标识（Vue3 版 uni-app 为 'web'，旧版为 'h5'）
  try {
    if (typeof uni !== 'undefined' && typeof uni.getSystemInfoSync === 'function') {
      const info = uni.getSystemInfoSync() || {};
      const uniPlatform = String(info.uniPlatform || '').toLowerCase();
      if (uniPlatform === 'web' || uniPlatform === 'h5') {
        return true;
      }
      const platform = String(info.platform || '').toLowerCase();
      if (platform === 'h5' || platform === 'browser') {
        return true;
      }
    }
  } catch (e) {
    /* getSystemInfoSync 不可用时继续走宿主探测 */
  }
  // 回退探测 2：浏览器宿主存在 window/document，小程序宿主不存在
  return typeof window !== 'undefined' && typeof window.document !== 'undefined';
  // #endif
}

/**
 * 获取 wx.login 临时凭证（code）。
 *
 * - 微信小程序：调用 uni.login 换取真实 code
 * - H5 浏览器预览：直接返回 {@link H5_MOCK_CODE}，配合后端 mock 开关打通登录链路
 *
 * @returns {Promise<string>} wx code
 */
export function wxLogin() {
  if (isH5Env()) {
    return Promise.resolve(H5_MOCK_CODE);
  }
  return new Promise((resolve, reject) => {
    uni.login({
      provider: 'weixin',
      success: (res) => {
        if (res.code) {
          resolve(res.code);
        } else {
          const err = new Error(res.errMsg || 'wx.login 未返回 code');
          err.stage = 'wxLogin';
          reject(err);
        }
      },
      fail: (err) => {
        const e = new Error(err.errMsg || '微信登录失败');
        e.stage = 'wxLogin';
        reject(e);
      },
    });
  });
}

/**
 * 上传图片/材料（认证页预留；后端响应按 Result<T> 解包）。
 *
 * @param {Object} options
 * @param {string} options.filePath  本地文件路径（uni.chooseImage 返回）
 * @param {string} options.url       上传接口地址（完整 URL 或相对路径）
 * @param {string} [options.name]    文件字段名，默认 file
 * @param {Object} [options.formData] 附加表单字段
 * @returns {Promise<any>} 成功 resolve 服务端 data
 */
export function uploadImage({ filePath, url, name = 'file', formData = {} }) {
  const token = getToken();
  const header = {
    'X-Client-Type': 'MINI_APP',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url,
      filePath,
      name,
      formData,
      header,
      success: (res) => {
        let body = null;
        try {
          body = typeof res.data === 'string' ? JSON.parse(res.data) : res.data;
        } catch (e) {
          reject(new Error('上传响应解析失败'));
          return;
        }
        if (res.statusCode === 200 && body && body.code === 0) {
          resolve(body.data);
        } else {
          reject(new Error((body && body.message) || `上传失败（${res.statusCode}）`));
        }
      },
      fail: (err) => {
        reject(new Error(err.errMsg || '上传失败'));
      },
    });
  });
}

/**
 * 选择并上传图片（组合封装，未接 OCR 场景预留）。
 *
 * @param {Object} options 同 uploadImage（filePath 由 chooseImage 自动获取）
 * @returns {Promise<any>}
 */
export function chooseAndUpload(options) {
  return new Promise((resolve, reject) => {
    uni.chooseImage({
      count: 1,
      sizeType: ['compressed'],
      success: (res) => {
        const filePath = res.tempFilePaths && res.tempFilePaths[0];
        if (!filePath) {
          reject(new Error('未选择图片'));
          return;
        }
        uploadImage({ ...options, filePath }).then(resolve).catch(reject);
      },
      fail: (err) => {
        reject(new Error(err.errMsg || '选择图片失败'));
      },
    });
  });
}
