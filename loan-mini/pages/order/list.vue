<template>
  <view class="order-page" :class="{ 'u-shell': store.isTablet }">
    <view class="page-head">
      <text class="head-title">{{ isStaff ? '工单' : '我的服务单' }}</text>
      <text class="head-sub">
        {{ isStaff ? '四维筛选 · 客户姓名 / 手机号 / 状态 / 时间' : '顾问跟进进度一览 · 仅展示本人服务记录' }}
      </text>
    </view>

    <!-- 筛选区（C7 四维 · UI v2：下拉选代替 chip 块）
         客户：仅状态 + 时间（无客户姓名/手机号——客户无权检索他人工单）
         企业员工：客户姓名 + 手机号 + 状态 + 时间 -->
    <view class="filter-card">
      <template v-if="isStaff">
        <input
          class="filter-input"
          v-model="filters.clientName"
          placeholder="客户姓名"
          placeholder-class="ph"
          confirm-type="search"
          @confirm="onSearch"
        />
        <input
          class="filter-input"
          v-model="filters.phone"
          type="number"
          placeholder="手机号"
          placeholder-class="ph"
          confirm-type="search"
          @confirm="onSearch"
        />
      </template>

      <!-- 工单状态：下拉选 -->
      <view class="filter-row">
        <text class="filter-label">工单状态</text>
        <picker mode="selector" :range="statusOptions" range-key="label" :value="statusIndex" @change="onStatusPickerChange">
          <view class="picker-trigger">
            <text class="picker-value">{{ currentStatusLabel }}</text>
            <text class="picker-arrow">▾</text>
          </view>
        </picker>
      </view>

      <!-- 时间区间：下拉选 -->
      <view class="filter-row">
        <text class="filter-label">时间区间</text>
        <picker mode="selector" :range="dateOptions" range-key="label" :value="dateIndex" @change="onDatePickerChange">
          <view class="picker-trigger">
            <text class="picker-value">{{ currentDateLabel }}</text>
            <text class="picker-arrow">▾</text>
          </view>
        </picker>
      </view>

      <view class="filter-actions">
        <AppButton variant="primary" size="sm" @click="onSearch">查询</AppButton>
        <AppButton variant="secondary" size="sm" @click="onReset">重置</AppButton>
      </view>
    </view>

    <!-- 骨架屏（P1-5 Loading 态） -->
    <AppSkeleton v-if="loading && !orders.length" :rows="3" />

    <!-- 加载失败（与空态分离） -->
    <AppEmpty v-else-if="hasError && !orders.length" title="加载失败"
      desc="网络异常或服务暂不可用，请重试">
      <AppButton variant="primary" size="md" @click="reload()">重试</AppButton>
    </AppEmpty>

    <!-- 空态：区分「从未有工单」与「筛选无结果」 -->
    <AppEmpty v-else-if="!loading && !orders.length && isFiltered"
      title="无匹配的工单" desc="当前筛选条件下没有找到工单，试试放宽条件或重置筛选">
      <AppButton variant="secondary" size="md" @click="onReset">重置筛选</AppButton>
    </AppEmpty>
    <AppEmpty v-else-if="!loading && !orders.length" title="暂无服务单"
      desc="完成匹配并有意向后，顾问会为您创建服务单" />

    <!-- 列表：统一用 AppListItem -->
    <view v-else class="order-list">
      <AppListItem
        v-for="item in orders" :key="item.orderNo"
        :id="item.orderNo"
        :title="itemTitle(item)"
        tappable
        :aria-label="`服务单 ${item.orderNo}，状态 ${statusLabel(item.status)}`"
        @click="goDetail(item)"
      >
        <template #leading>
          <view class="status-ic" :class="`si-${statusTone(item.status)}`">
            <AppIcon name="order" />
          </view>
        </template>
        <template #meta>
          <text v-if="item.clientName" class="meta-text">{{ item.clientName }}</text>
          <text v-if="item.phone" class="meta-text">{{ item.phone }}</text>
          <text v-if="isStaff && item.ownerStaffName" class="meta-text">归属 {{ item.ownerStaffName }}</text>
          <text v-if="item.dealAmount != null && item.dealAmount !== ''" class="meta-text">
            额度 ¥ {{ formatAmount(item.dealAmount) }}
          </text>
          <text class="meta-text">{{ formatTime(item.createdAt) }}</text>
        </template>
        <template #trailing>
          <AppTag :type="tagType(item.status)" size="sm">{{ statusLabel(item.status) }}</AppTag>
        </template>
      </AppListItem>

      <AppLoadMore :loading="loadingMore" :finished="finished" :error="hasError" @load="loadMore" />
    </view>
  </view>

  <!-- 角色化底部导航（自绘 tabBar） -->
  <TabBar current="order" />
