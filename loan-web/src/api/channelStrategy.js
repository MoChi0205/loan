import request from '@/utils/request';

/**
 * 渠道准入策略接口（对接 loan-service /api/admin/channel-strategy）。
 * 策略 = 渠道 × 产品 × 客群 → 计划(1:1)。
 */
export function pageStrategy(params) {
  return request({ url: '/api/admin/channel-strategy/page', method: 'get', params });
}

export function channelSummary() {
  return request({ url: '/api/admin/channel-strategy/channel-summary', method: 'get' });
}

export function createStrategy(data) {
  return request({ url: '/api/admin/channel-strategy', method: 'post', data });
}

export function updateStrategy(strategyCode, data) {
  return request({ url: `/api/admin/channel-strategy/${strategyCode}`, method: 'put', data });
}

export function deleteStrategy(strategyCode) {
  return request({ url: `/api/admin/channel-strategy/${strategyCode}`, method: 'delete' });
}

export function enableStrategy(strategyCode) {
  return request({ url: `/api/admin/channel-strategy/${strategyCode}/enable`, method: 'post' });
}

export function disableStrategy(strategyCode) {
  return request({ url: `/api/admin/channel-strategy/${strategyCode}/disable`, method: 'post' });
}

export function validateStrategy(strategyCode) {
  return request({ url: `/api/admin/channel-strategy/${strategyCode}/validate-before-enable`, method: 'post' });
}

export function importFromChannel(data) {
  return request({ url: '/api/admin/channel-strategy/import-from-channel', method: 'post', data });
}

export function importFromTemplate(data) {
  return request({ url: '/api/admin/channel-strategy/import-from-template', method: 'post', data });
}
