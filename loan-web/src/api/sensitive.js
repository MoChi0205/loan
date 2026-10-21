import request from '@/utils/request';
export function applyClientPhoneView(clientCode) { return request({ url: '/api/admin/lead/sensitive/client/apply-view', method: 'post', data: { clientCode } }); }
export function applyLeadPhoneView(leadNo) { return request({ url: '/api/admin/lead/sensitive/apply-view', method: 'post', data: { leadNo } }); }
export function sensitiveQuota() { return request({ url: '/api/admin/lead/sensitive/quota', method: 'get', __loanSilent: true }); }
export function sensitiveLimitConfig() { return request({ url: '/api/admin/lead/sensitive/limit-config', method: 'get' }); }
export function saveSensitiveLimitConfig(dailyLimit) { return request({ url: '/api/admin/lead/sensitive/limit-config', method: 'post', data: { dailyLimit } }); }
export function pendingSensitiveViewApprovals(params = {}) { return request({ url: '/api/admin/lead/sensitive/approval/pending', method: 'get', params }); }
export function auditSensitiveViewApproval(approvalNo, data) { return request({ url: `/api/admin/lead/sensitive/approval/${approvalNo}/audit`, method: 'post', data }); }
