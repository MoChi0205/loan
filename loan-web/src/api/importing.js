import request from '@/utils/request';

/** 管理端批量导入：仅老板/超级管理员可用，后端再次校验角色。 */
export function previewImport(type, file) {
  const form = new FormData();
  form.append('type', type);
  form.append('file', file);
  return request({ url: '/api/admin/import/preview', method: 'post', data: form });
}

export function executeImport(type, file) {
  const form = new FormData();
  form.append('type', type);
  form.append('file', file);
  return request({ url: '/api/admin/import/execute', method: 'post', data: form });
}

export function downloadImportTemplate(type) {
  return request({ url: '/api/admin/import/template', method: 'get', params: { type }, responseType: 'blob' });
}

export function exportImportFailures(failures) {
  return request({ url: '/api/admin/import/result-export', method: 'post', data: { failures }, responseType: 'blob' });
}
