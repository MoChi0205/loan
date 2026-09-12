<template>
  <view class="mcl theme-root" :data-theme="themeMode">
    <AppSearchBar
      v-model="keyword"
      inputable
      show-button
      :placeholder="isChannel ? '搜索企业名称 / 联系人 / 手机号' : '搜索企业名称 / 联系人'"
      @search="onSearch"
    />

    <AppSkeleton v-if="loading && !rows.length" :rows="4" />
    <AppEmpty v-else-if="hasError && !rows.length" title="加载失败" desc="网络异常，请重试">
      <AppButton variant="primary" size="md" @click="reload">重试</AppButton>
    </AppEmpty>
    <AppEmpty v-else-if="!loading && !rows.length" title="暂无客户" :desc="emptyDesc" />

    <view v-else class="list">
      <view class="count">共 <text class="num">{{ total }}</text> 位{{ isChannel ? '（仅本人录入）' : '' }}</view>
      <view v-for="(c, i) in rows" :key="c.clientCode || i" class="row" :class="{ first: i === 0 }">
        <view class="ava">{{ firstChar(c) }}</view>
        <view class="main">
          <text class="t1">{{ nameOf(c) }}</text>
          <text class="t2">{{ subOf(c) }}</text>
        </view>
        <AppTag :type="tagType(c)" size="sm">{{ tagLabel(c) }}</AppTag>
        <!-- 释放本人客户回公司公海：仅员工可见（渠道只读，D50）；15-规则 §31 -->
        <AppButton
          v-if="canRelease"
          class="row-op"
          variant="ghost"
          size="sm"
          :loading="releasing === c.clientCode"
          @click="onRelease(c)"
        >释放</AppButton>
      </view>
      <AppLoadMore :loading="loadingMore" :finished="finished" :error="hasError" @load="loadMore" />
    </view>
  </view>
</template>

<script setup>
/**
 * 「我的客户」列表（本人归属）。
 *
 * <p><b>数据范围（D74）：</b>所有员工角色统一只显示本人归属客户，不做团队 / 全司放大；
 * 渠道走 {@code GET /api/channel/client/page} 只读本人录入客户（D50），无认领、无公海、无编辑。
 *
 * <p><b>合规（D68 / 02-红线 #7）：</b>仅展示企业名 / 联系人 / 归属姓名 / 建档时间，
 * **不展示任何业务单号**；手机号由服务端脱敏下发。
 *
 * <p>作为组件运行：上拉加载 / 下拉刷新由容器页转发（defineExpose）。
 */
import { ref, computed, onMounted, watch } from 'vue';
import AppSearchBar from './AppSearchBar.vue';
import AppTag from './AppTag.vue';
import AppSkeleton from './AppSkeleton.vue';
import AppEmpty from './AppEmpty.vue';
import AppButton from './AppButton.vue';
import AppLoadMore from './AppLoadMore.vue';
import { myClients, channelClients, releaseClient } from '../api/client';
import { useUserStore } from '../store/user';
import { useThemeMode } from '../theme';

/** 容器传入：当前子页签是否激活 */
const props = defineProps({
  active: { type: Boolean, default: true },
});

/** 每页条数（与后端 PageParams.MAX_SIZE 一致上限内） */
const PAGE_SIZE = 10;

const store = useUserStore();
const themeMode = useThemeMode();
const isChannel = computed(() => store.isChannel);

const rows = ref([]);
const total = ref(0);
const keyword = ref('');
const page = ref(1);
const loading = ref(false);
const loadingMore = ref(false);
const finished = ref(false);
const hasError = ref(false);
/** 正在释放的客户编码（按钮级 loading） */
const releasing = ref('');

/** 「释放」按钮可见性：员工可释放本人客户；渠道只读、无操作（D50） */
const canRelease = computed(() => store.isStaff);

const emptyDesc = computed(() => (isChannel.value
  ? '仅展示您本人录入并已转化的客户'
  : '录入线索并完成归属后在此查看'));

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

/** 副标题：归属 + 最近跟进（无归属显示「公海客户」） */
function subOf(c) {
  const owner = c.ownerStaffName
    ? `归属 ${c.ownerStaffName}`
    : (c.assigned ? '已归属' : '公海客户');
  const at = fmtDate(c.lastFollowedAt || c.createdAt);
  return at ? `${owner} · 最近 ${at}` : owner;
}

/** 状态标签：仅区分停用，其余统一为「已建档」 */
function tagLabel(c) {
  return c.status === 'DISABLED' ? '已停用' : '已建档';
}

function tagType(c) {
  return c.status === 'DISABLED' ? 'muted' : 'success';
}

/**
 * 拉取列表。
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
    const res = isChannel.value
      ? await channelClients(keyword.value, next, PAGE_SIZE)
      : await myClients(keyword.value, next, PAGE_SIZE);
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

function onSearch() { fetchPage(1, false); }
function loadMore() { fetchPage(page.value + 1, true); }
function refresh() { return fetchPage(1, false); }
function reload() { fetchPage(1, false); }

/**
 * 释放本人客户回公司公海（15-规则 §31：进公司公海并按全局配置设置冷却）。
 *
 * <p>破坏性操作，先二次确认；后端仅允许「客户归属本人」调用，越权返回 FORBIDDEN。
 *
 * @param {Object} c 客户摘要行
 */
async function onRelease(c) {
  if (!c.clientCode || releasing.value) return;
  const ok = await new Promise((resolve) => {
    uni.showModal({
      title: '释放客户',
      content: '释放后该客户将进入公司公海，其他同事可认领，且会设置回收冷却期。确认释放？',
      success: (res) => resolve(!!res.confirm),
      fail: () => resolve(false),
    });
  });
  if (!ok) return;
  releasing.value = c.clientCode;
  try {
    await releaseClient(c.clientCode);
    uni.showToast({ title: '已释放到公司公海', icon: 'none', duration: 2400 });
    await fetchPage(1, false);
  } catch (e) {
    // 失败提示由 request.js 统一弹出（含非本人归属 / 冷却期等）
  } finally {
    releasing.value = '';
  }
}

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
.mcl {
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
</style>
