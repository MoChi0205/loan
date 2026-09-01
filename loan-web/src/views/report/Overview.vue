<template>
  <div class="report-page">
    <div class="loan-page-header">
      <div>
        <h2 class="loan-page-title">数据概览</h2>
        <p class="loan-page-subtitle">经营核心指标 · 转化漏斗 · 客群 / 产品 / 工单分布 · 成交 / 奖励趋势</p>
      </div>
      <div class="range-hint">数据周期：截至 {{ today }}</div>
    </div>

    <!-- 总览卡片 -->
    <div class="stat-grid" v-loading="loadingOv">
      <div v-for="card in statCards" :key="card.key" class="stat-card" :style="{ '--accent': card.color }">
        <div class="stat-icon">
          <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path :d="card.icon" /></svg>
        </div>
        <div class="stat-body">
          <div class="stat-label">{{ card.label }}</div>
          <div class="stat-value mono">{{ card.value }}</div>
        </div>
        <div v-if="card.delta !== null && card.delta !== undefined" class="stat-delta" :class="card.delta >= 0 ? 'up' : 'down'">
          <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path :d="card.delta >= 0 ? 'M6 15l6-6 6 6' : 'M6 9l6 6 6-6'" /></svg>
          {{ Math.abs(card.delta) }}%
          <span class="delta-cap">环比</span>
        </div>
      </div>
    </div>

    <!-- 转化漏斗 -->
    <div class="loan-card funnel-card" v-loading="loadingOv">
      <div class="panel-title">转化漏斗<small>线索 → 客户 → 工单 → 成交 → 奖励（累计口径）</small></div>
      <div class="funnel">
        <div v-for="(stage, i) in funnel" :key="stage.name" class="funnel-row">
          <div class="funnel-stage">
            <span class="funnel-name">{{ stage.name }}</span>
            <span class="funnel-value mono">{{ fmtInt(stage.value) }}</span>
          </div>
          <div class="funnel-track">
            <div class="funnel-bar" :style="{ width: funnelWidth(stage.value) + '%', background: funnelColor(i) }">
              <span class="funnel-pct">{{ funnelPct(stage.value) }}</span>
            </div>
          </div>
          <div class="funnel-conv" v-if="i > 0">
            <span v-if="funnel[i-1].value > 0" class="conv-badge">转化率 {{ convRate(funnel[i-1].value, stage.value) }}</span>
            <span v-else class="conv-badge muted">—</span>
          </div>
          <div class="funnel-conv" v-else><span class="conv-badge root">入口</span></div>
        </div>
      </div>
    </div>

    <!-- 趋势分析 -->
    <div class="trend-grid" v-loading="loadingT">
      <div class="loan-card">
        <div class="panel-title">成交趋势（近 12 个月）<small>单数 / 金额</small></div>
        <AppTrendChart title="成交趋势" :data="orderChartData" color="var(--loan-primary)" height="170px" />
        <div class="trend-foot">
          <span>近12月成交 <b class="mono">{{ fmtInt(orderTotalCount) }}</b> 单</span>
          <span>金额 <b class="mono">¥{{ fmtAmount(orderTotalAmount) }}</b></span>
        </div>
      </div>
      <div class="loan-card">
        <div class="panel-title">奖励趋势（近 12 个月）<small>单数 / 金额</small></div>
        <AppTrendChart title="奖励趋势" :data="rewardChartData" color="var(--loan-accent)" height="170px" />
        <div class="trend-foot">
          <span>近12月奖励 <b class="mono">{{ fmtInt(rewardTotalCount) }}</b> 单</span>
          <span>金额 <b class="mono">¥{{ fmtAmount(rewardTotalAmount) }}</b></span>
        </div>
      </div>
    </div>

    <!-- 分布维度 -->
    <div class="dist-grid" v-loading="loadingOv">
      <!-- 客群分布 -->
      <div class="loan-card">
        <div class="panel-title">客群分布<small>企业 / 个人</small></div>
        <div v-if="customerGroupDist.length" class="dist-bars">
          <div v-for="g in customerGroupDist" :key="g.name" class="dist-row">
            <span class="dist-label">{{ groupLabel(g.name) }}</span>
            <div class="dist-track"><div class="dist-bar" :style="{ width: distWidth(g.value, groupMax) + '%', background: 'var(--loan-primary)' }"></div></div>
            <span class="dist-num mono">{{ fmtInt(g.value) }}</span>
          </div>
        </div>
        <AppEmpty v-else title="暂无客群数据" />
      </div>

      <!-- 工单状态分布 -->
      <div class="loan-card">
        <div class="panel-title">工单状态分布</div>
        <div v-if="orderStatusDist.length" class="dist-bars">
          <div v-for="s in orderStatusDist" :key="s.name" class="dist-row">
            <span class="dist-label">{{ statusLabel(s.name) }}</span>
            <div class="dist-track"><div class="dist-bar" :style="{ width: distWidth(s.value, statusMax) + '%', background: statusColor(s.name) }"></div></div>
            <span class="dist-num mono">{{ fmtInt(s.value) }}</span>
          </div>
        </div>
        <AppEmpty v-else title="暂无工单数据" />
      </div>

      <!-- 产品分布 TOP -->
      <div class="loan-card card-product">
        <div class="panel-title">产品成交 TOP<small>按成交金额</small></div>
        <div v-if="productDist.length" class="dist-bars">
          <div v-for="p in productDist" :key="p.code" class="dist-row">
            <span class="dist-label" :title="p.name">{{ p.name }}</span>
            <div class="dist-track"><div class="dist-bar" :style="{ width: distWidth(p.dealAmount, productMax) + '%', background: 'var(--loan-accent)' }"></div></div>
            <span class="dist-num mono">¥{{ fmtAmount(p.dealAmount) }}</span>
          </div>
        </div>
        <AppEmpty v-else title="暂无产品成交数据" />
      </div>
    </div>
  </div>
