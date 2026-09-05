import request from '@/utils/request';
import { getStorageJSON, KEYS } from '@/utils/storage';
import { isChannelUser } from '@/utils/access';

const isChannel = () => isChannelUser(getStorageJSON(KEYS.USER, null));

/**
 * 线索接口（对接 loan-service /api/admin/lead）。
 */
export function pageLead(params) {
  return request({ url: isChannel() ? '/api/channel/lead/page' : '/api/admin/lead/page', method: 'get', params });
}

export function createLead(data) {
  return request({ url: isChannel() ? '/api/channel/lead' : '/api/admin/lead', method: 'post', data });
}

export function claimLead(leadNo) {
  return request({ url: '/api/admin/lead/claim', method: 'post', data: { leadNo } });
}

export function assignLead(leadNo, toStaffCode) {
  return request({ url: '/api/admin/lead/assign', method: 'post', data: { leadNo, toStaffCode } });
}

/** 批量认领（公海 → 我的线索），返回成功条数 */
export function batchClaimLead(leadNos) {
  return request({ url: '/api/admin/lead/batch-claim', method: 'post', data: { leadNos } });
}

/** 批量指派，返回成功条数 */
export function batchAssignLead(leadNos, toStaffCode) {
  return request({ url: '/api/admin/lead/batch-assign', method: 'post', data: { leadNos, toStaffCode } });
}

/** 批量删除（物理删除 + 审计留痕），返回成功条数 */
export function batchDeleteLead(leadNos) {
  return request({ url: '/api/admin/lead/batch-delete', method: 'post', data: { leadNos } });
}
