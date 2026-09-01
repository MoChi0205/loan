/**
 * 渠道产品管理接口（C9 撤销审批 + 申请删除）。
 *
 * 契约对齐「小程序模块结论沉淀」C9：
 * - GET   /api/mini/product/list              我的产品（渠道视角，沙箱内仅本行产品）
 * - POST  /api/mini/product                   录入产品（进 DRAFT，可提交审批）
 * - PUT   /api/mini/product/{code}            编辑（DRAFT / REJECTED 可编辑重提）
 * - POST  /api/mini/product/{code}/submit     提交审批（DRAFT → PENDING）
 * - POST  /api/mini/product/{code}/revoke     撤销审批（PENDING → DRAFT，无需审批）
 * - POST  /api/mini/product/{code}/delete-apply   申请删除（OK → PENDING_DELETE，需我司终审）
 * - POST  /api/mini/product/{code}/delete-cancel  撤销删除（PENDING_DELETE → OK）
 *
 * 状态机：
 *   DRAFT ─submit→ PENDING ─通过→ OK ─delete-apply→ PENDING_DELETE ─通过→ DELETED(物理移除+留痕)
 *     ↑              │                                    │
 *     └──── revoke ──┘                        delete-cancel┘（或驳回 → OK）
 */
import { requestGet, requestPost, requestPut } from './request';

/** 产品状态枚举（与后端 PartnerProductStatus 对齐） */
export const PRODUCT_STATUS = {
  DRAFT: 'DRAFT',
  PENDING: 'PENDING',
  OK: 'OK',
  REJECTED: 'REJECTED',
  PENDING_DELETE: 'PENDING_DELETE',
};

/**
 * 我的产品列表（渠道仅见本行录入产品）。
 *
 * @returns {Promise<Array<{
 *   code, bankProductCode, productName, bankName, amountRange, rate,
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
 * @returns {Promise<{code:string, bankProductCode:string, cooperateUntil?:string, amountMin?:string, amountMax?:string, requirement?:any}>}
 */
export function getProductDetail(code) {
  return requestGet(`/api/mini/product/${code}`);
}

/**
 * 录入产品（保存为草稿）。
 *
 * @param {Object} payload
 * @param {string} payload.bankProductCode 银行产品编码
 * @param {string} [payload.cooperateUntil] 合作有效期至
 * @param {Object} [payload.requirement]   进件要求（结构化，后端解析校验）
 * @returns {Promise<{code:string}>}
 */
export function createProduct(payload) {
  return requestPost('/api/mini/product', payload);
}

/**
 * 编辑产品（草稿 / 已驳回可编辑重提）。
 *
 * @param {string} code 产品编码
 * @param {Object} payload 同 createProduct
 * @returns {Promise<Void>}
 */
export function updateProduct(code, payload) {
  return requestPut(`/api/mini/product/${code}`, payload);
}

/**
 * 提交审批（草稿 → 待审批），走运营/超管终审。
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
 * 申请删除（已上架 → 待删除），需我司运营/超管终审，
 * 审批通过后从全量库物理删除（操作留痕至审计日志）。
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
