<template>
  <div class="app-trend-chart" :style="{ height: height }">
    <svg v-if="points.length" :viewBox="`0 0 ${viewW} ${viewH}`" preserveAspectRatio="none" class="chart-svg" role="img" :aria-label="`${title}趋势图，${points.length} 个数据点`">
      <!-- 网格线 -->
      <g class="grid" stroke="var(--loan-border, rgba(255,255,255,.07))" stroke-width="1">
        <line v-for="gy in gridY" :key="gy" :x1="padL" :y1="gy" :x2="viewW - padR" :y2="gy" />
      </g>

      <!-- 面积渐变 -->
      <defs>
        <linearGradient :id="gradId" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" :stop-color="color" stop-opacity="0.22" />
          <stop offset="100%" :stop-color="color" stop-opacity="0.02" />
        </linearGradient>
      </defs>

      <!-- 面积 -->
      <path :d="areaPath" :fill="`url(#${gradId})`" stroke="none" />

      <!-- 折线 -->
      <path :d="linePath" fill="none" :stroke="color" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />

      <!-- 数据点 -->
      <circle v-for="(p, i) in points" :key="i" :cx="p.x" :cy="p.y" r="3" :fill="color" stroke="var(--loan-card-bg, #fff)" stroke-width="1.5">
        <title>{{ p.label }}：{{ p.value }}</title>
      </circle>
    </svg>

    <div v-else class="chart-empty">暂无趋势数据</div>

    <!-- X 轴月份 -->
    <div v-if="points.length" class="chart-axis">
      <span v-for="(p, i) in xLabels" :key="i" class="axis-label">{{ p }}</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue';

/**
 * 轻量 SVG 折线图组件（零依赖，不引入 echarts）。
 *
 * <p>用法：
 *   <AppTrendChart title="成交趋势" :data="[{label:'2026-01',value:2},...]" color="#34d399" height="160px" />
 *
 * props:
 *   data  [{label, value}] 数据点
 *   color 折线颜色（默认主色）
 *   height 组件高度
 *   title  aria 无障碍标题
 */
const props = defineProps({
  data: { type: Array, default: () => [] },
  color: { type: String, default: 'var(--loan-primary, #3b82f6)' },
  height: { type: String, default: '150px' },
  title: { type: String, default: '' },
});

const viewW = 600;
const viewH = 160;
const padL = 8;
const padR = 8;
const padT = 14;
const padB = 6;

const gradId = `trend-grad-${Math.random().toString(36).slice(2, 8)}`;

/** 有效数据点（value 为数字） */
const points = computed(() => {
  const raw = props.data || [];
  const max = Math.max(...raw.map((d) => Number(d.value) || 0), 1);
  const min = 0;
  const n = raw.length;
  return raw.map((d, i) => {
    const x = n <= 1 ? (viewW - padL - padR) / 2 + padL : padL + ((viewW - padL - padR) * i) / (n - 1);
    const y = padT + (viewH - padT - padB) * (1 - (Number(d.value) - min) / (max - min || 1));
    return { x, y, label: d.label, value: d.value };
  });
});

const linePath = computed(() => {
  const pts = points.value;
  if (pts.length < 2) return '';
  return pts.map((p, i) => `${i === 0 ? 'M' : 'L'}${p.x.toFixed(1)},${p.y.toFixed(1)}`).join(' ');
});

const areaPath = computed(() => {
  const pts = points.value;
  if (pts.length < 2) return '';
  const line = linePath.value;
  const last = pts[pts.length - 1];
  const first = pts[0];
  return `${line} L${last.x.toFixed(1)},${(viewH - padB).toFixed(1)} L${first.x.toFixed(1)},${(viewH - padB).toFixed(1)} Z`;
});

const gridY = computed(() => {
  const rows = 3;
  return Array.from({ length: rows + 1 }, (_, i) => padT + ((viewH - padT - padB) * i) / rows);
});

/** X 轴标签（最多 6 个，避免拥挤） */
const xLabels = computed(() => {
  const pts = props.data || [];
  if (pts.length <= 6) return pts.map((d) => d.label);
  const step = Math.ceil(pts.length / 6);
  return pts.map((d, i) => (i % step === 0 ? d.label : '')).filter((l, i, arr) => l && arr.indexOf(l) === i);
});
</script>

<style scoped>
.app-trend-chart {
  width: 100%;
  display: flex;
  flex-direction: column;
}
.chart-svg {
  width: 100%;
  height: 100%;
  display: block;
}
.chart-empty {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--loan-text-muted, #8a94a6);
  font-size: 13px;
  border: 1px dashed var(--loan-border, rgba(255,255,255,.15));
  border-radius: 8px;
}
.chart-axis {
  display: flex;
  justify-content: space-between;
  margin-top: 4px;
  font-size: 11px;
  color: var(--loan-text-muted, #8a94a6);
}
.axis-label {
  flex: 1;
  text-align: center;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
