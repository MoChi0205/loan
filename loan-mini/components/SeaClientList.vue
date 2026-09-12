<template>
  <view class="scl theme-root" :data-theme="themeMode">
    <view class="tabs">
      <view
        v-for="s in SEA_TABS"
        :key="s.key"
        class="tab"
        :class="{ on: sea === s.key }"
        @click="onSwitchSea(s.key)"
      >{{ s.label }}</view>
    </view>

    <AppSearchBar v-model="keyword" inputable show-button placeholder="搜索企业名称" @search="onSearch" />

    <AppSkeleton v-if="loading && !rows.length" :rows="4" />
    <AppEmpty v-else-if="hasError && !rows.length" title="加载失败" desc="网络异常，请重试">
      <AppButton variant="primary" size="md" @click="reload">重试</AppButton>
    </AppEmpty>
    <AppEmpty v-else-if="!loading && !rows.length" title="公海暂无客户" :desc="emptyDesc" />

    <view v-else class="list">
      <view class="count">共 <text class="num">{{ total }}</text> 位 · {{ sea === 'TEAM' ? '仅本部门可认领' : '全员可认领' }}</view>
      <view v-for="(c, i) in rows" :key="c.clientCode || i" class="row" :class="{ first: i === 0 }">
        <view class="ava">{{ firstChar(c) }}</view>
        <view class="main">
          <text class="t1">{{ nameOf(c) }}</text>
          <text class="t2">{{ subOf(c) }}</text>
        </view>
        <AppButton
          variant="secondary"
          size="sm"
          :loading="claiming === c.clientCode"
          @click="onClaim(c)"
        >认领</AppButton>
      </view>
      <AppLoadMore :loading="loadingMore" :finished="finished" :error="hasError" @load="loadMore" />
    </view>
  </view>
</template>

<script setup>
/**
 * 客户公海列表（15-客户公海团队客户与分配回收规则 §11/§12/§17）。
 *
 * <p><b>范围：</b>公司公海 {@code ENTERPRISE} 全员可见、全员可认领；
 * 团队公海 {@code TEAM} 仅本部门可见、仅本部门成员可认领（后端按 deptCode 隔离，fail-closed）。
 *
 * <p><b>认领：</b>复用既有 {@code POST /api/mini/client/{clientCode}/claim} 原子落归属
 * —— 公司公海 / 本团队公海直接认领，已归属他人转分配审批；并发仅一人成功。
 * 冷却期拦截由后端负责，前端不重复判定。
 *
 * <p><b>合规（D68 / 02-红线 #7）：</b>列表只展示企业名 / 联系人 / 状态，不展示任何业务单号。
 *
 * <p>作为组件运行：上拉加载 / 下拉刷新由容器页转发（defineExpose）。
 */
import { ref, computed, onMounted, watch } from 'vue';
import AppSearchBar from './AppSearchBar.vue';
import AppSkeleton from './AppSkeleton.vue';
import AppEmpty from './AppEmpty.vue';
import AppButton from './AppButton.vue';
import AppLoadMore from './AppLoadMore.vue';
import { seaClients, claimClient } from '../api/client';
import { useThemeMode } from '../theme';

/** 容器传入：当前子页签是否激活 */
const props = defineProps({
  active: { type: Boolean, default: true },
});

/** 认领结果常量（与后端 MiniClientService 保持一致） */
const CLAIM_AUTO = 'AUTO_CLAIMED';
const CLAIM_PENDING = 'PENDING_APPROVAL';

const SEA_TABS = [
  { key: 'ENTERPRISE', label: '公司公海' },
  { key: 'TEAM', label: '团队公海' },
];

const PAGE_SIZE = 10;

const themeMode = useThemeMode();

const sea = ref('ENTERPRISE');
const rows = ref([]);
const total = ref(0);
const keyword = ref('');
const page = ref(1);
const loading = ref(false);
const loadingMore = ref(false);
const finished = ref(false);
const hasError = ref(false);
/** 正在认领的客户编码（按钮级 loading） */
const claiming = ref('');

const emptyDesc = computed(() => (sea.value === 'TEAM'
  ? '本部门暂无可认领客户'
  : '当前没有未分配客户'));

/** 客户展示名：企业名优先，个人档回退联系人 */
function nameOf(c) {
  return c.enterpriseName || c.clientName || c.contactName || '未命名客户';
}

function firstChar(c) {
  return String(nameOf(c)).slice(0, 1);
}