</template>

<script setup>
defineOptions({ name: '_report_overview' });
import { ref, computed, onMounted } from 'vue';
import AppTrendChart from '@/components/AppTrendChart.vue';
import AppEmpty from '@/components/AppEmpty.vue';
import { reportOverview, orderTrend, rewardTrend } from '@/api/report';

const ICONS = {
  client: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm0 2c-4 0-7 2-7 5v1h14v-1c0-3-3-5-7-5Z',
  lead: 'M13 2 4 14h6l-1 8 9-12h-6l1-8Z',
  order: 'M6 2h9l5 5v15H6V2Zm9 1.5V8h4.5',
  deal: 'M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20Zm-1 14-4-4 1.4-1.4L11 13.2l5.6-5.6L18 9l-7 7Z',
  amount: 'M7 4l5 7 5-7h2l-6 8.5V20h-2v-7.5L5 4h2Z',
  reward: 'M20 12v8H4v-8h16ZM4 8h16v4H4V8Zm8 0a3 3 0 0 0 0-6 3 3 0 0 0 0 6Z',
  screening: 'M10 2a8 8 0 1 0 5.3 14l5.3 5.3-1.4 1.4L13.9 17A8 8 0 0 0 10 2Zm0 2a6 6 0 1 1 0 12 6 6 0 0 1 0-12Z',
  trend: 'M3 17l6-6 4 4 8-8M21 7h-5M21 7v5',
};

function fmtAmount(v) {
  return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}
function fmtInt(v) {
  return Number(v || 0).toLocaleString('zh-CN');
}

const today = new Date().toLocaleDateString('zh-CN');

const loadingOv = ref(false);
const loadingT = ref(false);
const overview = ref({});

const statCards = computed(() => [
  { key: 'client', label: '客户数', value: fmtInt(overview.value.clientCount), delta: overview.value.clientCountDelta, icon: ICONS.client, color: '#3b82f6' },
  { key: 'lead', label: '线索数', value: fmtInt(overview.value.leadCount), delta: overview.value.leadCountDelta, icon: ICONS.lead, color: '#6366f1' },
  { key: 'order', label: '工单数', value: fmtInt(overview.value.orderCount), delta: overview.value.orderCountDelta, icon: ICONS.order, color: '#14b8a6' },
  { key: 'deal', label: '成交单数', value: fmtInt(overview.value.dealOrderCount), delta: overview.value.dealOrderCountDelta, icon: ICONS.deal, color: '#22c55e' },
  { key: 'dealAmount', label: '成交金额', value: '¥' + fmtAmount(overview.value.dealAmountSum), delta: overview.value.dealAmountSumDelta, icon: ICONS.amount, color: '#10b981' },
  { key: 'reward', label: '奖励单数', value: fmtInt(overview.value.rewardCount), delta: overview.value.rewardCountDelta, icon: ICONS.reward, color: '#f59e0b' },
  { key: 'rewardAmount', label: '奖励金额', value: '¥' + fmtAmount(overview.value.rewardAmountSum), delta: overview.value.rewardAmountSumDelta, icon: ICONS.trend, color: '#f97316' },
  { key: 'screening', label: '初筛报告', value: fmtInt(overview.value.screeningCount), delta: overview.value.screeningCountDelta, icon: ICONS.screening, color: '#a855f7' },
]);

