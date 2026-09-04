import { describe, expect, it } from 'vitest';
import { HOME_TAB, MAX_OPEN_TABS, closeTab, sanitizeTabs, upsertTab } from '@/utils/navigationTabs';

describe('navigationTabs', () => {
  it('清理损坏、重复和无权限瞬时标签', () => {
    expect(sanitizeTabs([
      null,
      { path: '/403?from=/client', title: '无权限' },
      { path: '/client', title: '客户档案' },
      { path: '/client', title: '重复客户' },
      { path: 'bad', title: '错误路径' },
    ])).toEqual([HOME_TAB, { path: '/client', title: '客户档案' }]);
  });

  it('大量菜单连续打开时保持固定上限并保留最后目标', () => {
    let tabs = [];
    for (let i = 0; i < MAX_OPEN_TABS * 3; i += 1) {
      tabs = upsertTab(tabs, { path: `/page-${i}`, title: `页面${i}` }, `/page-${i - 1}`);
    }
    expect(tabs).toHaveLength(MAX_OPEN_TABS);
    expect(tabs[0]).toEqual(HOME_TAB);
    expect(tabs.at(-1).path).toBe(`/page-${MAX_OPEN_TABS * 3 - 1}`);
  });

  it('关闭当前标签时返回相邻页面且工作台不可关闭', () => {
    const tabs = [HOME_TAB, { path: '/lead', title: '线索' }, { path: '/client', title: '客户' }];
    expect(closeTab(tabs, '/lead')).toEqual({
      tabs: [HOME_TAB, { path: '/client', title: '客户' }],
      nextPath: '/client',
    });
    expect(closeTab(tabs, HOME_TAB.path).tabs).toEqual(tabs);
  });
});
