<template>
  <el-select
    :model-value="modelValue"
    filterable remote clearable
    :remote-method="search"
    :loading="loading"
    :placeholder="placeholder"
    style="width: 100%"
    @update:model-value="$emit('update:modelValue', $event)"
    @visible-change="onVisibleChange"
  >
    <el-option v-for="item in items" :key="item.value" :label="item.label" :value="item.value" />
    <div v-if="!finished && items.length" class="remote-more" @mousedown.prevent @click="loadMore">
      {{ loading ? '加载中…' : '加载更多' }}
    </div>
    <div v-else-if="error" class="remote-more is-error" @mousedown.prevent @click="search('')">加载失败，点击重试</div>
  </el-select>
</template>

<script setup>
import { watch } from 'vue';
import { pageProducts, getProduct } from '@/api/product';
import { productDisplayLabel } from '@/utils/display';
import { useRemoteOptions } from '@/composables/useRemoteOptions';

const props = defineProps({
  modelValue: { type: String, default: '' },
  scope: { type: String, default: 'all' },
  customerGroup: { type: String, default: '' },
  status: { type: String, default: '' },
  placeholder: { type: String, default: '输入产品名称搜索' },
});
defineEmits(['update:modelValue']);
const normalize = (p) => ({ value: p.productCode, label: productDisplayLabel(p), raw: p });
const remote = useRemoteOptions(
  (params) => pageProducts({ ...params, productName: params.keyword, keyword: undefined, scope: props.scope, customerGroup: props.customerGroup || undefined, status: props.status || undefined }),
  { normalize },
);
const { items, loading, finished, error, search, loadMore, add } = remote;

async function ensureSelected(code) {
  if (!code || items.value.some((item) => item.value === code)) return;
  try {
    const res = await getProduct(code);
    if (res?.data) add(res.data);
  } catch { /* 请求层已统一提示 */ }
}
function onVisibleChange(visible) {
  if (visible && !items.value.length) search('');
}
watch(() => props.modelValue, ensureSelected, { immediate: true });
</script>

<style scoped>
.remote-more { min-height: 36px; display: flex; align-items: center; justify-content: center; color: var(--loan-primary); cursor: pointer; font-size: 13px; }
.remote-more.is-error { color: var(--loan-danger); }
</style>
