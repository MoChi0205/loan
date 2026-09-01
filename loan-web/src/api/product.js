import request from '@/utils/request';

/**
 * 产品库接口（对接 loan-service /api/admin/product）。
 */
export function pageProducts(params) {
  return request({
    url: '/api/admin/product/page',
    method: 'get',
    params,
  });
}

/** 新增产品 */
export function createProduct(data) {
  return request({
    url: '/api/admin/product',
    method: 'post',
    data,
  });
}

/** 编辑产品 */
export function updateProduct(data) {
  return request({
    url: '/api/admin/product',
    method: 'put',
    data,
  });
}

/** 删除产品（按业务唯一编码 productCode） */
export function deleteProduct(productCode) {
  return request({
    url: `/api/admin/product/${productCode}`,
    method: 'delete',
  });
}
