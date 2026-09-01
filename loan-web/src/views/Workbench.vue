<template>
  <div class="workbench">
    <div class="loan-page-header">
      <div>
        <h2 class="loan-page-title">我的工作台</h2>
        <p class="loan-page-subtitle">企业贷款咨询服务 · 阶段一最小闭环</p>
      </div>
      <div class="header-meta">
        <el-tooltip :content="nowText" placement="bottom">
          <span class="meta-time">
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.7"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></svg>
            {{ nowText }}
          </span>
        </el-tooltip>
      </div>
    </div>

    <!-- 指标卡（含趋势） -->
    <div class="metric-grid">
      <div v-for="m in metrics" :key="m.label" class="metric-card loan-card loan-card-hover">
        <div class="metric-icon" :style="{ color: m.color, background: m.bg }">
          <span v-html="m.icon"></span>
        </div>
        <div class="metric-body">
          <div class="metric-label">{{ m.label }}</div>
          <div class="metric-value" :title="m.value">{{ m.value }}</div>
          <div class="metric-foot">
            <span class="metric-foot__text">{{ m.foot }}</span>
            <span v-if="m.trend" class="metric-trend" :class="['trend-' + m.trend.dir]">
              <svg v-if="m.trend.dir === 'up'" viewBox="0 0 24 24" width="10" height="10" fill="none" stroke="currentColor" stroke-width="2.4"><path d="M5 15l7-7 7 7"/></svg>
              <svg v-else viewBox="0 0 24 24" width="10" height="10" fill="none" stroke="currentColor" stroke-width="2.4"><path d="M5 9l7 7 7-7"/></svg>
              {{ m.trend.text }}
            </span>
          </div>
        </div>
      </div>
    </div>

    <!-- 待办事项 -->
    <div class="loan-card todo-card">
      <h3 class="panel-title">待办事项</h3>
      <div class="todo-grid">
        <router-link v-for="t in todos" :key="t.path" :to="t.path" class="todo-item" :class="{ 'todo-empty': t.count === 0 }">
          <span class="todo-count mono">{{ t.count }}</span>
          <span class="todo-body">
            <span class="todo-name">{{ t.name }}</span>
            <span class="todo-desc">{{ t.desc }}</span>
          </span>
          <span class="quick-arrow" aria-hidden="true">→</span>
        </router-link>
      </div>
    </div>

    <!-- 合作库到期预警 -->
    <div class="loan-card expire-card">
      <h3 class="panel-title">
        合作库到期预警
        <span v-if="expiring.length" class="panel-tip">{{ expiring.length }} 个产品即将到期</span>
        <router-link to="/product" class="panel-link">前往合作库 →</router-link>
      </h3>
      <div v-if="expiring.length" class="expire-list">
        <div v-for="p in expiring" :key="p.bankProductCode" class="expire-item" :title="p.productName || p.bankProductCode">
          <span class="expire-code mono">{{ p.bankProductCode }}</span>
          <span class="expire-until">{{ formatPartnerUntil(p.cooperateUntil) }}</span>
          <span class="expire-badge" :class="remainClass(p)">{{ remainText(p) }}</span>
        </div>
      </div>
      <AppEmpty v-else title="暂无即将到期的合作产品" :minHeight="'120px'" />
    </div>

    <!-- 快捷入口 + 最近活动 -->
    <div class="row-2col">
      <div class="loan-card quick-card">
        <h3 class="panel-title">快捷操作</h3>
        <div class="quick-grid">
          <router-link v-for="q in quick" :key="q.path" :to="q.path" class="quick-item">
            <span class="quick-icon" v-html="q.icon"></span>
            <span class="quick-body">
              <span class="quick-name">{{ q.name }}</span>
              <span class="quick-desc">{{ q.desc }}</span>
            </span>
            <span class="quick-arrow" aria-hidden="true">→</span>
          </router-link>
        </div>
      </div>

      <div class="loan-card recent-card">
        <h3 class="panel-title">
          最近匹配
          <el-tag size="small" type="info" effect="plain" round>近 7 天</el-tag>
        </h3>
        <ul class="recent-list">
          <li
            v-for="r in recentMatches"
            :key="r.id"
            class="recent-item"
            role="link"
            tabindex="0"
            :title="`查看审计详情 · ${r.trace}`"
            @click="goAudit(r.trace)"
            @keyup.enter="goAudit(r.trace)"
          >
            <span class="recent-dot" :class="['dot-' + r.result]"></span>
            <div class="recent-body">
              <div class="recent-line">
                <span class="recent-product">{{ r.product }}</span>
                <DictTag type="totalResult" :value="r.result" />
              </div>
              <div class="recent-meta">
                <span>{{ r.time }}</span>
                <span class="recent-trace mono">{{ r.trace }}</span>
              </div>
            </div>
          </li>
        </ul>
      </div>
    </div>

    <!-- 主链路 -->
    <div class="loan-card chain-card">
      <h3 class="panel-title">核心主链路</h3>
      <div class="chain">
        <template v-for="(s, i) in chain" :key="s">
          <span class="chain-step">{{ s }}</span>
          <span v-if="i < chain.length - 1" class="chain-arrow" aria-hidden="true">→</span>
        </template>
      </div>
      <p class="chain-note">阶段一聚焦「配置产品 → 调试匹配 → 档位 → 审计」主链路，小程序 / 短信 / OCR / 权限中心等模块后续迭代追加。</p>
    </div>
  </div>
