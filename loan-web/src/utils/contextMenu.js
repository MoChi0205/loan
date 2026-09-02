/**
 * 全局右键菜单（AppContextMenu 的调用入口）
 *
 * 用法（任意组件内）：
 *   import { openContextMenu } from '@/utils/contextMenu';
 *
 *   function onRowContextMenu(ev, row) {
 *     openContextMenu(ev, [
 *       { label: '查看详情', icon: 'view', onClick: () => openDetail(row) },
 *       { divider: true },
 *       { label: '删除', danger: true, disabled: row.locked, onClick: () => del(row) },
 *     ]);
 *   }
 *
 * 菜单项字段：
 *   label   显示文案
 *   icon    AppIcon registry 名称，可选
 *   danger  红色危险样式
 *   disabled 禁用态（不可点击）
 *   divider true 渲染分隔线
 *   onClick 点击回调（点击后自动关闭菜单）
 *
 * 关闭时机：点击外部 / ESC / 窗口失焦 / 点击菜单项后，自动处理。
 */
import { reactive } from 'vue';

/** 菜单状态（单例，全局唯一实例消费） */
export const contextMenuState = reactive({
  open: false,
  x: 0,
  y: 0,
  items: [],
});

/**
 * 打开右键菜单
 * @param {MouseEvent} ev 触发事件（用其 clientX/clientY 定位）
 * @param {Array} items 菜单项
 */
export function openContextMenu(ev, items) {
  if (ev && ev.preventDefault) ev.preventDefault();
  if (ev && ev.stopPropagation) ev.stopPropagation();
  const vw = window.innerWidth;
  const vh = window.innerHeight;
  const w = 176;
  // 高度按项数估算：每项 ~38px + 内边距/分隔线
  const h = Math.min(240, items.length * 38 + 20);
  let x = ev.clientX;
  let y = ev.clientY;
  if (x + w > vw) x = vw - w - 8;
  if (y + h > vh) y = vh - h - 8;
  contextMenuState.x = Math.max(4, x);
  contextMenuState.y = Math.max(4, y);
  contextMenuState.items = items || [];
  contextMenuState.open = true;
}

/** 关闭菜单 */
export function closeContextMenu() {
  contextMenuState.open = false;
  contextMenuState.items = [];
}