/* 漏斗 */
const funnel = computed(() => overview.value.funnel || []);
const funnelMax = computed(() => {
  const vals = funnel.value.map((s) => Number(s.value || 0));
  return vals.length ? Math.max(...vals, 1) : 1;
});
function funnelWidth(v) {
  return Math.max(8, (Number(v || 0) / funnelMax.value) * 100);
}
function funnelPct(v) {
  return ((Number(v || 0) / funnelMax.value) * 100).toFixed(0) + '%';
}
function funnelColor(i) {
  const palette = ['#3b82f6', '#6366f1', '#14b8a6', '#22c55e', '#f59e0b'];
  return palette[i % palette.length];
}
function convRate(prev, cur) {
  if (!prev) return '0%';
  return ((Number(cur || 0) / Number(prev)) * 100).toFixed(1) + '%';
}

/* 分布 */
const customerGroupDist = computed(() => overview.value.customerGroupDist || []);
const orderStatusDist = computed(() => overview.value.orderStatusDist || []);
const productDist = computed(() => overview.value.productDist || []);
const groupMax = computed(() => Math.max(1, ...customerGroupDist.value.map((g) => Number(g.value || 0))));
const statusMax = computed(() => Math.max(1, ...orderStatusDist.value.map((s) => Number(s.value || 0))));
const productMax = computed(() => Math.max(1, ...productDist.value.map((p) => Number(p.dealAmount || 0))));
function distWidth(v, max) {
  return Math.max(4, (Number(v || 0) / max) * 100);
}
function groupLabel(g) {
  return { ENTERPRISE: '企业客户', PERSONAL: '个人客户', UNKNOWN: '未分组' }[g] || g || '未分组';
}
function statusLabel(s) {
  return { NEW: '新建', IN_SERVICE: '服务中', DEAL: '已成交', CANCEL: '已取消', REFUND: '已退款' }[s] || s || '—';
}
function statusColor(s) {
  return { NEW: '#94a3b8', IN_SERVICE: '#3b82f6', DEAL: '#22c55e', CANCEL: '#f87171', REFUND: '#f59e0b' }[s] || '#94a3b8';
}

/* 趋势 */
const orderTrendData = ref([]);
const rewardTrendData = ref([]);
const orderChartData = computed(() => (orderTrendData.value || []).map((r) => ({ label: r.month, value: r.count })));
const rewardChartData = computed(() => (rewardTrendData.value || []).map((r) => ({ label: r.month, value: r.count })));
const orderTotalCount = computed(() => (orderTrendData.value || []).reduce((a, r) => a + Number(r.count || 0), 0));
const orderTotalAmount = computed(() => (orderTrendData.value || []).reduce((a, r) => a + Number(r.amount || 0), 0));
const rewardTotalCount = computed(() => (rewardTrendData.value || []).reduce((a, r) => a + Number(r.count || 0), 0));
const rewardTotalAmount = computed(() => (rewardTrendData.value || []).reduce((a, r) => a + Number(r.amount || 0), 0));

onMounted(async () => {
  loadingOv.value = true;
  loadingT.value = true;
  try {
    const [ov, ot, rt] = await Promise.all([reportOverview(), orderTrend(12), rewardTrend(12)]);
    overview.value = ov.data || {};
    orderTrendData.value = ot.data || [];
    rewardTrendData.value = rt.data || [];
  } catch (e) { /* 拦截器已提示 */ } finally {
    loadingOv.value = false;
    loadingT.value = false;
  }
});
</script>

