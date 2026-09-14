<template>
  <view
    class="tab-bar"
    :class="{ 'is-tablet': store.isTablet }"
    :data-theme="themeMode"
    role="tablist"
    aria-label="主要导航"
  >
    <view
      v-for="item in tabList"
      :key="item.key"
      class="tab-item"
      :class="{ 'tab-active': item.key === current }"
      :style="{ '--tab-color': inactiveColor, '--tab-active-color': activeColor }"
      role="tab"
      :aria-selected="item.key === current"
      :aria-label="item.label"
      :tabindex="item.key === current ? 0 : -1"
      @click="onTap(item)"
      @keydown.enter="onTap(item)"
      @keydown.space.prevent="onTap(item)"
    >
      <view class="tab-icon-wrap" :class="{ 'icon-active': item.key === current }">
        <AppIcon
          :name="item.icon"
          size="md"
          variant="tab"
          :color="item.key === current ? activeColor : inactiveColor"
        />
      </view>
      <text class="tab-label">{{ item.label }}</text>
    </view>
  </view>
</template>

<script setup>
import { computed, ref, onUnmounted } from 'vue';
import { useUserStore } from '../store/user';
import { getThemeMode, onThemeChange } from '../theme';
import { roleConfig } from '../utils/roles';

/**
 * 角色化底部导航（全端统一自绘，替代原生 tabBar）。
 *
 * <p>原生 tabBar 为静态配置，无法按角色差异渲染；且 uni-app 的 tabBar
 * `custom` 字段仅微信/抖音小程序支持、H5 端忽略。故采用自绘组件。
 *
 * <p>角色差异唯一来源 = utils/roles.js（用户 2026-09-10 二次确认的合并结构，D74）：
 * - 客户：首页 · 智能匹配 · 我的报告 · 服务单 · 我的（5）
 * - 渠道：首页 · 线索录入 · 我的客户 · 我的（4，唯一不可匹配；我的客户只读）
 * - 顾问 / 部门经理：首页 · 线索录入 · 我的客户 · 我的（4）
 * - 运营：首页 · 线索录入 · 我的客户 · 审批中心 · 我的（5）
 * - 老板 / 超级管理员：首页 · 线索录入 · 智能匹配 · 我的客户 · 我的（5）
 *
 * <p>配色：未选中统一中性灰，选中统一皇家蓝（--brand-deep），禁止按 tab 跳色。
 *   ⚠️ 必须传真实色值（#RRGGBB / rgba）：AppIcon 的 SVG stroke 不解析 var(--…)。
 *
 * <p>用法（tab 页面底部）：`<TabBar current="home" />`
 * current 取值：home / match / report / order / mine / product / client / approval / luru / clients
 * 切换一律 uni.reLaunch（无原生 tabBar 配置时 switchTab 会失败）。
 */
const props = defineProps({
  /** 当前 tab 标识 */
  current: { type: String, default: 'home' },
});

const store = useUserStore();

/** 订阅主题：TabBar 颜色须随明暗切换（SVG stroke 不解析 var()，须注入真实色）。 */
const themeMode = ref(getThemeMode());
const offTheme = onThemeChange((m) => { themeMode.value = m; });
onUnmounted(() => { if (offTheme) offTheme(); });

/** tab 键 → 页面路由（小程序侧实存页面，全部为 tab 页） */
const TAB_URL = {
  home: '/pages/home/home',
  match: '/pages/match/match',
  report: '/pages/report/list',
  order: '/pages/order/list',
  mine: '/pages/mine/mine',
  product: '/pages/product/list',
  client: '/pages/client/create',
  approval: '/pages/approval/list',
  // 合并页（D74）
  luru: '/pages/lead-entry/lead-entry',
  clients: '/pages/client/mine',
};

// 明暗双真实色值（皇家蓝主色）：浅底 #2C52C9；暗底提亮为 #6E9BE0
const INACTIVE_LIGHT = '#6A768C';
const INACTIVE_DARK = 'rgba(232, 237, 245, 0.55)';
const ACTIVE_LIGHT = '#2C52C9';
const ACTIVE_DARK = '#6E9BE0';

const inactiveColor = computed(() => (themeMode.value === 'dark' ? INACTIVE_DARK : INACTIVE_LIGHT));
const activeColor = computed(() => (themeMode.value === 'dark' ? ACTIVE_DARK : ACTIVE_LIGHT));

/** 当前角色的 tab 列表（唯一来源 utils/roles.js） */
const tabList = computed(() => {
  const configured = roleConfig(store.role).tabs || [];
  // 审批权限是最终事实来源：即使后端动态下发权限，也必须出现审批中心 Tab。
  if (store.hasPermission('mini:approval:view') && !configured.some((item) => item.key === 'approval')) {
    const mineIndex = configured.findIndex((item) => item.key === 'mine');
    const approval = { key: 'approval', label: '审批中心', icon: 'shield' };
    const next = configured.slice();
    next.splice(mineIndex < 0 ? next.length : mineIndex, 0, approval);
    return next.slice(0, 5);
  }
  return configured;
});

function onTap(item) {
  if (item.key === props.current) return;
  const url = TAB_URL[item.key];
  if (!url) return;
  uni.reLaunch({ url });
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
  /* 随主题变化：浅底=白，暗底=墨蓝卡面，与卡片同色系 */
  background: var(--bg-card);
  border-top: 1rpx solid var(--line);
  box-shadow: 0 -4rpx 24rpx rgba(17, 30, 54, 0.06);
  /* 原型 m-tabbar min-height: 68px；rpx 下固定为 136rpx，4/5 个 Tab 高度一致。 */
  min-height: 136rpx;
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
  padding: 0;
  transition: opacity 0.15s;
}

.tab-item:active { opacity: 0.7; }

.tab-icon-wrap {
  width: 48rpx;
  height: 48rpx;
  border-radius: 12rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s;
}

.tab-icon-wrap.icon-active { background: transparent; }

.tab-label {
  margin-top: 6rpx;
  font-size: var(--fs-xs);
  line-height: 1;
  /* 禁止换行：H5 窄项下「智能匹配」等 4 字标签曾折行把 TabBar 撑高，压住页面底部内容形成叠影 */
  white-space: nowrap;
  color: var(--tab-color, var(--text-secondary));
  font-weight: 500;
  transition: color 0.15s;
}

.tab-active .tab-label {
  color: var(--tab-active-color, var(--brand-deep));
  font-weight: 600;
}

/* #ifdef H5 */
/* 宽屏限宽：与 uni-page-body 对齐
   ⚠️ 居中必须用 left:0 + right:0 + margin:auto —— 不可用 left:50% + translateX(-50%)：
   fixed 元素同时设 left/right 会先拉伸宽度（被 left/right 拉伸压过 max-width），
   实测宽度塌成 381px、5 个 tab 挤压换行（用户 2026-09-07 报告叠影的根因之一，D64） */
@media (min-width: 768px) {
  .tab-bar {
    max-width: 600px;
    margin: 0 auto;
  }
}
/* #endif */
</style>
