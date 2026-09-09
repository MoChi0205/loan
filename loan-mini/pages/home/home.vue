<template>
  <view class="home-page" :class="{ 'u-shell': store.isTablet }">
    <!-- 顶部欢迎区：冷玻璃磨砂质感（压缩 40%，头像+问候+认证一行） -->
    <view class="top-banner">
      <view class="banner-inner">
        <view class="user-row">
          <!-- 微信头像：有头像显示图片，无则首字默认头像；小程序可点击选择 -->
          <button v-if="canChooseAvatar" class="avatar-ring" open-type="chooseAvatar" @chooseavatar="onChooseAvatar" hover-class="avatar-hover">
            <image v-if="avatarUrl" :src="avatarUrl" class="avatar-img" mode="aspectFill" />
            <text v-else class="avatar-text">{{ avatarChar }}</text>
          </button>
          <view v-else class="avatar-ring" @click="onAvatarFallback">
            <image v-if="avatarUrl" :src="avatarUrl" class="avatar-img" mode="aspectFill" />
            <text v-else class="avatar-text">{{ avatarChar }}</text>
          </view>
          <view class="greeting-col">
            <text class="banner-hello">{{ greetingText }}，{{ shortName }}</text>
            <text class="banner-time">{{ timeGreeting }}</text>
          </view>
          <!-- 认证状态：右侧小胶囊（仅客户） -->
          <AppClickable v-if="!store.isChannel && !store.isStaff" class="auth-pill" :class="store.isAuthed ?' pill-ok' :' pill-todo'" @click="onGoAuth">
            <text class="pill-icon">{{ store.isAuthed ? '✓' : '!' }}</text>
            <text class="pill-text">{{ store.isAuthed ? '已认证' : '完成认证' }}</text>
          </AppClickable>
        </view>

        <!-- 电商风格搜索栏 -->
        <AppClickable class="search-bar" @click="onSearch">
          <AppIcon name="search" size="sm" color="rgba(26, 35, 54, 0.55)" />
          <text class="search-ph">{{ searchPlaceholder }}</text>
        </AppClickable>
      </view>
    </view>

    <!-- 内容区 -->
    <view class="content">
      <!-- 认证引导卡（仅客户；渠道与员工无需客户身份认证） -->
      <AppClickable class="card promo-card u-hover" v-if="!store.isStaff && !store.isAuthed && !store.isChannel" @click="onGoAuth">
        <view class="promo-left">
          <view class="promo-icon-wrap">
            <AppIcon name="bolt" size="md" />
          </view>
          <view class="promo-body">
            <text class="promo-title">完成认证，解锁智能匹配</text>
            <text class="promo-desc">企业营业执照或个人实名，二选一</text>
          </view>
        </view>
        <text class="promo-arrow">›</text>
      </AppClickable>

      <!-- 动态数据卡片（固定四列，不滚动，不折叠） -->
      <view class="stat-section" v-if="statCards.length">
        <view class="sec-header">
          <text class="sec-title">{{ roleSectionTitle }}</text>
        </view>
        <view class="grid-4 stat-grid">
          <AppClickable v-for="card in statCards" :key="card.key" class="stat-card" @click="card.action">
            <view class="stat-icon-wrap">
              <AppIcon :name="card.icon" size="lg" />
            </view>
            <text class="stat-num">{{ card.value }}</text>
            <text class="stat-name">{{ card.label }}</text>
          </AppClickable>
        </view>
      </view>

      <!-- 角色化功能导航网格（固定四列，不折叠断层） -->
      <view class="nav-section">
        <view class="sec-header">
          <text class="sec-title">快捷功能</text>
        </view>
        <view class="nav-grid">
          <AppClickable v-for="entry in navEntries" :key="entry.key" class="nav-cell u-hover" @click="entry.action">
            <view class="nav-icon-wrap">
              <AppIcon :name="entry.icon" size="xl" />
            </view>
            <text class="nav-name">{{ entry.label }}</text>
          </AppClickable>
        </view>
      </view>

      <!-- 合作产品提示（渠道为供给方，不展示机构就绪提示） -->
      <view class="card partner-tip" v-if="partnerCount > 0 && !store.isChannel">
        <view class="tip-dot" />
        <text class="tip-text">当前合作机构已就绪 · 在售产品 {{ partnerCount }} 款</text>
      </view>
    </view>

    <!-- 底部声明 -->
    <view class="footer-safety">
      <text class="safety-text">合规声明：匹配程度分析不构成任何银行通过承诺</text>
    </view>
  </view>

  <!-- 角色化底部导航（自绘 tabBar） -->
  <TabBar current="home" />
</template>

