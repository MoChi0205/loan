import request from '@/utils/request';
import { getStorageJSON, KEYS } from '@/utils/storage';
import { isChannelUser } from '@/utils/access';

const currentUser = () => getStorageJSON(KEYS.USER, null);
const isChannel = () => isChannelUser(currentUser());

/**
 * 客户档案接口（对接 loan-service /api/admin/client，P0-6）。
 *
 * <p>GET 返回合并视图（基础信息 + 企业信息 + 个人档案 + 邀请链 + 审计字段），
 * 敏感字段（phone / creditCode / realName / idCardNo）后端已脱敏，前端仅做兜底脱敏展示。
 */
export function getClientDetail(clientCode) {
  const prefix = isChannel() ? '/api/channel/client' : '/api/admin/client';
  return request({ url: `${prefix}/${clientCode}`, method: 'get' });
}

/**
 * 客户分页：渠道后端强制本人录入范围；顾问默认只查本人归属客户。
 *
 * <p>支持多维筛选：name / phone / enterpriseName / creditCode / createdAtStart / createdAtEnd
 * （D67）。顾问默认自动追加 ownerStaffCode；其他角色按入参 ownerStaffCode 透传。</p>
 */
export function pageClients(params) {
  const user = currentUser();
  const channel = isChannel();
  const url = channel ? '/api/channel/client/page' : '/api/admin/client/page-lite';
  const payload = { ...params };
  if (!channel && user?.roleCode === 'ADVISER' && !payload.ownerStaffCode) {
    payload.ownerStaffCode = user.userNo;
  }
  return request({ url, method: 'get', params: payload });
}

/** 档案编辑（基础信息 + 个人档案合并，含操作留痕） */
export function updateClientDetail(clientCode, data) {
  return request({ url: `/api/admin/client/${clientCode}`, method: 'put', data });
}

/** 未分配客户池：ownerStaffCode 为空的新客户自动进入。 */
export function pageUnassignedClients(params) {
  return request({ url: '/api/admin/client/unassigned/page', method: 'get', params });
}

/** 顾问申请认领未分配客户，审核通过后才建立归属。 */
export function claimUnassignedClient(clientCode) {
  return request({ url: `/api/admin/client/${clientCode}/claim`, method: 'post' });
}

/** 管理者直接指定归属（D39/C23）：立即落归属、无需审核。body 兼容 adviserStaffCode，新前端统一用 targetStaffCode。 */
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

/** 顾问主动释放自己的客户回公海（无需审核，仅归属本人可操作）。 */
export function releaseClient(clientCode) {
  return request({
    url: `/api/admin/client/${clientCode}/release`,
    method: 'post',
  });
}

/** 顾问记录跟进：刷新 lastFollowedAt，避免超期自动回收。 */
export function followClient(clientCode, content) {
  return request({
    url: `/api/admin/client/${clientCode}/follow`,
    method: 'post',
    data: { content },
  });
}

/**
 * 客户分配/跟进历史（t_lead_allocation_record 复用）：
 * CLAIM_APPLY/CLAIM_APPROVED/CLIENT_RECYCLE/CLIENT_RECYCLE_MANUAL/FOLLOW_UP 等流水。
 */
export function getClientHistory(clientCode, params) {
  return request({
    url: `/api/admin/client/${clientCode}/history`,
    method: 'get',
    params: params || { page: 1, size: 50 },
  });
}
