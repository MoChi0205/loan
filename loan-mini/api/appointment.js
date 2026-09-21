/**
 * 小程序 / H5 客户预约与服务进度接口（09-21 方案 §4.2 / §5）。
 *
 * 契约要点：
 * - 客户编号始终取登录态，前端不传 clientCode，服务端强校验归属；
 * - 改期不覆盖原预约：服务端把原预约置为 RESCHEDULED 并新建关联预约；
 * - 预约开始前 5 分钟内的取消/改期必须由服务顾问按异常流程处理，前端只做提示。
 */
import { requestGet, requestPost } from './request';

/**
 * 我的预约列表。
 *
 * @param {Object} [params]
 * @param {number} [params.page=1] 页码
 * @param {number} [params.size=20] 每页条数
 * @returns {Promise<{page:number,size:number,total:number,records:Array}>}
 */
export function myAppointments({ page = 1, size = 20 } = {}) {
  return requestGet('/api/mini/appointment/mine', { page, size });
}

/**
 * 客户发起预约。
 *
 * @param {Object} payload
 * @param {string} payload.serviceMethod      COMPANY_ON_SITE / HOME_VISIT / VIDEO_MEETING / PHONE_CONSULT
 * @param {string} payload.scheduledStart     开始时间（yyyy-MM-ddTHH:mm:ss）
 * @param {string} payload.scheduledEnd       结束时间（yyyy-MM-ddTHH:mm:ss）
 * @param {string} [payload.locationName]     地点名称（现场/上门必填）
 * @param {string} [payload.locationDetail]   详细地址
 * @param {string} [payload.customerVisibleNote] 补充说明
 * @returns {Promise<string>} 预约编号
 */
export function requestAppointment(payload) {
  return requestPost('/api/mini/appointment/request', payload);
}

/** 客户确认员工代建的预约。 */
export function confirmAppointment(appointmentNo) {
  return requestPost(`/api/mini/appointment/${appointmentNo}/confirm`);
}

/** 客户取消预约（开始前 5 分钟外）。 */
export function cancelAppointment(appointmentNo, reason) {
  return requestPost(`/api/mini/appointment/${appointmentNo}/cancel`, { reason });
}

/** 客户改期（开始前 5 分钟外，返回新预约编号）。 */
export function rescheduleAppointment(appointmentNo, payload) {
  return requestPost(`/api/mini/appointment/${appointmentNo}/reschedule`, payload);
}

/** 客户自助到店签到（仅公司现场预约，开始前 30 分钟起允许）。 */
export function checkInAppointment(appointmentNo) {
  return requestPost(`/api/mini/appointment/${appointmentNo}/check-in`);
}

/** 客户可见服务时间线（只返回客户可见事件）。 */
export function serviceTimeline({ page = 1, size = 20 } = {}) {
  return requestGet('/api/mini/service/timeline', { page, size });
}
