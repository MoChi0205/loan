import request from '@/utils/request';

/** 管理端批量导入：仅老板/超级管理员可用，后端再次校验角色。 */
export function previewImport(type, file) {
  const form = new FormData();
  form.append('type', type);
  form.append('file', file);
  return request({ url: '/api/admin/import/preview', method: 'post', data: form });
}

export function executeImport(type, file, poolCode) {
  const form = new FormData();
  form.append('type', type);
  if (poolCode) form.append('poolCode', poolCode);
  form.append('file', file);
  return request({ url: '/api/admin/import/execute', method: 'post', data: form });
}

/* ============ 资源池管理（创建/列表/启停） ============ */
export function pageResourcePools(params) {
  return request({ url: '/api/admin/resource-pool/page', method: 'get', params });
}
export function listResourcePools() {
  return request({ url: '/api/admin/resource-pool/list', method: 'get' });
}
export function createResourcePool(data) {
  return request({ url: '/api/admin/resource-pool', method: 'post', data });
}
export function toggleResourcePool(poolCode) {
  return request({ url: `/api/admin/resource-pool/${poolCode}/toggle`, method: 'post' });
}

export function downloadImportTemplate(type) {
  return request({ url: '/api/admin/import/template', method: 'get', params: { type }, responseType: 'blob' });
}

export function exportImportFailures(failures) {
  return request({ url: '/api/admin/import/result-export', method: 'post', data: { failures }, responseType: 'blob' });
}

export function pageImportTasks(params = {}) {
  return request({ url: '/api/admin/import/tasks', method: 'get', params });
}

export function getImportTask(taskNo) {
  return request({ url: '/api/admin/import/task', method: 'get', params: { taskNo } });
}

/** 维护单个资源池的回收规则（一个池一条规则，参考 tse；仅经理/老板/超管）。 */
export function savePoolRecycle(poolCode, data) {
  return request({ url: `/api/admin/resource-pool/${poolCode}/recycle`, method: 'put', data });
}
