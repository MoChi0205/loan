/**
 * Web 操作级权限与状态机统一定义。
 *
 * 页面只消费本模块导出的权限码和纯函数，避免角色数组、权限字符串与状态判断散落。
 */
export const ACTION_PERMISSION = Object.freeze({
  LEAD_CREATE: 'lead:create',
  LEAD_CLAIM: 'lead:claim',
  LEAD_ASSIGN: 'lead:assign',
  LEAD_DELETE: 'lead:delete',
  CLIENT_POOL_VIEW: 'client:pool:view',
  CLIENT_CLAIM: 'client:claim',
  CLIENT_ASSIGN: 'client:assign',
  CLIENT_RECYCLE: 'client:recycle',
  CLIENT_UPDATE: 'client:update',
  CLIENT_SCREENING: 'client:screening',
  CLIENT_OWN_VIEW: 'client:own:view',
  REPORT_OWN_VIEW: 'report:own:view',
  PRODUCT_AUDIT: 'approval:product:audit',
  DOWNLOAD_APPLY: 'approval:download:apply',
  DOWNLOAD_AUDIT: 'approval:download:audit',
  DOWNLOAD_VOID: 'approval:download:void',
  ALLOCATION_AUDIT: 'approval:allocation:audit',
  ORDER_CREATE: 'order:create',
  ORDER_STATUS: 'order:status',
});

const BUSINESS_MANAGER_PERMISSIONS = Object.freeze(['*']);

/** 未下发 permissions 时的角色最小权限，安全边界仍由后端负责。 */
const ROLE_DEFAULT_PERMISSIONS = Object.freeze({
  // BOSS 的“*”仅表示业务操作全量；页面能否进入仍以动态菜单为准，系统配置菜单不授予 BOSS。
  BOSS: BUSINESS_MANAGER_PERMISSIONS,
  OPERATOR: BUSINESS_MANAGER_PERMISSIONS,
  SUPER_ADMIN: BUSINESS_MANAGER_PERMISSIONS,
  SUPER: BUSINESS_MANAGER_PERMISSIONS,
  DEPT_MANAGER: Object.freeze([
    'page:client',
    'page:audit',
    ACTION_PERMISSION.LEAD_CREATE,
    ACTION_PERMISSION.LEAD_CLAIM,
    ACTION_PERMISSION.LEAD_ASSIGN,
    ACTION_PERMISSION.LEAD_DELETE,
    ACTION_PERMISSION.CLIENT_POOL_VIEW,
    ACTION_PERMISSION.CLIENT_ASSIGN,
    ACTION_PERMISSION.CLIENT_RECYCLE,
    ACTION_PERMISSION.CLIENT_UPDATE,
    ACTION_PERMISSION.CLIENT_SCREENING,
    ACTION_PERMISSION.PRODUCT_AUDIT,
    ACTION_PERMISSION.DOWNLOAD_APPLY,
    ACTION_PERMISSION.DOWNLOAD_AUDIT,
    ACTION_PERMISSION.DOWNLOAD_VOID,
    ACTION_PERMISSION.ALLOCATION_AUDIT,
    ACTION_PERMISSION.ORDER_CREATE,
    ACTION_PERMISSION.ORDER_STATUS,
  ]),
  ADVISER: Object.freeze([
    'page:client',
    ACTION_PERMISSION.LEAD_CREATE,
    ACTION_PERMISSION.LEAD_CLAIM,
    ACTION_PERMISSION.CLIENT_POOL_VIEW,
    ACTION_PERMISSION.CLIENT_CLAIM,
    ACTION_PERMISSION.CLIENT_UPDATE,
    ACTION_PERMISSION.CLIENT_SCREENING,
    ACTION_PERMISSION.DOWNLOAD_APPLY,
    ACTION_PERMISSION.ORDER_CREATE,
    ACTION_PERMISSION.ORDER_STATUS,
  ]),
  // 渠道只读查看本人录入形成的客户、分析报告和归属姓名；不能操作归属或发起匹配。
  CHANNEL: Object.freeze([
    'page:client',
    'page:channel-report',
    'page:channel-product',
    ACTION_PERMISSION.LEAD_CREATE,
    ACTION_PERMISSION.CLIENT_OWN_VIEW,
    ACTION_PERMISSION.REPORT_OWN_VIEW,
  ]),
});

/**
 * 返回角色默认权限的副本，防止页面意外修改共享常量。
 * @param {string} roleCode 角色编码
 * @returns {string[]} 权限码
 */
export function defaultPermissionsForRole(roleCode) {
  return [...(ROLE_DEFAULT_PERMISSIONS[String(roleCode || '').toUpperCase()] || [])];
}

/** 当前缓存登录态是否为合作渠道账号，供 API 路由选择统一复用。 */
export function isChannelUser(user) {
  return user?.userType === 'CHANNEL';
}

/**
 * 统一权限判断；数组要求全部命中。
 * @param {string[]} permissions 当前权限
 * @param {string|string[]} required 所需权限
 */
export function hasActionPermission(permissions, required) {
  if (!required) return true;
  const list = Array.isArray(permissions) ? permissions : [];
  if (!list.length) return false;
  const pass = (code) => list.includes('*') || list.includes(code);
  return Array.isArray(required) ? required.every(pass) : pass(required);
}

/**
 * 审批行的可操作状态。作废只允许针对已通过且仍有效的下载审批。
 */
export function approvalActionState(kind, row, permissions) {
  const status = row?.approveStatus;
  const active = !row?.voidFlag;
  return {
    canAudit:
      active
      && status === 'PENDING'
      && hasActionPermission(permissions, {
        product: ACTION_PERMISSION.PRODUCT_AUDIT,
        download: ACTION_PERMISSION.DOWNLOAD_AUDIT,
        allocation: ACTION_PERMISSION.ALLOCATION_AUDIT,
      }[kind]),
    canVoid:
      kind === 'download'
      && active
      && status === 'APPROVED'
      && hasActionPermission(permissions, ACTION_PERMISSION.DOWNLOAD_VOID),
  };
}

const ORDER_TRANSITIONS = Object.freeze({
  NEW: Object.freeze(['IN_SERVICE', 'CANCEL']),
  IN_SERVICE: Object.freeze(['DEAL', 'CANCEL']),
  DEAL: Object.freeze(['REFUND']),
});

/**
 * 根据后端状态机与权限返回可执行的目标状态。
 * @returns {string[]} 目标状态列表
 */
export function availableOrderTransitions(status, permissions) {
  if (!hasActionPermission(permissions, ACTION_PERMISSION.ORDER_STATUS)) return [];
  return [...(ORDER_TRANSITIONS[status] || [])];
}