<script setup>
import { ref, computed } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { useUserStore } from '../../store/user';
import TabBar from '../../components/TabBar.vue';
import AppIcon from '../../components/AppIcon.vue';
import { partnerProducts } from '../../api/match';
import { myProducts } from '../../api/product';
import { consumePendingInvitation } from '../../utils/invitation';
import { orderList } from '../../api/order';
import { approvalCounts } from '../../api/approval';
import { uploadAvatar } from '../../utils/avatar';

/**
 * tabBar 首页：冷玻璃风格重设计（压缩顶部 + 微信头像 + 固定四列网格）。
 */
const store = useUserStore();

const partnerCount = ref(0);
const orderCount = ref(0);
const approvalTotal = ref(0);

/** 头像地址（后端 avatarUrl 优先，本地选择兜底） */
const avatarUrl = computed(() => store.avatarUrl);

/** 是否可主动选择微信头像（仅微信小程序 + 已登录） */
const canChooseAvatar = computed(() => {
  // #ifdef MP-WEIXIN
  return !!store.token;
  // #endif
  // #ifndef MP-WEIXIN
  return false;
  // #endif
});

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

const timeGreeting = computed(() => {
  const now = new Date();
  const m = now.getMonth() + 1;
  const d = now.getDate();
  const w = ['日','一','二','三','四','五','六'][now.getDay()];
  return `${m}月${d}日 周${w}`;
});

/** 搜索栏占位文案（角色化） */
const searchPlaceholder = computed(() => {
  if (store.isChannel) return '搜索产品名称 / 银行';
  if (store.isStaff) return '搜索客户名称 / 手机号 / 信用代码';
  return '搜索报告 / 匹配记录';
});

/** 角色区标题 */
const roleSectionTitle = computed(() => {
  if (store.isChannel) return '渠道概览';
  if (store.isStaff) return '工作概览';
  return '我的数据';
});

/* ===== 动态数据卡片（角色化） ===== */
const statCards = computed(() => {
  const cards = [];

  if (store.isChannel) {
    cards.push({ key: 'product', label: '我的产品', value: partnerCount.value || 0, icon: 'bank', action: onProduct, extra: '点击管理' });
    return cards;
  }

  if (store.isStaff) {
    const canApprove = store.hasPermission('mini:approval:view');
    cards.push({ key: 'match', label: '匹配任务', value: '—', icon: 'match', action: onMatch, extra: '发起匹配' });
    cards.push({ key: 'order', label: '服务工单', value: orderCount.value || 0, icon: 'order', action: onOrder, extra: orderCount.value ? '待处理' : '暂无' });
    if (canApprove) {
      cards.push({ key: 'approval', label: '待审批', value: approvalTotal.value || 0, icon: 'check', action: onApproval, extra: approvalTotal.value ? '待处理' : '已清' });
    }
    cards.push({ key: 'report', label: '客户报告', value: '—', icon: 'chart', action: onReport, extra: '查看' });
    return cards;
  }

  // 客户
  cards.push({ key: 'match', label: '智能匹配', value: '—', icon: 'match', action: onMatch, extra: '发起匹配' });
  cards.push({ key: 'report', label: '我的报告', value: '—', icon: 'chart', action: onReport, extra: '查看历史' });
  cards.push({ key: 'order', label: '服务单', value: orderCount.value || 0, icon: 'order', action: onOrder, extra: orderCount.value ? '跟进中' : '暂无' });
  return cards;
});

/* ===== 宫格功能导航（角色化） ===== */
const navEntries = computed(() => {
  if (store.isChannel) {
    return [
      { key: 'product', label: '我的产品', icon: 'bank', desc: '录入·审批', action: onProduct },
      { key: 'client', label: '录入客户', icon: 'users', desc: '线索录入', action: onClient },
      { key: 'mine', label: '我的', icon: 'mine', desc: '账户设置', action: onMine },
    ];
  }

  if (store.isStaff) {
    const canApprove = store.hasPermission('mini:approval:view');
    const entries = [
      { key: 'match', label: '智能匹配', icon: 'match', desc: '替客匹配', action: onMatch },
      { key: 'report', label: store.role === 'adviser' ? '客户报告' : '报告中心', icon: 'chart', desc: '匹配报告', action: onReport },
      { key: 'order', label: store.role === 'adviser' ? '客户工单' : '工单中心', icon: 'order', desc: '服务跟进', action: onOrder },
      { key: 'client', label: '客户档案', icon: 'users', desc: '客户管理', action: onClient },
    ];
    if (canApprove) {
      entries.push({ key: 'approval', label: '审批中心', icon: 'check', desc: `${approvalTotal.value || 0} 待审`, action: onApproval });
    }
    entries.push({ key: 'mine', label: '我的', icon: 'mine', desc: '账户设置', action: onMine });
    return entries;
  }

  // 客户
  return [
    { key: 'match', label: '智能匹配', icon: 'match', desc: '获取评级', action: onMatch },
    { key: 'report', label: '我的报告', icon: 'chart', desc: '历史记录', action: onReport },
    { key: 'order', label: '服务单', icon: 'order', desc: '进度跟进', action: onOrder },
    { key: 'mine', label: '我的', icon: 'mine', desc: '账户设置', action: onMine },
  ];
});

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

