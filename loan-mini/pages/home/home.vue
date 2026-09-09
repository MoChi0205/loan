<template>
  <view class="home-page theme-root" :data-theme="themeMode">
    <!-- 顶部欢迎区（墨金）：头像 + 问候 + 待跟进 -->
    <view class="top-banner">
      <view class="banner-inner">
        <view class="user-row">
          <view class="avatar-ring">
            <image v-if="avatarUrl" :src="avatarUrl" class="avatar-img" mode="aspectFill" />
            <text v-else class="avatar-text">{{ avatarChar }}</text>
          </view>
          <view class="greeting-col">
            <text class="banner-hello">{{ greetingText }}，{{ shortName }}</text>
            <text class="banner-sub">{{ orderSubtitle }}</text>
          </view>
        </view>
      </view>
    </view>

    <view class="content">
      <!-- AI 智能匹配主卡片（墨金深色） -->
      <view class="ai-card" @click="onMatch">
        <view class="ai-glow" />
        <view class="ai-inner">
          <view class="ai-top">
            <text class="ai-badge">AI 智能匹配</text>
            <text class="ai-count">为你匹配 {{ recommendedCount }} 款产品</text>
          </view>
          <text class="ai-desc">基于企业资质与流水，30 秒生成资金方案</text>
          <view class="ai-btn">
            <text class="ai-btn-text">立即智能匹配</text>
            <text class="ai-btn-arrow">›</text>
          </view>
        </view>
      </view>

      <!-- 四宫格（设计稿顺序：我的报告 / 服务单 / 我的产品 / 录入客户） -->
      <view class="nav-section">
        <view class="nav-grid">
          <view
            v-for="entry in navEntries"
            :key="entry.key"
            class="nav-cell"
            @click="entry.action"
          >
            <view class="nav-icon-wrap">
              <AppIcon :name="entry.icon" size="xl" />
            </view>
            <text class="nav-name">{{ entry.label }}</text>
          </view>
        </view>
      </view>

      <!-- 我的报告列表 -->
      <view class="card report-card">
        <view class="sec-header">
          <text class="sec-title">我的报告</text>
          <text class="sec-more" @click="onReport">全部 ›</text>
        </view>
        <view class="report-list">
          <view
            v-for="r in reportList"
            :key="r.id"
            class="report-item"
            @click="onReport"
          >
            <view class="report-icon">
              <AppIcon :name="r.icon" size="md" :color="r.color" />
            </view>
            <view class="report-body">
              <text class="report-name">{{ r.name }}</text>
              <text class="report-meta">{{ r.date }} · {{ r.status }}</text>
            </view>
            <text class="report-arrow">›</text>
          </view>
        </view>
      </view>
    </view>

    <view class="footer-safety">
      <text class="safety-text">合规声明：匹配分析不构成任何资金机构通过承诺</text>
    </view>
  </view>
  <TabBar current="home" />
</template>

<script setup>
import { ref, computed } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { useUserStore } from '../../store/user';
import { useThemeMode } from '../../theme';
import TabBar from '../../components/TabBar.vue';
import AppIcon from '../../components/AppIcon.vue';
import { partnerProducts } from '../../api/match';
import { myProducts } from '../../api/product';
import { consumePendingInvitation } from '../../utils/invitation';
import { orderList } from '../../api/order';
import { approvalCounts } from '../../api/approval';
import { uploadAvatar } from '../../utils/avatar';

/**
 * 首页（对齐设计稿）：用户问候头 + 深色 AI 匹配主卡片 + 四宫格 + 我的报告列表。
 */
const store = useUserStore();
const themeMode = useThemeMode();

const partnerCount = ref(0);
const orderCount = ref(0);
const approvalTotal = ref(0);
// 设计稿占位：为你匹配 N 款产品（TODO: 接入智能匹配推荐接口替换）
const recommendedCount = ref(12);

/** 头像地址（后端 avatarUrl 优先，本地选择兜底） */
const avatarUrl = computed(() => store.avatarUrl);

/** 展示名称截断 */
const shortName = computed(() => {
  const name = (store.profile && store.profile.contactName) || (store.user && store.user.name);
  if (!name) return '用户';
  return name.length > 6 ? name.slice(0, 6) + '…' : name;
});

/** 默认头像首字 */
const avatarChar = computed(() => (shortName.value || '客')[0]);

/** 问候语 + 时间段 */
const greetingText = computed(() => {
  const h = new Date().getHours();
  if (h < 6) return '夜深了';
  if (h < 12) return '早上好';
  if (h < 14) return '中午好';
  if (h < 18) return '下午好';
  return '晚上好';
});

/** 待跟进副标题（动态，合规文案） */
const orderSubtitle = computed(() => {
  if (store.isChannel) return '渠道工作台 · 在售产品 ' + (partnerCount.value || 0) + ' 款';
  if (orderCount.value > 0) return `今天有 ${orderCount.value} 笔服务单待跟进`;
  return '暂无待跟进服务单';
});

