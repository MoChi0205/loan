<template>
  <div ref="el" class="app-echart" :style="{ height: height }" v-loading="loading" />
</template>

<script setup>
/**
 * ECharts 包装组件（管理端通用图表）。
 *
 * <p>特点：
 *   - 按需引入 echarts 子模块（折线 / 柱状 / 饼图 + 网格/提示/图例/标题），体积比全量小
 *   - 自动跟当前主题：documentElement 的 data-theme="dark" 切暗色，"light" 切亮色
 *   - 颜色优先用 CSS 变量（--loan-primary / --loan-accent 等），未传时回退到默认
 *   - option 变化自动 setOption 增量更新；组件卸载时 dispose
 *
 * 用法：
 *   <AppEChart :option="chartOption" height="280px" />
 *
 * props:
 *   option   ECharts 配置对象（必填）
 *   height   容器高度（默认 320px）
 *   loading  显示 v-loading 遮罩
 */
import { ref, onMounted, onBeforeUnmount, watch, nextTick } from 'vue';
import * as echarts from 'echarts/core';
import { LineChart, BarChart, PieChart } from 'echarts/charts';
import {
  GridComponent,
  TooltipComponent,
  LegendComponent,
  TitleComponent,
  ToolboxComponent,
} from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';

echarts.use([
  LineChart,
  BarChart,
  PieChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  TitleComponent,
  ToolboxComponent,
  CanvasRenderer,
]);

const props = defineProps({
  option: { type: Object, required: true },
  height: { type: String, default: '320px' },
  loading: { type: Boolean, default: false },
});

const el = ref(null);
let chart = null;
let observer = null;

/** 读 CSS 变量色值（带 trim） */
function cssVar(name, fallback) {
  if (typeof window === 'undefined') return fallback;
  const v = getComputedStyle(document.documentElement).getPropertyValue(name);
  return v ? v.trim() : fallback;
}

/** 构造主题：跟随 data-theme 切换 */
function buildTheme() {
  const isDark = document.documentElement.dataset.theme === 'dark';
  return {
    textStyle: { color: cssVar('--loan-text', isDark ? '#e5e7eb' : '#1a1a2e'), fontFamily: 'inherit' },
    grid: { left: 36, right: 16, top: 24, bottom: 28, containLabel: true },
    tooltip: {
      trigger: 'axis',
      backgroundColor: isDark ? 'rgba(17,24,39,.92)' : 'rgba(255,255,255,.95)',
      borderColor: cssVar('--loan-border', '#e5e7eb'),
      textStyle: { color: cssVar('--loan-text', '#1a1a2e') },
    },
    legend: { textStyle: { color: cssVar('--loan-text-secondary', '#6b7280') } },
    color: [
      cssVar('--loan-primary', '#3b82f6'),
      cssVar('--loan-accent', '#f59e0b'),
      cssVar('--loan-success', '#10b981'),
      cssVar('--loan-danger', '#ef4444'),
      cssVar('--loan-info', '#06b6d4'),
    ],
  };
}

function applyOption() {
  if (!chart) return;
  chart.setOption({ ...buildTheme(), ...props.option }, true);
}

function init() {
  if (!el.value) return;
  chart = echarts.init(el.value);
  applyOption();
  window.addEventListener('resize', resize);
  // 主题切换（ThemeSwitch 改 data-theme）时重渲
  observer = new MutationObserver(() => applyOption());
  observer.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] });
}

function resize() {
  chart?.resize();
}

onMounted(() => nextTick(init));

watch(
  () => props.option,
  () => applyOption(),
  { deep: true },
);

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize);
  observer?.disconnect();
  observer = null;
  chart?.dispose();
  chart = null;
});
</script>

<style scoped>
.app-echart {
  width: 100%;
  min-height: 120px;
}
</style>
