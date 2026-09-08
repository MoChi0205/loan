<template>
  <view class="app-icon" :class="[`icon-${size}`]">
    <image class="ico-img" :src="src" mode="aspectFit" aria-hidden="true" />
  </view>
</template>

<script setup>
import { computed } from 'vue';

/**
 * 图标组件 v5（冷玻璃·线性，PNG 资源版，兼容微信小程序）。
 *
 * <p>v2 内联 <svg> 在微信 WXML 不可用（渲染空白）；v3/v4 转为构建期栅格化 PNG。
 * v4 实心色块字形语义不清、资源不全（TabBar 选中态裂图）。v5 重制：
 * 字形真源为手写线性 SVG（24 网格 / stroke 1.75 圆头），经
 * scripts/gen-icons-v5.mjs（resvg-js）栅格化为 144×144 高清 PNG
 * （static/icons/，单文件约 2-6KB），运行时 <image> 渲染：mp-weixin / H5
 * 双端一致、无限缩放不虚、语义清晰。
 *
 * <p>两种形态：
 * - 磁贴（默认）：圆形冷玻璃底（浅蓝渐变 + 高光 + 细白边 + 品牌柔投影）
 *   + 品牌渐变线性字形 #2443C2→#3D63E0，用于内容功能入口
 *   （认证卡片、首页快捷功能、数据卡片、菜单行等）。
 *   ⚠️ 页面容器必须 background:transparent，禁止再叠浅底方块（双层底）。
 * - 单色 plain：传入 color 属性时启用，纯线性字形，用于 TabBar、
 *   搜索栏、深底按钮等需要跟随语义色/中性色的场景。
 *
 * <p>可用 name（24 枚）：home / match / chart / order / bank / users / mine /
 * check / search / wechat / bolt / support / list / share / enterprise /
 * person / refresh / alert / trend / doc / file / photo / lock / arrow
 *
 * 用法：
 *   <AppIcon name="enterprise" size="lg" />                      → 磁贴
 *   <AppIcon name="home" size="md" color="var(--brand-deep)" />  → 单色
 */
const props = defineProps({
  name: { type: String, default: 'home' },
  /** 尺寸：sm(32rpx) / md(48rpx) / lg(64rpx) / xl(88rpx) */
  size: { type: String, default: 'md' },
  /** 传入则渲染单色字形；支持设计令牌与常用色值，未知色回退中性灰 */
  color: { type: String, default: '' },
});

/**
 * color → plain 资源后缀映射。
 * PNG 无法运行时换色，故构建期预生成：gray / brand 全量（任意组合不裂图），
 * gold / success / info / white 按页面实际用法生成（见 gen-icons-v5.mjs
 * 的 PLAIN_EXTRA）；未命中回退 gray（--text-secondary）。
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
.icon-xl { width: 88rpx; height: 88rpx; }

.ico-img {
  width: 100%;
  height: 100%;
  display: block;
}
</style>