</template>

<script setup>
defineOptions({ name: '_workbench' });
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';
import DictTag from '@/components/DictTag.vue';
import AppEmpty from '@/components/AppEmpty.vue';
import { pageAudit } from '@/api/audit';
import { dashboardTodo, configStatus } from '@/api/dashboard';
import { reportOverview } from '@/api/report';
import { pagePartnerProducts } from '@/api/partnerProduct';
import { formatDateTime } from '@/utils/format';

const nowText = ref('');
const status = ref({});
const todo = ref({});
const overview = ref({});
let timer = null;
function refreshNow() {
  const d = new Date();
  const pad = (n) => String(n).padStart(2, '0');
  const w = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()];
  nowText.value = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} 周${w} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}
onMounted(() => {
  refreshNow();
  timer = setInterval(refreshNow, 30000);
  loadRecent();
  loadStats();
  loadExpiring();
});
onUnmounted(() => {
  if (timer) clearInterval(timer);
});

/** 加载真实统计（配置完成度 + 待办 + 总览） */
async function loadStats() {
  try {
    const [s, t, o] = await Promise.all([configStatus(), dashboardTodo(), reportOverview()]);
    status.value = s.data || {};
    todo.value = t.data || {};
    overview.value = o.data || {};
  } catch (e) {
    // 拦截器已提示
  }
}

/** 指标卡（真实数据：配置完成度 + 经营总览） */
const metrics = computed(() => {
  const c = status.value || {};
  const o = overview.value || {};
  return [
    {
      label: '合作银行',
      value: String(c.channelCount ?? '-'),
      foot: '家渠道已接入',
      color: 'var(--loan-info)',
      bg: 'color-mix(in srgb, var(--loan-info) 10%, transparent)',
      icon: '<svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M3 9l9-5 9 5-9 5-9-5z"/><path d="M3 9v6l9 5 9-5V9"/><path d="M12 14v6"/></svg>',
    },
    {
      label: '产品库',
      value: String(c.productCount ?? '-'),
      foot: '个产品（含审核）',
      color: 'var(--loan-primary)',
      bg: 'var(--loan-primary-soft)',
      icon: '<svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M4 7l8-4 8 4-8 4-8-4z"/><path d="M4 7v10l8 4 8-4V7"/><path d="M12 11v10"/></svg>',
    },
    {
      label: '成交金额',
      value: '¥' + fmtAmount(o.dealAmountSum),
      foot: `${o.dealOrderCount ?? 0} 单已成交`,
      color: 'var(--loan-success)',
      bg: 'color-mix(in srgb, var(--loan-success) 10%, transparent)',
      icon: '<svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M12 1v22"/><path d="M17 5H9.5a3.5 3.5 0 000 7h5a3.5 3.5 0 010 7H6"/></svg>',
    },
    {
      label: '奖励发放',
      value: '¥' + fmtAmount(o.rewardAmountSum),
      foot: `${o.rewardCount ?? 0} 单奖励`,
      color: 'var(--loan-accent)',
      bg: 'var(--loan-accent-soft)',
      icon: '<svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M12 2l2.9 6.3 6.9.8-5.1 4.7 1.4 6.8L12 17.3 5.9 20.6l1.4-6.8L2.2 9.1l6.9-.8L12 2z"/></svg>',
    },
  ];
});

