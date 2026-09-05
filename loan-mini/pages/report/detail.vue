<template>
  <view class="detail-page">
    <!-- 加载中：骨架屏替代纯文字（P1-5 Loading 态） -->
    <AppSkeleton v-if="loading" :rows="4" />

    <template v-else-if="report">
      <!-- 档位结果卡 -->
      <view class="result-card" :class="`rc-${resultClass}`">
        <text class="result-label">{{ resultLabel }}</text>
        <view class="result-metrics">
          <view class="metric">
            <text class="metric-num">{{ report.productCount || 0 }}</text>
            <text class="metric-name">可匹配产品数</text>
          </view>
          <view class="metric-divider" />
          <view class="metric">
            <text class="metric-num">{{ ratingLabel }}</text>
            <text class="metric-name">综合评级</text>
          </view>
        </view>
      </view>

      <!-- Tab：企业员工可见「命中产品」（C4）；客户对客脱敏，仅看报告信息与诊断 -->
      <view class="tab-seg">
        <view
          v-for="t in tabs" :key="t.key"
          class="tab-item" :class="{ active: activeTab === t.key }"
          :role="'tab'"
          :aria-selected="activeTab === t.key"
          @click="onTabChange(t.key)"
        >{{ t.label }}</view>
      </view>

      <!-- ===== Tab 1：命中产品（C4，仅企业员工） ===== -->
      <view v-if="activeTab === 'products'">
        <AppSkeleton v-if="prodLoading" :rows="3" />

        <AppEmpty v-else-if="!products.length"
          title="暂无命中产品明细"
          desc="命中产品明细需「报告 ↔ 产品」关联数据（t_screening_product）支撑，该能力正在补齐中" />

        <view v-else class="stack">
          <AppListItem
            v-for="(p, i) in products" :key="i"
            :title="p.productName"
            :desc="p.bankName"
          >
            <template #leading>
              <view class="score-block" :class="scoreClass(p.matchScore)">{{ p.matchScore }}</view>
            </template>
            <template #meta>
              <text class="meta-text">{{ p.amountRange }}</text>
              <text class="meta-text">{{ p.rate }}</text>
              <text v-if="p.term" class="meta-text">{{ p.term }}</text>
            </template>
            <template #trailing>
              <AppTag type="success" size="sm">匹配度 {{ p.matchScore }}</AppTag>
            </template>

            <!-- 匹配度进度条 -->
            <view class="score-bar">
              <view class="score-track">
                <view class="score-fill" :style="{ width: (p.matchScore || 0) + '%' }" />
              </view>
            </view>
            <!-- 产品标签 -->
            <view v-if="p.tags && p.tags.length" class="tag-row">
              <AppTag v-for="(tg, ti) in p.tags" :key="ti" type="muted" size="sm">{{ tg }}</AppTag>
            </view>
            <!-- 准入要求 -->
            <text v-if="p.requirement" class="req-text">准入要求：{{ p.requirement }}</text>
          </AppListItem>
        </view>
      </view>

      <!-- ===== Tab 2：报告信息（含规则命中说明，客户与员工均可见） ===== -->
      <view v-else-if="activeTab === 'info'" class="stack">
        <view class="card">
          <view class="info-row">
            <text class="info-label">报告编号</text>
            <text class="info-value">{{ report.reportNo }}</text>
          </view>
          <view class="info-row">
            <text class="info-label">生成时间</text>
            <text class="info-value">{{ formatTime(report.createdAt) }}</text>
          </view>
          <view class="info-row">
            <text class="info-label">银行覆盖</text>
            <text class="info-value">{{ report.bankCount || 0 }} 家</text>
          </view>
        </view>

        <view class="card" v-if="report.ruleLogs && report.ruleLogs.length">
          <text class="card-title">规则命中说明</text>
          <view class="rule-item" v-for="(log, i) in report.ruleLogs" :key="i">
            <text class="rule-code">{{ log.ruleCode }}</text>
            <text class="rule-expr">{{ log.expression }}</text>
            <AppTag :type="tagType(log.result)" size="sm">{{ statusLabel(log.result) }}</AppTag>
          </view>
        </view>

        <!-- 客户：明确提示产品明细需联系顾问（对客脱敏，评审决策 08-28） -->
        <view v-if="!isStaff" class="card tip-card">
          <text class="tip-text">对客报告仅展示产品数量与评级，具体产品额度与利率请咨询您的顾问。</text>
        </view>
      </view>

      <!-- ===== Tab 3：经营诊断（C5，按报告 ID 关联） ===== -->
      <view v-else class="stack">
        <AppSkeleton v-if="diagLoading" :rows="3" />

        <template v-else-if="diagnosis">
          <!-- 诊断头：报告关联 + 材料时效提示（P2-1） -->
          <view class="card diag-header">
            <view class="diag-header-l">
              <text class="diag-id">DIAG-{{ report.reportNo }}</text>
              <text class="diag-time">
                基于「{{ report.reportNo }}」上传材料生成 · {{ formatTime(diagnosis.generatedAt) }}
              </text>
            </view>
            <AppButton variant="secondary" size="sm" @click="onUploadMaterial">上传最新材料</AppButton>
          </view>

          <!-- KPI -->
          <view v-if="diagnosis.kpi && diagnosis.kpi.length" class="kpi-row">
            <view v-for="(k, i) in diagnosis.kpi" :key="i" class="kpi-item">
              <text class="kpi-label">{{ k.label }}</text>
              <text class="kpi-value" :class="`kpi-${k.tone || 'neutral'}`">{{ k.value }}</text>
              <text v-if="k.desc" class="kpi-desc">{{ k.desc }}</text>
            </view>
          </view>

          <!-- 经营建议 -->
          <view v-if="diagnosis.suggestions && diagnosis.suggestions.length" class="card">
            <text class="card-title">经营建议</text>
            <view v-for="(s, i) in diagnosis.suggestions" :key="i" class="sugg-item">
              <AppTag :type="s.tagType || 'info'" size="sm">{{ s.type }}</AppTag>
              <text class="sugg-body">{{ s.content }}</text>
            </view>
          </view>

          <!-- 风险提示 -->
          <view v-if="diagnosis.risks && diagnosis.risks.length" class="card">
            <text class="card-title">风险提示</text>
            <view v-for="(r, i) in diagnosis.risks" :key="i" class="risk-item">
              <AppTag :type="riskTagType(r.level)" size="sm">{{ r.level }}</AppTag>
              <text class="risk-body">{{ r.content }}</text>
            </view>
          </view>

          <!-- 历年营业数据 -->
          <view v-if="diagnosis.yearData && diagnosis.yearData.length" class="card">
            <text class="card-title">企业历年营业数据</text>
            <view class="yr-head">
              <text class="yr-cell">年份</text>
              <text class="yr-cell">营收</text>
              <text class="yr-cell">纳税</text>
              <text class="yr-cell">开票</text>
              <text class="yr-cell">利润</text>
            </view>
            <view v-for="(y, i) in diagnosis.yearData" :key="i" class="yr-row">
              <text class="yr-cell">{{ y.year }}</text>
              <text class="yr-cell">{{ y.revenue }}</text>
              <text class="yr-cell">{{ y.tax }}</text>
              <text class="yr-cell">{{ y.invoice }}</text>
              <text class="yr-cell">{{ y.profit }}</text>
            </view>
          </view>

          <!-- 多维统计（含行业均值对比） -->
          <view v-if="diagnosis.dimensions && diagnosis.dimensions.length" class="card">
            <text class="card-title">多维数据统计</text>
            <view v-for="(d, i) in diagnosis.dimensions" :key="i" class="dim-row">
              <text class="dim-label">{{ d.name }}</text>
              <view class="dim-track">
                <view class="dim-fill" :style="{ width: (d.value || 0) + '%' }" />
                <view class="dim-avg" :style="{ left: (d.industryAvg || 0) + '%' }" />
              </view>
              <text class="dim-val">{{ d.value }}</text>
            </view>
            <text class="dim-legend">灰色虚线为行业均值</text>
          </view>

          <!-- 诊断加载失败（与"生成中"空态分离） -->
          <AppEmpty v-if="diagError && !hasDiagContent" title="诊断加载失败"
            desc="网络异常或服务暂不可用，请重试">
            <AppButton variant="primary" size="md" @click="loadDiagnosis(reportNo)">重试</AppButton>
          </AppEmpty>

          <!-- 诊断算法未落地时的空态（后端返回骨架，五块均为空） -->
          <AppEmpty v-else-if="!hasDiagContent"
            title="诊断内容生成中"
            desc="经营诊断需基于材料解析结果与历年经营数据，该算法模块正在补齐，完成后本页将自动填充" />
        </template>
      </view>

      <!-- 合规提示 -->
      <view class="card tip-card">
        <text class="tip-text">本报告仅供融资参考，不构成任何银行通过承诺；具体产品额度与利率以顾问跟进为准。</text>
      </view>

      <AppButton variant="secondary" size="lg" block @click="goMatch">重新匹配</AppButton>
    </template>

    <!-- 空态 / 异常（P1-5 Error 态） -->
    <AppEmpty v-else title="报告不存在或已被移除" desc="可能已被删除，或您没有查看权限">
      <AppButton variant="primary" size="md" @click="goBack">返回列表</AppButton>
    </AppEmpty>
  </view>
