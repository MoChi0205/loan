<template>
  <view class="client-page theme-root" :class="{ 'u-shell': store.isTablet }" :data-theme="themeMode">
    <AppEmpty v-if="!canCreate" title="暂无权限" desc="当前账号没有线索录入权限，请联系管理员" />
    <!-- 内容区 -->
    <view v-else class="content">
      <!-- 提示条：录入客户进入公海，由顾问跟进（渠道沙箱隔离） -->
      <view class="tip-bar">
        <AppIcon name="users" size="md" />
        <text class="tip-text">{{ store.isStaff ? '新增线索自动归属本人，可主动释放到公司公海' : '新增后仅本人立即可见，待公司审核通过后进入公海并由顾问跟进' }}</text>
      </view>

      <!-- 员工专属：录入前客户查重（多条件：企业名/信用代码/手机号/身份证/联系人） -->
      <view v-if="store.isStaff" class="dedup-card">
        <view class="dedup-head">
          <AppIcon name="search" size="sm" />
          <text class="dedup-title">客户查重</text>
          <text class="dedup-hint">多条件（企业名 / 信用代码 / 手机号 / 身份证 / 联系人）</text>
        </view>
        <view class="dedup-row">
          <input
            class="field-input dedup-input"
            v-model="clientSearchKw"
            placeholder="输入任一标识查重已有客户（≥2 字）"
            placeholder-class="ph"
            maxlength="64"
            @input="onClientSearchInput"
            @blur="onClientSearch"
          />
        </view>
        <view v-if="clientSearchResult" class="dedup-result">
          <text class="dedup-result-title">⚠ 已存在客户，请确认是否重复录入</text>
          <text class="dedup-result-line">企业：{{ clientSearchResult.entName }}</text>
          <text class="dedup-result-line">联系人：{{ clientSearchResult.contactName }} · {{ clientSearchResult.contactPhone }}</text>
          <text class="dedup-result-line">归属：{{ clientSearchResult.ownerStaffName || '公海' }}</text>
          <view class="dedup-result-actions">
            <AppButton variant="secondary" size="sm" :loading="claimingClient" @click="onClaimClient">认领已有客户</AppButton>
          </view>
          <text class="dedup-result-tips">认领即建立跟进归属（已归属他人时转为分配申请，待审核）；忽略则继续录入新线索。</text>
        </view>
      </view>

      <view class="card" v-if="form.leadType === 'PERSONAL'">
        <text class="card-title">个人信息</text>
        <view class="field">
          <text class="field-label">身份证号</text>
          <input class="field-input" v-model="form.idCardNo" placeholder="选填，用于唯一身份校验" placeholder-class="ph" maxlength="18" />
        </view>
      </view>

      <!-- 基本信息 -->
      <view class="card">
        <text class="card-title">基本信息</text>

        <view class="field">
          <text class="field-label">联系人</text><text class="req"> *</text>
          <input class="field-input" v-model="form.contactName" placeholder="请输入联系人姓名" placeholder-class="ph" maxlength="30" />
        </view>

        <view class="field">
          <text class="field-label">手机号</text><text class="req"> *</text>
          <input class="field-input" type="number" v-model="form.phone" placeholder="请输入手机号" placeholder-class="ph" maxlength="11" />
        </view>

        <view class="field">
          <text class="field-label">客群</text>
          <view class="seg">
            <AppClickable class="seg-item" :class="{ active: form.leadType === 'ENTERPRISE' }" @click="onPickGroup('ENTERPRISE')">企业</AppClickable>
            <AppClickable class="seg-item" :class="{ active: form.leadType === 'PERSONAL' }" @click="onPickGroup('PERSONAL')">个人</AppClickable>
          </view>
        </view>
      </view>

      <!-- 企业信息：仅企业客群展开 -->
      <view class="card" v-if="form.leadType === 'ENTERPRISE'">
        <text class="card-title">企业信息</text>

        <view class="field">
          <text class="field-label">企业名称</text>
          <input class="field-input" v-model="form.entName" placeholder="请输入企业全称" placeholder-class="ph" maxlength="60" />
        </view>
        <view class="field">
          <text class="field-label">统一社会信用代码</text>
          <input class="field-input" v-model="form.creditCode" placeholder="18 位信用代码" placeholder-class="ph" maxlength="18" />
        </view>
        <view class="field">
          <text class="field-label">所属行业</text>
          <input class="field-input" v-model="form.industry" placeholder="如：制造业 / 批发零售" placeholder-class="ph" maxlength="30" />
        </view>
        <view class="field">
          <text class="field-label">成立年限（年）</text>
          <input class="field-input" type="digit" v-model="form.foundYears" placeholder="如：5" placeholder-class="ph" maxlength="4" />
        </view>
        <view class="field">
          <text class="field-label">年纳税额（万元）</text>
          <input class="field-input" type="digit" v-model="form.annualTaxAmount" placeholder="如：50" placeholder-class="ph" maxlength="12" />
        </view>
        <view class="field">
          <text class="field-label">年开票额（万元）</text>
          <input class="field-input" type="digit" v-model="form.annualInvoiceAmount" placeholder="如：300" placeholder-class="ph" maxlength="12" />
        </view>
      </view>

      <!-- 需求备注 -->
      <view class="card">
        <text class="card-title">需求备注</text>
        <textarea class="field-input field-textarea" v-model="form.remark" placeholder="补充客户资金需求、意向产品等" placeholder-class="ph" maxlength="200" />
      </view>

      <!-- 提交录入 -->
      <view class="submit-wrap">
        <AppButton variant="primary" size="lg" :loading="submitting" @click="onSubmit">提交录入</AppButton>
      </view>

      <!-- 我录入的线索 -->
      <view class="card list-card">
        <text class="card-title">我录入的线索</text>
        <AppSkeleton v-if="loading && !records.length" :rows="3" />
        <AppEmpty v-else-if="hasError && !records.length" title="加载失败" desc="网络异常，请重试">
          <AppButton variant="primary" size="md" @click="reload">重试</AppButton>
        </AppEmpty>
        <AppEmpty v-else-if="!loading && !records.length" title="暂无本人录入的线索" desc="新增成功后会立即显示，审核通过后进入公司公海" />
        <view v-else
          v-for="(item, index) in records"
          :key="item.leadNo"
          class="lead-item"
          :class="{ first: index === 0 }"
        >
          <view class="lead-main">
            <text class="lead-name">{{ item.contactName }}</text>
            <text class="lead-phone">{{ item.phone }}</text>
          </view>
          <text v-if="item.entName" class="lead-ent">{{ item.entName }}</text>
          <view class="lead-meta">
            <text class="lead-status">{{ leadStatusLabel(item.followStatus) }}</text>
            <text class="lead-date">创建人：{{ item.createdBy || '—' }}</text>
            <text class="lead-date">{{ formatDate(item.createdAt) }}</text>
          </view>
          <view v-if="canRelease(item)" class="lead-actions">
            <AppButton variant="secondary" size="sm" @click="onRelease(item)">释放到公司公海</AppButton>
          </view>
        </view>
        <AppLoadMore v-if="records.length" :loading="loadingMore" :finished="finished" :error="hasError" @load="loadMore" />
      </view>
    </view>
  </view>
