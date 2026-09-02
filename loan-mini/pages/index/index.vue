<template>
  <view class="index-page" :class="{ 'u-shell': store.isTablet }">
    <!-- 品牌英雄区：全宽深色渐变 + 几何装饰 -->
    <view class="hero">
      <view class="hero-decor decor-1" />
      <view class="hero-decor decor-2" />
      <view class="hero-content">
        <view class="brand-badge">
          <text class="badge-text">企融通</text>
        </view>
        <text class="hero-title">企业融资服务平台</text>
        <text class="hero-sub">多银行产品智能匹配 · 经营数据驱动准入分析</text>
      </view>
    </view>

    <!-- 主内容区 -->
    <view class="main-body">
      <!-- 三步流程：横向时间线风格 -->
      <view class="card flow-card">
        <text class="card-label">三步开启贷款咨询</text>
        <view class="timeline">
          <view v-for="(s, i) in flow" :key="s.title" class="tl-step">
            <view class="tl-node">
              <view class="tl-dot" :class="`dot-${i + 1}`">
                <text class="tl-num">{{ i + 1 }}</text>
              </view>
              <view v-if="i < flow.length - 1" class="tl-line" />
            </view>
            <view class="tl-body">
              <text class="tl-title">{{ s.title }}</text>
              <text class="tl-desc">{{ s.desc }}</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 主 CTA -->
      <button class="cta-btn" :loading="loggingIn" :disabled="loggingIn" @click="onStart">
        <AppIcon name="wechat" size="md" />
        <text class="cta-text">{{ loggingIn ? '正在登录…' : '微信一键登录' }}</text>
      </button>

      <!-- 合规声明 -->
      <text class="foot-note">合规声明：匹配程度分析不构成任何银行通过承诺</text>

      <!-- H5 预览模式提示（仅 H5 浏览器显示，小程序端不渲染） -->
      <text class="h5-note" v-if="isH5">H5 预览模式：采用模拟登录（后端 wechat.mock），仅供本地联调</text>

      <!-- 开发模式：角色切换（仅开发环境显示） -->
      <view class="dev-panel u-hover" v-if="showDevPanel" @click.stop="toggleDevRoles">
        <text class="dev-tag">DEV</text>
        <text class="dev-hint">当前：{{ devRoleLabel }} · 点击切换角色</text>
      </view>

      <!-- 角色选择浮层 -->
      <view class="dev-overlay" v-if="showRolePicker" @click="showRolePicker = false">
        <view class="role-picker" @click.stop>
          <text class="picker-title">切换身份（开发模式）</text>
          <view
            v-for="r in devRoles"
            :key="r.code"
            class="role-item u-hover"
            :class="{ active: devRole === r.code }"
            @click="pickRole(r)"
          >
            <view class="role-dot" />
            <view class="role-info">
              <text class="role-name">{{ r.name }}</text>
              <text class="role-desc">{{ r.desc }}</text>
            </view>
            <text class="role-check" v-if="devRole === r.code">✓</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import { wxLogin, isH5Env } from '../../utils/wx';
import { loginByWx, loginByCrm } from '../../api/auth';
import { useUserStore } from '../../store/user';
import {
  captureInvitation, clearPendingInviteCode, consumePendingInvitation, getPendingInviteCode,
} from '../../utils/invitation';

/**
 * 落地页（P0-1/P0-2）：微信登录 + 邀请码绑定。
 * 设计语言：瑞幸风格 —— 深色品牌主调、大圆角阴影卡片、横向时间线、去模板味。
 *
 * - onLoad 读取扫码带参 query.inviteCode 自动填充
 * - 已持有有效 token 直接进入 tabBar 首页
 * - 主按钮：wx.login → 登录（带邀请码）→ 存 token → 跳首页
 * - 开发模式：长按 DEV 标签可切换角色（customer/staff/admin/boss）
 */
const store = useUserStore();

/** H5 浏览器环境标识（wxLogin 在 H5 返回模拟 code，靠后端 wechat.mock 打通登录） */
const isH5 = computed(() => isH5Env());

