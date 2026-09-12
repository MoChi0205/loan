/**
 * 小程序 7 角色配置（唯一来源）。
 *
 * <p>供 TabBar / home / mine / 合并页容器复用，避免各页各写一套角色差异（对齐 loan-code-standard「复用优先」）。
 * 角色标识与 store/user.js#resolveRole 保持一致：
 *   customer / channel / adviser / deptmgr / boss / operator / super
 *
 * <p><b>导航差异（用户 2026-09-10 二次确认，D74）</b>——把原先并列的「录入线索 / 录入产品」与
 * 「我的客户 / 我的报告」各自合并为一个 tab，使各角色 tab 数收敛到 ≤5：
 * <ul>
 *   <li>客户：首页 · 智能匹配 · 我的报告 · 服务单 · 我的（5，不变；客户无录入与名下客户概念）</li>
 *   <li>渠道：首页 · 线索录入 · 我的客户 · 我的（4；「我的客户」只读，无公海、无认领，D50）</li>
 *   <li>顾问：首页 · 线索录入 · 我的客户 · 我的（4；顾问是<b>申请人</b>非审批人，
 *       仅持 `audit:page`，审批入口走 quick「我的申请」→ `/pages/approval/list`）</li>
 *   <li>部门经理 / 运营：首页 · 线索录入 · 我的客户 · 审批中心 · 我的（5）</li>
 *   <li>老板 / 超级管理员：首页 · 线索录入 · 审批中心 · 我的客户 · 我的（5）</li>
 * </ul>
 *
 * <p><b>审批中心 tab 规则（2026-09-11）</b>：具备审批权限的角色<b>一律承载「审批中心」tab</b>——
 * 审批人真值见 `01-角色权限模型.md` §46（DEPT_MANAGER 仅本团队；BOSS/OPERATOR/SUPER 全部）
 * 与后端 `ApiPermissionSyncService`（`MANAGER_APIS` 含 `approval:*`，顾问仅有 `audit:*`）。
 * 老板 / 超管原先把 tab 位给了「智能匹配」，本轮让位给审批中心；智能匹配仍可从首页
 * 快捷宫格磁贴进入，不影响可达性。
 *
 * <p><b>合规：</b>无任何业务单号出现在 label / aria 中（D68 / 02-红线 #7）。
 */

/** tab 键 → AppIcon 图标名 */
export const TAB_ICON = {
  home: 'home', match: 'match', report: 'report', order: 'order', mine: 'person',
  product: 'product', client: 'leads', approval: 'shield',
  // 合并页（D74）
  luru: 'leads',      // 线索录入（客户线索 + 产品）
  clients: 'users',   // 我的客户（我的客户 + 公海 + 我的报告）
};

/** 快捷入口色调 → 磁贴渐变（royal 为主，gold/ink 点缀，green/blue 语义档） */
export const TONE_BG = {
  royal: 'linear-gradient(140deg,#3A63D6,#2C52C9)',
  gold: 'linear-gradient(140deg,#D9BD86,#C7A15A)',
  ink: 'linear-gradient(140deg,#2A3A5E,#1B2C4D)',
  green: 'linear-gradient(140deg,#3FB98A,#2E8B6B)',
  blue: 'linear-gradient(140deg,#5B7CFF,#3A63D6)',
};

/**
 * 页面路由。
 *
 * <p><b>约定（2026-09-11 审计 P0-3 修复）</b>：`quick` 中的入口必须**可跳转**——要么有
 * `route`，要么由页面自行处理（如 `invite` 走分享）。当前端不承载的能力**不放进 quick**，
 * 不允许「展示成可点磁贴 → 点击后才提示请去 Web 管理端」。首页渲染侧同样按此过滤。
 */
export const ROUTES = {
  match: '/pages/match/match',
  report: '/pages/report/list',
  order: '/pages/order/list',
  product: '/pages/product/list',
  leads: '/pages/client/create',            // 录入线索/客户（保留原路由，指向薄壳页）
  approval: '/pages/approval/list',
  advisor: '/pages/mine/mine',              // 我的顾问 → 我的
  luru: '/pages/lead-entry/lead-entry',     // 线索录入合并页
  clients: '/pages/client/mine',            // 我的客户合并页
};

/** 合并页子页签定义 */
const seg = (key, label) => ({ key, label });
/** 线索录入子页签（员工） */
export const ENTRY_SEG_STAFF = [seg('lead', '客户线索'), seg('product', '产品')];
/** 线索录入子页签（渠道：自有产品语义） */
export const ENTRY_SEG_CHANNEL = [seg('lead', '录入客户'), seg('product', '我的产品')];
/** 我的客户子页签（员工：含公海认领，15-规则 §11/§12/§17） */
export const HUB_SEG_STAFF = [seg('mine', '我的客户'), seg('sea', '公海'), seg('report', '我的报告')];
/**
 * 我的客户子页签（部门经理专属）。
 *
 * <p>额外提供「团队」子页签承载本部门成员名下客户，用于「回收」（15-规则 §10 团队客户
 * 与我的客户独立展示、§34 支持本团队批量回收）。其他员工角色的团队 / 全司视图仍在 Web。
 */
