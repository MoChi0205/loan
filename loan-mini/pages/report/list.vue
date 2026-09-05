<template>
  <view class="report-page" :class="{ 'u-shell': store.isTablet }">
    <!-- 列表头：角色二分（C3） -->
    <view class="page-head">
      <text class="head-title">{{ isStaff ? '全部报告' : '我的报告' }}</text>
      <text class="head-sub">{{ isStaff ? '全量检索 · 用于陪访与穿透' : '历史智能匹配报告 · 数据仅本人可见' }}</text>
    </view>

    <!-- 筛选区（C3 角色二分 + C11 四维查询）
         客户：仅日期筛选（无搜索框、无归属筛选——客户无权跨用户检索）
         企业员工：手机号/客户姓名 + 公司名/信用代码 + 归属 + 日期 -->
    <view class="filter-card">
      <!-- 员工专属：关键词检索 -->
      <template v-if="isStaff">
        <input
          class="filter-input"
          v-model="filters.query"
          placeholder="手机号 / 客户姓名"
          placeholder-class="ph"
          confirm-type="search"
          @confirm="onSearch"
        />
        <input
          class="filter-input"
          v-model="filters.credit"
          placeholder="公司名称 / 统一社会信用代码"
          placeholder-class="ph"
          confirm-type="search"
          @confirm="onSearch"
        />
        <!-- 归属筛选（C3：顾问默认全量） -->
        <view class="owner-seg">
          <view
            v-for="o in ownerOptions" :key="o.key"
            class="owner-item" :class="{ active: filters.owner === o.key }"
            @click="onOwnerChange(o.key)"
          >{{ o.label }}</view>
        </view>
      </template>

      <!-- 共用：日期区间 -->
      <view class="date-chips">
        <view
          v-for="d in dateOptions" :key="d.key"
          class="date-chip" :class="{ active: filters.dateRange === d.key }"
          @click="onDateChange(d.key)"
        >{{ d.label }}</view>
      </view>

      <view class="filter-actions" v-if="isStaff">
        <AppButton variant="primary" size="sm" @click="onSearch">查询</AppButton>
        <AppButton variant="secondary" size="sm" @click="onReset">重置</AppButton>
      </view>
    </view>

    <!-- 骨架屏 -->
    <AppSkeleton v-if="loading && !reports.length" :rows="3" />

    <!-- 加载失败（与空态分离） -->
    <AppEmpty v-else-if="hasError && !reports.length" title="加载失败"
      desc="网络异常或服务暂不可用，请重试">
      <AppButton variant="primary" size="md" @click="reload()">重试</AppButton>
    </AppEmpty>

    <!-- 空态：区分「从未有过报告」与「筛选无结果」 -->
    <AppEmpty v-else-if="!loading && !reports.length && isFiltered"
      title="无匹配的报告" desc="当前筛选条件下没有找到报告，试试放宽条件或重置筛选">
      <AppButton variant="secondary" size="md" @click="onReset">重置筛选</AppButton>
    </AppEmpty>
    <AppEmpty v-else-if="!loading && !reports.length" title="暂无匹配报告"
      desc="完成智能匹配后，报告将展示在这里">
      <AppButton variant="primary" size="md" @click="goMatch">去匹配</AppButton>
    </AppEmpty>

    <!-- 列表 -->
    <view class="report-list" v-else>
      <!-- 统一用 AppListItem：左侧评级块 + 主内容 + 右侧状态（替代原 .report-item 独立实现） -->
      <AppListItem
        v-for="item in reports" :key="item.reportNo"
        :id="item.reportNo"
        :title="itemTitle(item)"
        tappable
        :aria-label="`报告 ${item.reportNo}，评级 ${gradeLabel(item.grade)}`"
        @click="goDetail(item)"
      >
        <template #leading>
          <view class="grade-block" :class="`gb-${gradeClass(item.grade)}`">{{ gradeLabel(item.grade) }}</view>
        </template>
        <template #meta>
          <text v-if="isStaff && item.ownerStaffName" class="meta-text">归属 {{ item.ownerStaffName }}</text>
          <text v-if="item.contactPhone" class="meta-text">{{ item.contactPhone }}</text>
          <text class="meta-text">可匹配 {{ item.productCount || 0 }} 款</text>
          <text class="meta-text">银行 {{ item.bankCount || 0 }} 家</text>
          <text class="meta-text">{{ formatTime(item.createdAt) }}</text>
        </template>
        <template #trailing>
          <AppTag :type="tagType(deriveTotal(item))" size="sm">{{ totalLabel(deriveTotal(item)) }}</AppTag>
        </template>
      </AppListItem>

      <!-- 加载状态 -->
      <AppLoadMore :loading="loadingMore" :finished="finished" :error="hasError" @load="loadMore" />
    </view>
  </view>

  <!-- 角色化底部导航（自绘 tabBar） -->
  <TabBar current="report" />
</template>

