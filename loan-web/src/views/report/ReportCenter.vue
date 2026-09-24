<template>
  <div class="report-page">
    <div class="loan-page-header">
      <div>
        <h2 class="loan-page-title">{{ pageTitle }}</h2>
        <p class="loan-page-subtitle">{{ pageSubtitle }}</p>
      </div>
    </div>

    <!-- 总览卡片 -->
    <div class="stat-grid" v-loading="loadingOv">
      <div v-for="card in statCards" :key="card.label" class="stat-card">
        <div class="stat-card-icon" :class="card.tone"><AppIcon :name="card.icon" :size="20" /></div>
        <div><div class="stat-label">{{ card.label }}</div><div class="stat-value mono">{{ card.value }}</div></div>
      </div>
    </div>

    <!-- TSE 经验：资产、公海效率、分配回收与跟进 SLA -->
    <div class="operations-section" v-loading="loadingOperations">
      <div class="section-heading">
        <div>
          <div class="panel-title no-border">客户运营分析</div>
          <div class="section-hint">{{ operations.scopeLabel || '当前' }}范围 · 资产/SLA 看当前，流转/转化统计近 {{ operations.periodDays || operationQuery.days }} 天</div>
        </div>
        <div class="report-filters">
          <el-radio-group v-if="scopeOptions.length > 1" v-model="operationQuery.scope" size="small" @change="loadOperations">
            <el-radio-button v-for="item in scopeOptions" :key="item.value" :value="item.value">
              {{ item.label }}
            </el-radio-button>
          </el-radio-group>
          <el-select v-model="operationQuery.days" size="small" style="width: 112px" @change="loadOperations">
            <el-option label="近 30 天" :value="30" />
            <el-option label="近 90 天" :value="90" />
            <el-option label="近 180 天" :value="180" />
          </el-select>
        </div>
      </div>

      <div class="visual-grid">
        <div class="loan-card visual-card">
          <div class="visual-title"><span>客户资产结构</span><small>{{ operations.scopeLabel || '当前' }}范围</small></div>
          <AppEChart :option="assetChartOption" height="260px" />
        </div>
        <div class="loan-card visual-card">
          <div class="visual-title"><span>经营流转对比</span><small>近 {{ operations.periodDays || operationQuery.days }} 天</small></div>
          <AppEChart :option="flowChartOption" height="260px" />
        </div>
      </div>

      <div class="metric-panel-grid">
        <div class="loan-card metric-panel">
          <div class="metric-panel-title">客户资产</div>
          <div class="mini-stat-grid">
            <div v-for="item in assetMetrics" :key="item.label" class="mini-stat">
              <span>{{ item.label }}</span><strong class="mono">{{ item.value }}</strong>
            </div>
          </div>
        </div>
        <div class="loan-card metric-panel">
          <div class="metric-panel-title">公海效率</div>
          <div class="mini-stat-grid">
            <div v-for="item in seaMetrics" :key="item.label" class="mini-stat">
              <span>{{ item.label }}</span><strong class="mono">{{ item.value }}</strong>
            </div>
          </div>
        </div>
        <div class="loan-card metric-panel">
          <div class="metric-panel-title">分配 / 回收</div>
          <div class="mini-stat-grid">
            <div v-for="item in allocationMetrics" :key="item.label" class="mini-stat">
              <span>{{ item.label }}</span><strong class="mono">{{ item.value }}</strong>
            </div>
          </div>
        </div>
        <div class="loan-card metric-panel">
          <div class="metric-panel-title">跟进 SLA</div>
          <div class="mini-stat-grid">
            <div v-for="item in slaMetrics" :key="item.label" class="mini-stat" :class="item.level">
              <span>{{ item.label }}</span><strong class="mono">{{ item.value }}</strong>
            </div>
          </div>
        </div>
      </div>

      <div class="loan-card conversion-panel">
        <div class="conversion-head">
          <div>
            <div class="metric-panel-title">线索到成交漏斗</div>
            <div class="section-hint">各阶段独立按发生时间统计，用于观察经营流量，不代表同一批客户队列转化率</div>
          </div>
          <div class="deal-total">成交金额 <strong class="mono">¥{{ fmtAmount(operations.conversion?.dealAmount) }}</strong></div>
        </div>
        <div class="funnel-grid">
          <div v-for="(item, index) in conversionMetrics" :key="item.label" class="funnel-stage">
            <div class="funnel-index">{{ index + 1 }}</div>
            <div class="funnel-copy"><span>{{ item.label }}</span><strong class="mono">{{ item.value }}</strong></div>
            <div v-if="index < conversionMetrics.length - 1" class="funnel-arrow">→</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 初筛报告已独立到「智能匹配 / 诊断报告」，趋势已独立到「经营分析 / 趋势分析」。 -->
    <div v-if="false" class="loan-card" style="margin-top: 16px">
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
        <el-input v-model="queryS.keyword" placeholder="企业/客户姓名 / 日期 / 报告编号" style="width: 300px" clearable @keyup.enter="searchS" />
      </AppSearchBar>

      <el-table :data="dataS" v-loading="loadingS" stripe row-key="reportNo" @sort-change="handleSortChange" style="height: calc(100vh - 320px); min-height: 360px">
        <template #empty>
          <AppEmpty title="暂无初筛报告" desc="执行初筛并生成报告后，可在此查看报告详情" />
        </template>
        <el-table-column label="报告" min-width="210" show-overflow-tooltip><template #default="{ row }">{{ reportDisplayTitle(row) }}</template></el-table-column>
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
    <el-drawer v-model="detailVisible" title="员工内部经营咨询报告" size="min(960px, 92vw)">
      <StaffAggregatedReport v-if="detail" :detail="detail" />
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import AppSearchBar from '@/components/AppSearchBar.vue';
import AppPagination from '@/components/AppPagination.vue';
import AppEmpty from '@/components/AppEmpty.vue';
import AppTableActions from '@/components/AppTableActions.vue';
import AppEChart from '@/components/AppEChart.vue';
import AppIcon from '@/components/AppIcon.vue';
import StaffAggregatedReport from '@/components/report/StaffAggregatedReport.vue';
import { useTable } from '@/composables/useTable';
import { formatDateTime, desensitizePhone } from '@/utils/format';
import { reportDisplayTitle } from '@/utils/display';
import { reportOverview, reportOperations, pageScreenings, screeningAggregate } from '@/api/report';
import { useUserStore } from '@/store/user';

