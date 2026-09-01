import request from '@/utils/request';

/**
 * 枚举字典接口（后端统一定义枚举值的唯一来源）。
 */
export function fetchDictAll() {
  return request({
    url: '/api/dict/all',
    method: 'get',
  });
}
