<template>
  <div class="rt-page">
    <div class="loan-page-header">
      <div>
        <h2 class="loan-page-title">规则模版</h2>
        <p class="loan-page-subtitle">一条可复用规则的骨架（字段定义 + 版本快照），上线后可「导入为规则」实例化</p>
      </div>
      <el-button type="primary" @click="openCreate">
        <AppIcon name="add" :size="14" />
        新建模版
      </el-button>
    </div>

    <div class="loan-card">
      <AppSearchBar :loading="loading" @search="onSearch" @reset="onReset">
        <el-select v-model="query.customerGroup" placeholder="客群" clearable style="width: 130px">
          <el-option label="企业" value="ENTERPRISE" />
          <el-option label="个人" value="PERSONAL" />
        </el-select>
        <el-input v-model="query.keyword" placeholder="模版编码 / 名称" clearable style="width: 200px" @keyup.enter="onSearch" />
      </AppSearchBar>

      <el-table :data="data" v-loading="loading" stripe row-key="templateCode">
        <template #empty>
          <AppEmpty title="暂无规则模版" desc="创建规则模版并发布后，可一键导入为规则" />
        </template>
        <el-table-column prop="templateCode" label="模版编码" width="180" show-overflow-tooltip />
        <el-table-column prop="templateName" label="模版名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="分类" width="130">
          <template #default="{ row }">{{ categoryName(row.categoryId) }}</template>
        </el-table-column>
        <el-table-column label="客群" width="90">
          <template #default="{ row }"><DictTag type="customerGroup" :value="row.customerGroup" /></template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <span class="loan-tag" :class="row.status === 'ACTIVE' ? 'loan-tag-success' : 'loan-tag-muted'">{{ row.status === 'ACTIVE' ? '已上线' : '草稿' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <AppTableActions :actions="rowActions(row)" />
          </template>
        </el-table-column>
      </el-table>

      <AppPagination v-model:page="query.page" v-model:size="query.size" :total="total" @change="load" />
    </div>

    <!-- 新建/编辑模版 -->
    <AppDialog v-model:visible="tplDialog.visible" :title="tplDialog.title" :loading="tplDialog.saving" @confirm="onSaveTpl">
      <el-form ref="tplFormRef" :model="tplDialog.form" :rules="tplRules" label-width="90px">
        <el-form-item label="模版编码" prop="templateCode"><el-input v-model="tplDialog.form.templateCode" placeholder="唯一编码" /></el-form-item>
        <el-form-item label="模版名称" prop="templateName"><el-input v-model="tplDialog.form.templateName" /></el-form-item>
        <el-form-item label="分类">
          <el-select v-model="tplDialog.form.categoryId" clearable placeholder="选择分类" style="width: 100%">
            <el-option v-for="c in categoryList" :key="c.id" :label="c.categoryName" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="客群" prop="customerGroup">
          <el-select v-model="tplDialog.form.customerGroup" style="width: 100%">
            <el-option label="企业" value="ENTERPRISE" />
            <el-option label="个人" value="PERSONAL" />
            <el-option label="通用" value="COMMON" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明"><el-input v-model="tplDialog.form.description" type="textarea" /></el-form-item>
      </el-form>
    </AppDialog>

    <!-- 字段编排弹窗 -->
    <AppDialog v-model:visible="editVisible" :title="`字段编排 · ${currentTpl?.templateName || ''}`" width="820px">
      <div class="rt-edit">
        <div class="rt-field-table">
          <div class="rt-field-row rt-field-head">
            <span class="c1">字段编码</span>
            <span class="c2">字段名称</span>
            <span class="c3">类型</span>
            <span class="c4">运算符</span>
            <span class="c5">默认值</span>
            <span class="c6">必填</span>
            <span class="c7">操作</span>
          </div>
          <div v-for="f in editFields" :key="f.fieldBizCode" class="rt-field-row">
            <span class="c1">{{ f.fieldCode }}</span>
            <span class="c2">{{ f.fieldName }}</span>
            <span class="c3">{{ f.fieldType }}</span>
            <span class="c4">{{ f.operator }}</span>
            <span class="c5" :title="f.defaultValue">{{ f.defaultValue || '—' }}</span>
            <span class="c6">{{ f.required === 1 ? '是' : '否' }}</span>
            <span class="c7">
              <el-button link type="primary" @click="openFieldDialog(f)">编辑</el-button>
              <el-button link type="danger" @click="onDeleteField(f)">删除</el-button>
            </span>
          </div>
          <div v-if="!editFields.length" class="rt-empty">暂无字段定义，点击下方「添加字段」</div>
        </div>
        <el-button style="margin-top: 12px" @click="openFieldDialog()">+ 添加字段</el-button>
      </div>
    </AppDialog>

    <!-- 字段编辑弹窗 -->
    <AppDialog v-model:visible="fieldDialog.visible" :title="fieldDialog.title" :loading="fieldDialog.saving" @confirm="onSaveField">
      <el-form ref="fieldFormRef" :model="fieldDialog.form" :rules="fieldRules" label-width="90px">
        <el-form-item label="字段编码" prop="fieldCode"><el-input v-model="fieldDialog.form.fieldCode" /></el-form-item>
        <el-form-item label="字段名称" prop="fieldName"><el-input v-model="fieldDialog.form.fieldName" /></el-form-item>
        <el-form-item label="类型" prop="fieldType">
          <el-select v-model="fieldDialog.form.fieldType" style="width: 100%">
            <el-option v-for="t in FIELD_TYPES" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="运算符" prop="operator">
          <el-select v-model="fieldDialog.form.operator" filterable style="width: 100%">
            <el-option v-for="o in OPERATORS" :key="o" :label="o" :value="o" />
          </el-select>
        </el-form-item>
        <el-form-item label="默认值"><el-input v-model="fieldDialog.form.defaultValue" /></el-form-item>
        <el-form-item label="必填">
          <el-switch v-model="fieldDialog.requiredFlag" />
        </el-form-item>
        <el-form-item label="顺序"><el-input-number v-model="fieldDialog.form.sort" :min="0" /></el-form-item>
      </el-form>
    </AppDialog>

    <!-- 导入为规则弹窗 -->
    <AppDialog v-model:visible="importDialog.visible" title="导入为规则" :loading="importDialog.saving" @confirm="onImport">
      <el-form label-width="90px">
        <el-form-item label="选择字段">
          <el-select v-model="importDialog.fieldCode" placeholder="选择字段定义（缺省取第一个）" clearable style="width: 100%">
            <el-option v-for="f in importFields" :key="f.fieldBizCode" :label="`${f.fieldName}（${f.fieldCode}）`" :value="f.fieldBizCode" />
          </el-select>
        </el-form-item>
        <div class="rt-import-tip">将按所选字段定义实例化一条「规则目录」规则（草稿态），字段编码/名称/运算符/默认值均来自模版。</div>
      </el-form>
    </AppDialog>
  </div>
