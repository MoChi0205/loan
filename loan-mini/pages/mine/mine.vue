<template>
  <view class="mine-page" :class="{ 'u-shell': store.isTablet }">
    <!-- 用户档案头：深色品牌渐变 -->
    <view class="profile-header">
      <view class="ph-decor" />
      <view class="ph-inner">
        <view class="ph-top">
          <view class="avatar-ring">
            <text class="avatar-char">{{ avatarChar }}</text>
          </view>
          <view class="name-col">
            <text class="profile-name">{{ displayName }}</text>
            <text class="profile-phone">{{ phoneDisplay }}</text>
          </view>
          <view class="auth-chip" :class="store.isAuthed ? 'chip-ok' : 'chip-todo'" @click="onGoAuth">
            {{ store.isAuthed ? '已认证' : '去认证' }}
          </view>
        </view>
      </view>
    </view>

    <view class="content">
      <!-- 档案摘要（C8 角色化账户）
           客户：实名状态 / 绑定手机号 / 性别 / 注册时间 / 邀请人（已移除客户编号——系统内部 ID 对用户无意义）
           渠道：所属银行 / 合作开始 / 银行联系人
           员工：部门名称 / 角色 / 入职时间，不展示内部编码 -->
      <view class="card">
        <text class="sec-title">{{ accountTitle }}</text>
        <view class="info-list">
          <view v-for="(row, i) in accountRows" :key="i" class="info-row">
            <text class="info-label">{{ row.label }}</text>
            <text class="info-value">{{ row.value }}</text>
          </view>
        </view>
      </view>

      <!-- 顾问服务：仅真实 owner 归属，分享引荐人与顾问严格分离 -->
      <view v-if="!isChannelRole && !isStaffRole" class="card advisor-card">
        <view class="sec-header">
          <text class="sec-title">顾问服务</text>
          <text class="sec-badge" :class="hasAdvisor ? 'badge-on' : 'badge-off'">{{ hasAdvisor ? '服务中' : '待分配' }}</text>
        </view>
        <view class="advisor-row" v-if="hasAdvisor">
          <view class="advisor-avatar">{{ advisorName[0] || '顾' }}</view>
          <view class="advisor-info">
            <text class="advisor-name">{{ advisorName }}</text>
            <text class="advisor-role">专属贷款顾问 · 全程跟进</text>
          </view>
          <view class="status-led" />
        </view>
        <view class="bind-empty" v-else>
          <view class="advisor-empty-icon"><AppIcon name="support" size="md" /></view>
          <view class="advisor-empty-body">
            <text class="bind-empty-title">暂未分配服务顾问</text>
            <text class="bind-empty-text">平台管理员可为您分配，顾问也可从客户公海认领后提供服务</text>
          </view>
        </view>
      </view>

      <!-- 功能菜单 -->
      <view class="card menu-card u-hover" @click="goOrder">
        <view class="menu-left">
          <view class="menu-icon-wrap">
            <AppIcon name="list" size="md" />
          </view>
          <view class="menu-body">
            <text class="menu-title">我的服务单</text>
            <text class="menu-desc">{{ orderTip }}</text>
          </view>
        </view>
        <text class="menu-arrow">›</text>
      </view>

      <!-- 我的产品（C9）：渠道管理自有产品；员工可录入银行产品。客户无此入口 -->
      <view v-if="isChannelRole || isStaffRole" class="card menu-card" @click="goProduct">
        <view class="menu-left">
          <view class="menu-icon-wrap">
            <AppIcon name="bank" size="md" />
          </view>
          <view class="menu-body">
            <text class="menu-title">{{ isChannelRole ? '我的产品' : '银行产品' }}</text>
            <text class="menu-desc">
              {{ isChannelRole ? '录入 / 撤销审批 / 申请删除' : '录入产品，走运营终审上架' }}
            </text>
          </view>
        </view>
        <text class="menu-arrow">›</text>
      </view>

      <!-- 审批中心（C19）：仅运营/超管/老板可见 -->
      <view v-if="isApproverRole" class="card menu-card" @click="goApproval">
        <view class="menu-left">
          <view class="menu-icon-wrap">
            <AppIcon name="check" size="md" />
          </view>
          <view class="menu-body">
            <text class="menu-title">审批中心</text>
            <text class="menu-desc">无归宿客户分配申请 · 通过后归属流转</text>
          </view>
        </view>
        <view v-if="approvalTotal > 0" class="menu-badge">{{ approvalTotal }}</view>
        <text class="menu-arrow">›</text>
      </view>

      <!-- 分享邀请：分享链接自动携带邀请码，接收方无需手填 -->
      <view v-if="role === 'customer'" class="card share-card">
        <view class="sec-header">
          <text class="sec-title">分享邀请</text>
          <text class="sec-sub">推荐有礼 · 7 天有效</text>
        </view>
        <text class="share-desc">分享链接或小程序码会自动携带引荐码，对方在有效期内登录即可建立邀请关系。</text>
        <view class="share-actions" v-if="inviteCode">
          <!-- #ifdef MP-WEIXIN -->
          <button class="share-btn" open-type="share">
            <AppIcon name="share" size="sm" />
            <text>分享给好友</text>
          </button>
          <!-- #endif -->
          <!-- #ifdef H5 -->
          <button class="share-btn" @click="onCopyShareLink">
            <AppIcon name="share" size="sm" />
            <text>复制分享链接</text>
          </button>
          <!-- #endif -->
          <button class="copy-btn" size="mini" @click="onCopyCode">复制引荐码</button>
        </view>
        <view class="code-empty" v-else>
          <text class="code-empty-text">分享信息生成中…</text>
        </view>
      </view>

      <!-- 奖励汇总 -->
      <view class="card reward-card" v-if="role === 'customer' && summary">
        <view class="reward-grid">
          <view class="reward-item">
            <text class="reward-num">¥{{ summary.totalAmount || 0 }}</text>
            <text class="reward-name">累计奖励</text>
          </view>
          <view class="reward-divider" />
          <view class="reward-item">
            <text class="reward-num">{{ summary.grantedCount || 0 }}</text>
            <text class="reward-name">已到账</text>
          </view>
          <view class="reward-divider" />
          <view class="reward-item">
            <text class="reward-num">{{ summary.pendingCount || 0 }}</text>
            <text class="reward-name">待结算</text>
          </view>
        </view>
      </view>

      <button class="logout-btn" @click="onLogout">退出登录</button>
    </view>
  </view>

  <!-- 角色化底部导航（自绘 tabBar） -->
  <TabBar current="mine" />
