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

/** 跟随当前 data-theme 的图表文字/轴线色 */
const isDarkTheme = typeof document !== 'undefined' && document.documentElement.dataset.theme === 'dark';
const C_TEXT = isDarkTheme ? '#f3f4f6' : '#111827';
const C_TEXT_SUB = isDarkTheme ? '#9ca3af' : '#6b7280';
const C_BORDER = isDarkTheme ? 'rgba(255,255,255,0.12)' : '#e5e7eb';
const C_TOOLTIP_BG = isDarkTheme ? 'rgba(17,24,39,0.92)' : 'rgba(255,255,255,0.96)';

/** 统一构造双 Y 轴趋势图 option（折线 + 面积，图例顶置防重叠） */
function buildTrendOption({ months, countData, amountData, countName, amountName, countColor, amountColor, areaColor }) {
  return {
    tooltip: {
      trigger: 'axis',
      backgroundColor: C_TOOLTIP_BG,
      borderColor: C_BORDER,
      textStyle: { color: C_TEXT },
      formatter: (params) => {
        const head = `<div style="font-weight:600;margin-bottom:4px;">${params[0]?.axisValue || ''}</div>`;
        const rows = params.map((p) => {
          const isAmount = p.seriesName.includes('金额');
          const val = isAmount ? `¥${Number(p.value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}` : Number(p.value || 0).toLocaleString('zh-CN');
          return `<span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:${p.color};margin-right:6px;"></span>${p.seriesName}: <b>${val}</b>`;
        }).join('<br>');
        return head + rows;
      },
    },
    legend: {
      data: [countName, amountName],
      top: 0,
      left: 'center',
      itemGap: 24,
      icon: 'roundRect',
      itemWidth: 14,
      itemHeight: 4,
      textStyle: { fontSize: 12, color: C_TEXT },
    },
    grid: { left: 16, right: 80, top: 50, bottom: 24, containLabel: true },
    xAxis: {
      type: 'category',
      data: months,
      boundaryGap: false,
      axisLine: { lineStyle: { color: C_BORDER } },
      axisLabel: { color: C_TEXT_SUB },
    },
    yAxis: [
      {
        type: 'value',
        name: '单数',
        position: 'left',
        nameTextStyle: { align: 'right', padding: [0, 8, 0, 0], color: C_TEXT_SUB },
        splitLine: { lineStyle: { color: C_BORDER, type: 'dashed' } },
        axisLabel: { color: C_TEXT_SUB },
      },
      {
        type: 'value',
        name: '金额',
        position: 'right',
        offset: 12,
        nameTextStyle: { align: 'left', padding: [0, 0, 0, 8], color: C_TEXT_SUB },
        splitLine: { show: false },
        axisLabel: {
          color: C_TEXT_SUB,
          formatter: (v) => v >= 10000 ? (v / 10000).toFixed(0) + '万' : v,
        },
      },
    ],
    series: [
      {
        name: countName,
        type: 'line',
        smooth: true,
        yAxisIndex: 0,
        data: countData,
        symbol: 'circle',
        symbolSize: 6,
        itemStyle: { color: countColor },
        lineStyle: { width: 2.5, color: countColor },
        areaStyle: { color: areaColor, opacity: 0.22 },
      },
      {
        name: amountName,
        type: 'line',
        smooth: true,
        yAxisIndex: 1,
        data: amountData,
        symbol: 'circle',
        symbolSize: 6,
        itemStyle: { color: amountColor },
        lineStyle: { width: 2.5, type: 'dashed', color: amountColor },
      },
    ],
  };
}

/** ECharts option：成交趋势 */
const orderEChartOption = computed(() => {
  const months = (orderTrendData.value || []).map((r) => r.month);
  const counts = (orderTrendData.value || []).map((r) => r.count || 0);
  const amounts = (orderTrendData.value || []).map((r) => Number(r.amount || 0));
  return buildTrendOption({
    months,
    countData: counts,
    amountData: amounts,
    countName: '成交单数',
    amountName: '成交金额',
    countColor: '#3b82f6',
    amountColor: '#f59e0b',
    areaColor: 'rgba(59, 130, 246, 0.25)',
  });
});

/** ECharts option：奖励趋势 */
const rewardEChartOption = computed(() => {
  const months = (rewardTrendData.value || []).map((r) => r.month);
  const counts = (rewardTrendData.value || []).map((r) => r.count || 0);
  const amounts = (rewardTrendData.value || []).map((r) => Number(r.amount || 0));
  return buildTrendOption({
    months,
    countData: counts,
    amountData: amounts,
    countName: '奖励单数',
    amountName: '奖励金额',
    countColor: '#10b981',
    amountColor: '#f97316',
    areaColor: 'rgba(16, 185, 129, 0.25)',
  });
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
  border-bottom: 1px solid var(--loan-border);
}
.trend-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.trend-table th, .trend-table td {
  padding: 6px 8px;
  text-align: right;
  border-bottom: 1px solid var(--loan-border, var(--loan-surface));
}
.trend-table th:first-child, .trend-table td:first-child { text-align: left; color: var(--loan-text-secondary, var(--loan-text-muted)); }
.mono { font-family: "SF Mono", Menlo, Consolas, monospace; }
</style>
