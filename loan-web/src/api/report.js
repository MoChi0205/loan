import request from '@/utils/request';
import { getStorageJSON, KEYS } from '@/utils/storage';
import { isChannelUser } from '@/utils/access';

const isChannel = () => isChannelUser(getStorageJSON(KEYS.USER, null));

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
  return request({ url: isChannel() ? '/api/channel/report/page' : '/api/admin/report/screening/page', method: 'get', params });
}
export function screeningDetail(reportNo) {
  const prefix = isChannel() ? '/api/channel/report' : '/api/admin/report/screening';
  return request({ url: `${prefix}/${reportNo}`, method: 'get' });
}
