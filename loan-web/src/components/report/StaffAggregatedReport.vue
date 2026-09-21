<template>
  <div class="staff-report">
    <div class="internal-notice">
      <AppIcon name="reportDoc" :size="18" />
      <div>
        <strong>公司员工内部经营咨询报告</strong>
        <span>仅用于电话或来访咨询，不向客户展示或直接转发</span>
      </div>
    </div>

    <div class="report-banner" :class="`rb-${gradeClass}`">
      <div>
        <span class="banner-eyebrow">{{ profile.enterpriseName || profile.contactName || '客户经营分析' }}</span>
        <strong>{{ gradeText[summary.grade] || summary.grade || '待分析' }} · {{ summary.rating || '待评级' }}</strong>
        <span class="banner-meta">{{ formatDateTime(summary.createdAt) }} · {{ detail.reportNo }}</span>
      </div>
      <div class="banner-metrics">
        <div><strong>{{ summary.productCount || 0 }}</strong><span>现有匹配产品</span></div>
        <div><strong>{{ summary.bankCount || 0 }}</strong><span>涉及机构</span></div>
        <div><strong>{{ summary.passCount || 0 }}/{{ summary.conditionCount || 0 }}/{{ summary.rejectCount || 0 }}</strong><span>通过/有条件/未匹配</span></div>
      </div>
    </div>

    <section class="report-section">
      <h3>1. 客户画像</h3>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="客户业务ID"><span class="mono">{{ profile.clientCode || '—' }}</span></el-descriptions-item>
        <el-descriptions-item label="客户类型">{{ customerGroupText(profile.customerGroup) }}</el-descriptions-item>
        <el-descriptions-item label="企业名称">{{ profile.enterpriseName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="联系人">{{ profile.contactName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="联系电话">{{ profile.contactPhoneMasked || '—' }}</el-descriptions-item>
        <el-descriptions-item :label="profile.identityType || '唯一身份标识'">{{ profile.identityMasked || profile.creditCodeMasked || '—' }}</el-descriptions-item>
        <el-descriptions-item label="归属顾问">{{ profile.ownerStaffCode || '待分配' }}</el-descriptions-item>
        <el-descriptions-item label="客户状态">{{ profile.status || '—' }}</el-descriptions-item>
        <el-descriptions-item label="VIP 等级">{{ profile.vipLevel || '普通客户' }}</el-descriptions-item>
      </el-descriptions>
    </section>

    <section class="report-section">
      <h3>2. 材料状态</h3>
      <div class="material-grid">
        <div><span>提交状态</span><strong>{{ material.submissionStatus || '暂无提交单' }}</strong></div>
        <div><span>材料版本</span><strong>{{ material.materialVersion || 'v1' }}</strong></div>
        <div><span>待复核</span><strong>{{ material.pendingReviewCount || 0 }}</strong></div>
        <div><span>已通过</span><strong>{{ material.approvedCount || 0 }}</strong></div>
        <div><span>已驳回</span><strong>{{ material.rejectedCount || 0 }}</strong></div>
      </div>
      <div class="section-meta">最近提交：{{ formatDateTime(material.submittedAt) }}；最近复核：{{ formatDateTime(material.latestReviewAt) }}</div>
    </section>

    <section class="report-section">
      <h3>3. 经营分析</h3>
      <div class="kpi-grid">
        <div v-for="item in analysis.kpi || []" :key="item.label" class="kpi-card" :class="`tone-${item.tone || 'neutral'}`">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
          <small>{{ item.desc }}</small>
        </div>
      </div>

      <div v-if="(analysis.dimensions || []).length" class="analysis-block">
        <h4>多维度分析</h4>
        <div class="dimension-grid">
          <div v-for="item in analysis.dimensions" :key="item.dimensionCode || item.name" class="dimension-row">
            <div><span>{{ item.name }}</span><small>行业参考 {{ item.industryAvg ?? '—' }}</small></div>
            <el-progress :percentage="Number(item.value || 0)" :stroke-width="8" />
          </div>
        </div>
      </div>

      <div class="analysis-columns">
        <div class="analysis-block">
          <h4>风险关注</h4>
          <div v-for="(item, index) in analysis.risks || []" :key="index" class="text-item risk-item">
            <el-tag size="small" type="warning">{{ item.level || '提示' }}</el-tag>
            <span>{{ item.content }}</span>
          </div>
          <div v-if="!(analysis.risks || []).length" class="empty-text">暂无风险提示</div>
        </div>
        <div class="analysis-block">
          <h4>咨询建议</h4>
          <div v-for="(item, index) in analysis.suggestions || []" :key="index" class="text-item">
            <el-tag size="small" :type="tagType(item.tagType)">{{ item.type || '建议' }}</el-tag>
            <span>{{ item.content }}</span>
          </div>
          <div v-if="!(analysis.suggestions || []).length" class="empty-text">暂无咨询建议</div>
        </div>
      </div>
    </section>

    <section class="report-section">
      <h3>4. 现有匹配结果</h3>
      <el-table v-if="products.length" :data="products" border size="small">
        <el-table-column prop="productName" label="产品" min-width="150">
          <template #default="{ row }">{{ row.productName || row.productCode || '—' }}</template>
        </el-table-column>
        <el-table-column prop="bankName" label="机构" min-width="120" />
        <el-table-column label="匹配结果" width="100">
          <template #default="{ row }"><el-tag size="small" :type="resultTag(row.hitResult)">{{ resultText(row.hitResult) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="matchScore" label="匹配度" width="85"><template #default="{ row }">{{ row.matchScore ?? '—' }}</template></el-table-column>
        <el-table-column prop="amountRange" label="额度区间" min-width="110" />
        <el-table-column prop="rate" label="利率区间" min-width="100" />
        <el-table-column prop="term" label="期限" min-width="100" />
      </el-table>
      <AppEmpty v-else title="暂无既有匹配结果" desc="本报告不会在查看时重新执行匹配" :min-height="'130px'" />
      <div class="section-meta">以上为报告生成时已落库的内部匹配结果，打开详情不会重新执行匹配。</div>
    </section>

    <section class="report-section">
      <h3>5. 报告说明</h3>
      <div class="data-notice"><AppIcon name="success" :size="16" /><span>{{ detail.dataSourceNotice }}</span></div>
      <div class="compliance-notice">本报告用于公司员工开展经营咨询和风险提示，不构成授信、额度、利率或审批结果承诺。</div>
    </section>
  </div>
</template>

<script setup>
import { computed } from 'vue';
import AppEmpty from '@/components/AppEmpty.vue';
import AppIcon from '@/components/AppIcon.vue';
import { formatDateTime } from '@/utils/format';

const props = defineProps({ detail: { type: Object, required: true } });
const summary = computed(() => props.detail.reportSummary || {});
const profile = computed(() => props.detail.clientProfile || {});
const material = computed(() => props.detail.materialStatus || {});
const analysis = computed(() => props.detail.businessAnalysis || {});
const products = computed(() => props.detail.matchedProducts || []);
const gradeText = { HIGH: '高', MIDDLE: '中', LOW: '低' };
const gradeClass = computed(() => ({ HIGH: 'high', MIDDLE: 'middle', LOW: 'low' }[summary.value.grade] || 'low'));

const customerGroupText = (value) => ({ ENTERPRISE: '企业客户', PERSONAL: '个人客户' }[value] || value || '—');
const resultText = (value) => ({ PASS: '通过', CONDITION: '有条件', REJECT: '未匹配' }[value] || value || '—');
const resultTag = (value) => ({ PASS: 'success', CONDITION: 'warning', REJECT: 'info' }[value] || 'info');
const tagType = (value) => ({ success: 'success', warning: 'warning', danger: 'danger', info: 'info' }[value] || 'info');
</script>

<style scoped>
.staff-report { padding-right: 4px; color: var(--loan-text); }
.internal-notice, .data-notice { display:flex; align-items:flex-start; gap:10px; border-radius:8px; padding:12px 14px; }
.internal-notice { margin-bottom:14px; background:var(--loan-warning-bg); border:1px solid var(--loan-warning-line); color:var(--loan-warning-text); }
.internal-notice div { display:flex; flex-direction:column; gap:3px; }
.internal-notice span, .section-meta, .empty-text { font-size:12px; color:var(--loan-text-secondary,var(--loan-text-muted)); }
.report-banner { display:flex; justify-content:space-between; gap:24px; padding:20px; border-radius:12px; color:var(--loan-paper); margin-bottom:18px; }
.rb-high { background:var(--loan-primary); }
.rb-middle { background:var(--loan-warning); }
.rb-low { background:var(--loan-text-secondary,var(--loan-text-muted)); }
.report-banner > div:first-child { display:flex; flex-direction:column; gap:5px; }
.banner-eyebrow, .banner-meta { font-size:12px; opacity:.85; }
.report-banner strong { font-size:18px; }
.banner-metrics { display:grid; grid-template-columns:repeat(3,minmax(92px,1fr)); align-items:center; text-align:center; }
.banner-metrics div { padding:0 14px; border-left:1px solid rgba(255,255,255,.24); }
.banner-metrics strong, .banner-metrics span { display:block; }
.banner-metrics strong { font-size:19px; }
.banner-metrics span { margin-top:3px; font-size:11px; opacity:.86; }
.report-section { margin-bottom:20px; }
.report-section h3 { margin:0 0 10px; padding-left:9px; border-left:3px solid var(--loan-primary); font-size:15px; }
.material-grid, .kpi-grid { display:grid; gap:10px; }
.material-grid { grid-template-columns:repeat(5,minmax(0,1fr)); }
.material-grid div, .kpi-card { padding:12px; border:1px solid var(--loan-border); background:var(--loan-surface); border-radius:8px; }
.material-grid span, .material-grid strong, .kpi-card span, .kpi-card strong, .kpi-card small { display:block; }
.material-grid span, .kpi-card span { font-size:12px; color:var(--loan-text-secondary,var(--loan-text-muted)); }
.material-grid strong { margin-top:5px; }
.section-meta { margin-top:8px; }
.kpi-grid { grid-template-columns:repeat(4,minmax(0,1fr)); }
.kpi-card { border-top:3px solid var(--loan-border); }
.kpi-card.tone-success { border-top-color:var(--loan-success); }
.kpi-card.tone-warning { border-top-color:var(--loan-warning); }
.kpi-card.tone-danger { border-top-color:var(--loan-danger); }
.kpi-card strong { margin:7px 0 5px; font-size:18px; }
.kpi-card small { line-height:1.5; color:var(--loan-text-secondary,var(--loan-text-muted)); }
.analysis-columns { display:grid; grid-template-columns:1fr 1fr; gap:12px; margin-top:12px; }
.analysis-block { padding:13px; border:1px solid var(--loan-border); border-radius:8px; margin-top:12px; }
.analysis-block h4 { margin:0 0 10px; font-size:13px; }
.dimension-grid { display:grid; grid-template-columns:1fr 1fr; gap:12px 20px; }
.dimension-row > div { display:flex; justify-content:space-between; margin-bottom:5px; font-size:12px; }
.dimension-row small { color:var(--loan-text-secondary,var(--loan-text-muted)); }
.text-item { display:flex; align-items:flex-start; gap:8px; font-size:12px; line-height:1.6; margin:8px 0; }
.text-item .el-tag { flex:0 0 auto; margin-top:1px; }
.data-notice { background:var(--loan-success-bg); border:1px solid var(--loan-success-line); color:var(--loan-success-text); }
.compliance-notice { margin-top:8px; padding:11px 14px; background:var(--loan-surface); border-radius:8px; font-size:12px; line-height:1.6; }
@media (max-width: 900px) {
  .report-banner { flex-direction:column; }
  .banner-metrics div:first-child { border-left:0; }
  .material-grid { grid-template-columns:repeat(3,minmax(0,1fr)); }
  .kpi-grid, .dimension-grid, .analysis-columns { grid-template-columns:1fr 1fr; }
}
</style>
