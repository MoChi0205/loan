<template>
  <view class="approval-page">
    <!-- 类型分段 tab（审批中心统一：ALL / PRODUCT / DOWNLOAD / ALLOCATION / MATERIAL_REVIEW） -->
    <view class="seg-tabs">
      <view
        v-for="t in segTabs" :key="t.value"
        class="seg-tab" :class="{ active: activeType === t.value }"
        @click="switchType(t.value)"
      >
        <text class="seg-label">{{ t.label }}</text>
        <text v-if="countMap[t.value] > 0" class="seg-badge">{{ countMap[t.value] }}</text>
      </view>
    </view>

    <!-- 加载中 -->
    <AppSkeleton v-if="loading && !items.length" :rows="3" />

    <!-- 加载失败 -->
    <AppEmpty v-else-if="hasError && !items.length" title="加载失败"
      desc="网络异常或服务暂不可用，请重试">
      <AppButton variant="primary" size="md" @click="load(activeType)">重试</AppButton>
    </AppEmpty>

    <!-- 空态 -->
    <AppEmpty v-else-if="!items.length" title="暂无待审批"
      desc="分配申请与材料复核会显示在这里" />

    <!-- 待审列表 -->
    <view v-else class="list">
      <view v-for="it in items" :key="it.approvalNo" class="card item">
        <view class="item-head">
          <text class="item-ent">{{ approvalTitle(it) }}</text>
          <view class="head-tags">
            <AppTag type="muted" size="sm">{{ it.type }}</AppTag>
            <AppTag type="warning" size="sm">待审批</AppTag>
          </view>
        </view>
        <view class="item-meta">
          <text class="meta-line">申请人：{{ applicantName(it) }}</text>
          <text v-if="it.contactName" class="meta-line">联系人：{{ it.contactName }} {{ it.contactPhone || '' }}</text>
          <text v-if="it.reportNo" class="meta-line">关联报告：{{ it.reportNo }}</text>
          <text v-if="it.purpose" class="meta-line">申请用途：{{ it.purpose }}</text>
          <text class="meta-line">申请时间：{{ formatTime(it.createdAt) }}</text>
        </view>
        <view class="item-actions">
          <AppButton variant="ghost" size="sm" @click="onReject(it)">驳回</AppButton>
          <AppButton variant="primary" size="sm" :loading="acting === it.approvalNo" @click="onApprove(it)">通过</AppButton>
        </view>
      </view>
      <!-- ALL 为概览：后端 paginationHint=SEGMENTED，不做深翻页 -->
      <view v-if="activeType === 'ALL' && paginationHint === 'SEGMENTED'" class="list-seg-hint">
        仅概览前 20 条，请按类型查看完整列表
      </view>
      <view v-else-if="finished" class="list-end">没有更多了</view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import {
  pendingApprovals, auditApproval, approvalCounts, normalizeApprovalItem,
} from '../../api/approval';

/** 类型分段 tab 定义（当前四类审批均开放） */
const segTabs = [
  { value: 'ALL', label: '全部' },
  { value: 'PRODUCT', label: '产品' },
  { value: 'DOWNLOAD', label: '渠道产品' },
  { value: 'ALLOCATION', label: '分配' },
  { value: 'MATERIAL_REVIEW', label: '材料复核' },
];

/** 当前选中类型（默认分配，其他审批可按类型查看） */
const activeType = ref('ALLOCATION');

/** 各类型待审数（来自 approvalCounts） */
const counts = ref({ PRODUCT: 0, DOWNLOAD: 0, ALLOCATION: 0, MATERIAL_REVIEW: 0, TOTAL: 0 });

/** 角标映射：ALL→TOTAL，其余→各自计数 */
const countMap = computed(() => {
  const c = counts.value || {};
  return {
    ALL: c.TOTAL || 0,
    PRODUCT: c.PRODUCT || 0,
    DOWNLOAD: c.DOWNLOAD || 0,
    ALLOCATION: c.ALLOCATION || 0,
    MATERIAL_REVIEW: c.MATERIAL_REVIEW || 0,
  };
});

const items = ref([]);
const loading = ref(true);
const hasError = ref(false);
const finished = ref(false);
const acting = ref('');
/** 后端分页提示：ALL 概览时为 SEGMENTED，不做深翻页 */
const paginationHint = ref('');

function formatTime(t) {
  if (!t) return '';
  return String(t).replace('T', ' ').slice(0, 16);
}

/** 拉取各类型待审数（填充角标） */
async function loadCounts() {
  try {
    counts.value = await approvalCounts();
  } catch (e) {
    counts.value = { PRODUCT: 0, DOWNLOAD: 0, ALLOCATION: 0, MATERIAL_REVIEW: 0, TOTAL: 0 };
  }
}

