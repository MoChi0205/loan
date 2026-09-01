<template>
  <view class="app-card" :class="{ 'app-card--flat': flat, 'app-card--pad': pad }" :style="customStyle">
    <slot />
  </view>
</template>

<script setup>
/**
 * 卡片组件（瑞幸风）：大圆角 24rpx + 柔和阴影 + 白底。
 *
 * <p>统一替代各页面散落的 .card 样式，改设计只需改这一处。
 *
 * props:
 *   flat   是否无阴影（默认 false）
 *   pad    是否启用默认内边距 32rpx（默认 true；自定义布局时传 false）
 *
 * 用法：
 *   <AppCard>
 *     <text class="card-title">标题</text>
 *     ...
 *   </AppCard>
 */
defineProps({
  flat: { type: Boolean, default: false },
  pad: { type: Boolean, default: true },
  customStyle: { type: String, default: '' },
});
</script>

<style scoped>
.app-card {
  background: var(--color-card, #ffffff);
  border-radius: var(--radius-card, 24rpx);
  box-shadow: var(--shadow-card, 0 4rpx 20rpx rgba(0, 0, 0, 0.05));
  overflow: hidden;
}
.app-card--flat {
  box-shadow: none;
  border: 2rpx solid var(--color-border, #e5e7eb);
}
.app-card--pad {
  padding: 32rpx;
}
</style>
