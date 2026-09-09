<template>
  <view
    class="app-icon"
    :class="[`icon-${size}`, 'is-plain']"
    aria-hidden="true"
  >
    <!--
      v8 渲染策略：CSS background-image + SVG data URI
      历史：
        v6 <image src=data:image/svg+xml + currentColor → mp-weixin 渲染为实心圆
        v7 <image src=data:image/svg+xml + stroke 真实色 → 部分 mp-weixin 不解析 fill="none"，
           且 viewBox 无 width/height 时画布退回 300×150 撑出奇怪缩放，图标变成不可见的细线
      v8：
        1) 改用 <view> + CSS background-image：走 CSS 渲染管线，对 SVG 支持稳定
        2) SVG 内补 width/height="24" + xmlns：给 mp-weixin 一个明确画布尺寸
        3) background-size: contain 保留 viewBox 比例缩放
        4) 颜色仍然内联到 stroke（绕开 currentColor 继承坑）
    -->
    <view class="ico-bg" :style="{ backgroundImage: `url('${src}')` }" />
  </view>
</template>

<script setup>
import { computed, ref, onUnmounted } from 'vue';
import { getThemeMode, onThemeChange } from '../theme';

/**
 * 图标组件 v8（SVG data URI + CSS background-image 版）
 *
 * 变更动机（v7 → v8）：
 * v7 用 <image src="data:image/svg+xml;…"> 加载 SVG，问题是：
 *   - mp-weixin <image> 对 SVG data URI 支持不稳定：部分版本不解析 fill="none"（渲染为实心），
 *     部分版本忽略 viewBox 用默认 300×150 画布（图标被裁/被缩成线）
 *   - 用户 2026-09-09 反馈「图标怎么又没了」：可见但只显示成细线/不显示
 *
 * v8 修复：
 *   - 把渲染从 <image> 换成 <view> + CSS background-image：CSS 渲染管线对 SVG 支持稳定得多
 *   - SVG 元素补 width="24" height="24"，避免 mp-weixin 退回默认画布
 *   - 颜色仍走 prop.color 内联（绕开 currentColor 继承坑）；未传默认深字（浅底可见）
 *
 * 字形规范：24×24 viewBox，stroke-width=1.75，stroke-linecap/linejoin=round。
 *
 * 28 枚：home / match / chart / order / bank / users / mine / check / search /
 * wechat / bolt / support / list / share / enterprise / person / refresh /
 * alert / trend / doc / file / photo / lock / arrow / workbench / credit /
 * shield / clock
 */
const props = defineProps({
  name: { type: String, default: 'home' },
  size: { type: String, default: 'md' },
  color: { type: String, default: '' },
});

/**
 * 颜色：直接透传 prop.color（含 var(--…) 令牌 / 十六进制 / rgb），未传默认深字。
 * 注意：调用方**禁止**传 var(--…)——SVG stroke 不解析 CSS 变量，
 * 必须传真实色值（#RRGGBB / rgba()）；不识别则原样传入（兜底）。
 */
// 浅色主题默认：墨蓝字色（浅底上图标可见）
const RESOLVED_LIGHT = '#16203A';
// 暗色主题默认：浅字色（墨金暗底 #0E1626 / #131E33 上图标可见）
const RESOLVED_DARK = '#E8EDF5';

// 订阅主题变化：未显式传 props.color 的图标随明暗切换自动改色。
// 原因：小程序 SVG stroke 不解析 CSS 变量（var()），必须注入真实色值，故需在运行时读取 mode。
const themeMode = ref(getThemeMode());
const offTheme = onThemeChange((m) => { themeMode.value = m; });
onUnmounted(() => { if (offTheme) offTheme(); });

const resolvedColor = computed(() => {
  if ((props.color || '').trim()) return props.color.trim();
  return themeMode.value === 'dark' ? RESOLVED_DARK : RESOLVED_LIGHT;
});

