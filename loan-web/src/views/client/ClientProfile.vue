<template>
  <div class="client-profile-page">
    <div class="loan-page-header">
      <div>
        <h2 class="loan-page-title">客户档案</h2>
        <p class="loan-page-subtitle">客户编码 {{ clientCode || '—' }} · 三端联动（小程序认证 / 顾问跟进 / 管理端留痕）</p>
      </div>
      <div class="header-actions">
        <el-button type="primary" @click="goScreening">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" style="margin-right: 4px; vertical-align: -2px"><path d="M5 3l14 9-14 9V3z"/></svg>
          发起初筛
        </el-button>
        <el-button :loading="saving" @click="openEdit">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" style="margin-right: 4px; vertical-align: -2px"><path d="M17 3a2.8 2.8 0 114 4L7.5 20.5 2 22l1.5-5.5L17 3z"/></svg>
          编辑档案
        </el-button>
      </div>
    </div>

    <div v-loading="loading" class="profile-body">
      <!-- ① 基础信息 -->
      <div class="loan-card section-card">
        <h3 class="panel-title">基础信息</h3>
        <el-descriptions :column="3" border>
          <el-descriptions-item label="客户编码">{{ detail.clientCode || '—' }}</el-descriptions-item>
          <el-descriptions-item label="客户姓名">{{ detail.name || '—' }}</el-descriptions-item>
          <el-descriptions-item label="手机号">{{ desensitizePhone(detail.phone) }}</el-descriptions-item>
          <el-descriptions-item label="来源">
            <span class="loan-tag" :class="sourceTag(detail.source)">{{ sourceText(detail.source) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="归属顾问">{{ detail.ownerStaffCode || '—' }}</el-descriptions-item>
          <el-descriptions-item label="认证状态">
            <span class="loan-tag" :class="authStatusTag(detail.authStatus)">{{ authStatusText(detail.authStatus) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="邀请链（引荐人）" :span="3">
            <span v-if="referrerText !== '-'">{{ referrerText }}<span v-if="detail.referrerType" class="cell-sub">（{{ referrerTypeText(detail.referrerType) }}）</span></span>
            <span v-else class="muted">—</span>
          </el-descriptions-item>
        </el-descriptions>
      </div>

      <!-- ② 企业档案 ↔ 个人档案 -->
      <div class="loan-card section-card">
        <el-tabs v-model="profileTab">
          <el-tab-pane label="企业档案" name="enterprise">
            <el-descriptions :column="2" border>
              <el-descriptions-item label="企业名称">{{ detail.enterpriseName || '—' }}</el-descriptions-item>
              <el-descriptions-item label="统一社会信用代码">{{ desensitizeCreditCode(detail.creditCode) }}</el-descriptions-item>
            </el-descriptions>
          </el-tab-pane>
          <el-tab-pane label="个人档案" name="personal">
            <el-descriptions :column="3" border>
              <el-descriptions-item label="姓名">{{ desensitizeName(detail.realName) }}</el-descriptions-item>
              <el-descriptions-item label="身份证号">{{ desensitizeIdCard(detail.idCardNo) }}</el-descriptions-item>
              <el-descriptions-item label="城市">{{ detail.city || '—' }}</el-descriptions-item>
              <el-descriptions-item label="年龄">{{ detail.age ?? '—' }}</el-descriptions-item>
              <el-descriptions-item label="房产">{{ flagText(detail.houseFlag) }}</el-descriptions-item>
              <el-descriptions-item label="车辆">{{ flagText(detail.carFlag) }}</el-descriptions-item>
              <el-descriptions-item label="社保">{{ flagText(detail.socialSecurityFlag) }}</el-descriptions-item>
              <el-descriptions-item label="公积金">{{ flagText(detail.fundFlag) }}</el-descriptions-item>
            </el-descriptions>
          </el-tab-pane>
        </el-tabs>
      </div>

      <!-- ③ 审计信息 -->
      <div class="loan-card section-card">
        <h3 class="panel-title">审计信息</h3>
        <el-descriptions :column="4" border>
          <el-descriptions-item label="创建人">{{ detail.createdBy || '—' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatDateTime(detail.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="最近更新人">{{ detail.updatedBy || '—' }}</el-descriptions-item>
          <el-descriptions-item label="最近更新时间">{{ formatDateTime(detail.updatedAt) }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </div>

    <!-- 编辑弹窗：基础信息 + 个人档案合并编辑 -->
    <AppDialog v-model:visible="editVisible" title="编辑客户档案" width="640px" :loading="saving" @confirm="onSave">
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="110px" label-position="right">
        <el-divider content-position="left">基础信息</el-divider>
        <el-form-item label="客户姓名" prop="name">
          <el-input v-model="editForm.name" placeholder="客户姓名" />
        </el-form-item>
        <el-form-item label="来源" prop="source">
          <el-select v-model="editForm.source" clearable placeholder="来源" style="width: 100%">
            <el-option v-for="(v, k) in sourceMap" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="归属顾问" prop="ownerStaffCode">
          <el-input v-model="editForm.ownerStaffCode" placeholder="员工工号（业务编码）" />
        </el-form-item>
        <el-divider content-position="left">个人档案</el-divider>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="editForm.realName" placeholder="实名姓名" />
        </el-form-item>
        <el-form-item label="身份证号" prop="idCardNo">
          <el-input v-model="editForm.idCardNo" placeholder="身份证号（录入后加密存储）" />
        </el-form-item>
        <el-form-item label="城市">
          <el-input v-model="editForm.city" placeholder="如 武汉市" />
        </el-form-item>
        <el-form-item label="年龄">
          <el-input-number v-model="editForm.age" :min="18" :max="100" :controls="false" style="width: 100%" />
        </el-form-item>
        <div class="flag-grid">
          <el-form-item v-for="f in flagFields" :key="f.key" :label="f.label">
            <el-select v-model="editForm[f.key]" clearable placeholder="未填写" style="width: 100%">
              <el-option label="是" :value="1" />
              <el-option label="否" :value="0" />
            </el-select>
          </el-form-item>
        </div>
      </el-form>
    </AppDialog>
  </div>
</template>

<script setup>
defineOptions({ name: '_client_profile' });
import { ref, reactive, computed, watch, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import AppDialog from '@/components/AppDialog.vue';
import { formatDateTime, desensitizePhone } from '@/utils/format';
import { getClientDetail, updateClientDetail } from '@/api/client';

const route = useRoute();
const router = useRouter();

const clientCode = ref('');
const loading = ref(false);
const profileTab = ref('enterprise');

/** 档案详情（后端已脱敏敏感字段，前端再做一层兜底展示） */
const detail = reactive({
  clientCode: '',
  name: '',
  phone: '',
  source: '',
  ownerStaffCode: '',
  authStatus: '',
  enterpriseName: '',
  creditCode: '',
  realName: '',
  idCardNo: '',
  city: '',
  age: null,
  houseFlag: null,
  carFlag: null,
  socialSecurityFlag: null,
  fundFlag: null,
  referrer: '',
  referrerType: '',
  createdBy: '',
  createdAt: '',
  updatedBy: '',
  updatedAt: '',
});

/** 拉取档案详情（兼容后端平铺 / 嵌套结构，逐字段兜底） */
async function loadDetail(code) {
  loading.value = true;
  try {
    const res = await getClientDetail(code);
    const d = res.data || {};
    const ent = d.enterprise || d.enterpriseInfo || {};
    const per = d.personal || d.personalProfile || {};
    Object.assign(detail, {
      clientCode: d.clientCode || code,
      name: d.name || per.realName || ent.enterpriseName || '',
      phone: d.phone,
      source: d.source,
      ownerStaffCode: d.ownerStaffCode,
      authStatus: d.authStatus ?? d.certStatus ?? d.verificationStatus,
      enterpriseName: ent.enterpriseName ?? d.enterpriseName,
      creditCode: ent.creditCode ?? d.creditCode,
      realName: per.realName ?? d.realName,
      idCardNo: per.idCardNo ?? d.idCardNo,
      city: per.city ?? d.city,
      age: per.age ?? d.age,
      houseFlag: per.houseFlag ?? d.houseFlag,
      carFlag: per.carFlag ?? d.carFlag,
      socialSecurityFlag: per.socialSecurityFlag ?? d.socialSecurityFlag,
      fundFlag: per.fundFlag ?? d.fundFlag,
      referrer: d.referrer ?? d.invitation?.referrer ?? d.referrerName,
      referrerType: d.referrerType ?? d.invitation?.referrerType,
      createdBy: d.createdBy,
      createdAt: d.createdAt,
      updatedBy: d.updatedBy,
      updatedAt: d.updatedAt,
    });
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false;
  }
}

// ============================================================
// 枚举映射与脱敏兜底（后端已脱敏则原样展示）
// ============================================================
const sourceMap = { MINI: '小程序注册', INVITE: '小程序·邀请', WEB: 'Web 录入', BOSS: '老板', ADVISER: '顾问', CHANNEL: '渠道', VIP: 'VIP 客户' };
function sourceText(code) {
  return sourceMap[code] || code || '-';
}
function sourceTag(code) {
  const m = { MINI: 'loan-tag-info', INVITE: 'loan-tag-primary', WEB: 'loan-tag-muted', BOSS: 'loan-tag-muted', ADVISER: 'loan-tag-primary', CHANNEL: 'loan-tag-info', VIP: 'loan-tag-warning' };
  return m[code] || 'loan-tag-muted';
}

const authStatusMap = { VERIFIED: '已认证', SUCCESS: '已认证', ACTIVE: '已认证', PENDING: '待复核', FAIL: '认证失败', UNVERIFIED: '未认证', NONE: '未认证' };
function authStatusText(code) {
  return authStatusMap[code] || (code ? code : '未认证');
}
function authStatusTag(code) {
  const m = { VERIFIED: 'loan-tag-success', SUCCESS: 'loan-tag-success', ACTIVE: 'loan-tag-success', PENDING: 'loan-tag-warning', FAIL: 'loan-tag-danger', UNVERIFIED: 'loan-tag-muted', NONE: 'loan-tag-muted' };
  return m[code] || 'loan-tag-muted';
}

const referrerTypeMap = { STAFF: '顾问', ADVISER: '顾问', CUSTOMER: '客户', VIP: 'VIP 客户' };
function referrerTypeText(code) {
  return referrerTypeMap[code] || code || '';
}
const referrerText = computed(() => {
  const r = detail.referrer;
  if (!r) return '-';
  if (typeof r === 'string') return r;
  if (typeof r === 'object') return r.name || r.referrerName || r.referrerClientCode || r.staffName || '-';
  return String(r);
});

function flagText(v) {
  if (v === null || v === undefined || v === '') return '—';
  if (v === true || v === 'true' || v === 'Y' || v === 'y' || v === '1' || Number(v) === 1) return '是';
  return '否';
}
/** 身份证脱敏：18 位身份证 前3后4 星号；后端已脱敏（含*）则原样返回 */
function desensitizeIdCard(v) {
  if (!v) return '-';
  if (/[*＊]/.test(v)) return v;
  const s = String(v).trim();
  if (/^\d{15}(\d{2}[\dXx])?$/.test(s)) return `${s.slice(0, 3)}********${s.slice(-4)}`;
  return s;
}
/** 姓名脱敏：2 字「*名」，3 字「*名」，其余首尾保留 */
function desensitizeName(v) {
  if (!v) return '-';
  if (/[*＊]/.test(v)) return v;
  const s = String(v).trim();
  if (s.length <= 1) return s;
  if (s.length === 2) return `*${s.slice(-1)}`;
  return `${s[0]}${'*'.repeat(s.length - 2)}${s.slice(-1)}`;
}
/** 统一社会信用代码脱敏：18 位 前3后4 */
function desensitizeCreditCode(v) {
  if (!v) return '-';
  if (/[*＊]/.test(v)) return v;
  const s = String(v).trim();
  if (s.length >= 12) return `${s.slice(0, 3)}********${s.slice(-4)}`;
  return s;
}

// ============================================================
// 编辑档案
// ============================================================
const editVisible = ref(false);
const saving = ref(false);
const editFormRef = ref();
const editForm = reactive({
  name: '',
  source: '',
  ownerStaffCode: '',
  realName: '',
  idCardNo: '',
  city: '',
  age: null,
  houseFlag: null,
  carFlag: null,
  socialSecurityFlag: null,
  fundFlag: null,
});
const flagFields = [
  { key: 'houseFlag', label: '房产' },
  { key: 'carFlag', label: '车辆' },
  { key: 'socialSecurityFlag', label: '社保' },
  { key: 'fundFlag', label: '公积金' },
];
const editRules = {};

function openEdit() {
  Object.assign(editForm, {
    name: detail.name,
    source: detail.source,
    ownerStaffCode: detail.ownerStaffCode,
    realName: detail.realName,
    idCardNo: detail.idCardNo,
    city: detail.city,
    age: detail.age ?? null,
    houseFlag: detail.houseFlag ?? null,
    carFlag: detail.carFlag ?? null,
    socialSecurityFlag: detail.socialSecurityFlag ?? null,
    fundFlag: detail.fundFlag ?? null,
  });
  editVisible.value = true;
}

async function onSave() {
  await editFormRef.value.validate();
  saving.value = true;
  try {
    await updateClientDetail(clientCode.value, { ...editForm });
    ElMessage.success('档案已更新');
    editVisible.value = false;
    loadDetail(clientCode.value);
  } catch (e) {
    // 拦截器已提示
  } finally {
    saving.value = false;
  }
}

/** 发起初筛：跳初筛中心并预填客户 */
function goScreening() {
  router.push({ path: '/screening', query: { clientCode: clientCode.value } });
}

// 路由参数 clientCode（query 或 path 参数均可）变化时重载
watch(
  () => route.query.clientCode || route.params.clientCode,
  (code) => {
    if (code) {
      clientCode.value = code;
      loadDetail(code);
    }
  },
  { immediate: true },
);

onMounted(() => {
  const code = route.query.clientCode || route.params.clientCode;
  if (code) {
    clientCode.value = code;
    loadDetail(code);
  }
});
</script>

<style scoped>
.header-actions {
  display: flex;
  gap: 10px;
}
.profile-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 200px;
}
.section-card {
  padding: 18px 22px;
}
.cell-sub {
  font-size: 12px;
  color: var(--loan-text-secondary, #8a94a6);
}
.muted {
  color: var(--loan-text-secondary, #8a94a6);
}
.flag-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
}
</style>
