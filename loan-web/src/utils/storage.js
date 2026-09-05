/**
 * 本地存储统一封装（前端工具类）。
 *
 * <p>统一 localStorage 访问：try/catch 兜底（隐私模式 / 容量满 / 非浏览器环境不崩溃），
 * 并提供 JSON 便捷读写。所有业务 key 集中到 KEYS 常量管理，避免字符串散落各处。
 */
export const KEYS = {
  /** 登录令牌（JWT） */
  TOKEN: 'token',
  /** 当前登录用户信息 JSON */
  USER: 'loan_user',
  /** 枚举字典缓存 JSON */
  DICT: 'loan:dict:v1',
  /** 登录页记住账号 */
  REMEMBER_USERNAME: 'remember_username',
  /** 主题偏好（明/暗 + 主色/强调色） */
  THEME_MODE: 'loan_theme_mode',
  THEME_PRIMARY: 'loan_theme_primary',
  THEME_ACCENT: 'loan_theme_accent',
  /** 布局：多标签栏 + 菜单分组展开状态 */
  LAYOUT_TABS: 'loan:openTabs',
  LAYOUT_GROUP: 'loan:menuGroupExpanded',
};

/** 存储不可用时的内存降级区（会话内有效） */
const fallback = new Map();

/** 读字符串：localStorage 优先，异常/缺失时回退内存降级区 */
export function getStorage(key) {
  try {
    const v = localStorage.getItem(key);
    if (v !== null) return v;
  } catch (e) { /* 落到降级区 */ }
  return fallback.has(key) ? fallback.get(key) : null;
}

/** 写字符串：写入 localStorage；失败（隐私模式/容量满）时降级到内存 */
export function setStorage(key, value) {
  try {
    localStorage.setItem(key, value);
    fallback.delete(key);
  } catch (e) {
    fallback.set(key, value);
  }
}

/** 删除：localStorage + 降级区双清 */
export function removeStorage(key) {
  try {
    localStorage.removeItem(key);
  } catch (e) { /* ignore */ }
  fallback.delete(key);
}

/** 读 JSON：自动 parse，损坏/缺失返回 defaultValue */
export function getStorageJSON(key, defaultValue = null) {
  try {
    const raw = getStorage(key);
    if (raw === null || raw === undefined || raw === '') return defaultValue;
    return JSON.parse(raw);
  } catch (e) {
    return defaultValue;
  }
}

/** 写 JSON：序列化后存储 */
export function setStorageJSON(key, value) {
  setStorage(key, JSON.stringify(value));
}
