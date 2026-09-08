import request from '@/utils/request';
import { getStorageJSON, KEYS } from '@/utils/storage';

/**
 * 服务工单接口（对接 loan-service /api/admin/order）。
 */
export function pageOrders(params) {
  return request({ url: '/api/admin/order/page', method: 'get', params });
}

export function createOrder(data) {
  return request({ url: '/api/admin/order', method: 'post', data });
}

export function orderDetail(orderNo) {
  return request({ url: `/api/admin/order/${orderNo}`, method: 'get' });
}

export function updateOrderStatus(orderNo, data) {
  return request({ url: `/api/admin/order/${orderNo}/status`, method: 'put', data });
}

/** 客户轻量分页（建单下拉）：顾问仅可下拉本人归属客户，不可选择他人客户。 */
export function pageClientLite(params) {
  const user = getStorageJSON(KEYS.USER, null);
  const payload = { ...params };
  if (user?.roleCode === 'ADVISER') {
    payload.ownerStaffCode = user.userNo;
  }
  return request({ url: '/api/admin/client/page-lite', method: 'get', params: payload });
}
