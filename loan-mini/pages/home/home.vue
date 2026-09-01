<template>
  <view class="home-page" :class="{ 'u-shell': store.isTablet }">
    <!-- 顶部欢迎区：深色品牌渐变 -->
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

      <!-- 功能入口：双列大卡片（渠道：我的产品 / 我的；其余：智能匹配 / 我的报告） -->
      <view class="entry-row">
        <template v-if="store.isChannel">
          <view class="entry-card u-hover" @click="onProduct">
            <view class="entry-top">
              <AppIcon name="bank" size="lg" />
            </view>
            <text class="entry-name">我的产品</text>
            <text class="entry-desc">录入 · 审批 · 上下架</text>
          </view>
          <view class="entry-card u-hover" @click="onMine">
            <view class="entry-top">
              <AppIcon name="mine" size="lg" />
            </view>
            <text class="entry-name">我的</text>
            <text class="entry-desc">账户与资料设置</text>
          </view>
        </template>
        <template v-else>
          <view class="entry-card u-hover" @click="onMatch">
            <view class="entry-top">
              <AppIcon name="match" size="lg" />
            </view>
            <text class="entry-name">智能匹配</text>
            <text class="entry-desc">提交经营事实获取评级</text>
          </view>
          <view class="entry-card u-hover" @click="onReport">
            <view class="entry-top">
              <AppIcon name="chart" size="lg" />
            </view>
            <text class="entry-name">我的报告</text>
            <text class="entry-desc">查看历史匹配与评级</text>
          </view>
        </template>
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
import { partnerProducts } from '../../api/match';
import { consumePendingInvitation } from '../../utils/invitation';

/**
 * tabBar 首页：瑞幸风格重设计。
 * - 深色品牌渐变头部 + 圆形头像环
 * - 阴影大圆角卡片（无边框）
 * - 双列功能入口（统一使用 AppIcon 线性图标）
 */
const store = useUserStore();

const partnerCount = ref(0);

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

onShow(() => {
  store.init().then((ok) => {
    if (ok) consumePendingInvitation(store);
  }).catch(() => {});
  loadPartnerCount();
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

function onGoAuth() {
  uni.navigateTo({ url: '/pages/auth/auth' });
}

function onMatch() {
  if (!store.isStaff && !store.isAuthed) {
    uni.showToast({ title: '请先完成身份认证', icon: 'none' });
    onGoAuth();
    return;
  }
  // 已移除原生 tabBar，switchTab 不可用，改用 reLaunch
  uni.reLaunch({ url: '/pages/match/match' });
}

function onReport() {
  uni.reLaunch({ url: '/pages/report/list' });
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
  background:var(--bg-page);
  box-sizing:border-box
}
.top-banner{
  position:relative;
  margin:0;
  padding:56rpx 40rpx 32rpx;
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
  width:96rpx;
  height:96rpx;
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
  font-size:40rpx;
  font-weight:700;
  line-height:96rpx
}
.greeting-col{
  display:flex;
  flex-direction:column
}
.banner-hello{
  color:var(--bg-card);
  font-size:36rpx;
  font-weight:700
}
.banner-time{
  margin-top:8rpx;
  color:rgba(255,255,255,.55);
  font-size:23rpx
}
.auth-pill{
  display:inline-flex;
  align-items:center;
  gap:8rpx;
  margin-top:24rpx;
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
  font-size:31rpx;
  font-weight:700;
  color:var(--text-primary)
}
.sec-badge{
  font-size:22rpx;
  font-weight:600;
  padding:6rpx 18rpx;
  border-radius:20rpx
}
.badge-on{
  background:var(--success-bg);
  color:var(--success-text)
}
.badge-off{
  background:var(--bg-input);
  color:var(--text-secondary)
}
.promo-card{
  display:flex;
  align-items:center;
  justify-content:space-between;
  padding:28rpx 32rpx;
  background:linear-gradient(135deg,var(--warning-bg),var(--warning-bg));
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
.promo-icon{
  font-size:32rpx
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
.entry-row{
  display:flex;
  gap:24rpx;
  margin-bottom:32rpx
}
.entry-card{
  flex:1;
  background:var(--bg-card);
  border-radius: var(--radius-md);
  padding:32rpx 24rpx;
  display:flex;
  flex-direction:column;
  box-shadow: var(--shadow-md)
}
.entry-top{
  margin-bottom:20rpx
}
.entry-emoji{
  font-size:52rpx
}
.entry-name{
  font-size:29rpx;
  font-weight:700;
  color:var(--text-primary)
}
.entry-desc{
  margin-top:8rpx;
  font-size:23rpx;
  color:var(--text-secondary)
}
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
.tip-num{
  color:var(--brand-deep);
  font-weight:700
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