</template>

<script setup>
import { ref, computed } from 'vue';
import { onShareAppMessage, onShareTimeline, onShow } from '@dcloudio/uni-app';
import { useUserStore } from '../../store/user';
import TabBar from '../../components/TabBar.vue';
import { mine as getMyInviteCode } from '../../api/invitation';
import { orderList, rewardSummary } from '../../api/order';
import { approvalCounts } from '../../api/approval';
import { buildInviteSharePath, consumePendingInvitation } from '../../utils/invitation';

const store = useUserStore();

const inviteCode = ref('');
const orderTip = ref('查看服务进度与跟进摘要');
const summary = ref(null);

const displayName = computed(() => {
  const name = (store.profile && store.profile.contactName) || (store.user && store.user.name);
  return name || '微信客户';
});

const avatarChar = computed(() => (displayName.value || '微')[0]);

const phoneDisplay = computed(() => {
  const p = (store.profile && store.profile.phone) || '';
  if (!p) return '未绑定手机号';
  if (p.length >= 7) return p.slice(0, 3) + '****' + p.slice(-4);
  return p;
});

/* ==================== C8 角色化账户信息 ==================== */

/** 角色判定 */
const role = computed(() => store.role || 'customer');
const isChannelRole = computed(() => role.value === 'channel');
const isStaffRole = computed(
  () => ['adviser', 'deptmgr', 'boss', 'operator', 'super'].indexOf(role.value) >= 0,
);
/** 审批中心可操作角色：D39 纳入部门经理；后端限制其仅审批本团队。 */
const isApproverRole = computed(
  () => ['deptmgr', 'boss', 'operator', 'super'].indexOf(role.value) >= 0,
);

/** 审批中心待审总数（角标，来自 approvalCounts 的 TOTAL） */
const approvalTotal = ref(0);

/** 档案卡标题 */
const accountTitle = computed(() => {
  if (isChannelRole.value) return '渠道信息';
  if (isStaffRole.value) return '员工信息';
  return '账户信息';
});

