<template>
  <div class="workbench">
    <div class="loan-page-header workbench-hero">
      <div>
        <h2 class="loan-page-title">{{ welcomeText }}</h2>
        <p class="loan-page-subtitle">{{ workbenchSubtitle }}</p>
      </div>
      <div class="header-meta">
        <el-tooltip :content="nowText" placement="bottom">
          <span class="meta-time">
            <AppIcon name="clock" :size="14" />
            {{ nowText }}
          </span>
        </el-tooltip>
      </div>
    </div>

    <!-- 今日服务：直接读取服务台聚合接口，统计口径与服务台列表完全一致。 -->
    <section v-if="!isChannel && visibleServiceStats.length" class="service-overview loan-card">
      <div class="section-head">
        <div>
          <h3 class="panel-title panel-title--plain">今日服务待办</h3>
          <p>预约、来访、外出和跟进按当前角色数据范围实时统计</p>
        </div>
        <router-link to="/service-operations/daily" class="panel-link">进入今日服务台 →</router-link>
      </div>
      <div class="service-stat-grid" v-loading="serviceLoading">
        <router-link
          v-for="item in visibleServiceStats"
          :key="item.key"
          :to="{ path: item.path, query: item.query }"
          class="service-stat"
          :class="`service-stat--${item.tone}`"
        >
          <span class="service-stat__icon"><AppIcon :name="item.icon" :size="19" /></span>
          <span class="service-stat__body">
            <strong class="mono">{{ item.value }}</strong>
            <span>{{ item.label }}</span>
            <small>{{ item.hint }}</small>
          </span>
          <AppIcon name="arrowRight" :size="14" class="service-stat__arrow" />
        </router-link>
      </div>
    </section>

    <!-- 指标卡（含趋势） -->
    <div v-if="!isChannel" class="metric-grid">
      <div v-for="m in metrics" :key="m.label" class="metric-card loan-card loan-card-hover">
        <div class="metric-icon" :style="{ color: m.color, background: m.bg }">
          <AppIcon :name="m.icon" :size="22" />
        </div>
        <div class="metric-body">
          <div class="metric-label">{{ m.label }}</div>
          <div class="metric-value" :title="m.value">{{ m.value }}</div>
          <div class="metric-foot">
            <span class="metric-foot__text">{{ m.foot }}</span>
            <span v-if="m.trend" class="metric-trend" :class="['trend-' + m.trend.dir]">
              <AppIcon :name="m.trend.dir === 'up' ? 'arrowUp' : 'arrowDown'" :size="10" />
              {{ m.trend.text }}
            </span>
          </div>
        </div>
      </div>
    </div>

    <!-- 待办事项 -->
    <div v-if="!isChannel && visibleTodos.length" class="loan-card todo-card">
      <h3 class="panel-title">待办事项</h3>
      <div class="todo-grid">
        <router-link v-for="t in visibleTodos" :key="t.path + t.name" :to="t.path" class="todo-item" :class="{ 'todo-empty': t.count === 0 }">
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
    <div v-if="!isChannel && canManagePartner" class="loan-card expire-card">
      <h3 class="panel-title">
        合作库到期预警
        <span v-if="expiring.length" class="panel-tip">{{ expiring.length }} 个产品即将到期</span>
        <router-link to="/product/cooperate" class="panel-link">前往合作库 →</router-link>
      </h3>
      <div v-if="expiring.length" class="expire-list">
        <div v-for="p in expiring" :key="p.bankProductCode" class="expire-item" :title="p.productName || '未命名产品'">
          <span class="expire-code">{{ p.productName || '未命名产品' }}</span>
          <span class="expire-until">{{ formatPartnerUntil(p.cooperateUntil) }}</span>
          <span class="expire-badge" :class="remainClass(p)">{{ remainText(p) }}</span>
        </div>
      </div>
      <AppEmpty v-else title="暂无即将到期的合作产品" :minHeight="'120px'" />
    </div>

    <!-- 快捷入口 + 最近活动 -->
    <div v-if="!isChannel && (visibleQuick.length || canViewAudit)" class="row-2col">
      <div class="loan-card quick-card">
        <h3 class="panel-title">快捷操作</h3>
        <div class="quick-grid">
          <router-link v-for="q in visibleQuick" :key="q.path" :to="q.path" class="quick-item">
            <span class="quick-icon"><AppIcon :name="q.icon" :size="20" /></span>
            <span class="quick-body">
              <span class="quick-name">{{ q.name }}</span>
              <span class="quick-desc">{{ q.desc }}</span>
            </span>
            <span class="quick-arrow" aria-hidden="true">→</span>
          </router-link>
        </div>
      </div>

      <div v-if="canViewAudit" class="loan-card recent-card">
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
            title="查看审计详情"
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
                <span>查看执行详情</span>
              </div>
            </div>
          </li>
        </ul>
      </div>
    </div>

    <div v-if="isChannel" class="loan-card quick-card channel-workspace">
      <h3 class="panel-title">渠道工作区</h3>
      <div class="quick-grid">
        <router-link v-for="q in channelQuick" :key="q.path" :to="q.path" class="quick-item">
          <span class="quick-icon"><AppIcon :name="q.icon" :size="20" /></span>
          <span class="quick-body"><span class="quick-name">{{ q.name }}</span><span class="quick-desc">{{ q.desc }}</span></span>
          <span class="quick-arrow" aria-hidden="true">→</span>
        </router-link>
      </div>
    </div>

    <!-- 主链路 -->
    <div v-if="!isChannel" class="loan-card chain-card">
      <h3 class="panel-title">核心主链路</h3>
      <div class="chain">
        <template v-for="(s, i) in chain" :key="s">
          <span class="chain-step">{{ s }}</span>
          <span v-if="i < chain.length - 1" class="chain-arrow" aria-hidden="true">→</span>
        </template>
      </div>
      <p class="chain-note">从产品配置、客户匹配到服务工单与审计留痕，各环节数据贯通并按角色权限协同处理。</p>
    </div>
  </div>
