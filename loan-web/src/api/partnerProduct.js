import request from '@/utils/request';

/**
 * 合作库接口（对接 loan-service /api/admin/partner-product，P0-5）。
 *
 * <p>t_partner_product 状态机：ACTIVE（在库）/ EXPIRING（即将到期）/ EXPIRED（已到期）/ OFFLINE（手动下架）。
 * 续签/上下架均按业务编码 bankProductCode 定位（评审决策：业务主键一律用业务编码）。
 */
export function pagePartnerProducts(params) {
  return request({ url: '/api/admin/partner-product/page', method: 'get', params });
}

/** 录入合作库：{ bankProductCode, cooperateUntil, status? } */
export function savePartnerProduct(data) {
  return request({ url: '/api/admin/partner-product', method: 'post', data });
}

/** 续签：{ cooperateUntil } */
export function renewPartnerProduct(code, cooperateUntil) {
  return request({ url: `/api/admin/partner-product/${code}/renew`, method: 'put', data: { cooperateUntil } });
}

/** 手动下架 / 上架：{ status } */
export function updatePartnerProductStatus(code, status) {
  return request({ url: `/api/admin/partner-product/${code}/status`, method: 'put', data: { status } });
}