async function load(type) {
  const t = type || activeType.value || 'ALLOCATION';
  loading.value = true;
  hasError.value = false;
  try {
    const data = await pendingApprovals(t, 1, 20);
    const records = (data && data.records) || [];
    items.value = records.map(normalizeApprovalItem);
    paginationHint.value = (data && data.paginationHint) || '';
    // ALL 为概览，仅取前 20 条；其余类型按返回长度判定是否到底
    finished.value = t === 'ALL' ? true : records.length < 20;
  } catch (e) {
    hasError.value = true;
  } finally {
    loading.value = false;
  }
}

/** 审批记录主标题：优先中文业务信息，材料复核退回到可理解的业务描述。 */
function approvalTitle(item) {
  if (item.entName || item.contactName) return item.entName || item.contactName;
  if (item.type === 'MATERIAL_REVIEW') return item.reportNo ? '报告材料复核' : '上传材料复核';
  if (item.type === 'PRODUCT') return item.bankProductName || '产品审核申请';
  if (item.type === 'DOWNLOAD') return '资料下载申请';
  return '待审批申请';
}

function applicantName(item) {
  return item.applicantName || item.applicantStaffName || item.createdBy || '待补充姓名';
}

function switchType(v) {
  if (v === activeType.value) return;
  activeType.value = v;
  load(v);
}

async function onApprove(it) {
  acting.value = it.approvalNo;
  try {
    await auditApproval(it.type, it.approvalNo, true);
    uni.showToast({ title: '审批已通过', icon: 'success' });
    items.value = items.value.filter(x => x.approvalNo !== it.approvalNo);
    await load(activeType.value); // 重新拉取当前类型
    await loadCounts();           // 刷新角标
  } catch (e) {
    uni.showToast({ title: (e && e.message) || '操作失败', icon: 'none' });
  } finally {
    acting.value = '';
  }
}

function onReject(it) {
  // 修复硬编码缺陷：驳回意见由用户输入，空意见不允许通过
  uni.showModal({
    title: '驳回',
    editable: true,
    placeholderText: '请输入驳回意见',
    success: (res) => {
      if (!res.confirm) return;
      const opinion = (res.content || '').trim();
      if (!opinion) {
        uni.showToast({ title: '请填写驳回意见', icon: 'none' });
        return;
      }
      acting.value = it.approvalNo;
      auditApproval(it.type, it.approvalNo, false, opinion)
        .then(() => {
          uni.showToast({ title: '已驳回', icon: 'success' });
          items.value = items.value.filter(x => x.approvalNo !== it.approvalNo);
          load(activeType.value);
          loadCounts();
        })
        .catch((e) => uni.showToast({ title: (e && e.message) || '操作失败', icon: 'none' }))
        .finally(() => { acting.value = ''; });
    },
  });
}

onLoad(async () => {
  await loadCounts();
  load('ALLOCATION');
});
</script>

<style scoped>
.approval-page {
  min-height: 100vh;
  padding: var(--space-page-gutter);
  box-sizing: border-box;
}

/* ===== 类型分段 tab ===== */
.seg-tabs {
  display: flex;
  gap: var(--space-1);
  background: var(--bg-input);
  padding: var(--space-1);
  border-radius: var(--radius-md);
  margin-bottom: var(--space-3);
}
.seg-tab {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  min-height: 88rpx;
  padding: 16rpx 6rpx;
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  font-size: var(--fs-sm);
}
.seg-tab.active {
  background: var(--bg-card);
  color: var(--brand-deep);
  font-weight: 700;
  box-shadow: var(--shadow-sm);
}
.seg-label { line-height: 40rpx; }
.seg-badge {
  min-width: 32rpx;
  height: 32rpx;
  padding: 0 8rpx;
  border-radius: 16rpx;
  background: var(--danger);
  color: var(--text-invert);
  font-size: var(--fs-xxs);
  font-weight: 700;
  line-height: 32rpx;
  text-align: center;
  flex-shrink: 0;
}

.list { display: flex; flex-direction: column; gap: var(--space-3); }
.item {
  padding: var(--space-card-pad);
  border-radius: var(--radius-md);
  background: var(--bg-card);
  box-shadow: var(--shadow-sm);
}
.item-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}
.item-ent {
  font-size: var(--fs-lg);
  font-weight: 700;
  color: var(--text-primary);
  flex: 1;
  min-width: 0;
}
.head-tags { display: flex; align-items: center; gap: var(--space-1); flex-shrink: 0; }
.item-meta {
  margin-top: var(--space-2);
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}
.meta-line {
  font-size: var(--fs-sm);
  color: var(--text-secondary);
}
.item-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
  margin-top: var(--space-3);
}
.list-end {
  text-align: center;
  font-size: var(--fs-xs);
  color: var(--text-placeholder);
  padding: var(--space-3) 0;
}
.list-seg-hint {
  text-align: center;
  font-size: var(--fs-xs);
  color: var(--text-placeholder);
  padding: var(--space-3) 0;
  border-top: 2rpx dashed var(--line);
  margin-top: var(--space-2);
}
</style>
