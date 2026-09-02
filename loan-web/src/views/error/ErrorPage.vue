<template>
  <div class="error-page">
    <div class="error-card">
      <div class="error-code mono" :class="`tone-${tone}`">{{ code }}</div>
      <div class="error-title">{{ title }}</div>
      <div class="error-desc">{{ desc }}</div>
      <div class="error-actions">
        <el-button type="primary" @click="router.push('/workbench')">返回工作台</el-button>
        <el-button v-if="showBack" @click="router.back()">返回上一页</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router';

/**
 * 统一错误页（403 / 404 共用，路由 props 区分文案与色调）。
 */
defineProps({
  /** 状态码展示（403 / 404 / 500 ...） */
  code: { type: String, default: '404' },
  /** 主标题 */
  title: { type: String, default: '页面不存在' },
  /** 说明文案 */
  desc: { type: String, default: '您访问的页面可能已被移除、改名，或暂时不可用。' },
  /** 是否显示「返回上一页」 */
  showBack: { type: Boolean, default: false },
  /** 色调：warning（403 橙红）| info（404 蓝） */
  tone: { type: String, default: 'info' },
});

const router = useRouter();
</script>

<style scoped>
.error-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--loan-bg, #f5f7fa);
  padding: 24px;
}
.error-card {
  text-align: center;
  max-width: 420px;
  padding: 48px 40px;
  background: var(--loan-card-bg, #ffffff);
  border: 1px solid var(--loan-border, #e5e8f0);
  border-radius: 16px;
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.08);
}
.error-code {
  font-size: 64px;
  font-weight: 800;
  line-height: 1;
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}
.error-code.tone-warning {
  background: linear-gradient(135deg, var(--loan-warning, #fbbf24), var(--loan-danger, #f87171));
}
.error-code.tone-info {
  background: linear-gradient(135deg, var(--loan-primary, #3b82f6), var(--loan-accent, #38bdf8));
}
.error-title {
  font-size: 20px;
  font-weight: 700;
  margin: 16px 0 8px;
  color: var(--loan-text, #1c2433);
}
.error-desc {
  font-size: 13px;
  color: var(--loan-text-secondary, #8a94a6);
  line-height: 1.7;
}
.error-actions {
  margin-top: 24px;
  display: flex;
  gap: 10px;
  justify-content: center;
}
</style>
