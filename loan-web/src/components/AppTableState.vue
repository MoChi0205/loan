<template>
  <div v-if="error" class="table-state" role="alert">
    <AppEmpty title="加载失败" :desc="message" :min-height="minHeight">
      <el-button type="primary" plain @click="$emit('retry')">重新加载</el-button>
    </AppEmpty>
  </div>
  <slot v-else />
</template>

<script setup>
import { computed } from 'vue';
import AppEmpty from '@/components/AppEmpty.vue';

const props = defineProps({
  error: { type: [Error, Object, String], default: null },
  minHeight: { type: String, default: '180px' },
});
defineEmits(['retry']);
const message = computed(() => props.error?.message || '网络异常或服务暂不可用，请稍后重试');
</script>

<style scoped>
.table-state { width: 100%; }
</style>
