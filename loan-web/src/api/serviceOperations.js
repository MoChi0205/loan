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

/** 外出记录：本人提交申请 → 主管审核 → 出发/返回双打卡（定位 + 现场照片）。 */
export function pageOutings(params) {
  return request({ url: '/api/admin/outing/day', method: 'get', params });
}
export function pagePendingOutings(params) {
  return request({ url: '/api/admin/outing/pending', method: 'get', params });
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
/** 审核通过（主管；不能审自己的申请，部门经理限本部门）。 */
export function approveOuting(no, reason) {
  return request({ url: `/api/admin/outing/${no}/approve`, method: 'post', data: { reason } });
}
/** 审核驳回（驳回原因必填）。 */
export function rejectOuting(no, reason) {
  return request({ url: `/api/admin/outing/${no}/reject`, method: 'post', data: { reason } });
}
/** 驳回后由申请人本人修改并重新提交。 */
export function resubmitOuting(no, data) {
  return request({ url: `/api/admin/outing/${no}/resubmit`, method: 'post', data });
}
/** 上传打卡照片，返回 { fileKey }，供出发/返回打卡携带。 */
export function uploadOutingPhoto(no, file) {
  const form = new FormData();
  form.append('file', file);
  return request({
    url: `/api/admin/outing/${no}/photo`,
    method: 'post',
    data: form,
    headers: { 'Content-Type': 'multipart/form-data' },
  });
}

/**
 * 取打卡照片 Blob（受控预览）。
 *
 * <p>统一请求层已支持 Blob；继续复用鉴权、超时、业务错误标记，避免预览失败击穿页面异常边界。
 */
export function fetchOutingPhoto(no, phase) {
  return request({
    url: `/api/admin/outing/${no}/photo`,
    method: 'get',
    params: { phase },
    responseType: 'blob',
  });
}

/** 客户跟进与活动回放。 */
export function createFollowRecord(clientCode, data) {
  return request({ url: `/api/admin/client/${clientCode}/follow-record`, method: 'post', data });
}
export function getClientActivityTimeline(clientCode, params) {
  return request({ url: `/api/admin/client/${clientCode}/activity-timeline`, method: 'get', params });
}

/** 客户画像版本快照（同一客户仅一个当前版本，历史版本用于回放变化）。 */
export function getClientInsight(clientCode) {
  return request({ url: `/api/admin/client/${clientCode}/insight`, method: 'get' });
}
export function getClientInsightHistory(clientCode, params) {
  return request({ url: `/api/admin/client/${clientCode}/insight/history`, method: 'get', params });
}
export function generateClientInsight(clientCode) {
  return request({ url: `/api/admin/client/${clientCode}/insight/generate`, method: 'post' });
}
export function reviewClientInsight(clientCode, snapshotNo, data) {
  return request({
    url: `/api/admin/client/${clientCode}/insight/${snapshotNo}/review`,
    method: 'post',
    data,
  });
}
