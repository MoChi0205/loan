<template>
  <view class="app-skeleton">
    <view v-for="r in rows" :key="r" class="sk-row">
      <view class="sk-line" :style="{ width: lineWidth(0) }" />
      <view class="sk-line" :style="{ width: lineWidth(1) }" />
      <view class="sk-line" :style="{ width: lineWidth(2) }" />
    </view>
  </view>
</template>

<script setup>
/**
 * 骨架屏组件（小程序版）。
 *
 * <p>列表首次加载时占位，替代空白等待，降低"等死"焦虑。
 *
 * 用法：
 *   <AppSkeleton v-if="loading && !list.length" :rows="4" />
 *   <view v-else>...真实列表...</view>
 */
const props = defineProps({
  rows: { type: Number, default: 4 },
});

/** 每行 3 条线，宽度错落模拟真实内容 */
function lineWidth(i) {
  const widths = ['34%', '26%', '30%'];
  return widths[i % widths.length];
}
</script>

<style scoped>
.app-skeleton {
  padding: 8rpx 0;
}
.sk-row {
  display: flex;
  gap: 24rpx;
  align-items: center;
  padding: 28rpx 0;
  border-bottom: 2rpx solid var(--color-border, #e5e7eb);
}
.sk-line {
  height: 24rpx;
  border-radius: 8rpx;
  background: linear-gradient(
    90deg,
    rgba(148, 163, 184, 0.14) 25%,
    rgba(148, 163, 184, 0.28) 50%,
    rgba(148, 163, 184, 0.14) 75%
  );
  background-size: 200% 100%;
  animation: sk-shimmer 1.4s ease-in-out infinite;
}
@keyframes sk-shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}
</style>