</template>

<script setup>
/**
 * 报告详情（C4 命中产品 + C5 经营诊断 + 对客脱敏）。
 *
 * - 企业员工（顾问/经理/老板/运营/超管）：可见「命中产品」tab，用于陪访解读（C4）
 * - 客户：对客脱敏，仅可见报告信息与经营诊断，不展示产品明细（评审决策 08-28）
 * - 渠道：受沙箱隔离，后端直接拒绝，此处不会到达
 *
 * 经营诊断按报告 ID 关联（P2-1），材料非最新时可上传最新材料刷新。
 *
 * 无障碍（P1-3/P1-4）：所有交互元素 min-height 88rpx（44px）；
 * tab 补 role="tab" + aria-selected；列表项由 AppListItem 统一处理键盘与语义。
 */
import { ref, computed } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import { useUserStore } from '../../store/user';
import { reportDetail, reportProducts, reportDiagnosis } from '../../api/match';
import { uploadMaterial } from '../../api/upload';

/** 企业员工（C1/C4）：可查看命中产品；客户对客脱敏 */
const STAFF_ROLES = ['adviser', 'deptmgr', 'boss', 'operator', 'super'];
const store = useUserStore();
const isStaff = computed(() => STAFF_ROLES.indexOf(store.role) >= 0);