/** 选择微信头像回调（button open-type=chooseAvatar） */
function onChooseAvatar(e) {
  const url = e.detail && e.detail.avatarUrl;
  if (!url) return;
  store.setAvatar(url); // 本地即时预览
  uploadAvatar(url).then((remote) => { if (remote) store.setAvatar(remote); }).catch(() => {});
}

/** H5 端无微信头像能力，点击提示 */
function onAvatarFallback() {
  uni.showToast({ title: '请在微信小程序中选择头像', icon: 'none' });
}

function onSearch() {
  if (store.isChannel) {
    uni.reLaunch({ url: '/pages/product/list' });
  } else if (store.isStaff) {
    uni.reLaunch({ url: '/pages/match/match' });
  } else {
    uni.reLaunch({ url: '/pages/report/list' });
  }
}

function onGoAuth() {
  uni.navigateTo({ url: '/pages/auth/auth' });
}

function onMatch() {
  if (!store.isStaff && !store.isAuthed) {
    uni.showToast({ title: '请先完成身份认证', icon: 'none' });
    onGoAuth();
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
  }
}

function onApproval() {
  uni.navigateTo({ url: '/pages/approval/list' });
}

/** 渠道入口：我的产品 */
function onProduct() {
  uni.reLaunch({ url: '/pages/product/list' });
}

/** 渠道入口：我的 */
function onMine() {
  uni.reLaunch({ url: '/pages/mine/mine' });
}
</script>

