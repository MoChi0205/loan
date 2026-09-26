<template>
  <el-date-picker
    :model-value="modelValue"
    type="datetimerange"
    :value-format="valueFormat"
    format="YYYY-MM-DD HH:mm"
    time-format="HH:mm"
    :default-time="defaultTime"
    range-separator="至"
    :start-placeholder="startPlaceholder"
    :end-placeholder="endPlaceholder"
    :clearable="clearable"
    :disabled="disabled"
    :teleported="true"
    popper-class="service-time-picker"
    class="service-date-time-range"
    @update:model-value="$emit('update:modelValue', $event)"
    @change="$emit('change', $event)"
  />
</template>

<script setup>
defineProps({
  modelValue: { type: Array, default: () => [] },
  valueFormat: { type: String, default: 'YYYY-MM-DDTHH:mm:ss' },
  defaultTime: { type: Array, default: () => [new Date(2000, 0, 1, 9, 0), new Date(2000, 0, 1, 10, 0)] },
  startPlaceholder: { type: String, default: '开始日期时间' },
  endPlaceholder: { type: String, default: '结束日期时间' },
  clearable: { type: Boolean, default: true },
  disabled: { type: Boolean, default: false },
});
defineEmits(['update:modelValue', 'change']);
</script>

<style scoped>
.service-date-time-range { width: 100%; }
</style>
