<template>
  <div class="client-import-export page-shell">
    <div class="loan-page-header"><div><h2 class="loan-page-title">客户导入与导出</h2><p class="loan-page-subtitle">资源池客户批量导入、模板下载和失败明细导出统一入口</p></div></div>
    <div class="loan-card import-card">
      <div class="import-actions"><el-radio-group v-model="type"><el-radio-button value="LEAD">客户资源池</el-radio-button><el-radio-button value="PRODUCT">产品</el-radio-button></el-radio-group><el-button @click="downloadTemplate">下载模板</el-button><input ref="fileRef" type="file" accept=".xlsx" hidden @change="preview" /><el-button type="primary" @click="fileRef?.click()">选择 Excel 导入</el-button></div>
      <el-alert v-if="result" :title="`导入完成：成功 ${result.success || 0} 条，失败 ${result.failed || 0} 条`" :type="result.failed ? 'warning' : 'success'" show-icon />
      <el-button v-if="result?.failures?.length" link type="primary" @click="downloadFailures">导出失败明细</el-button>
      <p class="import-tip">导入任务和处理进度请在“客户中心 → 客户导入进度”查看。</p>
    </div>
  </div>
</template>
<script setup>
import { ref } from 'vue';
import { ElMessage } from 'element-plus';
import { previewImport, executeImport, downloadImportTemplate, exportImportFailures } from '@/api/importing';
const type = ref('LEAD'); const fileRef = ref(); const result = ref(null); const currentFile = ref(null);
async function preview(e) { const file = e.target.files?.[0]; e.target.value = ''; if (!file) return; currentFile.value = file; const p = await previewImport(type.value, file); if (!p.data?.rows?.length) return ElMessage.warning('文件没有可导入数据'); result.value = await executeImport(type.value, file).then(r => r.data || {}); ElMessage.success('导入任务已提交'); }
async function downloadTemplate() { const blob = await downloadImportTemplate(type.value); const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = `${type.value === 'LEAD' ? '客户资源池' : '产品'}-导入模板.xlsx`; a.click(); URL.revokeObjectURL(url); }
async function downloadFailures() { const blob = await exportImportFailures(result.value.failures || []); const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = '导入失败明细.xlsx'; a.click(); URL.revokeObjectURL(url); }
</script>
