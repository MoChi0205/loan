import request from '@/utils/request';

/** 渠道 Web 本人产品工作区；全部接口由后端登录态强制限定当前渠道。 */
export function listChannelProducts() {
  return request({ url: '/api/channel/product/list', method: 'get' });
}

export function getChannelProduct(approvalNo) {
  return request({ url: `/api/channel/product/${approvalNo}`, method: 'get' });
}

export function createChannelProduct(data) {
  return request({ url: '/api/channel/product', method: 'post', data });
}

export function updateChannelProduct(approvalNo, data) {
  return request({ url: `/api/channel/product/${approvalNo}`, method: 'put', data });
}

export function submitChannelProduct(approvalNo) {
  return request({ url: `/api/channel/product/${approvalNo}/submit`, method: 'post' });
}

export function revokeChannelProduct(approvalNo) {
  return request({ url: `/api/channel/product/${approvalNo}/revoke`, method: 'post' });
}

export function applyDeleteChannelProduct(approvalNo, reason) {
  return request({ url: `/api/channel/product/${approvalNo}/delete-apply`, method: 'post', data: { reason } });
}

export function cancelDeleteChannelProduct(approvalNo) {
  return request({ url: `/api/channel/product/${approvalNo}/delete-cancel`, method: 'post' });
}