</template>

<script setup>
/**
 * 「线索录入 · 客户线索」子视图（由 pages/lead-entry/lead-entry.vue 容器承载）。
 *
 * 渠道合作方录入后本人立即可见，待公司终审通过才进入公海（沙箱隔离：列表仅返回本人录入）。
 * 对接 api/lead.js 的 submitLead / myLeads。
 *
 * <p>组件化改造（D74）：原页面级 onLoad 改为 onMounted；激活（active）时刷新线索列表；
 * 底部导航改由容器页统一提供，故不再内嵌 TabBar。
 */
import { ref, reactive, computed, onMounted, watch } from 'vue';
import { useUserStore } from '../store/user';
import { useThemeMode } from '../theme';
import { submitLead, myLeads, releaseLead, leadStatusLabel } from '../api/lead';
import { searchClient, claimClient } from '../api/client';
import AppIcon from './AppIcon.vue';
import AppButton from './AppButton.vue';
import AppEmpty from './AppEmpty.vue';
import AppSkeleton from './AppSkeleton.vue';
import AppLoadMore from './AppLoadMore.vue';

/** 容器传入：当前子页签是否激活 */
const props = defineProps({
  active: { type: Boolean, default: true },
});

const store = useUserStore();
const themeMode = useThemeMode();
const canCreate = computed(() => store.hasPermission('mini:lead:create'));

