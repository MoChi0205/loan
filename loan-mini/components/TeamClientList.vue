<template>
  <view class="tcl theme-root" :data-theme="themeMode">
    <AppSearchBar v-model="keyword" inputable show-button placeholder="搜索企业名称" @search="onSearch" />

    <AppSkeleton v-if="loading && !rows.length" :rows="4" />
    <AppEmpty v-else-if="hasError && !rows.length" title="加载失败" desc="网络异常，请重试">
      <AppButton variant="primary" size="md" @click="reload">重试</AppButton>
    </AppEmpty>
    <AppEmpty v-else-if="!loading && !rows.length" title="暂无团队客户" desc="本部门成员名下暂无客户" />

    <view v-else class="list">
      <view class="count">共 <text class="num">{{ total }}</text> 位 · 本部门成员名下客户</view>
      <view v-for="(c, i) in rows" :key="c.clientCode || i" class="row" :class="{ first: i === 0 }">
        <view class="ava">{{ firstChar(c) }}</view>
        <view class="main">
          <text class="t1">{{ nameOf(c) }}</text>
          <text class="t2">{{ subOf(c) }}</text>
        </view>
        <AppButton
          class="row-op"
          variant="ghost"
          size="sm"
          :loading="recycling === c.clientCode"
          @click="onRecycle(c)"
        >回收</AppButton>
      </view>
      <AppLoadMore :loading="loadingMore" :finished="finished" :error="hasError" @load="loadMore" />
    </view>

    <view class="tcl-tip">
      回收后客户进入<b>本团队公海</b>并设置冷却期；不可回收其他团队客户的客户（15-规则 §32/§33）。
    </view>
  </view>
</template>

<script setup>
/**
 * 「团队客户」列表（部门经理专属）。
 *
 * <p><b>范围（15-规则 §10）：</b>本部门在职成员（排除本人）名下客户，与「我的客户」独立展示；
 * 后端 {@code GET /api/mini/client/team} 仅放行 {@code DEPT_MANAGER}，无部门编码返回空（fail-closed）。
 *
 * <p><b>回收（15-规则 §32/§33/§34）：</b>部门经理回收本团队客户 → 落<b>团队公海</b>；
 * 跨团队回收由服务端拒绝；覆盖冷却期，不删除客户档案。
 *
 * <p><b>合规（D68 / 02-红线 #7）：</b>仅展示企业名 / 联系人 / 归属姓名 / 建档时间，**不展示任何业务单号**。
 *
 * <p>作为组件运行：上拉加载 / 下拉刷新由容器页转发（defineExpose）。
 */
import { ref, onMounted, watch } from 'vue';
import AppSearchBar from './AppSearchBar.vue';
import AppSkeleton from './AppSkeleton.vue';
import AppEmpty from './AppEmpty.vue';
import AppButton from './AppButton.vue';
import AppLoadMore from './AppLoadMore.vue';
import { teamClients, recycleClient } from '../api/client';
import { useThemeMode } from '../theme';

/** 容器传入：当前子页签是否激活 */
const props = defineProps({
  active: { type: Boolean, default: true },
});

/** 每页条数 */
const PAGE_SIZE = 10;

const themeMode = useThemeMode();

const rows = ref([]);
const total = ref(0);
const keyword = ref('');
const page = ref(1);
const loading = ref(false);
const loadingMore = ref(false);
const finished = ref(false);
const hasError = ref(false);
/** 正在回收的客户编码（按钮级 loading） */
const recycling = ref('');

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

/** 副标题：归属顾问 + 最近跟进 */
function subOf(c) {
  const owner = c.ownerStaffName ? `归属 ${c.ownerStaffName}` : '已归属';
  const at = fmtDate(c.lastFollowedAt || c.createdAt);
  return at ? `${owner} · 最近 ${at}` : owner;
}

/**
 * 拉取团队客户列表。
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
    const res = await teamClients(keyword.value, next, PAGE_SIZE);
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

/**
 * 回收团队客户进本团队公海。
 *
 * @param {Object} c 客户摘要行
 */
async function onRecycle(c) {
  if (!c.clientCode || recycling.value) return;
  const ok = await new Promise((resolve) => {
    uni.showModal({
      title: '回收客户',
      content: '回收后该客户进入本团队公海并设置冷却期，原归属顾问将失去该客户。确认回收？',
      success: (res) => resolve(!!res.confirm),
      fail: () => resolve(false),
    });
  });
  if (!ok) return;
  recycling.value = c.clientCode;
  try {
    await recycleClient(c.clientCode);
    uni.showToast({ title: '已回收进团队公海', icon: 'none', duration: 2400 });
    await fetchPage(1, false);
  } catch (e) {
    // 失败提示由 request.js 统一弹出（含跨团队 / 无归属等）
  } finally {
    recycling.value = '';
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
.tcl {
  padding: var(--space-4);
  padding-bottom: calc(var(--space-16) + env(safe-area-inset-bottom));
  box-sizing: border-box;
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
/* 行内操作按钮：不参与压缩，保证 88rpx 可点高度（无障碍 P1-3） */
.row-op { flex: none; margin-left: var(--space-1); }

.t1 {
  font-size: var(--fs-md);
  font-weight: 600;
  color: var(--text-primary);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.t2 { font-size: var(--fs-xs); color: var(--text-secondary); }

.tcl-tip {
  margin-top: var(--space-3);
  font-size: var(--fs-xs);
  line-height: var(--lh-base);
  color: var(--text-secondary);
  background: var(--warning-bg);
  border: 2rpx solid var(--warning-line);
  border-radius: var(--radius-md);
  padding: var(--space-3);
}
.tcl-tip b { color: var(--warning-text); }
</style>