/** 四宫格（设计稿统一顺序，不再按角色拆分） */
const navEntries = computed(() => [
  { key: 'report', label: '我的报告', icon: 'chart', action: onReport },
  { key: 'order', label: '服务单', icon: 'order', action: onOrder },
  { key: 'product', label: '我的产品', icon: 'bank', action: onProduct },
  { key: 'client', label: '录入客户', icon: 'users', action: onClient },
]);

/** 我的报告列表（示例数据，已合规化：设备贷预审批→设备预审核、审批中→审核中） */
const reportList = computed(() => [
  { id: 1, name: '企业资质初筛报告', icon: 'check', color: '#D9A441', date: '09-08', status: '已生成' },
  { id: 2, name: '流水分析报告', icon: 'chart', color: '#4C7BD9', date: '09-07', status: '已生成' },
  { id: 3, name: '设备预审核报告', icon: 'shield', color: '#11A86B', date: '09-05', status: '审核中' },
]);

onShow(async () => {
  try {
    const ok = await store.init();
    if (!ok) return;
    await consumePendingInvitation(store);
    approvalTotal.value = 0;
    orderCount.value = 0;
    if (store.isChannel) {
      await loadMyProductCount();
      return;
    }
    await Promise.all([loadPartnerCount(), loadOrderCount(), loadApprovalCount()]);
  } catch (e) {
    partnerCount.value = 0;
    orderCount.value = 0;
    approvalTotal.value = 0;
  }
});

async function loadMyProductCount() {
  try {
    const data = await myProducts();
    partnerCount.value = Array.isArray(data) ? data.length : 0;
  } catch (e) {
    partnerCount.value = 0;
  }
}

async function loadPartnerCount() {
  try {
    const data = await partnerProducts();
    if (Array.isArray(data)) {
      partnerCount.value = data.length;
    } else if (data && typeof data === 'object') {
      const c = data.count || data.productCount || data.total;
      partnerCount.value = Number(c) || (Array.isArray(data.records) ? data.records.length : 0);
    } else {
      partnerCount.value = Number(data) || 0;
    }
  } catch (e) {
    partnerCount.value = 0;
  }
}

async function loadOrderCount() {
  try {
    const data = await orderList({ page: 1, size: 1 });
    const total = (data && data.total) || (data && data.records ? data.records.length : 0);
    orderCount.value = total;
  } catch (e) {
    orderCount.value = 0;
  }
}

async function loadApprovalCount() {
  if (!store.hasPermission('mini:approval:view')) return;
  try {
    const c = await approvalCounts();
    const total = (c && typeof c.TOTAL === 'number') ? c.TOTAL : ((c && c.ALLOCATION) || 0);
    approvalTotal.value = total;
  } catch (e) {
    approvalTotal.value = 0;
  }
}

/** 选微信头像（小程序） */
function onChooseAvatar(e) {
  const url = e.detail && e.detail.avatarUrl;
  if (!url) return;
  store.setAvatar(url);
  uploadAvatar(url).then((remote) => { if (remote) store.setAvatar(remote); }).catch(() => {});
}

function onMatch() {
  if (!store.isStaff && !store.isAuthed) {
    uni.showToast({ title: '请先完成身份认证', icon: 'none' });
    uni.navigateTo({ url: '/pages/auth/auth' });
    return;
  }
  uni.reLaunch({ url: '/pages/match/match' });
}

function onReport() {
  uni.reLaunch({ url: '/pages/report/list' });
}

function onOrder() {
  uni.reLaunch({ url: '/pages/order/list' });
}

function onClient() {
  if (store.isChannel) {
    uni.navigateTo({ url: '/pages/client/create' });
  } else if (store.isStaff) {
    uni.reLaunch({ url: '/pages/match/match' });
  } else {
    uni.navigateTo({ url: '/pages/client/create' });
  }
}

/** 我的产品 */
function onProduct() {
  uni.reLaunch({ url: '/pages/product/list' });
}
</script>

