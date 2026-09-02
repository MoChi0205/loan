<template>
  <el-select :model-value="modelValue" filterable remote clearable :remote-method="search" :loading="loading" :placeholder="placeholder" style="width:100%" @update:model-value="onChange" @visible-change="v => { if (v && !items.length) search('') }">
    <el-option v-for="item in items" :key="item.value" :label="item.label" :value="item.value" />
    <div v-if="!finished && items.length" class="remote-more" @mousedown.prevent @click="loadMore">{{ loading ? '加载中…' : '加载更多' }}</div>
    <div v-else-if="error" class="remote-more is-error" @mousedown.prevent @click="search('')">加载失败，点击重试</div>
  </el-select>
</template>
<script setup>
import { pageStrategy } from '@/api/channelStrategy';
import { useRemoteOptions } from '@/composables/useRemoteOptions';
const props = defineProps({ modelValue: { type: String, default: '' }, channelCode: { type: String, default: '' }, placeholder: { type: String, default: '输入策略名称搜索' } });
const emit = defineEmits(['update:modelValue', 'selected']);
const { items, loading, finished, error, search, loadMore } = useRemoteOptions(
  params => pageStrategy({ ...params, channelCode: props.channelCode || undefined }),
  { normalize: s => ({ value: s.strategyCode, label: `${s.strategyName || '未命名策略'} · ${s.bankProductName || '产品待补充'}`, raw: s }) },
);
function onChange(value) {
  emit('update:modelValue', value);
  emit('selected', items.value.find(item => item.value === value)?.raw || null);
}
</script>
<style scoped>.remote-more{min-height:36px;display:flex;align-items:center;justify-content:center;color:var(--loan-primary);cursor:pointer;font-size:13px}.remote-more.is-error{color:var(--loan-danger)}</style>
