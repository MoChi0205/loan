<template>
  <view class="load-more" :class="{ actionable: state === 'more' || state === 'error' }" role="button" :aria-label="label" @click="onClick">
    <text class="load-text">{{ label }}</text>
  </view>
</template>

<script setup>
import { computed } from 'vue';

/** 列表分页尾部统一状态：加载、到底、失败重试、继续加载。 */
const props = defineProps({
  loading: { type: Boolean, default: false },
  finished: { type: Boolean, default: false },
  error: { type: Boolean, default: false },
});
const emit = defineEmits(['load']);
const state = computed(() => {
  if (props.loading) return 'loading';
  if (props.error) return 'error';
  if (props.finished) return 'finished';
  return 'more';
});
const label = computed(() => ({
  loading: '加载中…', error: '加载失败，点击重试', finished: '已加载全部', more: '点击加载更多',
}[state.value]));
function onClick() {
  if (state.value === 'more' || state.value === 'error') emit('load');
}
</script>

<style scoped>
.load-more { min-height: 88rpx; padding: var(--space-3) 0; display: flex; align-items: center; justify-content: center; box-sizing: border-box; }
.load-text { font-size: var(--fs-sm); color: var(--text-secondary); }
.actionable .load-text { color: var(--brand-deep); font-weight: 600; }
.actionable:active { opacity: .7; }
</style>
