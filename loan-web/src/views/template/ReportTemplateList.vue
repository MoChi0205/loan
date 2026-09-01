<template>
  <div class="template-page">
    <div class="loan-page-header">
      <div>
        <h2 class="loan-page-title">报告模板</h2>
        <p class="loan-page-subtitle">档位映射 / 免责声明 / 多维建议文案随版本锁定（改模板不影响历史报告）</p>
      </div>
      <el-button type="primary" @click="openAdd">
        <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" style="margin-right: 4px; vertical-align: -2px"><path d="M12 5v14M5 12h14"/></svg>
        新增模板
      </el-button>
    </div>

    <div class="loan-card">
      <AppSearchBar :loading="loading" @search="onSearch" @reset="onReset">
        <el-input v-model="query.keyword" placeholder="模板编码 / 名称" style="width: 220px" clearable @keyup.enter="onSearch" />
      </AppSearchBar>

      <el-table :data="data" v-loading="loading" stripe row-key="templateCode" @sort-change="handleSortChange">
        <el-table-column prop="templateCode" label="模板编码" width="120"  show-overflow-tooltip />
        <el-table-column prop="versionNo" label="版本" width="70">
          <template #default="{ row }">v{{ row.versionNo }}</template>
        </el-table-column>
        <el-table-column prop="templateName" label="模板名称" min-width="160" />
        <el-table-column prop="disclaimerText" label="免责声明" min-width="220" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <span class="loan-tag" :class="row.status === 'ACTIVE' ? 'loan-tag-success' : 'loan-tag-muted'">
              {{ row.status === 'ACTIVE' ? '已发布' : '已停用' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="publishedAt" label="发布时间" width="160" sortable>
          <template #default="{ row }">{{ row.publishedAt ? formatDateTime(row.publishedAt) : '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <AppTableActions :actions="rowActions(row)" />
          </template>
        </el-table-column>
      </el-table>
      <AppPagination v-model:page="query.page" v-model:size="query.size" :total="total" @change="load" />
    </div>

    <!-- 新增/编辑弹窗 -->
    <AppDialog v-model:visible="dialogVisible" :title="editing ? '编辑模板' : '新增模板'" :loading="saving" @confirm="onSave">
      <el-form ref="formRef" :model="dialogForm" :rules="formRules" label-width="110px" label-position="right">
        <el-form-item label="模板编码" prop="templateCode">
          <el-input v-model="dialogForm.templateCode" placeholder="如 DEFAULT_REPORT" :disabled="editing" />
        </el-form-item>
        <el-form-item label="版本号" prop="versionNo">
          <el-input-number v-model="dialogForm.versionNo" :min="1" :controls="false" :disabled="editing" style="width: 140px" />
        </el-form-item>
        <el-form-item label="模板名称" prop="templateName">
          <el-input v-model="dialogForm.templateName" placeholder="如 企业税贷标准报告" />
        </el-form-item>
        <el-form-item label="免责声明">
          <el-input v-model="dialogForm.disclaimerText" type="textarea" :rows="2" placeholder="不构成银行通过承诺" />
        </el-form-item>
        <el-form-item label="档位映射规则">
          <el-input v-model="dialogForm.gradeRuleJson" type="textarea" :rows="2" placeholder='{"HIGH":"命中>=3且核心维度全过"}' />
        </el-form-item>
        <el-form-item label="建议文案库">
          <el-input v-model="dialogForm.adviceRulesJson" type="textarea" :rows="3" placeholder='{"维度":"条件->建议文案"}' />
        </el-form-item>
      </el-form>
    </AppDialog>
  </div>
</template>

<script setup>
defineOptions({ name: '_report_template' });
import { ref, reactive, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import AppSearchBar from '@/components/AppSearchBar.vue';
import AppPagination from '@/components/AppPagination.vue';
import AppTableActions from '@/components/AppTableActions.vue';
import AppDialog from '@/components/AppDialog.vue';
import { useTable } from '@/composables/useTable';
import { formatDateTime } from '@/utils/format';
import { copyText } from '@/utils/clipboard';
import { pageReportTemplates, saveReportTemplate, toggleReportTemplate } from '@/api/report-template';

const { loading, data, total, query, load, onSearch, onReset, handleSortChange } = useTable(pageReportTemplates, { keyword: '' });

function rowActions(row) {
  return [
    { key: 'copy', label: '复制编码', onClick: () => onCopy(row.templateCode) },
    { key: 'edit', label: '编辑', onClick: () => onEdit(row) },
    row.status === 'ACTIVE'
      ? { key: 'disable', label: '停用', type: 'danger', confirm: `确认停用模板「${row.templateName}」？`, onClick: () => onToggle(row, false) }
      : { key: 'publish', label: '发布', type: 'success', confirm: `确认发布模板「${row.templateName}」？`, onClick: () => onToggle(row, true) },
  ];
}

async function onCopy(val) {
  try {
    await copyText(val || '');
    ElMessage.success('已复制');
  } catch {
    ElMessage.warning('复制失败');
  }
}

async function onToggle(row, active) {
  try {
    await toggleReportTemplate({ templateCode: row.templateCode, versionNo: row.versionNo, active });
    ElMessage.success(active ? '已发布' : '已停用');
    load();
  } catch (e) { /* 拦截器已提示 */ }
}

const dialogVisible = ref(false);
const editing = ref(false);
const saving = ref(false);
const formRef = ref();
const dialogForm = reactive({
  templateCode: '', versionNo: 1, templateName: '', disclaimerText: '',
  gradeRuleJson: '', adviceRulesJson: '', wecomGuideConfig: '', watermarkConfig: '',
});
const formRules = {
  templateCode: [{ required: true, message: '请输入模板编码', trigger: 'blur' }],
  versionNo: [{ required: true, message: '请输入版本号', trigger: 'blur' }],
  templateName: [{ required: true, message: '请输入模板名称', trigger: 'blur' }],
};

function openAdd() {
  editing.value = false;
  Object.assign(dialogForm, { templateCode: '', versionNo: 1, templateName: '', disclaimerText: '', gradeRuleJson: '', adviceRulesJson: '', wecomGuideConfig: '', watermarkConfig: '' });
  dialogVisible.value = true;
}

function onEdit(row) {
  editing.value = true;
  Object.assign(dialogForm, {
    templateCode: row.templateCode,
    versionNo: row.versionNo,
    templateName: row.templateName,
    disclaimerText: row.disclaimerText || '',
    gradeRuleJson: row.gradeRuleJson || '',
    adviceRulesJson: row.adviceRulesJson || '',
  });
  dialogVisible.value = true;
}

async function onSave() {
  await formRef.value.validate();
  saving.value = true;
  try {
    await saveReportTemplate({ ...dialogForm });
    ElMessage.success('保存成功');
    dialogVisible.value = false;
    load();
  } catch (e) { /* 拦截器已提示 */ } finally {
    saving.value = false;
  }
}

onMounted(load);
</script>
