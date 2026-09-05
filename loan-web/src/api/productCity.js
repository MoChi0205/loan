import request from '@/utils/request';

/**
 * 产品-服务城市接口（对接 loan-service /api/admin/product-city）。
 * 市一级，省市名称字符串，精确匹配。
 */
export function listProductCities(productCode) {
  return request({ url: `/api/admin/product-city/${productCode}`, method: 'get' });
}

export function pageProductCities(params) {
  return request({ url: '/api/admin/product-city/page', method: 'get', params });
}

export function getProductCity(productCityCode) {
  return request({ url: `/api/admin/product-city/relation/${productCityCode}`, method: 'get' });
}

export function batchQueryProductCities(productCityCodes) {
  return request({
    url: '/api/admin/product-city/batch-query',
    method: 'post',
    data: { productCityCodes },
  });
}

export function bindProductCities(productCode, cities) {
  return request({ url: `/api/admin/product-city/${productCode}`, method: 'post', data: cities });
}

export function updateProductCity(productCityCode, data) {
  return request({ url: `/api/admin/product-city/relation/${productCityCode}`, method: 'put', data });
}

export function unbindProductCity(productCityCode) {
  return request({ url: `/api/admin/product-city/relation/${productCityCode}`, method: 'delete' });
}
