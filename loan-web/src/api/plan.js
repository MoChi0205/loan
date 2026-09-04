import request from '@/utils/request';

/**
 * 执行计划接口（对接 loan-service /api/admin/execution-plan）。
 * 计划 + 模块 + 步骤编排。
 */
export function listPlans() {
  return request({ url: '/api/admin/execution-plan/list', method: 'get' });
}

export function planDetail(planCode) {
  return request({ url: `/api/admin/execution-plan/${encodeURIComponent(planCode)}/detail`, method: 'get' });
}

export function createPlan(data) {
  return request({ url: '/api/admin/execution-plan', method: 'post', data });
}

export function updatePlan(planCode, data) {
  return request({ url: `/api/admin/execution-plan/${encodeURIComponent(planCode)}`, method: 'put', data });
}

export function deletePlan(planCode) {
  return request({ url: `/api/admin/execution-plan/${encodeURIComponent(planCode)}`, method: 'delete' });
}

// 模块
export function createModule(data) {
  return request({ url: '/api/admin/execution-plan/module', method: 'post', data });
}
export function updateModule(code, data) {
  return request({ url: `/api/admin/execution-plan/module/${encodeURIComponent(code)}`, method: 'put', data });
}
export function deleteModule(code) {
  return request({ url: `/api/admin/execution-plan/module/${encodeURIComponent(code)}`, method: 'delete' });
}

// 步骤
export function createStep(data) {
  return request({ url: '/api/admin/execution-plan/step', method: 'post', data });
}
export function updateStep(code, data) {
  return request({ url: `/api/admin/execution-plan/step/${encodeURIComponent(code)}`, method: 'put', data });
}
export function deleteStep(code) {
  return request({ url: `/api/admin/execution-plan/step/${encodeURIComponent(code)}`, method: 'delete' });
}

// 模版应用 / 另存为模版 / 计划复制（对齐 mds v2 apply-template / save-as-template / copy）
export function applyTemplate(data) {
  return request({ url: '/api/admin/execution-plan/apply-template', method: 'post', data });
}

export function saveAsTemplate(data) {
  return request({ url: '/api/admin/execution-plan/save-as-template', method: 'post', data });
}

export function copyPlan(data) {
  return request({ url: '/api/admin/execution-plan/copy', method: 'post', data });
}
