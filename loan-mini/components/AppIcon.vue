<template>
  <view class="app-icon" :class="[`icon-${size}`]">
    <image class="ico-img" :src="src" mode="aspectFit" aria-hidden="true" />
  </view>
</template>

<script setup>
import { computed } from 'vue';

/**
 * 图标组件 v3（PNG 资源版，兼容微信小程序）。
 *
 * <p>v2 用内联 <svg> 渲染——微信小程序 WXML 不支持内联 SVG 标签，
 * 导致 mp-weixin 端所有图标（含 TabBar、首页、认证页）渲染为空白。
 * v3 改为构建期把同一套矢量字形栅格化为 144×144 高清 PNG
 * （static/icons/，单文件约 5KB，远低于分包 200K 限制），
 * 运行时经 <image> 渲染：mp-weixin / H5 双端一致、无限缩放不虚。
 *
 * <p>两种形态：
 * - 磁贴（默认）：品牌色圆角磁贴 + 白色线性字形，用于内容功能入口
 *   （认证卡片、首页快捷功能、数据卡片等）。
 * - 单色 plain：传入 color 属性时启用，纯线性字形，用于 TabBar、
 *   搜索栏等需要跟随语义色/中性色的场景。
 *
 * <p>可用 name：home / match / chart / order / bank / users / mine / check /
 * search / wechat / bolt / support / list / share / enterprise / person /
 * refresh / alert / trend / doc / file / photo / lock / arrow
 *
 * 用法：
 *   <AppIcon name="enterprise" size="lg" />                      → 磁贴
 *   <AppIcon name="home" size="md" color="var(--brand-deep)" />  → 单色
 */
const props = defineProps({
  name: { type: String, default: 'home' },
  /** 尺寸：sm(32rpx) / md(48rpx) / lg(64rpx) */
  size: { type: String, default: 'md' },
  /** 传入则渲染单色字形；支持设计令牌与常用色值，未知色回退中性灰 */
  color: { type: String, default: '' },
});

/**
 * color → plain 资源后缀映射。
 * PNG 无法运行时换色，故按「实际会被传入的色值」预生成（见
 * static/icons/<name>-<key>.png）；未命中回退 gray（--text-secondary）。
 */
const COLOR_KEY = {
  'var(--brand-deep)': 'brand', '#2443C2': 'brand', '#2C5BFF': 'brand',
  'var(--gold)': 'gold', '#FFB020': 'gold',
  'var(--info)': 'info', '#0EA8BE': 'info',
  'var(--success)': 'success', '#11A86B': 'success',
  'var(--text-secondary)': 'gray', '#5B6678': 'gray',
};

const src = computed(() => {
  const color = (props.color || '').trim();
  if (!color) return `/static/icons/${props.name}.png`;
  let key = COLOR_KEY[color];
  if (!key && /255,\s*255,\s*255/.test(color)) key = 'white'; // 深底白字形（如匹配页信任条）
  return `/static/icons/${props.name}-${key || 'gray'}.png`;
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

.ico-img {
  width: 100%;
  height: 100%;
  display: block;
}
</style>
