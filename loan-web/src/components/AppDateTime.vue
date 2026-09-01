<template>
  <el-date-picker
    :model-value="modelValue"
    :type="type"
    :placeholder="placeholder"
    :start-placeholder="startPlaceholder"
    :end-placeholder="endPlaceholder"
    :value-format="valueFormat"
    :format="format"
    :range-separator="rangeSeparator"
    :shortcuts="resolvedShortcuts"
    :disabled-date="disabledDate"
    :clearable="clearable"
    style="width: 100%"
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
 * 用法：
 *   <AppDateTime v-model="form.startTime" type="datetime" placeholder="选择时间" />
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
  clearable: { type: Boolean, default: true },
  /** 是否使用默认快捷选项（今日/近 7 天/近 30 天） */
  shortcuts: { type: Boolean, default: false },
  disabledDate: { type: Function, default: undefined },
});

const emit = defineEmits(['update:modelValue']);

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
