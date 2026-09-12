/**
 * 客户查重 / 归属分配接口（C2 归属流转 + C10 自动查重）。
 *
 * 契约对齐「小程序模块结论沉淀」C2 / C10：
 * - GET  /api/mini/client/search?keyword=  查重（企业名模糊 / 手机号精确 / 信用代码精确）
 * - POST /api/mini/client                  录入新客户（自动归属当前用户，C2 情形 A）
 * - POST /api/mini/client/{clientCode}/claim 认领/申请转分配：
 *       无归属/公海 → 直接认领；已归属本人 → 幂等；已归属他人 → 申请审批
 * - GET  /api/mini/client/{clientCode}/claim-status 查询分配申请审批状态
 */
import { requestGet, requestPost } from './request';

/**
 * 客户查重。
 *
 * 后端按「企业名称（模糊）/ 手机号（精确）/ 统一社会信用代码（精确）」任一字段命中即返回。
 * 命中结果含 hasOwner，决定前端走「自动归属」还是「审批流转」分支。
 *
 * @param {string} keyword 关键词（≥2 字才发起请求）
 * @returns {Promise<Object|null>}
 *   {
 *     clientCode, entName, contactPhone, creditCode,
 *     ownerStaffCode, ownerStaffName, hasOwner
 *   } 未命中返回 null
 */
export function searchClient(keyword) {
  const kw = (keyword || '').trim();
  if (kw.length < 2) return Promise.resolve(null);
  // 查重失败必须由页面进入错误态，绝不能降级成“未命中”，否则会误建重复客户。
  return requestGet('/api/mini/client/search', { keyword: kw }, { showError: false });
}

/**
 * 录入新客户。归属结果由后端按当前员工角色返回，前端不得假定自动归属。
 *
 * @param {Object} payload
 * @param {string} payload.entName    企业名称（必填）
 * @param {string} [payload.contactName]  联系人
 * @param {string} [payload.contactPhone] 手机号
 * @param {string} [payload.creditCode]   统一社会信用代码（18 位）
 * @param {string} [payload.customerGroup] 客群：ENTERPRISE / PERSONAL
 * @returns {Promise<{clientCode:string, ownerStaffCode?:string, action:string, result?:string, approvalNo?:string}>}
 */
export function createClient(payload) {
  return requestPost('/api/mini/client', payload);
}

/**
 * 申请把已有客户分配给当前用户（C2 情形 B）。
 *
 * 后端按当前归属分流：
 * - 已归属本人 → 幂等返回 { result:'AUTO_CLAIMED' }
 * - 无归属公海客户 → 直接认领；已归属他人 → 提交审批
 *
 * @param {string} clientCode 客户编号
 * @param {string} [reason]   申请理由
 * @returns {Promise<{result:'AUTO_CLAIMED'|'PENDING_APPROVAL', approvalNo?:string}>}
 */
export function claimClient(clientCode, reason) {
  return requestPost(`/api/mini/client/${clientCode}/claim`, { reason: reason || '' });
}

/**
 * 查询分配申请的审批状态（用于"无归宿需审批"分支的轮询/刷新）。
 *
 * @param {string} clientCode 客户编号
 * @returns {Promise<{status:'PENDING'|'APPROVED'|'REJECTED', rejectReason?:string}>}
 */
export function claimStatus(clientCode) {
  return requestGet(`/api/mini/client/${clientCode}/claim-status`, {}, { showError: false });
}

/** 释放本人客户回公海，无需审批。 */
export function releaseClient(clientCode) {
  return requestPost(`/api/mini/client/${clientCode}/release`, {});
}

/* ==================== 我的客户 / 客户公海（D74） ==================== */

/**
 * 「我的客户」列表：仅本人归属客户。
 *
 * 所有员工角色口径一致（用户 2026-09-10 确认「统一只显示本人归属」），
 * 不做团队 / 全司放大；渠道与客户无权调用（后端 requireStaff 拒绝）。
 *
 * @param {string} keyword 企业名模糊关键词，可为空
 * @param {number} page    页码
 * @param {number} size    每页大小
 * @returns {Promise<{records:Array,total:number}>}
 */
export function myClients(keyword = '', page = 1, size = 10) {
  const params = { page, size };
  if (keyword && keyword.trim()) params.keyword = keyword.trim();
  return requestGet('/api/mini/client/my', params);
}

