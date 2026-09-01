/**
 * 小程序服务单与奖励接口（P0-6）。
 *
 * 契约对齐 design-three-terminal.md §3.3：
 * - GET /api/mini/order/list        我的服务单（分页，可按状态过滤）
 * - GET /api/mini/order/{orderNo}   服务单详情（校验归属）
 * - GET /api/mini/reward/mine/summary 我的奖励汇总
 * - GET /api/mini/reward/mine       我的奖励记录（分页）
 */
import { requestGet } from './request';

/**
 * 服务单 / 工单列表（C7 四维筛选）。
 *
 * 契约：`GET /api/mini/order/list`
 *
 * 权限由后端强校验（不依赖前端传参）：
 * - 客户：仅返回本人服务单，且**忽略** clientName/phone 等跨用户检索参数，
 *   仅允许 status 与 dateRange 生效。
 * - 企业员工（顾问/经理/老板/运营/超管）：可查全量，支持四维组合。
 *
 * @param {Object} [params]
 * @param {number} [params.page=1]       页码
 * @param {number} [params.size=10]      每页条数
 * @param {string} [params.status]       工单状态：PENDING/PROCESSING/SUPPLEMENT/DONE/CANCELED/all
 * @param {string} [params.clientName]   客户姓名（模糊）
 * @param {string} [params.phone]        手机号（精确，后端做摘要比对）
 * @param {string} [params.dateRange]    日期区间：today / 7d / 30d / all
 * @returns {Promise<{page:number, size:number, total:number, records:Array}>}
 */
export function orderList({ status, clientName, phone, dateRange, page = 1, size = 10 } = {}) {
  const params = { page, size };
  // 仅传非空条件，避免后端收到空串做全等匹配
  if (status && status !== 'all') params.status = status;
  if (clientName && clientName.trim()) params.clientName = clientName.trim();
  if (phone && phone.trim()) params.phone = phone.trim();
  if (dateRange && dateRange !== 'all') params.dateRange = dateRange;
  return requestGet('/api/mini/order/list', params);
}

/**
 * 服务单详情（校验归属）。
 *
 * @param {string} orderNo 工单号
 * @returns {Promise<Object>}
 */
export function orderDetail(orderNo) {
  return requestGet(`/api/mini/order/${orderNo}`);
}

/**
 * 我的奖励汇总。
 *
 * @returns {Promise<{totalAmount:number, totalCount:number, pendingCount:number, grantedCount:number}>}
 */
export function rewardSummary() {
  return requestGet('/api/mini/reward/mine/summary');
}

/**
 * 我的奖励记录（分页）。
 *
 * @param {number} [page] 页码，默认 1
 * @param {number} [size] 每页条数，默认 10
 * @returns {Promise<{page:number, size:number, total:number, records:Array}>}
 */
export function rewardList(page = 1, size = 10) {
  return requestGet('/api/mini/reward/mine', { page, size });
}
