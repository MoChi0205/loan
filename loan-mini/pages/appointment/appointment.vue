<template>
  <view class="appt-page theme-root" :data-theme="themeMode">
    <AppEmpty v-if="!canView" title="暂无权限" desc="当前账号不支持预约，请联系服务顾问" />

    <template v-else>
      <view class="page-head">
        <text class="head-title">预约与服务进度</text>
        <text class="head-sub">选择服务方式与时间发起预约；开始前 5 分钟外可自行改期或取消</text>
      </view>

      <view class="seg-bar">
        <view
          v-for="item in segs"
          :key="item.key"
          class="seg-item"
          :class="{ active: seg === item.key }"
          @click="seg = item.key"
        >
          <text class="seg-text">{{ item.label }}</text>
        </view>
      </view>

      <template v-if="seg === 'mine'">
        <AppButton v-if="formOpen" variant="secondary" size="md" block @click="formOpen = false">收起预约表单</AppButton>
        <AppButton v-else variant="primary" size="md" block @click="openForm">发起新预约</AppButton>

        <!-- 预约表单：服务方式 + 日期 + 开始时间 + 时长 -->
        <view v-if="formOpen" class="card form-card">
          <view class="field">
            <text class="field-label">服务方式</text>
            <picker mode="selector" :range="methodOptions" range-key="label" :value="methodIndex" @change="onMethodChange">
              <view class="picker-trigger">
                <text class="picker-value">{{ methodOptions[methodIndex].label }}</text>
                <text class="picker-arrow">▾</text>
              </view>
            </picker>
          </view>

          <view class="field">
            <text class="field-label">日期</text>
            <picker mode="date" :value="form.date" :start="today" @change="onDateChange">
              <view class="picker-trigger">
                <text class="picker-value">{{ form.date }}</text>
                <text class="picker-arrow">▾</text>
              </view>
            </picker>
          </view>

          <view class="field-row">
            <view class="field field-half">
              <text class="field-label">开始时间</text>
              <picker mode="time" :value="form.startTime" @change="onStartTimeChange">
                <view class="picker-trigger">
                  <text class="picker-value">{{ form.startTime }}</text>
                </view>
              </picker>
            </view>
            <view class="field field-half">
              <text class="field-label">时长</text>
              <picker mode="selector" :range="durationOptions" range-key="label" :value="durationIndex" @change="onDurationChange">
                <view class="picker-trigger">
                  <text class="picker-value">{{ durationOptions[durationIndex].label }}</text>
                </view>
              </picker>
            </view>
          </view>

          <view v-if="needLocation" class="field">
            <text class="field-label">地点名称</text>
            <input v-model="form.locationName" class="field-input" placeholder="公司门店或拜访地点" placeholder-class="ph" />
          </view>

          <view v-if="needLocation" class="field">
            <text class="field-label">详细地址</text>
            <input v-model="form.locationDetail" class="field-input" placeholder="详细地址（选填）" placeholder-class="ph" />
          </view>

          <view class="field">
            <text class="field-label">补充说明</text>
            <input v-model="form.customerVisibleNote" class="field-input" placeholder="希望顾问提前准备的资料（选填）" placeholder-class="ph" />
          </view>

          <AppButton variant="primary" size="md" block :loading="submitting" @click="submitForm">提交预约</AppButton>
        </view>

        <AppSkeleton v-if="loading && !list.length" :rows="3" />
        <AppEmpty v-else-if="!loading && !list.length" title="暂无预约" desc="发起预约后，服务顾问会与你确认时间与地点" />

        <view v-else class="appt-list">
          <view v-for="item in list" :key="item.appointmentNo" class="card appt-card">
            <view class="appt-head">
              <text class="appt-method">{{ item.serviceMethodName || methodLabel(item.serviceMethod) }}</text>
              <AppTag :type="statusTone(item.status)" size="sm">{{ item.statusName || statusLabel(item.status) }}</AppTag>
            </view>
            <text class="appt-time">{{ formatRange(item.scheduledStart, item.scheduledEnd) }}</text>
            <text v-if="item.adviserName" class="appt-meta">服务顾问：{{ item.adviserName }}</text>
            <text v-if="item.locationName" class="appt-meta">地点：{{ item.locationName }}</text>
            <text v-if="item.nextActionText || item.nextAction" class="appt-next">{{ item.nextActionText || item.nextAction }}</text>
            <text v-if="item.customerNote" class="appt-note">你的补充说明：{{ item.customerNote }}</text>

            <view v-if="actionsOf(item).length" class="appt-actions">
              <AppButton
                v-for="action in actionsOf(item)"
                :key="action.key"
                :variant="action.variant"
                size="sm"
                @click="onAction(item, action.key)"
              >
                {{ action.label }}
              </AppButton>
            </view>
          </view>
        </view>
      </template>

      <template v-else>
        <AppEmpty v-if="!timelineLoading && !timeline.length" title="暂无服务记录"
          desc="预约、到店、资料与服务进度会在这里按时间沉淀" />
        <view v-else class="card tl-card">
          <view v-for="item in timeline" :key="item.eventNo" class="tl-item">
            <view class="tl-dot" />
            <view class="tl-body">
              <text class="tl-sum">{{ item.summary || eventLabel(item.eventType) }}</text>
              <text class="tl-time">{{ formatTime(item.happenedAt) }}</text>
            </view>
          </view>
        </view>
      </template>
    </template>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { useThemeMode } from '../../theme';