</template>

<script setup>
/**
 * 服务单 / 工单列表（C7 四维筛选）。
 *
 * - 客户：仅状态 + 时间（无客户姓名/手机号——客户无权检索他人工单，后端亦会忽略）
 * - 企业员工：客户姓名 + 手机号 + 状态 + 时间区间
 *
 * 日期区间在后端按绝对时间戳（created_at）计算，不由前端传相对天数，
 * 避免「18h 被判定为今日」这类相对时间误判。
 *
 * 无障碍（P1-3/P1-4）：筛选项 min-height 88rpx（44px）；
 * 列表项由 AppListItem 统一处理 role / tabindex / aria-label。
 */
import { ref, reactive, computed } from 'vue';
import { onShow, onReachBottom, onPullDownRefresh } from '@dcloudio/uni-app';
import { useUserStore } from '../../store/user';
import TabBar from '../../components/TabBar.vue';
import { orderList } from '../../api/order';

/** 企业员工（C7）：可查全量工单 */
const STAFF_ROLES = ['adviser', 'deptmgr', 'boss', 'operator', 'super'];
const store = useUserStore();
const isStaff = computed(() => STAFF_ROLES.indexOf(store.role) >= 0);

/** 工单状态：与后端 ServiceOrder 状态真值一致。 */
const statusOptions = [
  { key: 'all', label: '全部' },
  { key: 'NEW', label: '新建' },
  { key: 'IN_SERVICE', label: '服务中' },
  { key: 'DEAL', label: '已成交' },
  { key: 'CANCEL', label: '已取消' },
  { key: 'REFUND', label: '已退款' },
];

/** 日期区间 */
const dateOptions = [
  { key: 'today', label: '今日' },
  { key: '7d', label: '7 天' },
  { key: '30d', label: '30 天' },
  { key: 'all', label: '全部' },
];

/** 筛选条件 */
const filters = reactive({
  status: 'all',
  clientName: '',
  phone: '',
  dateRange: 'all',
});

/** 是否处于筛选态（区分两种空态） */
const isFiltered = computed(
  () => filters.status !== 'all' || filters.dateRange !== 'all'
    || !!(isStaff.value && (filters.clientName || filters.phone)),
);

const orders = ref([]);
const page = ref(1);
const finished = ref(false);
const loading = ref(false);
const hasError = ref(false);
const loadingMore = ref(false);

