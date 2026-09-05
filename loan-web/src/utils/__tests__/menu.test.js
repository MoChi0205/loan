import { describe, expect, it } from 'vitest';
import { findMenuItem } from '@/utils/menu';

describe('findMenuItem', () => {
  const menus = [
    { path: '/workbench', title: '工作台' },
    { path: '/product?source=menu', title: '产品库' },
  ];

  it('按完整路径查找菜单', () => {
    expect(findMenuItem(menus, '/workbench')?.title).toBe('工作台');
  });

  it('忽略查询参数匹配同一页面', () => {
    expect(findMenuItem(menus, '/product?source=tab')?.title).toBe('产品库');
  });

  it('菜单异常为对象或空值时安全返回', () => {
    expect(findMenuItem({}, '/workbench')).toBeUndefined();
    expect(findMenuItem(null, '/workbench')).toBeUndefined();
  });
});