/**
 * 档案行（按角色差异化）。
 *
 * - 客户：实名状态 / 绑定手机号 / 性别 / 注册时间 / 邀请人（无客户编号）
 * - 渠道：所属银行 / 合作开始 / 银行联系人
 * - 员工：部门名称 / 角色 / 入职时间，业务编码只留在接口内部
 */
const accountRows = computed(() => {
  const p = store.profile || {};
  const u = store.user || {};

  if (isChannelRole.value) {
    return [
      { label: '所属银行', value: p.bankName || (u.deptName) || '—' },
      { label: '合作开始', value: p.cooperateStartAt || '—' },
      { label: '银行联系人', value: p.bankContact || u.name || '—' },
    ];
  }

  if (isStaffRole.value) {
    const roleLabel = {
      adviser: '顾问', deptmgr: '部门经理', boss: '老板',
      operator: '运营管理员', super: '超级管理员',
    }[role.value] || '员工';
    const rows = [];
    // 老板与超管无归属部门概念；其他员工只展示部门名称，不用编码兜底。
    if (role.value !== 'boss' && role.value !== 'super') {
      rows.push({ label: '部门', value: u.deptName || '—' });
    }
    rows.push({ label: '角色', value: roleLabel });
    rows.push({ label: '入职时间', value: u.hiredAt || '—' });
    return rows;
  }

  // 客户
  return [
    { label: '实名状态', value: store.isAuthed ? '已认证' : '未认证' },
    { label: '绑定手机号', value: phoneDisplay.value || '—' },
    { label: '性别', value: p.gender || '—' },
    { label: '注册时间', value: p.registeredAt || p.createdAt || '—' },
    { label: '邀请人', value: store.referrerName || (p.invitedFlag ? '受邀注册' : '自然注册') },
  ];
});

const hasAdvisor = computed(() => !!(store.profile && store.profile.ownerStaffName));
const advisorName = computed(() => (store.profile && store.profile.ownerStaffName) || '平台顾问');

onShow(() => {
  store.init().then((ok) => {
    if (ok) {
      consumePendingInvitation(store);
      // 邀请码 / 奖励汇总为客户专属接口：仅客户角色调用，员工/渠道不触发（避免报错 toast）
      if (store.role === 'customer') {
        loadInviteCode();
        loadSummary();
      }
      loadOrderTip();
      // 审批中心角标：仅审批角色拉取待审总数
      if (isApproverRole.value) loadApprovalCount();
    }
  }).catch(() => {});
});

/** 拉取审批中心待审总数（TOTAL），用于「我的」页菜单项角标 */
async function loadApprovalCount() {
  try {
    const c = await approvalCounts();
    const total = (c && typeof c.TOTAL === 'number') ? c.TOTAL : ((c && c.ALLOCATION) || 0);
    approvalTotal.value = total;
  } catch (e) {
    approvalTotal.value = 0;
  }
}

async function loadInviteCode() {
  try { inviteCode.value = await getMyInviteCode(); }
  catch (e) { inviteCode.value = ''; }
}

async function loadOrderTip() {
  try {
    const data = await orderList({ page: 1, size: 1 });
    const records = (data && data.records) || [];
    if (records.length && records[0].status) {
      orderTip.value = `${records[0].orderNo} · ${records[0].status}`;
    }
  } catch (e) { orderTip.value = '暂无服务单'; }
}

async function loadSummary() {
  try { summary.value = await rewardSummary(); }
  catch (e) { summary.value = null; }
}

function onCopyCode() {
  uni.setClipboardData({
    data: inviteCode.value,
    success: () => uni.showToast({ title: '引荐码已复制', icon: 'success' }),
  });
}

function onCopyShareLink() {
  const path = buildInviteSharePath(inviteCode.value);
  const origin = typeof window !== 'undefined' && window.location ? window.location.origin : '';
  uni.setClipboardData({
    data: `${origin}/#${path}`,
    success: () => uni.showToast({ title: '分享链接已复制', icon: 'success' }),
  });
}

onShareAppMessage(() => ({
  title: '企融通 · 企业融资智能匹配',
  path: buildInviteSharePath(inviteCode.value),
}));

onShareTimeline(() => ({
  title: '企融通 · 企业融资智能匹配',
  query: inviteCode.value ? `inviteCode=${encodeURIComponent(inviteCode.value)}` : '',
}));

