<template>
  <view class="detail-page">
    <!-- 加载中 -->
    <AppSkeleton v-if="loading" :rows="4" />

    <template v-else-if="order">
      <!-- 状态头卡 -->
      <view class="status-card">
        <text class="status-label" :class="`sl-${tone}`">{{ statusLabel(order.status) }}</text>
        <text class="status-no">{{ order.orderNo }}</text>
        <text class="status-time">{{ formatTime(order.createdAt) }} 创建</text>
      </view>

      <!-- 跟进摘要 -->
      <view class="card">
        <text class="card-title">跟进摘要</text>
        <view class="info-row" v-if="order.customerRemark">
          <text class="info-label">客户备注</text>
          <text class="info-value">{{ order.customerRemark }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">最近更新</text>
          <text class="info-value">{{ formatTime(order.updatedAt) || '-' }}</text>
        </view>
        <view class="empty-remark" v-if="!order.customerRemark">
          <text class="empty-remark-text">暂无跟进备注，请联系顾问获取进展</text>
        </view>
      </view>

      <!-- 服务信息 -->
      <view class="card">
        <text class="card-title">服务信息</text>
        <view class="info-row">
          <text class="info-label">服务顾问</text>
          <text class="info-value">{{ order.ownerStaffName || '待分配' }}</text>
        </view>
        <view class="info-row" v-if="order.dealAmount != null && order.dealAmount !== ''">
          <text class="info-label">授信额度</text>
          <text class="info-value">¥ {{ formatAmount(order.dealAmount) }}</text>
        </view>
        <view class="info-row" v-if="order.dealTime">
          <text class="info-label">额度生效时间</text>
          <text class="info-value">{{ formatTime(order.dealTime) }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">客户类型</text>
          <text class="info-value">{{ groupLabel(order.customerGroup) }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">来源</text>
          <text class="info-value">{{ order.source || '小程序' }}</text>
        </view>
      </view>

      <!-- 合规提示 -->
      <view class="card tip-card">
        <text class="tip-text">具体产品、额度与利率以顾问沟通确认为准，本页不展示产品明细。</text>
      </view>
    </template>

    <!-- 空态 / 异常 -->
    <AppEmpty v-else title="服务单不存在或无权查看" desc="可能已被删除，或您没有查看权限">
      <AppButton type="primary" size="md" @click="goBack">返回列表</AppButton>
    </AppEmpty>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import { orderDetail } from '../../api/order';

/**
 * 服务单详情（P0-6）：状态 / 顾问 / 跟进摘要。
 * 按合规决策不展示产品名/银行名/额度/利率明细（评审决策 08-28）。
 */
const loading = ref(true);
const order = ref(null);

onLoad(async (query) => {
  const orderNo = query && query.orderNo;
  if (!orderNo) {
    loading.value = false;
    return;
  }
  try {
    order.value = await orderDetail(orderNo);
  } catch (e) {
    order.value = null;
  } finally {
    loading.value = false;
  }
});

const tone = computed(() => {
  const s = (order.value && order.value.status) || '';
  if (s === 'DEAL') return 'pass';
  if (['CANCEL', 'REFUND'].includes(s)) return 'reject';
  return 'condition';
});

function statusLabel(s) {
  const map = {
    NEW: '新建', IN_SERVICE: '服务中', DEAL: '已成交',
    CANCEL: '已取消', REFUND: '已退款',
  };
  return map[s] || (s || '未知');
}

function groupLabel(g) {
  return { ENTERPRISE: '企业客户', PERSONAL: '个人客户' }[g] || (g || '-');
}

function formatAmount(v) {
  const n = Number(v);
  if (Number.isNaN(n)) return v;
  return String(Math.round(n)).replace(/\B(?=(\d{3})+(?!\d))/g, ',');
}

function formatTime(t) {
  if (!t) return '';
  const s = String(t).replace('T', ' ').replace(/-/g, '/');
  return s.length > 19 ? s.slice(0, 19) : s;
}

function goBack() {
  uni.navigateBack();
}
</script>

<style scoped>
.detail-page {
  min-height: 100vh;
  padding: 0 32rpx 48rpx;
  background: var(--bg-page);
  box-sizing: border-box;
}

.loading-box {
  padding: 160rpx 0;
  text-align: center;
}

.loading-text {
  font-size: 26rpx;
  color: var(--text-placeholder);
}

/* 状态头卡 */
.status-card {
  margin-top: 24rpx;
  border-radius: 20rpx;
  padding: 44rpx 32rpx;
  background: linear-gradient(135deg, var(--brand-bright), var(--role-deptmgr), var(--info));
  color: var(--text-invert);
  display: flex;
  flex-direction: column;
  align-items: center;
}

.status-label {
  padding: 10rpx 36rpx;
  border-radius: 40rpx;
  font-size: 32rpx;
  font-weight: 700;
  background: rgba(255, 255, 255, 0.22);
}

.sl-pass {
  background: rgba(34, 197, 94, 0.9);
}

.sl-reject {
  background: rgba(107, 114, 128, 0.9);
}

.status-no {
  margin-top: 22rpx;
  font-size: 26rpx;
  color: rgba(255, 255, 255, 0.95);
}

.status-time {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.75);
}

/* 卡片 */
.card {
  background: var(--text-invert);
  border: 1rpx solid var(--line);
  border-radius: 16rpx;
  padding: 32rpx;
  /* 相邻卡片间距统一提到 32rpx，避免多卡片堆叠时层次不清 */
  margin-top: 32rpx;
}

.card-title {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 16rpx;
}

.info-row {
  display: flex;
  justify-content: space-between;
  padding: 14rpx 0;
}

.info-label {
  font-size: 26rpx;
  color: var(--text-secondary);
}

.info-value {
  flex: 1;
  margin-left: 32rpx;
  text-align: right;
  font-size: 26rpx;
  color: var(--text-primary);
  word-break: break-all;
}

.empty-remark-text {
  font-size: 24rpx;
  color: var(--text-placeholder);
}

/* 合规提示 */
.tip-card {
  background: var(--warning-bg);
  border-color: var(--warning-line);
}

.tip-text {
  font-size: 24rpx;
  color: var(--warning-text);
  line-height: 1.6;
}

</style>
