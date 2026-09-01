import request from '@/utils/request';

/**
 * 规则模板接口（对接 loan-service /api/admin/rule-template）。
 * 模板 = 一条可复用规则骨架（主表 + 字段定义 + 版本快照），可导入为规则。
 */
export function pageTemplate(params) {
  return request({ url: '/api/admin/rule-template/page', method: 'get', params });
}

export function categories() {
  return request({ url: '/api/admin/rule-template/categories', method: 'get' });
}

export function createTemplate(data) {
  return request({ url: '/api/admin/rule-template', method: 'post', data });
}

export function updateTemplate(id, data) {
  return request({ url: `/api/admin/rule-template/${id}`, method: 'put', data });
}

export function deleteTemplate(id) {
  return request({ url: `/api/admin/rule-template/${id}`, method: 'delete' });
}

export function publishTemplate(id) {
  return request({ url: `/api/admin/rule-template/${id}/publish`, method: 'post' });
}

export function offlineTemplate(id) {
  return request({ url: `/api/admin/rule-template/${id}/offline`, method: 'post' });
}

export function templateDetail(id) {
  return request({ url: `/api/admin/rule-template/${id}/detail`, method: 'get' });
}

export function importToRule(id, fieldId) {
  return request({ url: `/api/admin/rule-template/${id}/import`, method: 'post', params: { fieldId } });
}

// 字段定义
export function createField(data) {
  return request({ url: '/api/admin/rule-template/field', method: 'post', data });
}
export function updateField(id, data) {
  return request({ url: `/api/admin/rule-template/field/${id}`, method: 'put', data });
}
export function deleteField(id) {
  return request({ url: `/api/admin/rule-template/field/${id}`, method: 'delete' });
}
