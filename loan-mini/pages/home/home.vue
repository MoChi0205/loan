<template>
  <view class="home-page" :class="{ 'u-shell': store.isTablet }">
    <!-- 顶部欢迎区：深色品牌渐变 + 搜索栏 -->
    <view class="top-banner">
      <view class="banner-decor" />
      <view class="banner-inner">
        <view class="user-row">
          <view class="avatar-ring">
            <text class="avatar-text">{{ avatarChar }}</text>
          </view>
          <view class="greeting-col">
            <text class="banner-hello">{{ greetingText }}，{{ shortName }}</text>
            <text class="banner-time">{{ timeGreeting }}</text>
          </view>
        </view>

        <!-- 电商风格搜索栏 -->
        <view class="search-bar" @click="onSearch">
          <AppIcon name="search" size="sm" color="var(--text-secondary)" />
          <text class="search-ph">{{ searchPlaceholder }}</text>
        </view>

        <view class="auth-pill" v-if="!store.isChannel && !store.isStaff" :class="store.isAuthed ? 'pill-ok' : 'pill-todo'" @click="onGoAuth">
          <text class="pill-icon">{{ store.isAuthed ? '✓' : '!' }}</text>
          <text class="pill-text">{{ store.isAuthed ? '已认证' : '完成身份认证' }}</text>
        </view>
      </view>
    </view>

    <!-- 内容区 -->
    <view class="content">
      <!-- 认证引导卡（仅客户；渠道与员工无需客户身份认证） -->
      <view class="card promo-card u-hover" v-if="!store.isStaff && !store.isAuthed && !store.isChannel" @click="onGoAuth">
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
      </view>

      <!-- 动态数据卡片（电商风格：横向滚动 / 点击跳转） -->
      <view class="stat-section" v-if="statCards.length">
        <view class="sec-header">
          <text class="sec-title">{{ roleSectionTitle }}</text>
          <text class="sec-sub" v-if="statCards.length > 2">滑动查看更多</text>
        </view>
        <scroll-view scroll-x class="stat-scroll" :show-scrollbar="false">
          <view class="stat-track">
            <view
              v-for="card in statCards"
              :key="card.key"
              class="stat-card u-hover"
              :class="`stat-${card.tone}`"
              @click="card.action"
            >
              <view class="stat-icon-wrap">
                <AppIcon :name="card.icon" size="md" />
              </view>
              <text class="stat-num">{{ card.value }}</text>
              <text class="stat-name">{{ card.label }}</text>
              <text class="stat-extra" v-if="card.extra">{{ card.extra }}</text>
            </view>
          </view>
        </scroll-view>
      </view>

      <!-- 角色化功能导航网格（电商宫格风格） -->
      <view class="nav-section">
        <view class="sec-header">
          <text class="sec-title">快捷功能</text>
        </view>
        <view class="nav-grid">
          <view
            v-for="entry in navEntries"
            :key="entry.key"
            class="nav-cell u-hover"
            @click="entry.action"
          >
            <view class="nav-icon-wrap" :class="`nav-ic-${entry.tone}`">
              <AppIcon :name="entry.icon" size="md" />
            </view>
            <text class="nav-name">{{ entry.label }}</text>
            <text class="nav-desc" v-if="entry.desc">{{ entry.desc }}</text>
          </view>
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
import { consumePendingInvitation } from '../../utils/invitation';
import { orderList } from '../../api/order';
import { approvalCounts } from '../../api/approval';

/**
 * tabBar 首页：电商风格重设计。
 * - 深色品牌渐变头部 + 圆形头像环 + 搜索栏
 * - 横向滚动动态数据卡片（角色化：待办/报告/匹配数等）
 * - 宫格式功能导航（按角色差异化 4~8 项）
 */
const store = useUserStore();

const partnerCount = ref(0);
const orderCount = ref(0);
const approvalTotal = ref(0);

/** 展示名称截断 */
const shortName = computed(() => {
  const name = (store.profile && store.profile.contactName) || (store.user && store.user.name);
  if (!name) return '用户';
  return name.length > 6 ? name.slice(0, 6) + '…' : name;
});

