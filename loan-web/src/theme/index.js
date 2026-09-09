/**
 * 主题系统：支持运行时手动切换（明/暗 + 主色），localStorage 持久化 + 环境变量兜底。
 *
 * <p>优先级：localStorage 用户选择 > .env 环境默认。
 *
 * <p>设计语言：深色为「深蓝金融高对比暗色」，浅色为「浅色专业商务」。
 * 明/暗语义一致——文字高对比、面板/标签边界清晰。
 */
import { KEYS, getStorage, setStorage, removeStorage } from '@/utils/storage';

/** 十六进制颜色转 RGB 分量数组 */
function hexToRgb(hex) {
  const h = String(hex || '').replace('#', '');
  if (!/^[0-9a-fA-F]{6}$/.test(h)) {
    return [59, 130, 246]; // 兜底主色 #3b82f6
  }
  return [parseInt(h.slice(0, 2), 16), parseInt(h.slice(2, 4), 16), parseInt(h.slice(4, 6), 16)];
}

/** 生成 rgba 字符串 */
function rgba(rgb, alpha) {
  return `rgba(${rgb[0]}, ${rgb[1]}, ${rgb[2]}, ${alpha})`;
}

/** 生成主色到强调色的渐变 */
function gradient(primaryRgb, accentRgb, angle = '135deg') {
  return `linear-gradient(${angle}, rgb(${primaryRgb.join(',')}), rgb(${accentRgb.join(',')}))`;
}

/** 颜色提亮（向白靠拢，用于 hover） */
function lighten(rgb, ratio) {
  return rgb.map((c) => Math.round(c + (255 - c) * ratio));
}

/** 颜色加深（向黑靠拢，用于 hover/active） */
function darken(rgb, ratio) {
  return rgb.map((c) => Math.round(c * (1 - ratio)));
}

/** 当前主题状态 */
let current = {
  mode: 'dark',
  primary: '#D9A441', // 墨金 · 暖金（主强调色：按钮 / 激活 / 高亮）
  accent: '#4C7BD9',  // 墨金 · 信息蓝（渐变第二色 / info，语义色）
};

/** 读取用户偏好（localStorage） */
function readPref(key) {
  return getStorage(key);
}

/** 写入用户偏好 */
function writePref(key, value) {
  if (value == null) {
    removeStorage(key);
  } else {
    setStorage(key, value);
  }
}

/**
 * 应用主题变量到 document.documentElement。
 * @param {string} mode 明/暗（light/dark）
 * @param {string} primary 品牌主色
 * @param {string} accent 强调色
 */
