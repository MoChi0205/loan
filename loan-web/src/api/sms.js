import request from '@/utils/request';

/**
 * 短信中心接口（对接 loan-service /api/admin/sms）。
 */
export function pageSmsTemplates(params) {
  return request({ url: '/api/admin/sms/template/page', method: 'get', params });
}
export function listSmsTemplates() {
  return request({ url: '/api/admin/sms/template/list', method: 'get' });
}
export function saveSmsTemplate(data) {
  return request({ url: '/api/admin/sms/template/save', method: 'post', data });
}
export function toggleSmsTemplate(data) {
  return request({ url: '/api/admin/sms/template/toggle', method: 'post', data });
}
export function pageSmsRecords(params) {
  return request({ url: '/api/admin/sms/record/page', method: 'get', params });
}
export function sendSms(data) {
  return request({ url: '/api/admin/sms/send', method: 'post', data });
}
