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
            <template #default="{ row }">{{ row.productName || row.bankProductCode || '—' }}</template>
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
        <el-form-item label="银行产品编码" prop="bankProductCode">
          <el-input v-model="dialog.form.bankProductCode" :disabled="!!dialog.approvalNo" placeholder="请输入平台银行产品编码" />
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
        <el-form-item label="进件要求" prop="requirementText">
          <el-input v-model="dialog.form.requirementText" type="textarea" :rows="5" placeholder='请输入 JSON，例如 {"纳税要求":"10万元以上"}' />
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
const emptyForm = () => ({ bankProductCode: '', cooperateUntil: '', amountMin: null, amountMax: null, requirementText: '{}' });
const dialog = reactive({ visible: false, saving: false, approvalNo: '', form: emptyForm() });
const rules = {
  bankProductCode: [{ required: true, message: '请输入银行产品编码', trigger: 'blur' }],
  requirementText: [{ validator: (_rule, value, callback) => {
    try { JSON.parse(value || '{}'); callback(); } catch { callback(new Error('进件要求必须是合法 JSON')); }
  }, trigger: 'blur' }],
};

const statusText = (status) => ({ DRAFT: '草稿', PENDING: '待审批', OK: '已上架', REJECTED: '已驳回', PENDING_DELETE: '待删除' }[status] || status || '—');
const statusTone = (status) => ({ DRAFT: 'loan-tag-muted', PENDING: 'loan-tag-warning', OK: 'loan-tag-success', REJECTED: 'loan-tag-danger', PENDING_DELETE: 'loan-tag-danger' }[status] || 'loan-tag-muted');

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
      bankProductCode: data.bankProductCode || '',
      cooperateUntil: data.cooperateUntil || '',
      amountMin: data.amountMin == null ? null : Number(data.amountMin),
      amountMax: data.amountMax == null ? null : Number(data.amountMax),
      requirementText: data.requirement == null ? '{}' : (typeof data.requirement === 'string' ? data.requirement : JSON.stringify(data.requirement, null, 2)),
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
      bankProductCode: f.bankProductCode.trim(),
      cooperateUntil: f.cooperateUntil || undefined,
      amountRange: f.amountMin != null || f.amountMax != null ? `${f.amountMin ?? '?'}-${f.amountMax ?? '?'}万` : undefined,
      requirement: JSON.parse(f.requirementText || '{}'),
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
  if (row.status === 'OK') return [{ key: 'delete', label: '申请删除', type: 'danger', onClick: () => act('delete', row) }];
  if (row.status === 'PENDING_DELETE') return [{ key: 'cancel', label: '撤销删除', onClick: () => act('cancelDelete', row) }];
  return [];
}

onMounted(load);
</script>
