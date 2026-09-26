import request from '@/utils/request';
export function applyClientPhoneView(clientCode) { return request({ url: '/api/admin/lead/sensitive/client/apply-view', method: 'post', data: { clientCode } }); }
export function sensitiveQuota() { return request({ url: '/api/admin/lead/sensitive/quota', method: 'get', __loanSilent: true }); }
export function pendingSensitiveViewApprovals() { return request({ url: '/api/admin/lead/sensitive/approval/pending', method: 'get' }); }
export function auditSensitiveViewApproval(approvalNo, data) { return request({ url: `/api/admin/lead/sensitive/approval/${approvalNo}/audit`, method: 'post', data }); }
