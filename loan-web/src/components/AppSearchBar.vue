<template>
  <div
    class="app-search-bar"
    :class="{ 'is-compact': compact }"
    @keyup.enter="onSearchKeydown"
  >
    <div class="app-search-fields">
      <slot />
    </div>
    <div class="app-search-actions">
      <!-- 查询/重置始终保留；append 仅用于追加导出、批量等业务操作，避免插槽覆盖默认操作 -->
      <el-button v-if="showReset" :disabled="loading" @click="onResetClick">重置</el-button>
      <el-button v-if="showSearch" type="primary" :loading="loading" @click="onSearchClick">
        <svg v-if="!loading" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.8" style="margin-right: 4px; vertical-align: -2px"><circle cx="11" cy="11" r="7"/><path d="M21 21l-4.3-4.3"/></svg>
        查询
      </el-button>
      <slot name="append" />
    </div>
  </div>
</template>

<script setup>
/**
 * 列表页查询栏（公共组件）。
 *
 * <p>提供统一的筛选区布局 + 查询/重置按钮，支持回车提交（输入框内按回车触发 search）。
 * 默认 slot 放筛选字段，#append 插槽追加额外操作（导出/批量等），不会覆盖查询/重置。
 *
 * 用法：
 *   <AppSearchBar @search="onSearch" @reset="onReset" :loading="loading">
 *     <el-input v-model="query.name" placeholder="名称" />
 *     <el-select v-model="query.status" placeholder="状态">...</el-select>
 *     <template #append>
 *       <el-button>导出</el-button>
 *     </template>
 *   </AppSearchBar>
 */
const props = defineProps({
  loading: { type: Boolean, default: false },
  compact: { type: Boolean, default: false },
  showReset: { type: Boolean, default: true },
  showSearch: { type: Boolean, default: true },
});

const emit = defineEmits(['search', 'reset']);

function onSearchClick() {
  emit('search');
}
function onSearchKeydown() {
  if (props.loading) return; // 加载中忽略回车
  emit('search');
}
function onResetClick() {
  emit('reset');
}
</script>

<style scoped>
.app-search-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.app-search-fields {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  flex: 1;
  min-width: 0;
}

.app-search-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.app-search-bar.is-compact {
  margin-bottom: 0;
}
</style>
