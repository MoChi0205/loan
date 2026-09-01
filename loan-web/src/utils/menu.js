/**
 * 按完整路径或去掉查询参数后的路径查找菜单项。
 *
 * 动态菜单在初始加载或异常响应时可能不是数组，此处统一容错，
 * 避免布局初始化因 `.find is not a function` 中断。
 *
 * @param {unknown} menuList 菜单列表
 * @param {unknown} path 当前路径
 * @returns {Object|undefined} 命中的菜单项
 */
export function findMenuItem(menuList, path) {
  const list = Array.isArray(menuList) ? menuList : [];
  const targetPath = String(path || '');
  return list.find((item) => {
    const menuPath = String(item?.path || '');
    return menuPath === targetPath || menuPath.split('?')[0] === targetPath.split('?')[0];
  });
}