function onGoAuth() {
  if (store.isAuthed) { uni.showToast({ title: '已完成身份认证', icon: 'none' }); return; }
  uni.navigateTo({ url: '/pages/auth/auth' });
}

function goOrder() { uni.reLaunch({ url: '/pages/order/list' }); }

/** C9：跳转我的产品（渠道 / 员工可见） */
function goProduct() {  uni.navigateTo({ url: '/pages/product/list' });
}

/** C19：审批中心入口（运营/超管/老板） */
function goApproval() {
  uni.navigateTo({ url: '/pages/approval/list' });
}

function onLogout() {
  uni.showModal({
    title: '退出登录',
    content: '确认退出当前账号？',
    success: (res) => {
      if (res.confirm) {
        store.clear();
        uni.reLaunch({ url: '/pages/index/index' });
      }
    },
  });
}
</script>

<style scoped>
.mine-page {
  min-height: 100vh;
  background: var(--bg-page);
  box-sizing: border-box;
  /* P2-7：底部留白 ≥ tabbar 高度 + 安全区，避免最后一项被遮挡 */
  padding-bottom: calc(var(--space-16) + env(safe-area-inset-bottom));
}

.profile-header {
  position: relative;
  /* .mine-page 无左右 padding，档案头本身已是通栏；负 margin 外扩会比页面宽 64rpx，
     造成横向溢出 + 头部内容比卡片内容左移，故改为 0 */
  margin: 0;
  /* 底部 padding 由 48rpx 收到 32rpx：原值是给 .content 负 margin 上叠预留的空间 */
  padding: 56rpx 40rpx 32rpx;
  background: linear-gradient(145deg, var(--brand-deep) 0%, var(--brand-mid) 50%, var(--brand-bright) 100%);
  overflow: hidden;
}

.ph-decor {
  position: absolute;
  width: 260rpx;
  height: 260rpx;
  right: -60rpx;
  top: -80rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.06);
}

.ph-inner { position: relative; z-index: 1; }

.ph-top {
  display: flex;
  align-items: center;
  gap: 24rpx;
}

.avatar-ring {
  width: 100rpx;
  height: 100rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.15);
  border: 2rpx solid rgba(255, 255, 255, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.avatar-char { color: var(--text-invert); font-size: 44rpx; font-weight: 700; }

.name-col { flex: 1; display: flex; flex-direction: column; }

.profile-name { color: var(--text-invert); font-size: 34rpx; font-weight: 700; }

.profile-phone { margin-top: 8rpx; color: rgba(255, 255, 255, 0.55); font-size: 24rpx; }

.auth-chip {
  padding: 10rpx 24rpx;
  border-radius: 32rpx;
  font-size: 23rpx;
  font-weight: 600;
  flex-shrink: 0;
}

.chip-ok { background: rgba(16, 185, 129, 0.2); color: var(--success); }
.chip-todo { background: var(--warning-line); color: var(--warning-text); }

.content {
  padding: 0 32rpx;
  /* 修复重叠：原 -30rpx 负 margin 把内容区上拉，白底卡片盖在深色档案头上。
     改为 +24rpx 回到正常文档流（对齐 index.vue .main-body 的修复） */
  margin-top: 24rpx;
  position: relative;
  z-index: 2;
  padding-bottom: 48rpx;
}

.card {
  background: var(--text-invert);
  border-radius: 24rpx;
  padding: 32rpx;
  /* 20rpx 小于阴影扩散半径（20rpx），相邻卡片阴影会连成一片，提到 32rpx */
  margin-bottom: 32rpx;
  box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.05);
}

.sec-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24rpx;
}

.sec-title { font-size: 31rpx; font-weight: 700; color: var(--text-primary); }

.sec-badge { font-size: 22rpx; font-weight: 600; padding: 6rpx 18rpx; border-radius: 20rpx; }
.badge-on { background: var(--success-bg); color: var(--success-text); }
.badge-off { background: var(--bg-input); color: var(--text-placeholder); }

.sec-sub { font-size: 22rpx; color: var(--text-placeholder); }

.info-list { display: flex; flex-direction: column; }

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 18rpx 0;
  border-bottom: 1rpx solid var(--bg-input);
}

