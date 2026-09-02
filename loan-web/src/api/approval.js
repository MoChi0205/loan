import request from '@/utils/request';

/**
 * 审批接口（对接 loan-service /api/admin/approval）。
 */

/** 产品审核 */
export function pageProductApprovals(params) {
  return request({ url: '/api/admin/approval/product/page', method: 'get', params });
}
export function productApprovalDetail(approvalNo) {
  return request({ url: `/api/admin/approval/product/${approvalNo}`, method: 'get' });
}
export function auditProductApproval(approvalNo, data) {
  return request({ url: `/api/admin/approval/product/${approvalNo}/audit`, method: 'post', data });
}

/** 附件下载审批 */
export function applyDownload(data) {
  return request({ url: '/api/admin/approval/download/apply', method: 'post', data });
}
export function pageDownloadApprovals(params) {
  return request({ url: '/api/admin/approval/download/page', method: 'get', params });
}
export function auditDownloadApproval(approvalNo, data) {
  return request({ url: `/api/admin/approval/download/${approvalNo}/audit`, method: 'post', data });
}
export function voidDownloadApproval(approvalNo) {
  return request({ url: `/api/admin/approval/download/${approvalNo}/void`, method: 'post' });
}

/** 客户分配审批（D39/C24：OPERATOR/SUPER_ADMIN/SUPER/BOSS 及团队管理者 DM 可见，DM 仅本团队） */
export function pageAllocationApprovals(params) {
  return request({ url: '/api/admin/approval/allocation/pending', method: 'get', params });
}
export function auditAllocationApproval(approvalNo, data) {
  // 后端按动作路由到 /approve 或 /reject；通过忽略 body，驳回需 opinion
  const action = data && data.approve ? 'approve' : 'reject';
  return request({ url: `/api/admin/approval/allocation/${approvalNo}/${action}`, method: 'post', data });
}
