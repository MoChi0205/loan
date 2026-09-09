/**
 * 墨金 Ink & Gold · 统一图标路径源（单一事实来源 / Single Source of Truth）
 * ---------------------------------------------------------------
 * 规范：24×24 viewBox，stroke 1.75，圆角端点(stroke-linecap=round)、圆角连接(stroke-linejoin=round)，fill=none。
 * 着色机制：
 *   - Web 端：AppIcon 组件把 color 透传为 stroke，默认 currentColor，可接收 var(--xxx) 随主题联动。
 *   - Mini 端：AppIcon 组件在运行时把真实色值注入 stroke 并生成 data-URI（因小程序 SVG 不解析 var()）。
 * 命名：语义化英文 key，覆盖双端业务；新增图标请保持同一下面几何规范。
 */
export const ICON_PATHS = {
  dashboard: '<path d="M4 4h7v9H4zM13 4h7v5h-7zM13 11h7v9h-7zM4 15h7v5H4z"/>',
  home: '<path d="M3 11.2 12 4l9 7.2"/><path d="M5.5 10v9.5h13V10"/><path d="M10 19.5V14h4v5.5"/>',
  match: '<path d="M12 3l1.8 6.2L20 11l-6.2 1.8L12 19l-1.8-6.2L4 11l6.2-1.8z"/>',
  report: '<path d="M6 3h8l4 4v14H6z"/><path d="M14 3v4h4"/><path d="M9 12h6M9 15h6M9 18h4"/>',
  order: '<rect x="5" y="4" width="14" height="17" rx="2"/><path d="M9 4V3h6v1"/><path d="M8 10h8M8 14h8M8 18h5"/>',
  client: '<circle cx="9" cy="8" r="3"/><path d="M3.5 19a5.5 5.5 0 0 1 11 0"/><path d="M16 6a3 3 0 0 1 0 6"/><path d="M16.5 13.2a5.5 5.5 0 0 1 4 5.8"/>',
  approval: '<circle cx="12" cy="12" r="8.5"/><path d="M8.5 12.3l2.4 2.4 4.6-5"/>',
  product: '<path d="M12 3l8 4.5v9L12 21l-8-4.5v-9z"/><path d="M4 7.5 12 12l8-4.5"/><path d="M12 12v9"/>',
  search: '<circle cx="10.5" cy="10.5" r="6"/><path d="M15 15l5 5"/>',
  user: '<circle cx="12" cy="8" r="3.5"/><path d="M5 20a7 7 0 0 1 14 0"/>',
  settings: '<circle cx="12" cy="12" r="3"/><path d="M12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3M5 5l2.1 2.1M17 17l2.1 2.1M19 5l-2.1 2.1M7 17l-2.1 2.1"/>',
  bell: '<path d="M6 9a6 6 0 0 1 12 0c0 5 1.5 6 1.5 6H4.5S6 14 6 9z"/><path d="M10 20a2 2 0 0 0 4 0"/>',
  filter: '<path d="M4 5h16l-6 7v6l-4 2v-8z"/>',
  chart: '<path d="M4 4v16h16"/><path d="M7.5 14l3-3 2.5 2 4-5"/>',
  bank: '<path d="M3 9 12 4l9 5"/><path d="M5 9v9M9 9v9M15 9v9M19 9v9"/><path d="M3 20h18"/>',
  money: '<circle cx="12" cy="12" r="8.5"/><path d="M8.5 8.5 12 13l3.5-4.5M12 13v4M9.5 15h5"/>',
  lock: '<rect x="5" y="10" width="14" height="10" rx="2"/><path d="M8 10V7a4 4 0 0 1 8 0v3"/>',
  clock: '<circle cx="12" cy="12" r="8.5"/><path d="M12 7v5l3.5 2"/>',
  document: '<path d="M6 3h8l4 4v14H6z"/><path d="M14 3v4h4"/>',
  warning: '<path d="M12 3 22 20H2z"/><path d="M12 9v5M12 17h.01"/>',
  check: '<path d="M4 12.5 9 17.5 20 6.5"/>',
  close: '<path d="M6 6l12 12M18 6 6 18"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  arrowRight: '<path d="M5 12h13M13 6l6 6-6 6"/>',
  arrowDown: '<path d="M12 5v13M6 13l6 6 6-6"/>',
  refresh: '<path d="M4 12a8 8 0 0 1 13.7-5.6L20 8"/><path d="M20 4v4h-4"/><path d="M20 12a8 8 0 0 1-13.7 5.6L4 16"/><path d="M4 20v-4h4"/>',
  download: '<path d="M12 4v11M7 11l5 5 5-5"/><path d="M5 20h14"/>',
  upload: '<path d="M12 20V9M7 13l5-5 5 5"/><path d="M5 4h14"/>',
  eye: '<path d="M2 12s4-7 10-7 10 7 10 7-4 7-10 7-10-7-10-7z"/><circle cx="12" cy="12" r="3"/>',
  edit: '<path d="M4 20h4L19 9l-4-4L4 16z"/><path d="M14 6l4 4"/>',
  delete: '<path d="M5 7h14M9 7V5h6v2M6 7l1 13h10l1-13"/>',
  menu: '<path d="M4 7h16M4 12h16M4 17h16"/>',
  shield: '<path d="M12 3 20 6v6c0 5-4 8-8 9-4-1-8-4-8-9V6z"/><path d="M9 12l2 2 4-4"/>',
  trendUp: '<path d="M4 16l5-5 4 3 7-7"/><path d="M16 7h4v4"/>',
  wechat: '<path d="M9 4C5.1 4 2 6.6 2 9.8c0 1.8.9 3.3 2.4 4.4L3.5 16.5l3-1.5c.6.2 1.3.3 2 .3"/><path d="M22 14.2c0-2.7-2.7-4.9-6-4.9s-6 2.2-6 4.9 2.7 4.9 6 4.9c.7 0 1.4-.1 2-.4l3 1.6-.8-2.4c1.4-1 2.3-2.4 2.3-3.7z"/>',
  bolt: '<path d="M13 2 4 14h7l-1 8 9-12h-7z"/>',
  org: '<circle cx="12" cy="6" r="2.5"/><circle cx="6" cy="17" r="2.5"/><circle cx="18" cy="17" r="2.5"/><path d="M12 8.5v3M10.2 12.5 7 15M13.8 12.5 17 15"/>',
  sms: '<path d="M4 5h16v11H9l-4 4z"/><path d="M8 9h8M8 12h5"/>',
  audit: '<path d="M6 3h8l4 4v14H6z"/><path d="M14 3v4h4"/><circle cx="10" cy="13" r="2"/><path d="M11.5 14.5 14 17"/>',
  reward: '<circle cx="12" cy="9" r="5.5"/><path d="M9 13.5 7.5 21l4.5-2.5L16.5 21 15 13.5"/><path d="M12 6.5v5M9.5 9h5"/>',
  logout: '<path d="M14 4H7v16h7"/><path d="M14 12h7M18 9l3 3-3 3"/>',
  blacklist: '<circle cx="12" cy="12" r="8.5"/><path d="M6.5 6.5l11 11"/>',
  pie: '<circle cx="12" cy="12" r="8.5"/><path d="M12 12V3.5A8.5 8.5 0 0 1 20.5 12z"/>',
  info: '<circle cx="12" cy="12" r="8.5"/><path d="M12 11v5M12 8h.01"/>',
  error: '<circle cx="12" cy="12" r="8.5"/><path d="M9 9l6 6M15 9l-6 6"/>'
};

export const ICON_NAMES = Object.keys(ICON_PATHS);
