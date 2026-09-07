/**
 * 渠道产品管理接口（C9 撤销审批 + 申请删除）。
 *
 * 契约对齐「小程序模块结论沉淀」C9：
 * - GET   /api/mini/product/list              我的产品（渠道视角，沙箱内仅本行产品）
 * - POST  /api/mini/product                   录入产品（进 DRAFT，可提交审批）
 * - PUT   /api/mini/product/{code}            编辑（DRAFT / REJECTED 可编辑重提）
 * - POST  /api/mini/product/{code}/submit     提交审批（DRAFT → PENDING）
 * - POST  /api/mini/product/{code}/revoke     撤销审批（PENDING → DRAFT，无需审批）
 * - POST  /api/mini/product/{code}/delete-apply   申请删除（APPROVED → PENDING_DELETE，需我司终审）
 * - POST  /api/mini/product/{code}/delete-cancel  撤销删除（PENDING_DELETE → APPROVED）
 *
 * 状态机：
 *   DRAFT ─submit→ PENDING ─通过→ APPROVED ─delete-apply→ PENDING_DELETE ─通过→ OFFLINE(保留审批留痕)
 *     ↑              │                                    │
 *     └──── revoke ──┘                        delete-cancel┘（驳回后仍保持上架）
 */
import { requestGet, requestPost, requestPut } from './request';

/** 产品状态枚举（与后端 PartnerProductStatus 对齐） */
export const PRODUCT_STATUS = {
  DRAFT: 'DRAFT',
  PENDING: 'PENDING',
  APPROVED: 'APPROVED',
  REJECTED: 'REJECTED',
  PENDING_DELETE: 'PENDING_DELETE',
};

/**
 * 我的产品列表（渠道仅见本行录入产品）。
 *
 * @returns {Promise<Array<{
 *   code, productName, bankName, amountRange, rate,
 *   status, rejectReason, cooperateUntil, createdAt
 * }>>}
 */
export function myProducts() {
  return requestGet('/api/mini/product/list');
}

/**
 * 产品详情（编辑回填用）。
 *
 * @param {string} code 审批单号
 * @returns {Promise<{code:string, productName:string, customerGroup:string, cooperateUntil?:string, amountMin?:number, amountMax?:number, bizTermsJson?:string}>}
 */
export function getProductDetail(code) {
  return requestGet(`/api/mini/product/${code}`);
}

/**
 * 录入产品（保存为草稿）。
 *
 * @param {Object} payload
 * @param {string} payload.productName 产品名称
 * @param {'ENTERPRISE'|'PERSONAL'} payload.customerGroup 适用客群
 * @param {string} [payload.cooperateUntil] 合作有效期至
 * @param {number} [payload.amountMin] 额度下限（元）
 * @param {number} [payload.amountMax] 额度上限（元）
 * @param {string} [payload.bizTermsJson] 进件要求结构化 JSON
 * @returns {Promise<{code:string}>}
 */
export function createProduct(payload) {
  return requestPost('/api/mini/product', payload);
}

/**
 * 编辑产品（草稿 / 已驳回可编辑重提）。
 *
 * @param {string} code 产品编码
 * @param {Object} payload 同 createProduct；内部产品编码不向前端暴露或接收
 * @returns {Promise<Void>}
 */
export function updateProduct(code, payload) {
  return requestPut(`/api/mini/product/${code}`, payload);
}

/**
 * 提交审批（草稿 → 待审批），走平台终审。
 *
 * @param {string} code 产品编码
 * @returns {Promise<Void>}
 */
export function submitProduct(code) {
  return requestPost(`/api/mini/product/${code}/submit`, {});
}

/**
 * 撤销审批（待审批 → 草稿），无需审批，即时生效。
 *
 * @param {string} code 产品编码
 * @returns {Promise<Void>}
 */
export function revokeApproval(code) {
  return requestPost(`/api/mini/product/${code}/revoke`, {});
}

/**
 * 申请删除（已上架 → 待删除），需我司老板/超管终审，
 * 审批通过后合作库置为 OFFLINE，并永久保留审批记录。
 *
 * @param {string} code   产品编码
 * @param {string} reason 删除原因
 * @returns {Promise<Void>}
 */
export function applyDelete(code, reason) {
  return requestPost(`/api/mini/product/${code}/delete-apply`, { reason: reason || '' });
}

/**
 * 撤销删除申请（待删除 → 已上架）。
 *
 * @param {string} code 产品编码
 * @returns {Promise<Void>}
 */
export function cancelDelete(code) {
  return requestPost(`/api/mini/product/${code}/delete-cancel`, {});
}