</template>

<script setup>
defineOptions({ name: '_workbench' });
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';
import DictTag from '@/components/DictTag.vue';
import AppEmpty from '@/components/AppEmpty.vue';
import AppIcon from '@/components/AppIcon.vue';
import { pageAudit } from '@/api/audit';
import { dashboardTodo, configStatus } from '@/api/dashboard';
import { reportOverview } from '@/api/report';
import { pagePartnerProducts } from '@/api/partnerProduct';
import { getDailyServiceLists, pageAppointments } from '@/api/serviceOperations';
import { formatDateTime } from '@/utils/format';
import { useUserStore } from '@/store/user';

const userStore = useUserStore();
const isChannel = computed(() => userStore.roleCode === 'CHANNEL');
const roleCode = computed(() => userStore.roleCode || '');
const allowedPaths = ref(new Set(['/workbench']));
const canViewProduct = computed(() => allowedPaths.value.has('/product/all') || allowedPaths.value.has('/product'));
const canViewAudit = computed(() => allowedPaths.value.has('/audit'));
const canManagePartner = computed(() => ['BOSS', 'SUPER_ADMIN', 'SUPER'].includes(roleCode.value));
const workbenchSubtitle = computed(() => ({
  ADVISER: '聚焦我的客户、线索跟进、服务工单与本人审批申请',
  DEPT_MANAGER: '掌握团队客户分配、团队待办与服务进度',
  OPERATOR: '处理运营审批、服务工单与经营协同事项',
  BOSS: '查看全司经营数据并处理关键审批事项',
  SUPER_ADMIN: '查看全司业务运行、审批与系统治理事项',
  SUPER: '查看全司业务运行、审批与系统治理事项',
  CHANNEL: '管理本人录入的线索、客户、产品与分析报告',
}[roleCode.value] || '客户、工单与审批的统一业务工作台'));
const welcomeText = computed(() => `${userStore.displayName || '你好'}，今天从这里开始`);

const nowText = ref('');
const status = ref({});
const todo = ref({});
const overview = ref({});
const dailyService = ref({});
const appointmentTotal = ref(0);
const serviceLoading = ref(false);
let timer = null;
function refreshNow() {
  const d = new Date();
  const pad = (n) => String(n).padStart(2, '0');
  const w = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()];
  nowText.value = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} 周${w} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}
