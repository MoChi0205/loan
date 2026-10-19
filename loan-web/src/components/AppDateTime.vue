<template>
  <el-date-picker
    :model-value="modelValue"
    :class="['app-date-time-picker', `app-date-time-picker--${type}`]"
    :type="type"
    :placeholder="placeholder"
    :start-placeholder="startPlaceholder"
    :end-placeholder="endPlaceholder"
    :value-format="valueFormat"
    :format="format"
    :range-separator="rangeSeparator"
    :time-format="timeFormat"
    :default-time="defaultTime"
    :shortcuts="resolvedShortcuts"
    :disabled-date="disabledDate"
    :clearable="clearable"
    :style="{ width: resolvedWidth }"
    @update:model-value="(v) => $emit('update:modelValue', v)"
  />
</template>

<script setup>
import { computed } from 'vue';

/**
 * 日期/时间选择器（公共组件）。
 *
 * <p>统一 el-date-picker 的常用配置：默认宽度 100%、valueFormat、format、快捷选项。
 * type 支持 date / datetime / daterange / datetimerange / month / year 等。
 *
 * <p>宽度用 {@link width} prop 控制（2026-10-19）：组件内部宽度不再写死 width:100%，
 * 通过 style 透传的宽度会被内部 100% 覆盖导致筛选框撑满整行（客户列表成交起始/截止 bug）。
 *
 * 用法：
 *   <AppDateTime v-model="form.startTime" type="datetime" placeholder="选择时间" />
 *   <AppDateTime v-model="range" type="daterange" width="260px" />
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
  /** 宽度（如 '260px'）；数字按 px 处理；缺省 100%（配合 el-form-item 等流式布局）。 */
  width: { type: [String, Number], default: '' },
  /** 是否使用默认快捷选项（今日/近 7 天/近 30 天） */
  shortcuts: { type: Boolean, default: false },
  disabledDate: { type: Function, default: undefined },
});

const emit = defineEmits(['update:modelValue']);

/** 解析宽度：数字 → px；空串回落 100%。 */
const resolvedWidth = computed(() => {
  if (props.width === '' || props.width == null) return '100%';
  return typeof props.width === 'number' ? `${props.width}px` : String(props.width);
});

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
