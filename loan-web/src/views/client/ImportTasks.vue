<template>
  <div class="import-task-page">
    <div class="loan-page-header">
      <div><h2 class="loan-page-title">客户导入进度</h2><p class="loan-page-subtitle">资源池导入完成后可在此查看处理进度、成功数与失败明细</p></div>
      <div class="header-actions">
        <span class="action-group">
          <span class="action-label">导入类型</span>
          <el-radio-group v-model="importType"><el-radio-button value="LEAD">客户资源池</el-radio-button><el-radio-button value="PRODUCT">产品</el-radio-button></el-radio-group>
        </span>
        <span class="action-divider" aria-hidden="true"></span>
        <span class="action-group">
          <span class="action-label">导入资源池</span>
          <el-select v-model="poolCode" placeholder="选择资源池" clearable style="width: 180px">
            <el-option v-for="pl in poolOptions" :key="pl.poolCode" :label="pl.poolName" :value="pl.poolCode" />
          </el-select>
          <el-button @click="downloadTemplate">下载模板</el-button>
          <el-button type="primary" @click="fileRef?.click()">选择 Excel 导入</el-button>
        </span>
        <input ref="fileRef" type="file" accept=".xlsx" hidden @change="preview" />
      </div>
    </div>
    <el-alert v-if="lastResult" :title="`最近导入：成功 ${lastResult.success || 0} 条，失败 ${lastResult.failed || 0} 条`" :type="lastResult.failed ? 'warning' : 'success'" show-icon style="margin-bottom: 12px" />
    <el-button v-if="lastResult?.failures?.length" link type="primary" @click="downloadFailures" style="margin-bottom: 12px">导出失败明细</el-button>
    <div class="loan-card">
      <AppSearchBar :loading="loading" @search="load" @reset="reset"><el-select v-model="query.importType" placeholder="导入类型" clearable style="width: 140px"><el-option label="客户资源池" value="LEAD" /><el-option label="产品" value="PRODUCT" /></el-select><el-select v-model="query.status" placeholder="任务状态" clearable style="width: 130px"><el-option label="待处理" value="PENDING" /><el-option label="处理中" value="PROCESSING" /><el-option label="已完成" value="COMPLETED" /><el-option label="失败" value="FAILED" /></el-select></AppSearchBar>
      <el-table :data="rows" stripe v-loading="loading" row-key="taskNo">
        <el-table-column label="导入任务" min-width="180"><template #default="{ row }">{{ row.fileName || (row.importType === 'LEAD' ? '客户资源导入' : '产品导入') }}</template></el-table-column>
        <el-table-column label="导入类型" width="120"><template #default="{ row }">{{ row.importType === 'LEAD' ? '客户资源池' : '产品' }}</template></el-table-column>
        <el-table-column label="进度" width="180"><template #default="{ row }"><el-progress :percentage="Number(row.progress || 0)" :status="row.status === 'FAILED' ? 'exception' : undefined" /></template></el-table-column>
        <el-table-column label="处理结果" width="210"><template #default="{ row }">新增 {{ row.success || 0 }} / 失败 {{ row.failed || 0 }} / 共 {{ row.total || 0 }}</template></el-table-column>
        <el-table-column label="重复数据" width="90" align="center"><template #default="{ row }">{{ row.duplicate || 0 }}</template></el-table-column>
        <el-table-column label="系统已有" width="90" align="center"><template #default="{ row }">{{ row.existing || 0 }}</template></el-table-column>
        <el-table-column label="导入资源池" min-width="130"><template #default="{ row }">{{ poolName(row.poolCode) || '—' }}</template></el-table-column>
        <el-table-column label="状态" width="110"><template #default="{ row }">{{ enumLabel(statusText, row.status) }}</template></el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180" />
      </el-table>
      <AppPagination v-model:page="query.page" v-model:size="query.size" :total="total" @change="load" />
    </div>
  </div>
</template>
<script setup>
import { enumLabel } from '@/utils/enumDisplay';
import { ref, reactive, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import AppSearchBar from '@/components/AppSearchBar.vue';
import AppPagination from '@/components/AppPagination.vue';
import { pageImportTasks, previewImport, executeImport, downloadImportTemplate, exportImportFailures, listResourcePools } from '@/api/importing';
const rows = ref([]); const total = ref(0); const loading = ref(false);
/* 导入目标资源池 */
const poolCode = ref('');
const poolOptions = ref([]);
const poolMap = ref({});
async function loadPools() {
  try {
    poolOptions.value = (await listResourcePools()).data || [];
    poolMap.value = Object.fromEntries(poolOptions.value.map((pl) => [pl.poolCode, pl.poolName]));
  } catch { poolOptions.value = []; }
}
function poolName(code) { return poolMap.value[code]; }
loadPools();
const query = reactive({ page: 1, size: 20, importType: '', status: '' });
const statusText = { PENDING: '待处理', PROCESSING: '处理中', COMPLETED: '已完成', FAILED: '失败' };
const importType = ref('LEAD'); const fileRef = ref(); const lastResult = ref(null);
async function load() { loading.value = true; try { const res = await pageImportTasks(query); rows.value = res.data?.records || []; total.value = Number(res.data?.total || 0); } finally { loading.value = false; } }
function reset() { query.page = 1; query.importType = ''; query.status = ''; load(); }
async function preview(e) {
  const file = e.target.files?.[0]; e.target.value = ''; if (!file) return;
  if (!poolCode.value) return ElMessage.warning('请先选择导入资源池');
  const p = await previewImport(importType.value, file);
  if (!p.data?.rows?.length) return ElMessage.warning('文件没有可导入数据');
  lastResult.value = await executeImport(importType.value, file, poolCode.value).then((r) => r.data || {});
  ElMessage.success('导入任务已提交');
  load();
}
async function downloadTemplate() {
  const blob = await downloadImportTemplate(importType.value);
  const url = URL.createObjectURL(blob); const a = document.createElement('a');
  a.href = url; a.download = `${importType.value === 'LEAD' ? '客户资源池' : '产品'}-导入模板.xlsx`;
  a.click(); URL.revokeObjectURL(url);
}
async function downloadFailures() {
  const blob = await exportImportFailures(lastResult.value.failures || []);
  const url = URL.createObjectURL(blob); const a = document.createElement('a');
  a.href = url; a.download = '导入失败明细.xlsx'; a.click(); URL.revokeObjectURL(url);
}
onMounted(load);
</script>

<style scoped>
.header-actions {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}

.action-group {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.action-label {
  font-size: 13px;
  color: var(--loan-text-secondary);
  white-space: nowrap;
}

.action-divider {
  width: 1px;
  height: 18px;
  background: var(--loan-border);
}
</style>
