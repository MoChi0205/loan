import request from '@/utils/request';

/**
 * 报表中心接口（对接 loan-service /api/admin/report）。
 */
export function reportOverview() {
  return request({ url: '/api/admin/report/overview', method: 'get' });
}
export function orderTrend(months = 12) {
  return request({ url: '/api/admin/report/order-trend', method: 'get', params: { months } });
}
export function rewardTrend(months = 12) {
  return request({ url: '/api/admin/report/reward-trend', method: 'get', params: { months } });
}
export function pageScreenings(params) {
  return request({ url: '/api/admin/report/screening/page', method: 'get', params });
}
export function screeningDetail(reportNo) {
  return request({ url: `/api/admin/report/screening/${reportNo}`, method: 'get' });
}
