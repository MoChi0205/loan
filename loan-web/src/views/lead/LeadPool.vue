<template>
  <div class="lead-page">
    <div class="loan-page-header">
      <div>
        <h2 class="loan-page-title">线索公海</h2>
        <p class="loan-page-subtitle">谁录入归谁 · 超期未跟进自动回收 · 公海认领 / 手动指派</p>
      </div>
      <el-button type="primary" @click="openCreate">
        <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" style="margin-right: 4px; vertical-align: -2px"><path d="M12 5v14M5 12h14"/></svg>
        新增线索
      </el-button>
    </div>

    <div class="loan-card">
      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <el-tab-pane label="我的线索" name="mine" />
        <el-tab-pane label="公海" name="pool" />
      </el-tabs>

      <AppSearchBar :loading="loading" @search="onSearch" @reset="onReset">
        <el-select v-model="query.leadType" placeholder="客群" clearable style="width: 130px">
          <el-option label="企业" value="ENTERPRISE" />
          <el-option label="个人" value="PERSONAL" />
        </el-select>
        <el-select v-model="query.source" placeholder="来源" clearable style="width: 150px">
          <el-option v-for="(v, k) in sourceMap" :key="k" :label="v" :value="k" />
        </el-select>
        <el-select v-model="query.followStatus" placeholder="跟进状态" clearable style="width: 140px">
          <el-option v-for="(v, k) in followStatusMap" :key="k" :label="v.label" :value="k" />
        </el-select>
        <el-input v-model="query.keyword" placeholder="联系人 / 手机号 / 线索编号" clearable style="width: 200px" @keyup.enter="onSearch" />
      </AppSearchBar>

      <template v-if="loading && !data.length">
        <AppSkeleton :rows="6" :cols="8" :padding="'18px 24px'" />
      </template>

      <template v-else>
        <!-- 批量操作栏：选中 1 条以上时出现 -->
        <transition name="el-fade-in">
          <div v-if="selectedRows.length" class="batch-bar">
            <span class="batch-count">已选 <b>{{ selectedRows.length }}</b> 条</span>
            <el-button v-if="activeTab === 'pool'" type="primary" size="small" @click="onBatchClaim">
              批量认领
            </el-button>
            <el-button v-else type="primary" size="small" @click="openBatchAssign">
              批量指派
            </el-button>
            <el-button v-if="activeTab !== 'pool'" type="danger" plain size="small" @click="onBatchDelete">
              批量删除
            </el-button>
            <el-button size="small" @click="clearSelection">清空选择</el-button>
          </div>
        </transition>

        <el-table
          ref="tableRef"
          :data="data"
          v-loading="loading"
          stripe
          row-key="id"
          @sort-change="handleSortChange"
          @selection-change="onSelectionChange"
        >
          <template #empty>
            <AppEmpty title="暂无线索" desc="点击右上角「新增线索」录入第一条客户线索" />
          </template>
          <el-table-column type="selection" width="44" fixed="left" reserve-selection />
          <el-table-column prop="leadNo" label="线索编号" width="120"  show-overflow-tooltip />
        <el-table-column prop="contactName" label="联系人" width="110" />
        <el-table-column label="手机号" width="130">
          <template #default="{ row }">{{ desensitizePhone(row.phone) }}</template>
        </el-table-column>
        <el-table-column label="客群" width="90">
          <template #default="{ row }">
            <DictTag type="customerGroup" :value="row.leadType" />
          </template>
        </el-table-column>
        <el-table-column label="来源" width="120">
          <template #default="{ row }">
            <span class="loan-tag" :class="sourceTag(row.source)">{{ sourceText(row.source) }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="hasReferrer" label="邀请归因" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.referrerName || row.inviterName || row.referrer || '—' }}
          </template>
        </el-table-column>
        <el-table-column v-if="hasAuthStatus" label="认证状态" width="100">
          <template #default="{ row }">
            <span class="loan-tag" :class="authStatusTag(row.authStatus)">{{ authStatusText(row.authStatus) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="跟进状态" width="110">
          <template #default="{ row }">
            <span class="loan-tag" :class="followStatusTag(row.followStatus)">{{ followStatusText(row.followStatus) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="录入时间" width="170" sortable>
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <AppTableActions :actions="rowActions(row)" />
          </template>
        </el-table-column>
      </el-table>
      </template>

      <AppPagination
        v-model:page="query.page"
        v-model:size="query.size"
        :total="total"
        @change="load"
      />
    </div>

    <!-- 新增线索弹窗 -->
    <AppDialog v-model:visible="createVisible" title="新增线索" width="480px" :loading="creating" @confirm="onCreate">
      <el-form :model="form" :rules="formRules" ref="formRef" label-width="80px">
        <el-form-item label="联系人" prop="contactName">
          <el-input v-model="form.contactName" placeholder="联系人姓名" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="手机号" />
        </el-form-item>
        <el-form-item label="客群">
          <el-select v-model="form.leadType" style="width: 100%" placement="top-start">
            <el-option label="企业" value="ENTERPRISE" />
            <el-option label="个人" value="PERSONAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源">
          <el-select v-model="form.source" style="width: 100%" placement="top-start">
            <el-option label="老板" value="BOSS" />
            <el-option label="顾问" value="ADVISER" />
            <el-option label="渠道" value="CHANNEL" />
            <el-option label="VIP 客户" value="VIP" />
          </el-select>
        </el-form-item>
      </el-form>
    </AppDialog>

    <!-- 指派弹窗（单条 / 批量共用） -->
    <AppDialog v-model:visible="assignVisible" :title="assignBatchMode ? '批量指派' : '指派线索'" width="480px" :loading="assigning" @confirm="onAssign">
      <p class="assign-hint">
        {{ assignBatchMode ? `将选中的 ${selectedRows.length} 条线索` : `将「${currentLead?.contactName}」` }}指派给员工（仅顾问/主管可被指派）
      </p>
      <el-select v-model="targetStaffCode" filterable placeholder="选择员工" style="width: 100%">
        <el-option
          v-for="s in staffOptions"
          :key="s.value"
          :label="`${s.label}（${s.role}）`"
          :value="s.value"
        />
      </el-select>
    </AppDialog>
  </div>
</template>

<script setup>
defineOptions({ name: '_lead' });
import { ref, reactive, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import DictTag from '@/components/DictTag.vue';
import AppSearchBar from '@/components/AppSearchBar.vue';
import AppPagination from '@/components/AppPagination.vue';
import AppTableActions from '@/components/AppTableActions.vue';
import AppDialog from '@/components/AppDialog.vue';
import { useTable } from '@/composables/useTable';
import { formatDateTime, desensitizePhone } from '@/utils/format';
import { copyText } from '@/utils/clipboard';
import { pageLead, createLead, claimLead, assignLead, batchClaimLead, batchAssignLead, batchDeleteLead } from '@/api/lead';
import { staffPage } from '@/api/org';

const activeTab = ref('mine');
const router = useRouter();

// ============================================================
// 批量选择
// ============================================================
const tableRef = ref();
const selectedRows = ref([]);

function onSelectionChange(rows) {
  selectedRows.value = rows;
}

function clearSelection() {
  tableRef.value?.clearSelection();
}

async function onBatchClaim() {
  const nos = selectedRows.value.map((r) => r.leadNo);
  if (!nos.length) return;
  try {
    await ElMessageBox.confirm(`确认认领选中的 ${nos.length} 条线索？认领后进入「我的线索」。`, '批量认领', { type: 'warning' });
  } catch {
    return;
  }
  try {
    const count = await batchClaimLead(nos);
    ElMessage.success(`成功认领 ${count} 条`);
    clearSelection();
    load();
  } catch (e) {
    // 拦截器已提示
  }
}

async function onBatchDelete() {
  const nos = selectedRows.value.map((r) => r.leadNo);
  if (!nos.length) return;
  try {
    await ElMessageBox.confirm(
      `确认删除选中的 ${nos.length} 条线索？删除后不可恢复（审计留痕）。`,
      '批量删除',
      { type: 'error', confirmButtonText: '删除', confirmButtonClass: 'el-button--danger' },
    );
  } catch {
    return;
  }
  try {
    const count = await batchDeleteLead(nos);
    ElMessage.success(`成功删除 ${count} 条`);
    clearSelection();
    load();
  } catch (e) {
    // 拦截器已提示
  }
}

function openBatchAssign() {
  assignBatchMode.value = true;
  currentLead.value = null;
  targetStaffCode.value = null;
  assignVisible.value = true;
  loadStaffOptions();
}

/** useTable 接管列表（loader 闭包动态拼 pool 参数） */
const { loading, data, total, query, load, onSearch, onReset, handleSortChange } = useTable(
  (q) => pageLead({ ...q, pool: activeTab.value === 'pool' }),
  { leadType: '', source: '', followStatus: '', keyword: '' },
);

function onTabChange() {
  query.page = 1;
  load();
}

/** 操作列 */
function rowActions(row) {
  const actions = [];
  // 邀请绑定生成的小程序客户：可从线索直接进入客户档案
  if (row.clientCode) {
    actions.push({ key: 'profile', label: '档案', onClick: () => goProfile(row.clientCode) });
  }
  actions.push({ key: "copy", label: "复制线索编号", onClick: () => onCopy(row.leadNo) });
  if (activeTab.value === 'pool') {
    actions.push({ key: 'claim', label: '认领', type: 'success', confirm: `确认认领「${row.contactName}」？`, onClick: () => onClaim(row) });
  } else {
    actions.push({ key: 'assign', label: '指派', onClick: () => openAssign(row) });
  }
  return actions;
}

/** 跳客户档案独立页（P0-6） */
function goProfile(clientCode) {
  router.push({ path: '/client', query: { clientCode } });
}

async function onClaim(row) {
  try {
    await claimLead(row.leadNo);
    ElMessage.success('认领成功');
    load();
  } catch (e) {
    // 拦截器已提示
  }
}

// ============================================================
// 新增线索
// ============================================================
const createVisible = ref(false);
const creating = ref(false);
const formRef = ref();
const form = reactive({ contactName: '', phone: '', leadType: 'ENTERPRISE', source: 'ADVISER' });
const formRules = {
  contactName: [{ required: true, message: '请输入联系人', trigger: 'blur' }],
  phone: [{ required: true, message: '请输入手机号', trigger: 'blur' }],
};

function openCreate() {
  Object.assign(form, { contactName: '', phone: '', leadType: 'ENTERPRISE', source: 'ADVISER' });
  createVisible.value = true;
}

async function onCreate() {
  await formRef.value.validate();
  creating.value = true;
  try {
    await createLead({ ...form });
    ElMessage.success('新增成功');
    createVisible.value = false;
    load();
  } catch (e) {
    // 拦截器已提示
  } finally {
    creating.value = false;
  }
}

// ============================================================
// 指派
// ============================================================
const assignVisible = ref(false);
const assigning = ref(false);
const currentLead = ref(null);
const targetStaffCode = ref(null);
const staffOptions = ref([]);
const assignBatchMode = ref(false);

/** 拉可指派员工（顾问 ADVISER + 主管 DEPT_MANAGER），值为工号（业务编码） */
async function loadStaffOptions() {
  try {
    const [advisers, managers] = await Promise.all([
      staffPage({ roleCode: 'ADVISER', page: 1, size: 100 }),
      staffPage({ roleCode: 'DEPT_MANAGER', page: 1, size: 100 }),
    ]);
    const merged = new Map();
    [...(advisers.data?.records || []), ...(managers.data?.records || [])].forEach((s) => merged.set(s.staffCode, s));
    staffOptions.value = [...merged.values()].map((s) => ({
      value: s.staffCode,
      label: s.staffName,
      role: s.roleName || s.roleCode,
    }));
  } catch (e) {
    staffOptions.value = [];
  }
}

function openAssign(row) {
  assignBatchMode.value = false;
  currentLead.value = row;
  targetStaffCode.value = null;
  assignVisible.value = true;
  loadStaffOptions();
}

async function onAssign() {
  if (!targetStaffCode.value) {
    ElMessage.warning('请选择目标员工');
    return;
  }
  assigning.value = true;
  try {
    if (assignBatchMode.value) {
      const count = await batchAssignLead(selectedRows.value.map((r) => r.leadNo), targetStaffCode.value);
      ElMessage.success(`成功指派 ${count} 条`);
      clearSelection();
    } else {
      await assignLead(currentLead.value.leadNo, targetStaffCode.value);
      ElMessage.success('指派成功');
    }
    assignVisible.value = false;
    load();
  } catch (e) {
    // 拦截器已提示
  } finally {
    assigning.value = false;
  }
}

// ============================================================
// 本地枚举映射（后端字典暂未覆盖线索跟进状态/来源，待后端补齐后改用 DictTag）
// ============================================================
const followStatusMap = {
  NEW: { label: '新线索', type: 'info' },
  INTENTION: { label: '有意向', type: 'primary' },
  POTENTIAL: { label: '潜力客户', type: 'success' },
  VISITED: { label: '已到访', type: 'warning' },
  NO_ANSWER: { label: '未接通', type: 'muted' },
  NO_NEED: { label: '无需求', type: 'muted' },
};
/** 邀请绑定 / 小程序注册来源的引荐人与认证状态列：仅当列表数据含对应字段时展示 */
const hasReferrer = computed(() => data.value.some((r) => r.referrerName || r.inviterName || r.referrer));
const hasAuthStatus = computed(() => data.value.some((r) => r.authStatus || r.certStatus));

const sourceMap = { BOSS: '老板', ADVISER: '顾问', CHANNEL: '渠道', VIP: 'VIP 客户', INVITE: '小程序注册·邀请', MINI: '小程序注册' };

function followStatusText(code) {
  return followStatusMap[code]?.label || code || '-';
}
function followStatusTag(code) {
  const t = followStatusMap[code]?.type || 'muted';
  const m = { info: 'loan-tag-info', primary: 'loan-tag-primary', success: 'loan-tag-success', warning: 'loan-tag-warning', muted: 'loan-tag-muted' };
  return m[t] || 'loan-tag-muted';
}
function sourceText(code) {
  return sourceMap[code] || code || '-';
}
function sourceTag(code) {
  const m = { BOSS: 'loan-tag-muted', ADVISER: 'loan-tag-primary', CHANNEL: 'loan-tag-info', VIP: 'loan-tag-warning', INVITE: 'loan-tag-primary', MINI: 'loan-tag-info' };
  return m[code] || 'loan-tag-muted';
}

/** 认证状态（线索卡片展示，后端未下发时整列隐藏） */
const authStatusMap = { VERIFIED: '已认证', SUCCESS: '已认证', ACTIVE: '已认证', PENDING: '待复核', FAIL: '认证失败', UNVERIFIED: '未认证' };
function authStatusText(code) {
  return authStatusMap[code] || (code ? code : '-');
}
function authStatusTag(code) {
  const m = { VERIFIED: 'loan-tag-success', SUCCESS: 'loan-tag-success', ACTIVE: 'loan-tag-success', PENDING: 'loan-tag-warning', FAIL: 'loan-tag-danger', UNVERIFIED: 'loan-tag-muted' };
  return m[code] || 'loan-tag-muted';
}


async function onCopy(val) {
  try {
    await copyText(val || '');
    ElMessage.success('已复制');
  } catch {
    ElMessage.warning('复制失败');
  }
}
onMounted(load);
</script>

<style scoped>
.assign-hint {
  margin: 0 0 14px;
  font-size: 13px;
  color: var(--loan-text-secondary);
}

/* 批量操作栏 */
.batch-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  margin-bottom: 10px;
  background: var(--loan-bg-elevated, #f8fafc);
  border: 1px solid var(--loan-border);
  border-radius: 8px;
}
.batch-count {
  font-size: 13px;
  color: var(--loan-text-secondary);
  margin-right: 4px;
}
.batch-count b {
  color: var(--loan-primary, #3b82f6);
  font-size: 14px;
}
</style>
