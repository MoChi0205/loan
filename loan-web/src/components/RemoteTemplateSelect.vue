<template>
  <el-select :model-value="modelValue" filterable remote clearable :remote-method="search" :loading="loading" placeholder="输入模版名称搜索" style="width:100%" @update:model-value="onChange" @visible-change="v => { if (v && !items.length) search('') }">
    <el-option v-for="item in items" :key="item.value" :label="item.label" :value="item.value" />
    <div v-if="!finished && items.length" class="remote-more" @mousedown.prevent @click="loadMore">{{ loading ? '加载中…' : '加载更多' }}</div>
    <div v-else-if="error" class="remote-more is-error" @mousedown.prevent @click="search('')">加载失败，点击重试</div>
  </el-select>
</template>
<script setup>
import { pageTemplate } from '@/api/strategyTemplate';
import { useRemoteOptions } from '@/composables/useRemoteOptions';
const props = defineProps({ modelValue: { type: String, default: '' }, customerGroup: { type: String, default: '' }, activeOnly: { type: Boolean, default: true } });
const emit = defineEmits(['update:modelValue', 'selected']);
const { items, loading, finished, error, search, loadMore } = useRemoteOptions(
  params => pageTemplate({ ...params, customerGroup: props.customerGroup || undefined, status: props.activeOnly ? 'ACTIVE' : undefined }),
  { normalize: t => ({ value: t.templateCode, label: `${t.templateName || '未命名模版'} · ${t.customerGroup === 'PERSONAL' ? '个人' : '企业'}`, raw: t }) },
);
function onChange(value) {
  emit('update:modelValue', value);
  emit('selected', items.value.find(item => item.value === value)?.raw || null);
}
</script>
<style scoped>.remote-more{min-height:36px;display:flex;align-items:center;justify-content:center;color:var(--loan-primary);cursor:pointer;font-size:13px}.remote-more.is-error{color:var(--loan-danger)}</style>
