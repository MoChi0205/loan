import request from '@/utils/request';

/**
 * 调试中心接口（调试执行 / 试运行）。
 */
export function shadowMatch(data) {
  return request({
    url: '/api/debug/match',
    method: 'post',
    data,
  });
}
