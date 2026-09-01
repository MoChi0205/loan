<template>
  <view class="app-topbar" :class="{ 'is-fixed': fixed }">
    <!-- 状态栏占位 -->
    <view class="statusbar" />
    <!-- 导航栏 -->
    <view class="navbar">
      <view class="nav-left" @click="onBack">
        <view v-if="showBack" class="back-arrow" />
      </view>
      <text class="nav-title">{{ title }}</text>
      <view class="nav-right">
        <slot name="right" />
      </view>
    </view>
  </view>
</template>

<script setup>
/**
 * 顶部导航栏组件（瑞幸风）。
 *
 * <p>统一各页面的顶部栏（当前 9 个页面有 4 种风格）。
 * 自动处理状态栏安全区 + 返回箭头。
 *
 * props:
 *   title     标题
 *   showBack  是否显示返回箭头（默认 true）
 *   fixed     是否固定在顶部（默认 false，跟随页面滚动）
 *
 * 用法：
 *   <AppTopBar title="我的报告" />
 *   <AppTopBar title="报告详情" @back="goBack" />
 */
const props = defineProps({
  title: { type: String, default: '' },
  showBack: { type: Boolean, default: true },
  fixed: { type: Boolean, default: false },
});

const emit = defineEmits(['back']);

function onBack() {
  if (!props.showBack) return;
  emit('back');
  uni.navigateBack({ delta: 1 });
}
</script>

<style scoped>
.app-topbar {
  background: var(--color-card, #ffffff);
}
.is-fixed {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 100;
}
.statusbar {
  height: var(--status-bar-height, 44px);
}
.navbar {
  height: 88rpx;
  display: flex;
  align-items: center;
  padding: 0 24rpx;
  position: relative;
}
.nav-left {
  width: 80rpx;
  display: flex;
  align-items: center;
}
.back-arrow {
  width: 20rpx;
  height: 20rpx;
  border-left: 4rpx solid var(--color-primary, #0b1d3a);
  border-bottom: 4rpx solid var(--color-primary, #0b1d3a);
  transform: rotate(45deg);
  margin-left: 8rpx;
}
.nav-title {
  flex: 1;
  text-align: center;
  font-size: 32rpx;
  font-weight: 600;
  color: var(--color-text, #1a1a2e);
}
.nav-right {
  width: 80rpx;
  display: flex;
  align-items: center;
  justify-content: flex-end;
}
</style>
