/**
 * 客户查重 / 归属分配接口（C2 归属流转 + C10 自动查重）。
 *
 * 契约对齐「小程序模块结论沉淀」C2 / C10：
 * - GET  /api/mini/client/search?keyword=  查重（企业名模糊 / 手机号精确 / 信用代码精确）
 * - POST /api/mini/client                  录入新客户（自动归属当前用户，C2 情形 A）
 * - POST /api/mini/client/{clientCode}/claim 申请分配（C2 情形 B：
 *       有归属人 → 自动归属无需审批；无归宿/公海 → 走上级或运营审批）
 * - GET  /api/mini/client/{clientCode}/claim-status 查询分配申请审批状态
 */
import { requestGet, requestPost } from './request';

/**
 * 客户查重。
 *
 * 后端按「企业名称（模糊）/ 手机号（精确）/ 统一社会信用代码（精确）」任一字段命中即返回。
 * 命中结果含 hasOwner，决定前端走「自动归属」还是「审批流转」分支。
 *
 * @param {string} keyword 关键词（≥2 字才发起请求）
 * @returns {Promise<Object|null>}
 *   {
 *     clientCode, entName, contactPhone, creditCode,
 *     ownerStaffCode, ownerStaffName, hasOwner
 *   } 未命中返回 null
 */
export function searchClient(keyword) {
  const kw = (keyword || '').trim();
  if (kw.length < 2) return Promise.resolve(null);
  // 查重失败必须由页面进入错误态，绝不能降级成“未命中”，否则会误建重复客户。
  return requestGet('/api/mini/client/search', { keyword: kw }, { showError: false });
}

/**
 * 录入新客户。归属结果由后端按当前员工角色返回，前端不得假定自动归属。
 *
 * @param {Object} payload
 * @param {string} payload.entName    企业名称（必填）
 * @param {string} [payload.contactName]  联系人
 * @param {string} [payload.contactPhone] 手机号
 * @param {string} [payload.creditCode]   统一社会信用代码（18 位）
 * @param {string} [payload.customerGroup] 客群：ENTERPRISE / PERSONAL
 * @returns {Promise<{clientCode:string, ownerStaffCode?:string, action:string, result?:string, approvalNo?:string}>}
 */
export function createClient(payload) {
  return requestPost('/api/mini/client', payload);
}

/**
 * 申请把已有客户分配给当前用户（C2 情形 B）。
 *
 * 后端按当前归属分流：
 * - 已归属本人 → 幂等返回 { result:'AUTO_CLAIMED' }
 * - 已归属他人或无归属 → 提交审批，返回 { result:'PENDING_APPROVAL', approvalNo }
 *
 * @param {string} clientCode 客户编号
 * @param {string} [reason]   申请理由
 * @returns {Promise<{result:'AUTO_CLAIMED'|'PENDING_APPROVAL', approvalNo?:string}>}
 */
export function claimClient(clientCode, reason) {
  return requestPost(`/api/mini/client/${clientCode}/claim`, { reason: reason || '' });
}

/**
 * 查询分配申请的审批状态（用于"无归宿需审批"分支的轮询/刷新）。
 *
 * @param {string} clientCode 客户编号
 * @returns {Promise<{status:'PENDING'|'APPROVED'|'REJECTED', rejectReason?:string}>}
 */
export function claimStatus(clientCode) {
  return requestGet(`/api/mini/client/${clientCode}/claim-status`, {}, { showError: false });
}

/* ==================== C19-B3：无归宿分配审批（运营/超管/老板） ==================== */

/**
 * 分配待审列表（运营/超管/老板审批中心入口）。
 *
 * @deprecated 改用 api/approval.js 的 pendingApprovals / auditApproval
 * @param {number} page 页码
 * @param {number} size 每页大小
 * @returns {Promise<{records:Array}>}
 */
export function pendingAllocations(page = 1, size = 10) {
  return requestGet('/api/mini/client/allocation-approvals/pending', { page, size });
}

/**
 * 通过分配审批：客户归属流转给申请人。
 *
 * @deprecated 改用 api/approval.js 的 pendingApprovals / auditApproval
 * @param {string} approvalNo 审批单号
 * @returns {Promise<{status:'APPROVED'}>}
 */
export function approveAllocation(approvalNo) {
  return requestPost(`/api/mini/client/allocation-approvals/${approvalNo}/approve`, {});
}

/**
 * 驳回分配审批（驳回意见必填）。
 *
 * @deprecated 改用 api/approval.js 的 pendingApprovals / auditApproval
 * @param {string} approvalNo 审批单号
 * @param {string} opinion    驳回意见
 * @returns {Promise<{status:'REJECTED'}>}
 */
export function rejectAllocation(approvalNo, opinion) {
  return requestPost(`/api/mini/client/allocation-approvals/${approvalNo}/reject`, { opinion });
}