const userStore = useUserStore();
const executiveRoles = ['BOSS', 'SUPER_ADMIN', 'SUPER'];
const pageTitle = computed(() => executiveRoles.includes(userStore.roleCode) ? '经营概览' : '实时看板');
const roleScopeText = computed(() => ({
  BOSS: '全公司经营决策数据',
  SUPER_ADMIN: '全公司经营决策数据',
  SUPER: '全公司经营决策数据',
  OPERATOR: '全公司实时运营数据',
  DEPT_MANAGER: '本团队实时经营数据',
  ADVISER: '本人实时业务数据',
}[userStore.roleCode] || '当前角色可见数据'));
const pageSubtitle = computed(() => `${roleScopeText.value} · 客户资产 · 公海效率 · 跟进时效`);

const gradeText = { HIGH: '高', MIDDLE: '中', LOW: '低' };
const gradeTag = (g) => ({ HIGH: 'loan-tag-success', MIDDLE: 'loan-tag-warning', LOW: 'loan-tag-muted' }[g] || 'loan-tag-muted');
const gradeStar = (g) => ({ HIGH: 'A · 优质', MIDDLE: 'B · 良好', LOW: 'C · 一般', D: 'D · 暂不推荐' }[g] || (g ? `${g} · 未评级` : 'D · 暂不推荐'));
const starTag = (g) => ({ HIGH: 'loan-tag-success', MIDDLE: 'loan-tag-info', LOW: 'loan-tag-warning', D: 'loan-tag-muted' }[g] || 'loan-tag-muted');
/** 来源标签样式（后端 screeningPage 当前未返回 source，待对接时自动生效） */
const sourceText = (s) => ({ MINI: '小程序', CHANNEL: '渠道', WEB: 'Web 端', MINI_STAFF_CREATE: '员工建档' }[s] || (s ? s : '—'));
const sourceTag = (s) => ({ MINI: 'loan-tag-info', CHANNEL: 'loan-tag-success', WEB: 'loan-tag-muted', MINI_STAFF_CREATE: 'loan-tag-warning' }[s] || 'loan-tag-muted');
function fmtAmount(v) {
  return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}
