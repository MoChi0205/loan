import request from '@/utils/request';

/** 今日服务台（来访、外出、待回访、活跃工单）。 */
export function getDailyServiceLists(params) {
  return request({ url: '/api/admin/workbench/daily-lists', method: 'get', params });
}

/** 预约与履约。 */
export function pageAppointments(params) {
  return request({ url: '/api/admin/appointment/day', method: 'get', params });
}
export function createAppointment(data) {
  return request({ url: '/api/admin/appointment', method: 'post', data });
}
export function confirmAppointment(no) {
  return request({ url: `/api/admin/appointment/${no}/confirm`, method: 'post' });
}
export function arriveAppointment(no) {
  return request({ url: `/api/admin/appointment/${no}/arrive`, method: 'post' });
}
export function startAppointment(no) {
  return request({ url: `/api/admin/appointment/${no}/start`, method: 'post' });
}
export function completeAppointment(no) {
  return request({ url: `/api/admin/appointment/${no}/complete`, method: 'post' });
}
export function noShowAppointment(no) {
  return request({ url: `/api/admin/appointment/${no}/no-show`, method: 'post' });
}
export function cancelAppointment(no, reason, exceptionFlow = false) {
  return request({
    url: `/api/admin/appointment/${no}/${exceptionFlow ? 'exception-cancel' : 'cancel'}`,
    method: 'post',
    data: { reason },
  });
}
export function rescheduleAppointment(no, data, exceptionFlow = false) {
  return request({
    url: `/api/admin/appointment/${no}/${exceptionFlow ? 'exception-reschedule' : 'reschedule'}`,
    method: 'post',
    data,
  });
}

/** 外出记录与单点定位双打卡。 */
export function pageOutings(params) {
  return request({ url: '/api/admin/outing/day', method: 'get', params });
}
export function createOuting(data) {
  return request({ url: '/api/admin/outing', method: 'post', data });
}
export function departOuting(no, data) {
  return request({ url: `/api/admin/outing/${no}/depart`, method: 'post', data });
}
export function returnOuting(no, data) {
  return request({ url: `/api/admin/outing/${no}/return`, method: 'post', data });
}

/** 客户跟进与活动回放。 */
export function createFollowRecord(clientCode, data) {
  return request({ url: `/api/admin/client/${clientCode}/follow-record`, method: 'post', data });
}
export function getClientActivityTimeline(clientCode, params) {
  return request({ url: `/api/admin/client/${clientCode}/activity-timeline`, method: 'get', params });
}
