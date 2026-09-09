/**
 * 小程序主题切换（墨金 Ink & Gold）—— 与 loan-web 对齐的明暗双主题。
 *
 * <p>小程序 SVG 的 stroke 不解析 CSS 变量，故 AppIcon / TabBar 在运行时读取当前 mode
 * 注入真实色值（详见 components/AppIcon.vue、components/TabBar.vue）。
 *
 * <p>持久化：uni.setStorageSync('loan_theme_mode')，下次启动自动恢复。
 * H5 端额外作用于 document.documentElement[data-theme] 与 .uni-page-body[data-theme]，
 * 使 [data-theme="dark"] 选择器生效。
 *
 * <p>响应式：mode 为模块级单例 ref，所有页面/组件共享同一响应式对象；
 * 任一页面调用 toggleThemeMode 后，其它页面绑定的 :data-theme 会同步更新。
 */
import { ref } from 'vue';

const KEY = 'loan_theme_mode';

function readInitial() {
  try {
    const v = uni.getStorageSync(KEY);
    return v === 'dark' ? 'dark' : 'light';
  } catch (e) {
    return 'light';
  }
}

// 模块级单例：所有页面/组件共享同一响应式主题
const mode = ref(readInitial());

const listeners = [];

/**
 * H5 端：把 data-theme 写到 <html> 与 .uni-page-body 两处。
 * uni-app H5 会把 App.vue 全局样式里的 `page` 选择器编译到 `.uni-page-body`，
 * 暗色令牌块 `[data-theme="dark"]` 须命中该元素才能覆盖 `page {}` 上的浅色令牌。
 * 小程序端无 document，此函数为空操作（由页面根视图的 :data-theme 承担）。
 */
function applyDomTheme(root) {
  // #ifdef H5
  if (typeof document === 'undefined' || !document.documentElement) return;
  document.documentElement.setAttribute('data-theme', root);
  const body = document.querySelector('.uni-page-body') || document.querySelector('page');
  if (body) body.setAttribute('data-theme', root);
  // #endif
}

/** 读取当前主题模式（非响应式，供一次性读取） */
export function getThemeMode() {
  return mode.value;
}

/** 响应式主题模式（ref）：组件 setup 中 `const themeMode = useThemeMode();` 后模板绑定 :data-theme="themeMode" */
export function useThemeMode() {
  return mode;
}

/** 设置主题模式（持久化 + 通知 + H5 根节点标记） */
export function setThemeMode(next) {
  const target = next === 'dark' ? 'dark' : 'light';
  // 即便值未变也兜底刷新 H5 标记，保证首屏命中
  if (target === mode.value) {
    applyDomTheme(target);
    return target;
  }
  mode.value = target;
  try {
    uni.setStorageSync(KEY, target);
  } catch (e) {
    /* 忽略存储异常 */
  }
  applyDomTheme(target);
  listeners.forEach((fn) => {
    try {
      fn(target);
    } catch (e) {
      /* 忽略监听器异常 */
    }
  });
  return target;
}

/** 明暗互切 */
export function toggleThemeMode() {
  return setThemeMode(mode.value === 'dark' ? 'light' : 'dark');
}

/**
 * 订阅主题变化。
 * @returns {Function} 取消订阅函数（组件卸载时调用，避免监听器泄漏）
 */
export function onThemeChange(fn) {
  if (typeof fn !== 'function') return () => {};
  listeners.push(fn);
  return () => {
    const i = listeners.indexOf(fn);
    if (i >= 0) listeners.splice(i, 1);
  };
}

/** App 启动时应用已保存的主题（H5 端写入根节点 + 页面容器标记） */
export function applyThemeOnLaunch() {
  applyDomTheme(mode.value);
  // #ifdef H5
  // onLaunch 时 .uni-page-body 尚未挂载，下一帧补设，确保首屏即命中暗色块
  if (typeof requestAnimationFrame === 'function') {
    requestAnimationFrame(() => applyDomTheme(mode.value));
  } else if (typeof setTimeout === 'function') {
    setTimeout(() => applyDomTheme(mode.value), 0);
  }
  // #endif
}
