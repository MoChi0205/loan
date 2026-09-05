import request from '@/utils/request';

/**
 * 组织权限接口（对接 loan-service /api/admin/org）。
 */
export function menuTree(roleCode) {
  return request({ url: '/api/admin/org/menu/tree', method: 'get', params: { roleCode } });
}

export function departmentTree() {
  return request({ url: '/api/admin/org/department/tree', method: 'get' });
}

export function roleList() {
  return request({ url: '/api/admin/org/role/list', method: 'get' });
}

export function staffPage(params) {
  return request({ url: '/api/admin/org/staff/page', method: 'get', params });
}

/** 角色已授权菜单（权限配置回显） */
export function rolePermissionMenuIds(roleCode) {
  return request({ url: '/api/admin/org/permission/list', method: 'get', params: { roleCode } });
}

// ============ 组织权限写接口（与查询同域 /api/admin/org） ============

export function saveDepartment(data) {
  return request({ url: '/api/admin/org/department/save', method: 'post', data });
}
export function disableDepartment(data) {
  return request({ url: '/api/admin/org/department/disable', method: 'post', data });
}
export function saveStaff(data) {
  return request({ url: '/api/admin/org/staff/save', method: 'post', data });
}
export function disableStaff(data) {
  return request({ url: '/api/admin/org/staff/disable', method: 'post', data });
}
export function saveRolePermission(data) {
  return request({ url: '/api/admin/org/permission/save', method: 'post', data });
}
