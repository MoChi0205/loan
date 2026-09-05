<template>
  <div class="cl-page">
    <div class="loan-page-header">
      <div>
        <h2 class="loan-page-title">渠道名单</h2>
        <p class="loan-page-subtitle">名单键：个人=手机号 MD5、企业=统一社会信用代码；命中白名单放行、黑名单拒绝</p>
      </div>
      <el-button type="primary" @click="openAdd">
        <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" style="margin-right: 4px; vertical-align: -2px"><path d="M12 5v14M5 12h14"/></svg>
        新增名单
      </el-button>
    </div>

    <div class="loan-card">
      <AppSearchBar :loading="loading" @search="onSearch" @reset="onReset">
        <el-select v-model="query.channelCode" placeholder="渠道" clearable filterable style="width: 160px">
          <el-option v-for="c in channels" :key="c.channelCode" :label="c.bankName" :value="c.channelCode" />
        </el-select>
        <el-select v-model="query.customerGroup" placeholder="客群" clearable style="width: 120px">
          <el-option label="企业" value="ENTERPRISE" />
          <el-option label="个人" value="PERSONAL" />
        </el-select>
        <el-select v-model="query.listType" placeholder="名单类型" clearable style="width: 130px">
          <el-option label="黑名单" value="LOCAL_BLACK" />
          <el-option label="白名单" value="LOCAL_WHITE" />
        </el-select>
        <el-input v-model="query.keyword" placeholder="名单键 / 手机号" clearable style="width: 200px" @keyup.enter="onSearch" />
      </AppSearchBar>

      <div v-if="selection.length" class="cl-batch-bar">
        <span class="cl-batch-count">已选 {{ selection.length }} 项</span>
        <el-button size="small" type="danger" @click="onBatchDelete">批量删除</el-button>
        <el-button size="small" @click="clearSelection">取消选择</el-button>
      </div>

      <el-table :data="data" v-loading="loading" stripe row-key="listCode" @selection-change="onSelectionChange">
        <el-table-column type="selection" width="44" />
        <el-table-column label="渠道" width="160">
          <template #default="{ row }">{{ channelName(row.channelCode) }}</template>
        </el-table-column>
        <el-table-column label="客群" width="90">
          <template #default="{ row }"><DictTag type="customerGroup" :value="row.customerGroup" /></template>
        </el-table-column>
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <span class="loan-tag" :class="row.listType === 'LOCAL_BLACK' ? 'loan-tag-danger' : 'loan-tag-success'">{{ row.listType === 'LOCAL_BLACK' ? '黑名单' : '白名单' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="listKey" label="名单键" min-width="220" show-overflow-tooltip />
        <el-table-column prop="createdBy" label="创建人" width="110" />
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" min-width="130" fixed="right">
          <template #default="{ row }">
            <AppTableActions :actions="[
              { key: 'edit', label: '编辑', onClick: () => openEdit(row) },
              { key: 'del', label: '删除', type: 'danger', confirm: '确认删除该名单项？', onClick: () => onDelete(row) },
            ]" />
          </template>
        </el-table-column>
      </el-table>

      <AppPagination v-model:page="query.page" v-model:size="query.size" :total="total" @change="load" />
    </div>

    <!-- 新增名单 -->
    <AppDialog v-model:visible="addDialog.visible" title="新增名单" :loading="addDialog.saving" @confirm="onAdd">
      <el-form :model="addDialog.form" label-width="90px">
        <el-form-item label="渠道">
          <el-select v-model="addDialog.form.channelCode" placeholder="选择渠道" filterable style="width: 100%">
            <el-option v-for="c in channels" :key="c.channelCode" :label="c.bankName" :value="c.channelCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="客群">
          <el-select v-model="addDialog.form.customerGroup" style="width: 100%">
            <el-option label="企业" value="ENTERPRISE" />
            <el-option label="个人" value="PERSONAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="名单类型">
          <el-select v-model="addDialog.form.listType" style="width: 100%">
            <el-option label="黑名单" value="LOCAL_BLACK" />
            <el-option label="白名单" value="LOCAL_WHITE" />
          </el-select>
        </el-form-item>
        <el-form-item label="名单键">
          <el-input v-model="addDialog.keys" type="textarea" :rows="5" :placeholder="addDialog.form.customerGroup === 'PERSONAL' ? '每行一个手机号' : '每行一个统一社会信用代码（18位）'" />
        </el-form-item>
        <div class="cl-tip">个人名单键以手机号 MD5 落库，企业以统一社会信用代码落库（后端自动归一化）。</div>
      </el-form>
    </AppDialog>

    <!-- 编辑名单：按 listCode 定位，不回传物理主键或改写业务编码 -->
    <AppDialog v-model:visible="editDialog.visible" title="编辑名单" :loading="editDialog.saving" @confirm="onEdit">
      <el-form :model="editDialog.form" label-width="90px">
        <el-form-item label="渠道">
          <el-select v-model="editDialog.form.channelCode" placeholder="选择渠道" filterable style="width: 100%">
            <el-option v-for="c in channels" :key="c.channelCode" :label="c.bankName" :value="c.channelCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="客群">
          <el-select v-model="editDialog.form.customerGroup" style="width: 100%">
            <el-option label="企业" value="ENTERPRISE" />
            <el-option label="个人" value="PERSONAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="名单类型">
          <el-select v-model="editDialog.form.listType" style="width: 100%">
            <el-option label="黑名单" value="LOCAL_BLACK" />
            <el-option label="白名单" value="LOCAL_WHITE" />
          </el-select>
        </el-form-item>
        <el-form-item label="新名单键">
          <el-input v-model="editDialog.form.listKey" :placeholder="editKeyPlaceholder" />
        </el-form-item>
        <div class="cl-tip">名单键留空则保留原值；变更客群时必须填写新手机号或统一社会信用代码。</div>
      </el-form>
    </AppDialog>
  </div>
</template>

<script setup>
defineOptions({ name: '_channel_user_list' });
import { ref, reactive, computed, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import DictTag from '@/components/DictTag.vue';
import AppSearchBar from '@/components/AppSearchBar.vue';
import AppPagination from '@/components/AppPagination.vue';
import AppTableActions from '@/components/AppTableActions.vue';
import AppDialog from '@/components/AppDialog.vue';
import { useTable } from '@/composables/useTable';
import { appConfirm } from '@/utils/confirm';
import { formatDateTime } from '@/utils/format';
import { listChannels } from '@/api/channel';
import { pageUserList, addUserList, updateUserList, deleteUserList, batchDeleteUserList } from '@/api/channelUserList';

const channels = ref([]);
const { loading, data, total, query, load, onSearch, onReset } = useTable(pageUserList, { channelCode: '', customerGroup: '', listType: '', keyword: '' });

const channelMap = computed(() => Object.fromEntries(channels.value.map((c) => [c.channelCode, c.bankName])));
function channelName(code) {
  return channelMap.value[code] || code;
}

// 多选
const selection = ref([]);
function onSelectionChange(rows) { selection.value = rows; }
function clearSelection() { selection.value = []; }

async function onDelete(row) {
  await deleteUserList(row.listCode);
  ElMessage.success('已删除');
  load();
}
async function onBatchDelete() {
  try { await appConfirm(`确认删除选中的 ${selection.value.length} 项名单？`); } catch { return; }
  await batchDeleteUserList(selection.value.map((r) => r.listCode));
  ElMessage.success('已删除');
  clearSelection();
  load();
}

// 编辑：业务编码只保存在弹窗状态中作为路径参数，不进入修改体。
const editDialog = reactive({
  visible: false,
  saving: false,
  listCode: '',
  originalCustomerGroup: '',
  form: { channelCode: '', customerGroup: '', listType: '', listKey: '' },
});
const editKeyPlaceholder = computed(() => (
  editDialog.form.customerGroup === 'PERSONAL' ? '留空保留原手机号名单键' : '留空保留原统一社会信用代码'
));
function openEdit(row) {
  editDialog.listCode = row.listCode;
  editDialog.originalCustomerGroup = row.customerGroup;
  Object.assign(editDialog.form, {
    channelCode: row.channelCode,
    customerGroup: row.customerGroup,
    listType: row.listType,
    listKey: '',
  });
  editDialog.visible = true;
}
async function onEdit() {
  if (editDialog.form.customerGroup !== editDialog.originalCustomerGroup && !editDialog.form.listKey.trim()) {
    ElMessage.warning('变更客群时必须填写新名单键');
    return;
  }
  editDialog.saving = true;
  try {
    const payload = { ...editDialog.form };
    if (!payload.listKey.trim()) delete payload.listKey;
    await updateUserList(editDialog.listCode, payload);
    ElMessage.success('修改成功');
    editDialog.visible = false;
    load();
  } finally { editDialog.saving = false; }
}

// 新增
const addDialog = reactive({ visible: false, saving: false, keys: '', form: { channelCode: '', customerGroup: 'ENTERPRISE', listType: 'LOCAL_BLACK' } });
function openAdd() {
  Object.assign(addDialog.form, { channelCode: '', customerGroup: 'ENTERPRISE', listType: 'LOCAL_BLACK' });
  addDialog.keys = '';
  addDialog.visible = true;
}
async function onAdd() {
  const keys = addDialog.keys.split('\n').map((s) => s.trim()).filter(Boolean);
  if (!keys.length) { ElMessage.warning('请输入名单键'); return; }
  addDialog.saving = true;
  try {
    const res = await addUserList({ ...addDialog.form, keys });
    ElMessage.success(`已新增 ${res.data} 条`);
    addDialog.visible = false;
    load();
  } finally { addDialog.saving = false; }
}

onMounted(async () => {
  try { const res = await listChannels(); channels.value = res.data || []; } catch { channels.value = []; }
  load();
});
</script>

<style scoped>
.cl-batch-bar { display: flex; align-items: center; gap: 12px; padding: 8px 12px; margin-bottom: 8px; background: var(--loan-surface); border: 1px solid var(--loan-border); border-radius: var(--loan-radius); }
.cl-batch-count { font-size: 13px; color: var(--loan-text-secondary); }
.cl-tip { font-size: 12px; color: var(--loan-text-muted); line-height: 1.6; }
</style>