const loading = ref(true);
const report = ref(null);
const activeTab = ref('info');

/* 命中产品（C4） */
const products = ref([]);
const prodLoading = ref(false);

/* 经营诊断（C5） */
const diagnosis = ref(null);
const diagLoading = ref(false);
const diagError = ref(false);

/** Tab 定义：客户不展示「命中产品」 */
const tabs = computed(() => {
  const list = [];
  if (isStaff.value) list.push({ key: 'products', label: '命中产品' });
  list.push({ key: 'info', label: '报告信息' });
  list.push({ key: 'diagnosis', label: '经营诊断' });
  return list;
});

/** 诊断是否有实质内容（后端算法未落地时五块皆空 → 显示"生成中"空态） */
const hasDiagContent = computed(() => {
  const d = diagnosis.value;
  if (!d) return false;
  return (d.kpi && d.kpi.length)
    || (d.suggestions && d.suggestions.length)
    || (d.risks && d.risks.length)
    || (d.yearData && d.yearData.length)
    || (d.dimensions && d.dimensions.length);
});

onLoad(async (query) => {
  const reportNo = query && query.reportNo;
  if (!reportNo) {
    loading.value = false;
    return;
  }
  try {
    report.value = await reportDetail(reportNo);
    // 默认落到第一个可见 tab
    activeTab.value = tabs.value[0] ? tabs.value[0].key : 'info';
    // 员工：并行拉取命中产品（C4）
    if (isStaff.value) loadProducts(reportNo);
  } catch (e) {
    report.value = null;
  } finally {
    loading.value = false;
  }
});

