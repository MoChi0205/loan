<template>
  <button
    class="app-btn"
    :class="[
      `btn-${resolvedType}`,
      `btn-${size}`,
      { 'is-block': block, 'is-loading': loading, 'is-disabled': disabled }
    ]"
    :loading="loading"
    :disabled="disabled || loading"
    :open-type="openType || undefined"
    hover-class="btn-hover"
    @click="onClick"
  >
    <slot>{{ text }}</slot>
  </button>
</template>

<script setup>
import { computed } from 'vue';

/**
 * 按钮组件（瑞幸风）：主按钮深海军蓝、次按钮描边、文字按钮。
 *
 * props:
 *   variant primary(主) / secondary(次) / ghost(透明描边) / text(文字) / gold(暖金)
 *   type    兼容旧用法别名（type 有值时优先于 variant）
 *   size    sm / md / lg
 *   block   是否通栏（默认 false，需要通栏的页面显式传入）
 *   loading 加载态（显示 loading 且禁用）
 *   disabled 禁用
 *
 * 用法：
 *   <AppButton variant="primary" size="lg" :loading="submitting" @click="onSubmit">提交</AppButton>
 *   <AppButton variant="ghost" @click="onReset">重新匹配</AppButton>
 */
const props = defineProps({
  type: { type: String, default: '' },
  variant: { type: String, default: 'primary' },
  size: { type: String, default: 'md' },
  block: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
  text: { type: String, default: '' },
  /** 微信开放能力（如 share），H5 会忽略该属性 */
  openType: { type: String, default: '' },
});

/** 解析最终按钮类型：type（旧用法）优先，其次 variant（新用法） */
const resolvedType = computed(() => props.type || props.variant);

const emit = defineEmits(['click']);

function onClick(e) {
  if (props.disabled || props.loading) return;
  emit('click', e);
}
</script>

<style scoped>
.app-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-btn, 20rpx);
  font-weight: 600;
  letter-spacing: 2rpx;
  transition: opacity 0.15s, transform 0.1s;
  box-sizing: border-box;
}

/* 尺寸 */
.btn-sm { height: 64rpx; font-size: 24rpx; padding: 0 24rpx; }
.btn-md { height: 88rpx; font-size: 28rpx; padding: 0 32rpx; }
.btn-lg { height: 100rpx; font-size: 32rpx; padding: 0 36rpx; }

/* 通栏 */
.is-block { width: 100%; }

/* 主按钮：深海军蓝 */
.btn-primary {
  background: var(--color-primary, #0b1d3a);
  color: var(--text-invert);
  box-shadow: 0 8rpx 24rpx rgba(11, 29, 58, 0.22);
}

/* 暖金按钮（强调/CTA） */
.btn-gold {
  background: var(--color-accent, #c8a96e);
  color: var(--text-invert);
  box-shadow: 0 8rpx 24rpx rgba(200, 169, 110, 0.28);
}

/* 次按钮：描边 */
.btn-secondary,
.btn-ghost {
  background: var(--bg-card, #fff);
  color: var(--color-primary, #0b1d3a);
  border: 2rpx solid var(--color-primary, #0b1d3a);
  box-shadow: none;
}
.btn-ghost { background: transparent; }

/* 文字按钮 */
.btn-text {
  background: transparent;
  color: var(--color-primary, #0b1d3a);
  box-shadow: none;
}

/* 状态 */
.is-loading,
.is-disabled {
  opacity: 0.55;
}
.btn-hover {
  opacity: 0.88;
  transform: scale(0.985);
}
</style>
