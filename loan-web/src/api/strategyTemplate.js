import request from '@/utils/request';

/**
 * 策略模版接口（对接 loan-service /api/admin/strategy-template）。
 */
export function pageTemplate(params) {
  return request({ url: '/api/admin/strategy-template/page', method: 'get', params });
}

export function createTemplate(data) {
  return request({ url: '/api/admin/strategy-template', method: 'post', data });
}

export function updateTemplate(templateCode, data) {
  return request({ url: `/api/admin/strategy-template/${encodeURIComponent(templateCode)}`, method: 'put', data });
}

export function deleteTemplate(templateCode) {
  return request({ url: `/api/admin/strategy-template/${encodeURIComponent(templateCode)}`, method: 'delete' });
}

export function publishTemplate(templateCode) {
  return request({ url: `/api/admin/strategy-template/${encodeURIComponent(templateCode)}/publish`, method: 'post' });
}

export function offlineTemplate(templateCode) {
  return request({ url: `/api/admin/strategy-template/${encodeURIComponent(templateCode)}/offline`, method: 'post' });
}

export function templateDetail(templateCode) {
  return request({ url: `/api/admin/strategy-template/${encodeURIComponent(templateCode)}/detail`, method: 'get' });
}

// 模块
export function createTemplateModule(data) {
  return request({ url: '/api/admin/strategy-template/module', method: 'post', data });
}
export function updateTemplateModule(code, data) {
  return request({ url: `/api/admin/strategy-template/module/${encodeURIComponent(code)}`, method: 'put', data });
}
export function deleteTemplateModule(code) {
  return request({ url: `/api/admin/strategy-template/module/${encodeURIComponent(code)}`, method: 'delete' });
}

// 步骤
export function createTemplateStep(data) {
  return request({ url: '/api/admin/strategy-template/step', method: 'post', data });
}
export function updateTemplateStep(code, data) {
  return request({ url: `/api/admin/strategy-template/step/${encodeURIComponent(code)}`, method: 'put', data });
}
export function deleteTemplateStep(code) {
  return request({ url: `/api/admin/strategy-template/step/${encodeURIComponent(code)}`, method: 'delete' });
}

// 渠道策略 → 模版快照（对齐 mds v2 snapshot-from-channel）
export function snapshotFromChannel(data) {
  return request({ url: '/api/admin/strategy-template/snapshot-from-channel', method: 'post', data });
}
