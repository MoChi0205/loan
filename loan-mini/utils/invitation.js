/**
 * 小程序分享引荐参数的统一入口。
 *
 * <p>页面只负责把启动参数交给本工具；本工具统一兼容分享链接 query 与
 * 小程序码 scene，并在登录完成后自动消费。邀请码只建立分享引荐关系，
 * 不代表客户的服务顾问归属。</p>
 */
import { bind } from '../api/invitation';

const PENDING_INVITE_KEY = 'loan_pending_invite_code';
const INVITE_KEYS = ['inviteCode', 'invite', 'ref'];
let consumingPromise = null;

/** 安全解码 URL 参数，非法编码时保留原值。 */
function safeDecode(value) {
  let result = String(value || '').trim();
  for (let i = 0; i < 2; i += 1) {
    try {
      const decoded = decodeURIComponent(result);
      if (decoded === result) break;
      result = decoded;
    } catch (e) {
      break;
    }
  }
  return result;
}

/**
 * 从普通 query 或二维码 scene 中解析邀请码。
 * scene 支持 `inviteCode=xxx`、`invite=xxx`、`ref=xxx` 或直接放短码。
 */
export function resolveInviteCode(options = {}) {
  const query = options.query && typeof options.query === 'object' ? options.query : options;
  for (let i = 0; i < INVITE_KEYS.length; i += 1) {
    const value = query && query[INVITE_KEYS[i]];
    if (value) return safeDecode(value).slice(0, 64);
  }

  const scene = safeDecode((query && query.scene) || options.scene || '');
  if (!scene) return '';
  const pairs = scene.split(/[&;]/);
  for (let i = 0; i < pairs.length; i += 1) {
    const separator = pairs[i].indexOf('=');
    if (separator < 0) continue;
    const key = pairs[i].slice(0, separator);
    if (INVITE_KEYS.indexOf(key) >= 0) {
      return safeDecode(pairs[i].slice(separator + 1)).slice(0, 64);
    }
  }
  return scene.indexOf('=') < 0 ? scene.slice(0, 64) : '';
}

/** 捕获启动参数并暂存，供登录后自动绑定。 */
export function captureInvitation(options) {
  const inviteCode = resolveInviteCode(options);
  if (!inviteCode) return '';
  try {
    uni.setStorageSync(PENDING_INVITE_KEY, inviteCode);
  } catch (e) {
    /* storage 异常时仍返回解析结果，登录页可直接消费 */
  }
  return inviteCode;
}

/** 读取待消费的邀请码。 */
export function getPendingInviteCode() {
  try {
    return uni.getStorageSync(PENDING_INVITE_KEY) || '';
  } catch (e) {
    return '';
  }
}

/** 清理已消费或已明确失效的邀请码。 */
export function clearPendingInviteCode() {
  try {
    uni.removeStorageSync(PENDING_INVITE_KEY);
  } catch (e) {
    /* storage 异常忽略 */
  }
}

/**
 * 登录后自动绑定待处理的邀请码。
 *
 * <p>网络错误保留待处理参数，等待下次 onShow 重试；业务上已使用、过期或不存在
 * 则清理，避免每次进入页面重复弹错。并发调用合并为同一个 Promise。</p>
 */
export function consumePendingInvitation(store, { silent = true } = {}) {
  const inviteCode = getPendingInviteCode();
  if (!inviteCode || !store || !store.token || store.role !== 'customer') {
    return Promise.resolve(null);
  }
  if (consumingPromise) return consumingPromise;

  consumingPromise = bind(inviteCode, { showError: !silent })
    .then(async (result) => {
      clearPendingInviteCode();
      if (result && result.referrerName) store.setReferrer(result.referrerName);
      await store.refreshProfile().catch(() => {});
      return result;
    })
    .catch((error) => {
      if (error && error.code !== -1 && error.code !== 2000) {
        clearPendingInviteCode();
      }
      return null;
    })
    .finally(() => {
      consumingPromise = null;
    });
  return consumingPromise;
}

/** 分享链接统一生成，二维码 scene 使用同一个 inviteCode 参数名。 */
export function buildInviteSharePath(inviteCode) {
  const code = encodeURIComponent(String(inviteCode || '').trim());
  return code ? `/pages/index/index?inviteCode=${code}` : '/pages/index/index';
}

export { PENDING_INVITE_KEY };