/** 头像字符 */
const avatarChar = computed(() => (shortName.value || '用')[0]);

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
    cards.push({ key: 'product', label: '我的产品', value: partnerCount.value || 0, icon: 'bank', tone: 'blue', action: onProduct, extra: '点击管理' });
    return cards;
  }

  if (store.isStaff) {
    const isManager = ['deptmgr', 'boss', 'operator', 'super'].includes(store.role);
    cards.push({ key: 'match', label: '匹配任务', value: '—', icon: 'match', tone: 'blue', action: onMatch, extra: '发起匹配' });
    cards.push({ key: 'order', label: '服务工单', value: orderCount.value || 0, icon: 'order', tone: 'gold', action: onOrder, extra: orderCount.value ? '待处理' : '暂无' });
    if (isManager) {
      cards.push({ key: 'approval', label: '待审批', value: approvalTotal.value || 0, icon: 'check', tone: 'red', action: onApproval, extra: approvalTotal.value ? '待处理' : '已清' });
    }
    cards.push({ key: 'report', label: '客户报告', value: '—', icon: 'chart', tone: 'green', action: onReport, extra: '查看' });
    return cards;
  }

  // 客户
  cards.push({ key: 'match', label: '智能匹配', value: '—', icon: 'match', tone: 'blue', action: onMatch, extra: '发起匹配' });
  cards.push({ key: 'report', label: '我的报告', value: '—', icon: 'chart', tone: 'gold', action: onReport, extra: '查看历史' });
  cards.push({ key: 'order', label: '服务单', value: orderCount.value || 0, icon: 'order', tone: 'green', action: onOrder, extra: orderCount.value ? '跟进中' : '暂无' });
  return cards;
});

/* ===== 宫格功能导航（角色化） ===== */
const navEntries = computed(() => {
  if (store.isChannel) {
    return [
      { key: 'product', label: '我的产品', icon: 'bank', tone: 'blue', desc: '录入·审批', action: onProduct },
      { key: 'client', label: '录入客户', icon: 'users', tone: 'gold', desc: '线索录入', action: onClient },
      { key: 'order', label: '服务工单', icon: 'order', tone: 'green', desc: '跟进记录', action: onOrder },
      { key: 'mine', label: '我的', icon: 'mine', tone: 'gray', desc: '账户设置', action: onMine },
    ];
  }

  if (store.isStaff) {
    const isManager = ['deptmgr', 'boss', 'operator', 'super'].includes(store.role);
    const entries = [
      { key: 'match', label: '智能匹配', icon: 'match', tone: 'blue', desc: '替客匹配', action: onMatch },
      { key: 'report', label: store.role === 'adviser' ? '客户报告' : '报告中心', icon: 'chart', tone: 'gold', desc: '匹配报告', action: onReport },
      { key: 'order', label: store.role === 'adviser' ? '客户工单' : '工单中心', icon: 'order', tone: 'green', desc: '服务跟进', action: onOrder },
      { key: 'client', label: '客户档案', icon: 'users', tone: 'red', desc: '客户管理', action: onClient },
    ];
    if (isManager) {
      entries.push({ key: 'approval', label: '审批中心', icon: 'check', tone: 'red', desc: `${approvalTotal.value || 0} 待审`, action: onApproval });
    }
    entries.push({ key: 'mine', label: '我的', icon: 'mine', tone: 'gray', desc: '账户设置', action: onMine });
    return entries;
  }

  // 客户
  return [
    { key: 'match', label: '智能匹配', icon: 'match', tone: 'blue', desc: '获取评级', action: onMatch },
    { key: 'report', label: '我的报告', icon: 'chart', tone: 'gold', desc: '历史记录', action: onReport },
    { key: 'order', label: '服务单', icon: 'order', tone: 'green', desc: '进度跟进', action: onOrder },
    { key: 'mine', label: '我的', icon: 'mine', tone: 'gray', desc: '账户设置', action: onMine },
  ];
});

