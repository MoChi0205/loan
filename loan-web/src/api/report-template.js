import request from '@/utils/request';

/**
 * 报告模板接口（对接 loan-service /api/admin/report/template）。
 */
export function pageReportTemplates(params) {
  return request({ url: '/api/admin/report/template/page', method: 'get', params });
}
export function saveReportTemplate(data) {
  return request({ url: '/api/admin/report/template/save', method: 'post', data });
}
export function toggleReportTemplate(data) {
  return request({ url: '/api/admin/report/template/toggle', method: 'post', data });
}
