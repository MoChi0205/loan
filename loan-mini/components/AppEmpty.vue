<template>
  <view class="app-empty">
    <!-- CSS 绘制的空状态插画：文件卡 + 放大镜 -->
    <view class="empty-art">
      <view class="art-circle" />
      <view class="art-card">
        <view class="art-line line-1" />
        <view class="art-line line-2" />
        <view class="art-line line-3" />
      </view>
      <view class="art-lens" />
    </view>
    <text class="empty-title">{{ title }}</text>
    <text v-if="desc" class="empty-desc">{{ desc }}</text>
    <view v-if="$slots.default" class="empty-action">
      <slot />
    </view>
  </view>
</template>

<script setup>
/**
 * 空状态组件（小程序版，纯 view/CSS 绘制插画）。
 *
 * <p>统一各页面的"暂无数据"表现，避免每页各写各的。
 *
 * 用法：
 *   <AppEmpty v-if="!list.length" title="暂无报告" desc="完成匹配后在此查看" />
 */
defineProps({
  title: { type: String, default: '暂无数据' },
  desc: { type: String, default: '' },
});
</script>

<style scoped>
.app-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 80rpx 32rpx;
}
.empty-art {
  position: relative;
  width: 200rpx;
  height: 140rpx;
  margin-bottom: 24rpx;
}
.art-circle {
  position: absolute;
  left: 20rpx;
  top: 10rpx;
  width: 120rpx;
  height: 120rpx;
  border-radius: 50%;
  background: rgba(11, 29, 58, 0.05);
}
.art-card {
  position: absolute;
  left: 26rpx;
  top: 22rpx;
  width: 116rpx;
  height: 84rpx;
  background: rgba(11, 29, 58, 0.07);
  border: 2rpx solid rgba(11, 29, 58, 0.14);
  border-radius: 16rpx;
  padding: 16rpx 14rpx;
  box-sizing: border-box;
}
.art-line {
  height: 8rpx;
  border-radius: 4rpx;
  background: rgba(11, 29, 58, 0.16);
  margin-bottom: 12rpx;
}
.line-1 { width: 88%; }
.line-2 { width: 68%; background: rgba(11, 29, 58, 0.11); }
.line-3 { width: 48%; background: rgba(11, 29, 58, 0.08); margin-bottom: 0; }
.art-lens {
  position: absolute;
  right: 24rpx;
  bottom: 12rpx;
  width: 44rpx;
  height: 44rpx;
  border: 6rpx solid rgba(200, 169, 110, 0.55);
  border-radius: 50%;
  box-sizing: border-box;
}
.art-lens::after {
  content: '';
  position: absolute;
  right: -14rpx;
  bottom: -12rpx;
  width: 22rpx;
  height: 6rpx;
  background: rgba(200, 169, 110, 0.55);
  border-radius: 3rpx;
  transform: rotate(45deg);
}
.empty-title {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--color-text, #1a1a2e);
  margin-bottom: 8rpx;
}
.empty-desc {
  font-size: 24rpx;
  color: var(--color-text-hint, #9ca3af);
  text-align: center;
  line-height: 1.6;
}
.empty-action {
  margin-top: 28rpx;
}
</style>
