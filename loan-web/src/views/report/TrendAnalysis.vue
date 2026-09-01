<template>
  <div class="report-page">
    <div class="loan-page-header">
      <div>
        <h2 class="loan-page-title">趋势分析</h2>
        <p class="loan-page-subtitle">近 12 个月成交 + 奖励趋势分析</p>
      </div>
    </div>

    <div class="trend-grid" v-loading="loadingT">
      <div class="loan-card">
        <div class="panel-title">成交趋势（近 12 个月）</div>
        <!-- ECharts 折线 + 面积：成交单数 / 成交金额（双 Y 轴） -->
        <AppEChart :option="orderEChartOption" height="260px" />
        <table class="trend-table">
          <thead><tr><th>月份</th><th>成交单数</th><th>成交金额（元）</th></tr></thead>
          <tbody>
            <tr v-for="r in orderTrendData" :key="r.month">
              <td>{{ r.month }}</td><td>{{ r.count }}</td>
              <td class="mono">¥{{ fmtAmount(r.amount) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="loan-card">
        <div class="panel-title">奖励趋势（近 12 个月）</div>
        <!-- ECharts 折线 + 面积：奖励单数 / 奖励金额（双 Y 轴） -->
        <AppEChart :option="rewardEChartOption" height="260px" />
        <table class="trend-table">
          <thead><tr><th>月份</th><th>奖励单数</th><th>奖励金额（元）</th></tr></thead>
          <tbody>
            <tr v-for="r in rewardTrendData" :key="r.month">
              <td>{{ r.month }}</td><td>{{ r.count }}</td>
              <td class="mono">¥{{ fmtAmount(r.amount) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup>
defineOptions({ name: '_report_trend' });
import { ref, computed, onMounted } from 'vue';
import AppEChart from '@/components/AppEChart.vue';
import { orderTrend, rewardTrend } from '@/api/report';

function fmtAmount(v) {
  return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

const loadingT = ref(false);
const orderTrendData = ref([]);
const rewardTrendData = ref([]);

/** ECharts option：成交趋势（折线 + 面积，双 Y 轴） */
const orderEChartOption = computed(() => {
  const months = (orderTrendData.value || []).map((r) => r.month);
  const counts = (orderTrendData.value || []).map((r) => r.count || 0);
  const amounts = (orderTrendData.value || []).map((r) => Number(r.amount || 0));
  return {
    tooltip: { trigger: 'axis' },
    legend: { data: ['成交单数', '成交金额'], right: 0, top: 0 },
    grid: { left: 40, right: 60, top: 36, bottom: 24, containLabel: true },
    xAxis: { type: 'category', data: months, boundaryGap: false },
    yAxis: [
      { type: 'value', name: '单数', position: 'left' },
      { type: 'value', name: '金额', position: 'right', axisLabel: { formatter: (v) => v >= 10000 ? (v / 10000) + '万' : v } },
    ],
    series: [
      {
        name: '成交单数', type: 'line', smooth: true, yAxisIndex: 0,
        data: counts, symbol: 'circle', symbolSize: 6,
        areaStyle: { opacity: 0.18 },
      },
      {
        name: '成交金额', type: 'line', smooth: true, yAxisIndex: 1,
        data: amounts, symbol: 'circle', symbolSize: 6,
        lineStyle: { type: 'dashed' },
      },
    ],
  };
});

/** ECharts option：奖励趋势 */
const rewardEChartOption = computed(() => {
  const months = (rewardTrendData.value || []).map((r) => r.month);
  const counts = (rewardTrendData.value || []).map((r) => r.count || 0);
  const amounts = (rewardTrendData.value || []).map((r) => Number(r.amount || 0));
  return {
    tooltip: { trigger: 'axis' },
    legend: { data: ['奖励单数', '奖励金额'], right: 0, top: 0 },
    grid: { left: 40, right: 60, top: 36, bottom: 24, containLabel: true },
    xAxis: { type: 'category', data: months, boundaryGap: false },
    yAxis: [
      { type: 'value', name: '单数', position: 'left' },
      { type: 'value', name: '金额', position: 'right', axisLabel: { formatter: (v) => v >= 10000 ? (v / 10000) + '万' : v } },
    ],
    series: [
      {
        name: '奖励单数', type: 'line', smooth: true, yAxisIndex: 0,
        data: counts, symbol: 'circle', symbolSize: 6,
        areaStyle: { opacity: 0.18 },
      },
      {
        name: '奖励金额', type: 'line', smooth: true, yAxisIndex: 1,
        data: amounts, symbol: 'circle', symbolSize: 6,
        lineStyle: { type: 'dashed' },
      },
    ],
  };
});

onMounted(async () => {
  loadingT.value = true;
  try {
    const [ot, rt] = await Promise.all([orderTrend(12), rewardTrend(12)]);
    orderTrendData.value = ot.data || [];
    rewardTrendData.value = rt.data || [];
  } catch (e) { /* 拦截器已提示 */ } finally {
    loadingT.value = false;
  }
});
</script>

<style scoped>
.trend-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
@media (max-width: 900px) {
  .trend-grid { grid-template-columns: 1fr; }
}
.panel-title {
  font-size: 14px;
  font-weight: 600;
  margin: 0 0 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--loan-border, #e5e8f0);
}
.trend-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.trend-table th, .trend-table td {
  padding: 6px 8px;
  text-align: right;
  border-bottom: 1px solid var(--loan-border, #f0f2f7);
}
.trend-table th:first-child, .trend-table td:first-child { text-align: left; color: var(--loan-text-secondary, #8a94a6); }
.mono { font-family: "SF Mono", Menlo, Consolas, monospace; }
</style>