<style scoped>
.home-page{
  min-height:100vh;
  padding-bottom:calc(128rpx + env(safe-area-inset-bottom));
  background:var(--bg-page);
  box-sizing:border-box
}
/* ===== 顶部欢迎区（墨金浅底） ===== */
.top-banner{
  background:var(--bg-card);
  padding:28rpx 32rpx;
  border-bottom:1rpx solid var(--line)
}
.banner-inner{ display:flex }
.user-row{
  display:flex;
  align-items:center;
  gap:20rpx;
  width:100%
}
.avatar-ring{
  width:84rpx;
  height:84rpx;
  border-radius:50%;
  background:var(--bg-input);
  display:flex;
  align-items:center;
  justify-content:center;
  overflow:hidden;
  flex-shrink:0
}
.avatar-img{
  width:100%;
  height:100%;
  border-radius:50%;
  display:block
}
.avatar-text{
  color:var(--text-secondary);
  font-size:32rpx;
  font-weight:600
}
.greeting-col{
  display:flex;
  flex-direction:column;
  flex:1;
  min-width:0
}
.banner-hello{
  color:var(--text-primary);
  font-size:36rpx;
  font-weight:700
}
.banner-sub{
  margin-top:6rpx;
  color:var(--text-secondary);
  font-size:24rpx
}
.content{
  padding:24rpx 32rpx 32rpx
}
/* ===== AI 智能匹配主卡片（墨金深色） ===== */
.ai-card{
  position:relative;
  overflow:hidden;
  border-radius:28rpx;
  padding:36rpx 32rpx;
  margin-bottom:28rpx;
  background:linear-gradient(155deg,#16203A 0%,#1C2A4D 55%,#243A66 100%);
  box-shadow:0 16rpx 40rpx rgba(15,23,42,.28)
}
.ai-glow{
  position:absolute;
  top:-80rpx;
  right:-60rpx;
  width:280rpx;
  height:280rpx;
  border-radius:50%;
  background:radial-gradient(circle, rgba(217,164,65,.28) 0%, rgba(217,164,65,0) 70%);
  pointer-events:none
}
.ai-inner{ position:relative; z-index:1 }
.ai-top{
  display:flex;
  align-items:center;
  justify-content:space-between;
  gap:12rpx
}
.ai-badge{
  font-size:24rpx;
  font-weight:700;
  color:#16203A;
  background:linear-gradient(135deg,#F2C879,#D9A441);
  padding:6rpx 18rpx;
  border-radius:999rpx
}
.ai-count{
  font-size:22rpx;
  color:rgba(232,237,245,.75)
}
.ai-desc{
  display:block;
  margin-top:20rpx;
  color:#E8EDF5;
  font-size:28rpx;
  line-height:1.4;
  font-weight:500
}
.ai-btn{
  display:inline-flex;
  align-items:center;
  gap:8rpx;
  margin-top:28rpx;
  padding:18rpx 36rpx;
  border-radius:999rpx;
  background:linear-gradient(135deg,#F2C879 0%,#D9A441 100%);
  box-shadow:0 10rpx 24rpx rgba(217,164,65,.4)
}
.ai-btn-text{
  color:#16203A;
  font-size:28rpx;
  font-weight:700
}
.ai-btn-arrow{
  color:#16203A;
  font-size:32rpx;
  font-weight:700
}
/* ===== 四宫格 ===== */
.nav-section{ margin-bottom:28rpx }
.nav-grid{
  display:flex;
  flex-wrap:wrap;
  background:var(--bg-card);
  border-radius:24rpx;
  padding:20rpx 4rpx;
  box-shadow:var(--shadow-md)
}
.nav-cell{
  display:flex;
  flex-direction:column;
  align-items:center;
  width:25%;
  padding:16rpx 4rpx;
  min-height:120rpx
}
.nav-icon-wrap{
  width:88rpx;
  height:88rpx;
  display:flex;
  align-items:center;
  justify-content:center;
  margin-bottom:12rpx;
  border-radius:24rpx;
  background:var(--bg-input)
}
.nav-name{
  font-size:24rpx;
  font-weight:600;
  color:var(--text-primary);
  text-align:center
}
/* ===== 我的报告列表 ===== */
.card{
  background:var(--bg-card);
  border-radius:var(--radius-md);
  padding:28rpx 32rpx;
  box-shadow:var(--shadow-md)
}
.sec-header{
  display:flex;
  align-items:center;
  justify-content:space-between;
  margin-bottom:8rpx;
  min-height:44rpx
}
.sec-title{
  font-size:32rpx;
  font-weight:700;
  color:var(--text-primary)
}
.sec-more{
  font-size:24rpx;
  color:var(--brand-deep);
  font-weight:500
}
.report-list{ display:flex; flex-direction:column }
.report-item{
  display:flex;
  align-items:center;
  gap:20rpx;
  padding:24rpx 0;
  border-bottom:1rpx solid var(--line)
}
.report-item:last-child{ border-bottom:none }
.report-icon{
  width:64rpx;
  height:64rpx;
  border-radius:16rpx;
  display:flex;
  align-items:center;
  justify-content:center;
  background:var(--bg-input);
  flex-shrink:0
}
.report-body{
  flex:1;
  display:flex;
  flex-direction:column;
  min-width:0
}
.report-name{
  font-size:28rpx;
  font-weight:600;
  color:var(--text-primary)
}
.report-meta{
  margin-top:4rpx;
  font-size:22rpx;
  color:var(--text-secondary)
}
.report-arrow{
  font-size:36rpx;
  color:var(--text-secondary);
  flex-shrink:0
}
.footer-safety{ padding:24rpx 32rpx 48rpx }
.safety-text{
  text-align:center;
  font-size:22rpx;
  color:var(--text-secondary)
}
/* 点击反馈 */
.ai-card:active,.nav-cell:active,.report-item:active{
  transform:scale(.99);
  opacity:.96
}
</style>
