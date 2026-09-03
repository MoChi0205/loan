import request from '@/utils/request';

/**
 * 推荐奖励接口（对接 loan-service /api/admin/reward）。
 */
export function pageRewards(params) {
  return request({ url: '/api/admin/reward/page', method: 'get', params });
}

export function auditReward(rewardNo, data) {
  return request({ url: `/api/admin/reward/${rewardNo}/audit`, method: 'post', data });
}

export function voidReward(rewardNo, data) {
  return request({ url: `/api/admin/reward/${rewardNo}/void`, method: 'post', data });
}

/** 奖励规则列表（按产品×客群分层） */
export function listRewardRules() {
  return request({ url: '/api/admin/reward/rule', method: 'get' });
}
/** 保存奖励规则（新增/更新） */
export function saveRewardRule(data) {
  return request({ url: '/api/admin/reward/rule', method: 'post', data });
}
/** 停用奖励规则 */
export function disableRewardRule(ruleVersion) {
  return request({ url: `/api/admin/reward/rule/${ruleVersion}/disable`, method: 'post' });
}
