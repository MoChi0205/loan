import request from '@/utils/request';

/**
 * 认领设置接口（对接 loan-service /api/admin/allocation-quota）。
 *
 * <p>参考 tse「分配设置」：每日认领上限与持有上限集中在配置表，后台可改、改完即时生效。
 * 本项目按资源池分行：LEAD=线索公海、CLIENT=客户公海；上限 0 表示不限。</p>
 */

/** 查询各资源池的认领配额配置。 */
export function listAllocationQuota() {
  return request({ url: '/api/admin/allocation-quota/list', method: 'get' });
}

/**
 * 保存认领配额配置（按 scope 幂等 upsert，保存后即时生效）。
 *
 * @param {Array<{scope: string, dailyClaimLimit: number, maxHolding: number, remark?: string}>} items 配置项
 */
export function saveAllocationQuota(items) {
  return request({ url: '/api/admin/allocation-quota/save', method: 'put', data: { items } });
}