<script setup>
import { ref, reactive, computed } from 'vue';
import { onShow, onReachBottom, onPullDownRefresh } from '@dcloudio/uni-app';
import { useUserStore } from '../../store/user';
import TabBar from '../../components/TabBar.vue';
import { reportList } from '../../api/match';

/**
 * 报告列表（tabBar 页）：C3 角色二分 + C11 四维查询。
 *
 * - 客户（C3）：**仅按日期**筛选自己的报告。无搜索框、无归属筛选——
 *   客户无权跨用户检索他人报告，后端也会忽略这些参数。
 * - 企业员工（C11）：手机号 / 客户姓名 / 公司名称 / 统一社会信用代码 + 归属 + 日期。
 *
 * 列表仅展示报告号、评级、产品数、银行覆盖数等脱敏信息，
 * 命中产品明细在「报告详情」查看（C4）。
 */
const PAGE_SIZE = 10;

/** 企业员工（C1/C3/C4）：可查全量报告与命中产品；客户与渠道除外 */
const STAFF_ROLES = ['adviser', 'deptmgr', 'boss', 'operator', 'super'];
const store = useUserStore();
const isStaff = computed(() => STAFF_ROLES.indexOf(store.role) >= 0);

/** 归属筛选项（仅员工可见） */
const ownerOptions = [
  { key: 'all', label: '全量' },
  { key: 'me', label: '归属到我' },
  { key: 'staff', label: '归属到员工' },
];
/** 日期区间（客户与员工共用） */
const dateOptions = [
  { key: 'today', label: '今日' },
  { key: '7d', label: '7 天' },
  { key: '30d', label: '30 天' },
  { key: 'all', label: '全部' },
];

/** 筛选条件：客户只用 dateRange，其余字段后端会忽略 */
const filters = reactive({
  query: '',
  credit: '',
  owner: (STAFF_ROLES.indexOf(store.role) >= 0 && store.role !== 'adviser') ? 'all' : 'all',
  dateRange: 'all',
});

/** 是否处于筛选态（用于区分「从未有报告」与「筛选无结果」两种空态） */
const isFiltered = computed(
  () => filters.dateRange !== 'all' || !!(isStaff.value && (filters.query || filters.credit || filters.owner !== 'all')),
);

const reports = ref([]);
const page = ref(1);
const finished = ref(false);
const loading = ref(false);
const loadingMore = ref(false);
const hasError = ref(false);

async function fetchList(loadMore = false) {
  if (loading.value || (loadMore && finished.value)) return;
  if (loadMore) {
    loadingMore.value = true;
  } else {
    loading.value = true;
  }
  hasError.value = false;
  try {
    // 客户只传 dateRange；员工传全部维度（后端按角色做权限过滤）
    const params = isStaff.value
      ? filters
      : { dateRange: filters.dateRange };
    const data = await reportList(page.value, PAGE_SIZE, params);
    const records = (data && data.records) || [];
    if (loadMore) {
      reports.value = reports.value.concat(records);
    } else {
      reports.value = records;
    }
    finished.value = records.length < PAGE_SIZE;
  } catch (e) { hasError.value = true; }
  finally {
    loading.value = false;
    loadingMore.value = false;
  }
}

function reload() {
  page.value = 1;
  finished.value = false;
  fetchList(false);
}

/** 查询：回到第一页重新拉取 */
function onSearch() { reload(); }

/** 重置：恢复到角色默认筛选态 */
function onReset() {
  filters.query = '';
  filters.credit = '';
  filters.owner = 'all';
  filters.dateRange = 'all';
  reload();
}

function onOwnerChange(key) {
  if (filters.owner === key) return;
  filters.owner = key;
  reload();
}

function onDateChange(key) {
  if (filters.dateRange === key) return;
  filters.dateRange = key;
  reload();
}

function loadMore() {
  if (finished.value || loading.value || loadingMore.value) return;
  page.value += 1;
  fetchList(true);
}

function goDetail(item) {
  uni.navigateTo({ url: `/pages/report/detail?reportNo=${item.reportNo}` });
}

function goMatch() {
  uni.reLaunch({ url: '/pages/match/match' });
}

/** 报告列表接口未直接返回 totalResult，按各命中数推导三档结果 */
function deriveTotal(item) {
  if (item.totalResult) return item.totalResult;
  if ((item.passCount || 0) > 0) return 'PASS';
  if ((item.conditionCount || 0) > 0) return 'CONDITION';
  if ((item.rejectCount || 0) > 0) return 'REJECT';
  return 'SKIP_SEGMENT_MISMATCH';
}

function totalLabel(t) {
  if (t === 'PASS') return '可进件';
  if (t === 'CONDITION') return '需补料';
  if (t === 'REJECT') return '暂不匹配';
  if (t === 'SKIP_SEGMENT_MISMATCH') return '暂不匹配';
  return t || '未知';
}

function totalClass(t) {
  if (t === 'PASS') return 'pass';
  if (t === 'CONDITION') return 'condition';
  return 'reject';
}

