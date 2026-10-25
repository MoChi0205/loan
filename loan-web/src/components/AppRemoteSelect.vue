<template>
  <AppSelect
    :model-value="modelValue"
    filterable
    remote
    clearable
    :remote-method="search"
    :loading="loading"
    :placeholder="placeholder"
    :style="style"
    @update:model-value="onChange"
    @visible-change="onVisibleChange"
  >
    <el-option v-for="item in items" :key="item.value" :label="item.label" :value="item.value" />
    <div v-if="!finished && items.length" class="remote-more" @mousedown.prevent @click="loadMore">
      {{ loading ? '加载中…' : '加载更多' }}
    </div>
    <div v-else-if="error" class="remote-more is-error" @mousedown.prevent @click="search('')">
      加载失败，点击重试
    </div>
  </AppSelect>
</template>

<script setup>
import AppSelect from './AppSelect.vue';

defineProps({
  modelValue: { type: [String, Number], default: '' },
  items: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  finished: { type: Boolean, default: true },
  error: { type: Boolean, default: false },
  placeholder: { type: String, default: '输入名称搜索' },
  style: { type: [String, Object], default: 'width: 100%' },
  search: { type: Function, required: true },
  loadMore: { type: Function, required: true },
});

const emit = defineEmits(['update:modelValue', 'visible-change']);
function onChange(value) { emit('update:modelValue', value); }
function onVisibleChange(value) { emit('visible-change', value); }
</script>

<style scoped>
.remote-more { min-height: 36px; display: flex; align-items: center; justify-content: center; color: var(--loan-primary); cursor: pointer; font-size: 13px; }
.remote-more.is-error { color: var(--loan-danger); }
</style>
