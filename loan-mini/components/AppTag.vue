<template>
  <text class="app-tag" :class="[`tag-${type}`, `tag-${size}`]">
    <slot>{{ text }}</slot>
  </text>
</template>

<script setup>
/**
 * 状态标签组件（设计系统 v1.0）：语义色 + 圆角胶囊。
 *
 * 统一替代原 .status-tag / .upload-status / .risk-tag 三套实现。
 * 文字一律使用 --*-text 无障碍色（对比度已验证 ≥4.5:1，WCAG AA），
 * 禁止直接用 --warning / --gold 原色作文字色。
 *
 * props:
 *   type  success(绿) / warning(橙) / danger(红) / info(青) / gold(暖金) / muted(灰)
 *   size  sm / md
 *
 * 用法：
 *   <AppTag type="success">已认证</AppTag>
 *   <AppTag type="warning">待审批</AppTag>
 *   <AppTag type="danger" size="sm">需补充</AppTag>
 */
defineProps({
  type: { type: String, default: 'info' },
  size: { type: String, default: 'md' },
  text: { type: String, default: '' },
});
</script>

<style scoped>
.app-tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-full);
  font-weight: 700;
  white-space: nowrap;
  line-height: 1.4;
}

/* 尺寸：最小触控目标 44px 由父级容器保证，标签为行内元素不强制 */
.tag-sm { font-size: var(--fs-xs); padding: 6rpx 20rpx; min-height: 36rpx; }
.tag-md { font-size: var(--fs-sm); padding: 8rpx 24rpx; min-height: 44rpx; }

/* 语义色：淡底 + 无障碍深字（对比度 ≥4.5:1） */
.tag-success {
  background: rgba(16, 185, 129, 0.14);
  color: var(--success-text, #047857);
}
.tag-warning {
  background: rgba(245, 158, 11, 0.16);
  color: var(--warning-text, #b45309);
}
.tag-danger {
  background: rgba(239, 68, 68, 0.14);
  color: var(--danger-text, #b91c1c);
}
.tag-info {
  background: rgba(6, 182, 212, 0.14);
  color: var(--info-text, #0e7490);
}
.tag-gold {
  background: rgba(200, 169, 110, 0.18);
  color: var(--gold-text, #3a2e12);
}
.tag-muted {
  background: var(--bg-input, #f1f5f9);
  color: var(--text-secondary, #64748b);
}
</style>