function gradeLabel(g) {
  return { HIGH: '高', MIDDLE: '中', LOW: '低' }[g] || (g || '-');
}

/** 评级块配色：高=品牌深蓝渐变，中=亮蓝渐变，低=灰蓝渐变（均满足白字 4.5:1） */
function gradeClass(g) {
  if (g === 'HIGH') return 'high';
  if (g === 'MIDDLE') return 'middle';
  return 'low';
}

/** AppTag 语义色映射：可进件=绿，需补料=橙，暂不匹配=红 */
function tagType(t) {
  if (t === 'PASS') return 'success';
  if (t === 'CONDITION') return 'warning';
  return 'danger';
}

/**
 * 列表标题：员工视角显示「客户 · 企业名」便于陪访定位；
 * 客户视角只显示中性标题（不暴露他人信息，其本就看的是自己的报告）。
 */
function itemTitle(item) {
  if (!isStaff.value) return '综合匹配报告';
  const name = item.clientName || '';
  const ent = item.entName || '';
  if (name && ent) return `${name} · ${ent}`;
  return name || ent || '综合匹配报告';
}

function formatTime(t) {
  if (!t) return '';
  const s = String(t).replace('T', ' ').replace(/-/g, '/');
  return s.length > 16 ? s.slice(0, 16) : s;
}

onShow(() => {
  uni.setNavigationBarTitle({ title: isStaff.value ? '报告中心' : '我的报告' });
  page.value = 1;
  finished.value = false;
  fetchList(false);
});

onReachBottom(() => {
  loadMore();
});

onPullDownRefresh(async () => {
  page.value = 1;
  finished.value = false;
  await fetchList(false);
  uni.stopPullDownRefresh();
});
</script>

<style scoped>
.report-page {
  min-height: 100vh;
  padding: 0 var(--space-4) calc(var(--space-16) + env(safe-area-inset-bottom));
  background: var(--bg-page);
  box-sizing: border-box;
}

/* ===== 筛选区（C3 角色二分 / C11 四维） ===== */
.filter-card {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  padding: var(--space-4);
  margin-bottom: var(--space-3);
  box-shadow: var(--shadow-md);
}

.filter-input {
  width: 100%;
  background: var(--bg-input);
  border: 2rpx solid transparent;
  border-radius: var(--radius-md);
  padding: 24rpx 28rpx;
  font-size: var(--fs-md);
  color: var(--text-primary);
  min-height: 88rpx; /* 触控 44px */
  box-sizing: border-box;
  margin-bottom: var(--space-3);
}
/* #ifdef H5 */
.filter-input:focus { border-color: var(--gold); background: var(--bg-card); }
/* #endif */
.ph { color: var(--text-placeholder); }

/* 归属分段控件 */
.owner-seg {
  display: flex;
  gap: var(--space-1);
  background: var(--bg-input);
  padding: var(--space-1);
  border-radius: var(--radius-md);
  margin-bottom: var(--space-3);
}
.owner-item {
  flex: 1;
  text-align: center;
  font-size: var(--fs-sm);
  padding: 20rpx 8rpx;
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  min-height: 44rpx;
  line-height: 44rpx;
}
.owner-item.active {
  background: var(--bg-card);
  color: var(--brand-deep);
  font-weight: 700;
  box-shadow: var(--shadow-sm);
}

/* 日期 chip */
.date-chips {
  display: flex;
  gap: var(--space-2);
}
.date-chip {
  flex: 1;
  text-align: center;
  font-size: var(--fs-sm);
  padding: 20rpx 8rpx;
  border-radius: var(--radius-sm);
  background: var(--bg-input);
  color: var(--text-secondary);
  min-height: 44rpx;
  line-height: 44rpx;
}
.date-chip.active {
  background: var(--brand-deep);
  color: var(--text-invert);
  font-weight: 600;
}
.date-chip:active { transform: scale(0.96); }

.filter-actions {
  display: flex;
  gap: var(--space-3);
  margin-top: var(--space-3);
}
.filter-actions view, .filter-actions button { flex: 1; min-width: 0; }

/* ===== 列表项内的评级块与元信息 ===== */
.grade-block {
  width: 92rpx;
  height: 92rpx;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--fs-lg);
  font-weight: 700;
  color: var(--text-invert);
}
.gb-high { background: var(--brand-deep); }
.gb-middle { background: var(--brand-bright); }
.gb-low { background: var(--text-secondary); }

.meta-text { font-size: var(--fs-sm); color: var(--text-secondary); }
.meta-strong { color: var(--brand-deep); font-weight: 600; }

.page-head {
  padding: 40rpx 8rpx 24rpx;
}

.head-title {
  display: block;
  font-size: 42rpx;
  font-weight: 800;
  color: var(--text-primary);
  letter-spacing: 1rpx;
}

.head-sub {
  display: block;
  margin-top: 10rpx;
  font-size: 24rpx;
  color: var(--text-secondary);
}

/* ====== 空态 ====== */
.report-list { display: flex; flex-direction: column; }

</style>
