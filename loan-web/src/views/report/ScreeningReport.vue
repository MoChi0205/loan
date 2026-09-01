<template>
  <div class="report-page">
    <div class="loan-page-header">
      <div>
        <h2 class="loan-page-title">初筛报告</h2>
        <p class="loan-page-subtitle">初筛引擎生成的报告记录，可按档位/状态/编号检索</p>
      </div>
    </div>

    <div class="loan-card">
      <div class="panel-title">初筛报告列表</div>
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

      <el-table :data="dataS" v-loading="loadingS" stripe row-key="reportNo">
        <template #empty>
          <AppEmpty title="暂无报告" desc="在「初筛执行」中为客户生成第一份匹配报告" />
        </template>
        <el-table-column prop="reportNo" label="报告编号" width="120" show-overflow-tooltip />
        <el-table-column prop="clientName" label="客户" min-width="150" show-overflow-tooltip />
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
defineOptions({ name: '_report_screening' });
import { ref, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import AppSearchBar from '@/components/AppSearchBar.vue';
import AppPagination from '@/components/AppPagination.vue';
import AppTableActions from '@/components/AppTableActions.vue';
import { useTable } from '@/composables/useTable';
import { formatDateTime, desensitizePhone } from '@/utils/format';
import { pageScreenings, screeningDetail } from '@/api/report';

const route = useRoute();

const gradeText = { HIGH: '高', MIDDLE: '中', LOW: '低' };
const gradeTag = (g) => ({ HIGH: 'loan-tag-success', MIDDLE: 'loan-tag-warning', LOW: 'loan-tag-muted' }[g] || 'loan-tag-muted');
const gradeStar = (g) => ({ HIGH: 'A · 优质', MIDDLE: 'B · 良好', LOW: 'C · 一般', D: 'D · 暂不推荐' }[g] || (g ? `${g} · 未评级` : 'D · 暂不推荐'));
const starTag = (g) => ({ HIGH: 'loan-tag-success', MIDDLE: 'loan-tag-info', LOW: 'loan-tag-warning', D: 'loan-tag-muted' }[g] || 'loan-tag-muted');
/** 报告来源（P0-4：小程序提交标注） */
const sourceMap = { MINI: '小程序提交', WEB: 'Web 录入', INVITE: '邀请提交' };
function sourceText(code) {
  return sourceMap[code] || code || '-';
}
function sourceTag(code) {
  const m = { MINI: 'loan-tag-info', WEB: 'loan-tag-muted', INVITE: 'loan-tag-primary' };
  return m[code] || 'loan-tag-muted';
}
function prettyJson(s) {
  if (!s) return '—';
  try { return JSON.stringify(JSON.parse(s), null, 2); } catch { return s; }
}

const { loading: loadingS, data: dataS, total: totalS, query: queryS, load: loadS, onSearch: searchS, onReset: resetS } =
  useTable(pageScreenings, { grade: '', status: '', source: '', keyword: '' });

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
  // 从初筛执行页跳转时，URL 携带 reportNo → 自动定位并打开详情
  const targetNo = route.query.reportNo;
  if (targetNo) {
    queryS.keyword = String(targetNo);
    await searchS();
    // 等列表加载完后尝试打开详情
    setTimeout(async () => {
      const row = dataS.value.find((r) => r.reportNo === targetNo);
      if (row) {
        await openDetail(row);
      } else {
        // 列表未命中时直接用 reportNo 拉详情
        try {
          const res = await screeningDetail(String(targetNo));
          if (res.data) {
            detail.value = res.data;
            detailVisible.value = true;
          }
        } catch { /* 静默 */ }
      }
    }, 300);
  }
});
</script>

<style scoped>
.panel-title {
  font-size: 14px;
  font-weight: 600;
  margin: 0 0 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--loan-border, #e5e8f0);
}
.mono { font-family: "SF Mono", Menlo, Consolas, monospace; }
.cell-main { font-weight: 500; }
.cell-sub { font-size: 12px; color: var(--loan-text-secondary, #8a94a6); }
.pass-cnt { color: #34d399; margin-right: 8px; }
.cond-cnt { color: #fbbf24; margin-right: 8px; }
.rej-cnt  { color: #f87171; }
.advice { white-space: pre-wrap; font-size: 12px; margin: 0; color: var(--loan-text, #1c2433); }
</style>
