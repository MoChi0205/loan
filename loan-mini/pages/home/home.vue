<template>
  <view class="home-page theme-root" :class="{ 'u-shell': store.isTablet }" :data-theme="themeMode">
    <view class="m-scroll">
      <!-- ===== 深色墨蓝 Hero（原型 .m-head）===== -->
      <view class="m-head">
        <view class="m-head-user">
          <!-- #ifdef MP-WEIXIN -->
          <button class="m-head-avatar" open-type="chooseAvatar" @chooseavatar="onChooseAvatar">
            <image v-if="avatarUrl" :src="avatarUrl" class="avatar-img" mode="aspectFill" />
            <text v-else class="avatar-char">{{ avatarChar }}</text>
          </button>
          <!-- #endif -->
          <!-- #ifndef MP-WEIXIN -->
          <view class="m-head-avatar" @click="onChooseAvatarClick">
            <image v-if="avatarUrl" :src="avatarUrl" class="avatar-img" mode="aspectFill" />
            <text v-else class="avatar-char">{{ avatarChar }}</text>
          </view>
          <!-- #endif -->
          <view class="head-col">
            <text class="hi">{{ greetingText }}</text>
            <text class="nm">{{ displayName }}</text>
          </view>
          <view class="head-msg" @click="openMsg" aria-label="消息中心">
            <AppIcon name="bell" size="md" color="#FFFFFF" />
            <view v-if="hasUnread" class="dot" />
          </view>
        </view>
        <text class="sub">{{ rc.homeSub }}</text>
      </view>

      <!-- ===== 内容区 ===== -->
      <view class="m-body">
        <!-- 统计行（非客户角色） -->
        <view v-if="statPair" class="m-stat-row">
          <view class="m-stat">
            <text class="v">{{ statPair[0] }}</text>
            <text class="l">{{ statPair[1] }}</text>
          </view>
          <view class="m-stat">
            <text class="v">{{ statPair[2] }}</text>
            <text class="l">{{ statPair[3] }}</text>
          </view>
        </view>

        <!-- 角色化快捷宫格 -->
        <view class="m-grid">
          <view
            v-for="q in quickItems"
            :key="q.key + q.label"
            class="m-qk"
            :style="{ width: quickItemWidth }"
            @click="onQuick(q)"
          >
            <view class="qk-ic" :style="{ background: toneBg(q.tone) }">
              <AppIcon :name="q.icon" size="tile" color="#FFFFFF" />
            </view>
            <text class="qt">{{ q.label }}</text>
          </view>
        </view>

        <!-- 客户：服务顾问卡 -->
        <view v-if="isCustomer" class="m-card">
          <view class="m-card-h"><text class="m-card-t">为您匹配的顾问</text></view>
          <view v-if="advisorName" class="m-row">
            <view class="m-ava">{{ advisorName[0] }}</view>
            <view class="m-row-main">
              <text class="t1">{{ advisorName }}</text>
              <text class="t2">企业资金顾问 · 服务中</text>
            </view>
            <view class="m-tag ok">服务中</view>
          </view>
          <view v-else class="m-empty">
            <view class="ill"><AppIcon name="support" size="tile" color="#C7A15A" /></view>
            <text class="m-empty-t">暂未分配服务顾问</text>
          </view>
        </view>

        <!-- 渠道：我的线索概览（含线索审批状态，D50：待审核/通过/驳回均可查看） -->
        <view v-if="isChannelRole" class="m-card">
          <view class="m-card-h"><text class="m-card-t">我的线索概览</text></view>
          <view v-for="(l, i) in myLeadList" :key="i" class="m-row">
            <view class="m-row-main">
              <text class="t1">{{ l.entName || '—' }}</text>
              <text class="t2">创建人：{{ l.createdBy || '—' }} · 录入于 {{ l.createdAt || '—' }}</text>
            </view>
            <view :class="['m-tag', leadStatusTone(l.followStatus)]">{{ leadStatusLabel(l.followStatus) }}</view>
          </view>
          <view v-if="!myLeadList.length" class="m-empty">
            <view class="ill"><AppIcon name="leads" size="tile" color="#C7A15A" /></view>
            <text class="m-empty-t">暂无录入线索</text>
          </view>
        </view>

        <!-- 员工：审批中心卡 -->
        <view v-if="isStaffRole && store.hasPermission('mini:approval:view')" class="m-card">
          <view class="m-card-h">
            <text class="m-card-t">审批待办</text>
            <text class="m-card-more" @click="goApproval">进入 ›</text>
          </view>
          <view v-if="approvalTotal > 0" class="m-row">
            <view class="m-row-main">
              <text class="t1">待我审批 {{ approvalTotal }} 项</text>
              <text class="t2">仅显示 提交人 + 事项 + 时间</text>
            </view>
            <view class="m-tag warn">待审批</view>
          </view>
          <view v-else class="m-empty">
            <view class="ill"><AppIcon name="check" size="tile" color="#C7A15A" /></view>
            <text class="m-empty-t">暂无待办审批</text>
          </view>
        </view>

        <!-- 我的报告入口（有报告权限的角色） -->
        <view v-if="store.hasPermission('mini:report:view')" class="m-card">
          <view class="m-card-h">
            <text class="m-card-t">{{ rc.reportTitle }}</text>
            <text class="m-card-more" @click="goReport">全部 ›</text>
          </view>
          <view class="m-row" @click="goReport">
            <view class="row-ic" :style="{ background: toneBg('gold') }">
              <AppIcon name="report" size="tile" color="#FFFFFF" />
            </view>
            <view class="m-row-main">
              <text class="t1">{{ rc.reportPrompt }}</text>
              <text class="t2">{{ rc.reportDesc }}</text>
            </view>
            <text class="m-go">›</text>
          </view>
        </view>

        <view class="m-note">
          <text class="m-note-t">{{ isCustomer ? '合规提示：仅提供资质与经营风险分析，不推荐金融产品或预测审批结果' : '合规提示：内部匹配结果仅供业务作业，不构成审批承诺' }}</text>
        </view>
      </view>
    </view>

  </view>

  <!-- ===== 消息中心：数据全部来自 /api/mini/notification，无示例数据 ===== -->
  <MessageSheet v-model:visible="msgOpen" @read="onMessageRead" />

  <TabBar current="home" />