/** 待办事项（真实统计） */
const todos = computed(() => {
  const t = todo.value || {};
  return [
    { name: '待审核产品', count: t.pendingProductApproval ?? 0, desc: '渠道提交的产品审核工单', path: '/approval' },
    { name: '待审批下载', count: t.pendingDownloadApproval ?? 0, desc: '无水印下载申请', path: '/approval' },
    { name: '待审核奖励', count: t.pendingReward ?? 0, desc: '成交自动结算待发放', path: '/reward' },
    { name: '我的工单', count: t.myOrderCount ?? 0, desc: '服务中工单', path: '/order' },
    { name: '我的线索', count: t.myLeadCount ?? 0, desc: '跟进中线索', path: '/lead' },
  ];
});

function fmtAmount(v) {
  return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

const quick = [
  {
    path: '/product',
    name: '产品库',
    desc: '全量库 / 合作库双层管理',
    icon: '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M4 7l8-4 8 4-8 4-8-4z"/><path d="M4 7v10l8 4 8-4V7"/><path d="M12 11v10"/></svg>',
  },
  {
    path: '/rule',
    name: '规则集',
    desc: '企业 16 条准入规则四分类',
    icon: '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M8 6h13M8 12h13M8 18h13"/><path d="M3 6h.01M3 12h.01M3 18h.01"/></svg>',
  },
  {
    path: '/audit',
    name: '审计日志',
    desc: '匹配全链路 traceUuid 追踪',
    icon: '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.7"><circle cx="11" cy="11" r="7"/><path d="M21 21l-4.3-4.3"/></svg>',
  },
  {
    path: '/config-wizard',
    name: '系统配置',
    desc: '首次上线基础配置一键完成',
    icon: '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M12 2l8 4v6c0 5-3.4 8.6-8 10-4.6-1.4-8-5-8-10V6l8-4z"/><path d="M9 12l2 2 4-4"/></svg>',
  },
];

/** 最近匹配（对接 /api/admin/audit/page 取真实审计记录） */
const recentMatches = ref([]);

function shortTrace(uuid) {
  if (!uuid) return '-';
  return uuid.length > 12 ? `${uuid.slice(0, 6)}…${uuid.slice(-4)}` : uuid;
}

function relativeTime(timeStr) {
  if (!timeStr) return '-';
  const t = new Date(String(timeStr).replace(' ', 'T'));
  if (Number.isNaN(t.getTime())) return '-';
  const diff = Date.now() - t.getTime();
  if (diff < 60000) return '刚刚';
  if (diff < 3600000) return `${Math.floor(diff / 60000)} 分钟前`;
  if (diff < 86400000) return `${Math.floor(diff / 3600000)} 小时前`;
  return `${Math.floor(diff / 86400000)} 天前`;
}

const router = useRouter();

/** 合作库到期预警（GET /api/admin/partner-product/page?status=EXPIRING） */
const expiring = ref([]);

function partnerRemainDays(until) {
  if (!until) return null;
  const t = new Date(String(until).replace(' ', 'T'));
  if (Number.isNaN(t.getTime())) return null;
  return Math.ceil((t.getTime() - Date.now()) / 86400000);
}

function formatPartnerUntil(until) {
  return until ? formatDateTime(until).slice(0, 10) : '—';
}

function remainText(p) {
  const d = partnerRemainDays(p.cooperateUntil);
  if (d === null) return '—';
  return d > 0 ? `剩 ${d} 天` : '已到期';
}

function remainClass(p) {
  const d = partnerRemainDays(p.cooperateUntil);
  if (d === null) return '';
  return d !== null && d <= 7 ? 'badge-danger' : 'badge-warning';
}

async function loadExpiring() {
  try {
    const res = await pagePartnerProducts({ status: 'EXPIRING', page: 1, size: 10 });
    expiring.value = res.data?.records || [];
  } catch (e) {
    expiring.value = [];
  }
}

/** 最近匹配项 → 审计中心 trace 详情 */
function goAudit(trace) {
  router.push({ path: '/audit', query: trace ? { trace } : {} });
}

async function loadRecent() {
  try {
    const res = await pageAudit({ page: 1, size: 5 });
    recentMatches.value = (res.data?.records || []).map((t) => ({
      id: t.id,
      product: `匹配 ${shortTrace(t.traceUuid)}`,
      result: t.totalResult,
      time: relativeTime(t.executedAt),
      trace: t.traceUuid,
    }));
  } catch (e) {
    recentMatches.value = [];
  }
}

const chain = ['认证', '资料提取', '规则引擎匹配', '档位聚合', '报告与审计'];
</script>

<style scoped>
/* 页头右侧时间 */
.header-meta {
  display: flex;
  align-items: center;
  gap: 12px;
}

.meta-time {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 12px;
  font-size: 12px;
  color: var(--loan-text-secondary);
  background: var(--loan-surface);
  border: 1px solid var(--loan-border);
  border-radius: 999px;
}

.metric-grid {
  display: grid;
  /* minmax(0, 1fr) 而非默认 minmax(auto, 1fr)：避免 "¥100,000.00" 等大字号撑开列宽，
     导致 4 列不等宽、右侧留白；列内内容溢出由 metric-value/min-width:0 截断 */
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 20px;              /* 16→20px 卡片呼吸更舒展 */
  margin-bottom: 20px;    /* 16→20px 与下方待办区拉开 */
}

/* 待办事项 */
.todo-card {
  margin-bottom: 16px;
}
.todo-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 10px;
}
.todo-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border: 1px solid var(--loan-border);
  border-radius: var(--loan-radius);
  background: var(--loan-card-bg);
  text-decoration: none;
  transition: border-color var(--loan-transition), transform var(--loan-transition);
}
.todo-item:hover {
  border-color: var(--loan-primary);
  transform: translateY(-2px);
}
.todo-empty {
  opacity: 0.62;
}
.todo-count {
  font-size: 22px;
  font-weight: 700;
  color: var(--loan-primary);
  min-width: 34px;
  text-align: center;
}
.todo-empty .todo-count {
  color: var(--loan-text-muted);
}
.todo-body {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.todo-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--loan-text);
}
.todo-desc {
  font-size: 11px;
  color: var(--loan-text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

@media (max-width: 1100px) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .metric-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

.metric-card {
  display: flex;
  align-items: center;
  gap: 16px;
  /* grid item min-width:0 让卡片可被 grid 列宽约束，避免内容（¥大数字）撑开列 */
  min-width: 0;
}

.metric-icon {
  width: 46px;
  height: 46px;
  border-radius: var(--loan-radius);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.metric-label {
  font-size: 13px;
  color: var(--loan-text-secondary);
}

.metric-body {
  display: flex;
  flex-direction: column;
  min-width: 0; /* 允许 flex 子项压缩，配合 metric-value overflow 截断大数字 */
  flex: 1;
}

.metric-value {
  /* 大金额（¥100,000,000.00）用 clamp 自适应缩放，title 悬浮可看完整值 */
  font-size: clamp(16px, 2vw, 28px);
  font-weight: 700;
  line-height: 1.2;
  margin: 2px 0;
  color: var(--loan-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.metric-foot {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--loan-text-muted);
}

.metric-trend {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-size: 11px;
  font-weight: 600;
  padding: 1px 6px;
  border-radius: 999px;
}

.trend-up {
  color: var(--loan-success);
  background: color-mix(in srgb, var(--loan-success) 10%, transparent);
}

.trend-down {
  color: var(--loan-danger);
  background: color-mix(in srgb, var(--loan-danger) 10%, transparent);
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
}

/* 合作库到期预警 */
.expire-card {
  margin-bottom: 16px;
}
.panel-tip {
  font-size: 11px;
  font-weight: 400;
  color: var(--loan-text-muted);
}
.panel-link {
  margin-left: auto;
  font-size: 12px;
  color: var(--loan-primary);
  text-decoration: none;
  transition: color var(--loan-transition);
}
.panel-link:hover {
  color: var(--loan-primary-hover);
  text-decoration: underline;
}
.expire-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.expire-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid var(--loan-border);
  border-radius: var(--loan-radius-sm);
  background: var(--loan-surface);
  transition: border-color var(--loan-transition);
}
.expire-item:hover {
  border-color: var(--loan-warning);
}
.expire-code {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  font-weight: 500;
  color: var(--loan-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.expire-until {
  font-size: 12px;
  color: var(--loan-text-secondary);
  flex-shrink: 0;
}
.expire-badge {
  flex-shrink: 0;
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 999px;
}
.badge-warning {
  color: var(--loan-warning);
  background: color-mix(in srgb, var(--loan-warning) 12%, transparent);
  border: 1px solid color-mix(in srgb, var(--loan-warning) 28%, transparent);
}
.badge-danger {
  color: var(--loan-danger);
  background: color-mix(in srgb, var(--loan-danger) 12%, transparent);
  border: 1px solid color-mix(in srgb, var(--loan-danger) 28%, transparent);
}

/* 两列：快捷 + 最近 */
.row-2col {
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  gap: 16px;
  margin-bottom: 16px;
}

@media (max-width: 1100px) {
  .row-2col {
    grid-template-columns: minmax(0, 1fr);
  }
}

.quick-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

@media (max-width: 560px) {
  .quick-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

.quick-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  border: 1px solid var(--loan-border);
  border-radius: var(--loan-radius);
  background: var(--loan-card-bg);
  text-decoration: none;
  transition: transform var(--loan-transition), border-color var(--loan-transition),
    box-shadow var(--loan-transition);
}

.quick-icon {
  width: 38px;
  height: 38px;
  border-radius: var(--loan-radius-sm);
  background: var(--loan-primary-soft);
  color: var(--loan-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.quick-body {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.quick-item:hover {
  transform: translateY(-2px);
  border-color: var(--loan-primary);
  box-shadow: var(--loan-shadow-lg);
}

.quick-item:focus-visible {
  outline: 2px solid var(--loan-primary);
  outline-offset: 2px;
}

.quick-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--loan-text);
  margin-bottom: 2px;
}

.quick-desc {
  font-size: 12px;
  color: var(--loan-text-muted);
}

.quick-arrow {
  position: absolute;
  right: 14px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--loan-primary);
  opacity: 0;
  transition: var(--loan-transition);
}

.quick-item:hover .quick-arrow {
  opacity: 1;
  transform: translateY(-50%) translateX(3px);
}

/* 最近匹配 */
.recent-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.recent-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 10px 8px;
  border-radius: var(--loan-radius-sm);
  transition: background var(--loan-transition);
  cursor: pointer;
}

.recent-item:hover {
  background: var(--loan-surface);
}

.recent-item:focus-visible {
  outline: 2px solid var(--loan-primary);
  outline-offset: -2px;
}

.recent-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-top: 6px;
  flex-shrink: 0;
}

.dot-PASS {
  background: var(--loan-success);
}

.dot-CONDITION {
  background: var(--loan-warning);
}

.dot-REJECT {
  background: var(--loan-danger);
}

.recent-body {
  flex: 1;
  min-width: 0;
}

.recent-line {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.recent-product {
  color: var(--loan-text);
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
  min-width: 0;
}

.recent-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 3px;
  font-size: 11px;
  color: var(--loan-text-muted);
}

.recent-trace {
  font-family: "SF Mono", Menlo, Consolas, monospace;
}

.mono {
  font-family: "SF Mono", Menlo, Consolas, monospace;
}

/* 链路 */
.chain-card {
  margin-top: 0;
}

.chain {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.chain-step {
  padding: 6px 14px;
  background: var(--loan-primary-soft);
  color: var(--loan-primary);
  border: 1px solid color-mix(in srgb, var(--loan-primary) 20%, transparent);
  border-radius: var(--loan-radius-sm);
  font-size: 13px;
  font-weight: 500;
}

.chain-arrow {
  color: var(--loan-text-muted);
}

.chain-note {
  margin-top: 14px;
  font-size: 12px;
  color: var(--loan-text-muted);
}
</style>
