<template>
  <view class="tab-bar" :class="{ 'is-tablet': store.isTablet }" role="tablist" aria-label="主要导航">
    <view
      v-for="item in tabList"
      :key="item.key"
      class="tab-item"
      :class="{ 'tab-active': item.key === current }"
      role="tab"
      :aria-selected="item.key === current"
      :aria-label="item.label"
      :tabindex="item.key === current ? 0 : -1"
      @click="onTap(item)"
      @keydown.enter="onTap(item)"
      @keydown.space.prevent="onTap(item)"
    >
      <!-- 选中态顶部指示条 -->
      <view class="tab-indicator" v-if="item.key === current" />
      <view class="tab-icon-wrap" :class="{ 'icon-active': item.key === current }">
        <AppIcon :name="item.icon" size="md" :color="item.key === current ? 'var(--brand-deep)' : 'var(--text-secondary)'" />
      </view>
      <text class="tab-label">{{ item.label }}</text>
    </view>
  </view>
</template>

<script setup>
import { computed } from 'vue';
import { useUserStore } from '../store/user';

/**
 * 角色化底部导航（全端统一自绘，替代原生 tabBar）。
 *
 * <p>原生 tabBar 为静态配置，无法按角色差异渲染；且 uni-app 的 tabBar
 * `custom` 字段仅微信/抖音小程序支持、H5 端忽略。故采用自绘组件：
 * 一套代码在小程序与 H5 渲染一致的「渠道沙箱」导航。
 *
 * 角色差异（对齐交互原型 §7 角色导航 & 结论 C1/C3/服务单模块）：
 * - 客户 / 企业员工（顾问/经理/老板/运营/超管）：首页 · 智能匹配 · 我的报告 · 服务单 · 我的
 * - 渠道合作方（沙箱隔离）：首页 · 我的产品 · 录入客户 · 我的（隐藏匹配/报告/服务单）
 *
 * 用法（tab 页面底部）：
 *   <TabBar current="home" />
 * current 取值：home / match / report / order / mine / product / client
 *
 * 注意：移除原生 tabBar 后 uni.switchTab 不可用，切换用 uni.reLaunch。
 */
const props = defineProps({
  /** 当前 tab 标识 */
  current: { type: String, default: 'home' },
});

const store = useUserStore();

const tabList = computed(() => {
  if (store.isChannel) {
    return [
      { key: 'home', label: '首页', icon: 'home', url: '/pages/home/home' },
      { key: 'product', label: '我的产品', icon: 'bank', url: '/pages/product/list' },
      { key: 'client', label: '录入客户', icon: 'users', url: '/pages/client/create' },
      { key: 'mine', label: '我的', icon: 'mine', url: '/pages/mine/mine' },
    ];
  }
  const isManager = ['deptmgr', 'boss', 'operator', 'super'].includes(store.role);
  return [
    { key: 'home', label: '首页', icon: 'home', url: '/pages/home/home' },
    { key: 'match', label: '智能匹配', icon: 'match', url: '/pages/match/match' },
    { key: 'report', label: isManager ? '报告中心' : store.role === 'adviser' ? '客户报告' : '我的报告', icon: 'chart', url: '/pages/report/list' },
    { key: 'order', label: isManager ? '工单中心' : store.role === 'adviser' ? '客户工单' : '服务单', icon: 'order', url: '/pages/order/list' },
    { key: 'mine', label: '我的', icon: 'mine', url: '/pages/mine/mine' },
  ];
});

function onTap(item) {
  if (item.key === props.current) return;
  uni.reLaunch({ url: item.url });
}
</script>

<style scoped>
.tab-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 100;
  display: flex;
  align-items: stretch;
  background: var(--text-invert);
  border-top: 1rpx solid var(--line);
  box-shadow: 0 -4rpx 24rpx rgba(0, 0, 0, 0.06);
  padding-bottom: constant(safe-area-inset-bottom);
  padding-bottom: env(safe-area-inset-bottom);
}

.tab-item {
  flex: 1;
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 16rpx 0 14rpx;
  transition: opacity 0.15s;
}

.tab-item:active {
  opacity: 0.7;
}

/* 选中态顶部指示条（电商风格：3px 主色圆角条） */
.tab-indicator {
  position: absolute;
  top: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 48rpx;
  height: 6rpx;
  border-radius: 0 0 6rpx 6rpx;
  background: var(--brand-deep);
}

/* 图标容器：选中态加柔和背景药丸（电商风格） */
.tab-icon-wrap {
  width: 56rpx;
  height: 56rpx;
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s;
}

.tab-icon-wrap.icon-active {
  background: rgba(11, 29, 58, 0.08);
}

.tab-label {
  margin-top: 6rpx;
  font-size: var(--fs-xs);
  line-height: 1;
  color: var(--text-secondary);
  font-weight: 500;
  transition: color 0.15s;
}

.tab-active .tab-label {
  color: var(--brand-deep);
  font-weight: 600;
}

/* #ifdef H5 */
/* 宽屏限宽：与 uni-page-body 对齐（阶段2 H1） */
@media (min-width: 768px) {
  .tab-bar {
    max-width: 600px;
    margin: 0 auto;
    left: 50%;
    transform: translateX(-50%);
  }
}
/* #endif */
</style>