/** 元转万元：纯字符串移位，保留全部有效小数，不做四舍五入。 */
function fmtWan(v) {
  const raw = String(v ?? 0).trim();
  if (!/^-?\d+(\.\d+)?$/.test(raw)) return '0';
  const negative = raw.startsWith('-');
  const [integer, decimal = ''] = raw.replace('-', '').split('.');
  const padded = integer.padStart(5, '0');
  const head = padded.slice(0, -4).replace(/^0+(?=\d)/, '') || '0';
  const tail = (padded.slice(-4) + decimal).replace(/0+$/, '');
  return `${negative ? '-' : ''}${Number(head).toLocaleString('zh-CN')}${tail ? `.${tail}` : ''}`;
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
  { label: '客户数', value: overview.value.clientCount ?? '-', icon: 'client', tone: 'blue' },
  { label: '线索数', value: overview.value.leadCount ?? '-', icon: 'lead', tone: 'cyan' },
  { label: '工单数', value: overview.value.orderCount ?? '-', icon: 'order', tone: 'violet' },
  { label: '成交单数', value: overview.value.dealOrderCount ?? '-', icon: 'success', tone: 'green' },
  { label: '成交金额', value: fmtWan(overview.value.dealAmountSum) + '万', icon: 'trend', tone: 'orange' },
  { label: '奖励单数', value: overview.value.rewardCount ?? '-', icon: 'reward', tone: 'red' },
  { label: '奖励金额', value: fmtWan(overview.value.rewardAmountSum) + '万', icon: 'reward', tone: 'orange' },
  { label: '初筛报告', value: overview.value.screeningCount ?? '-', icon: 'reportDoc', tone: 'blue' },
]);

const loadingOperations = ref(false);
const operations = ref({});
const operationQuery = reactive({ scope: '', days: 30 });
const scopeText = { MY: '我的', TEAM: '本团队', ALL: '全司' };
const scopeOptions = computed(() => (operations.value.availableScopes || [])
  .map((value) => ({ value, label: scopeText[value] || value })));
const showTeamSea = computed(() => operations.value.clientAssets?.teamSeaVisible === true);
const assetMetrics = computed(() => {
  const data = operations.value.clientAssets || {};
  const rows = [
    { label: '已分配客户', value: data.assigned ?? '—' },
    { label: '公司公海', value: data.companySea ?? '—' },
  ];
  if (showTeamSea.value) rows.push({ label: '团队公海', value: data.teamSea ?? '—' });
  rows.push({ label: '冷却中', value: data.cooldown ?? '—' });
  return rows;
});
const percent = (v) => v === null || v === undefined ? '待积累' : `${Number(v).toFixed(1)}%`;
const seaMetrics = computed(() => {
  const data = operations.value.seaEfficiency || {};
  return [
    { label: '进入公海', value: data.entered ?? '—' },
    { label: '公海出池', value: data.claimed ?? '—' },
    { label: '出入池比', value: percent(data.claimRate) },
    { label: '平均停留', value: data.avgStayMetricAvailable ? `${data.avgStayHours} 小时` : '待补采集' },
    { label: '当前平均池龄', value: data.currentPoolAgeHours == null ? '待补采集' : `${Number(data.currentPoolAgeHours).toFixed(1)} 小时` },
  ];
});
const allocationMetrics = computed(() => {
  const data = operations.value.allocationFlow || {};
  return [
    { label: '完成分配', value: data.assigned ?? '—' },
    { label: '公海认领', value: data.claimed ?? '—' },
    { label: '回收公海', value: data.recycled ?? '—' },
    { label: '主动释放', value: data.selfReleased ?? '—' },
  ];
});
const slaMetrics = computed(() => {
  const data = operations.value.followSla || {};
  return [
    { label: '从未跟进', value: data.neverFollowed ?? '—', level: 'is-warning' },
    { label: `超 ${data.recycleDays || 30} 天未跟进`, value: data.overdue ?? '—', level: 'is-danger' },
    { label: `${data.warnDays || 3} 天内将到期`, value: data.dueSoon ?? '—', level: 'is-warning' },
    { label: '首次跟进均时', value: data.firstFollowMetricAvailable ? `${data.avgFirstFollowHours} 小时` : '待补采集' },
  ];
});
const conversionMetrics = computed(() => {
  const data = operations.value.conversion || {};
  return [
    { label: '新增线索', value: data.leads ?? '—' },
    { label: '新增客户', value: data.clients ?? '—' },
    { label: '初筛报告', value: data.screenings ?? '—' },
    { label: '新建工单', value: data.orders ?? '—' },
    { label: '成交', value: data.deals ?? '—' },
  ];
});

