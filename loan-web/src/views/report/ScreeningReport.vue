<template>
  <div class="report-page">
    <div class="loan-page-header">
      <div>
        <h2 class="loan-page-title">{{ isChannel ? '客户分析报告' : '初筛报告' }}</h2>
        <p class="loan-page-subtitle">{{ isChannel ? '仅展示本人录入客户的分析结果和归属顾问' : '初筛引擎生成的报告记录，可按档位/状态/编号检索' }}</p>
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
        <el-select v-if="!isChannel" v-model="queryS.source" placeholder="来源" clearable style="width: 130px">
          <el-option label="小程序提交" value="MINI" />
          <el-option label="Web 录入" value="WEB" />
        </el-select>
        <el-input v-model="queryS.keyword" placeholder="报告编号 / 客户姓名 / 手机号 / 企业名" style="width: 260px" clearable @keyup.enter="searchS" />
      </AppSearchBar>

      <el-table :data="dataS" v-loading="loadingS" stripe row-key="reportNo">
        <template #empty>
          <AppEmpty title="暂无报告" :desc="isChannel ? '本人录入的客户生成分析报告后会显示在这里' : '在「初筛执行」中为客户生成第一份匹配报告'" />
        </template>
        <el-table-column v-if="!isChannel" prop="reportNo" label="报告编号" width="120" show-overflow-tooltip />
        <el-table-column prop="clientName" label="客户" min-width="150" show-overflow-tooltip />
        <el-table-column v-if="isChannel" prop="ownerStaffName" label="归属顾问" width="130"><template #default="{ row }">{{ row.ownerStaffName || '待分配' }}</template></el-table-column>
        <el-table-column v-if="!isChannel" label="来源" width="110">
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

    <el-drawer v-model="detailVisible" title="合规分析报告详情" size="520px">
      <template v-if="detail">
        <!-- 结果横幅 -->
        <div class="report-banner" :class="`rb-${detailGradeClass}`">
          <span class="rb-label">{{ gradeText[detail.grade] || detail.grade }} · {{ detailStarLabel }}</span>
          <div class="rb-metrics">
            <div class="rb-metric">
              <span class="rb-num">{{ detail.productCount || 0 }}</span>
              <span class="rb-name">命中产品</span>
            </div>
            <div class="rb-divider" />
            <div class="rb-metric">
              <span class="rb-num">{{ detail.bankCount || 0 }}</span>
              <span class="rb-name">可进件银行</span>
            </div>
            <div class="rb-divider" />
            <div class="rb-metric">
              <span class="rb-num">{{ detail.passCount || 0 }}/{{ detail.conditionCount || 0 }}/{{ detail.rejectCount || 0 }}</span>
              <span class="rb-name">通过/有条件/拒绝</span>
            </div>
          </div>
        </div>

        <!-- 基础信息卡 -->
        <div class="report-section">
          <div class="report-section-title">基础信息</div>
          <el-descriptions :column="2" border>
            <el-descriptions-item v-if="!isChannel" label="报告编号">{{ detail.reportNo }}</el-descriptions-item>
            <el-descriptions-item label="来源">
              <span class="loan-tag" :class="sourceTag(detail.source)">{{ sourceText(detail.source) }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="客户">{{ detail.clientName || detail.enterpriseName || detail.contactName || '—' }}</el-descriptions-item>
            <el-descriptions-item v-if="detail.phone" label="手机号">{{ desensitizePhone(detail.phone) }}</el-descriptions-item>
            <el-descriptions-item v-if="isChannel" label="归属顾问">{{ detail.ownerStaffName || '待分配' }}</el-descriptions-item>
            <el-descriptions-item label="档位">{{ gradeText[detail.grade] || detail.grade }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ detail.status === 'VIEWED' ? '已查看' : '已生成' }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <!-- 建议清单 -->
        <div class="report-section" v-if="detail.adviceJson">
          <div class="report-section-title">匹配建议</div>
          <div class="advice-box">{{ prettyJson(detail.adviceJson) }}</div>
        </div>

        <!-- 合规声明 -->
        <div class="report-compliance">
          <AppIcon name="success" :size="16" />
          <span>本报告仅供融资参考，不构成任何银行通过承诺；具体产品额度与利率以顾问跟进为准。</span>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
defineOptions({ name: '_report_screening' });
import { ref, computed, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import AppSearchBar from '@/components/AppSearchBar.vue';
import AppPagination from '@/components/AppPagination.vue';
import AppTableActions from '@/components/AppTableActions.vue';
import { useTable } from '@/composables/useTable';
import { formatDateTime, desensitizePhone } from '@/utils/format';
import { pageScreenings, screeningDetail } from '@/api/report';
import { useUserStore } from '@/store/user';

const route = useRoute();
const userStore = useUserStore();
const isChannel = computed(() => userStore.roleCode === 'CHANNEL');

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

/** 详情档位样式类 */
const detailGradeClass = computed(() => {
  const g = detail.value && detail.value.grade;
  return { HIGH: 'high', MIDDLE: 'middle', LOW: 'low' }[g] || 'low';
});
/** 详情星级标签 */
const detailStarLabel = computed(() => {
  const g = detail.value && detail.value.grade;
  return gradeStar(g);
});

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
.pass-cnt { color: var(--loan-success, #10b981); margin-right: 8px; }
.cond-cnt { color: var(--loan-warning, #f59e0b); margin-right: 8px; }
.rej-cnt  { color: var(--loan-danger, #ef4444); }
.advice { white-space: pre-wrap; font-size: 12px; margin: 0; color: var(--loan-text, #1c2433); }

/* ===== 报告详情抽屉 ===== */
.report-banner {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 24px 20px;
  border-radius: 12px;
  color: #fff;
  margin-bottom: 16px;
}
.rb-high { background: var(--loan-primary, #3b82f6); }
.rb-middle { background: var(--loan-warning, #f59e0b); }
.rb-low { background: var(--loan-text-secondary, #94a3b8); }
.rb-label { font-size: 15px; font-weight: 600; margin-bottom: 12px; }
.rb-metrics { display: flex; align-items: center; width: 100%; }
.rb-metric { flex: 1; text-align: center; }
.rb-num { display: block; font-size: 20px; font-weight: 700; }
.rb-name { display: block; font-size: 11px; opacity: 0.85; margin-top: 4px; }
.rb-divider { width: 1px; height: 36px; background: rgba(255, 255, 255, 0.25); }

.report-section { margin-bottom: 16px; }
.report-section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--loan-text);
  margin-bottom: 8px;
  padding-left: 8px;
  border-left: 3px solid var(--loan-primary, #3b82f6);
}

.advice-box {
  background: var(--loan-surface, #f8fafc);
  border: 1px solid var(--loan-border, #e2e8f0);
  border-radius: 8px;
  padding: 12px 16px;
  font-size: 13px;
  line-height: 1.8;
  color: var(--loan-text);
  white-space: pre-wrap;
  word-break: break-all;
}

.report-compliance {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 12px 16px;
  background: var(--loan-warning-bg, #fffbeb);
  border: 1px solid var(--loan-warning-line, #fde68a);
  border-radius: 8px;
  font-size: 12px;
  color: var(--loan-warning-text, #b45309);
  line-height: 1.6;
  margin-top: 8px;
}
</style>