onMounted(async () => {
  refreshNow();
  timer = setInterval(refreshNow, 30000);
  if (!isChannel.value) {
    await loadMenuAccess();
    loadRecent();
    loadStats();
    // 合作库仅老板/超管可查看和操作；普通员工虽可查看全量产品库，不得请求合作库接口。
    if (canManagePartner.value) loadExpiring();
  }
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
  if (allowedPaths.value.has('/service-operations/daily') || allowedPaths.value.has('/service-operations')) {
    await loadDailyService();
  }
}

function pageTotal(page) {
  return Number(page?.total || 0);
}

function todayParam() {
  const d = new Date();
  const pad = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/** 今日服务统计与服务台使用同一聚合接口，避免首页和列表出现不同口径。 */
async function loadDailyService() {
  serviceLoading.value = true;
  try {
    const [daily, appointments] = await Promise.all([
      getDailyServiceLists({ page: 1, size: 1 }),
      pageAppointments({ date: todayParam(), page: 1, size: 1 }),
    ]);
    dailyService.value = daily.data || {};
    appointmentTotal.value = pageTotal(appointments.data);
  } catch (e) {
    dailyService.value = {};
    appointmentTotal.value = 0;
  } finally {
    serviceLoading.value = false;
  }
}

const serviceStats = computed(() => {
  const d = dailyService.value || {};
  const date = todayParam();
  return [
    { key: 'appointment', label: '今日预约', value: appointmentTotal.value, hint: '点击查看客户、时间与地点', icon: 'clock', tone: 'primary', path: '/service-operations/appointments', query: { date } },
    { key: 'visit', label: '客户到访我司', value: pageTotal(d.companyVisits), hint: '点击查看来访客户与安排', icon: 'client', tone: 'success', path: '/service-operations/appointments', query: { date, serviceMethod: 'COMPANY_ON_SITE' } },
    { key: 'outing', label: '员工上门外出', value: pageTotal(d.staffOutings), hint: '点击查看员工、客户与打卡', icon: 'lead', tone: 'warning', path: '/service-operations/outings', query: { date } },
    { key: 'follow', label: '今日待回访', value: pageTotal(d.pendingFollows), hint: '点击查看客户与回访时间', icon: 'clock', tone: 'info', path: '/service-operations/daily', query: { date, focus: 'pendingFollows' } },
    { key: 'order', label: '活跃工单', value: pageTotal(d.activeOrders), hint: '点击查看客户与工单进度', icon: 'order', tone: 'accent', path: '/service-operations/daily', query: { date, focus: 'activeOrders' } },
  ];
});
const visibleServiceStats = computed(() => serviceStats.value.filter((item) => {
  const path = item.path.split('?')[0];
  return allowedPaths.value.has(path) || (path.startsWith('/service-operations/') && allowedPaths.value.has('/service-operations'));
}));

/** 指标卡（真实数据：配置完成度 + 经营总览） */
const metrics = computed(() => {
  const c = status.value || {};
  const o = overview.value || {};
  if (roleCode.value === 'ADVISER') return [
    metric('我的客户', o.clientCount, '当前归属客户', 'client', 'var(--loan-info)'),
    metric('我的线索', o.leadCount, '本人范围线索', 'lead', 'var(--loan-primary)'),
    metric('服务工单', o.orderCount, '本人负责工单', 'order', 'var(--loan-accent)'),
    metric('成交金额', '¥' + fmtAmount(o.dealAmountSum), `${o.dealOrderCount ?? 0} 单已成交`, 'money', 'var(--loan-success)'),
  ];
  if (roleCode.value === 'DEPT_MANAGER') return [
    metric('团队客户', o.clientCount, '本团队数据范围', 'client', 'var(--loan-info)'),
    metric('团队线索', o.leadCount, '本团队线索', 'lead', 'var(--loan-primary)'),
    metric('团队工单', o.orderCount, '本团队服务工单', 'order', 'var(--loan-accent)'),
    metric('团队成交', '¥' + fmtAmount(o.dealAmountSum), `${o.dealOrderCount ?? 0} 单已成交`, 'money', 'var(--loan-success)'),
  ];
  return [
    {
      label: '合作银行',
      value: String(c.channelCount ?? '-'),
      foot: '家渠道已接入',
      color: 'var(--loan-info)',
      bg: 'color-mix(in srgb, var(--loan-info) 10%, transparent)',
      icon: 'channel',
    },
    {
      label: '产品库',
      value: String(c.productCount ?? '-'),
      foot: '个产品（含审核）',
      color: 'var(--loan-primary)',
      bg: 'var(--loan-primary-soft)',
      icon: 'product',
    },
    {
      label: '成交金额',
      value: '¥' + fmtAmount(o.dealAmountSum),
      foot: `${o.dealOrderCount ?? 0} 单已成交`,
      color: 'var(--loan-success)',
      bg: 'color-mix(in srgb, var(--loan-success) 10%, transparent)',
      icon: 'money',
    },
    {
      label: '奖励发放',
      value: '¥' + fmtAmount(o.rewardAmountSum),
      foot: `${o.rewardCount ?? 0} 单奖励`,
      color: 'var(--loan-accent)',
      bg: 'var(--loan-accent-soft)',
      icon: 'reward',
    },
  ];
});
function metric(label, value, foot, icon, color) {
  return { label, value: String(value ?? '-'), foot, icon, color, bg: `color-mix(in srgb, ${color} 10%, transparent)` };
}

/** 待办事项（真实统计） */
const todos = computed(() => {
  const t = todo.value || {};
  const mine = [
    { name: '我的外出申请', count: t.myOutingApply ?? 0, desc: '待审批的本人外出申请', path: '/approval/mine' },
    { name: '我的认领申请', count: t.myAllocationApply ?? 0, desc: '待处理的客户认领/转移', path: '/approval/mine' },
    { name: '我的下载申请', count: t.myDownloadApply ?? 0, desc: '待处理的资料下载申请', path: '/approval/mine' },
    { name: '我的工单', count: t.myOrderCount ?? 0, desc: '服务中的客户工单', path: '/order' },
    { name: '我的线索', count: t.myLeadCount ?? 0, desc: '当前归属我的线索', path: '/lead/my' },
  ];
  if (roleCode.value === 'ADVISER') return mine;
  const allocation = { name: roleCode.value === 'DEPT_MANAGER' ? '团队认领待审批' : '客户认领待审批', count: t.pendingAllocationApproval ?? 0, desc: roleCode.value === 'DEPT_MANAGER' ? '本团队客户归属流转' : '客户归属流转审核', path: '/approval/allocation' };
  const outing = { name: roleCode.value === 'DEPT_MANAGER' ? '团队外出待审批' : '外出待审批', count: t.pendingOutingApproval ?? 0, desc: roleCode.value === 'DEPT_MANAGER' ? '本团队员工外出申请' : '权限范围内的员工外出申请', path: '/approval/outing' };
  if (roleCode.value === 'DEPT_MANAGER') return [outing, allocation, ...mine];
  if (roleCode.value === 'OPERATOR') return [
    outing, { name: '下载待审批', count: t.pendingDownloadApproval ?? 0, desc: '无水印资料下载审核', path: '/approval/download' }, allocation,
    { name: '奖励待处理', count: t.pendingReward ?? 0, desc: '成交奖励审核与发放', path: '/reward/records' }, ...mine.slice(2),
  ];
  return [
    outing,
    { name: '产品待审批', count: t.pendingProductApproval ?? 0, desc: '新增产品发布审核', path: '/approval/product' },
    { name: '下载待审批', count: t.pendingDownloadApproval ?? 0, desc: '无水印资料下载审核', path: '/approval/download' },
    allocation,
    { name: '奖励待处理', count: t.pendingReward ?? 0, desc: '成交奖励审核与发放', path: '/reward/records' },
  ];
});
const visibleTodos = computed(() => todos.value.filter((item) => allowedPaths.value.has(item.path.split('?')[0])));

function fmtAmount(v) {
  return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

const quick = [
  {
    path: '/product/all',
    name: '产品库',
    desc: '全量库 / 合作库双层管理',
    icon: 'product',
  },
  {
    path: '/rule',
    name: '规则集',
    desc: '企业 16 条准入规则四分类',
    icon: 'rule',
  },
  {
    path: '/audit',
    name: '审计日志',
    desc: '匹配全链路执行追踪',
    icon: 'audit',
  },
  {
    path: '/config-wizard',
    name: '系统配置',
    desc: '首次上线基础配置一键完成',
    icon: 'config',
  },
];
const visibleQuick = computed(() => quick.filter((item) => allowedPaths.value.has(item.path)));

async function loadMenuAccess() {
  try {
    const paths = await userStore.ensureMenuPaths();
    allowedPaths.value = new Set(['/workbench', ...paths]);
  } catch (e) {
    allowedPaths.value = new Set(['/workbench']);
  }
}

const channelQuick = [
  { path: '/lead/my', name: '我的线索', desc: '录入并查看本人提交的客户线索', icon: 'lead' },
  { path: '/client/my', name: '我的客户', desc: '查看本人线索形成的客户档案与归属', icon: 'client' },
  { path: '/product/all', name: '我的产品', desc: '录入产品并跟踪平台审核进度', icon: 'product' },
  { path: '/report/screening', name: '客户分析报告', desc: '查看本人客户的分析结果', icon: 'reportDoc' },
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
  if (!canViewAudit.value) return;
  try {
    const res = await pageAudit({ page: 1, size: 5 });
    recentMatches.value = (res.data?.records || []).map((t) => ({
      id: t.id,
      product: t.enterpriseName || t.contactName || '客户匹配记录',
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
.workbench {
  width: 100%;
  max-width: 1600px;
  margin: 0 auto;
}
.workbench-hero {
  position: relative;
  overflow: hidden;
  margin-bottom: 14px;
  padding: 15px 20px;
  border: 1px solid color-mix(in srgb, var(--loan-primary) 18%, var(--loan-border));
  border-radius: 12px;
  background:
    radial-gradient(circle at 88% 20%, color-mix(in srgb, var(--loan-accent) 18%, transparent) 0, transparent 32%),
    linear-gradient(135deg, color-mix(in srgb, var(--loan-primary) 10%, var(--loan-card-bg)) 0%, var(--loan-card-bg) 72%);
}
.service-overview {
  margin-bottom: 20px;
  padding: 18px 20px 20px;
}
.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 14px;
}
.section-head p {
  margin: 4px 0 0;
  color: var(--loan-text-muted);
  font-size: 12px;
}
.panel-title--plain {
  margin: 0;
  padding: 0;
  border: 0;
  font-size: 16px;
}
.service-stat-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
}
.service-stat {
  position: relative;
  display: flex;
  align-items: center;
  gap: 11px;
  min-width: 0;
  padding: 14px 12px;
  color: var(--loan-text);
  background: var(--loan-surface);
  border: 1px solid var(--loan-border);
  border-radius: 12px;
  text-decoration: none;
  transition: transform var(--loan-transition), border-color var(--loan-transition), box-shadow var(--loan-transition);
}
.service-stat:hover {
  transform: translateY(-2px);
  border-color: var(--stat-color, var(--loan-primary));
  box-shadow: 0 10px 24px color-mix(in srgb, var(--stat-color, var(--loan-primary)) 10%, transparent);
}
.service-stat--primary { --stat-color: var(--loan-primary); }
.service-stat--success { --stat-color: var(--loan-success); }
.service-stat--warning { --stat-color: var(--loan-warning); }
.service-stat--info { --stat-color: var(--loan-info); }
.service-stat--accent { --stat-color: var(--loan-accent); }
.service-stat__icon {
  display: grid;
  place-items: center;
  flex: 0 0 38px;
  height: 38px;
  color: var(--stat-color);
  background: color-mix(in srgb, var(--stat-color) 11%, transparent);
  border-radius: 10px;
}
.service-stat__body { min-width: 0; }
.service-stat__body strong { display: block; color: var(--loan-text); font-size: 21px; line-height: 1; }
.service-stat__body span { display: block; margin-top: 5px; font-size: 12px; font-weight: 600; white-space: nowrap; }
.service-stat__body small { display: block; margin-top: 2px; color: var(--loan-text-muted); font-size: 10px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.service-stat__arrow { margin-left: auto; color: var(--loan-text-muted); opacity: 0; transition: opacity var(--loan-transition), transform var(--loan-transition); }
.service-stat:hover .service-stat__arrow { opacity: 1; transform: translateX(2px); }
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
  .service-stat-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .workbench-hero { padding: 18px; }
  .service-stat-grid { grid-template-columns: 1fr; }
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
