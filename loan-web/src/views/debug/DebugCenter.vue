<template>
  <div class="debug-page">
    <div class="loan-page-header">
      <div>
        <h2 class="loan-page-title">调试中心</h2>
        <p class="loan-page-subtitle">调试执行：模拟客户空跑规则引擎，不落线上（dryRun）</p>
      </div>
    </div>

    <div class="debug-body">
      <!-- 左：模拟客户参数 -->
      <div class="loan-card fact-panel">
        <div class="fact-header">
          <h3 class="panel-title" style="margin-bottom:0;padding-bottom:0;border-bottom:none;">模拟客户参数</h3>
          <el-button type="primary" :loading="loading" @click="runMatch">
            <svg v-if="!loading" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" style="margin-right:4px;vertical-align:-2px"><path d="M5 3l14 9-14 9V3z"/></svg>
            调试执行
          </el-button>
        </div>

        <!-- 客户类型切换 -->
        <div class="fact-cg-bar">
          <el-segmented v-model="customerGroup" :options="customerGroupOptions" />
        </div>

        <!-- 表单区域：可滚动 -->
        <div class="fact-scroll">
          <el-form label-position="top" size="default">
            <el-form-item label="合作渠道" style="margin-bottom:12px">
              <el-select v-model="channelCode" placeholder="选择渠道（决定执行策略/计划）" style="width:100%">
                <option v-for="c in channels" :key="c.channelCode" :label="c.bankName" :value="c.channelCode" />
              </el-select>
            </el-form-item>
            <el-form-item label="申请城市" style="margin-bottom:12px">
              <el-input v-model="applyCity" placeholder="如 武汉市（市一级，按产品服务城市精确筛选，可留空）" clearable />
            </el-form-item>

            <!-- 企业客户字段 -->
            <template v-if="customerGroup === 'ENTERPRISE'">
              <div class="fact-grid">
                <el-form-item v-for="f in enterpriseFields" :key="f.code" :label="f.label">
                  <el-select v-if="f.options" v-model="f.value" style="width:100%">
                    <option v-for="o in f.options" :key="o" :label="o" :value="o" />
                  </el-select>
                  <el-input v-else v-model="f.value" />
                </el-form-item>
              </div>
              <div class="fact-tip">字段与规则目录「企业 16 条」对齐；黑名单/失信/欺诈置 1 可演示风控拒绝。</div>
            </template>

            <!-- 个人客户字段 -->
            <template v-else>
              <div class="fact-grid">
                <el-form-item v-for="f in personalFields" :key="f.code" :label="f.label">
                  <el-select v-if="f.options" v-model="f.value" style="width:100%">
                    <option v-for="o in f.options" :key="o" :label="o" :value="o" />
                  </el-select>
                  <el-input v-else v-model="f.value" />
                </el-form-item>
              </div>
              <div class="fact-tip">个人客户字段与规则目录「个人 N 条」对齐；黑名单/失信置 1 可演示风控拒绝。</div>
            </template>
          </el-form>
        </div>
      </div>

      <!-- 右：匹配结果 -->
      <div class="loan-card result-panel">
        <h3 class="panel-title">
          匹配结果
          <span v-if="result" class="panel-extra">
            trace：
            <code class="trace-code">{{ result.traceUuid }}</code>
            <el-button link type="primary" size="small" @click="onCopyTrace">复制</el-button>
          </span>
        </h3>

        <div v-if="!result" class="empty">
          <el-empty description="点击「调试执行」查看规则引擎步骤级结果" :image-size="90" />
        </div>
        <template v-else>
          <!-- 摘要：档位 + 统计 -->
          <div class="result-summary">
            <div class="grade-badge" :class="gradeClass">
              <span class="grade-label">匹配程度</span>
              <span class="grade-value">{{ dictLabel('grade', result.grade) }}</span>
            </div>
            <div class="stat">
              <div class="stat-num primary">{{ result.bankCount }}</div>
              <div class="stat-label">可进件银行</div>
            </div>
            <div class="stat">
              <div class="stat-num">{{ result.productCount }}</div>
              <div class="stat-label">命中产品</div>
            </div>
            <div class="stat">
              <div class="stat-num ok">{{ result.passCount }}</div>
              <div class="stat-label">可进件</div>
            </div>
            <div class="stat">
              <div class="stat-num warn">{{ result.conditionCount }}</div>
              <div class="stat-label">需补料</div>
            </div>
            <div class="stat">
              <div class="stat-num danger">{{ result.rejectCount }}</div>
              <div class="stat-label">暂不匹配</div>
            </div>
          </div>

          <!-- 产品结果树 -->
          <div class="result-tree">
            <div v-for="p in result.products" :key="p.productCode" class="product-node">
              <div class="product-head">
                <div class="product-info">
                  <span class="product-name">{{ p.productName }}</span>
                  <span class="product-code">{{ p.productCode }}</span>
                </div>
                <DictTag type="totalResult" :value="p.totalResult" />
              </div>
              <div v-for="m in p.modules" :key="m.moduleCode" class="module-node">
                <div class="module-head">
                  <span class="module-flag" :class="{ risk: m.globalPre }">
                    {{ m.globalPre ? '风控' : m.logicType }}
                  </span>
                  <span class="module-name">{{ m.moduleName }}</span>
                  <span class="module-state" :class="m.modulePassed ? 'ok' : 'no'">
                    {{ m.modulePassed ? '通过' : '未通过' }}
                  </span>
                </div>
                <div class="step-list">
                  <div v-for="s in m.steps" :key="s.ruleCode + s.expression" class="step-node">
                    <span class="step-dot" :class="stepDotClass(s.stepResult)"></span>
                    <div class="step-body">
                      <div class="step-line">
                        <span class="step-rule">{{ s.ruleName }}</span>
                        <span class="step-expr">{{ s.expression }}</span>
                        <span class="step-result">
                          <DictTag type="stepResult" :value="s.stepResult" />
                        </span>
                      </div>
                      <div v-if="s.detail" class="step-detail">{{ s.detail }}</div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
