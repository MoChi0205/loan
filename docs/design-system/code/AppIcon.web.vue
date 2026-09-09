<template>
  <!-- 墨金 Ink & Gold · Web 端统一图标组件 -->
  <svg
    class="app-icon"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    :stroke="color"
    :stroke-width="strokeWidth"
    stroke-linecap="round"
    stroke-linejoin="round"
    role="img"
    :aria-label="label || name"
    v-html="path"
  ></svg>
</template>

<script setup>
import { computed } from 'vue';
import { ICON_PATHS } from './icons.js';

const props = defineProps({
  name: { type: String, required: true },
  size: { type: [Number, String], default: 24 },
  /** 默认 currentColor，可传 var(--loan-primary) 等随主题联动 */
  color: { type: String, default: 'currentColor' },
  strokeWidth: { type: [Number, String], default: 1.75 },
  label: { type: String, default: '' }
});

const path = computed(() => ICON_PATHS[props.name] || '');
</script>

<style scoped>
.app-icon {
  display: inline-block;
  vertical-align: -0.18em;
  flex-shrink: 0;
}
</style>