/**
 * 客户公海列表（15-客户公海团队客户与分配回收规则 §11/§12）。
 *
 * 公司公海全员可见；团队公海仅本部门可见（无部门账号后端返回空）。
 * 认领仍走 {@link claimClient}，本接口只读。
 *
 * @param {string} seaLevel ENTERPRISE 公司公海 / TEAM 团队公海
 * @param {string} keyword  企业名模糊关键词，可为空
 * @param {number} page     页码
 * @param {number} size     每页大小
 * @returns {Promise<{records:Array,total:number}>}
 */
export function seaClients(seaLevel = 'ENTERPRISE', keyword = '', page = 1, size = 10) {
  const params = { seaLevel, page, size };
  if (keyword && keyword.trim()) params.keyword = keyword.trim();
  return requestGet('/api/mini/client/sea', params);
}

/**
 * 渠道「我的客户」只读列表：复用既有渠道数据范围接口（D50 只读本人录入客户）。
 *
 * 渠道账号走 typeRules 的 `channel:` 前缀，天然无法访问 mini 客户接口，
 * 故此处单独指向渠道专属只读分页；无认领、无公海、无编辑。
 *
 * @param {string} keyword 关键词，可为空
 * @param {number} page    页码
 * @param {number} size    每页大小
 * @returns {Promise<{records:Array,total:number}>}
 */
export function channelClients(keyword = '', page = 1, size = 10) {
  const params = { page, size };
  if (keyword && keyword.trim()) params.keyword = keyword.trim();
  return requestGet('/api/channel/client/page', params);
}

/* ==================== 团队客户与回收（D75） ==================== */

/**
 * 「团队客户」列表：本部门成员（排除本人）名下客户，仅供部门经理。
 *
 * <p>15-客户公海团队客户与分配回收规则 §10：部门经理「团队客户」与「我的客户」独立展示。
 * 其他员工角色的团队 / 全司视图仍在 Web 管理端。
 *
 * @param {string} keyword 企业名模糊关键词，可为空
 * @param {number} page    页码
 * @param {number} size    每页大小
 * @returns {Promise<{records:Array,total:number}>}
 */
export function teamClients(keyword = '', page = 1, size = 10) {
  const params = { page, size };
  if (keyword && keyword.trim()) params.keyword = keyword.trim();
  return requestGet('/api/mini/client/team', params);
}

/**
 * 回收客户进公海（15-规则 §32/§33/§34）。
 *
 * <p>部门经理回收本团队客户落地到<b>团队公海</b>，老板 / 运营 / 超管回收落地到<b>公司公海</b>；
 * 跨团队回收由服务端拒绝。覆盖冷却期，不删除客户档案。
 *
 * @param {string} clientCode 客户编码
 * @returns {Promise<{clientCode:string, recycled:boolean, fromOwnerStaffCode:string}>}
 */
export function recycleClient(clientCode) {
  return requestPost(`/api/mini/client/${clientCode}/recycle`, {});
}

/* ==================== C19-B3：无归宿分配审批（运营/超管/老板） ==================== */

/**
 * 分配待审列表（运营/超管/老板审批中心入口）。
 *
 * @deprecated 改用 api/approval.js 的 pendingApprovals / auditApproval
 * @param {number} page 页码
 * @param {number} size 每页大小
 * @returns {Promise<{records:Array}>}
 */
export function pendingAllocations(page = 1, size = 10) {
  return requestGet('/api/mini/client/allocation-approvals/pending', { page, size });
}

/**
 * 通过分配审批：客户归属流转给申请人。
 *
 * @deprecated 改用 api/approval.js 的 pendingApprovals / auditApproval
 * @param {string} approvalNo 审批单号
 * @returns {Promise<{status:'APPROVED'}>}
 */
export function approveAllocation(approvalNo) {
  return requestPost(`/api/mini/client/allocation-approvals/${approvalNo}/approve`, {});
}

/**
 * 驳回分配审批（驳回意见必填）。
 *
 * @deprecated 改用 api/approval.js 的 pendingApprovals / auditApproval
 * @param {string} approvalNo 审批单号
 * @param {string} opinion    驳回意见
 * @returns {Promise<{status:'REJECTED'}>}
 */
export function rejectAllocation(approvalNo, opinion) {
  return requestPost(`/api/mini/client/allocation-approvals/${approvalNo}/reject`, { opinion });
}
