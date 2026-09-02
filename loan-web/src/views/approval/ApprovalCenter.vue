<template>
  <div class="approval-page">
    <div class="loan-page-header">
      <div>
        <h2 class="loan-page-title">审批中心</h2>
        <p class="loan-page-subtitle">产品审核入全量库 · 附件无水印下载审批（通过生成 24h 限时链接）</p>
      </div>
    </div>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="产品审核" name="product" />
      <el-tab-pane label="附件下载审批" name="download" />
      <el-tab-pane v-if="canAllocate" label="客户分配审批" name="allocation" />
    </el-tabs>

    <!-- ============ 产品审核 ============ -->
    <div v-show="activeTab === 'product'" class="loan-card">
      <AppSearchBar :loading="loadingP" @search="searchP" @reset="resetP">
        <el-select v-model="queryP.status" placeholder="审核状态" clearable style="width: 140px">
          <el-option v-for="(t, k) in statusText" :key="k" :label="t" :value="k" />
        </el-select>
        <el-input v-model="queryP.keyword" placeholder="审核单号 / 产品编码" style="width: 220px" clearable @keyup.enter="searchP" />
      </AppSearchBar>

      <AppTableState :error="errorP" @retry="loadP">
      <el-table :data="dataP" v-loading="loadingP" stripe row-key="approvalNo" @sort-change="handleSortChange">
        <template #empty>
          <AppEmpty title="暂无产品审核" desc="新产品申请入全量库后将在此等待审核" />
        </template>
        <el-table-column prop="approvalNo" label="审核单号" min-width="180" show-overflow-tooltip />
        <el-table-column label="产品" min-width="170">
          <template #default="{ row }">
            <div class="cell-main">{{ row.bankProductName || '—' }}</div>
            <div class="cell-sub mono">{{ row.bankProductCode }}</div>
          </template>
        </el-table-column>
        <el-table-column label="申请类型" width="100">
          <template #default="{ row }">
            <span class="loan-tag loan-tag-info">{{ row.applyType === 'CREATE' ? '新建' : '变更' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="重复标记" width="90">
          <template #default="{ row }">
            <span v-if="row.duplicateFlag" class="loan-tag loan-tag-danger">疑似重复</span>
            <span v-else class="loan-tag loan-tag-muted">正常</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <span class="loan-tag" :class="statusTag(row.approveStatus)">{{ statusText[row.approveStatus] || row.approveStatus }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="approverStaffName" label="审核人" width="120">
          <template #default="{ row }">{{ row.approverStaffName || (row.approveStatus === 'PENDING' ? '待审核' : '姓名待补充') }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="160" sortable>
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <AppTableActions :actions="productActions(row)" />
          </template>
        </el-table-column>
      </el-table>
      </AppTableState>
      <AppPagination v-if="!errorP" v-model:page="queryP.page" v-model:size="queryP.size" :total="totalP" @change="loadP" />
    </div>

    <!-- ============ 附件下载审批 ============ -->
    <div v-show="activeTab === 'download'" class="loan-card">
      <AppSearchBar :loading="loadingD" @search="searchD" @reset="resetD">
        <el-select v-model="queryD.status" placeholder="审批状态" clearable style="width: 140px">
          <el-option v-for="(t, k) in statusText" :key="k" :label="t" :value="k" />
        </el-select>
        <el-input v-model="queryD.keyword" placeholder="申请单号 / 申请人姓名" style="width: 220px" clearable @keyup.enter="searchD" />
        <template #append>
          <el-button type="primary" plain @click="openApply">
            <AppIcon name="add" :size="14" />
            发起申请
          </el-button>
        </template>
      </AppSearchBar>

      <AppTableState :error="errorD" @retry="loadD">
      <el-table :data="dataD" v-loading="loadingD" stripe row-key="approvalNo" @sort-change="handleSortChangeD">
        <template #empty>
          <AppEmpty title="暂无下载审批" desc="员工发起无水印下载申请后将在此审批" />
        </template>
        <el-table-column prop="approvalNo" label="申请单号" min-width="180" show-overflow-tooltip />
        <el-table-column prop="applicantStaffName" label="申请人" width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.applicantStaffName || '姓名待补充' }}</template>
        </el-table-column>
        <el-table-column prop="purpose" label="用途说明" min-width="180" show-overflow-tooltip />
        <el-table-column label="资料清单" min-width="120">
          <template #default="{ row }">{{ attachmentCount(row.attachmentIds) }} 份资料</template>
        </el-table-column>
        <el-table-column label="期望期限" width="90">
          <template #default="{ row }">{{ row.expectDays ? row.expectDays + ' 天' : '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <span class="loan-tag" :class="statusTag(row.approveStatus)">{{ statusText[row.approveStatus] || row.approveStatus }}</span>
            <span v-if="row.voidFlag" class="loan-tag loan-tag-muted" style="margin-left: 4px">已作废</span>
          </template>
        </el-table-column>
        <el-table-column label="限时链接" min-width="180">
          <template #default="{ row }">
            <span v-if="row.approveStatus === 'APPROVED' && !row.voidFlag" class="mono link-token">
              {{ row.linkExpireAt ? '24h 内有效 · 截止 ' + formatDateTime(row.linkExpireAt) : row.linkToken }}
            </span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <AppTableActions :actions="downloadActions(row)" />
          </template>
        </el-table-column>
      </el-table>
      </AppTableState>
      <AppPagination v-if="!errorD" v-model:page="queryD.page" v-model:size="queryD.size" :total="totalD" @change="loadD" />
    </div>

    <!-- ============ 客户分配审批（无归宿客户归属流转，仅审批分配管理员可见） ============ -->
    <div v-show="activeTab === 'allocation'" class="loan-card">
      <AppSearchBar :loading="loadingA" @search="searchA" @reset="resetA">
        <el-input v-model="queryA.keyword" placeholder="审批单号 / 企业名称 / 申请人" style="width: 240px" clearable @keyup.enter="searchA" />
      </AppSearchBar>

      <AppTableState :error="errorA" @retry="loadA">
      <el-table :data="dataA" v-loading="loadingA" stripe row-key="approvalNo">
        <template #empty>
          <AppEmpty title="暂无分配审批" desc="客户申请归属流转、无归宿客户分配将在此等待审批" />
        </template>
        <el-table-column prop="approvalNo" label="审批单号" min-width="180" show-overflow-tooltip />
        <el-table-column label="客户" min-width="180">
          <template #default="{ row }">
            <div class="cell-main">{{ row.entName || row.contactName || '未命名客户' }}</div>
            <div class="cell-sub">{{ row.customerGroup === 'PERSONAL' ? '个人客户' : '企业客户' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="申请人" min-width="140">
          <template #default="{ row }">
            <div class="cell-main">{{ row.applicantName || '姓名待补充' }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="contactName" label="联系人" width="120" show-overflow-tooltip />
        <el-table-column prop="contactPhone" label="联系电话" width="140" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="创建时间" width="160" sortable>
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <AppTableActions :actions="allocationActions(row)" />
          </template>
        </el-table-column>
      </el-table>
      </AppTableState>
      <AppPagination v-if="!errorA" v-model:page="queryA.page" v-model:size="queryA.size" :total="totalA" @change="loadA" />
    </div>

    <!-- 通用审核弹窗（产品 / 下载 / 分配共用） -->
    <AppDialog v-model:visible="auditVisible" title="审核" :loading="auditing" @confirm="onAudit">
      <el-form ref="auditFormRef" :model="auditForm" :rules="auditRules" label-width="100px" label-position="right">
        <el-form-item label="单号">
          <span class="mono">{{ auditForm.approvalNo }}</span>
        </el-form-item>
        <el-form-item label="审核结果" prop="approve">
          <el-radio-group v-model="auditForm.approve">
            <el-radio :value="true">通过</el-radio>
            <el-radio :value="false">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="意见" prop="opinion">
          <el-input v-model="auditForm.opinion" type="textarea" :rows="2" :placeholder="auditForm.approve ? '通过选填' : '驳回意见必填'" />
        </el-form-item>
      </el-form>
    </AppDialog>

    <!-- 下载申请弹窗 -->
    <AppDialog v-model:visible="applyVisible" title="发起无水印下载申请" :loading="applying" @confirm="onApply">
      <el-form ref="applyFormRef" :model="applyForm" :rules="applyRules" label-width="110px" label-position="right">
        <el-form-item label="资料清单" prop="attachmentIds">
          <el-select v-model="applyForm.attachmentIds" multiple filterable remote :remote-method="searchAttachments" :loading="attachmentLoading" placeholder="输入文件名、资料类型或工单号搜索" style="width: 100%" @visible-change="(v) => { if (v) searchAttachments('') }">
            <el-option v-for="item in attachmentOptions" :key="item.value" :label="item.label" :value="item.value" />
            <div v-if="!attachmentFinished && attachmentOptions.length" class="remote-more" @mousedown.prevent @click="loadMoreAttachments">{{ attachmentLoading ? '加载中…' : '加载更多' }}</div>
          </el-select>
        </el-form-item>
        <el-form-item label="用途说明" prop="purpose">
          <el-input v-model="applyForm.purpose" type="textarea" :rows="2" placeholder="如 报送银行 / 纸质留存" />
        </el-form-item>
        <el-form-item label="期望期限" prop="expectDays">
          <el-input-number v-model="applyForm.expectDays" :min="1" :max="365" :controls="false" placeholder="天" style="width: 140px" />
        </el-form-item>
      </el-form>
    </AppDialog>
  </div>
</template>

<script setup>
defineOptions({ name: '_approval' });
import { ref, reactive, computed, watch } from 'vue';
import { ElMessage } from 'element-plus';
import AppSearchBar from '@/components/AppSearchBar.vue';
import AppPagination from '@/components/AppPagination.vue';
import AppEmpty from '@/components/AppEmpty.vue';
import AppTableActions from '@/components/AppTableActions.vue';
import AppIcon from '@/components/AppIcon.vue';
import AppTableState from '@/components/AppTableState.vue';
import AppDialog from '@/components/AppDialog.vue';
import { useTable } from '@/composables/useTable';
import { useRemoteOptions } from '@/composables/useRemoteOptions';
import { formatDateTime } from '@/utils/format';
import { copyText } from '@/utils/clipboard';
import { useUserStore } from '@/store/user';
import {
  pageProductApprovals, auditProductApproval,
  pageDownloadApprovals, auditDownloadApproval, voidDownloadApproval, applyDownload,
  pageAllocationApprovals, auditAllocationApproval,
} from '@/api/approval';
import { pageAttachments } from '@/api/attachment';

/** D39/C24：分配审批对运营管理员 / 超级管理员 / 老板 / 团队管理者可见；后端按团队过滤列表（DM 仅本团队） */
const ALLOC_APPROVER = ['BOSS', 'OPERATOR', 'SUPER_ADMIN', 'SUPER', 'DEPT_MANAGER'];
const userStore = useUserStore();
const canAllocate = computed(() =>
  ALLOC_APPROVER.includes((userStore.roleCode || '').toUpperCase()),
);

const activeTab = ref('product');
const loadedTabs = reactive({ product: false, download: false, allocation: false });
const statusText = { PENDING: '待审核', APPROVED: '已通过', REJECTED: '已驳回' };
const statusTag = (s) => ({ PENDING: 'loan-tag-warning', APPROVED: 'loan-tag-success', REJECTED: 'loan-tag-danger' }[s] || 'loan-tag-muted');
function attachmentCount(value) {
  try { return Array.isArray(value) ? value.length : JSON.parse(value || '[]').length; } catch { return 0; }
}

async function onCopy(val) {
  try {
    await copyText(val || '');
    ElMessage.success('已复制');
  } catch {
    ElMessage.warning('复制失败');
  }
}

/** 产品审核表 */
const { loading: loadingP, error: errorP, data: dataP, total: totalP, query: queryP, load: loadP, onSearch: searchP, onReset: resetP, handleSortChange } =
  useTable(pageProductApprovals, { status: '', keyword: '' });

/** 下载审批表 */
const { loading: loadingD, error: errorD, data: dataD, total: totalD, query: queryD, load: loadD, onSearch: searchD, onReset: resetD, handleSortChange: handleSortChangeD } =
  useTable(pageDownloadApprovals, { status: '', keyword: '' });

/** 客户分配审批表 */
const { loading: loadingA, error: errorA, data: dataA, total: totalA, query: queryA, load: loadA, onSearch: searchA, onReset: resetA } =
  useTable(pageAllocationApprovals, { keyword: '' });

function productActions(row) {
  const actions = [];
    actions.push({ key: "copy", label: "复制单号", onClick: () => onCopy(row.approvalNo) });
  if (row.approveStatus === 'PENDING') {
    actions.push({ key: 'audit', label: '审核', type: 'success', onClick: () => openAudit('product', row) });
  }
  return actions;
}

function downloadActions(row) {
  const actions = [];
  actions.push({ key: 'copy', label: '复制单号', onClick: () => onCopy(row.approvalNo) });
  if (row.approveStatus === 'PENDING' && !row.voidFlag) {
    actions.push({ key: 'audit', label: '审批', type: 'success', onClick: () => openAudit('download', row) });
  }
  if (!row.voidFlag && row.approveStatus !== 'REJECTED') {
    actions.push({
      key: 'void',
      label: '作废',
      type: 'danger',
      confirm: `确认作废申请单「${row.approvalNo}」？`,
      onClick: () => onVoid(row),
    });
  }
  return actions;
}

function allocationActions(row) {
  const actions = [];
  actions.push({ key: 'copy', label: '复制单号', onClick: () => onCopy(row.approvalNo) });
  // 待审列表仅返回 PENDING 记录，故统一展示审核入口
  actions.push({ key: 'audit', label: '审核', type: 'success', onClick: () => openAudit('allocation', row) });
  return actions;
}

// ============================================================
// 审核
// ============================================================
const auditVisible = ref(false);
const auditing = ref(false);
const auditForm = reactive({ kind: 'product', approvalNo: '', approve: true, opinion: '' });
const auditFormRef = ref();
const auditRules = {
  opinion: [
    {
      validator: (rule, value, callback) => {
        if (!auditForm.approve && !(value || '').trim()) {
          callback(new Error('驳回意见必填'));
        } else {
          callback();
        }
      },
      trigger: 'blur',
    },
  ],
};

function openAudit(kind, row) {
  auditForm.kind = kind;
  auditForm.approvalNo = row.approvalNo;
  auditForm.approve = true;
  auditForm.opinion = '';
  auditFormRef.value?.clearValidate();
  auditVisible.value = true;
}

async function onAudit() {
  try {
    await auditFormRef.value.validate();
  } catch (e) {
    return;
  }
  auditing.value = true;
  try {
    const payload = { approve: auditForm.approve, opinion: auditForm.opinion.trim() || null };
    if (auditForm.kind === 'product') {
      await auditProductApproval(auditForm.approvalNo, payload);
      ElMessage.success(auditForm.approve ? '已通过，产品入全量库' : '已驳回');
      loadP();
    } else if (auditForm.kind === 'allocation') {
      await auditAllocationApproval(auditForm.approvalNo, payload);
      ElMessage.success(auditForm.approve ? '已通过，客户归属流转完成' : '已驳回');
      loadA();
    } else {
      await auditDownloadApproval(auditForm.approvalNo, payload);
      ElMessage.success(auditForm.approve ? '已通过，24h 限时链接已生成' : '已驳回');
      loadD();
    }
    auditVisible.value = false;
  } catch (e) { /* 拦截器已提示 */ } finally {
    auditing.value = false;
  }
}

async function onVoid(row) {
  try {
    await voidDownloadApproval(row.approvalNo);
    ElMessage.success('已作废');
    loadD();
  } catch (e) { /* 拦截器已提示 */ }
}

// ============================================================
// 发起下载申请
// ============================================================
const applyVisible = ref(false);
const applying = ref(false);
const applyForm = reactive({ attachmentIds: [], purpose: '', expectDays: null });
const {
  items: attachmentOptions, loading: attachmentLoading, finished: attachmentFinished,
  search: searchAttachments, loadMore: loadMoreAttachments,
} = useRemoteOptions(pageAttachments, {
  normalize: (a) => ({ value: a.id, label: `${a.fileName || '未命名资料'} · ${a.attachmentType || '其他资料'}${a.orderNo ? ` · 工单 ${a.orderNo}` : ''}` }),
});
const applyFormRef = ref();
const applyRules = {
  attachmentIds: [
    { required: true, message: '资料清单必填', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (!Array.isArray(value) || value.length === 0) {
          callback(new Error('附件列表不能为空'));
        } else {
          callback();
        }
      },
      trigger: 'blur',
    },
  ],
  purpose: [
    { required: true, message: '用途说明必填', trigger: 'blur' },
    { min: 2, max: 200, message: '用途说明需 2-200 字', trigger: 'blur' },
  ],
  expectDays: [
    {
      validator: (rule, value, callback) => {
        if (value != null && (!Number.isInteger(value) || value < 1 || value > 365)) {
          callback(new Error('期望期限需为 1-365 天的整数'));
        } else {
          callback();
        }
      },
      trigger: 'change',
    },
  ],
};

function openApply() {
  applyForm.attachmentIds = [];
  applyForm.purpose = '';
  applyForm.expectDays = null;
  applyFormRef.value?.clearValidate();
  applyVisible.value = true;
}

async function onApply() {
  try {
    await applyFormRef.value.validate();
  } catch (e) {
    return;
  }
  applying.value = true;
  try {
    await applyDownload({ ...applyForm, attachmentIds: JSON.stringify(applyForm.attachmentIds) });
    ElMessage.success('申请已提交');
    applyVisible.value = false;
    loadD();
  } catch (e) { /* 拦截器已提示 */ } finally {
    applying.value = false;
  }
}

/** 页签首次激活时再取数，避免审批中心首屏并发三套分页。 */
watch(activeTab, async (tab) => {
  if (loadedTabs[tab]) return;
  if (tab === 'allocation' && !canAllocate.value) return;
  loadedTabs[tab] = true;
  try {
    if (tab === 'product') await loadP();
    else if (tab === 'download') await loadD();
    else await loadA();
  } catch {
    loadedTabs[tab] = false;
  }
}, { immediate: true });
</script>

<style scoped>
.cell-main { font-weight: 500; }
.cell-sub { font-size: 12px; color: var(--loan-text-secondary, #8a94a6); }
.mono { font-family: "SF Mono", Menlo, Consolas, monospace; }
.link-token { font-size: 12px; color: var(--loan-primary, #4f7cff); }
.muted { color: var(--loan-text-secondary, #8a94a6); }
.remote-more { min-height: 36px; display: flex; align-items: center; justify-content: center; color: var(--loan-primary); cursor: pointer; font-size: 13px; }
</style>