const loggingIn = ref(false);

const flow = [
  { title: '微信一键登录', desc: '授权获取 openid，自动创建客户档案' },
  { title: '身份认证', desc: '企业营业执照 / 个人实名认证（二选一）' },
  { title: '智能匹配', desc: '提交经营事实，获取匹配评级与报告' },
];

/* ---------- 开发模式角色切换 ---------- */
// 仅非生产构建显示（dev:xxx NODE_ENV=development；build NODE_ENV=production 自动隐藏，防生产泄漏）
const showDevPanel = ref(typeof process !== 'undefined' && process.env.NODE_ENV !== 'production');
const showRolePicker = ref(false);
const devRole = ref('customer');

const devRoles = [
  { code: 'customer', name: '客户（对客）', desc: '小程序端普通客户，仅看通过银行数+评级' },
  { code: 'staff', name: '渠道顾问', desc: 'STAFF 角色，可管理引荐客户与服务单' },
  { code: 'admin', name: '运营管理员', desc: 'OPERATOR 角色，可配置渠道与策略' },
  { code: 'boss', name: '超级管理员', desc: 'BOSS 角色，全部权限' },
];

// 角色 → CRM 测试账号（与后端 dev 环境预置账号对应）
const devCrmUsers = {
  customer: null,              // 客户走微信登录，不适用 CRM 切换
  staff: 'crm-adv-001',
  admin: 'crm-op-001',
  boss: 'crm-boss-001',
};

const devRoleLabel = computed(() => {
  const found = devRoles.find(r => r.code === devRole.value);
  return found ? found.name : '未知';
});

function toggleDevRoles() {
  showRolePicker.value = !showRolePicker.value;
}

function pickRole(r) {
  devRole.value = r.code;
  showRolePicker.value = false;
  const crmUserId = devCrmUsers[r.code];
  if (!crmUserId) {
    // 客户：清除登录态回到落地页，走微信登录
    store.clear();
    uni.showToast({ title: '已切回客户，请微信登录', icon: 'none' });
    setTimeout(() => { try { uni.reLaunch({ url: '/pages/index/index' }); } catch (e) { location.reload(); } }, 400);
    return;
  }
  // dev 角色真换 token：按角色调不同 CRM 账号登录
  uni.showLoading({ title: `切换为${r.name}...` });
  loginByCrm(crmUserId)
    .then((data) => {
      store.setToken(data.token);
      store.setUser(data.user);
      try { uni.setStorageSync('loan_dev_role', r.code); } catch (e) { /* ignore */ }
      uni.hideLoading();
      uni.showToast({ title: `已切换为：${r.name}`, icon: 'success' });
      // 重新加载让新 token 生效 + 跳到 tabBar 首页
      setTimeout(() => {
        try { uni.reLaunch({ url: '/pages/home/home' }); } catch (e) { location.reload(); }
      }, 600);
    })
    .catch((e) => {
      uni.hideLoading();
      uni.showToast({ title: `切换失败：${e.message || '请检查后端'}`, icon: 'none' });
    });
}
/* ---------- 角色切换结束 ---------- */

onLoad(async (query) => {
  captureInvitation(query);
  // 恢复上次选择的开发角色
  try {
    const saved = uni.getStorageSync('loan_dev_role');
    if (saved) devRole.value = saved;
  } catch (e) { /* ignore */ }
  // 已登录则直接进入首页
  const ok = await store.init();
  if (ok) {
    await consumePendingInvitation(store);
    jumpHome();
  }
});

/** 跳转首页（已移除原生 tabBar，switchTab 不可用，改用 reLaunch） */
function jumpHome() {
  uni.reLaunch({ url: '/pages/home/home' });
}

