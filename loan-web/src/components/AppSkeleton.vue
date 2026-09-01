<template>
  <div class="app-skeleton" :style="{ padding: padding }" role="status" aria-label="数据加载中">
    <!-- 头部:标题条 -->
    <div v-if="showHeader" class="sk-line sk-title" />
    <!-- 表格行骨架 -->
    <div v-for="r in rows" :key="r" class="sk-row">
      <div v-for="c in cols" :key="c" class="sk-line" :style="{ width: colWidth(c) }" />
    </div>
  </div>
</template>

<script setup>
/**
 * 表格骨架屏：列表初次加载时替代 v-loading 遮罩,减少"空白等待"焦虑感。
 *
 * <p>用法:
 *   <AppSkeleton v-if="loading && !data.length" :rows="6" :cols="8" />
 *   <el-table v-else ...>...</el-table>
 *
 * 或与 el-table 同时渲染(骨架在表格上方占位):
 *   <AppSkeleton v-show="loading" />
 *   <el-table v-show="!loading" ...>
 */
defineProps({
  /** 骨架行数(模拟数据行) */
  rows: { type: Number, default: 5 },
  /** 骨架列数(模拟表格列) */
  cols: { type: Number, default: 8 },
  /** 是否显示顶部标题条 */
  showHeader: { type: Boolean, default: true },
  /** 内边距 */
  padding: { type: String, default: '16px 24px' },
});

/** 列宽:首列窄(40%),中间列错落,末列宽(30%)模拟操作列 */
function colWidth(c) {
  const widths = ['34%', '26%', '30%', '22%', '28%', '20%', '24%', '18%', '26%'];
  const last = '22%';
  if (c >= 9) return last;
  return widths[c % widths.length];
}
</script>

<style scoped>
.app-skeleton {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 200px;
}
.sk-line {
  height: 12px;
  border-radius: 6px;
  background: linear-gradient(90deg, rgba(148,163,184,.14) 25%, rgba(148,163,184,.28) 50%, rgba(148,163,184,.14) 75%);
  background-size: 200% 100%;
  animation: sk-shimmer 1.4s ease-in-out infinite;
}
.sk-title {
  width: 36%;
  height: 16px;
  margin-bottom: 6px;
}
.sk-row {
  display: flex;
  gap: 16px;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid var(--loan-border, rgba(255,255,255,.06));
}
@keyframes sk-shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}
@media (prefers-reduced-motion: reduce) {
  .sk-line { animation: none; }
}
</style>
