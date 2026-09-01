<template>
  <div class="app-empty" :style="{ minHeight }">
    <svg class="empty-art" viewBox="0 0 200 140" width="140" height="98" fill="none" aria-hidden="true">
      <!-- 背景圆 -->
      <circle cx="100" cy="72" r="58" fill="var(--empty-bg, rgba(59,130,246,.07))" />
      <!-- 文件/卡片 -->
      <rect x="46" y="42" width="108" height="72" rx="10" fill="var(--empty-card, rgba(59,130,246,.10))" stroke="var(--empty-line, rgba(59,130,246,.25))" stroke-width="1.5" />
      <!-- 折叠角 -->
      <path d="M108 42v18h18" fill="var(--empty-card, rgba(59,130,246,.10))" stroke="var(--empty-line, rgba(59,130,246,.25))" stroke-width="1.5" />
      <!-- 线条 -->
      <rect x="60" y="64" width="72" height="6" rx="3" fill="var(--empty-line, rgba(59,130,246,.22))" />
      <rect x="60" y="80" width="56" height="6" rx="3" fill="var(--empty-line, rgba(59,130,246,.16))" />
      <rect x="60" y="96" width="40" height="6" rx="3" fill="var(--empty-line, rgba(59,130,246,.12))" />
      <!-- 放大镜（搜索空状态语义） -->
      <circle cx="146" cy="110" r="14" stroke="var(--empty-line, rgba(59,130,246,.35))" stroke-width="3" />
      <line x1="156" y1="120" x2="168" y2="132" stroke="var(--empty-line, rgba(59,130,246,.35))" stroke-width="3" stroke-linecap="round" />
    </svg>
    <div class="empty-title">{{ title }}</div>
    <div v-if="desc" class="empty-desc">{{ desc }}</div>
    <div v-if="$slots.default" class="empty-action">
      <slot />
    </div>
  </div>
</template>

<script setup>
/**
 * 空状态插画组件：替代 el-table 默认"暂无数据"文字。
 *
 * <p>用法：
 *   <el-table :data="data">
 *     <template #empty>
 *       <AppEmpty title="暂无线索" desc="点击右上角「新增线索」录入第一条线索">
 *         <el-button type="primary" size="small" @click="openCreate">新增线索</el-button>
 *       </AppEmpty>
 *     </template>
 *   </el-table>
 */
defineProps({
  /** 空状态标题（必填） */
  title: { type: String, default: '暂无数据' },
  /** 空状态说明（可选） */
  desc: { type: String, default: '' },
  /** 最小高度（px 或 CSS 值），让空状态在表格区居中 */
  minHeight: { type: String, default: '220px' },
});
</script>

<style scoped>
.app-empty {
  /* 插画变量 → 主题主色派生（随品牌色/明暗切换），fallback 仅兜底 */
  --empty-bg: var(--loan-primary-soft, rgba(59, 130, 246, 0.08));
  --empty-card: var(--loan-primary-deep, rgba(59, 130, 246, 0.14));
  --empty-line: var(--loan-primary, rgba(59, 130, 246, 0.25));
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 24px 16px;
  color: var(--loan-text-secondary, #8a94a6);
  text-align: center;
}
.empty-art {
  margin-bottom: 4px;
  opacity: 0.9;
}
.empty-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--loan-text, #1c2433);
}
.empty-desc {
  font-size: 12px;
  color: var(--loan-text-muted, #8a94a6);
  max-width: 320px;
  line-height: 1.6;
}
.empty-action {
  margin-top: 4px;
}
</style>