async function doLogin() {
  if (loggingIn.value) return;
  loggingIn.value = true;
  try {
    const code = await wxLogin();
    const pendingInviteCode = getPendingInviteCode();
    const data = await loginByWx(code, {
      inviteCode: pendingInviteCode || undefined,
    });
    if (pendingInviteCode) clearPendingInviteCode();
    store.setToken(data.token);
    store.setUser(data.user);
    await store.refreshProfile().catch(() => {});
    jumpHome();
  } catch (e) {
    console.error('[login]', e);
    if (!isH5 && e && e.stage === 'wxLogin') {
      // 仅小程序端可能「取不到 wx code」（用户拒绝授权等）；
      // H5 下 wxLogin 直接返回模拟 code，不会走到该分支。
      uni.showToast({ title: '微信授权失败，请重试', icon: 'none', duration: 2500 });
    } else if (isH5) {
      // H5 预览登录失败（多为后端未开启 wechat.mock 或网络不可达）：
      // 自动展开角色入口，引导用 CRM 账号进入，避免卡死在落地页。
      showRolePicker.value = true;
      uni.showToast({
        title: 'H5 预览登录失败，请选择角色进入',
        icon: 'none',
        duration: 2500,
      });
    } else {
      uni.showToast({ title: (e && e.message) || '登录失败', icon: 'none', duration: 2500 });
    }
  } finally {
    loggingIn.value = false;
  }
}

function onStart() {
  doLogin();
}

</script>

