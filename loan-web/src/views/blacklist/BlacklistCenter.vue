<template>
  <div class="blacklist-page">
    <div class="loan-page-header">
      <div>
        <h2 class="loan-page-title">风控黑名单</h2>
        <p class="loan-page-subtitle">全局前置风控命中直接 REJECT · 提交即全局生效留痕到人 · 解禁仅老板</p>
      </div>
      <el-button type="primary" @click="openAdd">
        <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" style="margin-right: 4px; vertical-align: -2px"><path d="M12 5v14M5 12h14"/></svg>
        新增黑名单
      </el-button>
    </div>

    <div class="loan-card">
      <AppSearchBar :loading="loading" @search="onSearch" @reset="onReset">
        <el-select v-model="query.dimension" placeholder="维度" clearable style="width: 140px">
          <el-option v-for="(t, k) in dimensionText" :key="k" :label="t" :value="k" />
        </el-select>
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px">
          <el-option label="生效" value="EFFECTIVE" /><el-option label="已解禁" value="RELEASED" />
        </el-select>
        <el-input v-model="query.keyword" placeholder="手机号 / 证件 / 信用代码（精确）" style="width: 240px" clearable @keyup.enter="onSearch" />
      </AppSearchBar>

      <el-table :data="data" v-loading="loading" stripe row-key="id" @sort-change="handleSortChange">
        <template #empty>
          <AppEmpty title="暂无名单记录" desc="添加个人或企业至名单后，将按维度拦截匹配" />
        </template>
        <el-table-column label="维度" width="120">
          <template #default="{ row }">
            <span class="loan-tag loan-tag-danger">{{ dimensionText[row.dimension] || row.dimension }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="value" label="命中值" min-width="180" show-overflow-tooltip />
        <el-table-column label="原因分类" width="110">
          <template #default="{ row }">
            <span class="loan-tag loan-tag-warning">{{ reasonText[row.reasonType] || row.reasonType }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="reasonRemark" label="原因说明" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.reasonRemark || '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <span class="loan-tag" :class="row.status === 'EFFECTIVE' ? 'loan-tag-danger' : 'loan-tag-muted'">
              {{ row.status === 'EFFECTIVE' ? '生效' : '已解禁' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="createdBy" label="录入人" width="100" />
        <el-table-column prop="createdAt" label="创建时间" width="160" sortable>
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <AppTableActions :actions="rowActions(row)" />
          </template>
        </el-table-column>
      </el-table>
      <AppPagination v-model:page="query.page" v-model:size="query.size" :total="total" @change="load" />
    </div>

    <!-- 新增弹窗 -->
    <AppDialog v-model:visible="addVisible" title="新增黑名单" :loading="adding" @confirm="onAdd">
      <el-form ref="addFormRef" :model="addForm" :rules="addRules" label-width="100px" label-position="right">
        <el-form-item label="维度" prop="dimension">
          <el-select v-model="addForm.dimension" style="width: 100%">
            <el-option v-for="(t, k) in dimensionText" :key="k" :label="t" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="命中值" prop="value">
          <el-input v-model="addForm.value" placeholder="手机号 / 身份证 / 统一信用代码 / 法人姓名" />
        </el-form-item>
        <el-form-item label="原因分类" prop="reasonType">
          <el-select v-model="addForm.reasonType" style="width: 100%">
            <el-option v-for="(t, k) in reasonText" :key="k" :label="t" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="原因说明">
          <el-input v-model="addForm.reasonRemark" type="textarea" :rows="2" placeholder="可选" />
        </el-form-item>
      </el-form>
    </AppDialog>
  </div>
</template>

<script setup>
defineOptions({ name: '_blacklist' });
import { ref, reactive, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import AppSearchBar from '@/components/AppSearchBar.vue';
import AppPagination from '@/components/AppPagination.vue';
import AppEmpty from '@/components/AppEmpty.vue';
import AppTableActions from '@/components/AppTableActions.vue';
import AppDialog from '@/components/AppDialog.vue';
import { useTable } from '@/composables/useTable';
import { formatDateTime } from '@/utils/format';
import { pageBlacklist, addBlacklist, releaseBlacklist } from '@/api/blacklist';

const dimensionText = { PHONE: '手机号', ID_CARD: '身份证', CREDIT_CODE: '企业信用代码', LEGAL_PERSON: '法人' };
const reasonText = { FRAUD: '欺诈', DISHONEST: '失信', SENSITIVE: '敏感', OTHER: '其他' };

const { loading, data, total, query, load, onSearch, onReset, handleSortChange } = useTable(pageBlacklist, {
  dimension: '', status: '', keyword: '',
});

function rowActions(row) {
  if (row.status !== 'EFFECTIVE') return [];
  return [{
    key: 'release',
    label: '解禁',
    type: 'danger',
    confirm: `确认解禁「${row.value}」？解禁仅老板可操作。`,
    onClick: () => onRelease(row),
  }];
}

async function onRelease(row) {
  try {
    await releaseBlacklist({ id: row.id });
    ElMessage.success('已解禁');
    load();
  } catch (e) { /* 拦截器已提示 */ }
}

const addVisible = ref(false);
const adding = ref(false);
const addForm = reactive({ dimension: 'PHONE', value: '', reasonType: 'FRAUD', reasonRemark: '' });
const addFormRef = ref();
const addRules = {
  dimension: [{ required: true, message: '请选择维度', trigger: 'change' }],
  value: [{ required: true, message: '请输入命中值', trigger: 'blur' }],
  reasonType: [{ required: true, message: '请选择原因分类', trigger: 'change' }],
};

function openAdd() {
  Object.assign(addForm, { dimension: 'PHONE', value: '', reasonType: 'FRAUD', reasonRemark: '' });
  addVisible.value = true;
}

async function onAdd() {
  await addFormRef.value.validate();
  adding.value = true;
  try {
    await addBlacklist({ ...addForm });
    ElMessage.success('已加入黑名单（全局生效）');
    addVisible.value = false;
    load();
  } catch (e) { /* 拦截器已提示 */ } finally {
    adding.value = false;
  }
}

onMounted(load);
</script>