</template>

<script setup>
import { ref, computed } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { useUserStore } from '../../store/user';
import { useThemeMode } from '../../theme';
import TabBar from '../../components/TabBar.vue';
import MessageSheet from '../../components/MessageSheet.vue';
import AppIcon from '../../components/AppIcon.vue';
import { roleConfig, TONE_BG } from '../../utils/roles';
import { gridItemWidth } from '../../utils/grid';
import { myLeads, leadStatusLabel, leadStatusTone } from '../../api/lead';
import { homeStats } from '../../api/dashboard';
import { approvalCounts } from '../../api/approval';
import { unreadCount } from '../../api/notification';
import { consumePendingInvitation, buildInviteSharePath } from '../../utils/invitation';
import { uploadAvatar } from '../../utils/avatar';

/**
 * 首页（角色化，唯一视觉真源 = docs/prototypes/redesign-all-roles-v1.html）。
 * 结构：深色墨蓝 Hero（头像 + 问候 + 角色化业务说明 + 消息铃铛）→ 统计行 → 角色化快捷宫格 → 角色化信息卡。
 */
const store = useUserStore();
const themeMode = useThemeMode();

const rc = computed(() => roleConfig(store.role));
const isCustomer = computed(() => store.role === 'customer');
const isChannelRole = computed(() => store.role === 'channel');
const isStaffRole = computed(() => store.isStaff);

const stats = ref({});
const approvalTotal = ref(0);
const myLeadList = ref([]);
const msgOpen = ref(false);

/**
 * 铃铛红点：由真实未读数驱动（无未读即不渲染，2026-09-11 审计 P0-2）。
 * 此前为常显 + 三条硬编码示例消息，会让用户误认为有真实待办。
 */
const hasUnread = ref(false);

/** 展示名称 */
const displayName = computed(() => {
  const name = (store.profile && store.profile.contactName) || (store.user && store.user.name);
  return name || '微信客户';
});
const avatarChar = computed(() => (displayName.value || '客')[0]);
const avatarUrl = computed(() => store.avatarUrl);
const advisorName = computed(() => (store.profile && store.profile.ownerStaffName) || '');

