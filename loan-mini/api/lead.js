/**
 * 渠道「录入客户」线索接口（T4 渠道录入客户 tab）。
 *
 * 契约对齐后端 /api/mini/lead（已核实，前端照此对接）：
 * - POST /api/mini/lead/submit：录入线索。请求体为 Map<String,String>（所有值均传字符串，
 *   数字字段由前端转字符串）。source 由后端按用户类型派生，前端不可传。
 *   成功返回 { leadNo, duplicated:false }；重复时 HTTP 200 但 msg="该客户已被录入，请联系运营"、
 *   data={ leadNo:null, duplicated:true }（沙箱脱敏，不泄归属人）。前端据 duplicated 判断友好文案。
 * - GET  /api/mini/lead/my?page=&size=：分页返回本人录入的线索（PageResult<Map>），
 *   每条字段：leadNo / contactName(脱敏) / entName / phone(掩码) / followStatus / createdAt。
 */
import { requestGet, requestPost } from './request';

/**
 * 提交线索录入。
 *
 * @param {Object} payload 字段均为字符串（调用方负责把数字转字符串）。
 *   公共字段：contactName / phone / leadType(ENTERPRISE|PERSONAL) / remark
 *   企业(leadType=ENTERPRISE) 可选：entName / creditCode / industry /
 *     foundYears / annualTaxAmount / annualInvoiceAmount（全部字符串）
 * @returns {Promise<{leadNo:string|null, duplicated:boolean}>}
 *   重复时后端仍走 code=0 成功分支，仅 data.duplicated=true，前端据此显示友好文案。
 */
export function submitLead(payload) {
  return requestPost('/api/mini/lead/submit', payload);
}

/**
 * 查询我录入的线索（分页，仅本人）。
 *
 * @param {number} [page] 页码，默认 1
 * @param {number} [size] 每页大小，默认 10
 * @returns {Promise<{page:number,size:number,total:number,records:Array}>}
 */
export function myLeads(page = 1, size = 10) {
  return requestGet('/api/mini/lead/my', { page, size });
}

/**
 * 线索状态 → 中文标签（与后端 `Lead.followStatus` 枚举对齐，含审批态与跟进态）。
 *
 * <p>渠道视角的核心诉求（D50）：本人录入的线索**待审批、已通过、已驳回均可查看**，
 * 不因审批状态隐藏——故状态标签是「待公司审核 / 审核通过 / 已驳回」，而不是客户跟进态。
 * 未知枚举回退原值，空值回退「待跟进」。
 *
 * @param {string} s `followStatus`
 * @returns {string} 中文标签
 */
export function leadStatusLabel(s) {
  const map = {
    PENDING_APPROVAL: '待公司审核',
    NEW: '审核通过',
    REJECTED: '已驳回',
    PENDING: '待跟进',
    FOLLOWING: '跟进中',
    WON: '已成交',
    LOST: '已流失',
  };
  return map[s] || s || '待跟进';
}

/**
 * 线索状态 → 展示色调（映射到首页 `.m-tag` 的 ok/warn/info 三类）。
 *
 * @param {string} s `followStatus`
 * @returns {'ok'|'warn'|'info'} 标签色调
 */
export function leadStatusTone(s) {
  if (s === 'REJECTED' || s === 'LOST' || s === 'PENDING_APPROVAL') return 'warn';
  if (s === 'NEW' || s === 'WON') return 'ok';
  return 'info';
}
