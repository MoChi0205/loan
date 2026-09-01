<template>
  <div class="wizard-page">
    <div class="loan-page-header">
      <div>
        <h2 class="loan-page-title">系统配置</h2>
        <p class="loan-page-subtitle">首次上线按步骤完成基础配置，全部就绪即可对外运营</p>
      </div>
      <el-button :loading="loading" @click="load">
        <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" style="margin-right: 4px; vertical-align: -2px"><path d="M21 12a9 9 0 11-2.6-6.4"/><path d="M21 3v6h-6"/></svg>
        刷新
      </el-button>
    </div>

    <div class="loan-card">
      <div class="progress-head">
        <div class="progress-label">
          完成度
          <span class="progress-num mono">{{ doneCount }}/{{ steps.length }}</span>
        </div>
        <el-progress :percentage="progressPct" :stroke-width="10" :show-text="false" :color="'var(--loan-primary)'" style="flex: 1" />
        <span class="progress-tip">{{ progressPct === 100 ? '🎉 配置已完成，可以开始运营' : '完成以下步骤后即可对外服务' }}</span>
      </div>

      <div class="step-list">
        <div v-for="(s, i) in steps" :key="s.key" class="step-row" :class="{ 'step-done': s.done, 'step-active': s.required && !s.done }">
          <div class="step-index mono" :class="{ 'step-index-done': s.done }">
            <svg v-if="s.done" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2.4"><path d="M5 13l4 4L19 7"/></svg>
            <template v-else>{{ i + 1 }}</template>
          </div>
          <div class="step-body">
            <div class="step-title">
              {{ s.title }}
              <span v-if="s.required" class="loan-tag loan-tag-danger">必配</span>
              <span v-else class="loan-tag loan-tag-muted">可选</span>
            </div>
            <div class="step-desc">{{ s.desc }}</div>
          </div>
          <div class="step-right">
            <span class="step-count mono">{{ s.countLabel }}</span>
            <el-button size="small" :type="s.done ? 'default' : 'primary'" plain @click="go(s.path)">
              {{ s.done ? '查看' : '去配置' }}
            </el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
defineOptions({ name: '_config_wizard' });
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { configStatus } from '@/api/dashboard';

const router = useRouter();
const loading = ref(false);
const status = ref({});

const steps = computed(() => {
  const c = status.value || {};
  const mk = (key, title, desc, path, required, countLabel, done) => ({ key, title, desc, path, required, countLabel, done });
  return [
    mk('dept', '组织架构', '配置部门树与员工账号（老板/主管/顾问）', '/org', true, `${c.departmentCount ?? 0} 部门 / ${c.staffCount ?? 0} 员工`, (c.departmentCount ?? 0) > 0 && (c.staffCount ?? 0) > 0),
    mk('channel', '合作渠道', '接入合作银行渠道', '/product', false, `${c.channelCount ?? 0} 家银行`, (c.channelCount ?? 0) > 0),
    mk('rule', '规则集', '配置准入规则与分类', '/rule', true, `${c.ruleCount ?? 0} 条规则`, (c.ruleCount ?? 0) > 0),
    mk('product', '产品库', '录入产品并审核入全量库', '/product', true, `${c.productCount ?? 0} 个产品`, (c.productCount ?? 0) > 0),
    mk('reward', '奖励规则', '配置推荐奖励比例（冻结快照）', '/reward', false, `${c.rewardRuleCount ?? 0} 条规则`, (c.rewardRuleCount ?? 0) > 0),
    mk('sms', '短信模板', '配置三类短信模板与签名', '/sms', false, `${c.smsTemplateCount ?? 0} 个模板`, (c.smsTemplateCount ?? 0) > 0),
    mk('template', '报告模板', '配置报告档位映射与文案', '/report-template', false, `${c.reportTemplateCount ?? 0} 个模板`, (c.reportTemplateCount ?? 0) > 0),
  ];
});

const doneCount = computed(() => steps.value.filter((s) => s.done).length);
const progressPct = computed(() => Math.round((doneCount.value / steps.value.length) * 100));

function go(path) {
  router.push(path);
}

async function load() {
  loading.value = true;
  try {
    const res = await configStatus();
    status.value = res.data || {};
  } catch (e) { /* 拦截器已提示 */ } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.progress-head {
  display: flex;
  align-items: center;
  gap: 16px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--loan-border, #e5e8f0);
  margin-bottom: 8px;
}
.progress-label {
  font-size: 14px;
  font-weight: 600;
  white-space: nowrap;
  color: var(--loan-text, #1c2433);
}
.progress-num {
  color: var(--loan-primary, #4f7cff);
  margin-left: 6px;
}
.progress-tip {
  font-size: 12px;
  color: var(--loan-text-secondary, #8a94a6);
  white-space: nowrap;
}
.step-list {
  display: flex;
  flex-direction: column;
}
.step-row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 8px;
  border-bottom: 1px solid var(--loan-border, #f0f2f7);
}
.step-row:last-child {
  border-bottom: none;
}
.step-index {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
  background: var(--loan-surface, #f5f7fa);
  color: var(--loan-text-secondary, #8a94a6);
  flex-shrink: 0;
}
.step-index-done {
  background: var(--loan-success, #2fbf71);
  color: #fff;
}
.step-body {
  flex: 1;
  min-width: 0;
}
.step-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--loan-text, #1c2433);
  display: flex;
  align-items: center;
  gap: 8px;
}
.step-desc {
  font-size: 12px;
  color: var(--loan-text-secondary, #8a94a6);
  margin-top: 2px;
}
.step-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.step-count {
  font-size: 12px;
  color: var(--loan-text-secondary, #8a94a6);
}
.mono {
  font-family: "SF Mono", Menlo, Consolas, monospace;
}
</style>
