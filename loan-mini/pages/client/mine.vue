<template>
  <view class="cm theme-root" :class="{ 'u-shell': store.isTablet }" :data-theme="themeMode">
    <!-- 页头：角色化说明（客户侧无本页） -->
    <view class="cm-head">
      <text class="cm-title">我的客户</text>
      <text class="cm-sub">{{ hubSub }}</text>
    </view>

    <!-- 子页签：我的客户 / 公海 / 我的报告（渠道永远只有“我的客户”，无公海数据） -->
    <view v-if="segs.length > 1" class="seg-bar">
      <view
        v-for="s in segs"
        :key="s.key"
        class="seg-item"
        :class="{ on: seg === s.key }"
        @click="switchSeg(s.key)"
      >{{ s.label }}</view>
    </view>

    <MyClientList ref="mineRef" v-show="seg === 'mine'" :active="seg === 'mine'" />
    <TeamClientList v-if="hasTeam" ref="teamRef" v-show="seg === 'team'" :active="seg === 'team'" />
    <SeaClientList v-if="hasSea" ref="seaRef" v-show="seg === 'sea'" :active="seg === 'sea'" />
    <MyReportList v-if="hasReport" ref="reportRef" v-show="seg === 'report'" :active="seg === 'report'" />

    <TabBar current="clients" />
  </view>
</template>

<script setup>
/**
 * 「我的客户」合并页（D74，D75 增加部门经理「团队」子页签）。
 *
 * <p>把原先并列的「我的客户」「我的报告」两个 tab 合并为一个 tab，内部以子页签切换：
 * 员工侧 = 我的客户 / 公海 / 我的报告；部门经理额外多一个「团队」；渠道侧 = 我的客户（只读）。
 *
 * <p><b>数据范围：</b>员工「我的客户」统一只显示本人归属（用户 2026-09-10 确认）；
 * 「团队」仅部门经理且限本部门（15-规则 §10/§32/§34，用于回收）；
 * 「公海」按 15-规则 §11/§12/§17 —— 公司公海全员可认领、团队公海仅本部门；
 * 渠道只读本人录入客户、无公海、无认领（D50）。
 *
 * <p><b>操作按钮按角色权限展示：</b>「释放」= 员工本人归属客户；
 * 「回收」= 仅部门经理（团队子页签）；渠道无任何写操作。
 *
 * <p>子视图抽为组件（MyClientList / TeamClientList / SeaClientList / MyReportList）：
 * 原页面级 onShow / onReachBottom / onPullDownRefresh 已改为 onMounted + active 侦听，
 * 并在此由容器页转发上拉加载与下拉刷新（组件内无法注册页面级生命周期）。
 */
import { ref, computed } from 'vue';
import { onLoad, onReachBottom, onPullDownRefresh } from '@dcloudio/uni-app';
import TabBar from '../../components/TabBar.vue';
import MyClientList from '../../components/MyClientList.vue';
import TeamClientList from '../../components/TeamClientList.vue';
import SeaClientList from '../../components/SeaClientList.vue';
import MyReportList from '../../components/MyReportList.vue';
import { useUserStore } from '../../store/user';
import { useThemeMode } from '../../theme';
import { roleConfig, resolveSeg } from '../../utils/roles';

const store = useUserStore();
const themeMode = useThemeMode();

/** 当前角色的子页签定义（唯一来源 utils/roles.js） */
const segs = computed(() => roleConfig(store.role).hub || []);

/** 「团队」子页签仅部门经理（15-规则 §10） */
const hasTeam = computed(() => segs.value.some((s) => s.key === 'team'));

/** 渠道无公海页签（D50：不可认领 / 指派 / 回收） */
const hasSea = computed(() => segs.value.some((s) => s.key === 'sea'));

/** 渠道无报告子页签（D50 / 08 矩阵：渠道报告仍在 Web 查看），避免挂载无权访问的子视图 */
const hasReport = computed(() => segs.value.some((s) => s.key === 'report'));

/** 页头说明：按角色二分 */
const hubSub = computed(() => {
  if (store.isChannel) return '仅展示您本人录入并已转化的客户';
  if (store.role === 'deptmgr') return '本人归属客户 · 团队客户可回收 · 公海可认领';
  return '本人归属客户 · 公海可认领 · 历史报告';
});

/** 当前子页签 */
const seg = ref('');

/** 子组件引用（转发上拉加载 / 下拉刷新） */
const mineRef = ref(null);
const teamRef = ref(null);
const seaRef = ref(null);
const reportRef = ref(null);

onLoad((options) => {
  if (!segs.value.length) {
    uni.reLaunch({ url: '/pages/home/home' });
    return;
  }
  seg.value = resolveSeg(segs.value, options && options.seg);
});

/** 切换子页签（组件以 v-show 保留各自列表状态） */
function switchSeg(key) {
  seg.value = key;
}

/** 当前激活子视图的组件实例 */
function activeRef() {
  if (seg.value === 'mine') return mineRef.value;
  if (seg.value === 'team') return teamRef.value;
  if (seg.value === 'sea') return seaRef.value;
  if (seg.value === 'report') return reportRef.value;
  return null;
}

onReachBottom(() => {
  const view = activeRef();
  if (view && typeof view.loadMore === 'function') view.loadMore();
});

onPullDownRefresh(async () => {
  const view = activeRef();
  try {
    if (view && typeof view.refresh === 'function') await view.refresh();
  } finally {
    uni.stopPullDownRefresh();
  }
});
</script>

<style scoped>
.cm {
  min-height: 100vh;
  background: var(--bg-page);
  box-sizing: border-box;
}

.cm-head {
  padding: var(--space-4) var(--space-4) 0;
}

.cm-title {
  display: block;
  font-size: var(--fs-xl);
  font-weight: 700;
  color: var(--text-primary);
}

.cm-sub {
  display: block;
  margin-top: 6rpx;
  font-size: var(--fs-xs);
  color: var(--text-secondary);
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
