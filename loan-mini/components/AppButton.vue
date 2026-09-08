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
.btn-sm { height: 64rpx; font-size: 24rpx; padding: 0 24rpx; border-radius: var(--radius-full); }
.btn-md { height: 88rpx; font-size: var(--fs-em); padding: 0 32rpx; border-radius: var(--radius-full); }
.btn-lg { height: 100rpx; font-size: var(--fs-title); padding: 0 36rpx; border-radius: var(--radius-full); }

/* 通栏 */
.is-block { width: 100%; }

/* 主按钮：油画渐变（与登录页 hero 同源），白字 + 柔和投影 */
.btn-primary {
  background: var(--btn-primary-bg);
  color: var(--text-invert);
  box-shadow: var(--btn-primary-shadow);
  border: none;
}

/* 暖金按钮（强调/CTA）：暖金渐变 + 深棕字（保证 AA 对比度） */
.btn-gold {
  background: var(--btn-gold-bg);
  color: var(--gold-text);
  box-shadow: var(--btn-gold-shadow);
  border: none;
}

/* 危险按钮：红色渐变 */
.btn-danger {
  background: var(--btn-danger-bg);
  color: var(--text-invert);
  box-shadow: var(--btn-danger-shadow);
  border: none;
}

/* 次按钮：玻璃拟态（半透白底 + 品牌细边），配冷玻璃卡片 */
.btn-secondary {
  background: var(--btn-glass-bg);
  color: var(--brand-deep);
  border: 2rpx solid var(--btn-glass-border);
  box-shadow: var(--shadow-sm);
}

/* 幽灵按钮：透明 + 实心品牌边 */
.btn-ghost {
  background: var(--btn-ghost-bg);
  color: var(--brand-deep);
  border: 2rpx solid var(--btn-ghost-border);
  box-shadow: none;
}

/* 文字按钮 */
.btn-text {
  background: transparent;
  color: var(--brand-deep);
  box-shadow: none;
  border: none;
}

/* 状态：禁用/加载改用专用灰底，避免渐变叠 opacity 发灰发脏 */
.is-loading,
.is-disabled {
  background: var(--btn-disabled-bg) !important;
  color: var(--btn-disabled-text) !important;
  box-shadow: none !important;
  border: none !important;
}
.btn-hover {
  opacity: 0.92;
  transform: scale(0.985);
}
</style>
