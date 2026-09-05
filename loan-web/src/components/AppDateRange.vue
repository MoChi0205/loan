<template>
  <div class="date-range-filter">
    <el-date-picker
      v-model="dateRange"
      type="daterange"
      :start-placeholder="startPlaceholder || '开始日期'"
      :end-placeholder="endPlaceholder || '结束日期'"
      value-format="YYYY-MM-DD"
      :shortcuts="shortcuts"
      :clearable="true"
      :style="{ width: width || '260px' }"
      @change="onChange"
    />
  </div>
</template>

<script setup>
import { ref, watch } from 'vue';

const props = defineProps({
  /** 绑定值，格式 [startDate, endDate] */
  modelValue: {
    type: Array,
    default: () => null,
  },
  /** 开始日期占位 */
  startPlaceholder: {
    type: String,
    default: '',
  },
  /** 结束日期占位 */
  endPlaceholder: {
    type: String,
    default: '',
  },
  /** 选择器宽度 */
  width: {
    type: String,
    default: '260px',
  },
});

const emit = defineEmits(['update:modelValue', 'change']);

const dateRange = ref(props.modelValue || null);

watch(() => props.modelValue, (val) => {
  dateRange.value = val || null;
});

const shortcuts = [
  {
    text: '最近7天',
    value: () => {
      const end = new Date();
      const start = new Date();
      start.setDate(start.getDate() - 6);
      return [start, end];
    },
  },
  {
    text: '最近30天',
    value: () => {
      const end = new Date();
      const start = new Date();
      start.setDate(start.getDate() - 29);
      return [start, end];
    },
  },
  {
    text: '本月',
    value: () => {
      const end = new Date();
      const start = new Date();
      start.setDate(1);
      return [start, end];
    },
  },
  {
    text: '上月',
    value: () => {
      const end = new Date();
      end.setDate(0); // 上月最后一天
      const start = new Date();
      start.setDate(0);
      start.setDate(1); // 上月第一天
      return [start, end];
    },
  },
];

function onChange(val) {
  emit('update:modelValue', val || null);
  emit('change', val || null);
}
</script>

<style scoped>
.date-range-filter {
  display: inline-flex;
  align-items: center;
}
</style>
