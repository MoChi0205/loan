<template>
  <div class="app-pagination">
    <el-pagination
      :current-page="page"
      :page-size="size"
      :total="total"
      :page-sizes="pageSizes"
      :layout="layout"
      :background="background"
      @update:current-page="(v) => $emit('update:page', v)"
      @current-change="emitChange"
      @size-change="onSizeChange"
    />
  </div>
</template>

<script setup>
/**
 * 列表页分页（公共组件）。
 *
 * <p>统一分页配置（size 选项、layout、背景色），与 useTable 配合使用：
 *   <AppPagination v-model:page="query.page" v-model:size="query.size" :total="total" @change="load" />
 */
defineProps({
  page: { type: Number, required: true },
  size: { type: Number, required: true },
  total: { type: Number, default: 0 },
  pageSizes: { type: Array, default: () => [10, 20, 50] },
  layout: { type: String, default: 'total, sizes, prev, pager, next, jumper' },
  background: { type: Boolean, default: true },
});

const emit = defineEmits(['update:page', 'update:size', 'change']);

function emitChange() {
  emit('change');
}

/** 每页条数变化：同步 size 并重置回第 1 页（避免第 N 页切 size 后请求空列表） */
function onSizeChange(v) {
  emit('update:size', v);
  emit('update:page', 1);
  emit('change');
}
</script>

<style scoped>
.app-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