<style scoped>
.range-hint {
  font-size: 12px;
  color: var(--loan-text-secondary, #8a94a6);
  background: var(--loan-surface, rgba(255,255,255,.04));
  border: 1px solid var(--loan-border, rgba(255,255,255,.08));
  padding: 4px 10px;
  border-radius: 999px;
}
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 14px;
  margin-bottom: 16px;
}
.stat-card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  background: var(--loan-card-bg, #fff);
  border: 1px solid var(--loan-border, #e5e8f0);
  border-left: 3px solid var(--accent, var(--loan-primary));
  border-radius: var(--loan-radius-md, 10px);
  padding: 14px 16px;
  overflow: hidden;
}
.stat-card::after {
  content: '';
  position: absolute;
  right: -20px;
  top: -20px;
  width: 70px;
  height: 70px;
  border-radius: 50%;
  background: var(--accent, var(--loan-primary));
  opacity: 0.08;
}
.stat-icon {
  flex-shrink: 0;
  width: 38px;
  height: 38px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: var(--accent, var(--loan-primary));
}
.stat-body { flex: 1; min-width: 0; }
.stat-label {
  font-size: 12px;
  color: var(--loan-text-secondary, #8a94a6);
  margin-bottom: 4px;
}
.stat-value {
  font-size: 22px;
  font-weight: 700;
  color: var(--loan-text, #1c2433);
  line-height: 1.1;
}
/* 环比标签：绿涨红跌 */
.stat-delta {
  position: absolute;
  right: 12px;
  bottom: 12px;
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-size: 11px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 8px;
}
.stat-delta .delta-cap { font-weight: 500; opacity: .7; margin-left: 2px; }
.stat-delta.up { background: rgba(52, 211, 153, 0.14); color: var(--loan-success, #34d399); }
.stat-delta.down { background: rgba(248, 113, 113, 0.14); color: var(--loan-danger, #f87171); }

.panel-title {
  font-size: 14px;
  font-weight: 600;
  margin: 0 0 14px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--loan-border, #e5e8f0);
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.panel-title small {
  font-size: 11px;
  font-weight: 400;
  color: var(--loan-text-secondary, #8a94a6);
}

/* 漏斗 */
.funnel-card { margin-bottom: 16px; }
.funnel { display: flex; flex-direction: column; gap: 10px; }
.funnel-row {
  display: grid;
  grid-template-columns: 120px 1fr 96px;
  align-items: center;
  gap: 12px;
}
.funnel-stage { display: flex; flex-direction: column; }
.funnel-name { font-size: 13px; font-weight: 600; color: var(--loan-text, #1c2433); }
.funnel-value { font-size: 12px; color: var(--loan-text-secondary, #8a94a6); }
.funnel-track {
  height: 30px;
  background: var(--loan-surface, rgba(255,255,255,.04));
  border-radius: 6px;
  display: flex;
  align-items: center;
  overflow: hidden;
}
.funnel-bar {
  height: 100%;
  margin: 0 auto;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: width .4s ease;
  min-width: 30px;
}
.funnel-pct {
  font-size: 11px;
  font-weight: 700;
  color: #fff;
  white-space: nowrap;
}
.funnel-conv { text-align: right; }
.conv-badge {
  font-size: 11px;
  font-weight: 600;
  color: var(--loan-text-secondary, #8a94a6);
  background: var(--loan-surface, rgba(255,255,255,.05));
  padding: 2px 8px;
  border-radius: 8px;
}
.conv-badge.root { color: var(--loan-primary); }
.conv-badge.muted { opacity: .5; }

/* 趋势 */
.trend-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 16px;
}
.trend-foot {
  display: flex;
  justify-content: space-between;
  margin-top: 10px;
  font-size: 12px;
  color: var(--loan-text-secondary, #8a94a6);
}
.trend-foot b { color: var(--loan-text, #1c2433); }

/* 分布 */
.dist-grid {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 16px;
}
.card-product { grid-column: span 1; }
.dist-bars { display: flex; flex-direction: column; gap: 12px; }
.dist-row {
  display: grid;
  grid-template-columns: 84px 1fr 96px;
  align-items: center;
  gap: 10px;
}
.dist-label {
  font-size: 12px;
  color: var(--loan-text, #1c2433);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.dist-track {
  height: 14px;
  background: var(--loan-surface, rgba(255,255,255,.04));
  border-radius: 7px;
  overflow: hidden;
}
.dist-bar {
  height: 100%;
  border-radius: 7px;
  transition: width .4s ease;
  min-width: 4px;
}
.dist-num {
  font-size: 12px;
  font-weight: 600;
  color: var(--loan-text, #1c2433);
  text-align: right;
}

.mono { font-family: "SF Mono", Menlo, Consolas, monospace; }

@media (max-width: 1100px) {
  .trend-grid { grid-template-columns: 1fr; }
  .dist-grid { grid-template-columns: 1fr; }
}
@media (max-width: 700px) {
  .funnel-row { grid-template-columns: 90px 1fr 70px; }
  .dist-row { grid-template-columns: 70px 1fr 80px; }
}
</style>