<style scoped>
.home-page{
  min-height:100vh;
  padding-bottom:calc(128rpx + env(safe-area-inset-bottom));
  background:var(--bg-page);
  box-sizing:border-box
}
/* ===== 冷玻璃顶部（磨砂油画质感，压缩高度） ===== */
.top-banner{
  position:relative;
  margin:0;
  padding:28rpx 32rpx 32rpx;
  background:var(--glass-gradient);
  overflow:hidden;
  border-bottom:1rpx solid var(--glass-edge)
}
.top-banner::before{
  content:'';
  position:absolute;
  top:-60rpx;
  right:-40rpx;
  width:280rpx;
  height:280rpx;
  border-radius:50%;
  background:radial-gradient(circle at 50% 50%, var(--glass-hi) 0%, rgba(255,255,255,0) 70%);
  pointer-events:none
}
/* H5 真毛玻璃（小程序不支持 backdrop-filter，降级为上方渐变） */
/* #ifdef H5 */
.top-banner{
  backdrop-filter:blur(28rpx);
  -webkit-backdrop-filter:blur(28rpx);
  background:rgba(232,236,245,0.72)
}
/* #endif */
.banner-inner{
  position:relative;
  z-index:1
}
.user-row{
  display:flex;
  align-items:center;
  gap:20rpx
}
.avatar-ring{
  width:84rpx;
  height:84rpx;
  border-radius:50%;
  background:var(--glass-bg-deep);
  border:3rpx solid var(--glass-edge);
  display:flex;
  align-items:center;
  justify-content:center;
  flex-shrink:0;
  padding:0;
  margin:0;
  overflow:hidden;
  line-height:1
}
.avatar-ring::after{
  border:none
}
.avatar-hover{
  opacity:.85
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
  font-size:var(--fs-xl);
  font-weight:700
}
.banner-time{
  margin-top:6rpx;
  color:var(--text-secondary);
  font-size:22rpx
}
/* 认证小胶囊（右侧，不占第二行） */
.auth-pill{
  display:inline-flex;
  align-items:center;
  gap:6rpx;
  margin-left:8rpx;
  padding:8rpx 20rpx;
  border-radius:var(--radius-full);
  flex-shrink:0;
  /* 玻璃底 + 品牌蓝字（默认/状态态） */
  background:var(--btn-glass-bg);
  border:1rpx solid var(--btn-glass-border);
  color:var(--brand-deep);
  font-size:22rpx;
  font-weight:500;
  transition:background .2s,transform .1s
}
.auth-pill:active{
  transform:scale(.97)
}
.pill-icon{
  font-size:20rpx;
  font-weight:700
}
.pill-text{
  font-size:22rpx;
  font-weight:500
}
/* 已认证：状态徽标 —— 柔和绿玻璃，弱化不抢主视觉 */
.pill-ok{
  background:rgba(17,168,107,.14);
  border-color:rgba(17,168,107,.26);
  color:var(--success-text)
}
/* 完成认证：行动按钮 —— 与主 CTA 同源的油画渐变，白字 + 投影 */
.pill-todo{
  background:var(--btn-primary-bg);
  border:none;
  color:var(--text-invert);
  box-shadow:var(--btn-primary-shadow)
}
/* 电商风格搜索栏 */
.search-bar{
  display:flex;
  align-items:center;
  gap:12rpx;
  margin-top:24rpx;
  height:72rpx;
  padding:0 24rpx;
  background:var(--bg-card);
  border-radius:var(--radius-full);
  box-shadow:var(--shadow-sm)
}
.search-bar:active{
  opacity:.92;
  transform:scale(.99)
}
.search-ph{
  font-size:26rpx;
  color:var(--text-secondary)
}
.content{
  padding:0 32rpx 32rpx;
  margin-top:28rpx;
  position:relative;
  z-index:2
}
.card{
  background:var(--bg-card);
  border-radius: var(--radius-md);
  padding:32rpx;
  margin-bottom:32rpx;
  box-shadow: var(--shadow-md)
}
.sec-header{
  display:flex;
  align-items:center;
  justify-content:space-between;
  margin-bottom:24rpx;
  min-height:44rpx
}
.sec-title{
  font-size:var(--fs-lg);
  font-weight:700;
  color:var(--text-primary);
  line-height:1.2
}
/* ===== 认证引导卡 ===== */
.promo-card{
  display:flex;
  align-items:center;
  justify-content:space-between;
  padding:28rpx 32rpx;
  background:var(--warning-bg);
  box-shadow:none
}
.promo-left{
  display:flex;
  align-items:center;
  gap:20rpx;
  flex:1
}
/* 认证引导卡图标：沿用 v4 玻璃圆底 + 品牌蓝渐变字形，去掉橙底方块 */
.promo-icon-wrap{
  width:64rpx;
  height:64rpx;
  display:flex;
  align-items:center;
  justify-content:center;
  flex-shrink:0
}
.promo-body{
  display:flex;
  flex-direction:column
}
.promo-title{
  font-size: var(--fs-em);
  font-weight:600;
  color:var(--gold-text)
}
.promo-desc{
  margin-top:4rpx;
  font-size:23rpx;
  color:var(--warning-text)
}
.promo-arrow{
  font-size: var(--fs-arrow);
  color:var(--warning-text);
  flex-shrink:0
}
/* ===== 固定四列网格（动态数据 + 快捷功能通用） ===== */
.grid-4{
  display:flex;
  flex-wrap:wrap;
  background:var(--bg-card);
  border-radius:var(--radius-md);
  padding:16rpx 4rpx;
  box-shadow:var(--shadow-md)
}
.stat-grid{
  margin-bottom:32rpx
}
.stat-card{
  display:flex;
  flex-direction:column;
  align-items:center;
  justify-content:center;
  width:25%;
  padding:16rpx 4rpx;
  background:transparent;
  box-shadow:none;
  text-align:center
}
.stat-card:active{
  transform:scale(.97)
}
/* 图标自带圆形玻璃底（v4 油画玻璃），容器不再叠加方角浅底，避免双层底 */
.stat-icon-wrap{
  width:64rpx;
  height:64rpx;
  display:flex;
  align-items:center;
  justify-content:center;
  margin-bottom:10rpx
}
.stat-num{
  font-size:var(--fs-2xl);
  font-weight:800;
  color:var(--text-primary);
  line-height:1.1
}
.stat-name{
  display:block;
  width:100%;
  margin-top:4rpx;
  font-size:23rpx;
  color:var(--text-secondary)
}
/* ===== 宫格功能导航（固定四列，统一品牌蓝） ===== */
.nav-section{
  margin-bottom:32rpx
}
.nav-grid{
  display:flex;
  flex-wrap:wrap;
  background:var(--bg-card);
  border-radius:var(--radius-md);
  padding:16rpx 4rpx;
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
.nav-cell:active{
  transform:scale(.94)
}
/* 图标自带圆形玻璃底（v4），容器透明 */
.nav-icon-wrap{
  width:88rpx;
  height:88rpx;
  display:flex;
  align-items:center;
  justify-content:center;
  margin-bottom:12rpx
}
.nav-name{
  display:block;
  width:100%;
  font-size:24rpx;
  font-weight:600;
  color:var(--text-primary);
  line-height:1.3;
  flex-shrink:0;
  text-align:center
}

/* ===== 合作产品提示 ===== */
.partner-tip{
  display:flex;
  align-items:center;
  gap:14rpx;
  padding:24rpx 28rpx
}
.tip-dot{
  width:12rpx;
  height:12rpx;
  border-radius:50%;
  background:var(--brand-deep);
  flex-shrink:0
}
.tip-text{
  font-size:24rpx;
  color:var(--text-body)
}
.footer-safety{
  padding:24rpx 32rpx 48rpx
}
.safety-text{
  text-align:center;
  font-size:22rpx;
  color:var(--text-secondary)
}
</style>
