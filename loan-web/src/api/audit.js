import request from '@/utils/request';

/**
 * 审计接口（对接 loan-service /api/admin/audit）。
 */
export function queryAudit(traceUuid) {
  return request({
    url: `/api/admin/audit/${traceUuid}`,
    method: 'get',
  });
}

export function pageAudit(params) {
  return request({
    url: '/api/admin/audit/page',
    method: 'get',
    params,
  });
}
