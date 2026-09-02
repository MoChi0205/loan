import request from '@/utils/request';

/**
 * 客户档案接口（对接 loan-service /api/admin/client，P0-6）。
 *
 * <p>GET 返回合并视图（基础信息 + 企业信息 + 个人档案 + 邀请链 + 审计字段），
 * 敏感字段（phone / creditCode / realName / idCardNo）后端已脱敏，前端仅做兜底脱敏展示。
 */
export function getClientDetail(clientCode) {
  return request({ url: `/api/admin/client/${clientCode}`, method: 'get' });
}

/** 档案编辑（基础信息 + 个人档案合并，含操作留痕） */
export function updateClientDetail(clientCode, data) {
  return request({ url: `/api/admin/client/${clientCode}`, method: 'put', data });
}

/** 未分配客户池：ownerStaffCode 为空的新客户自动进入。 */
export function pageUnassignedClients(params) {
  return request({ url: '/api/admin/client/unassigned/page', method: 'get', params });
}

/** 顾问申请认领未分配客户，审批通过后才建立归属。 */
export function claimUnassignedClient(clientCode) {
  return request({ url: `/api/admin/client/${clientCode}/claim`, method: 'post' });
}

/** 管理者直接指定归属（D39/C23）：立即落归属、无需审批。body 兼容 adviserStaffCode，新前端统一用 targetStaffCode。 */
export function assignClient(clientCode, targetStaffCode) {
  return request({
    url: `/api/admin/client/${clientCode}/assign`,
    method: 'post',
    data: { targetStaffCode },
  });
}

/** 管理端手动回收进公海（C26）：清空归属 + 置冷却，不删档案。 */
export function recycleClient(clientCode) {
  return request({
    url: `/api/admin/client/${clientCode}/recycle`,
    method: 'post',
  });
}