<style scoped>
.index-page{
  display:flex;
  flex-direction:column;
  min-height:100vh;
  background:var(--bg-page)
}
.hero{
  position:relative;
  margin:0;
  padding:100rpx 40rpx 60rpx;
  background:linear-gradient(145deg,var(--brand-deep),var(--brand-mid),var(--brand-bright));
  overflow:hidden
}
.hero-decor{
  position:absolute;
  border-radius:50%;
  opacity:.08
}
.decor-1{
  width:320rpx;
  height:320rpx;
  right:-80rpx;
  top:-100rpx;
  background:var(--bg-card)
}
.decor-2{
  width:200rpx;
  height:200rpx;
  left:-40rpx;
  bottom:-60rpx;
  background:var(--gold)
}
.hero-content{
  position:relative;
  z-index:1;
  display:flex;
  flex-direction:column;
  align-items:center
}
.brand-badge{
  padding:12rpx 32rpx;
  background:rgba(255,255,255,.12);
  border:1rpx solid rgba(255,255,255,.18);
  border-radius:40rpx;
  margin-bottom:28rpx
}
.badge-text{
  font-size:22rpx;
  color:rgba(255,255,255,.85);
  letter-spacing:4rpx;
  font-weight:500
}
.hero-title{
  font-size:44rpx;
  font-weight:800;
  color:var(--bg-card);
  letter-spacing:2rpx;
  line-height:1.3
}
.hero-sub{
  margin-top:16rpx;
  font-size:26rpx;
  color:rgba(255,255,255,.65);
  line-height:1.5
}
.main-body{
  padding:0 32rpx 48rpx;
  margin-top:24rpx;
  position:relative;
  z-index:2
}
.card{
  background:var(--bg-card);
  border-radius: var(--radius-md);
  padding:36rpx 32rpx;
  margin-bottom:24rpx;
  box-shadow: var(--shadow-md)
}
.card-label{
  font-size:30rpx;
  font-weight:700;
  color:var(--text-primary)
}
.timeline{
  display:flex;
  flex-direction:column;
  gap:0;
  margin-top:8rpx
}
.tl-step{
  display:flex;
  align-items:flex-start;
  gap:24rpx;
  position:relative
}
.tl-node{
  display:flex;
  flex-direction:column;
  align-items:center;
  flex-shrink:0;
  width:48rpx
}
.tl-dot{
  width:48rpx;
  height:48rpx;
  border-radius:50%;
  display:flex;
  align-items:center;
  justify-content:center;
  flex-shrink:0
}
.dot-1{
  background:var(--brand-deep)
}
.dot-2{
  background:var(--brand-bright)
}
.dot-3{
  background:var(--gold)
}
.tl-num{
  color:var(--bg-card);
  font-size:24rpx;
  font-weight:700
}
.tl-line{
  width:2rpx;
  flex:1;
  min-height:32rpx;
  background:var(--line);
  margin:8rpx 0
}
.tl-body{
  display:flex;
  flex-direction:column;
  padding-top:8rpx;
  padding-bottom:16rpx;
  border-bottom:1rpx solid var(--line)
}
.tl-step:last-child .tl-body{
  border-bottom:none;
  padding-bottom:0
}
.tl-step:last-child .tl-line{
  display:none
}
.tl-title{
  font-size:29rpx;
  font-weight:600;
  color:var(--text-primary)
}
.tl-desc{
  margin-top:6rpx;
  font-size:24rpx;
  color:var(--text-secondary);
  line-height:1.5
}
.cta-btn{
  margin-top:8rpx;
  height:96rpx;
  line-height:96rpx;
  background:var(--brand-deep);
  color:var(--bg-card);
  font-size:32rpx;
  font-weight:700;
  border-radius:20rpx;
  display:flex;
  align-items:center;
  justify-content:center;
  gap:12rpx;
  box-shadow:0 8rpx 24rpx rgba(11,29,58,.3)
}
.cta-btn:after{
  border:none
}
.cta-icon{
  font-size:36rpx
}
.cta-text{
  letter-spacing:2rpx
}
.foot-note{
  display:block;
  margin-top:28rpx;
  text-align:center;
  font-size:22rpx;
  color:var(--text-secondary);
  line-height:1.6
}
.h5-note{
  display:block;
  margin-top:12rpx;
  text-align:center;
  font-size:22rpx;
  color:var(--info-text);
  background:var(--warning-bg);
  border:1rpx solid var(--warning-line);
  border-radius:var(--radius-sm);
  padding:12rpx 20rpx;
  line-height:1.5
}
.dev-panel{
  display:flex;
  align-items:center;
  gap:12rpx;
  margin-top:32rpx;
  padding:20rpx 24rpx;
  background:var(--warning-bg);
  border:1rpx solid var(--warning-line);
  border-radius: var(--radius-sm)
}
.dev-tag{
  padding:4rpx 14rpx;
  background:var(--warning);
  color:var(--bg-card);
  font-size:22rpx;
  font-weight:800;
  border-radius:8rpx;
  letter-spacing:2rpx
}
.dev-hint{
  font-size:22rpx;
  color:var(--warning-text)
}
.dev-overlay{
  position:fixed;
  top:0;
  left:0;
  right:0;
  bottom:0;
  background:rgba(0,0,0,.45);
  z-index:999;
  display:flex;
  align-items:flex-end;
  justify-content:center
}
.role-picker{
  width:100%;
  background:var(--bg-card);
  border-radius: var(--radius-lg) 32rpx 0 0;
  padding:40rpx 32rpx 60rpx;
  animation:slideUp-a55e80fe .25s ease-out
}
@keyframes slideUp-a55e80fe{
0%{
  transform:translateY(100%)
}
to{
  transform:translateY(0)
}
}
.picker-title{
  display:block;
  font-size:34rpx;
  font-weight:700;
  color:var(--text-primary);
  margin-bottom:32rpx;
  text-align:center
}
.role-item{
  display:flex;
  align-items:center;
  gap:20rpx;
  padding:28rpx 0;
  border-bottom:1rpx solid var(--line)
}
.role-item:last-child{
  border-bottom:none
}
.role-item.active .role-dot{
  background:var(--brand-deep);
  box-shadow:0 0 0 6rpx rgba(11,29,58,.15)
}
.role-dot{
  width:20rpx;
  height:20rpx;
  border-radius:50%;
  background:var(--line);
  flex-shrink:0;
  transition:all .2s
}
.role-info{
  flex:1;
  display:flex;
  flex-direction:column
}
.role-name{
  font-size:30rpx;
  font-weight:600;
  color:var(--text-primary)
}
.role-desc{
  font-size:23rpx;
  color:var(--text-secondary);
  margin-top:4rpx
}
.role-check{
  color:var(--brand-deep);
  font-size:32rpx;
  font-weight:700
}
</style>