export const HUB_SEG_DEPT_MGR = [seg('mine', '我的客户'), seg('team', '团队'), seg('sea', '公海'), seg('report', '我的报告')];
/** 我的客户子页签（渠道：仅只读本人录入客户；报告按 D50 仍只在 Web 查看） */
export const HUB_SEG_CHANNEL = [seg('mine', '我的客户')];

/** tab 定义助手 */
const tab = (key, label) => ({ key, label, icon: TAB_ICON[key] });
/** 快捷入口定义助手；seg 为合并页内目标子页签（可选） */
const quick = (key, label, icon, tone, route, segKey) => ({
  key, label, icon, tone,
  route: route === undefined ? (ROUTES[key] || '') : route,
  seg: segKey || '',
});

export const ROLES = {
  customer: {
    label: '客户', tag: '客户', ut: '个人用户', color: '#3AA37E',
    homeSub: '您可自助智能匹配、查看我的报告与服务单',
    tabs: [tab('home', '首页'), tab('match', '智能匹配'), tab('report', '我的报告'), tab('order', '服务单'), tab('mine', '我的')],
    entry: null,
    hub: null,
    stats: null,
    quick: [
      quick('match', '智能匹配', 'match', 'royal'),
      quick('report', '我的报告', 'report', 'gold'),
      quick('order', '服务单', 'order', 'ink'),
      quick('advisor', '我的顾问', 'support', 'green'),
      { key: 'invite', label: '邀请用户', icon: 'invite', tone: 'royal', route: '' },
    ],
  },
  channel: {
    label: '渠道合作方', tag: '渠道合作方', ut: '合作渠道', color: '#7A5CC0',
    homeSub: '仅展示您录入的线索所形成的客户与报告',
    // 「我的客户」只读；无公海、无认领（D50）
    tabs: [tab('home', '首页'), tab('luru', '线索录入'), tab('clients', '我的客户'), tab('mine', '我的')],
    entry: ENTRY_SEG_CHANNEL,
    hub: HUB_SEG_CHANNEL,
    stats: [{ key: 'leadCount', label: '我录线索' }, { key: 'convertedCount', label: '已转化' }],
    quick: [
      { key: 'leads', label: '我的线索', icon: 'leads', tone: 'royal', route: ROUTES.luru, seg: 'lead' },
      quick('product', '我的产品', 'product', 'gold', ROUTES.luru, 'product'),
      quick('leads', '录入客户', 'leads', 'ink', ROUTES.luru, 'lead'),
      // 客户分析报告：小程序不承载（D50 / 08 矩阵），仍在 Web 查看 → 按 P0-3 不放入 quick
      { key: 'invite', label: '邀请用户', icon: 'invite', tone: 'royal', route: '' },
    ],
  },
  adviser: {
    label: '顾问', tag: '顾问', ut: '公司员工', color: '#2C52C9',
    homeSub: '查看您名下客户，跟进待办与申请进度',
    tabs: [tab('home', '首页'), tab('luru', '线索录入'), tab('clients', '我的客户'), tab('mine', '我的')],
    entry: ENTRY_SEG_STAFF,
    hub: HUB_SEG_STAFF,
    stats: [{ key: 'clientCount', label: '我的客户' }, { key: 'orderCount', label: '待跟进' }],
    quick: [
      { key: 'clients', label: '我的客户', icon: 'users', tone: 'royal', route: ROUTES.clients, seg: 'mine' },
      quick('leads', '录入线索', 'leads', 'gold', ROUTES.luru, 'lead'),
      quick('product', '录入产品', 'product', 'ink', ROUTES.luru, 'product'),
      quick('match', '智能匹配', 'match', 'green'),
      { key: 'invite', label: '邀请用户', icon: 'invite', tone: 'royal', route: '' },
      { key: 'audit', label: '我的申请', icon: 'audit', tone: 'ink', route: ROUTES.approval },
    ],
  },
  deptmgr: {
    label: '部门经理', tag: '部门经理', ut: '公司员工', color: '#C7A15A',
    homeSub: '团队客户、团队分配与本团队审批',
    // 部门经理是审批人（后端 MANAGER_APIS 含 approval:*），与运营一致承载审批中心 tab
    tabs: [tab('home', '首页'), tab('luru', '线索录入'), tab('clients', '我的客户'), tab('approval', '审批中心'), tab('mine', '我的')],
    entry: ENTRY_SEG_STAFF,
    hub: HUB_SEG_DEPT_MGR,
    stats: [{ key: 'teamMemberCount', label: '团队成员' }, { key: 'unassignedCount', label: '待分配' }],
    quick: [
      { key: 'clients', label: '我的客户', icon: 'users', tone: 'royal', route: ROUTES.clients, seg: 'mine' },
      // 团队客户 → 「我的客户」合并页的「团队」子页签（仅部门经理，HUB_SEG_DEPT_MGR）
      quick('team', '团队客户', 'team', 'gold', ROUTES.clients, 'team'),
      quick('leads', '录入线索', 'leads', 'ink', ROUTES.luru, 'lead'),
      quick('product', '录入产品', 'product', 'green', ROUTES.luru, 'product'),
      quick('match', '智能匹配', 'match', 'green'),
      { key: 'invite', label: '邀请用户', icon: 'invite', tone: 'royal', route: '' },
      quick('approval', '审批中心', 'shield', 'ink'),
    ],
  },
  boss: {
    label: '老板', tag: '老板', ut: '公司管理者', color: '#B5443B',
    homeSub: '全局经营、在途审批与业务全量',
    // 老板是审批人（全量角色）→ 承载审批中心 tab；智能匹配让位给审批中心，
    // 仍可从首页快捷宫格「智能匹配」磁贴进入（D79 已给全部员工加该磁贴）。
    tabs: [tab('home', '首页'), tab('luru', '线索录入'), tab('approval', '审批中心'), tab('clients', '我的客户'), tab('mine', '我的')],
    entry: ENTRY_SEG_STAFF,
    hub: HUB_SEG_STAFF,
    stats: [{ key: 'orgClientCount', label: '全司客户' }, { key: 'pendingApprovalCount', label: '在途审批' }],
    quick: [
      { key: 'clients', label: '我的客户', icon: 'users', tone: 'royal', route: ROUTES.clients, seg: 'mine' },
      quick('leads', '录入线索', 'leads', 'gold', ROUTES.luru, 'lead'),
      quick('product', '录入产品', 'product', 'ink', ROUTES.luru, 'product'),
      quick('match', '智能匹配', 'match', 'green'),
      { key: 'invite', label: '邀请用户', icon: 'invite', tone: 'royal', route: '' },
      quick('approval', '审批中心', 'shield', 'ink'),
    ],
  },
  operator: {
    label: '运营管理员', tag: '运营管理员', ut: '公司员工', color: '#2E8B6B',
    homeSub: '我的客户、普通运营审批、模板与短信管理',
    tabs: [tab('home', '首页'), tab('luru', '线索录入'), tab('clients', '我的客户'), tab('approval', '审批中心'), tab('mine', '我的')],
    entry: ENTRY_SEG_STAFF,
    hub: HUB_SEG_STAFF,
    stats: [{ key: 'clientCount', label: '我的客户' }, { key: 'pendingApprovalCount', label: '待我审批' }],
    quick: [
      { key: 'clients', label: '我的客户', icon: 'users', tone: 'royal', route: ROUTES.clients, seg: 'mine' },
      quick('leads', '录入线索', 'leads', 'gold', ROUTES.luru, 'lead'),
      quick('product', '录入产品', 'product', 'ink', ROUTES.luru, 'product'),
      quick('match', '智能匹配', 'match', 'green'),
      { key: 'invite', label: '邀请用户', icon: 'invite', tone: 'green', route: '' },
      quick('approval', '审批中心', 'shield', 'royal'),
    ],
  },
  super: {
    label: '超级管理员', tag: '超级管理员', ut: '系统管理员', color: '#1B2C4D',
    homeSub: '组织·角色·菜单·权限与全量业务',
    // 超管是审批人（全量角色）→ 承载审批中心 tab；智能匹配让位给审批中心，
    // 仍可从首页快捷宫格「智能匹配」磁贴进入（D79 已给全部员工加该磁贴）。
    tabs: [tab('home', '首页'), tab('luru', '线索录入'), tab('approval', '审批中心'), tab('clients', '我的客户'), tab('mine', '我的')],
    entry: ENTRY_SEG_STAFF,
    hub: HUB_SEG_STAFF,
    stats: [{ key: 'orgClientCount', label: '全司客户' }, { key: 'systemErrorCount', label: '系统异常' }],
    quick: [
      { key: 'clients', label: '我的客户', icon: 'users', tone: 'royal', route: ROUTES.clients, seg: 'mine' },
      quick('leads', '录入线索', 'leads', 'gold', ROUTES.luru, 'lead'),
      quick('product', '录入产品', 'product', 'ink', ROUTES.luru, 'product'),
      quick('match', '智能匹配', 'match', 'green'),
      { key: 'invite', label: '邀请用户', icon: 'invite', tone: 'royal', route: '' },
    ],
  },
};

/** 角色展示顺序（与原型 ROLE_ORDER 一致） */
export const ROLE_ORDER = ['customer', 'channel', 'adviser', 'deptmgr', 'boss', 'operator', 'super'];

/**
 * 取角色配置，未知角色兜底为 customer（最小权限）。
 * @param {string} role store.role
 * @returns {Object} ROLES[role] || ROLES.customer
 */
export function roleConfig(role) {
  return ROLES[role] || ROLES.customer;
}

/**
 * 合并页当前子页签解析：优先取 URL 查询参数 seg，非法或缺失时落到第一个子页签。
 * @param {Array<{key:string,label:string}>} segments 子页签定义
 * @param {string} seg 目标子页签 key
 * @returns {string} 合法子页签 key
 */
export function resolveSeg(segments, seg) {
  const list = segments || [];
  if (!list.length) return '';
  return list.some((s) => s.key === seg) ? seg : list[0].key;
}