/** 切到产品 tab 时懒加载，避免首屏多余请求 */
function onTabChange(key) {
  activeTab.value = key;
  const no = report.value && report.value.reportNo;
  if (key === 'products' && isStaff.value && !products.value.length && no) {
    loadProducts(no);
  }
  if (key === 'diagnosis' && !diagnosis.value && no) {
    loadDiagnosis(no);
  }
}

async function loadProducts(reportNo) {
  prodLoading.value = true;
  try {
    const data = await reportProducts(reportNo);
    products.value = Array.isArray(data) ? data : [];
  } catch (e) {
    products.value = [];
  } finally {
    prodLoading.value = false;
  }
}

async function loadDiagnosis(reportNo) {
  diagLoading.value = true;
  diagError.value = false;
  try {
    diagnosis.value = await reportDiagnosis(reportNo);
  } catch (e) {
    diagnosis.value = { kpi: [], suggestions: [], risks: [], yearData: [], dimensions: [] };
    diagError.value = true;
  } finally {
    diagLoading.value = false;
  }
}

/** C5：材料非最新时上传最新材料刷新诊断（T2：OCR 回灌状态诚实回显） */
function onUploadMaterial() {
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    success: async (res) => {
      const filePath = res.tempFilePaths && res.tempFilePaths[0];
      if (!filePath) return;
      const no = report.value && report.value.reportNo;
      uni.showLoading({ title: '上传中…' });
      try {
        const uploadRes = await uploadMaterial(filePath, {
          bizType: 'FINANCIAL_STATEMENT',
          clientCode: store.clientCode,
          reportNo: no,
        });
        uni.hideLoading();
        // 诚实回显：取响应 data（uploadImage 已解包 Result.data；此处防御性二次解包）
        const d = (uploadRes && uploadRes.data) ? uploadRes.data : uploadRes;
        const ocrApplied = !!(d && d.ocrApplied);
        if (ocrApplied === true) {
          const recognized = (d.extractedFields && d.extractedFields.length)
            || (d.mergedCount || 0);
          uni.showToast({
            title: `已识别 ${recognized} 项，诊断已刷新`,
            icon: 'none',
          });
        } else {
          // 含 Mock 环境下 ocrApplied=false：绝不承诺「已识别 N 项」
          uni.showToast({ title: '材料已上传，人工核验后更新', icon: 'none' });
        }
        // 重新拉取诊断（后端基于最新材料重算）
        if (no) await loadDiagnosis(no);
      } catch (e) {
        uni.hideLoading();
        uni.showToast({ title: (e && e.message) || '上传失败', icon: 'none' });
      }
    },
    fail: () => {},
  });
}

/* ===== 展示辅助 ===== */
const resultClass = computed(() => {
  const t = (report.value && report.value.totalResult) || '';
  if (t === 'PASS') return 'pass';
  if (t === 'CONDITION') return 'condition';
  return 'reject';
});

const resultLabel = computed(() => {
  const t = (report.value && report.value.totalResult) || '';
  return { PASS: '可进件', CONDITION: '需补料', REJECT: '暂不匹配', SKIP_SEGMENT_MISMATCH: '暂不匹配' }[t] || (t || '匹配完成');
});

const ratingLabel = computed(() => {
  const r = report.value && report.value.rating;
  if (r) return r;
  const g = report.value && report.value.grade;
  return { HIGH: '高', MIDDLE: '中', LOW: '低' }[g] || (g || '-');
});

/** 匹配度色阶：≥85 高（品牌蓝）/ 70-84 中（暖金）/ <70 低（灰） */
function scoreClass(score) {
  const s = Number(score) || 0;
  if (s >= 85) return 'sc-high';
  if (s >= 70) return 'sc-mid';
  return 'sc-low';
}

function tagType(result) {
  if (result === 'PASS') return 'success';
  if (result === 'FAIL') return 'danger';
  return 'muted';
}

function riskTagType(level) {
  if (level === '高' || level === 'HIGH') return 'danger';
  if (level === '中' || level === 'MIDDLE' || level === 'MEDIUM') return 'warning';
  return 'info';
}