/* SVG 源：所有图均为 24×24 viewBox + stroke-width 1.75 + 圆头
   关键策略：width/height=24 显式声明 + fill="none" + stroke=真实色值（不用 currentColor）
   这样 CSS background-image 加载的 SVG 文档有明确尺寸，按 viewBox 比例 contain 缩放 */
const SVGS = {
  home: 'M3 11 L12 3 L21 11 V20 A1 1 0 0 1 20 21 H15 V14 H9 V21 H4 A1 1 0 0 1 3 20 Z',
  match: 'M12 3 A9 9 0 1 0 12 21 A9 9 0 1 0 12 3 Z M12 7 A5 5 0 1 1 12 17 A5 5 0 1 1 12 7 Z M12 10 A2 2 0 1 0 12 14 A2 2 0 1 0 12 10 Z',
  chart: 'M4 20 H20 M7 16 V11 M12 16 V6 M17 16 V13',
  order: 'M6 3 H18 A1 1 0 0 1 19 4 V20 A1 1 0 0 1 18 21 H6 A1 1 0 0 1 5 20 V4 A1 1 0 0 1 6 3 Z M9 8 H15 M9 12 H15 M9 16 H13',
  bank: 'M3 9 L12 3 L21 9 V11 H3 Z M5 12 V19 M9 12 V19 M15 12 V19 M19 12 V19 M3 21 H21',
  users: 'M9 11 A4 4 0 1 0 9 3 A4 4 0 0 0 9 11 Z M2 21 V19 A4 4 0 0 1 6 15 H12 A4 4 0 0 1 16 19 V21 M17 11 A4 4 0 1 0 17 3 M16 15 A4 4 0 0 1 20 19 V21',
  mine: 'M12 12 A4 4 0 1 0 12 4 A4 4 0 0 0 12 12 Z M4 21 V19 A8 8 0 0 1 20 19 V21',
  check: 'M5 12 L10 17 L19 7',
  search: 'M11 4 A7 7 0 1 0 11 18 A7 7 0 0 0 11 4 Z M16 16 L21 21',
  wechat: 'M9 4 C5 4 2 7 2 10 C2 12 3 13 4 14 L3 17 L6 16 C7 16 8 16 9 16 M15 9 C18 9 21 11 21 14 C21 16 20 17 19 18 L20 21 L17 20 C16 20 15 20 15 20 C15 17 12 15 9 15',
  bolt: 'M13 3 L4 14 H11 L10 21 L20 10 H13 Z',
  support: 'M12 4 A8 8 0 0 0 4 12 V16 A2 2 0 0 0 6 18 H8 V12 H6 A6 6 0 0 1 12 6 A6 6 0 0 1 18 12 H16 V18 H18 A2 2 0 0 0 20 16 V12 A8 8 0 0 0 12 4 Z',
  list: 'M8 6 H21 M8 12 H21 M8 18 H21 M3 6 H4 M3 12 H4 M3 18 H4',
  share: 'M8 12 L16 6 M8 12 L16 18 M6 12 A2 2 0 1 0 6 12.01 Z M18 6 A2 2 0 1 0 18 6.01 Z M18 18 A2 2 0 1 0 18 18.01 Z',
  enterprise: 'M3 21 H21 M5 21 V9 L12 4 L19 9 V21 M9 21 V14 H15 V21 M9 11 H10 M14 11 H15 M9 8 H10 M14 8 H15',
  person: 'M12 4 A4 4 0 1 0 12 12 A4 4 0 0 0 12 4 Z M4 21 V19 A8 8 0 0 1 20 19 V21',
  refresh: 'M4 4 V10 H10 M20 20 V14 H14 M5 14 A8 8 0 0 0 19 18 M19 10 A8 8 0 0 0 5 6',
  alert: 'M12 4 L22 20 H2 Z M12 10 V15 M12 17 V18',
  trend: 'M3 17 L9 11 L13 15 L21 7 M15 7 H21 V13',
  doc: 'M7 3 H15 L19 7 V21 A1 1 0 0 1 18 22 H7 A1 1 0 0 1 6 21 V4 A1 1 0 0 1 7 3 Z M9 12 H16 M9 16 H14 M9 8 H12',
  file: 'M14 3 V8 H19 M14 3 H6 V21 H19 V8 Z M9 13 H16 M9 17 H13',
  photo: 'M3 6 H8 L10 4 H14 L16 6 H21 V19 A1 1 0 0 1 20 20 H4 A1 1 0 0 1 3 19 Z M12 16 A4 4 0 1 0 12 8 A4 4 0 0 0 12 16 Z',
  lock: 'M6 11 V8 A6 6 0 0 1 18 8 V11 M5 11 H19 V20 A1 1 0 0 1 18 21 H6 A1 1 0 0 1 5 20 Z M12 14 V17',
  arrow: 'M5 12 H19 M13 6 L19 12 L13 18',
  workbench: 'M3 6 H21 V18 H3 Z M3 10 H21 M8 14 H10 M14 14 H16',
  credit: 'M3 6 H21 V18 H3 Z M3 10 H21 M7 15 H10',
  shield: 'M12 3 L20 6 V12 A8 8 0 0 1 12 21 A8 8 0 0 1 4 12 V6 Z M9 12 L11 14 L15 10',
  clock: 'M12 4 A8 8 0 1 0 12 20 A8 8 0 0 0 12 4 Z M12 7 V12 L16 14',
};

