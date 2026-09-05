/**
 * 管理端顶部导航标签的纯函数模型。
 *
 * 标签只保存轻量路由元数据，不持有组件实例；所有入口统一经过这里做校验、去重和限量，
 * 避免持久化脏数据或无限标签参与每次 Vue patch。
 */
export const HOME_TAB = Object.freeze({ path: '/workbench', title: '工作台' });
export const MAX_OPEN_TABS = 12;

function normalizeTab(tab) {
  const path = String(tab?.path || '').trim();
  const title = String(tab?.title || '').trim();
  if (!path.startsWith('/') || !title || path.split('?')[0] === '/403') return null;
  return { path, title };
}

/** 清理、去重并限制标签数量；工作台永远位于首位。 */
export function sanitizeTabs(tabs, max = MAX_OPEN_TABS) {
  const limit = Math.max(1, Number(max) || MAX_OPEN_TABS);
  const seen = new Set([HOME_TAB.path]);
  const normalized = [HOME_TAB];
  if (Array.isArray(tabs)) {
    tabs.forEach((tab) => {
      const item = normalizeTab(tab);
      if (!item || seen.has(item.path)) return;
      seen.add(item.path);
      normalized.push(item);
    });
  }
  if (normalized.length <= limit) return normalized;
  return [HOME_TAB, ...normalized.slice(-(limit - 1))];
}

/**
 * 加入或更新标签。超过上限时回收最早打开的非工作台、非当前标签，保持渲染规模稳定。
 */
export function upsertTab(tabs, tab, currentPath = '', max = MAX_OPEN_TABS) {
  const item = normalizeTab(tab);
  const list = sanitizeTabs(tabs, Number.MAX_SAFE_INTEGER);
  if (!item) return sanitizeTabs(list, max);
  const exists = list.find((entry) => entry.path === item.path);
  if (exists) {
    // HOME_TAB 是 Object.freeze 冻结对象，不可赋值；且其 title 已是最终值，跳过
    if (exists !== HOME_TAB) exists.title = item.title;
  } else list.push(item);
  const limit = Math.max(1, Number(max) || MAX_OPEN_TABS);
  while (list.length > limit) {
    const removable = list.findIndex((entry, index) => index > 0 && entry.path !== currentPath && entry.path !== item.path);
    list.splice(removable > 0 ? removable : 1, 1);
  }
  return list;
}

/** 关闭标签并给出当前页关闭后的相邻跳转目标。 */
export function closeTab(tabs, path) {
  const list = sanitizeTabs(tabs, Number.MAX_SAFE_INTEGER);
  if (path === HOME_TAB.path) return { tabs: list, nextPath: HOME_TAB.path };
  const index = list.findIndex((tab) => tab.path === path);
  if (index < 0) return { tabs: list, nextPath: null };
  list.splice(index, 1);
  const next = list[Math.min(index, list.length - 1)] || HOME_TAB;
  return { tabs: list, nextPath: next.path };
}
