import request from '@/utils/request';

/** 黑名单接口（对接 loan-service /api/admin/blacklist） */
export function pageBlacklist(params) {
  return request({ url: '/api/admin/blacklist/page', method: 'get', params });
}
export function addBlacklist(data) {
  return request({ url: '/api/admin/blacklist', method: 'post', data });
}
export function releaseBlacklist(data) {
  return request({ url: '/api/admin/blacklist/release', method: 'post', data });
}