const page = ref(1);
const size = ref(10);
const total = ref(0);
const records = ref([]);
const loading = ref(false);
const loadingMore = ref(false);
const finished = ref(false);
const hasError = ref(false);
const submitting = ref(false);
const releasingLeadNo = ref('');

// 客户查重（员工专属，录入前多条件查重；提交时仍由后端 MiniLeadService 兜底）
const clientSearchKw = ref('');
const clientSearchResult = ref(null);
let clientSearchTimer = null;
/** 输入 debounce：≥2 字后 350ms 触发查重（避免每键请求） */
function onClientSearchInput() {
  if (clientSearchTimer) clearTimeout(clientSearchTimer);
  const kw = clientSearchKw.value.trim();
  if (kw.length < 2) { clientSearchResult.value = null; return; }
  clientSearchTimer = setTimeout(onClientSearch, 350);
}
async function onClientSearch() {
  const kw = clientSearchKw.value.trim();
  if (kw.length < 2) { clientSearchResult.value = null; return; }
  try { clientSearchResult.value = await searchClient(kw); }
  catch (e) { clientSearchResult.value = null; }
}

// 认领已有客户（D39：本人归属幂等通过；他人归属/无归属转分配申请，待审核）
const claimingClient = ref(false);
async function onClaimClient() {
  if (claimingClient.value || !clientSearchResult.value) return;
  const code = clientSearchResult.value.clientCode;
  claimingClient.value = true;
  try {
    const data = await claimClient(code);
    if (data && data.result === 'AUTO_CLAIMED') {
      uni.showToast({ title: '认领成功，可在「我的客户」查看', icon: 'success' });
    } else {
      uni.showToast({ title: '已提交分配申请，待审核', icon: 'none' });
    }
    clientSearchResult.value = null;
    clientSearchKw.value = '';
  } catch (e) { /* toast 已由请求层弹出 */ }
  finally { claimingClient.value = false; }
}

const form = reactive({
  contactName: '',
  phone: '',
  leadType: 'ENTERPRISE',
  entName: '',
  creditCode: '',
  idCardNo: '',
  industry: '',
  foundYears: '',
  annualTaxAmount: '',
  annualInvoiceAmount: '',
  remark: '',
});

/** 切换客群（企业 / 个人） */
function onPickGroup(v) {
  form.leadType = v;
}

/**
 * 组装提交体：所有值转字符串（数字字段转字符串）。
 * source 由后端按用户类型派生，前端不传；企业字段仅在 ENTERPRISE 时附带。
 *
 * @returns {Object} 字符串化后的 payload
 */
function buildPayload() {
  const payload = {
    contactName: form.contactName.trim(),
    phone: form.phone.trim(),
    leadType: form.leadType,
    remark: form.remark.trim(),
  };
  if (form.leadType === 'ENTERPRISE') {
    payload.entName = form.entName.trim();
    payload.creditCode = form.creditCode.trim();
    payload.industry = form.industry.trim();
    payload.foundYears = String(form.foundYears || '');
    payload.annualTaxAmount = String(form.annualTaxAmount || '');
    payload.annualInvoiceAmount = String(form.annualInvoiceAmount || '');
  }
  if (form.leadType === 'PERSONAL' && form.idCardNo.trim()) payload.idCardNo = form.idCardNo.trim();
  return payload;
}

/* 跟进状态中文标签已收敛到 api/lead.js#leadStatusLabel（D50 渠道线索审批态），本组件直接复用。 */

/** 日期格式化：取 YYYY-MM-DD 的 MM-DD 部分展示 */
function formatDate(s) {
  if (!s) return '';
  const day = String(s).replace('T', ' ').split(' ')[0];
  const parts = day.split('-');
  if (parts.length === 3) return `${parts[1]}-${parts[2]}`;
  return day;
}

/** 提交成功后重置表单（客群默认回企业） */
function resetForm() {
  form.contactName = '';
  form.phone = '';
  form.entName = '';
  form.creditCode = '';
  form.idCardNo = '';
  form.industry = '';
  form.foundYears = '';
  form.annualTaxAmount = '';
  form.annualInvoiceAmount = '';
  form.remark = '';
  form.leadType = 'ENTERPRISE';
}

