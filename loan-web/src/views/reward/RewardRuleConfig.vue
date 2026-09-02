<template>
  <div class="reward-rule-page">
    <div class="loan-page-header">
      <div>
        <h2 class="loan-page-title">奖励规则配置</h2>
        <p class="loan-page-subtitle">按「产品 × 客群」分层配置直推/间推比例与奖励上下限 · 无全局默认，须显式配置</p>
      </div>
      <el-button type="primary" @click="openCreate">
        <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" style="margin-right: 4px; vertical-align: -2px"><path d="M12 5v14M5 12h14"/></svg>
        新增规则
      </el-button>
    </div>

    <div class="loan-card">
      <el-table :data="rules" v-loading="loading" stripe>
        <template #empty>
          <AppEmpty title="暂无奖励规则" desc="按产品×客群新增一条规则，成交时才会计入推荐奖励" />
        </template>
        <el-table-column label="产品" min-width="160">
          <template #default="{ row }">{{ productName(row.productCode) }}</template>
        </el-table-column>
        <el-table-column label="客群" width="90">
          <template #default="{ row }">
            <span class="loan-tag" :class="row.customerGroup === 'ENTERPRISE' ? 'loan-tag-info' : 'loan-tag-warning'">
              {{ row.customerGroup === 'ENTERPRISE' ? '企业' : row.customerGroup === 'PERSONAL' ? '个人' : row.customerGroup }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="直推比例(L1)" width="110" align="right">
          <template #default="{ row }">{{ fmtPercent(row.directRate) }}</template>
        </el-table-column>
        <el-table-column label="间推比例(L2)" width="130">
          <template #default="{ row }">
            <span v-if="row.indirectEnabled === 1">{{ fmtPercent(row.indirectRate) }}</span>
            <span v-else class="cell-sub">关</span>
          </template>
        </el-table-column>
        <el-table-column label="奖励下限" width="100" align="right">
          <template #default="{ row }">{{ row.minAmount != null ? '¥' + fmtAmount(row.minAmount) : '—' }}</template>
        </el-table-column>
        <el-table-column label="奖励上限" width="100" align="right">
          <template #default="{ row }">{{ row.maxAmount != null ? '¥' + fmtAmount(row.maxAmount) : '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <span class="loan-tag" :class="row.status === 'ACTIVE' ? 'loan-tag-success' : 'loan-tag-muted'">
              {{ row.status === 'ACTIVE' ? '生效中' : '已停用' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <AppTableActions :actions="rowActions(row)" />
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 规则编辑弹窗 -->
    <AppDialog v-model:visible="dialogVisible" :title="editingId ? '编辑奖励规则' : '新增奖励规则'" :loading="saving" @confirm="onSave">
      <el-form ref="formRef" :model="form" :rules="rules_valid" label-width="110px" label-position="right">
        <el-form-item label="产品" prop="productCode">
          <RemoteProductSelect v-model="form.productCode" :customer-group="form.customerGroup" />
        </el-form-item>
        <el-form-item label="客群" prop="customerGroup">
          <el-radio-group v-model="form.customerGroup">
            <el-radio value="ENTERPRISE">企业</el-radio>
            <el-radio value="PERSONAL">个人</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="直推比例(L1)" prop="directRate">
          <el-input-number v-model="form.directRate" :min="0" :max="1" :step="0.01" :precision="4" controls-position="right" style="width: 200px" />
          <span class="suffix">（0~1，如 0.1 = 10%）</span>
        </el-form-item>
        <el-form-item label="间推开关(L2)">
          <el-switch v-model="indirectOn" />
        </el-form-item>
        <el-form-item label="间推比例(L2)" v-if="indirectOn" prop="indirectRate">
          <el-input-number v-model="form.indirectRate" :min="0" :max="1" :step="0.01" :precision="4" controls-position="right" style="width: 200px" />
          <span class="suffix">（0~1）</span>
        </el-form-item>
        <el-form-item label="奖励下限(元)">
          <el-input-number v-model="form.minAmount" :min="0" :precision="2" :controls="false" style="width: 200px" />
        </el-form-item>
        <el-form-item label="奖励上限(元)">
          <el-input-number v-model="form.maxAmount" :min="0" :precision="2" :controls="false" style="width: 200px" />
        </el-form-item>
      </el-form>
    </AppDialog>
  </div>
</template>

<script setup>
defineOptions({ name: '_reward_rule' });
import { ref, reactive, onMounted, computed } from 'vue';
import { ElMessage } from 'element-plus';
import AppEmpty from '@/components/AppEmpty.vue';
import AppDialog from '@/components/AppDialog.vue';
import AppTableActions from '@/components/AppTableActions.vue';
import RemoteProductSelect from '@/components/RemoteProductSelect.vue';
import { listRewardRules, saveRewardRule, disableRewardRule } from '@/api/reward';

function fmtAmount(v) {
  return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}
function fmtPercent(v) {
  return v == null ? '—' : (Number(v) * 100).toFixed(2) + '%';
}

const loading = ref(false);
const rules = ref([]);
const productNames = reactive({});

const productName = (code) => {
  return productNames[code] || '产品信息待补充';
};

async function load() {
  loading.value = true;
  try {
    const [r] = await Promise.all([listRewardRules()]);
    rules.value = r.data || [];
    rules.value.forEach((row) => { if (row.productName) productNames[row.productCode] = row.productName; });
  } catch (e) { /* 拦截器已提示 */ } finally {
    loading.value = false;
  }
}

function rowActions(row) {
  const actions = [{ key: 'edit', label: '编辑', onClick: () => openEdit(row) }];
  if (row.status === 'ACTIVE') {
    actions.push({
      key: 'disable', label: '停用', type: 'danger',
      confirm: `确认停用「${productName(row.productCode)} / ${row.customerGroup === 'ENTERPRISE' ? '企业' : '个人'}」规则？停用后该场景成交不再结算奖励。`,
      onClick: () => onDisable(row),
    });
  }
  return actions;
}

const dialogVisible = ref(false);
const saving = ref(false);
const editingId = ref(null);
const formRef = ref();
const indirectOn = ref(false);
const form = reactive({
  productCode: '',
  customerGroup: 'ENTERPRISE',
  directRate: 0.1,
  indirectRate: 0.05,
  minAmount: null,
  maxAmount: null,
});
const rules_valid = {
  productCode: [{ required: true, message: '请选择产品', trigger: 'change' }],
  customerGroup: [{ required: true, message: '请选择客群', trigger: 'change' }],
  directRate: [{ required: true, message: '请填写直推比例', trigger: 'blur' }],
};

function resetForm() {
  Object.assign(form, {
    productCode: '', customerGroup: 'ENTERPRISE', directRate: 0.1,
    indirectRate: 0.05, minAmount: null, maxAmount: null,
  });
  indirectOn.value = false;
  editingId.value = null;
}

function openCreate() {
  resetForm();
  dialogVisible.value = true;
}

function openEdit(row) {
  Object.assign(form, {
    productCode: row.productCode,
    customerGroup: row.customerGroup,
    directRate: row.directRate,
    indirectRate: row.indirectRate,
    minAmount: row.minAmount,
    maxAmount: row.maxAmount,
  });
  indirectOn.value = row.indirectEnabled === 1;
  editingId.value = row.id;
  dialogVisible.value = true;
}

async function onSave() {
  await formRef.value.validate();
  saving.value = true;
  try {
    const payload = {
      productCode: form.productCode,
      customerGroup: form.customerGroup,
      directRate: form.directRate,
      indirectEnabled: indirectOn.value ? 1 : 0,
      indirectRate: indirectOn.value ? form.indirectRate : null,
      minAmount: form.minAmount,
      maxAmount: form.maxAmount,
    };
    if (editingId.value) payload.id = editingId.value;
    await saveRewardRule(payload);
    ElMessage.success('已保存');
    dialogVisible.value = false;
    load();
  } catch (e) { /* 拦截器已提示 */ } finally {
    saving.value = false;
  }
}

async function onDisable(row) {
  try {
    await disableRewardRule(row.id);
    ElMessage.success('已停用');
    load();
  } catch (e) { /* 拦截器已提示 */ }
}

onMounted(load);
</script>

<style scoped>
.cell-sub { color: var(--loan-text-secondary, #8a94a6); font-size: 12px; }
.suffix { margin-left: 8px; color: var(--loan-text-secondary, #8a94a6); font-size: 12px; }
</style>
