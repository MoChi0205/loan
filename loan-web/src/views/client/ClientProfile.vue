<template>
  <div class="client-profile-page">
    <div class="loan-page-header">
      <div>
        <h2 class="loan-page-title">{{ isChannel && !clientCode ? '我的客户' : (detail.enterpriseName || detail.realName || detail.name || '客户档案') }}</h2>
        <p class="loan-page-subtitle">{{ isChannel ? '本人录入客户 · 档案只读 · 服务归属' : '客户资料 · 认证信息 · 服务归属 · 操作留痕' }}</p>
      </div>
      <div class="header-actions">
        <el-button v-if="isChannel && clientCode" @click="backToChannelClients">返回我的客户</el-button>
        <el-button v-if="userStore.hasPerm(ACTION_PERMISSION.CLIENT_SCREENING)" type="primary" :disabled="!clientCode" @click="goScreening">
          <AppIcon name="screening" :size="14" />
          发起初筛
        </el-button>
        <el-button v-if="userStore.hasPerm(ACTION_PERMISSION.CLIENT_UPDATE)" :loading="saving" :disabled="!clientCode" @click="openEdit">
          <AppIcon name="edit" :size="14" />
          编辑档案
        </el-button>
        <el-button v-if="userStore.hasPerm(ACTION_PERMISSION.CLIENT_ASSIGN)" type="warning" plain @click="openAssign">分配归属</el-button>
        <el-button v-if="userStore.hasPerm(ACTION_PERMISSION.CLIENT_RECYCLE)" type="danger" plain :disabled="!detail.ownerStaffCode" @click="onRecycle">回收进公海</el-button>
      </div>
    </div>

    <div v-if="!clientCode && !loading" class="profile-empty loan-card">
      <template v-if="isChannel">
        <AppSearchBar :loading="listLoading" @search="searchClients" @reset="resetClients">
          <el-input v-model="clientQuery.keyword" placeholder="客户姓名 / 企业名称 / 手机号" clearable style="width: 260px" @keyup.enter="searchClients" />
        </AppSearchBar>
        <el-table :data="clientRows" v-loading="listLoading" stripe row-key="clientCode">
          <template #empty><AppEmpty title="暂无客户档案" desc="本人录入的线索转化为客户后会显示在这里" /></template>
          <el-table-column prop="clientName" label="客户" min-width="180" />
          <el-table-column prop="phone" label="手机号" width="130" />
          <el-table-column label="归属顾问" width="140"><template #default="{ row }">{{ row.ownerStaffName || '待分配' }}</template></el-table-column>
          <el-table-column prop="createdAt" label="建档时间" width="170"><template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template></el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <AppTableActions :actions="[{ key: 'detail', label: '查看档案', onClick: () => openChannelClient(row) }]" />
            </template>
          </el-table-column>
        </el-table>
        <AppPagination v-model:page="clientQuery.page" v-model:size="clientQuery.size" :total="clientTotal" @change="loadClients" />
      </template>
      <AppEmpty v-else title="请选择客户" desc="从客户列表选择一条客户档案后查看详情" />
    </div>
    <div v-else v-loading="loading" class="profile-body">
      <!-- ① 基础信息 -->
      <div class="loan-card section-card">
        <h3 class="panel-title">基础信息</h3>
        <el-descriptions :column="3" border>
          <el-descriptions-item label="客户姓名">{{ detail.name || '—' }}</el-descriptions-item>
          <el-descriptions-item label="手机号">{{ desensitizePhone(detail.phone) }}</el-descriptions-item>
          <el-descriptions-item label="来源">
            <span class="loan-tag" :class="sourceTag(detail.source)">{{ sourceText(detail.source) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="归属顾问">{{ detail.ownerStaffName || (detail.ownerStaffCode ? '姓名待补充' : '待分配') }}</el-descriptions-item>
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
      <div v-if="!isChannel" class="loan-card section-card">
        <h3 class="panel-title">审计信息</h3>
        <el-descriptions :column="4" border>
          <el-descriptions-item label="创建人">{{ detail.createdByName || detail.createdBy || '—' }}</el-descriptions-item>
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

    <!-- 分配归属弹窗：角色门控（DEPT_MANAGER/BOSS/OPERATOR/SUPER_ADMIN/SUPER） -->
    <AppDialog v-model:visible="assignVisible" title="分配归属" width="560px" :loading="assigning" @confirm="onAssignConfirm">
      <el-form label-width="84px" label-position="right">
        <el-form-item label="目标角色">
          <el-radio-group v-model="assignRole" @change="onAssignRoleChange">
            <el-radio value="ADVISER">顾问</el-radio>
            <el-radio value="DEPT_MANAGER">团队管理者</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="归属人">
          <el-select
            v-model="assignTarget"
            filterable
            remote
            :remote-method="searchAssignStaff"
            :loading="assignLoading"
            placeholder="输入姓名搜索"
            style="width: 100%"
            @visible-change="(v) => { if (v) searchAssignStaff('') }"
          >
            <el-option
              v-for="o in assignOptions"
              :key="o.staffCode"
              :label="staffDisplayLabel(o)"
              :value="o.staffCode"
              :disabled="o.status && o.status !== 'ACTIVE'"
            />
          </el-select>
        </el-form-item>
      </el-form>
    </AppDialog>
  </div>
</template>

<script setup>
defineOptions({ name: '_client_profile' });
import { ref, reactive, computed, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import AppDialog from '@/components/AppDialog.vue';
import AppIcon from '@/components/AppIcon.vue';
import AppEmpty from '@/components/AppEmpty.vue';
import AppSearchBar from '@/components/AppSearchBar.vue';
import AppPagination from '@/components/AppPagination.vue';
import { formatDateTime, desensitizePhone } from '@/utils/format';
import { getClientDetail, pageClients, updateClientDetail, assignClient, recycleClient } from '@/api/client';
import { staffPage } from '@/api/org';
import { useUserStore } from '@/store/user';
import { useTable } from '@/composables/useTable';
import { staffDisplayLabel } from '@/utils/display';
import { ACTION_PERMISSION } from '@/utils/access';
import AppTableActions from '@/components/AppTableActions.vue';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const isChannel = computed(() => userStore.roleCode === 'CHANNEL');
const clientCode = ref('');
const loading = ref(false);
const profileTab = ref('enterprise');
const {
  loading: listLoading,
  data: clientRows,
  total: clientTotal,
  query: clientQuery,
  load: loadClients,
  onSearch: searchClients,
  onReset: resetClients,
} = useTable(pageClients, { keyword: '' });
function openChannelClient(row) {
  router.push({ path: '/client', query: { clientCode: row.clientCode } });
}
function backToChannelClients() {
  router.push({ path: '/client' });
}

/** 档案详情（后端已脱敏敏感字段，前端再做一层兜底展示） */
const detail = reactive({
  clientCode: '',
  name: '',
  phone: '',
  source: '',
  ownerStaffCode: '',
  ownerStaffName: '',
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
  createdByName: '',
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
      ownerStaffName: d.ownerStaffName,
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
      createdByName: d.createdByName || d.creatorName,
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

// ============================================================
// 分配归属 / 回收（C23 / C26，角色门控：DEPT_MANAGER/BOSS/OPERATOR/SUPER_ADMIN/SUPER）
// ============================================================
const assignVisible = ref(false);
const assigning = ref(false);
const assignRole = ref('ADVISER');
const assignTarget = ref('');
const assignOptions = ref([]);
const assignLoading = ref(false);
let assignSeq = 0;
let assignTimer;

function openAssign() {
  assignRole.value = 'ADVISER';
  assignTarget.value = '';
  assignOptions.value = [];
  assignVisible.value = true;
  searchAssignStaff('');
}
function onAssignRoleChange() {
  assignTarget.value = '';
  searchAssignStaff('');
}
function searchAssignStaff(keyword) {
  clearTimeout(assignTimer);
  assignTimer = setTimeout(async () => {
    const seq = ++assignSeq;
    assignLoading.value = true;
    try {
      const res = await staffPage({ roleCode: assignRole.value, keyword: keyword.trim() || undefined, page: 1, size: 50 });
      if (seq !== assignSeq) return;
      assignOptions.value = (res.data?.records || []).map((s) => ({
        staffCode: s.staffCode,
        staffName: s.staffName,
        deptName: s.deptName,
        status: s.status,
      }));
    } catch (e) {
      if (seq === assignSeq) assignOptions.value = [];
    } finally {
      if (seq === assignSeq) assignLoading.value = false;
    }
  }, 250);
}
async function onAssignConfirm() {
  const picked = assignOptions.value.find((o) => o.staffCode === assignTarget.value);
  if (!assignTarget.value) {
    ElMessage.warning('请选择目标归属人');
    return;
  }
  try {
    await ElMessageBox.confirm(
      `确认将客户【${detail.enterpriseName || detail.name || clientCode.value}】的归属调整为「${picked ? picked.staffName : assignTarget.value}」？此操作立即生效，无需审批。`,
      '分配归属确认',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  assigning.value = true;
  try {
    await assignClient(clientCode.value, assignTarget.value);
    ElMessage.success('归属已更新');
    assignVisible.value = false;
    loadDetail(clientCode.value);
  } catch (e) { /* 拦截器已提示 */ } finally { assigning.value = false; }
}
async function onRecycle() {
  try {
    await ElMessageBox.confirm(
      '确认回收该客户进公海？原归属将清空，冷却期内不可认领。',
      '回收确认',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  try {
    await recycleClient(clientCode.value);
    ElMessage.success('已回收进公海');
    loadDetail(clientCode.value);
  } catch (e) { /* 拦截器已提示 */ }
}

// 路由参数 clientCode（query 或 path 参数均可）变化时重载
watch(
  () => route.query.clientCode || route.params.clientCode,
  (code) => {
    if (code) {
      clientCode.value = code;
      loadDetail(code);
      return;
    }
    clientCode.value = '';
    if (isChannel.value) loadClients();
  },
  { immediate: true },
);
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