const assetChartOption = computed(() => {
  const d = operations.value.clientAssets || {};
  const data = [
    { name: '已分配客户', value: Number(d.assigned || 0) },
    { name: '公司公海', value: Number(d.companySea || 0) },
  ];
  if (d.teamSeaVisible) data.push({ name: '团队公海', value: Number(d.teamSea || 0) });
  return {
    tooltip: { trigger: 'item', formatter: '{b}<br/><strong>{c}</strong> 位（{d}%）' },
    legend: { orient: 'vertical', right: 12, top: 'middle', itemWidth: 10, itemHeight: 10 },
    series: [{ type: 'pie', radius: ['52%', '76%'], center: ['35%', '52%'], avoidLabelOverlap: true,
      itemStyle: { borderRadius: 8, borderWidth: 3, borderColor: 'transparent' },
      label: { show: false }, data }],
  };
});

const flowChartOption = computed(() => {
  const d = operations.value.allocationFlow || {};
  return {
    grid: { left: 12, right: 22, top: 18, bottom: 8, containLabel: true },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    xAxis: { type: 'value', splitLine: { lineStyle: { type: 'dashed', opacity: 0.22 } } },
    yAxis: { type: 'category', data: ['完成分配', '公海认领', '回收公海', '主动释放'], axisTick: { show: false } },
    series: [{ type: 'bar', barWidth: 18, data: [d.assigned || 0, d.claimed || 0, d.recycled || 0, d.selfReleased || 0],
      itemStyle: { borderRadius: [0, 8, 8, 0], color: '#4f7cff' }, label: { show: true, position: 'right', fontWeight: 700 } }],
  };
});

async function loadOperations() {
  loadingOperations.value = true;
  try {
    const params = { days: operationQuery.days };
    if (operationQuery.scope) params.scope = operationQuery.scope;
    const res = await reportOperations(params);
    operations.value = res.data || {};
    operationQuery.scope = operations.value.scope || operationQuery.scope;
  } catch (e) { /* 拦截器已提示 */ } finally {
    loadingOperations.value = false;
  }
}

// ============================================================
// 初筛报告
// ============================================================
const { loading: loadingS, data: dataS, total: totalS, query: queryS, load: loadS, onSearch: searchS, onReset: resetS, handleSortChange } =
  useTable(pageScreenings, { grade: '', status: '', keyword: '' });

const detailVisible = ref(false);
const detail = ref(null);

async function openDetail(row) {
  try {
    const res = await screeningAggregate(row.reportNo);
    detail.value = res.data;
    detailVisible.value = true;
  } catch (e) { /* 拦截器已提示 */ }
}