function applyVars(mode, primary, accent) {
  const primaryRgb = hexToRgb(primary);
  const accentRgb = hexToRgb(accent);
  const isDark = mode === 'dark';
  const root = document.documentElement;

  root.setAttribute('data-theme', mode);

  // hover/active：暗色提亮，浅色加深
  const primaryHover = isDark ? lighten(primaryRgb, 0.12) : darken(primaryRgb, 0.08);
  const primaryActive = isDark ? lighten(primaryRgb, 0.2) : darken(primaryRgb, 0.16);

  const vars = {
    // 品牌色
    '--loan-primary': primary,
    '--loan-primary-hover': `rgb(${primaryHover.join(',')})`,
    '--loan-primary-active': `rgb(${primaryActive.join(',')})`,
    '--loan-primary-soft': rgba(primaryRgb, isDark ? 0.16 : 0.08),
    '--loan-primary-deep': rgba(primaryRgb, isDark ? 0.26 : 0.14),

    // 强调色
    '--loan-accent': accent,
    '--loan-accent-soft': rgba(accentRgb, isDark ? 0.16 : 0.1),

    // 主渐变
    '--loan-gradient': gradient(primaryRgb, accentRgb),

    // 表面与背景（墨金 Ink & Gold：深墨蓝承载 + 暖金强调）
    '--loan-bg': isDark ? '#0E1626' : '#F4F6FA',
    '--loan-card-bg': isDark ? '#131E33' : '#FFFFFF',
    '--loan-surface': isDark ? '#1B2945' : '#EEF2F8',

    // 语义强调令牌（墨金二元：墨蓝 + 暖金）
    '--loan-ink': isDark ? '#0E1626' : '#16203A',
    '--loan-gold': primary,
    '--loan-gold-strong': isDark ? '#E0AE4E' : '#B8861A',

    // 边框
    '--loan-border': isDark ? '#26344F' : '#E2E8F2',
    '--loan-border-strong': isDark ? '#3A4D6E' : '#CBD5E6',

    // 文本（高对比）
    '--loan-text': isDark ? '#E8EDF5' : '#16203A',
    '--loan-text-secondary': isDark ? '#B6C2D6' : '#3A465C',
    '--loan-text-muted': isDark ? '#93A0B8' : '#6B7689',

    // 语义色（墨金语义色板）
    '--loan-success': isDark ? '#3FB98A' : '#2FA37A',
    '--loan-warning': isDark ? '#E0A441' : '#C98A2B',
    '--loan-danger': isDark ? '#E0877D' : '#D8695F',
    '--loan-info': isDark ? '#6E9BE0' : '#4C7BD9',

    // 语义柔和色（callout/徽标软底，暗色低透明叠加 / 浅色浅底）
    '--loan-success-bg': isDark ? 'rgba(47, 163, 122, 0.16)' : '#E7F4EF',
    '--loan-success-line': isDark ? 'rgba(47, 163, 122, 0.38)' : '#A7D8C4',
    '--loan-success-text': isDark ? '#6EE7B7' : '#1F7A5A',
    '--loan-warning-bg': isDark ? 'rgba(224, 164, 65, 0.16)' : '#FBF1DE',
    '--loan-warning-line': isDark ? 'rgba(224, 164, 65, 0.38)' : '#EBD2A0',
    '--loan-warning-text': isDark ? '#F2C879' : '#9A6B1E',
    '--loan-danger-bg': isDark ? 'rgba(216, 105, 95, 0.16)' : '#FBEDEB',
    '--loan-danger-line': isDark ? 'rgba(216, 105, 95, 0.38)' : '#EBC3BD',
    '--loan-danger-text': isDark ? '#F0A89F' : '#B0493F',
    '--loan-info-bg': isDark ? 'rgba(76, 123, 217, 0.16)' : '#EAEEF9',
    '--loan-info-line': isDark ? 'rgba(76, 123, 217, 0.38)' : '#B9C6EC',
    '--loan-info-text': isDark ? '#9FBEF0' : '#2E5BB0',

    // 分类强调色（仪表盘多指标区分，稳定品牌邻近色）
    '--loan-cat-indigo': isDark ? '#818cf8' : '#6366f1',
    '--loan-cat-teal': isDark ? '#2dd4bf' : '#14b8a6',
    '--loan-cat-orange': isDark ? '#fb923c' : '#f97316',
    '--loan-cat-purple': isDark ? '#c084fc' : '#a855f7',

    // 侧栏
    '--loan-sider-bg': isDark ? '#0E1626' : '#FFFFFF',
    '--loan-sider-text': isDark ? '#B6C2D6' : '#3A465C',
    '--loan-sider-active-bg': rgba(primaryRgb, isDark ? 0.18 : 0.10),
    '--loan-sider-active-text': isDark ? '#F2C879' : '#B8861A',
    '--loan-sider-active-bar': primary,
  };

  Object.entries(vars).forEach(([key, value]) => {
    root.style.setProperty(key, value);
  });

  // 注入 Element Plus 主色变量（浅阶色与白色混色、深阶色与黑色混色，均为不透明色）
  root.style.setProperty('--el-color-primary', primary);
  root.style.setProperty('--el-color-primary-light-3', `rgb(${lighten(primaryRgb, 0.3).join(',')})`);
  root.style.setProperty('--el-color-primary-light-5', `rgb(${lighten(primaryRgb, 0.5).join(',')})`);
  root.style.setProperty('--el-color-primary-light-7', `rgb(${lighten(primaryRgb, 0.7).join(',')})`);
  root.style.setProperty('--el-color-primary-light-8', `rgb(${lighten(primaryRgb, 0.8).join(',')})`);
  root.style.setProperty('--el-color-primary-light-9', `rgb(${lighten(primaryRgb, 0.9).join(',')})`);
  root.style.setProperty('--el-color-primary-dark-2', `rgb(${darken(primaryRgb, 0.2).join(',')})`);
}

/**
 * 应用主题：读取环境变量 + localStorage 偏好，写入 CSS 变量。
 * 在 main.js 挂载前调用一次即可。
 */
export function applyTheme() {
  const mode = readPref(KEYS.THEME_MODE) || import.meta.env.VITE_THEME_MODE || 'dark';
  const primary = readPref(KEYS.THEME_PRIMARY) || import.meta.env.VITE_THEME_PRIMARY || '#D9A441';
  const accent = readPref(KEYS.THEME_ACCENT) || import.meta.env.VITE_THEME_ACCENT || '#4C7BD9';
  current = {
    mode: mode === 'light' ? 'light' : 'dark',
    primary,
    accent,
  };
  applyVars(current.mode, current.primary, current.accent);
}

/** 切换明/暗模式（持久化） */
export function setThemeMode(mode) {
  current.mode = mode === 'light' ? 'light' : 'dark';
  writePref(KEYS.THEME_MODE, current.mode);
  applyVars(current.mode, current.primary, current.accent);
}

/** 切换品牌主色（持久化） */
export function setThemePrimary(primary) {
  current.primary = primary;
  writePref(KEYS.THEME_PRIMARY, primary);
  applyVars(current.mode, current.primary, current.accent);
}

/** 切换强调色（持久化） */
export function setThemeAccent(accent) {
  current.accent = accent;
  writePref(KEYS.THEME_ACCENT, accent);
  applyVars(current.mode, current.primary, current.accent);
}

/** 读取当前主题状态 */
export function getTheme() {
  return { ...current };
}