/** 日期格式化：YYYY-MM-DD → MM-DD */
function fmtDate(s) {
  if (!s) return '';
  const day = String(s).replace('T', ' ').split(' ')[0];
  const parts = day.split('-');
  return parts.length === 3 ? `${parts[1]}-${parts[2]}` : day;
}

/** 副标题：公海客户 + 入库时间 */
function subOf(c) {
  const at = fmtDate(c.createdAt || c.lastFollowedAt);
  return at ? `公海客户 · 入库 ${at}` : '公海客户';
}

/**
 * 拉取公海列表。
 *
 * @param {number}  next   目标页码
 * @param {boolean} append 是否追加（上拉加载）
 */
async function fetchPage(next = 1, append = false) {
  if (append && (loadingMore.value || finished.value)) return;
  if (append) loadingMore.value = true;
  else loading.value = true;
  hasError.value = false;
  try {
    const res = await seaClients(sea.value, keyword.value, next, PAGE_SIZE);
    const list = (res && res.records) || [];
    rows.value = append ? rows.value.concat(list) : list;
    total.value = (res && res.total) || rows.value.length;
    page.value = next;
    finished.value = rows.value.length >= total.value || list.length < PAGE_SIZE;
  } catch (e) {
    hasError.value = true;
    if (!append) { rows.value = []; total.value = 0; }
  } finally {
    loading.value = false;
    loadingMore.value = false;
  }
}

/** 切换公海层级：重置分页后重新拉取 */
function onSwitchSea(key) {
  if (sea.value === key) return;
  sea.value = key;
  page.value = 1;
  finished.value = false;
  fetchPage(1, false);
}

/**
 * 认领公海客户到本人。
 *
 * @param {Object} c 客户摘要行
 */
async function onClaim(c) {
  if (!c.clientCode || claiming.value) return;
  claiming.value = c.clientCode;
  try {
    const res = await claimClient(c.clientCode);
    const result = res && res.result;
    if (result === CLAIM_AUTO) {
      uni.showToast({ title: '认领成功，已归入我的客户', icon: 'none', duration: 2400 });
    } else if (result === CLAIM_PENDING) {
      uni.showToast({ title: '已提交分配申请，等待审核', icon: 'none', duration: 2400 });
    } else {
      uni.showToast({ title: '已提交', icon: 'none' });
    }
    await fetchPage(1, false);
  } catch (e) {
    // 失败提示由 request.js 统一弹出（含并发抢占 / 无权认领等）
  } finally {
    claiming.value = '';
  }
}

function onSearch() { fetchPage(1, false); }
function loadMore() { fetchPage(page.value + 1, true); }
function refresh() { return fetchPage(1, false); }
function reload() { fetchPage(1, false); }

onMounted(() => {
  if (props.active) fetchPage(1, false);
});

// 从其它子页签切回时刷新第一页
watch(() => props.active, (on) => {
  if (on) fetchPage(1, false);
});

// 组件内无法注册页面级生命周期：上拉加载 / 下拉刷新由容器页转发
defineExpose({ loadMore, refresh });
</script>

<style scoped>
.scl {
  padding: var(--space-4);
  padding-bottom: calc(var(--space-16) + env(safe-area-inset-bottom));
  box-sizing: border-box;
}

.tabs {
  display: flex;
  gap: var(--space-2);
  background: var(--bg-input);
  border-radius: var(--radius-md);
  padding: var(--space-1);
  margin-bottom: var(--space-3);
}
.tab {
  flex: 1;
  min-width: 0;
  text-align: center;
  padding: 18rpx 0;
  font-size: var(--fs-sm);
  color: var(--text-secondary);
  border-radius: var(--radius-sm);
  transition: background var(--transition-fast), color var(--transition-fast);
}
.tab.on {
  background: var(--bg-card);
  color: var(--brand-deep);
  font-weight: 600;
  box-shadow: var(--shadow-sm);
}

.list { margin-top: var(--space-3); }

.count {
  font-size: var(--fs-xs);
  color: var(--text-secondary);
  padding: 0 var(--space-1) var(--space-2);
}
.count .num { color: var(--brand-deep); font-weight: 700; }

.row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) 0;
  border-top: 2rpx solid var(--line);
}
.row.first { border-top: none; }

.ava {
  flex-shrink: 0;
  width: 80rpx;
  height: 80rpx;
  border-radius: var(--radius-md);
  background: var(--bg-input);
  color: var(--brand-deep);
  font-size: var(--fs-lg);
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 6rpx; }
.t1 {
  font-size: var(--fs-md);
  font-weight: 600;
  color: var(--text-primary);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.t2 { font-size: var(--fs-xs); color: var(--text-secondary); }
</style>