function statusLabel(s) {
  return { PASS: '通过', FAIL: '未通过', SKIP: '跳过', SKIP_SEGMENT_MISMATCH: '不适用', ERROR: '异常' }[s] || (s || '');
}

function formatTime(t) {
  if (!t) return '';
  const s = String(t).replace('T', ' ').replace(/-/g, '/');
  return s.length > 19 ? s.slice(0, 19) : s;
}

function goMatch() { uni.reLaunch({ url: '/pages/match/match' }); }
function goBack() { uni.navigateBack(); }
</script>

<style scoped>
.detail-page {
  min-height: 100vh;
  padding: 0 var(--space-4) var(--space-12);
  background: var(--bg-page);
  box-sizing: border-box;
}

.stack view { margin-top: var(--space-3); }
.stack view:first-child { margin-top: 0; }

/* ===== 结果卡（扁平化，去除渐变，对齐设计系统） ===== */
.result-card {
  margin-top: var(--space-3);
  border-radius: var(--radius-lg);
  padding: var(--space-8) var(--space-4);
  color: var(--text-invert);
  text-align: center;
}
.rc-pass { background: var(--success); }
.rc-condition { background: var(--warning); }
.rc-reject { background: var(--text-secondary); }
.result-label { font-size: var(--fs-xl); font-weight: 800; }
.result-metrics {
  display: flex; align-items: center; justify-content: center;
  margin-top: var(--space-4);
}
.metric { flex: 1; text-align: center; }
.metric-num { display: block; font-size: 56rpx; font-weight: 800; line-height: 1.1; }
.metric-name { display: block; font-size: var(--fs-sm); opacity: .85; margin-top: var(--space-1); }
.metric-divider { width: 2rpx; height: 80rpx; background: rgba(255, 255, 255, .25); }

/* ===== Tab 分段（触控 44px + 键盘语义） ===== */
.tab-seg {
  display: flex;
  gap: var(--space-1);
  background: var(--bg-input);
  padding: var(--space-1);
  border-radius: var(--radius-md);
  margin-top: var(--space-3);
}
.tab-item {
  flex: 1;
  text-align: center;
  font-size: var(--fs-sm);
  padding: 20rpx 8rpx;
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  min-height: 88rpx;      /* 44px 触控 */
  line-height: 48rpx;
}
.tab-item.active {
  background: var(--bg-card);
  color: var(--brand-deep);
  font-weight: 700;
  box-shadow: var(--shadow-sm);
}

/* ===== 命中产品 ===== */
.score-block {
  width: 92rpx; height: 92rpx;
  border-radius: var(--radius-md);
  display: flex; align-items: center; justify-content: center;
  font-size: var(--fs-lg); font-weight: 700; color: var(--text-invert);
}
.sc-high { background: var(--brand-deep); }
.sc-mid { background: var(--gold); }
.sc-low { background: var(--text-secondary); }

.meta-text { font-size: var(--fs-sm); color: var(--text-secondary); }

.score-bar { margin-top: var(--space-2); }
.score-track {
  height: 12rpx; border-radius: 6rpx;
  background: var(--bg-input); overflow: hidden;
}
.score-fill {
  height: 100%; border-radius: 6rpx;
  background: var(--brand-deep);
}

.tag-row { display: flex; flex-wrap: wrap; gap: var(--space-1); margin-top: var(--space-2); }
.req-text {
  display: block; margin-top: var(--space-2);
  font-size: var(--fs-sm); color: var(--text-secondary); line-height: var(--lh-base);
}

/* ===== 报告信息 ===== */
.card {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  padding: var(--space-4);
  box-shadow: var(--shadow-md);
  margin-top: var(--space-3);
}
.card-title { font-size: var(--fs-lg); font-weight: 700; color: var(--text-primary); }

.info-row {
  display: flex; justify-content: space-between; align-items: center;
  padding: var(--space-2) 0; min-height: 64rpx;
  border-bottom: 2rpx solid var(--line);
}
.info-row:last-child { border-bottom: none; }
.info-label { font-size: var(--fs-sm); color: var(--text-secondary); }
.info-value { font-size: var(--fs-md); color: var(--text-primary); font-weight: 600; }