defineOptions({ name: '_debug' });
import { reactive, ref, computed, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import DictTag from '@/components/DictTag.vue';
import { dictLabel } from '@/utils/dict';
import { shadowMatch } from '@/api/debug';
import { listChannels } from '@/api/channel';
import { copyText } from '@/utils/clipboard';

const loading = ref(false);
const result = ref(null);

/** 客户类型：企业 / 个人 */
const customerGroup = ref('ENTERPRISE');
const customerGroupOptions = [
  { label: '企业客户', value: 'ENTERPRISE' },
  { label: '个人客户', value: 'PERSONAL' },
];

/** 合作渠道列表 + 当前选中渠道 */
const channels = ref([]);
const channelCode = ref('');
const applyCity = ref('');

/** 企业客户经营事实字段（与企业 16 条规则对齐） */
const enterpriseFields = reactive([
  { code: 'blacklist', label: '黑名单（0/1）', value: '0', options: ['0', '1'] },
  { code: 'dishonest', label: '失信（0/1）', value: '0', options: ['0', '1'] },
  { code: 'industry', label: '行业', value: '制造业' },
  { code: 'region', label: '区域', value: '武汉' },
  { code: 'establish_years', label: '成立年限', value: '5' },
  { code: 'annual_tax', label: '年纳税额（元）', value: '120000' },
  { code: 'annual_invoice', label: '年开票额（元）', value: '2000000' },
  { code: 'tax_grade', label: '纳税等级', value: 'A' },
  { code: 'debt_ratio', label: '负债率（%）', value: '45' },
  { code: 'biz_status', label: '经营状态', value: '存续' },
  { code: 'registered_capital', label: '注册资本（元）', value: '2000000' },
  { code: 'social_security_count', label: '社保人数', value: '20' },
]);

/** 个人客户事实字段（与个人规则对齐，后续可按实际规则扩展） */
const personalFields = reactive([
  { code: 'blacklist', label: '黑名单（0/1）', value: '0', options: ['0', '1'] },
  { code: 'dishonest', label: '失信（0/1）', value: '0', options: ['0', '1'] },
  { code: 'age', label: '年龄', value: '35' },
  { code: 'annual_income', label: '年收入（元）', value: '150000' },
  { code: 'credit_score', label: '信用分', value: '680' },
  { code: 'has_house', label: '有房（0/1）', value: '1', options: ['0', '1'] },
  { code: 'has_car', label: '有车（0/1）', value: '0', options: ['0', '1'] },
  { code: 'employment_type', label: '就业类型', value: '全职' },
  { code: 'work_years', label: '工作年限', value: '5' },
]);

/** 根据当前客群获取对应字段 */
const factFields = computed(() => customerGroup.value === 'ENTERPRISE' ? enterpriseFields : personalFields);

/** 执行调试匹配（按渠道加载策略/计划） */
async function runMatch() {
  if (!channelCode.value) {
    ElMessage.warning('请先选择合作渠道');
    return;
  }
  loading.value = true;
  try {
    const facts = {};
    factFields.forEach((f) => {
      facts[f.code] = f.value;
    });
    const res = await shadowMatch({ channelCode: channelCode.value, customerGroup: customerGroup.value, applyCity: applyCity.value || undefined, facts });
    result.value = {
      traceUuid: res.data?.traceUuid || `local-${Date.now().toString(36)}`,
      ...res.data,
    };
  } catch {
    result.value = null;
  } finally {
    loading.value = false;
  }
}

onMounted(async () => {
  try {
    const res = await listChannels();
    channels.value = res.data || [];
  } catch {
    channels.value = [];
  }
});

/** 复制 traceUuid */
async function onCopyTrace() {
  if (!result.value?.traceUuid) return;
  try {
    await copyText(result.value.traceUuid);
    ElMessage.success('traceUuid 已复制');
  } catch {
    ElMessage.warning('复制失败');
  }
}

/** 档位徽标样式 */
const gradeClass = computed(() => {
  if (!result.value) return '';
  const m = { HIGH: 'grade-high', MIDDLE: 'grade-mid', LOW: 'grade-low' };
  return m[result.value.grade] || 'grade-low';
});

/** 步骤圆点样式 */
function stepDotClass(r) {
  const m = {
    PASS: 'dot-pass',
    FAIL: 'dot-fail',
    SKIP: 'dot-skip',
    SKIP_SEGMENT_MISMATCH: 'dot-skip',
    ERROR: 'dot-fail',
  };
  return m[r] || 'dot-skip';
}
</script>

<style scoped>
.debug-body {
  display: grid;
  grid-template-columns: 360px 1fr;
  gap: 16px;
  align-items: start;
}

@media (max-width: 900px) {
  .debug-body {
    grid-template-columns: 1fr;
  }
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--loan-text);
  margin: 0 0 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--loan-border);
  flex-wrap: wrap;
}

