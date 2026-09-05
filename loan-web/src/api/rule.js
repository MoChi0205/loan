import request from '@/utils/request';

/**
 * 规则目录接口（对接 loan-service /api/admin/rule）。
 */
export function listRules(params) {
  return request({
    url: '/api/admin/rule/list',
    method: 'get',
    params,
  });
}

/** 新增规则 */
export function createRule(data) {
  return request({
    url: '/api/admin/rule',
    method: 'post',
    data,
  });
}

/** 编辑规则 */
export function updateRule(data) {
  return request({
    url: '/api/admin/rule',
    method: 'put',
    data,
  });
}

/** 删除规则（按业务唯一编码 ruleCode） */
export function deleteRule(ruleCode) {
  return request({
    url: `/api/admin/rule/${ruleCode}`,
    method: 'delete',
  });
}

/** 批量更新规则状态（启用 / 停用） */
export function batchUpdateRuleStatus(data) {
  return request({
    url: '/api/admin/rule/batch-status',
    method: 'post',
    data,
  });
}
