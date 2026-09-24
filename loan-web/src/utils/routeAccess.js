/**
 * 动态菜单驱动的路由准入工具。
 *
 * 后端菜单树是页面级权限真值；前端路由仅负责把已授权 path 映射到组件。
 * 子页面通过 ROUTE_PARENT 显式继承父菜单权限，避免再次维护一套角色数组。
 */
const PUBLIC_PATHS = new Set(['/login']);
const LAYOUT_COMMON_PATHS = new Set(['/workbench', '/403']);
const ROUTE_PARENT = Object.freeze({
  '/client': '/client/my',
  '/service-operations': '/service-operations/daily',
  '/channel-config-wizard': '/channel-config',
  // 合并菜单后的旧页面仍作为“渠道管理 / 奖励管理”的页内 Tab 保留。
  '/reward-rule': '/reward/rules',
  '/report/overview': '/report/center',
  '/report/center': '/report/center',
  '/report/trend': '/report/trend',
  '/report/screening': '/report/screening',
});

const LEGACY_PARENT = Object.freeze({
  '/lead/my': '/lead',
  '/lead/pool': '/lead',
  '/client/my': '/client',
  '/client/team': '/client',
  '/client/company': '/client',
  '/client/company-sea': '/client',
  '/client/team-sea': '/client',
  '/service-operations/daily': '/service-operations',
  '/service-operations/appointments': '/service-operations',
  '/service-operations/outings': '/service-operations',
  '/service-operations/replay': '/service-operations',
  '/product/all': '/product',
  '/product/cooperate': '/product',
  '/reward/records': '/reward',
  '/reward/rules': '/reward-rule',
  '/sms/templates': '/sms',
  '/sms/records': '/sms',
  '/org/staff': '/org',
  '/org/roles': '/org',
  '/org/api-permissions': '/org',
  '/approval/mine': '/approval',
  '/approval/product': '/approval',
  '/approval/download': '/approval',
  '/approval/allocation': '/approval',
  '/approval/channel-lead': '/approval',
  '/approval/sms-template': '/approval',
  '/approval/report-template': '/approval',
});

/** 把菜单树扁平化为不带 query 的 path 集合。 */
export function flattenMenuPaths(nodes) {
  const paths = new Set();
  const walk = (list) => {
    if (!Array.isArray(list)) return;
    list.forEach((node) => {
      const routePath = String(node?.code || node?.path || '').split('?')[0];
      if (routePath) paths.add(routePath);
      walk(node?.children);
    });
  };
  walk(nodes);
  return paths;
}

/** 判断目标路由是否属于动态菜单授权范围。 */
export function canAccessRoute(routePath, menuPaths, options = {}) {
  const normalized = String(routePath || '').split('?')[0];
  if (PUBLIC_PATHS.has(normalized) || LAYOUT_COMMON_PATHS.has(normalized)) return true;
  if (!(menuPaths instanceof Set)) return false;
  // 调试中心是“菜单授权 + 运行环境开关”双门禁；生产关闭时手输 URL 也不能加载组件。
  if (normalized === '/debug' && options.debugCenterEnabled !== true) return false;
  const requiredPath = ROUTE_PARENT[normalized] || normalized;
  return menuPaths.has(normalized)
    || menuPaths.has(requiredPath)
    || Boolean(LEGACY_PARENT[normalized] && menuPaths.has(LEGACY_PARENT[normalized]));
}

/**
 * 根据动态菜单判断已打开标签是否可保留。
 *
 * 与路由守卫共用同一套子路由继承规则，避免合法的报表/渠道向导标签在
 * 菜单异步返回后被误删。/403 是瞬时错误态，永不持久化。
 */
export function canKeepOpenedTab(tabPath, menuPaths, options = {}) {
  const normalized = String(tabPath || '').split('?')[0];
  if (normalized === '/403') return false;
  return canAccessRoute(normalized, menuPaths, options);
}