.panel-extra {
  margin-left: auto;
  font-size: 12px;
  font-weight: 400;
  color: var(--loan-text-secondary);
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.trace-code {
  font-family: "SF Mono", Menlo, Consolas, monospace;
  font-size: 11px;
  color: var(--loan-text);
  background: var(--loan-surface);
  border: 1px solid var(--loan-border);
  border-radius: 4px;
  padding: 1px 6px;
}

/* ===== 左侧参数面板：标题+按钮同行，表单区域可滚动 ===== */
.fact-panel {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* 标题行：左侧标题 + 右侧执行按钮 */
.fact-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--loan-border);
  flex-shrink: 0;
}

/* 客群切换栏 */
.fact-cg-bar {
  margin-top: 14px;
  flex-shrink: 0;
}

/* 表单滚动区域：占据剩余空间，内容过多时可滚动 */
.fact-scroll {
  margin-top: 12px;
  overflow-y: auto;
  flex: 1;
  min-height: 0;
  padding-bottom: 8px;
}

.fact-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 12px;
}

@media (max-width: 560px) {
  .fact-grid {
    grid-template-columns: 1fr;
  }
}

.fact-tip {
  margin-top: 8px;
  font-size: 12px;
  color: var(--loan-text-muted);
  line-height: 1.6;
}

.result-panel {
  min-height: 480px;
}

.empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 320px;
}

