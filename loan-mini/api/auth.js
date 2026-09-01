/**
 * 小程序认证接口（P0-1 / P0-3）。
 *
 * 契约对齐 design-three-terminal.md §3.3：
 * - POST /api/mini/auth/login      微信登录（wx.login code 换 token）
 * - GET  /api/mini/me              档案摘要（脱敏）
 * - POST /api/mini/auth/enterprise 企业认证
 * - POST /api/mini/auth/personal   个人认证
 */
import { requestGet, requestPost } from './request';

/**
 * 开发模式 CRM 登录（用于 H5/开发者工具 dev 角色切换）。
 * - crm-boss-001 → STAFF token（userType=STAFF）
 * - 与 Web 管理端共用 /api/auth/login 接口（context-path=/loan 由 request.js BASE_URL 处理）
 *
 * @param {string} crmUserId
 * @returns {Promise<{token:string, user:Object}>}
 */
export function loginByCrm(crmUserId) {
  return requestPost('/api/auth/login', { crmUserId });
}

/**
 * 微信登录（P0-1 主通道）：uni.login 取得 code 后换 token。
 *
 * @param {string} code wx.login 临时凭证
 * @param {Object} [extra]
 * @param {string} [extra.nickname]   昵称（可选）
 * @param {string} [extra.avatar]     头像（可选）
 * @param {string} [extra.inviteCode] 邀请码（可选，登录时自动绑定引荐关系）
 * @returns {Promise<{token:string, expireMillis:number, user:Object}>}
 */
export function loginByWx(code, { nickname, avatar, inviteCode } = {}) {
  return requestPost('/api/mini/auth/login', {
    code,
    ...(nickname ? { nickname } : {}),
    ...(avatar ? { avatar } : {}),
    ...(inviteCode ? { inviteCode } : {}),
  });
}

/**
 * 手机号验证码登录（兼容通道）：仅后端开启 mini.auth.phone-compat 时可用，
 * 一般用于管理端手动建档场景，小程序侧默认不暴露。
 *
 * @param {string} phone 手机号
 * @param {string} smsCode 短信验证码
 * @param {string} [inviteCode] 邀请码（可选）
 * @returns {Promise<{token:string, expireMillis:number, user:Object}>}
 */
export function loginByCode(phone, smsCode, inviteCode) {
  return requestPost('/api/mini/auth/login', {
    phone,
    code: smsCode,
    ...(inviteCode ? { inviteCode } : {}),
  });
}

/**
 * 我的资料摘要（脱敏：手机号/信用代码等由后端掩码后返回）。
 *
 * @returns {Promise<Object>}
 */
export function me() {
  return requestGet('/api/mini/me');
}

/**
 * 企业认证（营业执照信息，认证后 customerGroup 锁定 ENTERPRISE）。
 *
 * @param {Object} params
 * @param {string} params.creditCode    统一社会信用代码
 * @param {string} params.enterpriseName 企业名称
 * @param {string} [params.contactName] 联系人姓名（可选）
 * @returns {Promise<Object>}
 */
export function enterpriseAuth({ creditCode, enterpriseName, contactName } = {}) {
  return requestPost('/api/mini/auth/enterprise', {
    creditCode,
    enterpriseName,
    ...(contactName ? { contactName } : {}),
  });
}

/**
 * 个人认证（Mock 三要素，落 t_personal_profile / t_personal_auth）。
 *
 * @param {Object} params
 * @param {string} params.realName           真实姓名
 * @param {string} params.idCardNo           身份证号
 * @param {string} params.city               城市
 * @param {number} params.age                年龄
 * @param {number} [params.houseFlag]        房产 0/1
 * @param {number} [params.carFlag]          车辆 0/1
 * @param {number} [params.socialSecurityFlag] 社保 0/1
 * @param {number} [params.fundFlag]         公积金 0/1
 * @returns {Promise<Object>}
 */
export function personalAuth({
  realName, idCardNo, city, age,
  houseFlag, carFlag, socialSecurityFlag, fundFlag,
} = {}) {
  return requestPost('/api/mini/auth/personal', {
    realName,
    idCardNo,
    city,
    age: Number(age),
    houseFlag: Number(houseFlag) || 0,
    carFlag: Number(carFlag) || 0,
    socialSecurityFlag: Number(socialSecurityFlag) || 0,
    fundFlag: Number(fundFlag) || 0,
  });
}