.info-row:last-child { border-bottom: none; }
.info-label { font-size: 26rpx; color: var(--text-secondary); }
.info-value { font-size: 26rpx; color: var(--text-primary); font-weight: 500; text-align: right; max-width: 60%; word-break: break-all; }

.advisor-row { display: flex; align-items: center; gap: 20rpx; }

.advisor-avatar {
  width: 76rpx;
  height: 76rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--brand-deep), var(--brand-bright));
  color: var(--text-invert);
  font-size: 32rpx;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.advisor-info { flex: 1; display: flex; flex-direction: column; }
.advisor-name { font-size: 29rpx; font-weight: 600; color: var(--text-primary); }
.advisor-role { margin-top: 6rpx; font-size: 23rpx; color: var(--text-placeholder); }

.status-led { width: 14rpx; height: 14rpx; border-radius: 50%; background: var(--success); flex-shrink: 0; }

.bind-empty { display: flex; align-items: center; gap: var(--space-3); }
.advisor-empty-icon { width: 72rpx; height: 72rpx; border-radius: var(--radius-md); background: var(--gold-bg); color: var(--gold-text); display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.advisor-empty-body { display: flex; flex-direction: column; min-width: 0; }
.bind-empty-title { font-size: var(--fs-md); font-weight: 600; color: var(--text-primary); }
.bind-empty-text { margin-top: var(--space-1); font-size: var(--fs-sm); line-height: var(--lh-base); color: var(--text-secondary); }

.menu-card { display: flex; align-items: center; justify-content: space-between; }
.menu-left { display: flex; align-items: center; gap: 20rpx; }

.menu-icon-wrap {
  width: 72rpx;
  height: 72rpx;
  border-radius: 18rpx;
  background: linear-gradient(135deg, var(--bg-card), var(--bg-input));
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.menu-icon-emoji { font-size: 34rpx; }
.menu-body { display: flex; flex-direction: column; }
.menu-title { font-size: 29rpx; font-weight: 600; color: var(--text-primary); }
.menu-desc { margin-top: 6rpx; font-size: 23rpx; color: var(--text-placeholder); }
.menu-arrow { font-size: 40rpx; color: var(--line); }

.menu-badge {
  min-width: 32rpx;
  height: 32rpx;
  padding: 0 8rpx;
  border-radius: 16rpx;
  background: var(--danger);
  color: #fff;
  font-size: 20rpx;
  font-weight: 700;
  line-height: 32rpx;
  text-align: center;
  margin-right: 12rpx;
  flex-shrink: 0;
}

.share-desc { display: block; color: var(--text-secondary); font-size: var(--fs-sm); line-height: var(--lh-base); }
.share-actions { display: flex; align-items: center; gap: var(--space-2); margin-top: var(--space-3); }
.share-btn { flex: 1; height: 72rpx; padding: 0 var(--space-3); border-radius: var(--radius-sm); background: var(--brand-deep); color: var(--text-invert); display: flex; align-items: center; justify-content: center; gap: var(--space-1); font-size: var(--fs-sm); font-weight: 600; }

.copy-btn {
  margin: 0;
  background: var(--bg-input);
  color: var(--brand-deep);
  font-size: 25rpx;
  font-weight: 600;
  padding: 0 28rpx;
  height: 72rpx;
  line-height: 72rpx;
  border-radius: 14rpx;
}

.copy-btn::after { border: none; }
.code-empty-text { font-size: 25rpx; color: var(--text-placeholder); }

.reward-card { padding: 28rpx 0; }
.reward-grid { display: flex; align-items: center; }
.reward-item { flex: 1; display: flex; flex-direction: column; align-items: center; }
.reward-num { font-size: 34rpx; font-weight: 800; color: var(--text-primary); }
.reward-name { margin-top: 10rpx; font-size: 22rpx; color: var(--text-placeholder); }
.reward-divider { width: 2rpx; height: 56rpx; background: var(--line); }

.logout-btn {
  /* 与上方卡片保持同一节奏（原 12rpx 靠 margin 折叠取 max，语义不清且过窄） */
  margin-top: 32rpx;
  height: 92rpx;
  line-height: 92rpx;
  background: var(--text-invert);
  color: var(--danger);
  font-size: 30rpx;
  font-weight: 600;
  border-radius: 20rpx;
  box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.05);
}

.logout-btn::after { border: none; }
</style>
