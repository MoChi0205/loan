/**
 * 小程序匹配与报告接口（P0-4 / P0-5）。
 *
 * 契约对齐 design-three-terminal.md §3.3：
 * - POST /api/mini/match/run           发起匹配（facts + clientSubmitId 幂等）
 * - GET  /api/mini/match/history       匹配历史（分页）
 * - GET  /api/mini/report/list         我的报告列表（分页）
 * - GET  /api/mini/report/{reportNo}   报告详情（对客脱敏，无产品明细）
 * - GET  /api/mini/partner-product/active 合作库在售产品（仅数量）
 */
import { requestGet, requestPost } from './request';

/**
 * 发起匹配（需先完成身份认证）。
 *
 * 响应字段（✅评审决策 08-28，仅脱敏信息）：
 * { reportNo, grade, totalResult, productCount, rating, ruleLogs:[{ruleCode,expression,result}] }
 *
 * @param {Object} payload
 * @param {Object} payload.facts         经营事实，key 即 t_rule.field_code
 * @param {string} [payload.applyCity]   申请城市（可选）
 * @param {string} payload.clientSubmitId 客户端幂等键（同键不重复落库）
 * @returns {Promise<Object>}
 */
export function runMatch({ facts, applyCity, clientSubmitId } = {}) {
  return requestPost('/api/mini/match/run', {
    facts,
    ...(applyCity ? { applyCity } : {}),
    ...(clientSubmitId ? { clientSubmitId } : {}),
  });
}

/**
 * 我的匹配历史（与报告列表同源，分页）。
 *
 * @param {number} [page] 页码，默认 1
 * @param {number} [size] 每页条数，默认 10
 * @returns {Promise<{page:number, size:number, total:number, records:Array}>}
 */
export function matchHistory(page = 1, size = 10) {
  return requestGet('/api/mini/match/history', { page, size });
}

/**
 * 报告列表（分页 + 多维筛选，C3 角色二分 / C11 四维查询）。
 *
 * 权限由后端强校验（不依赖前端传参）：
 * - 客户：仅返回本人报告，且**忽略** phone/clientName/entName/creditCode/ownerStaff 等参数
 *   （客户无权跨用户检索，前端亦不展示这些筛选项）
 * - 企业员工（顾问/经理/老板/运营/超管）：可查全量，支持下列维度组合（AND 关系）
 *
 * @param {number} [page=1] 页码
 * @param {number} [size=10] 每页条数
 * @param {Object} [filters] 筛选条件
 * @param {string} [filters.query]      手机号（精确后缀）或客户姓名（模糊）
 * @param {string} [filters.credit]     公司名称（模糊）或统一社会信用代码（精确）
 * @param {string} [filters.owner]      归属：me（归属到我）/ staff（归属到员工）/ all（全量）
 * @param {string} [filters.dateRange]  日期区间：today / 7d / 30d / all
 * @returns {Promise<{page:number, size:number, total:number, records:Array}>}
 */
export function reportList(page = 1, size = 10, filters = {}) {
  const params = { page, size };
  // 仅传非空条件，避免后端收到空串做全等匹配
  if (filters.query) params.query = filters.query;
  if (filters.credit) params.credit = filters.credit;
  if (filters.owner && filters.owner !== 'all') params.owner = filters.owner;
  if (filters.dateRange && filters.dateRange !== 'all') params.dateRange = filters.dateRange;
  return requestGet('/api/mini/report/list', params);
}

/**
 * 报告命中的银行产品列表（C4）。
 *
 * 企业员工（渠道除外）可查看某企业的匹配命中产品，用于陪访解读。
 * 渠道受沙箱隔离，后端应直接拒绝。
 *
 * @param {string} reportNo 报告编号
 * @returns {Promise<Array<{productName,bankName,amountRange,rate,term,matchScore,tags,requirement}>>}
 */
export function reportProducts(reportNo) {
  return requestGet(`/api/mini/report/${reportNo}/products`);
}

/**
 * 企业经营诊断（C5，基于报告已上传材料生成）。
 *
 * 材料非最新时前端提示上传最新材料，上传后调用本接口重新生成。
 *
 * @param {string} reportNo 报告编号
 * @returns {Promise<{reportNo, generatedAt, kpi, suggestions, risks, yearData, dimensions}>}
 */
export function reportDiagnosis(reportNo) {
  return requestGet(`/api/mini/report/${reportNo}/diagnosis`);
}

/**
 * 报告详情（校验归属，对客脱敏：仅档位/产品数/评级/规则说明）。
 *
 * @param {string} reportNo 报告编号
 * @returns {Promise<Object>}
 */
export function reportDetail(reportNo) {
  return requestGet(`/api/mini/report/${reportNo}`);
}

/**
 * 合作库在售产品（ACTIVE 且未过期，仅返回数量/列表，小程序仅展示数量）。
 *
 * @returns {Promise<Array|Object|number>}
 */
export function partnerProducts() {
  return requestGet('/api/mini/partner-product/active');
}
