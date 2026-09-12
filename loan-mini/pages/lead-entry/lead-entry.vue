<template>
  <view class="le theme-root" :class="{ 'u-shell': store.isTablet }" :data-theme="themeMode">
    <!-- 子页签：客户线索 / 产品（用户 2026-09-10 确认合并，D74） -->
    <view class="seg-bar">
      <view
        v-for="s in segs"
        :key="s.key"
        class="seg-item"
        :class="{ on: seg === s.key }"
        @click="switchSeg(s.key)"
      >{{ s.label }}</view>
    </view>

    <LeadEntryClient v-show="seg === 'lead'" :active="seg === 'lead'" />
    <LeadEntryProduct v-show="seg === 'product'" :active="seg === 'product'" />

    <TabBar current="luru" />
  </view>
</template>

<script setup>
/**
 * 「线索录入」合并页（D74）。
 *
 * <p>把原先并列的「录入线索」「录入产品」两个 tab 合并为一个 tab，
 * 内部以子页签切换：员工侧 = 客户线索 / 产品；渠道侧 = 录入客户 / 我的产品。
 *
 * <p>子视图抽为组件（LeadEntryClient / LeadEntryProduct）：
 * 二者原本依赖页面级生命周期（onLoad / onShow），已改为 onMounted + active 侦听，
 * 因此本页只负责切换与底部导航，不再重复承载数据逻辑（对齐 loan-code-standard「复用优先」）。
 *
 * <p>进入方式：`/pages/lead-entry/lead-entry?seg=product`（seg 非法或缺失时落到首个子页签）。
 */
import { ref, computed } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import TabBar from '../../components/TabBar.vue';
import LeadEntryClient from '../../components/LeadEntryClient.vue';
import LeadEntryProduct from '../../components/LeadEntryProduct.vue';
import { useUserStore } from '../../store/user';
import { useThemeMode } from '../../theme';
import { roleConfig, resolveSeg } from '../../utils/roles';

const store = useUserStore();
const themeMode = useThemeMode();

/** 当前角色的子页签定义（唯一来源 utils/roles.js） */
const segs = computed(() => roleConfig(store.role).entry || []);

/** 当前子页签 */
const seg = ref('');

onLoad((options) => {
  // 无该合并页的角色（客户）不应到达：按最小权限送回首页
  if (!segs.value.length) {
    uni.reLaunch({ url: '/pages/home/home' });
    return;
  }
  seg.value = resolveSeg(segs.value, options && options.seg);
});

/** 切换子页签（组件以 v-show 保留各自表单 / 列表状态） */
function switchSeg(key) {
  seg.value = key;
}
</script>

<style scoped>
.le {
  min-height: 100vh;
  background: var(--bg-page);
  box-sizing: border-box;
}

/* 子页签：吸顶，与页面同色底避免滚动穿透 */
.seg-bar {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-4) var(--space-2);
  background: var(--bg-page);
}

.seg-item {
  flex: 1;
  min-width: 0;
  text-align: center;
  padding: 20rpx 0;
  font-size: var(--fs-sm);
  font-weight: 500;
  color: var(--text-secondary);
  background: var(--bg-input);
  border-radius: var(--radius-md);
  transition: background var(--transition-fast), color var(--transition-fast);
}

.seg-item.on {
  background: var(--bg-card);
  color: var(--brand-deep);
  font-weight: 600;
  box-shadow: var(--shadow-sm);
}
</style>
