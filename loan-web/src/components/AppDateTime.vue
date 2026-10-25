<template>
  <el-date-picker
    :model-value="modelValue"
    :class="['app-date-time-picker', `app-date-time-picker--${type}`]"
    :type="type"
    :placeholder="placeholder"
    :start-placeholder="startPlaceholder"
    :end-placeholder="endPlaceholder"
    :value-format="valueFormat"
    :format="resolvedFormat"
    :range-separator="rangeSeparator"
    :time-format="timeFormat"
    :default-time="defaultTime"
    :shortcuts="resolvedShortcuts"
    :disabled-date="disabledDate"
    :clearable="clearable"
    :style="resolvedStyle"
    @update:model-value="(v) => $emit('update:modelValue', v)"
  />
</template>

<script setup>
import { computed } from 'vue';

/**
 * 日期/时间选择器（公共组件）。
 *
 * <p>统一 el-date-picker 的常用配置：默认宽度、valueFormat、format、快捷选项。
 * type 支持 date / datetime / daterange / datetimerange / month / year 等。
 *
 * <p><b>宽度策略（2026-10-20 第二次收口，见 D83 根因 B）</b>：
 * 缺省宽度<b>按 type 定死</b>，不再回落 100%。原因：上一版把 3 个日期组件收敛成 1 个之后，
 * 缺省仍是 100%，导致"是否铺满"完全依赖每个调用处记得传 width —— 漏一处就复发一处
 * （服务总览日期框连修 4 次仍铺满，即此因）。现在漏传 width 也只会是固定窄宽，不会撑满整行。
 *
 * <p>表单内（el-form-item）需要铺满时显式传 <code>width="100%"</code>；
 * 筛选条/工具栏直接用缺省值即可。
 *
 * 用法：
 *   <AppDateTime v-model="form.startTime" type="datetime" width="100%" />
 *   <AppDateTime v-model="range" type="daterange" />
 */
const props = defineProps({
  modelValue: { type: [String, Array, Date, Number], default: null },
  type: { type: String, default: 'date' },
  placeholder: { type: String, default: '请选择日期' },
  startPlaceholder: { type: String, default: '开始日期' },
  endPlaceholder: { type: String, default: '结束日期' },
  valueFormat: { type: String, default: 'YYYY-MM-DD' },
  format: { type: String, default: 'YYYY-MM-DD' },
  rangeSeparator: { type: String, default: '至' },
  /** datetimerange 面板时间列格式（如 HH:mm） */
  timeFormat: { type: String, default: 'HH:mm:ss' },
  /** datetimerange 起止默认时刻（[startDate, endDate]，仅带时分秒） */
  defaultTime: { type: Array, default: undefined },
  clearable: { type: Boolean, default: true },
  /**
   * 宽度（如 '260px'）；数字按 px 处理。
   * 缺省<b>不再回落 100%</b>，而是按 type 取 {@link DEFAULT_WIDTH} 的固定值（D83 根因 B）。
   * 表单内需要铺满时显式传 '100%'。
   */
  width: { type: [String, Number], default: '' },
  /** 是否使用默认快捷选项（今日/近 7 天/近 30 天） */
  shortcuts: { type: Boolean, default: false },
  disabledDate: { type: Function, default: undefined },
});

const emit = defineEmits(['update:modelValue']);

/**
 * 各 type 的缺省宽度（D83 根因 B）。
 * 取值原则：刚好容纳该 type 的完整内容，既不铺满整行、也不截断。
 */
const DEFAULT_WIDTH = Object.freeze({
  date: '200px',
  datetime: '220px',
  daterange: '260px',
  datetimerange: '360px',
  month: '180px',
  year: '180px',
  dates: '260px',
  monthrange: '300px',
});

/** 解析宽度：数字 → px；空串按 type 取缺省固定值（不再回落 100%）。 */
const resolvedWidth = computed(() => {
  if (props.width === '' || props.width == null) {
    return DEFAULT_WIDTH[props.type] || '200px';
  }
  return typeof props.width === 'number' ? `${props.width}px` : String(props.width);
});

/**
 * flex 容器里 inline width 会被 flex-grow 拉伸覆盖（服务总览日期框铺满的第二个成因），
 * 这里显式声明不参与伸缩。
 */
const resolvedStyle = computed(() => ({
  width: resolvedWidth.value,
  flex: '0 0 auto',
}));

const resolvedFormat = computed(() => ({ datetime: 'YYYY-MM-DD HH:mm', datetimerange: 'YYYY-MM-DD HH:mm' }[props.type] || props.format));

/** 默认快捷选项（按 valueFormat 输出） */
const resolvedShortcuts = computed(() => {
  if (!props.shortcuts) return undefined;
  const fmt = (d) => {
    const pad = (n) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
  };
  const today = new Date();
  const ago = (days) => {
    const d = new Date();
    d.setDate(d.getDate() - days);
    return d;
  };
  return [
    { text: '今日', value: () => fmt(today) },
    { text: '近 7 天', value: () => [fmt(ago(6)), fmt(today)] },
    { text: '近 30 天', value: () => [fmt(ago(29)), fmt(today)] },
  ];
});
</script>

<style scoped>
.app-date-time-picker {
  min-width: 180px;
}

.app-date-time-picker--daterange,
.app-date-time-picker--datetimerange {
  min-width: 260px;
}

@media (max-width: 760px) {
  .app-date-time-picker,
  .app-date-time-picker--daterange,
  .app-date-time-picker--datetimerange {
    min-width: 0;
  }
}
</style>