/** 时间段问候 */
const greetingText = computed(() => {
  const h = new Date().getHours();
  if (h < 6) return '夜深了';
  if (h < 12) return '早上好';
  if (h < 14) return '中午好';
  if (h < 18) return '下午好';
  return '晚上好';
});

/** 统计行（指标键 + 标签唯一来源 = utils/roles.js 的 stats；缺数据以 '—' 占位） */
const statPair = computed(() => {
  const conf = rc.value.stats;
  if (!conf) return null;
  const s = stats.value || {};
  const v = (k) => (s[k] === undefined || s[k] === null ? '—' : String(s[k]));
  return [v(conf[0].key), conf[0].label, v(conf[1].key), conf[1].label];
});

/**
 * 可点快捷入口（P0-3）：必须有 `route`，或由页面自行处理（`invite` 走分享）。
 * 无对应页面/能力由当前端承载时不渲染，避免「点了才提示请去 Web 管理端」。
 */
const quickItems = computed(() => (rc.value.quick || []).filter((q) => q.route || q.key === 'invite'));

/**
 * 磁贴宽度：按**实际数量**分列（与设计真源原型 gridCols 同规则，见 `utils/grid.js`）。
 *
 * <p>原实现把宽度写死成 4 列的 1/4，导致 6 个磁贴排成 4+2、3 个磁贴右侧空一格。
 * 列间距须与 `.m-grid` 的 `gap` 一致（20rpx，适配窄屏小程序）。
 */
const quickItemWidth = computed(() => gridItemWidth(quickItems.value.length, 20));

function toneBg(tone) {
  return TONE_BG[tone] || TONE_BG.royal;
}

onShow(async () => {
  try {
    const ok = await store.init();
    if (!ok) return;
    await consumePendingInvitation(store);
    stats.value = {};
    approvalTotal.value = 0;
    if (store.isChannel) {
      // 渠道：线索概览用 myLeads，统计行用 dashboard/stats（仅本人指标，D72 精确白名单）
      await Promise.all([loadMyLeads(), loadStats(), loadUnread()]);
      return;
    }
    // 客户无统计行；仅员工拉取统计与审批角标
    if (store.isStaff) {
      await Promise.all([loadStats(), loadApprovalCount(), loadUnread()]);
    } else {
      await loadUnread();
    }
  } catch (e) {
    stats.value = {};
    approvalTotal.value = 0;
    hasUnread.value = false;
  }
});

async function loadMyLeads() {
  try {
    const data = await myLeads(1, 3);
    myLeadList.value = (data && data.records) || [];
  } catch (e) {
    myLeadList.value = [];
  }
}

async function loadStats() {
  try {
    stats.value = (await homeStats()) || {};
  } catch (e) {
    stats.value = {};
  }
}

async function loadApprovalCount() {
  if (!store.hasPermission('mini:approval:view')) return;
  try {
    const c = await approvalCounts();
    approvalTotal.value = (c && typeof c.TOTAL === 'number') ? c.TOTAL : ((c && c.ALLOCATION) || 0);
  } catch (e) {
    approvalTotal.value = 0;
  }
}

/**
 * 拉取未读消息数（驱动铃铛红点）。
 *
 * <p>失败静默降级为「无未读」：消息中心属次要内容，不允许因它拉取失败而弹错或阻断首页。
 */
async function loadUnread() {
  try {
    const n = await unreadCount();
    hasUnread.value = Number(n) > 0;
  } catch (e) {
    hasUnread.value = false;
  }
}

/** 消息已读（弹层内已全部标记）→ 立即清红点，不必等下次 onShow。 */
function onMessageRead() {
  hasUnread.value = false;
}

/** 选微信头像（小程序）：MP 走 chooseAvatar，H5 提示 */
function onChooseAvatarClick() {
  // #ifdef H5
  uni.showToast({ title: '请在微信小程序内授权头像', icon: 'none' });
  // #endif
}
function onChooseAvatar(e) {
  const url = e.detail && e.detail.avatarUrl;
  if (!url) return;
  store.setAvatar(url);
  uploadAvatar(url).then((remote) => { if (remote) store.setAvatar(remote); }).catch(() => {});
}

