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
