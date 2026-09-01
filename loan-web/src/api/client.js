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