/** tab 页用 reLaunch，非 tab 页用 navigateTo */
const TAB_URLS = [
  '/pages/home/home', '/pages/match/match', '/pages/report/list', '/pages/order/list',
  '/pages/mine/mine', '/pages/approval/list',
  // 合并页（D74）：线索录入 / 我的客户
  '/pages/lead-entry/lead-entry', '/pages/client/mine',
];

function onQuick(q) {
  if (q.key === 'invite') { onInvite(); return; }
  if (!q.route) {
    uni.showToast({ title: '该功能请在 Web 管理端使用', icon: 'none' });
    return;
  }
  // 合并页支持 seg 定位子页签（如「录入产品」直达 线索录入-产品）
  const url = q.seg ? `${q.route}?seg=${q.seg}` : q.route;
  if (TAB_URLS.indexOf(q.route) >= 0) uni.reLaunch({ url });
  else uni.navigateTo({ url });
}

/** 邀请：H5 复制分享链接，小程序引导右上角分享 */
function onInvite() {
  // #ifdef H5
  const path = buildInviteSharePath('');
  const origin = (typeof window !== 'undefined' && window.location) ? window.location.origin : '';
  uni.setClipboardData({
    data: `${origin}/#${path}`,
    success: () => uni.showToast({ title: '分享链接已复制', icon: 'success' }),
  });
  // #endif
  // #ifdef MP-WEIXIN
  uni.showToast({ title: '请点击右上角分享给好友', icon: 'none' });
  // #endif
}

/** 我的报告：客户仍是独立 tab；员工侧已并入「我的客户」合并页的报告子页签（D74） */
function goReport() {
  if (store.isStaff) uni.reLaunch({ url: '/pages/client/mine?seg=report' });
  else uni.reLaunch({ url: '/pages/report/list' });
}
function goApproval() { uni.navigateTo({ url: '/pages/approval/list' }); }

function openMsg() { msgOpen.value = true; }
</script>

<style scoped>
.home-page {
  min-height: 100vh;
  background: var(--bg-page);
  box-sizing: border-box;
  padding-bottom: calc(128rpx + env(safe-area-inset-bottom));
}

