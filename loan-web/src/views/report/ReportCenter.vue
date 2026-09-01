<template>
  <div class="report-page">
    <div class="loan-page-header">
      <div>
        <h2 class="loan-page-title">报表中心</h2>
        <p class="loan-page-subtitle">经营总览 · 月度趋势 · 初筛报告查询</p>
      </div>
    </div>

    <!-- 总览卡片 -->
    <div class="stat-grid" v-loading="loadingOv">
      <div v-for="card in statCards" :key="card.label" class="stat-card">
        <div class="stat-label">{{ card.label }}</div>
        <div class="stat-value mono">{{ card.value }}</div>
      </div>
    </div>

    <!-- 趋势 -->
    <div class="trend-grid">
      <div class="loan-card">
        <div class="panel-title">成交趋势（近 12 个月）</div>
        <table class="trend-table">
          <thead><tr><th>月份</th><th>成交单数</th><th>成交金额（元）</th></tr></thead>
          <tbody>
            <tr v-for="r in orderTrendData" :key="r.month">
              <td>{{ r.month }}</td><td>{{ r.count }}</td>
              <td class="mono">¥{{ fmtAmount(r.amount) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="loan-card">
        <div class="panel-title">奖励趋势（近 12 个月）</div>
        <table class="trend-table">
          <thead><tr><th>月份</th><th>奖励单数</th><th>奖励金额（元）</th></tr></thead>
          <tbody>
            <tr v-for="r in rewardTrendData" :key="r.month">
              <td>{{ r.month }}</td><td>{{ r.count }}</td>
              <td class="mono">¥{{ fmtAmount(r.amount) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- 初筛报告 -->
    <div class="loan-card" style="margin-top: 16px">
      <div class="panel-title">初筛报告</div>
      <AppSearchBar :loading="loadingS" @search="searchS" @reset="resetS">
        <el-select v-model="queryS.grade" placeholder="档位" clearable style="width: 120px">
          <el-option label="高" value="HIGH" /><el-option label="中" value="MIDDLE" /><el-option label="低" value="LOW" />
        </el-select>
        <el-select v-model="queryS.status" placeholder="状态" clearable style="width: 130px">
          <el-option label="已生成" value="GENERATED" /><el-option label="已查看" value="VIEWED" />
        </el-select>
        <el-select v-model="queryS.source" placeholder="来源" clearable style="width: 130px">
          <el-option label="小程序提交" value="MINI" />
          <el-option label="Web 录入" value="WEB" />
        </el-select>
        <el-input v-model="queryS.keyword" placeholder="报告编号 / 客户姓名 / 手机号 / 企业名" style="width: 260px" clearable @keyup.enter="searchS" />
      </AppSearchBar>

      <el-table :data="dataS" v-loading="loadingS" stripe row-key="reportNo" @sort-change="handleSortChange" style="height: calc(100vh - 320px); min-height: 360px">
        <template #empty>
          <AppEmpty title="暂无初筛报告" desc="执行初筛并生成报告后，可在此查看报告详情" />
        </template>
        <el-table-column prop="reportNo" label="报告编号" width="120" show-overflow-tooltip />
        <el-table-column label="客户" min-width="180">
          <template #default="{ row }">
            <div class="cell-main">{{ row.clientName || row.enterpriseName || row.contactName || '—' }}</div>
            <div class="cell-sub" v-if="row.phone">{{ desensitizePhone(row.phone) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="110">
          <template #default="{ row }">
            <span class="loan-tag" :class="sourceTag(row.source)">{{ sourceText(row.source) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="档位" width="80">
          <template #default="{ row }">
            <span class="loan-tag" :class="gradeTag(row.grade)">{{ gradeText[row.grade] || row.grade }}</span>
          </template>
        </el-table-column>
        <el-table-column label="企业星级" width="100">
          <template #default="{ row }">
            <span class="loan-tag" :class="starTag(row.grade)">{{ gradeStar(row.grade) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="可进件银行" width="100">
          <template #default="{ row }">{{ row.bankCount }}</template>
        </el-table-column>
        <el-table-column label="命中产品" width="90">
          <template #default="{ row }">{{ row.productCount }}</template>
        </el-table-column>
        <el-table-column label="通过/有条件/拒绝" min-width="140">
          <template #default="{ row }">
            <span class="pass-cnt">{{ row.passCount || 0 }} 通过</span>
            <span class="cond-cnt">{{ row.conditionCount || 0 }} 有条件</span>
            <span class="rej-cnt">{{ row.rejectCount || 0 }} 拒绝</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <span class="loan-tag" :class="row.status === 'VIEWED' ? 'loan-tag-muted' : 'loan-tag-info'">
              {{ row.status === 'VIEWED' ? '已查看' : '已生成' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="160" sortable>
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <AppTableActions :actions="[{ key: 'detail', label: '详情', onClick: () => openDetail(row) }]" />
          </template>
        </el-table-column>
      </el-table>
      <AppPagination v-model:page="queryS.page" v-model:size="queryS.size" :total="totalS" @change="loadS" />
    </div>

    <!-- 报告详情抽屉 -->
    <el-drawer v-model="detailVisible" title="初筛报告详情" size="480px">
      <template v-if="detail">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="报告编号">{{ detail.reportNo }}</el-descriptions-item>
          <el-descriptions-item label="来源">
            <span class="loan-tag" :class="sourceTag(detail.source)">{{ sourceText(detail.source) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="客户">{{ detail.clientName || detail.enterpriseName || detail.contactName || '—' }}<template v-if="detail.phone"><br><span class="cell-sub">{{ desensitizePhone(detail.phone) }}</span></template></el-descriptions-item>
          <el-descriptions-item label="档位">{{ gradeText[detail.grade] || detail.grade }}</el-descriptions-item>
          <el-descriptions-item label="可进件银行">{{ detail.bankCount }}</el-descriptions-item>
          <el-descriptions-item label="命中产品">{{ detail.productCount }}</el-descriptions-item>
          <el-descriptions-item label="PASS / CONDITION / REJECT">{{ detail.passCount }} / {{ detail.conditionCount }} / {{ detail.rejectCount }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ detail.status === 'VIEWED' ? '已查看' : '已生成' }}</el-descriptions-item>
          <el-descriptions-item label="建议清单">
            <pre class="advice">{{ prettyJson(detail.adviceJson) }}</pre>
          </el-descriptions-item>
        </el-descriptions>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import AppSearchBar from '@/components/AppSearchBar.vue';
import AppPagination from '@/components/AppPagination.vue';
import AppEmpty from '@/components/AppEmpty.vue';
import AppTableActions from '@/components/AppTableActions.vue';
import { useTable } from '@/composables/useTable';
import { formatDateTime, desensitizePhone } from '@/utils/format';
import { reportOverview, orderTrend, rewardTrend, pageScreenings, screeningDetail } from '@/api/report';

const gradeText = { HIGH: '高', MIDDLE: '中', LOW: '低' };
const gradeTag = (g) => ({ HIGH: 'loan-tag-success', MIDDLE: 'loan-tag-warning', LOW: 'loan-tag-muted' }[g] || 'loan-tag-muted');
const gradeStar = (g) => ({ HIGH: 'A · 优质', MIDDLE: 'B · 良好', LOW: 'C · 一般', D: 'D · 暂不推荐' }[g] || (g ? `${g} · 未评级` : 'D · 暂不推荐'));
const starTag = (g) => ({ HIGH: 'loan-tag-success', MIDDLE: 'loan-tag-info', LOW: 'loan-tag-warning', D: 'loan-tag-muted' }[g] || 'loan-tag-muted');
function fmtAmount(v) {
  return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}
function prettyJson(s) {
  if (!s) return '—';
  try { return JSON.stringify(JSON.parse(s), null, 2); } catch { return s; }
}

// ============================================================
// 总览
// ============================================================
const loadingOv = ref(false);
const overview = ref({});
const statCards = computed(() => [
  { label: '客户数', value: overview.value.clientCount ?? '-' },
  { label: '线索数', value: overview.value.leadCount ?? '-' },
  { label: '工单数', value: overview.value.orderCount ?? '-' },
  { label: '成交单数', value: overview.value.dealOrderCount ?? '-' },
  { label: '成交金额', value: '¥' + fmtAmount(overview.value.dealAmountSum) },
  { label: '奖励单数', value: overview.value.rewardCount ?? '-' },
  { label: '奖励金额', value: '¥' + fmtAmount(overview.value.rewardAmountSum) },
  { label: '初筛报告', value: overview.value.screeningCount ?? '-' },
]);

// ============================================================
// 趋势
// ============================================================
const orderTrendData = ref([]);
const rewardTrendData = ref([]);

// ============================================================
// 初筛报告
// ============================================================
const { loading: loadingS, data: dataS, total: totalS, query: queryS, load: loadS, onSearch: searchS, onReset: resetS, handleSortChange } =
  useTable(pageScreenings, { grade: '', status: '', keyword: '' });

const detailVisible = ref(false);
const detail = ref(null);

async function openDetail(row) {
  try {
    const res = await screeningDetail(row.reportNo);
    detail.value = res.data;
    detailVisible.value = true;
  } catch (e) { /* 拦截器已提示 */ }
}

onMounted(async () => {
  loadS();
  loadingOv.value = true;
  try {
    const [ov, ot, rt] = await Promise.all([reportOverview(), orderTrend(12), rewardTrend(12)]);
    overview.value = ov.data || {};
    orderTrendData.value = ot.data || [];
    rewardTrendData.value = rt.data || [];
  } catch (e) { /* 拦截器已提示 */ } finally {
    loadingOv.value = false;
  }
});
</script>

<style scoped>
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}
.stat-card {
  background: var(--loan-card-bg, #fff);
  border: 1px solid var(--loan-border, #e5e8f0);
  border-radius: var(--loan-radius-md, 10px);
  padding: 14px 16px;
}
.stat-label {
  font-size: 12px;
  color: var(--loan-text-secondary, #8a94a6);
  margin-bottom: 6px;
}
.stat-value {
  font-size: 20px;
  font-weight: 700;
  color: var(--loan-text, #1c2433);
}
.trend-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
@media (max-width: 900px) {
  .trend-grid { grid-template-columns: 1fr; }
}
.panel-title {
  font-size: 14px;
  font-weight: 600;
  margin: 0 0 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--loan-border, #e5e8f0);
}
.trend-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.trend-table th, .trend-table td {
  padding: 6px 8px;
  text-align: right;
  border-bottom: 1px solid var(--loan-border, #f0f2f7);
}
.trend-table th:first-child, .trend-table td:first-child { text-align: left; color: var(--loan-text-secondary, #8a94a6); }
.mono { font-family: "SF Mono", Menlo, Consolas, monospace; }
.cell-main { font-weight: 500; }
.cell-sub { font-size: 12px; color: var(--loan-text-secondary, #8a94a6); }

/* 通过/有条件/拒绝：彩色小字 */
.pass-cnt { color: var(--loan-success, #34d399); margin-right: 8px; }
.cond-cnt { color: var(--loan-warning, #f59e0b); margin-right: 8px; }
.rej-cnt  { color: var(--loan-danger, #e5484d); }
.advice { white-space: pre-wrap; font-size: 12px; margin: 0; color: var(--loan-text, #1c2433); }
</style>