/** 加载我录入的线索 */
async function loadLeads() {
  if (loading.value) return;
  loading.value = true;
  hasError.value = false;
  page.value = 1;
  try {
    const res = await myLeads(page.value, size.value);
    records.value = (res && res.records) || [];
    total.value = (res && res.total) || 0;
    finished.value = records.value.length >= total.value || records.value.length < size.value;
  } catch (e) {
    hasError.value = true;
  } finally {
    loading.value = false;
  }
}

async function loadMore() {
  if (loadingMore.value || finished.value) return;
  loadingMore.value = true;
  hasError.value = false;
  try {
    const next = page.value + 1;
    const res = await myLeads(next, size.value);
    const rows = (res && res.records) || [];
    records.value = records.value.concat(rows);
    page.value = next;
    total.value = (res && res.total) || total.value;
    finished.value = records.value.length >= total.value || rows.length < size.value;
  } catch (e) {
    hasError.value = true;
  } finally { loadingMore.value = false; }
}

function reload() { loadLeads(); }

function canRelease(item) {
  return store.isStaff && store.hasPermission('mini:lead:release')
    && item.ownerStaffCode && item.ownerStaffCode === (store.user && store.user.userNo);
}

async function onRelease(item) {
  if (releasingLeadNo.value) return;
  const confirmed = await new Promise((resolve) => {
    uni.showModal({
      title: '释放线索',
      content: '释放后该线索将进入公司公海，其他员工可以认领。确认释放？',
      success: (res) => resolve(!!res.confirm),
      fail: () => resolve(false),
    });
  });
  if (!confirmed) return;
  releasingLeadNo.value = item.leadNo;
  try {
    await releaseLead(item.leadNo);
    uni.showToast({ title: '已释放到公司公海', icon: 'none' });
    loadLeads();
  } catch (e) { /* toast 已由请求层弹出 */ }
  finally { releasingLeadNo.value = ''; }
}

/** 提交录入 */
async function onSubmit() {
  if (submitting.value) return;
  if (!form.contactName.trim()) {
    uni.showToast({ title: '请填写联系人', icon: 'none' });
    return;
  }
  if (!/^1\d{10}$/.test(form.phone.trim())) {
    uni.showToast({ title: '请填写正确的手机号', icon: 'none' });
    return;
  }
  submitting.value = true;
  try {
    const res = await submitLead(buildPayload());
    // 重复线索：HTTP 200 + duplicated=true，沙箱脱敏不泄归属人，显示友好文案而非报错
    if (res && res.duplicated) {
      uni.showToast({ title: '该客户已存在，可申请认领', icon: 'none', duration: 2500 });
      return;
    }
    const successText = store.isStaff
      ? '录入成功，已自动归属本人'
      : '录入成功，等待公司审核';
    uni.showToast({ title: res && res.sameNameWarning ? '录入成功，存在同名客户请注意核对' : successText, icon: 'none', duration: 2600 });
    resetForm();
    loadLeads();
  } catch (e) {
    // 真正的失败由 request.js 弹 toast，此处不重复提示
  } finally {
    submitting.value = false;
  }
}

onMounted(() => {
  loadLeads();
});

// 从其它子页签切回时刷新我录入的线索（表单内容保留，不重置）
watch(() => props.active, (on) => {
  if (on) loadLeads();
});
</script>

<style scoped>
.client-page {
  min-height: 100vh;
  background: var(--bg-page);
  box-sizing: border-box;
}

.content {
  padding: var(--space-4);
  padding-bottom: calc(var(--space-16) + env(safe-area-inset-bottom));
}

.lead-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--space-2);
}

/* 提示条 */
.tip-bar {
  display: flex;
  align-items: flex-start;
  gap: var(--space-2);
  background: var(--warning-bg);
  border: 2rpx solid var(--warning-line);
  border-radius: var(--radius-md);
  padding: var(--space-3);
  margin-bottom: var(--space-3);
}
.tip-text {
  flex: 1;
  min-width: 0;
  font-size: var(--fs-xs);
  line-height: var(--lh-base);
  color: var(--warning-text);
}