import { useUserStore } from '../../store/user';
import {
  myAppointments, requestAppointment, confirmAppointment,
  cancelAppointment, rescheduleAppointment, checkInAppointment, serviceTimeline,
} from '../../api/appointment';

/** 服务方式与后端 ServiceMethod 枚举一一对应。 */
const METHOD_OPTIONS = [
  { value: 'COMPANY_ON_SITE', label: '客户到访我司' },
  { value: 'HOME_VISIT', label: '顾问上门拜访您' },
  { value: 'VIDEO_MEETING', label: '视频沟通' },
  { value: 'PHONE_CONSULT', label: '电话沟通' },
];
const DURATION_OPTIONS = [
  { value: 30, label: '30 分钟' },
  { value: 60, label: '1 小时' },
  { value: 90, label: '1.5 小时' },
  { value: 120, label: '2 小时' },
];
const STATUS_TEXT = {
  REQUESTED: '待顾问确认', CONFIRMED: '已预约', ARRIVED: '已到店',
  SERVING: '服务中', COMPLETED: '已完成', CANCELLED: '已取消',
  NO_SHOW: '未到场', RESCHEDULED: '已改期',
};
const STATUS_TONE = {
  COMPLETED: 'success', CANCELLED: 'danger', NO_SHOW: 'danger',
  REQUESTED: 'warning', RESCHEDULED: 'warning',
};
const EVENT_TEXT = {
  INSIGHT_GENERATED: '经营分析已更新', CUSTOMER_ARRIVED: '已到店',
  CUSTOMER_CHECKED_IN: '已签到到店', SERVICE_STARTED: '服务已开始',
  SERVICE_COMPLETED: '服务已完成', APPOINTMENT_CANCELLED: '预约已取消',
};

const themeMode = useThemeMode();
const store = useUserStore();
const canView = computed(() => store.role === 'customer');

const seg = ref('mine');
const segs = [
  { key: 'mine', label: '我的预约' },
  { key: 'progress', label: '服务进度' },
];
const methodOptions = METHOD_OPTIONS;
const durationOptions = DURATION_OPTIONS;
const methodIndex = ref(0);
const durationIndex = ref(1);
const formOpen = ref(false);
const submitting = ref(false);
const loading = ref(false);
const list = ref([]);
const timeline = ref([]);
const timelineLoading = ref(false);

const pad = (value) => String(value).padStart(2, '0');
const todayText = () => {
  const now = new Date();
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
};
/** 日期选择器不允许选过去，起点取当天。 */
const today = todayText();

const form = reactive({
  date: todayText(),
  startTime: '10:00',
  locationName: '',
  locationDetail: '',
  customerVisibleNote: '',
});

const method = computed(() => METHOD_OPTIONS[methodIndex.value].value);
const needLocation = computed(() => method.value === 'COMPANY_ON_SITE' || method.value === 'HOME_VISIT');

function methodLabel(value) {
  const hit = METHOD_OPTIONS.find((item) => item.value === value);
  return hit ? hit.label : value;
}
function statusLabel(status) {
  return STATUS_TEXT[status] || status;
}
function eventLabel(type) {
  return EVENT_TEXT[type] || type;
}
function statusTone(status) {
  return STATUS_TONE[status] || 'info';
}
function formatTime(value) {
  if (!value) return '';
  return String(value).replace('T', ' ').replace(/:\d{2}$/, '');
}
function formatRange(start, end) {
  const s = formatTime(start);
  const e = formatTime(end);
  return e ? `${s} — ${e}` : s;
}