</template>

<script setup>
defineOptions({ name: '_rule_template' });
import { ref, reactive, computed, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import DictTag from '@/components/DictTag.vue';
import AppSearchBar from '@/components/AppSearchBar.vue';
import AppPagination from '@/components/AppPagination.vue';
import AppEmpty from '@/components/AppEmpty.vue';
import AppTableActions from '@/components/AppTableActions.vue';
import AppDialog from '@/components/AppDialog.vue';
import AppIcon from '@/components/AppIcon.vue';
import { useTable } from '@/composables/useTable';
import { appConfirm } from '@/utils/confirm';
import {
  pageTemplate, categories, createTemplate, updateTemplate, deleteTemplate, publishTemplate, offlineTemplate, templateDetail, importToRule,
  createField, updateField, deleteField,
} from '@/api/ruleTemplate';

const FIELD_TYPES = ['STRING', 'NUMBER', 'DATE', 'LIST'];
const OPERATORS = ['==', '!=', '>', '<', '>=', '<=', 'in', 'not_in', 'contains', 'not_contains', 'between', 'is_null', 'not_null'];

const categoryList = ref([]);
const { loading, data, total, query, load, onSearch, onReset } = useTable(pageTemplate, { customerGroup: '', keyword: '' });

const categoryMap = computed(() => Object.fromEntries(categoryList.value.map((c) => [c.id, c.categoryName])));
function categoryName(id) {
  return categoryMap.value[id] || '—';
}

function rowActions(row) {
  return [
    { key: 'fields', label: '字段', onClick: () => openEdit(row) },
    { key: 'import', label: '导入规则', type: 'primary', onClick: () => openImport(row) },
    row.status === 'ACTIVE'
      ? { key: 'offline', label: '下线', type: 'warning', confirm: `确认下线「${row.templateName}」？`, onClick: () => onOffline(row) }
      : { key: 'publish', label: '发布', type: 'success', onClick: () => onPublish(row) },
    { key: 'edit', label: '编辑', onClick: () => openTplDialog(row) },
    { key: 'del', label: '删除', type: 'danger', confirm: `确认删除「${row.templateName}」？`, onClick: () => onDelete(row) },
  ];
}

async function onPublish(row) { await publishTemplate(row.templateCode); ElMessage.success('已发布上线'); load(); }
async function onOffline(row) { await offlineTemplate(row.templateCode); ElMessage.success('已下线'); load(); }
async function onDelete(row) { await deleteTemplate(row.templateCode); ElMessage.success('已删除'); load(); }

// 模版 CRUD
const tplDialog = reactive({ visible: false, title: '', saving: false, editingId: null, form: { templateCode: '', templateName: '', categoryId: null, customerGroup: 'ENTERPRISE', description: '' } });
const tplFormRef = ref();
const tplRules = {
  templateCode: [{ required: true, message: '请输入模版编码', trigger: 'blur' }],
  templateName: [{ required: true, message: '请输入模版名称', trigger: 'blur' }],
  customerGroup: [{ required: true, message: '请选择客群', trigger: 'change' }],
};
function openCreate() {
  tplDialog.title = '新建模版';
  tplDialog.editingId = null;
  Object.assign(tplDialog.form, { templateCode: '', templateName: '', categoryId: null, customerGroup: 'ENTERPRISE', description: '' });
  tplDialog.visible = true;
}
function openTplDialog(row) {
  tplDialog.title = '编辑模版';
  tplDialog.editingId = row.id;
  Object.assign(tplDialog.form, { templateCode: row.templateCode, templateName: row.templateName, categoryId: row.categoryId, customerGroup: row.customerGroup, description: row.description });
  tplDialog.visible = true;
}
async function onSaveTpl() {
  await tplFormRef.value.validate();
  tplDialog.saving = true;
  try {
    if (tplDialog.editingId) await updateTemplate(tplDialog.form.templateCode, { ...tplDialog.form });
    else await createTemplate({ ...tplDialog.form });
    ElMessage.success('已保存');
    tplDialog.visible = false;
    load();
  } finally { tplDialog.saving = false; }
}

// 字段编排
const editVisible = ref(false);
const currentTpl = ref(null);
const editFields = ref([]);
async function openEdit(row) {
  currentTpl.value = row;
  editVisible.value = true;
  const res = await templateDetail(row.templateCode);
  editFields.value = res.data?.fields || [];
}

const fieldDialog = reactive({ visible: false, title: '', saving: false, editingId: null, requiredFlag: true, form: { fieldCode: '', fieldName: '', fieldType: 'STRING', operator: '==', defaultValue: '', sort: 0 } });
const fieldFormRef = ref();
const fieldRules = {
  fieldCode: [{ required: true, message: '请输入字段编码', trigger: 'blur' }],
  fieldName: [{ required: true, message: '请输入字段名称', trigger: 'blur' }],
  fieldType: [{ required: true, message: '请选择类型', trigger: 'change' }],
  operator: [{ required: true, message: '请选择运算符', trigger: 'change' }],
};
function openFieldDialog(f) {
  fieldDialog.title = f ? '编辑字段' : '添加字段';
  fieldDialog.editingId = f?.fieldBizCode || null;
  Object.assign(fieldDialog.form, f
    ? { fieldCode: f.fieldCode, fieldName: f.fieldName, fieldType: f.fieldType, operator: f.operator, defaultValue: f.defaultValue, sort: f.sort }
    : { fieldCode: '', fieldName: '', fieldType: 'STRING', operator: '==', defaultValue: '', sort: 0 });
  fieldDialog.requiredFlag = f ? f.required === 1 : true;
  fieldDialog.visible = true;
}
async function onSaveField() {
  await fieldFormRef.value.validate();
  fieldDialog.saving = true;
  try {
    const payload = { templateCode: currentTpl.value.templateCode, ...fieldDialog.form, required: fieldDialog.requiredFlag ? 1 : 0 };
    if (fieldDialog.editingId) await updateField(fieldDialog.editingId, payload);
    else await createField(payload);
    fieldDialog.visible = false;
    const res = await templateDetail(currentTpl.value.templateCode);
    editFields.value = res.data?.fields || [];
  } finally { fieldDialog.saving = false; }
}
async function onDeleteField(f) {
  try { await appConfirm(`确认删除字段「${f.fieldName}」？`); } catch { return; }
  await deleteField(f.fieldBizCode);
  const res = await templateDetail(currentTpl.value.templateCode);
  editFields.value = res.data?.fields || [];
}

// 导入为规则
const importDialog = reactive({ visible: false, saving: false, templateId: null, fieldCode: null });
const importFields = ref([]);
async function openImport(row) {
  importDialog.templateId = row.templateCode;
  importDialog.fieldCode = null;
  const res = await templateDetail(row.templateCode);
  importFields.value = res.data?.fields || [];
  importDialog.visible = true;
}
async function onImport() {
  importDialog.saving = true;
  try {
    const res = await importToRule(importDialog.templateId, importDialog.fieldCode || undefined);
    ElMessage.success(`已导入为规则：${res.data}`);
    importDialog.visible = false;
  } finally { importDialog.saving = false; }
}

onMounted(async () => {
  try { const res = await categories(); categoryList.value = res.data || []; } catch { categoryList.value = []; }
  load();
});
</script>

<style scoped>
.rt-field-table { border: 1px solid var(--loan-border); border-radius: var(--loan-radius); overflow: hidden; }
.rt-field-row { display: flex; align-items: center; gap: 8px; padding: 8px 12px; border-bottom: 1px solid var(--loan-border); font-size: 13px; color: var(--loan-text); }
.rt-field-row:last-child { border-bottom: none; }
.rt-field-head { background: var(--loan-surface); font-weight: 600; }
.c1 { width: 110px; flex-shrink: 0; }
.c2 { width: 110px; flex-shrink: 0; }
.c3 { width: 70px; flex-shrink: 0; }
.c4 { width: 90px; flex-shrink: 0; }
.c5 { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--loan-text-secondary); }
.c6 { width: 46px; flex-shrink: 0; }
.c7 { width: 100px; flex-shrink: 0; display: flex; gap: 4px; }
.rt-empty { padding: 20px; text-align: center; color: var(--loan-text-muted); font-size: 13px; }
.rt-import-tip { font-size: 12px; color: var(--loan-text-muted); line-height: 1.6; }
</style>