onMounted(async () => {
  loadS();
  loadingOv.value = true;
  try {
    const [ov] = await Promise.all([reportOverview(), loadOperations()]);
    overview.value = ov.data || {};
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
  display: flex;
  align-items: center;
  gap: 12px;
  background: var(--loan-card-bg, var(--loan-paper));
  border: 1px solid var(--loan-border);
  border-radius: var(--loan-radius-md, 10px);
  padding: 16px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.04);
  transition: transform .2s ease, box-shadow .2s ease;
}
.stat-card:hover { transform: translateY(-2px); box-shadow: 0 12px 30px rgba(15, 23, 42, 0.08); }
.stat-card-icon { display:grid; place-items:center; flex:0 0 42px; height:42px; border-radius:12px; color:#fff; }
.stat-card-icon.blue { background:linear-gradient(135deg,#4f7cff,#6aa5ff); }
.stat-card-icon.cyan { background:linear-gradient(135deg,#06b6d4,#22d3ee); }
.stat-card-icon.violet { background:linear-gradient(135deg,#7c3aed,#a78bfa); }
.stat-card-icon.green { background:linear-gradient(135deg,#10b981,#34d399); }
.stat-card-icon.orange { background:linear-gradient(135deg,#f59e0b,#fb923c); }
.stat-card-icon.red { background:linear-gradient(135deg,#ef4444,#fb7185); }
.stat-label {
  font-size: 12px;
  color: var(--loan-text-secondary, var(--loan-text-muted));
  margin-bottom: 6px;
}
.stat-value {
  font-size: 20px;
  font-weight: 700;
  color: var(--loan-text);
}
.report-filters { display: flex; align-items: center; gap: 10px; }
.operations-section { margin-bottom: 16px; }
.visual-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:12px; margin:12px 0; }
.visual-card { padding:16px; overflow:hidden; }
.visual-title { display:flex; justify-content:space-between; align-items:center; font-weight:650; color:var(--loan-text); }
.visual-title small { font-size:12px; font-weight:400; color:var(--loan-text-secondary,var(--loan-text-muted)); }
.section-heading, .conversion-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}
.panel-title.no-border { border: 0; padding: 0; margin-bottom: 4px; }
.section-hint { color: var(--loan-text-secondary, var(--loan-text-muted)); font-size: 12px; line-height: 1.5; }
.metric-panel-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.metric-panel { padding: 16px; }
.metric-panel-title { font-size: 14px; font-weight: 600; color: var(--loan-text); margin-bottom: 12px; }
.mini-stat-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 8px; }
.mini-stat {
  min-width: 0;
  padding: 10px;
  background: var(--loan-surface, rgba(127, 127, 127, 0.06));
  border-radius: 8px;
}
.mini-stat span { display: block; min-height: 32px; color: var(--loan-text-secondary, var(--loan-text-muted)); font-size: 12px; line-height: 16px; }
.mini-stat strong { display: block; margin-top: 4px; color: var(--loan-text); font-size: 18px; overflow: hidden; text-overflow: ellipsis; }
.mini-stat.is-warning strong { color: var(--loan-warning); }
.mini-stat.is-danger strong { color: var(--loan-danger); }
.conversion-panel { margin-top: 12px; padding: 16px; }
.conversion-head { align-items: center; }
.deal-total { color: var(--loan-text-secondary, var(--loan-text-muted)); font-size: 12px; white-space: nowrap; }
.deal-total strong { color: var(--loan-text); font-size: 16px; margin-left: 8px; }
.funnel-grid { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); }
.funnel-stage { position: relative; display: flex; align-items: center; gap: 9px; min-width: 0; padding: 8px 24px 8px 8px; }
.funnel-index {
  display: grid; place-items: center; flex: 0 0 26px; height: 26px; border-radius: 50%;
  color: var(--loan-primary); background: color-mix(in srgb, var(--loan-primary) 12%, transparent); font-size: 12px; font-weight: 700;
}
.funnel-copy { min-width: 0; }
.funnel-copy span { display: block; color: var(--loan-text-secondary, var(--loan-text-muted)); font-size: 12px; }
.funnel-copy strong { display: block; margin-top: 3px; color: var(--loan-text); font-size: 18px; }
.funnel-arrow { position: absolute; right: 7px; color: var(--loan-text-secondary, var(--loan-text-muted)); }
@media (max-width: 900px) {
  .metric-panel-grid { grid-template-columns: 1fr; }
  .visual-grid { grid-template-columns:1fr; }
  .funnel-grid { grid-template-columns: 1fr; }
  .funnel-arrow { display: none; }
  .report-filters { width: 100%; flex-wrap: wrap; }
}
.panel-title {
  font-size: 14px;
  font-weight: 600;
  margin: 0 0 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--loan-border);
}
.mono { font-family: "SF Mono", Menlo, Consolas, monospace; }
.cell-main { font-weight: 500; }
.cell-sub { font-size: 12px; color: var(--loan-text-secondary, var(--loan-text-muted)); }

/* 通过/有条件/拒绝：彩色小字 */
.pass-cnt { color: var(--loan-success); margin-right: 8px; }
.cond-cnt { color: var(--loan-warning); margin-right: 8px; }
.rej-cnt  { color: var(--loan-danger); }
.advice { white-space: pre-wrap; font-size: 12px; margin: 0; color: var(--loan-text); }
</style>
