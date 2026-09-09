<template>
  <view class="tab-bar" :class="{ 'is-tablet': store.isTablet }" role="tablist" aria-label="主要导航">
    <view
      v-for="item in tabList"
      :key="item.key"
      class="tab-item"
      :class="{ 'tab-active': item.key === current }"
      :style="{ '--tab-color': item.color, '--tab-active-color': item.activeColor }"
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
        <AppIcon :name="item.icon" size="lg" :color="iconColor(item)" />
      </view>
      <text class="tab-label">{{ item.label }}</text>
    </view>
  </view>
</template>

<script setup>
import { computed, ref, onUnmounted } from 'vue';
import { useUserStore } from '../store/user';
import { getThemeMode, onThemeChange } from '../theme';

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
 * 配色：每个 tab 使用各自的语义色（不再是死灰），与首页色彩体系一致：
 * - 首页：主色 brand-deep
 * - 智能匹配：蓝紫（匹配/连接）
 * - 我的报告：暖金（数据/报告）
 * - 服务单：绿色（服务/进行）
 * - 我的：青色（个人中心）
 * - 渠道「我的产品」：暖金；「录入客户」：绿色
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

/** 订阅主题：TabBar 颜色与背景须随明暗切换（SVG stroke 不解析 var()，须注入真实色）。 */
const themeMode = ref(getThemeMode());
const offTheme = onThemeChange((m) => { themeMode.value = m; });
onUnmounted(() => { if (offTheme) offTheme(); });

/** TabBar 配色（墨金体系，全局统一）：未选中统一中性灰，选中统一主色。
 *  禁止按 tab 跳色，保证品牌识别一致。
 *  ⚠️ 必须传真实色值（#RRGGBB / rgba），不能用 var(--…)：AppIcon v8 的 SVG stroke
 *     不解析 CSS 变量，传 var(--…) 会导致图标字形画不出来（用户 2026-09-09 反馈）。
 *  明暗双值：暗底用浅灰（保证可见）+ 主色转暖金（= 暗底 --brand-deep #E0AE4E）。 */
const INACTIVE_LIGHT = '#6B7689';   // 浅底未选中（text-muted）
const INACTIVE_DARK = 'rgba(232, 237, 245, 0.55)';
const ACTIVE_LIGHT = '#D9A441';     // 浅底选中（暖金 gold-500）
const ACTIVE_DARK = '#E0AE4E';      // 暗底主色（暖金，对齐 --brand-deep 暗值）

const inactiveColor = computed(() => (themeMode.value === 'dark' ? INACTIVE_DARK : INACTIVE_LIGHT));
const activeColor = computed(() => (themeMode.value === 'dark' ? ACTIVE_DARK : ACTIVE_LIGHT));

const tabList = computed(() => {
  if (store.isChannel) {
    return [
      { key: 'home', label: '首页', icon: 'home', url: '/pages/home/home', color: inactiveColor.value, activeColor: activeColor.value },
      { key: 'product', label: '我的产品', icon: 'bank', url: '/pages/product/list', color: inactiveColor.value, activeColor: activeColor.value },
      { key: 'client', label: '录入客户', icon: 'users', url: '/pages/client/create', color: inactiveColor.value, activeColor: activeColor.value },
      { key: 'mine', label: '我的', icon: 'mine', url: '/pages/mine/mine', color: inactiveColor.value, activeColor: activeColor.value },
    ].filter((item) => item.key === 'home' || item.key === 'mine'
      || (item.key === 'product' && store.hasPermission('mini:product:view'))
      || (item.key === 'client' && store.hasPermission('mini:lead:create')));
  }
  return [
    { key: 'home', label: '首页', icon: 'home', url: '/pages/home/home', color: inactiveColor.value, activeColor: activeColor.value },
    { key: 'match', label: '匹配', icon: 'match', url: '/pages/match/match', color: inactiveColor.value, activeColor: activeColor.value },
    { key: 'report', label: '报告', icon: 'chart', url: '/pages/report/list', color: inactiveColor.value, activeColor: activeColor.value },
    { key: 'order', label: '服务单', icon: 'order', url: '/pages/order/list', color: inactiveColor.value, activeColor: activeColor.value },
    { key: 'mine', label: '我的', icon: 'mine', url: '/pages/mine/mine', color: inactiveColor.value, activeColor: activeColor.value },
  ].filter((item) => item.key === 'home' || item.key === 'mine'
    || (item.key === 'match' && store.hasPermission('mini:match:view'))
    || (item.key === 'report' && store.hasPermission('mini:report:view'))
    || (item.key === 'order' && store.hasPermission('mini:order:view')));
});

/**
 * 图标颜色：未选中用 tab 自身的语义色，选中态用主色 brand-deep。
 * 通过 CSS 自定义属性 --tab-color 传递，样式表中可统一引用。
 */
function iconColor(item) {
  if (item.key === props.current) return item.activeColor;
  return item.color;
}

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
  /* 随主题变化：浅底=白(#FFFFFF)，暗底=墨金卡面(#131E33)，与卡片同色系 */
  background: var(--bg-card);
  border-top: 1rpx solid var(--line);
  box-shadow: 0 -4rpx 24rpx rgba(15, 23, 42, 0.06);
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
  background: var(--tab-active-color, var(--brand-deep));
}

/* 图标容器：选中态加柔和背景药丸（电商风格）
   注意：微信 wxss 不支持 color-mix()，用静态 rgba 替代 */
.tab-icon-wrap {
  width: 64rpx;
  height: 64rpx;
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s;
}

.tab-icon-wrap.icon-active {
  background: transparent;
}

.tab-label {
  margin-top: 6rpx;
  font-size: var(--fs-xs);
  line-height: 1;
  /* 禁止换行：H5 窄项下「智能匹配」等 4 字标签曾折行把 TabBar 撑高，
     压住页面底部内容形成叠影（用户 2026-09-07 报告） */
  white-space: nowrap;
  /* 未选中用 tab 自身语义色（不再灰！） */
  color: var(--tab-color, var(--text-secondary));
  font-weight: 500;
  transition: color 0.15s;
}

.tab-active .tab-label {
  color: var(--tab-active-color, var(--brand-deep));
  font-weight: 600;
}

/* #ifdef H5 */
/* 宽屏限宽：与 uni-page-body 对齐（阶段2 H1）
   ⚠️ 居中必须用 left:0 + right:0 + margin:auto —— 不可用 left:50% + translateX(-50%)：
   fixed 元素同时设 left/right 会先拉伸宽度（被 left/right 拉伸压过 max-width），
   实测宽度塌成 381px、5 个 tab 挤压换行（用户 2026-09-07 报告叠影的根因之一） */
@media (min-width: 768px) {
  .tab-bar {
    max-width: 600px;
    margin: 0 auto;
  }
}
/* #endif */
</style>