function buildSvg(name, color) {
  const pathData = SVGS[name] || SVGS.home;
  // 关键：width/height 显式声明，让 mp-weixin 拿到 intrinsic 尺寸；
  // stroke 写死真实色值，不用 currentColor；fill="none" 保证只描边不填充
  // 注意：SVGS 存的是 path 的 d 属性值，必须包在 <path d="..."/> 里才是合法 SVG。
  return `<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="${color}" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><path d="${pathData}"/></svg>`;
}

function toBase64(str) {
  // H5 用 btoa；mp-weixin 旧基础库 fallback 到 uni.arrayBufferToBase64
  if (typeof btoa === 'function') return btoa(str);
  if (typeof uni !== 'undefined' && uni.arrayBufferToBase64) {
    const buf = new Uint8Array(str.length);
    for (let i = 0; i < str.length; i++) buf[i] = str.charCodeAt(i);
    return uni.arrayBufferToBase64(buf.buffer);
  }
  // 最后兜底：纯 JS base64（极小 polyfill）
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
  let out = '';
  for (let i = 0; i < str.length; i += 3) {
    const a = str.charCodeAt(i);
    const b = i + 1 < str.length ? str.charCodeAt(i + 1) : 0;
    const c = i + 2 < str.length ? str.charCodeAt(i + 2) : 0;
    out += chars[(a >> 2) & 0x3F];
    out += chars[((a & 0x03) << 4) | ((b >> 4) & 0x0F)];
    out += i + 1 < str.length ? chars[((b & 0x0F) << 2) | ((c >> 6) & 0x03)] : '=';
    out += i + 2 < str.length ? chars[c & 0x3F] : '=';
  }
  return out;
}

const src = computed(() => {
  const svg = buildSvg(props.name, resolvedColor.value);
  // base64 data URI 在 H5/小程序渲染管线中最稳定（避免 url-encoded data URI 被部分浏览器截断）
  return `data:image/svg+xml;base64,${toBase64(svg)}`;
});
</script>

<style scoped>
.app-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  line-height: 0;
}
.icon-sm { width: 32rpx; height: 32rpx; }
.icon-md { width: 48rpx; height: 48rpx; }
.icon-lg { width: 64rpx; height: 64rpx; }
.icon-xl { width: 88rpx; height: 88rpx; }

/* 单色（plain）模式：透明、无磁贴底、无投影 */
.is-plain {
  background: transparent;
  border: none;
  box-shadow: none;
  padding: 0;
}

/* v8：用 <view> + CSS background-image 渲染 SVG
   background-size: contain + background-repeat: no-repeat 保留 viewBox 比例缩放 */
.ico-bg {
  width: 100%;
  height: 100%;
  background-repeat: no-repeat;
  background-position: center center;
  background-size: contain;
}
</style>
