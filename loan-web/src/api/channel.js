import request from '@/utils/request';

/**
 * 合作渠道接口（对接 loan-service /api/admin/channel）。
 */
export function listChannels() {
  return request({ url: '/api/admin/channel/list', method: 'get' });
}