onShow(() => {
  store.init().then((ok) => {
    if (ok) consumePendingInvitation(store);
  }).catch(() => {});
  loadPartnerCount();
  loadOrderCount();
  loadApprovalCount();
});

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
  if (!['deptmgr', 'boss', 'operator', 'super'].includes(store.role)) return;
  try {
    const c = await approvalCounts();
    const total = (c && typeof c.TOTAL === 'number') ? c.TOTAL : ((c && c.ALLOCATION) || 0);
    approvalTotal.value = total;
  } catch (e) {
    approvalTotal.value = 0;
  }
}

function onSearch() {
  // 渠道搜索产品；员工搜索客户；客户搜索报告
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
    // 员工暂无独立客户档案页，引导到匹配页的目标企业录入
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
.top-banner{
  position:relative;
  margin:0;
  padding:48rpx 40rpx 36rpx;
  background:linear-gradient(145deg,var(--brand-deep),var(--brand-mid),var(--brand-bright));
  overflow:hidden
}
.banner-decor{
  position:absolute;
  width:280rpx;
  height:280rpx;
  right:-60rpx;
  top:-80rpx;
  border-radius:50%;
  background:rgba(255,255,255,.06)
}
.banner-inner{
  position:relative;
  z-index:1
}
.user-row{
  display:flex;
  align-items:center;
  gap:24rpx
}
.avatar-ring{
  width:88rpx;
  height:88rpx;
  border-radius:50%;
  background:rgba(255,255,255,.15);
  border:2rpx solid rgba(255,255,255,.3);
  display:flex;
  align-items:center;
  justify-content:center;
  flex-shrink:0
}
.avatar-text{
  color:var(--bg-card);
  font-size:var(--fs-xl);
  font-weight:700;
  line-height:88rpx
}
.greeting-col{
  display:flex;
  flex-direction:column;
  flex:1;
  min-width:0
}
.banner-hello{
  color:var(--bg-card);
  font-size:var(--fs-xl);
  font-weight:700
}
.banner-time{
  margin-top:8rpx;
  color:rgba(255,255,255,.55);
  font-size:23rpx
}
/* 电商风格搜索栏 */
.search-bar{
  display:flex;
  align-items:center;
  gap:12rpx;
  margin-top:28rpx;
  height:72rpx;
  padding:0 24rpx;
  background:var(--bg-card);
  border-radius:var(--radius-full);
  box-shadow:var(--shadow-md)
}
.search-bar:active{
  opacity:.92;
  transform:scale(.99)
}
.search-ph{
  font-size:26rpx;
  color:var(--text-secondary)
}
.auth-pill{
  display:inline-flex;
  align-items:center;
  gap:8rpx;
  margin-top:20rpx;
  padding:10rpx 22rpx;
  border-radius: var(--radius-lg);
  align-self:flex-start;
  background:rgba(255,255,255,.1);
  border:1rpx solid rgba(255,255,255,.3);
  color:var(--bg-card);
  transition:background .2s,transform .1s
}
.auth-pill:active{
  background:rgba(255,255,255,.18);
  transform:scale(.97)
}
.pill-icon{
  font-size:22rpx;
  font-weight:700;
  opacity:.9
}
.pill-text{
  font-size:24rpx;
  font-weight:500
}
.pill-ok{
  background:rgba(16,185,129,.22);
  border-color:rgba(16,185,129,.5);
  color:#a7f3d0
}
.pill-todo{
  background:rgba(254,230,138,.12);
  border-color:rgba(254,230,138,.4);
  color:var(--warning-line)
}
.content{
  padding:0 32rpx 32rpx;
  margin-top:24rpx;
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
  margin-bottom:24rpx
}
.sec-title{
  font-size:var(--fs-lg);
  font-weight:700;
  color:var(--text-primary)
}
.sec-sub{
  font-size:22rpx;
  color:var(--text-secondary)
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
.promo-icon-wrap{
  width:64rpx;
  height:64rpx;
  border-radius:18rpx;
  background:var(--warning);
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
  font-size:28rpx;
  font-weight:600;
  color:var(--gold-text)
}
.promo-desc{
  margin-top:4rpx;
  font-size:23rpx;
  color:var(--warning-text)
}
.promo-arrow{
  font-size:40rpx;
  color:var(--warning-text);
  flex-shrink:0
}
/* ===== 动态数据卡片（横向滚动，电商风格） ===== */
.stat-section{
  margin-bottom:32rpx
}
.stat-scroll{
  width:100%;
  white-space:nowrap
}
.stat-track{
  display:flex;
  gap:20rpx;
  padding:4rpx 0
}
.stat-card{
  display:inline-flex;
  flex-direction:column;
  align-items:center;
  justify-content:center;
  width:200rpx;
  min-width:200rpx;
  padding:28rpx 16rpx;
  background:var(--bg-card);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-md);
  text-align:center;
  position:relative;
  overflow:hidden
}
.stat-card:active{
  transform:scale(.97)
}
.stat-icon-wrap{
  width:56rpx;
  height:56rpx;
  border-radius:50%;
  display:flex;
  align-items:center;
  justify-content:center;
  margin-bottom:12rpx
}
.stat-num{
  font-size:var(--fs-2xl);
  font-weight:800;
  color:var(--text-primary);
  line-height:1.1
}
.stat-name{
  margin-top:6rpx;
  font-size:23rpx;
  color:var(--text-secondary)
}
.stat-extra{
  margin-top:4rpx;
  font-size:var(--fs-xxs);
  color:var(--text-placeholder)
}
/* 色调（引用设计令牌，禁止裸色值） */
.stat-blue .stat-icon-wrap{ background:rgba(11,29,58,.08); color:var(--brand-deep) }
.stat-gold .stat-icon-wrap{ background:var(--gold-bg); color:var(--gold-text) }
.stat-green .stat-icon-wrap{ background:var(--success-bg); color:var(--success-text) }
.stat-red .stat-icon-wrap{ background:rgba(239,68,68,.08); color:var(--danger-text) }
.stat-blue::before, .stat-gold::before, .stat-green::before, .stat-red::before{
  content:'';
  position:absolute;
  top:0;
  left:0;
  right:0;
  height:6rpx
}
.stat-blue::before{ background:var(--brand-deep) }
.stat-gold::before{ background:var(--gold) }
.stat-green::before{ background:var(--success) }
.stat-red::before{ background:var(--danger) }

/* ===== 宫格功能导航（电商风格） ===== */
.nav-section{
  margin-bottom:32rpx
}
.nav-grid{
  display:grid;
  grid-template-columns:repeat(4,1fr);
  gap:20rpx 0;
  background:var(--bg-card);
  border-radius:var(--radius-md);
  padding:24rpx 16rpx;
  box-shadow:var(--shadow-md)
}
.nav-cell{
  display:flex;
  flex-direction:column;
  align-items:center;
  padding:12rpx 4rpx
}
.nav-cell:active{
  transform:scale(.94)
}
.nav-icon-wrap{
  width:80rpx;
  height:80rpx;
  border-radius:24rpx;
  display:flex;
  align-items:center;
  justify-content:center;
  margin-bottom:12rpx
}
.nav-name{
  font-size:24rpx;
  font-weight:600;
  color:var(--text-primary)
}
.nav-desc{
  margin-top:4rpx;
  font-size:var(--fs-xxs);
  color:var(--text-placeholder)
}
/* 宫格图标色调（引用设计令牌） */
.nav-ic-blue{ background:rgba(11,29,58,.08); color:var(--brand-deep) }
.nav-ic-gold{ background:var(--gold-bg); color:var(--gold-text) }
.nav-ic-green{ background:var(--success-bg); color:var(--success-text) }
.nav-ic-red{ background:rgba(239,68,68,.08); color:var(--danger-text) }
.nav-ic-gray{ background:var(--bg-input); color:var(--text-secondary) }

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