/** 与后端约束保持一致：仅公司现场预约可自助签到，未开始的预约可改期与取消。 */
function actionsOf(item) {
  const actions = [];
  if (item.status === 'CONFIRMED' && item.serviceMethod === 'COMPANY_ON_SITE') {
    actions.push({ key: 'checkIn', label: '到店签到', variant: 'primary' });
  }
  if (item.changeAllowed !== false && ['REQUESTED', 'CONFIRMED'].includes(item.status)) {
    actions.push({ key: 'reschedule', label: '改期', variant: 'secondary' });
    actions.push({ key: 'cancel', label: '取消', variant: 'secondary' });
  }
  return actions;
}

async function loadMine() {
  if (!canView.value) return;
  loading.value = true;
  try {
    const data = await myAppointments({ page: 1, size: 20 });
    list.value = (data && data.records) || [];
  } catch (e) {
    list.value = list.value || [];
  } finally {
    loading.value = false;
  }
}

async function loadTimeline() {
  if (!canView.value) return;
  timelineLoading.value = true;
  try {
    const data = await serviceTimeline({ page: 1, size: 20 });
    timeline.value = (data && data.records) || [];
  } catch (e) {
    timeline.value = timeline.value || [];
  } finally {
    timelineLoading.value = false;
  }
}

function reload() {
  return Promise.all([loadMine(), loadTimeline()]);
}

function openForm() {
  formOpen.value = true;
}
function onMethodChange(e) {
  methodIndex.value = Number(e.detail.value);
}
function onDurationChange(e) {
  durationIndex.value = Number(e.detail.value);
}
function onDateChange(e) {
  form.date = e.detail.value;
}
function onStartTimeChange(e) {
  form.startTime = e.detail.value;
}

/** 起止时间由「日期 + 开始时间 + 时长」推导，保证结束晚于开始。 */
function timeRange() {
  const start = `${form.date}T${form.startTime}:00`;
  const startDate = new Date(start);
  const endDate = new Date(startDate.getTime() + DURATION_OPTIONS[durationIndex.value].value * 60 * 1000);
  const end = `${endDate.getFullYear()}-${pad(endDate.getMonth() + 1)}-${pad(endDate.getDate())}T`
    + `${pad(endDate.getHours())}:${pad(endDate.getMinutes())}:00`;
  return { start, end };
}

async function submitForm() {
  if (needLocation.value && !form.locationName.trim()) {
    uni.showToast({ title: '请填写地点名称', icon: 'none' });
    return;
  }
  const { start, end } = timeRange();
  submitting.value = true;
  try {
    await requestAppointment({
      serviceMethod: method.value,
      scheduledStart: start,
      scheduledEnd: end,
      locationName: form.locationName.trim(),
      locationDetail: form.locationDetail.trim(),
      customerVisibleNote: form.customerVisibleNote.trim(),
    });
    uni.showToast({ title: '预约已提交，等待顾问确认', icon: 'none' });
    formOpen.value = false;
    await reload();
  } catch (e) {
    // 时间冲突、地点必填等具体原因由请求层统一提示。
  } finally {
    submitting.value = false;
  }
}

function confirmDialog(content) {
  return new Promise((resolve) => {
    uni.showModal({
      title: '请确认',
      content,
      success: (res) => resolve(!!res.confirm),
      fail: () => resolve(false),
    });
  });
}

/** 改期沿用原服务方式与地点，只改时间；服务端仍会做冲突校验与 5 分钟门禁。 */
function pickNewTime(item) {
  const startDate = new Date(String(item.scheduledStart).replace(' ', 'T'));
  const endDate = new Date(String(item.scheduledEnd).replace(' ', 'T'));
  const minutes = Math.max(Math.round((endDate - startDate) / 60000), 30);
  const hint = `${startDate.getFullYear()}-${pad(startDate.getMonth() + 1)}-${pad(startDate.getDate())} `
    + `${pad(startDate.getHours())}:${pad(startDate.getMinutes())}`;
  return new Promise((resolve) => {
    uni.showModal({
      title: '改期时间',
      editable: true,
      content: hint,
      placeholderText: hint,
      success: (res) => {
        if (!res.confirm) return resolve(null);
        const parsed = parseDateTime(String(res.content || '').trim());
        if (!parsed) {
          uni.showToast({ title: '请按 2026-09-21 10:00 格式填写', icon: 'none' });
          return resolve(null);
        }
        const end = new Date(parsed.moment.getTime() + minutes * 60000);
        const endText = `${end.getFullYear()}-${pad(end.getMonth() + 1)}-${pad(end.getDate())}T`
          + `${pad(end.getHours())}:${pad(end.getMinutes())}:00`;
        return resolve({ scheduledStart: `${parsed.date}T${parsed.time}:00`, scheduledEnd: endText });
      },
      fail: () => resolve(null),
    });
  });
}