/* 卡片 */
.card {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  padding: var(--space-4);
  box-shadow: var(--shadow-md);
  margin-bottom: var(--space-3);
}
.card-title {
  display: block;
  font-size: var(--fs-lg);
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: var(--space-3);
}

/* 表单字段 */
.field { margin-bottom: var(--space-3); }
.field:last-child { margin-bottom: 0; }
.field-label {
  display: block;
  font-size: var(--fs-sm);
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: var(--space-1);
}
.req { color: var(--danger-text); }
.field-input {
  width: 100%;
  background: var(--bg-input);
  border: 2rpx solid transparent;
  border-radius: var(--radius-md);
  padding: 24rpx 28rpx;
  /* 输入字号 ≥16px（32rpx）：低于 16px 会被 iOS Safari 聚焦自动放大视口 */
  font-size: 32rpx;
  color: var(--text-primary);
  min-height: 88rpx;
  box-sizing: border-box;
}
.field-textarea { min-height: 180rpx; line-height: var(--lh-base); }
/* #ifdef H5 */
/* 键盘焦点可见（小程序无 focus 概念，隔离 H5） */
.field-input:focus { border-color: var(--gold); background: var(--bg-card); }
/* #endif */
.ph { color: var(--text-placeholder); }

/* 客户查重卡（员工专属，录入前多条件查重；复用 .field-input 输入框样式） */
.dedup-card { padding: 18rpx 18rpx 8rpx; }
.dedup-head { display: flex; align-items: center; gap: 10rpx; margin-bottom: 10rpx; }
.dedup-title { font-size: var(--fs-md); font-weight: 700; color: var(--text-primary); }
.dedup-hint { margin-left: auto; font-size: var(--fs-xs); color: var(--text-secondary); }
.dedup-result { margin-top: 14rpx; padding: 16rpx; background: var(--warning-bg); border-radius: var(--radius-sm); border: 1rpx solid var(--line); }
.dedup-result-title { display: block; font-size: var(--fs-md); font-weight: 700; color: var(--warning-text); margin-bottom: 8rpx; }
.dedup-result-line { display: block; font-size: var(--fs-sm); color: var(--text-primary); margin-top: 4rpx; }
.dedup-result-actions { margin-top: 14rpx; }
.dedup-result-tips { display: block; font-size: var(--fs-xs); color: var(--text-secondary); margin-top: 10rpx; line-height: 1.5; }

/* 客群切换（等宽用 .seg view） */
.seg {
  display: flex;
  gap: var(--space-2);
  background: var(--bg-input);
  border-radius: var(--radius-md);
  padding: var(--space-1);
}
.seg view {
  flex: 1;
  min-width: 0;
  text-align: center;
  padding: 18rpx 0;
  font-size: var(--fs-sm);
  color: var(--text-secondary);
  border-radius: var(--radius-sm);
  transition: background var(--transition-fast), color var(--transition-fast);
}
.seg view.active {
  background: var(--bg-card);
  color: var(--brand-deep);
  font-weight: 600;
  box-shadow: var(--shadow-sm);
}

/* 提交按钮（通栏等宽） */
.submit-wrap { margin: var(--space-2) 0 var(--space-4); }
.submit-wrap view, .submit-wrap button { width: 100%; }

/* 线索列表 */
.list-hint {
  font-size: var(--fs-xs);
  color: var(--text-secondary);
  text-align: center;
  padding: var(--space-4) 0;
}
.lead-item {
  padding: var(--space-3) 0;
  border-top: 2rpx solid var(--line);
}
.lead-item.first { border-top: none; padding-top: 0; }
.lead-main {
  display: flex;
  align-items: baseline;
  gap: var(--space-2);
}
.lead-name { font-size: var(--fs-md); font-weight: 600; color: var(--text-primary); }
.lead-phone { font-size: var(--fs-sm); color: var(--text-secondary); }
.lead-ent {
  display: block;
  font-size: var(--fs-xs);
  color: var(--text-body);
  margin-top: var(--space-1);
}
.lead-meta {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-top: var(--space-1);
}
.lead-status {
  font-size: var(--fs-xs);
  color: var(--info-text);
  background: rgba(6, 182, 212, 0.1);
  border-radius: var(--radius-full);
  padding: 4rpx 16rpx;
}
.lead-date { font-size: var(--fs-xs); color: var(--text-placeholder); }
</style>
