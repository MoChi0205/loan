<template>
  <div class="screening-page">
    <div class="loan-page-header">
      <div>
        <h2 class="loan-page-title">初筛执行</h2>
        <p class="loan-page-subtitle">选客户 + 经营事实 → 规则引擎匹配 → 生成初筛报告（档位 + 数量）</p>
      </div>
    </div>

    <div class="screening-layout">
      <!-- 左：表单区 -->
      <div class="loan-card form-panel">
        <el-form label-width="100px" label-position="right">
          <!-- 客户选择：独占整行 -->
          <el-form-item label="客户" required>
            <el-select
              v-model="form.clientCode"
              filterable
              remote
              :remote-method="searchClients"
              :loading="clientLoading"
              placeholder="搜索客户（姓名 / 手机号 / 企业名）"
              style="width: 100%"
            >
              <el-option v-for="c in clientOptions" :key="c.clientCode" :label="clientLabel(c)" :value="c.clientCode" />
            </el-select>
          </el-form-item>
          <el-form-item label="客群">
            <span v-if="selectedClient" class="loan-tag" :class="selectedClient.customerGroup === 'ENTERPRISE' ? 'loan-tag-info' : 'loan-tag-warning'">
              {{ selectedClient.customerGroup === 'ENTERPRISE' ? '企业' : '个人' }}
            </span>
            <span v-else class="muted">选择客户后自动识别</span>
          </el-form-item>

          <el-divider content-position="left">经营事实（{{ selectedClient?.customerGroup === 'PERSONAL' ? '个人：年龄 / 收入 / 信用分 / 房产' : '企业：纳税 / 开票 / 成立年限 / 行业' }}）</el-divider>

          <!-- 企业字段：两列网格 -->
          <template v-if="!selectedClient || selectedClient.customerGroup === 'ENTERPRISE'">
            <div class="fact-grid">
              <el-form-item label="年纳税额(元)">
                <el-input-number v-model="form.facts.annualTaxAmount" :min="0" :precision="2" :controls="false" placeholder="如 500000" style="width: 100%" />
              </el-form-item>
              <el-form-item label="年开票额(元)">
                <el-input-number v-model="form.facts.annualInvoiceAmount" :min="0" :precision="2" :controls="false" placeholder="如 3000000" style="width: 100%" />
              </el-form-item>
              <el-form-item label="成立年限(年)">
                <el-input-number v-model="form.facts.foundYears" :min="0" :max="100" :controls="false" placeholder="如 3" style="width: 100%" />
              </el-form-item>
              <el-form-item label="行业">
                <el-select v-model="form.facts.industry" clearable placeholder="可选" style="width: 100%">
                  <el-option v-for="s in industries" :key="s" :label="s" :value="s" />
                </el-select>
              </el-form-item>
            </div>
          </template>

          <!-- 个人字段：两列网格 -->
          <template v-else>
            <div class="fact-grid">
              <el-form-item label="年龄">
                <el-input-number v-model="form.facts.age" :min="18" :max="70" :controls="false" placeholder="如 30" style="width: 100%" />
              </el-form-item>
              <el-form-item label="月收入(元)">
                <el-input-number v-model="form.facts.monthlyIncome" :min="0" :precision="2" :controls="false" placeholder="如 15000" style="width: 100%" />
              </el-form-item>
              <el-form-item label="信用分">
                <el-input-number v-model="form.facts.creditScore" :min="300" :max="950" :controls="false" placeholder="如 680" style="width: 100%" />
              </el-form-item>
              <el-form-item label="是否有房产">
                <el-select v-model="form.facts.hasHouse" clearable placeholder="可选" style="width: 100%">
                  <el-option label="是" value="1" />
                  <el-option label="否" value="0" />
                </el-select>
              </el-form-item>
              <el-form-item label="就业状态">
                <el-select v-model="form.facts.employmentStatus" clearable placeholder="可选" style="width: 100%">
                  <el-option label="在职" value="EMPLOYED" />
                  <el-option label="自雇" value="SELF_EMPLOYED" />
                  <el-option label="自由职业" value="FREELANCE" />
                  <el-option label="无业" value="UNEMPLOYED" />
                </el-select>
              </el-form-item>
              <el-form-item label="社保缴纳(月)">
                <el-input-number v-model="form.facts.socialSecurityMonths" :min="0" :controls="false" placeholder="如 36" style="width: 100%" />
              </el-form-item>
            </div>
          </template>

          <el-form-item label-width="0" class="btn-row">
            <el-button type="primary" :loading="running" @click="onRun">
              <AppIcon name="screening" :size="14" />
              执行初筛
            </el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 右：结果区 -->
      <div class="result-panel">
        <div v-if="!result" class="loan-card result-placeholder">
          <AppIcon name="clock" :size="56" color="var(--loan-border, #e5e8f0)" />
          <p class="placeholder-text">选择客户并填写经营事实后<br/>点击「执行初筛」查看匹配结果</p>
        </div>

        <div v-else class="loan-card result-box">
          <div class="result-head">
            <span class="result-title">初筛完成</span>
            <span class="loan-tag" :class="gradeTag(result.grade)">{{ gradeText[result.grade] || result.grade }} 档</span>
          </div>

          <div class="result-grid">
            <div class="result-item">
              <div class="result-num mono">{{ result.bankCount }}</div>
              <div class="result-label">预计可进件银行</div>
            </div>
            <div class="result-item">
              <div class="result-num mono">{{ result.productCount }}</div>
              <div class="result-label">命中产品</div>
            </div>
            <div class="result-item result-item--pass">
              <div class="result-num mono">{{ result.passCount }}</div>
              <div class="result-label">可进件 PASS</div>
            </div>
            <div class="result-item result-item--cond">
              <div class="result-num mono">{{ result.conditionCount }}</div>
              <div class="result-label">需补料 CONDITION</div>
            </div>
            <div class="result-item result-item--reject">
              <div class="result-num mono">{{ result.rejectCount }}</div>
              <div class="result-label">暂不匹配 REJECT</div>
            </div>
          </div>

          <div class="result-foot">
            <span class="mono report-no">{{ result.reportNo }}</span>
            <el-button size="small" type="primary" plain @click="goReport">查看报告</el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 最近初筛记录（小程序提交 / Web 录入 统一可见，P0-4） -->
    <div class="loan-card records-card">
      <div class="panel-title">最近初筛记录</div>
      <AppSearchBar :loading="loadingR" @search="searchR" @reset="resetR">
        <el-select v-model="queryR.source" placeholder="来源" clearable style="width: 140px">
          <el-option label="小程序提交" value="MINI" />
          <el-option label="Web 录入" value="WEB" />
          <el-option label="邀请提交" value="INVITE" />
        </el-select>
        <el-input v-model="queryR.keyword" placeholder="报告编号 / 客户姓名 / 手机号 / 企业名" clearable style="width: 240px" @keyup.enter="searchR" />
      </AppSearchBar>

      <el-table :data="dataR" v-loading="loadingR" stripe row-key="reportNo">
        <template #empty>
          <AppEmpty title="暂无初筛记录" desc="执行初筛或小程序客户提交后展示于此" />
        </template>
        <el-table-column prop="reportNo" label="报告编号" width="140" show-overflow-tooltip />
        <el-table-column label="客户" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.clientName || row.enterpriseName || row.contactName || '—' }}</template>
        </el-table-column>
        <el-table-column label="来源" width="120">
          <template #default="{ row }">
            <span class="loan-tag" :class="screeningSourceTag(row.source)">{{ screeningSourceText(row.source) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="档位" width="80">
          <template #default="{ row }">
            <span class="loan-tag" :class="gradeTag(row.grade)">{{ gradeText[row.grade] || row.grade }}</span>
          </template>
        </el-table-column>
        <el-table-column label="可进件银行" width="100">
          <template #default="{ row }">{{ row.bankCount }}</template>
        </el-table-column>
        <el-table-column label="命中产品" width="90">
          <template #default="{ row }">{{ row.productCount }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="提交时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <AppTableActions :actions="[{ key: 'detail', label: '详情', onClick: () => goReportRecord(row) }]" />
          </template>
        </el-table-column>
      </el-table>

      <AppPagination v-model:page="queryR.page" v-model:size="queryR.size" :total="totalR" @change="loadR" />
    </div>
  </div>
</template>

<script setup>
defineOptions({ name: '_screening' });
import { ref, reactive, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import AppSearchBar from '@/components/AppSearchBar.vue';
import AppPagination from '@/components/AppPagination.vue';
import AppEmpty from '@/components/AppEmpty.vue';
import AppTableActions from '@/components/AppTableActions.vue';
import AppIcon from '@/components/AppIcon.vue';
import { useTable } from '@/composables/useTable';
import { formatDateTime } from '@/utils/format';
import { pageClientLite } from '@/api/order';
import { runScreening } from '@/api/dashboard';
import { pageScreenings, screeningDetail } from '@/api/report';
import { clientDisplayLabel } from '@/utils/display';

const route = useRoute();
const router = useRouter();
const clientLoading = ref(false);
const clientOptions = ref([]);
const selectedClient = ref(null);
const running = ref(false);
const result = ref(null);
const form = reactive({
  clientCode: '',
  facts: {
    // 企业
    annualTaxAmount: null,
    annualInvoiceAmount: null,
    foundYears: null,
    industry: '',
    // 个人
    age: null,
    monthlyIncome: null,
    creditScore: null,
    hasHouse: '',
    employmentStatus: '',
    socialSecurityMonths: null,
  },
});
const industries = ['制造业', '贸易', '批发零售', '建筑', '物流', '餐饮', '科技', '其他'];

const gradeText = { HIGH: '高', MIDDLE: '中', LOW: '低' };
const gradeTag = (g) => ({ HIGH: 'loan-tag-success', MIDDLE: 'loan-tag-warning', LOW: 'loan-tag-muted' }[g] || 'loan-tag-muted');

function clientLabel(c) {
  return clientDisplayLabel(c);
}

async function searchClients(keyword) {
  clientLoading.value = true;
  try {
    const res = await pageClientLite({ keyword: keyword || '', page: 1, size: 20 });
    clientOptions.value = res.data?.records || [];
    // 自动识别选中客户的客群
    if (form.clientCode) {
      selectedClient.value = clientOptions.value.find((c) => c.clientCode === form.clientCode) || null;
    }
  } catch (e) {
    clientOptions.value = [];
  } finally {
    clientLoading.value = false;
  }
}

async function onRun() {
  if (!form.clientCode) {
    ElMessage.warning('请先选择客户');
    return;
  }
  running.value = true;
  try {
    const res = await runScreening({ clientCode: form.clientCode, facts: form.facts });
    const detail = await screeningDetail(res.data);
    if (!detail.data) throw new Error('报告详情为空');
    result.value = detail.data;
    ElMessage.success('初筛完成，报告已生成');
  } catch (e) {
    /* 拦截器已提示 */ result.value = null;
  } finally {
    running.value = false;
  }
}

function goReport() {
  if (result.value?.reportNo) {
    router.push({ path: '/report/screening', query: { reportNo: result.value.reportNo } });
  } else {
    router.push('/report/screening');
  }
}

// ============================================================
// 最近初筛记录（P0-4：小程序提交 / Web 录入统一可见）
// ============================================================
const { loading: loadingR, data: dataR, total: totalR, query: queryR, load: loadR, onSearch: searchR, onReset: resetR } =
  useTable(pageScreenings, { source: '', keyword: '' });

const screeningSourceMap = { MINI: '小程序提交', WEB: 'Web 录入', INVITE: '邀请提交' };
function screeningSourceText(code) {
  return screeningSourceMap[code] || code || '-';
}
function screeningSourceTag(code) {
  const m = { MINI: 'loan-tag-info', WEB: 'loan-tag-muted', INVITE: 'loan-tag-primary' };
  return m[code] || 'loan-tag-muted';
}

function goReportRecord(row) {
  if (row.reportNo) {
    router.push({ path: '/report/screening', query: { reportNo: row.reportNo } });
  }
}

// ============================================================
// 路由预填：从客户档案「发起初筛」进入时自动选中客户
// ============================================================
async function preselectClient(code) {
  if (!code) return;
  try {
    const res = await pageClientLite({ keyword: code, page: 1, size: 20 });
    const list = res.data?.records || [];
    const c = list.find((x) => x.clientCode === code) || list[0];
    if (c) {
      form.clientCode = c.clientCode;
      selectedClient.value = c;
    } else {
      form.clientCode = code;
      selectedClient.value = null;
    }
  } catch (e) {
    // 拦截器已提示
  }
}

onMounted(() => {
  loadR();
  const code = route.query.clientCode || route.params.clientCode;
  if (code) preselectClient(code);
});
</script>

<style scoped>
.muted { color: var(--loan-text-secondary, #8a94a6); }

/* 左右布局 */
.screening-layout {
  display: flex;
  gap: 20px;
  align-items: flex-start;
}
.form-panel {
  flex: 0 0 520px;
}
.result-panel {
  flex: 1;
  min-width: 0;
}

/* 两列事实字段网格 */
.fact-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
}
.fact-grid .el-form-item {
  margin-bottom: 16px;
}

/* 按钮行 */
.btn-row {
  margin-top: 4px;
}

/* 结果占位 */
.result-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 320px;
  color: var(--loan-text-muted, #b0b8c6);
}
.placeholder-text {
  margin-top: 16px;
  font-size: 14px;
  line-height: 1.8;
  text-align: center;
  color: var(--loan-text-secondary, #8a94a6);
}

/* 结果卡片 */
.result-box {
  padding: 20px;
}
.result-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 18px;
}
.result-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--loan-text, #1c2433);
}
.result-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 10px;
}
.result-item {
  text-align: center;
  padding: 14px 8px;
  background: var(--loan-surface, #f8fafc);
  border: 1px solid var(--loan-border, #e5e8f0);
  border-radius: 8px;
}
.result-num {
  font-size: 24px;
  font-weight: 700;
  color: var(--loan-text, #1c2433);
}
.result-label {
  font-size: 12px;
  color: var(--loan-text-secondary, #8a94a6);
  margin-top: 4px;
}
.result-item--pass .result-num { color: var(--loan-success, #2fbf71); }
.result-item--cond .result-num { color: var(--loan-warning, #f59e0b); }
.result-item--reject .result-num { color: var(--loan-danger, #e5484d); }

.result-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px dashed var(--loan-border, #e5e8f0);
  font-size: 12px;
  color: var(--loan-text-secondary, #8a94a6);
}
.report-no {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.result-foot > .el-button {
  flex-shrink: 0;
}

/* 最近初筛记录区 */
.records-card {
  margin-top: 16px;
}

/* 响应式：窄屏时上下堆叠 */
@media (max-width: 960px) {
  .screening-layout {
    flex-direction: column;
  }
  .form-panel {
    flex: none;
    width: 100%;
  }
  .result-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}
@media (max-width: 640px) {
  .fact-grid {
    grid-template-columns: 1fr;
  }
  .result-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