.result-summary {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 16px;
  border-radius: var(--loan-radius);
  background: var(--loan-surface);
  border: 1px solid var(--loan-border);
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.grade-badge {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 110px;
  height: 72px;
  border-radius: var(--loan-radius);
  flex-shrink: 0;
}

.grade-high {
  background: color-mix(in srgb, var(--loan-success) 10%, transparent);
  border: 1px solid color-mix(in srgb, var(--loan-success) 30%, transparent);
}

.grade-mid {
  background: color-mix(in srgb, var(--loan-warning) 10%, transparent);
  border: 1px solid color-mix(in srgb, var(--loan-warning) 30%, transparent);
}

.grade-low {
  background: var(--loan-surface);
  border: 1px solid var(--loan-border);
}

.grade-label {
  font-size: 12px;
  color: var(--loan-text-secondary);
}

.grade-value {
  font-size: 22px;
  font-weight: 600;
  margin-top: 2px;
  color: var(--loan-text);
}

.grade-high .grade-value {
  color: var(--loan-success);
}
.grade-mid .grade-value {
  color: var(--loan-warning);
}

.stat {
  text-align: center;
  flex: 1;
  min-width: 56px;
}

.stat-num {
  font-size: 24px;
  font-weight: 600;
  color: var(--loan-text);
}

.stat-num.ok {
  color: var(--loan-success);
}

.stat-num.warn {
  color: var(--loan-warning);
}

.stat-num.danger {
  color: var(--loan-danger);
}

.stat-num.primary {
  color: var(--loan-primary);
}

.stat-label {
  font-size: 12px;
  color: var(--loan-text-secondary);
  margin-top: 2px;
}

.result-tree {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.product-node {
  border: 1px solid var(--loan-border);
  border-radius: var(--loan-radius);
  background: var(--loan-card-bg);
  overflow: hidden;
}

.product-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: var(--loan-surface);
  border-bottom: 1px solid var(--loan-border);
  gap: 12px;
}

.product-info {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.product-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--loan-text);
}

.product-code {
  font-family: "SF Mono", Menlo, Consolas, monospace;
  font-size: 11px;
  color: var(--loan-text-muted);
  background: var(--loan-card-bg);
  border: 1px solid var(--loan-border);
  border-radius: 4px;
  padding: 1px 6px;
}

.module-node {
  padding: 10px 16px;
  border-bottom: 1px solid var(--loan-border);
}

.module-node:last-child {
  border-bottom: none;
}

.module-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.module-flag {
  font-size: 11px;
  padding: 1px 8px;
  border-radius: 999px;
  color: var(--loan-primary);
  background: var(--loan-primary-soft);
  font-weight: 500;
}

.module-flag.risk {
  color: var(--loan-danger);
  background: color-mix(in srgb, var(--loan-danger) 10%, transparent);
}

.module-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--loan-text);
  flex: 1;
}

.module-state {
  font-size: 12px;
  font-weight: 500;
}

.module-state.ok {
  color: var(--loan-success);
}

.module-state.no {
  color: var(--loan-danger);
}

.step-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.step-node {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 6px 8px;
  border-radius: 4px;
  transition: background var(--loan-transition);
}

.step-node:hover {
  background: var(--loan-surface);
}

.step-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
  margin-top: 6px;
}

.dot-pass {
  background: var(--loan-success);
}

.dot-fail {
  background: var(--loan-danger);
}

.dot-skip {
  background: var(--loan-border-strong);
}

.step-body {
  flex: 1;
  min-width: 0;
}

.step-line {
  display: flex;
  align-items: center;
  gap: 10px;
}

.step-rule {
  font-size: 13px;
  color: var(--loan-text);
  width: 150px;
  flex-shrink: 0;
}

.step-expr {
  font-size: 12px;
  color: var(--loan-text-muted);
  font-family: "SF Mono", Menlo, Consolas, monospace;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.step-result {
  flex-shrink: 0;
}

.step-detail {
  margin-top: 3px;
  font-size: 12px;
  color: var(--loan-text-secondary);
  line-height: 1.5;
  word-break: break-all;
}

@media (max-width: 560px) {
  .step-rule {
    width: 100px;
  }
}
</style>
