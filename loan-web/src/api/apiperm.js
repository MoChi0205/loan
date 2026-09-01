import request from '@/utils/request';

/**
 * 接口权限管理 API（BOSS 可用）。
 */

/** 接口清单分页 */
export function pageApiPerm(params) {
  return request({ url: '/api/admin/api-perm/page', method: 'get', params });
}

/** 角色已授权接口列表（回显） */
export function roleApiPerm(roleCode) {
  return request({ url: '/api/admin/api-perm/role/list', method: 'get', params: { roleCode } });
}

/** 保存角色接口授权 */
export function saveRoleApiPerm(roleCode, apiKeys) {
  return request({ url: '/api/admin/api-perm/role/save', method: 'post', data: { roleCode, apiKeys } });
}

/** 更新接口可用端（WEB/MINI_APP） */
export function updateApiClientTypes(apiKey, clientTypes) {
  return request({ url: '/api/admin/api-perm/client-types', method: 'post', data: { apiKey, clientTypes } });
}

/** 手动同步接口清单 */
export function syncApiPerm() {
  return request({ url: '/api/admin/api-perm/sync', method: 'post' });
}
