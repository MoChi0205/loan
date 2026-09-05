import { describe, expect, it } from 'vitest';
import { canAccessRoute, canKeepOpenedTab, flattenMenuPaths } from '@/utils/routeAccess';

describe('动态菜单路由准入', () => {
  it('递归提取菜单路径并忽略查询参数', () => {
    const paths = flattenMenuPaths([
      { code: '/workbench' },
      { children: [{ code: '/rule?cg=ENTERPRISE' }] },
    ]);
    expect([...paths]).toEqual(['/workbench', '/rule']);
  });

  it('仅放行动态菜单、公共布局页及显式子页面', () => {
    const paths = new Set(['/workbench', '/client', '/channel-config', '/report/center']);
    expect(canAccessRoute('/client', paths)).toBe(true);
    expect(canAccessRoute('/product', paths)).toBe(false);
    expect(canAccessRoute('/channel-config-wizard', paths)).toBe(true);
    expect(canAccessRoute('/report/overview', paths)).toBe(true);
    expect(canAccessRoute('/403', new Set())).toBe(true);
  });

  it('调试中心必须同时满足菜单授权与运行环境开关', () => {
    const paths = new Set(['/debug']);
    expect(canAccessRoute('/debug', paths)).toBe(false);
    expect(canAccessRoute('/debug', paths, { debugCenterEnabled: false })).toBe(false);
    expect(canAccessRoute('/debug', paths, { debugCenterEnabled: true })).toBe(true);
  });

  it('标签清理保留合法子路由且永不保留瞬时 403', () => {
    const paths = new Set(['/workbench', '/channel-config', '/report/center']);
    expect(canKeepOpenedTab('/channel-config-wizard?channelCode=C001', paths)).toBe(true);
    expect(canKeepOpenedTab('/report/overview', paths)).toBe(true);
    expect(canKeepOpenedTab('/403?from=/product', paths)).toBe(false);
    expect(canKeepOpenedTab('/product', paths)).toBe(false);
  });
});