/* ===== 深色墨蓝 Hero ===== */
.m-head {
  background: var(--hero-gradient);
  color: var(--text-invert);
  padding: 56rpx 32rpx 40rpx;
  position: relative;
  overflow: hidden;
}
.m-head-user { display: flex; align-items: center; gap: 22rpx; min-width: 0; }
.m-head-avatar {
  width: 96rpx; height: 96rpx; border-radius: 28rpx; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  background: var(--hero-avatar-bg);
  border: 2rpx solid rgba(255, 255, 255, 0.55);
  overflow: hidden;
}
.avatar-img { width: 100%; height: 100%; }
.avatar-char { color: var(--hero-avatar-ink); font-size: 40rpx; font-weight: 800; }
.head-col { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.hi { font-size: 24rpx; color: var(--hero-text-dim); }
.nm { margin-top: 4rpx; font-size: 38rpx; font-weight: 800; color: var(--text-invert); }
.head-msg { position: relative; width: 76rpx; height: 76rpx; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.head-msg .dot { position: absolute; right: 14rpx; top: 12rpx; width: 16rpx; height: 16rpx; border-radius: 50%; background: var(--hero-dot); border: 3rpx solid var(--brand-navy); }
.sub { margin-top: 18rpx; font-size: 23rpx; color: var(--hero-text-muted); }

/* ===== 内容区 ===== */
.m-body { padding: 20rpx 24rpx 18rpx; }

.m-stat-row { display: flex; gap: 20rpx; margin-bottom: 24rpx; }
.m-stat {
  flex: 1; border-radius: 20rpx; padding: 22rpx;
  background: var(--hero-stat-bg);
  border: 1rpx solid rgba(199, 161, 90, 0.42);
}
.m-stat .v { font-size: 38rpx; font-weight: 800; color: var(--text-invert); }
.m-stat .l { display: block; margin-top: 8rpx; font-size: 22rpx; color: var(--hero-text-soft); }

/* 与设计真源原型 .m-grid / .m-qk 逐项对齐（px→rpx ×2）：
   列间距 10px→20rpx、磁贴高 82px→164rpx，减少窄屏横向拥挤与首页留白。
   justify-content:center 让「不满的末行」居中（5 项 = 3+2，第二行 2 项居中而非左对齐）；
   列数仍由 utils/grid.js#gridCols 决定，与原型 gridStyle 同源。 */
.m-grid { display: flex; flex-wrap: wrap; justify-content: center; gap: 20rpx; margin-bottom: 24rpx; }
.m-qk {
  /* 宽度由 script 按磁贴数量注入（utils/grid.js），不再写死 4 列的 1/4 */
  min-height: 164rpx;
  background: var(--bg-card);
  border: 1rpx solid var(--line);
  border-radius: 32rpx;
  padding: 20rpx 10rpx 18rpx;
  display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 12rpx;
  box-shadow: var(--shadow-tile);
  box-sizing: border-box;
}
.m-qk:active { opacity: 0.9; }
/* 图标底：80rpx（40px）圆角磁贴，与设计真源原型 .m-qk-ic 一致；图标 42rpx（21px）占 52.5% */
.qk-ic { width: 68rpx; height: 68rpx; border-radius: 20rpx; display: flex; align-items: center; justify-content: center; }
/* 标签：单行省略，防止长文案撑破磁贴（同原型 .m-qk .qt 的 max-width + ellipsis） */
.qt { font-size: 22rpx; font-weight: 700; color: var(--text-primary); text-align: center; max-width: 100%; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

.m-card {
  background: var(--bg-card);
  border: 1rpx solid var(--line);
  border-radius: 24rpx;
  padding: 22rpx 24rpx;
  margin-bottom: 16rpx;
  box-shadow: var(--shadow-sm);
}
.m-card-h { display: flex; align-items: center; margin-bottom: 10rpx; min-height: 36rpx; }
.m-card-t { font-size: 30rpx; font-weight: 800; color: var(--text-primary); }
.m-card-more { margin-left: auto; font-size: 24rpx; font-weight: 700; color: var(--brand-deep); }

.m-row { display: flex; align-items: center; gap: 16rpx; padding: 12rpx 0; border-top: 1rpx solid var(--line); }
.m-row:first-of-type { border-top: none; }
.m-ava {
  width: 76rpx; height: 76rpx; border-radius: 22rpx; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  background: var(--bg-input); color: var(--brand-deep); font-size: 28rpx; font-weight: 800;
}
.m-row-main { flex: 1; min-width: 0; display: flex; flex-direction: column; }
/* 行首图标锚点：与 .m-ava 同尺寸（76rpx/22rpx），让「我的报告」入口与其它行有同一视觉锚点 */
.row-ic { width: 76rpx; height: 76rpx; border-radius: 22rpx; flex-shrink: 0; display: flex; align-items: center; justify-content: center; }
.t1 { font-size: 27rpx; font-weight: 700; color: var(--text-primary); }
.t2 { margin-top: 6rpx; font-size: 22rpx; color: var(--text-secondary); }
.m-go { font-size: 36rpx; color: var(--text-secondary); flex-shrink: 0; }

.m-tag { font-size: 22rpx; font-weight: 700; padding: 6rpx 16rpx; border-radius: 999rpx; flex-shrink: 0; }
.m-tag.ok { color: var(--success-text); background: var(--success-bg); }
.m-tag.warn { color: var(--warning-text); background: var(--warning-bg); }
.m-tag.info { color: var(--info-text); background: var(--brand-bg); }

.m-empty { display: flex; align-items: center; gap: 16rpx; padding: 12rpx 0 2rpx; }
.m-empty .ill { width: 76rpx; height: 76rpx; border-radius: 22rpx; background: var(--bg-input); display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.m-empty-t { font-size: 24rpx; color: var(--text-secondary); text-align: left; }

.m-note { padding: 4rpx 4rpx 8rpx; }
.m-note-t { display: block; text-align: center; font-size: 22rpx; color: var(--text-secondary); }
</style>
