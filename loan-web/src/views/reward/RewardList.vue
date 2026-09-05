<template>
  <div class="reward-page">
    <div class="loan-page-header">
      <div>
        <h2 class="loan-page-title">推荐奖励</h2>
        <p class="loan-page-subtitle">工单成交自动结算 · 待审核 → 发放 / 驳回 · 可作废冲正</p>
      </div>
    </div>

    <div class="loan-card">
      <AppSearchBar :loading="loading" @search="onSearch" @reset="onReset">
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px">
          <el-option v-for="(t, k) in statusText" :key="k" :label="t" :value="k" />
        </el-select>
        <AppDateRange
          v-model="query.dateRange"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          width="250px"
          style="margin-left: 8px"
        />
        <el-input v-model="query.keyword" placeholder="奖励单号 / 工单号 / 客户姓名 / 手机号 / 企业名" style="width: 300px" clearable @keyup.enter="onSearch" />
      </AppSearchBar>

      <el-table :data="data" v-loading="loading" stripe row-key="rewardNo" @sort-change="handleSortChange">
        <template #empty>
          <AppEmpty title="暂无奖励记录" desc="工单成交并完成结算后，奖励记录将在此展示" />
        </template>
        <el-table-column prop="rewardNo" label="奖励单号" min-width="140" show-overflow-tooltip />
        <el-table-column label="推荐人" min-width="150">
          <template #default="{ row }">
            <div class="cell-main">{{ row.referrerName || '—' }}</div>
            <div v-if="row.referrerPhone" class="cell-sub">{{ row.referrerPhone }}</div>
          </template>
        </el-table-column>
        <el-table-column label="被推荐人" min-width="150">
          <template #default="{ row }">
            <div class="cell-main">{{ row.refereeName || '—' }}</div>
            <div v-if="row.refereePhone" class="cell-sub">{{ row.refereePhone }}</div>
          </template>
        </el-table-column>
        <el-table-column label="层级" width="60" align="center">
          <template #default="{ row }">
            <span class="loan-tag loan-tag-info">L{{ row.level }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="serviceOrderNo" label="关联工单" min-width="130" show-overflow-tooltip />
        <el-table-column label="基数(元)" width="100" align="right">
          <template #default="{ row }">
            <span class="mono">{{ fmtAmount(row.baseAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="比例" width="70" align="right">
          <template #default="{ row }">{{ fmtPercent(row.rateSnapshot) }}</template>
        </el-table-column>
        <el-table-column label="奖励金额" width="110" align="right">
          <template #default="{ row }">
            <span class="mono reward-amount">¥{{ fmtAmount(row.rewardAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="165" sortable="custom" show-overflow-tooltip>
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <span class="status-tag" :class="statusTagClass(row.status)">{{ statusText[row.status] || row.status }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <AppTableActions :actions="rowActions(row)" />
          </template>
        </el-table-column>
      </el-table>

      <AppPagination v-model:page="query.page" v-model:size="query.size" :total="total" @change="load" />
    </div>

    <!-- 审核弹窗（通过 / 驳回） -->
    <AppDialog v-model:visible="auditVisible" :title="`审核奖励单`" :loading="auditing" @confirm="onAudit">
      <el-form ref="auditFormRef" :model="auditForm" :rules="auditRules" label-width="110px" label-position="right">
        <el-form-item label="奖励单号">
          <span class="mono">{{ auditForm.rewardNo }}</span>
        </el-form-item>
        <el-form-item label="系统计算金额">
          <span class="mono">{{ auditForm.systemAmount != null ? '¥' + fmtAmount(auditForm.systemAmount) : '—' }}</span>
          <span v-if="auditForm.manualAdjustFlag === 1" class="cell-sub">（已人工调整）</span>
        </el-form-item>
        <el-form-item label="发放金额" prop="rewardAmount">
          <el-input-number v-model="auditForm.rewardAmount" :min="0" :precision="2" :controls="false" style="width: 200px" />
          <div class="cell-sub">默认等于系统计算金额；修改后将按此金额发放，须填写调整原因</div>
        </el-form-item>
        <el-form-item label="审核结果" prop="approve">
          <el-radio-group v-model="auditForm.approve">
            <el-radio :value="true">发放</el-radio>
            <el-radio :value="false">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="意见" prop="opinion">
          <el-input v-model="auditForm.opinion" type="textarea" :rows="2" :placeholder="auditForm.approve ? '通过选填' : '驳回原因必填'" />
        </el-form-item>
        <el-form-item label="调整原因" prop="manualAdjustReason" v-if="amountChanged">
          <el-input v-model="auditForm.manualAdjustReason" type="textarea" :rows="2" placeholder="手动调整奖励金额须填写原因" />
        </el-form-item>
      </el-form>
    </AppDialog>
  </div>
</template>

<script setup>
defineOptions({ name: '_reward' });
import { ref, reactive, computed, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import AppSearchBar from '@/components/AppSearchBar.vue';
import AppDateRange from '@/components/AppDateRange.vue';
import AppPagination from '@/components/AppPagination.vue';
import AppEmpty from '@/components/AppEmpty.vue';
import AppTableActions from '@/components/AppTableActions.vue';
import AppDialog from '@/components/AppDialog.vue';
import { useTable } from '@/composables/useTable';
import { formatDateTime, desensitizePhone } from '@/utils/format';
import { copyText } from '@/utils/clipboard';
import { pageRewards, auditReward, voidReward } from '@/api/reward';

const statusText = {
  PENDING_AUDIT: '待审核',
  GRANTED: '已发放',
  REJECTED: '已驳回',
  VOID: '已作废',
};

/** 状态标签样式类名（用独立 class 避免 el-tag 深色模式问题） */
function statusTagClass(s) {
  return ({
    PENDING_AUDIT: 'status-warning',
    GRANTED: 'status-success',
    REJECTED: 'status-danger',
    VOID: 'status-muted',
  }[s]) || 'status-muted';
}

function fmtAmount(v) {
  return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}
function fmtPercent(v) {
  return v == null ? '—' : (Number(v) * 100).toFixed(2) + '%';
}

/** 将 dateRange 数组拆为 startDate/endDate 后端参数 */
function rewardLoader(q) {
  const params = { ...q };
  if (q.dateRange && Array.isArray(q.dateRange) && q.dateRange.length === 2) {
    params.startDate = q.dateRange[0];
    params.endDate = q.dateRange[1];
  }
  delete params.dateRange;
  return pageRewards(params);
}

const { loading, data, total, query, load, onSearch, onReset, handleSortChange } = useTable(rewardLoader, {
  status: '',
  keyword: '',
  dateRange: null,
});

function rowActions(row) {
  const actions = [];
  actions.push({ key: "copy", label: "复制奖励单号", onClick: () => onCopy(row.rewardNo) });
  if (row.status === 'PENDING_AUDIT') {
    actions.push({ key: 'audit', label: '审核', type: 'success', onClick: () => openAudit(row) });
    actions.push({
      key: 'void',
      label: '作废',
      type: 'danger',
      confirm: `确认作废奖励单「${row.rewardNo}」？`,
      onClick: () => onVoid(row),
    });
  } else if (row.status === 'GRANTED') {
    actions.push({
      key: 'void',
      label: '作废',
      type: 'danger',
      confirm: `确认作废已发放奖励单「${row.rewardNo}」？`,
      onClick: () => onVoid(row),
    });
  }
  return actions;
}

const auditVisible = ref(false);
const auditing = ref(false);
const auditForm = reactive({ rewardNo: '', rewardAmount: null, systemAmount: null, manualAdjustFlag: 0, approve: true, opinion: '', manualAdjustReason: '' });
const auditFormRef = ref();
const amountChanged = computed(() => auditForm.systemAmount != null
  && Number(auditForm.rewardAmount) !== Number(auditForm.systemAmount));
const auditRules = {
  rewardAmount: [{ required: true, message: '请填写发放金额', trigger: 'blur' }],
  opinion: [
    {
      validator: (rule, value, callback) => {
        if (!auditForm.approve && !(value || '').trim()) {
          callback(new Error('驳回原因必填'));
        } else {
          callback();
        }
      },
      trigger: 'blur',
    },
  ],
  manualAdjustReason: [
    {
      validator: (rule, value, callback) => {
        if (auditForm.approve && amountChanged.value && !(value || '').trim()) {
          callback(new Error('手动调整奖励金额须填写原因'));
        } else {
          callback();
        }
      },
      trigger: 'blur',
    },
  ],
};

function openAudit(row) {
  auditForm.rewardNo = row.rewardNo;
  auditForm.rewardAmount = row.rewardAmount;
  auditForm.systemAmount = row.rewardAmount;
  auditForm.manualAdjustFlag = row.manualAdjustFlag;
  auditForm.approve = true;
  auditForm.opinion = '';
  auditForm.manualAdjustReason = '';
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
    await auditReward(auditForm.rewardNo, {
      approve: auditForm.approve,
      opinion: auditForm.opinion.trim() || null,
      rewardAmount: auditForm.approve ? auditForm.rewardAmount : null,
      manualAdjustReason: amountChanged.value ? auditForm.manualAdjustReason.trim() : null,
    });
    ElMessage.success(auditForm.approve ? '已发放' : '已驳回');
    auditVisible.value = false;
    load();
  } catch (e) { /* 拦截器已提示 */ } finally {
    auditing.value = false;
  }
}

async function onVoid(row) {
  try {
    await voidReward(row.rewardNo, { reason: '操作作废' });
    ElMessage.success('已作废');
    load();
  } catch (e) { /* 拦截器已提示 */ }
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
/* el-table 列总宽 > 容器宽时：width:100% 受 .loan-card 约束，overflow-x:auto 内部滚动条 */
.reward-page > .loan-card > .el-table {
  width: 100%;
  overflow-x: auto;
}
.cell-main {
  font-weight: 500;
  font-size: 13px;
}
.cell-sub {
  font-size: 12px;
  color: var(--loan-text-secondary, #8a94a6);
  margin-top: 2px;
}
.mono {
  font-family: "SF Mono", Menlo, Consolas, monospace;
}
.reward-amount {
  font-weight: 600;
  color: var(--loan-primary, #4f7cff);
}

/* 独立状态标签（不依赖 el-tag，避免深色模式/截断问题） */
.status-tag {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
  line-height: 20px;
  white-space: nowrap;
}
.status-success {
  background: rgba(47, 191, 113, 0.1);
  color: #16a34a;
}
.status-warning {
  background: rgba(245, 158, 11, 0.1);
  color: #d97706;
}
.status-danger {
  background: rgba(229, 72, 77, 0.1);
  color: #dc2626;
}
.status-muted {
  background: var(--loan-surface, #f1f3f5);
  color: var(--loan-text-secondary, #8a94a6);
}

/* 深色模式下状态标签适配 */
:global(html.dark) .status-success {
  background: rgba(47, 191, 113, 0.15);
  color: #4ade80;
}
:global(html.dark) .status-warning {
  background: rgba(245, 158, 11, 0.15);
  color: #fbbf24;
}
:global(html.dark) .status-danger {
  background: rgba(229, 72, 77, 0.15);
  color: #f87171;
}
:global(html.dark) .status-muted {
  background: rgba(255, 255, 255, 0.06);
  color: var(--loan-text-secondary, #8a94a6);
}
</style>
