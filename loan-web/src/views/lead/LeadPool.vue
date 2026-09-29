<template>
  <div class="lead-page">
    <div class="loan-page-header">
      <div>
        <h2 class="loan-page-title">{{ activeTab === 'pool' ? '线索公海' : '我的线索' }}</h2>
        <p class="loan-page-subtitle">{{ isChannel ? '新增后本人立即可见，公司审核通过后进入公海' : '我的线索＝当前归属我的线索；线索公海＝未分配线索；客户公海＝未分配客户；创建人始终单独展示' }}</p>
      </div>
      <div class="header-actions">
      <el-button v-if="canImport" plain @click="openImport">批量导入</el-button>
      <el-button v-permission="ACTION_PERMISSION.LEAD_CREATE" type="primary" @click="openCreate">
        <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" style="margin-right: 4px; vertical-align: -2px"><path d="M12 5v14M5 12h14"/></svg>
        新增线索
      </el-button>
      </div>
    </div>

    <div class="loan-card">
      <AppSearchBar :loading="loading" @search="onSearch" @reset="onReset">
        <el-select v-if="activeTab !== 'clients'" v-model="query.leadType" placeholder="客群" clearable style="width: 130px">
          <el-option label="企业" value="ENTERPRISE" />
          <el-option label="个人" value="PERSONAL" />
        </el-select>
        <el-select v-if="activeTab !== 'clients' && !isChannel" v-model="query.source" placeholder="来源" clearable style="width: 150px">
          <el-option v-for="(v, k) in sourceMap" :key="k" :label="v" :value="k" />
        </el-select>
        <el-select v-if="activeTab !== 'clients'" v-model="query.followStatus" placeholder="跟进状态" clearable style="width: 140px">
          <el-option v-for="(v, k) in followStatusMap" :key="k" :label="v.label" :value="k" />
        </el-select>
        <el-input v-model="query.keyword" :placeholder="activeTab === 'clients' ? '客户姓名 / 企业名称' : '联系人 / 手机号 / 企业名称'" clearable style="width: 220px" @keyup.enter="onSearch" />
      </AppSearchBar>

      <template v-if="loading && !data.length">
        <AppSkeleton :rows="6" :cols="8" :padding="'18px 24px'" />
      </template>

      <template v-else>
        <!-- 批量操作栏：选中 1 条以上时出现 -->
        <transition name="el-fade-in">
          <div v-if="selectedRows.length" class="batch-bar">
            <span class="batch-count">已选 <b>{{ selectedRows.length }}</b> 条</span>
            <el-button v-if="activeTab === 'pool' && userStore.hasPerm(ACTION_PERMISSION.LEAD_CLAIM)" type="primary" size="small" @click="onBatchClaim">
              批量认领
            </el-button>
            <el-button v-else-if="activeTab === 'mine' && userStore.hasPerm(ACTION_PERMISSION.LEAD_ASSIGN)" type="primary" size="small" @click="openBatchAssign">
              批量指派
            </el-button>
            <el-button v-if="activeTab === 'mine' && userStore.hasPerm(ACTION_PERMISSION.LEAD_DELETE)" type="danger" plain size="small" @click="onBatchDelete">
              批量删除
            </el-button>
            <el-button size="small" @click="clearSelection">清空选择</el-button>
          </div>
        </transition>

        <el-table
          ref="tableRef"
          style="height: calc(100vh - 320px); min-height: 360px"
          :data="data"
          v-loading="loading"
          stripe
          :row-key="rowKey"
          @sort-change="handleSortChange"
          @selection-change="onSelectionChange"
        >
          <template #empty>
            <AppEmpty
              :title="activeTab === 'clients' ? '暂无未分配客户' : (isChannel ? '暂无本人录入的线索' : (activeTab === 'pool' ? '暂无线索公海' : '暂无我的线索'))"
              :desc="activeTab === 'clients' ? '尚未分配服务顾问的客户会显示在这里，认领后进入「我的客户」' : (isChannel ? '新增成功后会立即显示，审核通过后进入公司公海' : (activeTab === 'pool' ? '已释放或审核通过的未分配线索会显示在这里' : '新增线索自动归属本人，释放后移入公司公海'))"
            />
          </template>
          <el-table-column v-if="activeTab !== 'clients' && !isChannel" type="selection" width="44" fixed="left" reserve-selection />
        <el-table-column v-if="activeTab === 'clients'" label="客户" min-width="180">
          <template #default="{ row }">
            <div class="cell-main">{{ row.enterpriseName || row.customerName || '微信客户' }}</div>
            <div v-if="row.enterpriseName && row.customerName" class="cell-sub">{{ row.customerName }}</div>
          </template>
        </el-table-column>
        <el-table-column v-if="activeTab !== 'clients'" prop="contactName" label="联系人" width="110" />
        <el-table-column label="联系方式" width="190">
          <template #default="{ row }"><span>{{ revealedPhones.get(row.leadNo) || desensitizePhone(row.phone) || '未绑定' }}</span><el-button v-if="row.leadNo && !revealedPhones.has(row.leadNo)" link type="primary" size="small" @click.stop="requestLeadPhoneView(row)">申请查看</el-button><span v-else-if="revealedPhones.has(row.leadNo)" class="phone-revealed">已授权查看</span></template>
        </el-table-column>
        <el-table-column label="客群" width="90">
          <template #default="{ row }">
            <DictTag type="customerGroup" :value="row.leadType || row.customerGroup" />
          </template>
        </el-table-column>
        <el-table-column v-if="activeTab !== 'clients'" label="来源" width="120">
          <template #default="{ row }">
            <span class="loan-tag" :class="sourceTag(row.source)">{{ sourceText(row.source) }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="activeTab !== 'clients'" label="创建人" width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ row.createdBy || '—' }}</template>
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
        <el-table-column v-if="activeTab !== 'clients'" label="跟进状态" width="110">
          <template #default="{ row }">
            <span class="loan-tag" :class="followStatusTag(row.followStatus)">{{ followStatusText(row.followStatus) }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="activeTab !== 'clients'" label="客户标签" width="110">
          <template #default="{ row }"><span class="loan-tag loan-tag-info">{{ customerTagMap[row.customerTag] || '新用户' }}</span></template>
        </el-table-column>
        <el-table-column :prop="activeTab === 'clients' ? 'registeredAt' : 'createdAt'" :label="activeTab === 'clients' ? '注册时间' : '录入时间'" width="170" sortable>
          <template #default="{ row }">{{ formatDateTime(row.registeredAt || row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" min-width="260" fixed="right">
          <template #default="{ row }">
            <AppTableActions :actions="rowActions(row)" :max-inline="3" />
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
          <el-select v-model="form.leadType" style="width: 100%">
            <el-option label="企业" value="ENTERPRISE" />
            <el-option label="个人" value="PERSONAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="客户标签">
          <el-select v-model="form.customerTag" clearable placeholder="可选，默认新用户" style="width: 100%">
            <el-option v-for="(label, code) in customerTagMap" :key="code" :label="label" :value="code" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!isChannel" label="归属">
          <el-input model-value="创建后自动归属本人" disabled />
        </el-form-item>
      </el-form>
    </AppDialog>

    <AppDialog v-model:visible="importVisible" title="批量导入" width="760px" :show-footer="false">
      <div class="import-toolbar">
        <el-radio-group v-model="importType" :disabled="importing">
          <el-radio-button value="LEAD">客户/线索</el-radio-button>
          <el-radio-button value="PRODUCT">产品</el-radio-button>
        </el-radio-group>
        <el-button link type="primary" @click="downloadTemplate">下载{{ importType === 'LEAD' ? '客户线索' : '产品' }}模板</el-button>
        <input ref="importFileRef" type="file" accept=".xlsx" hidden @change="onImportFileChange" />
        <el-button type="primary" :loading="importing" @click="importFileRef?.click()">选择 Excel</el-button>
      </div>
      <div v-if="importFile" class="import-file">已选择：{{ importFile.name }}（{{ previewRows.length }} 行预览）</div>
      <el-table v-if="previewRows.length" :data="previewRows.slice(0, 8)" size="small" border max-height="300">
        <el-table-column v-for="column in previewColumns" :key="column" :prop="column" :label="column" min-width="120" show-overflow-tooltip />
      </el-table>
      <el-empty v-else description="请选择按模板填写的 .xlsx 文件，先预览再导入" :image-size="72" />
      <div v-if="importResult" class="import-result">
        <el-alert :title="`导入完成：成功 ${importResult.success} 条，失败 ${importResult.failed} 条`" :type="importResult.failed ? 'warning' : 'success'" :closable="false" />
        <el-button v-if="importResult.failed" link type="primary" @click="downloadFailures">下载失败明细</el-button>
      </div>
      <div class="import-footer">
        <el-button @click="importVisible = false">关闭</el-button>
        <el-button type="primary" :disabled="!importFile || !previewRows.length" :loading="importing" @click="executeCurrentImport">确认导入</el-button>
      </div>
    </AppDialog>

    <!-- 指派弹窗（单条 / 批量共用） -->
    <AppDialog v-model:visible="assignVisible" :title="assignBatchMode ? '批量指派' : '指派线索'" width="480px" :loading="assigning" @confirm="onAssign">
      <p class="assign-hint">
        {{ assignBatchMode ? `将选中的 ${selectedRows.length} 条线索` : `将「${currentLead?.contactName}」` }}指派给员工（仅顾问/主管可被指派）
      </p>
      <el-select v-model="targetStaffCode" filterable remote :remote-method="searchAssignableStaff" :loading="staffLoading" placeholder="输入员工姓名搜索" style="width: 100%" @visible-change="(v) => { if (v) searchAssignableStaff('') }">
        <el-option
          v-for="s in staffOptions"
          :key="s.value"
          :label="`${s.label}（${s.role}）`"
          :value="s.value"
        />
      </el-select>
    </AppDialog>

    <AppDialog v-model:visible="clientAssignVisible" title="为客户分配顾问" width="480px" :loading="assigningClient" @confirm="onAssignClient">
      <p class="assign-hint">为「{{ currentClient?.enterpriseName || currentClient?.customerName || '微信客户' }}」选择服务顾问，提交后立即生效。</p>
      <el-select
        v-model="targetAdviserCode"
        filterable
        remote
        :remote-method="searchAdvisers"
        :loading="adviserLoading"
        placeholder="输入顾问姓名搜索"
        style="width: 100%"
      >
        <el-option v-for="s in adviserOptions" :key="s.value" :label="s.label" :value="s.value" />
      </el-select>
    </AppDialog>

    <AppDialog v-model:visible="leadProfileVisible" title="线索档案" width="680px" :show-footer="false">
      <div v-loading="leadProfileLoading" class="lead-profile">
        <el-descriptions v-if="leadProfile" :column="2" border>
          <el-descriptions-item label="联系人">{{ leadProfile.contactName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="联系方式">{{ desensitizePhone(leadProfile.phone) }}</el-descriptions-item>
          <el-descriptions-item label="客群">{{ leadTypeText(leadProfile.leadType) }}</el-descriptions-item>
          <el-descriptions-item label="来源">{{ sourceText(leadProfile.source) }}</el-descriptions-item>
          <el-descriptions-item label="跟进状态">{{ followStatusText(leadProfile.followStatus) }}</el-descriptions-item>
          <el-descriptions-item label="创建人">{{ leadProfile.createdBy || '—' }}</el-descriptions-item>
          <el-descriptions-item label="录入时间">{{ formatDateTime(leadProfile.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="最近跟进">{{ formatDateTime(leadProfile.lastFollowedAt) }}</el-descriptions-item>
        </el-descriptions>
        <div class="profile-section-title">跟进与流转记录</div>
        <el-timeline v-if="leadProfile?.history?.length">
          <el-timeline-item v-for="(item, index) in leadProfile.history" :key="`${item.createdAt}-${index}`" :timestamp="formatDateTime(item.createdAt)" placement="top">
            <strong>{{ historyActionText(item.actionType) }}</strong>
            <span class="history-operator">{{ item.operator || '系统' }}</span>
            <p>{{ item.remark || '—' }}</p>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无跟进记录" :image-size="72" />
      </div>
    </AppDialog>

    <AppDialog v-model:visible="followVisible" title="填写线索跟进" width="520px" :loading="following" @confirm="onFollowSubmit">
      <el-form ref="followFormRef" :model="followForm" :rules="followRules" label-width="86px">
        <el-form-item label="跟进结果" prop="followStatus">
          <el-select v-model="followForm.followStatus" style="width: 100%">
            <el-option v-for="(item, code) in followEditableStatusMap" :key="code" :label="item.label" :value="code" />
          </el-select>
        </el-form-item>
        <el-form-item label="跟进内容" prop="content">
          <el-input v-model="followForm.content" type="textarea" :rows="4" maxlength="240" show-word-limit placeholder="填写沟通结果、客户需求及下一步安排" />
        </el-form-item>
      </el-form>
    </AppDialog>
  </div>
</template>

<script setup>
defineOptions({ name: '_lead' });
import { ref, reactive, computed, onMounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import DictTag from '@/components/DictTag.vue';
import AppSearchBar from '@/components/AppSearchBar.vue';
import AppPagination from '@/components/AppPagination.vue';
import AppTableActions from '@/components/AppTableActions.vue';
import AppDialog from '@/components/AppDialog.vue';
import { useTable } from '@/composables/useTable';
import { formatDateTime, desensitizePhone } from '@/utils/format';
import { pageLead, createLead, claimLead, releaseLead, assignLead, batchClaimLead, batchAssignLead, batchDeleteLead, getLeadDetail, followLead } from '@/api/lead';
import { staffPage } from '@/api/org';
import { pageUnassignedClients, claimUnassignedClient, assignClient } from '@/api/client';
import { useUserStore } from '@/store/user';
import { ACTION_PERMISSION } from '@/utils/access';
import { applyLeadPhoneView } from '@/api/sensitive';
import { previewImport, executeImport, downloadImportTemplate, exportImportFailures } from '@/api/importing';

const route = useRoute();
const activeTab = ref(String(route.meta.leadView || 'mine'));
watch(() => route.fullPath, () => {
  const next = String(route.meta.leadView || route.query.view || 'mine');
  if (next !== activeTab.value) activeTab.value = next;
});
const router = useRouter();
const userStore = useUserStore();
const roleCode = computed(() => (userStore.roleCode || '').toUpperCase());
const isChannel = computed(() => roleCode.value === 'CHANNEL');
const canImport = computed(() => ['BOSS', 'SUPER_ADMIN', 'SUPER'].includes(roleCode.value));
const canClaimClient = computed(() => roleCode.value === 'ADVISER');
const rowKey = (row) => row.leadNo || row.clientCode;

const importVisible = ref(false);
const importing = ref(false);
const importType = ref('LEAD');
const importFile = ref(null);
const importFileRef = ref();
const previewRows = ref([]);
const importResult = ref(null);
const previewColumns = computed(() => previewRows.value.length ? Object.keys(previewRows.value[0]) : []);
function openImport() {
  importType.value = 'LEAD'; importFile.value = null; previewRows.value = []; importResult.value = null; importVisible.value = true;
}
async function onImportFileChange(event) {
  const file = event.target.files?.[0]; event.target.value = '';
  if (!file) return;
  importFile.value = file; importResult.value = null; importing.value = true;
  try {
    const result = await previewImport(importType.value, file);
    previewRows.value = result.data?.rows || [];
    ElMessage.success(`已预览 ${previewRows.value.length} 行，可确认导入`);
  } catch { previewRows.value = []; }
  finally { importing.value = false; }
}
async function executeCurrentImport() {
  if (!importFile.value) return;
  importing.value = true;
  try {
    const result = await executeImport(importType.value, importFile.value);
    importResult.value = result.data || {};
    ElMessage.success('批量导入处理完成');
    await load();
  } catch { /* 请求层已提示 */ }
  finally { importing.value = false; }
}
async function downloadTemplate() {
  try {
    const blob = await downloadImportTemplate(importType.value);
    const url = URL.createObjectURL(blob); const a = document.createElement('a');
    a.href = url; a.download = `${importType.value === 'LEAD' ? '客户线索' : '产品'}-导入模板.xlsx`; a.click(); URL.revokeObjectURL(url);
  } catch { /* 请求层已提示 */ }
}
async function downloadFailures() {
  try {
    const blob = await exportImportFailures(importResult.value.failures || []);
    const url = URL.createObjectURL(blob); const a = document.createElement('a');
    a.href = url; a.download = '导入失败明细.xlsx'; a.click(); URL.revokeObjectURL(url);
  } catch { /* 请求层已提示 */ }
}

// ============================================================
// 批量选择
// ============================================================
const tableRef = ref();
const selectedRows = ref([]);
const revealedPhones = reactive(new Map());
async function requestLeadPhoneView(row) {
  try {
    const data = (await applyLeadPhoneView(row.leadNo)).data || {};
    if (data.phonePlain) { revealedPhones.set(row.leadNo, data.phonePlain); ElMessage.success(`已授权查看，本日剩余 ${data.remaining ?? '—'} 次`); }
    else if (data.approvalNo) ElMessage.warning(data.message || '已提交审批，审批通过后可再次查看');
  } catch (e) { /* 拦截器已提示 */ }
}

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
  (q) => activeTab.value === 'clients'
    ? pageUnassignedClients({ keyword: q.keyword, page: q.page, size: q.size })
    : pageLead({ ...q, pool: activeTab.value === 'pool' }),
  { leadType: '', source: '', followStatus: '', keyword: '' },
);
watch(activeTab, () => {
  query.page = 1;
  load();
});

/** 操作列 */
function rowActions(row) {
  const actions = [];
  if (activeTab.value === 'clients') {
    actions.push({ key: 'profile', label: '查看档案', onClick: () => goProfile(row.clientCode) });
    // 已有认领待审时：所有角色（含管理者）只展示「待审核：xxx」禁用按钮，
    // 禁止「直接分配」绕开认领审核——必须走审核中心通过/驳回该认领申请。
    if (row.allocationPending) {
      actions.push({ key: 'pending', label: `待审核${row.applicantName ? `：${row.applicantName}` : ''}`, disabled: true });
    } else if (userStore.hasPerm(ACTION_PERMISSION.CLIENT_ASSIGN)) {
      actions.push({
        key: 'assign-client',
        label: '分配顾问',
        type: 'primary',
        onClick: () => openClientAssign(row),
      });
    } else if (canClaimClient.value && userStore.hasPerm(ACTION_PERMISSION.CLIENT_CLAIM)) {
      actions.push({ key: 'claim-client', label: '申请认领', type: 'success', confirm: '确认申请认领该客户？审核通过后将成为其服务顾问。', onClick: () => onClaimClient(row) });
    }
    return actions;
  }
  // 已转客户进入完整客户档案；历史未转客户使用线索档案，保证每条“我的线索”都有档案入口。
  if (activeTab.value === 'mine' && !isChannel.value) {
    actions.push({ key: 'profile', label: '查看档案', onClick: () => openLeadProfile(row) });
  }
  if (activeTab.value === 'pool' && userStore.hasPerm(ACTION_PERMISSION.LEAD_CLAIM)) {
    actions.push({ key: 'claim', label: '认领', type: 'success', confirm: `确认认领「${row.contactName}」？`, onClick: () => onClaim(row) });
  } else if (activeTab.value === 'mine') {
    if (!isChannel.value && row.ownerStaffCode === userStore.user?.userNo) {
      actions.push({ key: 'follow', label: '填写跟进', type: 'primary', onClick: () => openFollow(row) });
      actions.push({
        key: 'release',
        label: '释放到公海',
        type: 'warning',
        confirm: `确认将「${row.contactName}」释放到公司公海？释放后其他员工可认领。`,
        onClick: () => onRelease(row),
      });
    }
    if (userStore.hasPerm(ACTION_PERMISSION.LEAD_ASSIGN)) {
      actions.push({ key: 'assign', label: '指派', onClick: () => openAssign(row) });
    }
  }
  return actions;
}

async function onClaimClient(row) {
  try {
    await claimUnassignedClient(row.clientCode);
    ElMessage.success('认领申请已提交，等待审核');
    load();
  } catch (e) { /* 拦截器已提示 */ }
}

const clientAssignVisible = ref(false);
const assigningClient = ref(false);
const currentClient = ref(null);
const targetAdviserCode = ref('');
const adviserOptions = ref([]);
const adviserLoading = ref(false);
let adviserSearchSequence = 0;
let adviserSearchTimer;

async function loadAdvisers(keyword = '') {
  const sequence = ++adviserSearchSequence;
  adviserLoading.value = true;
  try {
    const res = await staffPage({ roleCode: 'ADVISER', keyword: keyword.trim() || undefined, page: 1, size: 50 });
    if (sequence !== adviserSearchSequence) return;
    adviserOptions.value = (res.data?.records || []).map((s) => ({ value: s.staffCode, label: s.staffName }));
  } catch (e) {
    if (sequence === adviserSearchSequence) adviserOptions.value = [];
  } finally {
    if (sequence === adviserSearchSequence) adviserLoading.value = false;
  }
}

function searchAdvisers(keyword) {
  clearTimeout(adviserSearchTimer);
  adviserSearchTimer = setTimeout(() => loadAdvisers(keyword), 250);
}

function openClientAssign(row) {
  currentClient.value = row;
  targetAdviserCode.value = '';
  clientAssignVisible.value = true;
  loadAdvisers('');
}

async function onAssignClient() {
  if (!targetAdviserCode.value) {
    ElMessage.warning('请选择目标顾问');
    return;
  }
  const target = adviserOptions.value.find((item) => item.value === targetAdviserCode.value);
  try {
    await ElMessageBox.confirm(
      `确认将客户「${currentClient.value?.enterpriseName || currentClient.value?.customerName || '微信客户'}」直接分配给「${target?.label || '所选顾问'}」？分配后立即生效，无需审核。`,
      '客户归属确认',
      { type: 'warning', confirmButtonText: '确认分配' },
    );
  } catch { return; }
  assigningClient.value = true;
  try {
    await assignClient(currentClient.value.clientCode, targetAdviserCode.value);
    ElMessage.success('归属已直接分配');
    clientAssignVisible.value = false;
    load();
  } catch (e) { /* 拦截器已提示 */ }
  finally { assigningClient.value = false; }
}

/** 跳客户档案独立页（P0-6） */
function goProfile(clientCode) {
  router.push({ path: '/client/my', query: { clientCode } });
}

function clientCodeOf(row) {
  return row?.clientProfileCode || row?.clientCode || '';
}

const leadProfileVisible = ref(false);
const leadProfileLoading = ref(false);
const leadProfile = ref(null);
async function openLeadProfile(row) {
  const clientCode = clientCodeOf(row);
  if (clientCode) {
    goProfile(clientCode);
    return;
  }
  leadProfile.value = null;
  leadProfileVisible.value = true;
  leadProfileLoading.value = true;
  try {
    const res = await getLeadDetail(row.leadNo);
    leadProfile.value = res.data || null;
  } catch (e) {
    leadProfileVisible.value = false;
  } finally {
    leadProfileLoading.value = false;
  }
}

const followVisible = ref(false);
const following = ref(false);
const followFormRef = ref();
const currentFollowLead = ref(null);
const followForm = reactive({ followStatus: 'INTENTION', content: '' });
const followRules = {
  followStatus: [{ required: true, message: '请选择跟进结果', trigger: 'change' }],
  content: [{ required: true, message: '请填写跟进内容', trigger: 'blur' }],
};
function openFollow(row) {
  currentFollowLead.value = row;
  Object.assign(followForm, {
    followStatus: followEditableStatusMap[row.followStatus] ? row.followStatus : 'INTENTION',
    content: '',
  });
  followVisible.value = true;
}
async function onFollowSubmit() {
  await followFormRef.value?.validate();
  following.value = true;
  try {
    await followLead(currentFollowLead.value.leadNo, { ...followForm });
    ElMessage.success('跟进信息已保存');
    followVisible.value = false;
    await load();
  } catch (e) {
    // 拦截器已提示
  } finally {
    following.value = false;
  }
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

async function onRelease(row) {
  try {
    await releaseLead(row.leadNo);
    ElMessage.success('已释放到公司公海');
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
const customerTagMap = { NEW: '新用户', INTENTION: '意向客户', POTENTIAL: '潜在客户', NO_ANSWER: '无人接听', NO_NEED: '无需求' };
const form = reactive({ contactName: '', phone: '', leadType: 'ENTERPRISE', customerTag: 'NEW' });
const formRules = {
  contactName: [{ required: true, message: '请输入联系人', trigger: 'blur' }],
  phone: [{ required: true, message: '请输入手机号', trigger: 'blur' }],
};

function openCreate() {
  Object.assign(form, { contactName: '', phone: '', leadType: 'ENTERPRISE', customerTag: 'NEW' });
  createVisible.value = true;
}

async function onCreate() {
  await formRef.value.validate();
  creating.value = true;
  try {
    await createLead({ ...form });
    ElMessage.success(isChannel.value ? '已提交，等待公司审核' : '新增成功，已自动归属本人');
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
const staffLoading = ref(false);
const assignBatchMode = ref(false);
let staffSearchSequence = 0;
let staffSearchTimer;

/** 拉可指派员工（顾问 ADVISER + 主管 DEPT_MANAGER），值为工号（业务编码） */
async function loadStaffOptions(keyword = '') {
  const sequence = ++staffSearchSequence;
  staffLoading.value = true;
  try {
    const [advisers, managers] = await Promise.all([
      staffPage({ roleCode: 'ADVISER', keyword: keyword.trim() || undefined, page: 1, size: 20 }),
      staffPage({ roleCode: 'DEPT_MANAGER', keyword: keyword.trim() || undefined, page: 1, size: 20 }),
    ]);
    if (sequence !== staffSearchSequence) return;
    const merged = new Map();
    [...(advisers.data?.records || []), ...(managers.data?.records || [])].forEach((s) => merged.set(s.staffCode, s));
    staffOptions.value = [...merged.values()].map((s) => ({
      value: s.staffCode,
      label: s.staffName,
      role: s.roleName || s.roleCode,
    }));
  } catch (e) {
    if (sequence === staffSearchSequence) staffOptions.value = [];
  } finally {
    if (sequence === staffSearchSequence) staffLoading.value = false;
  }
}

function searchAssignableStaff(keyword) {
  clearTimeout(staffSearchTimer);
  staffSearchTimer = setTimeout(() => loadStaffOptions(keyword), 250);
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
  PENDING_APPROVAL: { label: '待公司审核', type: 'warning' },
  NEW: { label: '新线索', type: 'info' },
  FOLLOWING: { label: '跟进中', type: 'primary' },
  INTENTION: { label: '有意向', type: 'primary' },
  POTENTIAL: { label: '潜力客户', type: 'success' },
  VISITED: { label: '已到访', type: 'warning' },
  NO_ANSWER: { label: '未接通', type: 'muted' },
  NO_NEED: { label: '无需求', type: 'muted' },
  REJECTED: { label: '已驳回', type: 'muted' },
};
const followEditableStatusMap = {
  NEW: followStatusMap.NEW,
  INTENTION: followStatusMap.INTENTION,
  POTENTIAL: followStatusMap.POTENTIAL,
  VISITED: followStatusMap.VISITED,
  NO_ANSWER: followStatusMap.NO_ANSWER,
  NO_NEED: followStatusMap.NO_NEED,
};
/** 邀请绑定 / 小程序注册来源的引荐人与认证状态列：仅当列表数据含对应字段时展示 */
const hasReferrer = computed(() => (data.value || []).some((r) => r.referrerName || r.inviterName || r.referrer));
const hasAuthStatus = computed(() => (data.value || []).some((r) => r.authStatus || r.certStatus));

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
function leadTypeText(code) {
  return ({ ENTERPRISE: '企业', PERSONAL: '个人' }[code] || '—');
}
function historyActionText(code) {
  return ({
    MANUAL: '录入或指派', CLAIM: '认领线索', RELEASE: '释放到公海', RECYCLE: '回收到公海',
    TRANSFER: '转移归属', CONVERT: '转为客户', FOLLOW_UP: '跟进记录', APPROVE: '审核通过', REJECT: '审核驳回',
  }[code] || '线索动态');
}

/** 认证状态（线索卡片展示，后端未下发时整列隐藏） */
const authStatusMap = { VERIFIED: '已认证', SUCCESS: '已认证', ACTIVE: '已认证', PENDING: '待复核', FAIL: '认证失败', UNVERIFIED: '未认证' };
function authStatusText(code) {
  return authStatusMap[code] || ({ ENTERPRISE_AUTHED: '企业已认证', PERSONAL_AUTHED: '个人已认证' }[code] || (code ? '状态待确认' : '-'));
}
function authStatusTag(code) {
  const m = { VERIFIED: 'loan-tag-success', SUCCESS: 'loan-tag-success', ACTIVE: 'loan-tag-success', PENDING: 'loan-tag-warning', FAIL: 'loan-tag-danger', UNVERIFIED: 'loan-tag-muted' };
  return m[code] || 'loan-tag-muted';
}


onMounted(load);
</script>

<style scoped>
.assign-hint {
  margin: 0 0 14px;
  font-size: 13px;
  color: var(--loan-text-secondary);
}

.header-actions { display: flex; align-items: center; gap: 10px; }
.import-toolbar { display: flex; align-items: center; gap: 14px; margin-bottom: 14px; }
.import-file { padding: 10px 12px; margin-bottom: 12px; color: var(--loan-text-secondary); background: var(--loan-bg-elevated, var(--loan-surface)); border-radius: 6px; }
.import-result { display: flex; align-items: center; gap: 12px; margin-top: 14px; }
.import-footer { display: flex; justify-content: flex-end; gap: 10px; margin-top: 18px; }

/* 批量操作栏 */
.batch-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  margin-bottom: 10px;
  background: var(--loan-bg-elevated, var(--loan-surface));
  border: 1px solid var(--loan-border);
  border-radius: 8px;
}
.batch-count {
  font-size: 13px;
  color: var(--loan-text-secondary);
  margin-right: 4px;
}
.batch-count b {
  color: var(--loan-primary);
  font-size: 14px;
}

.lead-profile {
  min-height: 180px;
}
.profile-section-title {
  margin: 22px 0 16px;
  color: var(--loan-text-primary);
  font-size: 15px;
  font-weight: 600;
}
.history-operator {
  margin-left: 10px;
  color: var(--loan-text-secondary);
  font-size: 12px;
}
.lead-profile :deep(.el-timeline-item__content p) {
  margin: 6px 0 0;
  color: var(--loan-text-secondary);
}
</style>
