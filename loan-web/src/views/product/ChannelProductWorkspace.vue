<template>
  <div class="channel-product-page">
    <div class="loan-page-header">
      <div>
        <h2 class="loan-page-title">我的产品</h2>
        <p class="loan-page-subtitle">仅展示本渠道录入产品；提交后由平台超级管理员或老板审批</p>
      </div>
      <el-button type="primary" @click="openCreate">
        <AppIcon name="add" :size="14" />录入产品
      </el-button>
    </div>

    <div class="loan-card">
      <AppTableState :error="error" @retry="load">
        <el-table :data="rows" v-loading="loading" stripe row-key="code">
          <template #empty>
            <AppEmpty title="暂无产品" desc="录入第一项合作产品，保存草稿后提交平台审批" />
          </template>
          <el-table-column prop="productName" label="产品名称" min-width="180">
            <template #default="{ row }">{{ row.productName || '—' }}</template>
          </el-table-column>
          <el-table-column prop="bankName" label="所属银行" min-width="150">
            <template #default="{ row }">{{ row.bankName || '本渠道所属银行' }}</template>
          </el-table-column>
          <el-table-column prop="amountRange" label="额度区间" width="150" />
          <el-table-column prop="rate" label="利率区间" width="130" />
          <el-table-column label="审批状态" width="110">
            <template #default="{ row }">
              <span class="loan-tag" :class="statusTone(row.status)">{{ statusText(row.status) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="审批意见" min-width="180">
            <template #default="{ row }">{{ row.rejectReason || '—' }}</template>
          </el-table-column>
          <el-table-column label="录入时间" width="165">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <AppTableActions :actions="rowActions(row)" />
            </template>
          </el-table-column>
        </el-table>
      </AppTableState>
    </div>

    <AppDialog
      v-model:visible="dialog.visible"
      :title="dialog.approvalNo ? '编辑产品草稿' : '录入产品'"
      width="620px"
      :loading="dialog.saving"
      @confirm="save"
    >
      <el-form ref="formRef" :model="dialog.form" :rules="rules" label-width="112px">
        <el-form-item label="产品名称" prop="productName">
          <el-input v-model="dialog.form.productName" maxlength="128" show-word-limit placeholder="请输入对外产品名称" />
        </el-form-item>
        <el-form-item label="适用客群" prop="customerGroup">
          <el-radio-group v-model="dialog.form.customerGroup">
            <el-radio-button label="ENTERPRISE">企业客户</el-radio-button>
            <el-radio-button label="PERSONAL">个人客户</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="合作有效期">
          <el-date-picker v-model="dialog.form.cooperateUntil" type="date" value-format="YYYY-MM-DD" placeholder="请选择有效期" style="width:100%" />
        </el-form-item>
        <el-form-item label="额度下限(万)">
          <el-input-number v-model="dialog.form.amountMin" :min="0" :controls="false" style="width:100%" />
        </el-form-item>
        <el-form-item label="额度上限(万)">
          <el-input-number v-model="dialog.form.amountMax" :min="0" :controls="false" style="width:100%" />
        </el-form-item>
        <el-form-item label="利率区间(%)">
          <div class="range-field">
            <el-input-number v-model="dialog.form.rateMin" :min="0" :max="100" :precision="4" :controls="false" placeholder="下限" />
            <span>至</span>
            <el-input-number v-model="dialog.form.rateMax" :min="0" :max="100" :precision="4" :controls="false" placeholder="上限" />
          </div>
        </el-form-item>
        <el-form-item label="期限区间(月)">
          <div class="range-field">
            <el-input-number v-model="dialog.form.termMin" :min="1" :controls="false" placeholder="下限" />
            <span>至</span>
            <el-input-number v-model="dialog.form.termMax" :min="1" :controls="false" placeholder="上限" />
          </div>
        </el-form-item>
        <el-form-item label="纳税门槛(万)">
          <el-input-number v-model="dialog.form.taxThreshold" :min="0" :controls="false" style="width:100%" />
        </el-form-item>
        <el-form-item label="开票要求">
          <el-input v-model="dialog.form.invoiceRequire" maxlength="255" placeholder="例如：近12个月开票额不低于100万" />
        </el-form-item>
        <el-form-item label="进件要求">
          <el-input v-model="dialog.form.requirementText" type="textarea" :rows="4" maxlength="1000" show-word-limit placeholder="请用业务语言说明准入条件、担保方式和材料清单" />
        </el-form-item>
      </el-form>
    </AppDialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import AppDialog from '@/components/AppDialog.vue';
import AppEmpty from '@/components/AppEmpty.vue';
import AppIcon from '@/components/AppIcon.vue';
import AppTableActions from '@/components/AppTableActions.vue';
import AppTableState from '@/components/AppTableState.vue';
import { appConfirm } from '@/utils/confirm';
import { formatDateTime } from '@/utils/format';
import {
  applyDeleteChannelProduct,
  cancelDeleteChannelProduct,
  createChannelProduct,
  getChannelProduct,
  listChannelProducts,
  revokeChannelProduct,
  submitChannelProduct,
  updateChannelProduct,
} from '@/api/channelProduct';

const loading = ref(false);
const error = ref('');
const rows = ref([]);
const formRef = ref();
const emptyForm = () => ({
  productName: '', customerGroup: 'ENTERPRISE', cooperateUntil: '',
  amountMin: null, amountMax: null, rateMin: null, rateMax: null,
  termMin: null, termMax: null, taxThreshold: null, invoiceRequire: '', requirementText: '',
});
const dialog = reactive({ visible: false, saving: false, approvalNo: '', form: emptyForm() });
const rules = {
  productName: [{ required: true, message: '请输入产品名称', trigger: 'blur' }],
  customerGroup: [{ required: true, message: '请选择适用客群', trigger: 'change' }],
};

const statusText = (status) => ({ DRAFT: '草稿', PENDING: '待审批', APPROVED: '已上架', REJECTED: '已驳回', PENDING_DELETE: '待删除' }[status] || status || '—');
const statusTone = (status) => ({ DRAFT: 'loan-tag-muted', PENDING: 'loan-tag-warning', APPROVED: 'loan-tag-success', REJECTED: 'loan-tag-danger', PENDING_DELETE: 'loan-tag-danger' }[status] || 'loan-tag-muted');

async function load() {
  loading.value = true;
  error.value = '';
  try {
    const res = await listChannelProducts();
    rows.value = Array.isArray(res.data) ? res.data : [];
  } catch (e) {
    rows.value = [];
    error.value = e?.message || '产品加载失败';
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  dialog.approvalNo = '';
  dialog.form = emptyForm();
  dialog.visible = true;
}

async function openEdit(row) {
  try {
    const res = await getChannelProduct(row.code);
    const data = res.data || {};
    dialog.approvalNo = row.code;
    dialog.form = {
      productName: data.productName || '',
      customerGroup: data.customerGroup || 'ENTERPRISE',
      cooperateUntil: data.cooperateUntil || '',
      amountMin: toWan(data.amountMin), amountMax: toWan(data.amountMax),
      rateMin: numberOrNull(data.rateMin), rateMax: numberOrNull(data.rateMax),
      termMin: numberOrNull(data.termMin), termMax: numberOrNull(data.termMax),
      taxThreshold: toWan(data.taxThreshold),
      invoiceRequire: data.invoiceRequire || '',
      requirementText: requirementDescription(data.bizTermsJson),
    };
    dialog.visible = true;
  } catch (e) { /* 统一提示 */ }
}

async function save() {
  await formRef.value.validate();
  dialog.saving = true;
  try {
    const f = dialog.form;
    const data = {
      productName: f.productName.trim(),
      customerGroup: f.customerGroup,
      cooperateUntil: f.cooperateUntil || undefined,
      amountMin: toYuan(f.amountMin), amountMax: toYuan(f.amountMax),
      rateMin: toRate(f.rateMin), rateMax: toRate(f.rateMax),
      termMin: f.termMin, termMax: f.termMax,
      taxThreshold: toYuan(f.taxThreshold),
      invoiceRequire: f.invoiceRequire.trim() || undefined,
      bizTermsJson: JSON.stringify({ description: f.requirementText.trim() }),
    };
    if (dialog.approvalNo) await updateChannelProduct(dialog.approvalNo, data);
    else await createChannelProduct(data);
    ElMessage.success('草稿已保存');
    dialog.visible = false;
    await load();
  } catch (e) { /* 统一提示 */ } finally { dialog.saving = false; }
}

async function act(action, row) {
  try {
    if (action === 'submit') await submitChannelProduct(row.code);
    if (action === 'revoke') await revokeChannelProduct(row.code);
    if (action === 'delete') {
      const ok = await appConfirm('申请删除后需平台审批，审批通过后产品将下架。确认提交申请？', '申请删除');
      if (!ok) return;
      await applyDeleteChannelProduct(row.code, '渠道 Web 申请下架');
    }
    if (action === 'cancelDelete') await cancelDeleteChannelProduct(row.code);
    ElMessage.success('操作成功');
    await load();
  } catch (e) { /* 统一提示 */ }
}

function rowActions(row) {
  if (row.status === 'DRAFT') return [
    { key: 'edit', label: '编辑', onClick: () => openEdit(row) },
    { key: 'submit', label: '提交审批', type: 'primary', onClick: () => act('submit', row) },
  ];
  if (row.status === 'PENDING') return [{ key: 'revoke', label: '撤销审批', onClick: () => act('revoke', row) }];
  if (row.status === 'REJECTED') return [{ key: 'edit', label: '编辑重提', type: 'primary', onClick: () => openEdit(row) }];
  if (row.status === 'APPROVED') return [{ key: 'delete', label: '申请删除', type: 'danger', onClick: () => act('delete', row) }];
  if (row.status === 'PENDING_DELETE') return [{ key: 'cancel', label: '撤销删除', onClick: () => act('cancelDelete', row) }];
  return [];
}

onMounted(load);

const numberOrNull = (value) => value == null || value === '' ? null : Number(value);
const toWan = (value) => value == null || value === '' ? null : Number(value) / 10000;
const toYuan = (value) => value == null || value === '' ? null : Number(value) * 10000;
const toRate = (value) => value == null || value === '' ? null : Number(value) / 100;
function requirementDescription(value) {
  if (!value) return '';
  try { return JSON.parse(value)?.description || ''; } catch { return String(value); }
}
</script>

<style scoped>
.range-field { display: flex; align-items: center; gap: 10px; width: 100%; }
.range-field .el-input-number { flex: 1; width: 0; }
</style>
