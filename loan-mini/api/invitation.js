/**
 * 小程序邀请绑定接口（P0-2）。
 *
 * 契约对齐 design-three-terminal.md §3.3：
 * - POST /api/mini/invitation/bind   绑定邀请码 → {referrerType, referrerName}
 * - GET  /api/mini/invitation/mine   我的邀请码（幂等生成，7 天有效）
 * - GET  /api/mini/invitation/records 我的邀请记录（分页）
 */
import { requestGet, requestPost } from './request';

/**
 * 绑定邀请码（登录后可补绑，返回引荐人类型与昵称/姓名）。
 *
 * @param {string} inviteCode 邀请码
 * @returns {Promise<{referrerType:string, referrerName:string}>}
 */
export function bind(inviteCode, options = {}) {
  return requestPost('/api/mini/invitation/bind', { inviteCode }, options);
}

/**
 * 我的邀请码。
 *
 * @returns {Promise<string>}
 */
export function mine() {
  return requestGet('/api/mini/invitation/mine');
}

/**
 * 我的邀请记录（通过我的邀请码注册的客户，分页）。
 *
 * @param {number} [page] 页码，默认 1
 * @param {number} [size] 每页条数，默认 10
 * @returns {Promise<{page:number, size:number, total:number, records:Array}>}
 */
export function records(page = 1, size = 10) {
  return requestGet('/api/mini/invitation/records', { page, size });
}
