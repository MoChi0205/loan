import request from '@/utils/request';

/**
 * 渠道本地白/黑名单接口（对接 loan-service /api/admin/channel-user-list）。
 * 名单键：个人=手机号MD5；企业=统一社会信用代码。
 */
export function pageUserList(params) {
  return request({ url: '/api/admin/channel-user-list/page', method: 'get', params });
}

export function getUserList(listCode) {
  return request({ url: `/api/admin/channel-user-list/${listCode}`, method: 'get' });
}

export function batchQueryUserList(listCodes) {
  return request({ url: '/api/admin/channel-user-list/batch-query', method: 'post', data: { listCodes } });
}

export function addUserList(data) {
  return request({ url: '/api/admin/channel-user-list', method: 'post', data });
}

export function updateUserList(listCode, data) {
  return request({ url: `/api/admin/channel-user-list/${listCode}`, method: 'put', data });
}

export function deleteUserList(listCode) {
  return request({ url: `/api/admin/channel-user-list/${listCode}`, method: 'delete' });
}

export function batchDeleteUserList(listCodes) {
  return request({ url: '/api/admin/channel-user-list/batch-delete', method: 'post', data: { listCodes } });
}
