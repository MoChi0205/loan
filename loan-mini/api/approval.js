/**
 * 审批中心统一接口（T5 前端）。
 *
 * 契约对齐后端 MiniApprovalController：
 * - GET /api/mini/approval/counts                           各类型待审数 { PRODUCT, DOWNLOAD, ALLOCATION, TOTAL }
 * - GET /api/mini/approval/pending?type=&page=&size=        统一待审列表（每条记录带 type 字段）
 * - POST /api/mini/approval/{type}/{approvalNo}/audit       审核（通过 / 驳回）
 *
 * 说明：
 * - 白名单当前仅开放 ALLOCATION，故 PRODUCT / DOWNLOAD 实际计数恒为 0。
 * - type=ALL 为「概览」：后端返回 paginationHint="SEGMENTED"，前端仅取前 20 条、不做深翻页。
 */
import { requestGet, requestPost } from './request';

/**
 * 各类型待审数量。
 *
 * @returns {Promise<{PRODUCT:number, DOWNLOAD:number, ALLOCATION:number, TOTAL:number}>}
 *   PRODUCT/DOWNLOAD/ALLOCATION 各自待审数；TOTAL 为三者合计（用于「全部」角标）
 */
export function approvalCounts() {
  return requestGet('/api/mini/approval/counts');
}

/**
 * 统一待审列表。
 *
 * @param {('ALL'|'PRODUCT'|'DOWNLOAD'|'ALLOCATION')} [type='ALLOCATION'] 审批类型，默认 ALLOCATION（当前唯一开放）
 * @param {number} [page=1] 页码（ALL 仅请求 page=1）
 * @param {number} [size=20] 每页大小
 * @returns {Promise<{page:number, size:number, total:number, records:Array, paginationHint:string}>}
 *   records 中每条记录自带 type 字段（PRODUCT / DOWNLOAD / ALLOCATION）
 */
export function pendingApprovals(type = 'ALLOCATION', page = 1, size = 20) {
  return requestGet('/api/mini/approval/pending', { type, page, size });
}

/**
 * 审核一条审批（通过或驳回）。
 *
 * @param {string} type 审批类型（PRODUCT / DOWNLOAD / ALLOCATION）
 * @param {string} approvalNo 审批单号
 * @param {boolean} approve true=通过，false=驳回
 * @param {string} [opinion] 驳回意见（approve=false 时必填，由调用方校验非空）
 * @returns {Promise<any>} 后端审核结果
 */
export function auditApproval(type, approvalNo, approve, opinion) {
  return requestPost(`/api/mini/approval/${type}/${approvalNo}/audit`, { approve, opinion });
}