.rule-item {
  display: flex; align-items: center; gap: var(--space-2);
  padding: var(--space-2) 0; min-height: 64rpx;
  border-bottom: 2rpx solid var(--line);
}
.rule-item:last-child { border-bottom: none; }
.rule-code { font-size: var(--fs-sm); font-weight: 600; color: var(--text-primary); flex-shrink: 0; }
.rule-expr { flex: 1; min-width: 0; font-size: var(--fs-sm); color: var(--text-secondary); }

.tip-card {
  background: var(--warning-bg);
  border: 1rpx solid var(--warning-line);
  box-shadow: none;
  border-radius: var(--radius-md);
}
.tip-text {
  font-size: var(--fs-sm);
  color: var(--warning-text);
  line-height: var(--lh-base);
}

/* ===== 经营诊断 ===== */
.diag-header { display: flex; align-items: flex-end; justify-content: space-between; gap: var(--space-3); }
.diag-header-l { flex: 1; min-width: 0; }
.diag-id {
  display: block; font-size: var(--fs-md); font-weight: 700;
  color: var(--brand-deep); font-family: ui-monospace, monospace;
}
.diag-time { display: block; font-size: var(--fs-xs); color: var(--text-secondary); margin-top: var(--space-1); }

.kpi-row { display: flex; gap: var(--space-2); margin-top: var(--space-3); }
.kpi-item {
  flex: 1; background: var(--bg-card); border-radius: var(--radius-md);
  padding: var(--space-3) var(--space-2); text-align: center; box-shadow: var(--shadow-sm);
}
.kpi-label { display: block; font-size: var(--fs-xs); color: var(--text-secondary); margin-bottom: var(--space-1); }
.kpi-value { display: block; font-size: var(--fs-xl); font-weight: 700; line-height: 1.1; }
.kpi-desc { display: block; font-size: var(--fs-xs); color: var(--text-secondary); margin-top: var(--space-1); }
.kpi-success { color: var(--success-text); }
.kpi-warning { color: var(--warning-text); }
.kpi-danger { color: var(--danger-text); }
.kpi-neutral { color: var(--text-primary); }

.sugg-item, .risk-item {
  display: flex; align-items: flex-start; gap: var(--space-2);
  padding: var(--space-3) 0; border-bottom: 2rpx solid var(--line);
}
.sugg-item:last-child, .risk-item:last-child { border-bottom: none; }
.sugg-body, .risk-body {
  flex: 1; min-width: 0; font-size: var(--fs-md);
  color: var(--text-body); line-height: var(--lh-base);
}

/* 历年数据表 */
.yr-head, .yr-row {
  display: flex; padding: var(--space-2) 0; min-height: 64rpx; align-items: center;
}
.yr-head { background: var(--bg-input); border-radius: var(--radius-sm); }
.yr-row { border-bottom: 2rpx solid var(--line); }
.yr-row:last-child { border-bottom: none; }
.yr-cell {
  flex: 1; text-align: right; font-size: var(--fs-sm); color: var(--text-body);
}
.yr-cell:first-child { text-align: left; font-weight: 600; color: var(--text-primary); }

/* 多维统计（含行业均值虚线） */
.dim-row { display: flex; align-items: center; gap: var(--space-2); padding: var(--space-2) 0; min-height: 64rpx; }
.dim-label { width: 120rpx; flex-shrink: 0; font-size: var(--fs-sm); color: var(--text-body); }
.dim-track {
  flex: 1; height: 16rpx; border-radius: 8rpx;
  background: var(--bg-input); position: relative; overflow: hidden;
}
.dim-fill { height: 100%; border-radius: 8rpx; background: var(--brand-deep); }
.dim-avg { position: absolute; top: 0; bottom: 0; width: 0; border-left: 3rpx dashed rgba(11, 29, 58, .4); }
.dim-val { width: 60rpx; flex-shrink: 0; text-align: right; font-size: var(--fs-sm); font-weight: 700; color: var(--text-primary); }
.dim-legend { display: block; font-size: var(--fs-xs); color: var(--text-secondary); margin-top: var(--space-2); }
</style>