async function fetchList(loadMore = false) {
  if (loading.value || (loadMore && finished.value)) return;
  if (loadMore) {
    loadingMore.value = true;
  } else {
    loading.value = true;
  }
  hasError.value = false;
  try {
    // 客户只传 status/dateRange；员工传全部维度（后端按角色做权限过滤）
    const params = {
      status: filters.status,
      dateRange: filters.dateRange,
      page: page.value,
      size: 10,
      ...(isStaff.value
        ? { clientName: filters.clientName, phone: filters.phone }
        : {}),
    };
    const data = await orderList(params);
    const records = (data && data.records) || [];
    if (loadMore) {
      orders.value = orders.value.concat(records);
    } else {
      orders.value = records;
    }
    finished.value = records.length < 10;
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

function onSearch() { reload(); }

function onReset() {
  filters.status = 'all';
  filters.clientName = '';
  filters.phone = '';
  filters.dateRange = 'all';
  reload();
}

function onStatusChange(key) {
  if (filters.status === key) return;
  filters.status = key;
  reload();
}

function onDateChange(key) {
  if (filters.dateRange === key) return;
  filters.dateRange = key;
  reload();
}

/** UI v2：picker 触发（uni-app picker change 返回选中的索引） */
function onStatusPickerChange(e) {
  const idx = Number(e.detail.value);
  const item = statusOptions[idx];
  if (item) onStatusChange(item.key);
}

function onDatePickerChange(e) {
  const idx = Number(e.detail.value);
  const item = dateOptions[idx];
  if (item) onDateChange(item.key);
}

/** 当前选中的状态/时间索引与显示文本（picker 需要 index，模板需要 label） */
const statusIndex = computed(() => statusOptions.findIndex(s => s.key === filters.status));
const dateIndex = computed(() => dateOptions.findIndex(d => d.key === filters.dateRange));
const currentStatusLabel = computed(() => (statusOptions[statusIndex.value] && statusOptions[statusIndex.value].label) || '');
const currentDateLabel = computed(() => (dateOptions[dateIndex.value] && dateOptions[dateIndex.value].label) || '');

function loadMore() {
  if (finished.value || loading.value || loadingMore.value) return;
  page.value += 1;
  fetchList(true);
}

function goDetail(item) {
  uni.navigateTo({ url: `/pages/order/detail?orderNo=${item.orderNo}` });
}

/** 列表标题：员工显示客户名便于定位，客户显示服务类型 */
function itemTitle(item) {
  if (isStaff.value && item.clientName) return `${item.serviceType || '服务单'} · ${item.clientName}`;
  return item.serviceType || '服务单';
}

function statusLabel(s) {
  return {
    NEW: '新建', IN_SERVICE: '服务中', DEAL: '已成交', CANCEL: '已取消', REFUND: '已退款',
  }[s] || (s || '未知');
}

/** AppTag 语义色映射 */
function tagType(s) {
  if (s === 'DEAL') return 'success';
  if (s === 'NEW') return 'warning';
  if (s === 'IN_SERVICE') return 'info';
  if (s === 'CANCEL' || s === 'REFUND') return 'danger';
  return 'muted';
}

/** 状态图标底色（沿用 tag 语义，用浅底 + 深字保证对比度） */
function statusTone(s) {
  if (s === 'DEAL') return 'success';
  if (s === 'NEW') return 'warning';
  if (s === 'IN_SERVICE') return 'info';
  if (s === 'CANCEL' || s === 'REFUND') return 'danger';
  return 'muted';
}

function formatAmount(v) {
  if (v == null || v === '') return '—';
  const n = Number(v);
  if (Number.isNaN(n)) return String(v);
  return n.toLocaleString('zh-CN');
}

function formatTime(t) {
  if (!t) return '';
  const s = String(t).replace('T', ' ').replace(/-/g, '/');
  return s.length > 16 ? s.slice(0, 16) : s;
}

onShow(() => {
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
.order-page {
  min-height: 100vh;
  padding: 0 var(--space-4) calc(var(--space-16) + env(safe-area-inset-bottom));
  background: var(--bg-page);
  box-sizing: border-box;
}

/* ===== 筛选区（C7） ===== */
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
  min-height: 88rpx; /* 44px 触控 */
  box-sizing: border-box;
  margin-bottom: var(--space-3);
}
/* #ifdef H5 */
.filter-input:focus { border-color: var(--gold); background: var(--bg-card); }
/* #endif */
.ph { color: var(--text-placeholder); }

/* UI v2：下拉选行（标准做法，更省屏更专业） */
.filter-row {
  display: flex; align-items: center; justify-content: space-between;
  min-height: 96rpx; padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-sm); background: var(--bg-input);
  margin-bottom: var(--space-2);
}
.filter-label {
  font-size: var(--fs-sm); color: var(--text-secondary);
}
.picker-trigger {
  display: flex; align-items: center; gap: var(--space-1);
  flex: 1; justify-content: flex-end;
}
.picker-value {
  font-size: var(--fs-md); color: var(--text-primary); font-weight: 600;
  text-align: right;
}
.picker-arrow {
  font-size: var(--fs-sm); color: var(--text-placeholder);
  margin-left: 4rpx;
}
.date-chip:active { transform: scale(0.96); }

.filter-actions { display: flex; gap: var(--space-3); margin-top: var(--space-3); }
.filter-actions view, .filter-actions button { flex: 1; min-width: 0; }

/* ===== 列表项 ===== */
.status-ic {
  width: 92rpx; height: 92rpx;
  border-radius: var(--radius-md);
  display: flex; align-items: center; justify-content: center;
}
.si-success { background: rgba(16, 185, 129, .14); color: var(--success-text); }
.si-warning { background: rgba(245, 158, 11, .16); color: var(--warning-text); }
.si-info { background: rgba(6, 182, 212, .14); color: var(--info-text); }
.si-danger { background: rgba(239, 68, 68, .14); color: var(--danger-text); }
.si-muted { background: var(--bg-input); color: var(--text-secondary); }

.meta-text { font-size: var(--fs-sm); color: var(--text-secondary); }
.meta-strong { color: var(--brand-deep); font-weight: 600; }

</style>