function parseDateTime(text) {
  const hit = /^(\d{4}-\d{2}-\d{2})[ T](\d{2}:\d{2})$/.exec(text);
  if (!hit) return null;
  const moment = new Date(`${hit[1]}T${hit[2]}:00`);
  if (Number.isNaN(moment.getTime()) || moment.getTime() < Date.now()) return null;
  return { date: hit[1], time: hit[2], moment };
}

async function onAction(item, key) {
  try {
    if (key === 'confirm') {
      await confirmAppointment(item.appointmentNo);
      uni.showToast({ title: '已确认预约', icon: 'none' });
    } else if (key === 'checkIn') {
      await checkInAppointment(item.appointmentNo);
      uni.showToast({ title: '签到成功，请到前台登记', icon: 'none' });
    } else if (key === 'cancel') {
      const ok = await confirmDialog('取消后需要重新预约，确认取消本次预约？');
      if (!ok) return;
      await cancelAppointment(item.appointmentNo, '客户在小程序取消');
      uni.showToast({ title: '预约已取消', icon: 'none' });
    } else if (key === 'reschedule') {
      const target = await pickNewTime(item);
      if (!target) return;
      await rescheduleAppointment(item.appointmentNo, target);
      uni.showToast({ title: '已改期，等待顾问确认', icon: 'none' });
    }
    await reload();
  } catch (e) {
    // 开始前 5 分钟内的变更会被服务端拒绝，提示语由请求层给出。
  }
}

onShow(() => {
  reload();
});
</script>

<style scoped>
.appt-page { min-height: 100vh; padding: var(--space-page-gutter); padding-bottom: var(--space-16); background: var(--bg-page); }
.page-head { display: flex; flex-direction: column; gap: var(--space-1); margin-bottom: var(--space-4); }
.head-title { font-size: 40rpx; font-weight: 600; color: var(--text-primary); }
.head-sub { font-size: 24rpx; color: var(--text-secondary); line-height: 1.6; }
.seg-bar { display: flex; padding: 6rpx; margin-bottom: var(--space-3); border-radius: var(--radius-md); background: var(--bg-input); }
.seg-item { flex: 1; padding: 16rpx 0; text-align: center; border-radius: var(--radius-sm); }
.seg-item.active { background: var(--bg-card); }
.seg-text { font-size: 26rpx; color: var(--text-secondary); }
.seg-item.active .seg-text { color: var(--brand-deep); font-weight: 500; }
.card { padding: var(--space-card-pad); margin-top: var(--space-3); border: 2rpx solid var(--line); border-radius: var(--radius-md); background: var(--bg-card); }
.form-card { display: flex; flex-direction: column; gap: var(--space-field); }
.field { display: flex; flex-direction: column; gap: var(--space-1); }
.field-row { display: flex; gap: var(--space-3); }
.field-half { flex: 1; }
.field-label { font-size: 24rpx; color: var(--text-secondary); }
.field-input, .picker-trigger { padding: 20rpx 24rpx; font-size: 28rpx; color: var(--text-body); border: 2rpx solid var(--line); border-radius: var(--radius-sm); background: var(--bg-input); }
.picker-trigger { display: flex; align-items: center; justify-content: space-between; }
.picker-value { font-size: 28rpx; color: var(--text-body); }
.picker-arrow { font-size: 24rpx; color: var(--text-placeholder); }
.ph { color: var(--text-placeholder); }
.appt-list { margin-top: var(--space-2); }
.appt-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: var(--space-1); }
.appt-method { font-size: 30rpx; font-weight: 500; color: var(--text-primary); }
.appt-time { font-size: 26rpx; color: var(--text-body); }
.appt-meta { display: block; margin-top: 6rpx; font-size: 24rpx; color: var(--text-secondary); }
.appt-next { display: block; margin-top: var(--space-2); font-size: 24rpx; color: var(--brand-deep); }
.appt-actions { display: flex; flex-wrap: wrap; gap: var(--space-2); margin-top: var(--space-3); }
.tl-card { display: flex; flex-direction: column; gap: var(--space-3); }
.tl-item { display: flex; gap: var(--space-3); }
.tl-dot { width: 16rpx; height: 16rpx; margin-top: 10rpx; border-radius: 50%; background: var(--brand-deep); }
.tl-body { flex: 1; display: flex; flex-direction: column; gap: 4rpx; }
.tl-sum { font-size: 26rpx; color: var(--text-body); }
.tl-time { font-size: 22rpx; color: var(--text-placeholder); }
</style>
