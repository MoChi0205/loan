<template>
  <div class="import-task-page">
    <div class="loan-page-header"><div><h2 class="loan-page-title">客户导入进度</h2><p class="loan-page-subtitle">资源池导入完成后可在此查看处理进度、成功数与失败明细</p></div></div>
    <div class="loan-card">
      <AppSearchBar :loading="loading" @search="load" @reset="reset"><el-select v-model="query.importType" placeholder="导入类型" clearable style="width: 140px"><el-option label="客户资源池" value="LEAD" /><el-option label="产品" value="PRODUCT" /></el-select></AppSearchBar>
      <el-table :data="rows" stripe v-loading="loading" row-key="taskNo">
        <el-table-column prop="taskNo" label="任务编号" min-width="180" />
        <el-table-column label="导入类型" width="120"><template #default="{ row }">{{ row.importType === 'LEAD' ? '客户资源池' : '产品' }}</template></el-table-column>
        <el-table-column label="进度" width="180"><template #default="{ row }"><el-progress :percentage="Number(row.progress || 0)" :status="row.status === 'FAILED' ? 'exception' : undefined" /></template></el-table-column>
        <el-table-column label="处理结果" width="180"><template #default="{ row }">成功 {{ row.success || 0 }} / 失败 {{ row.failed || 0 }} / 共 {{ row.total || 0 }}</template></el-table-column>
        <el-table-column label="状态" width="110"><template #default="{ row }">{{ statusText[row.status] || row.status }}</template></el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180" />
      </el-table>
      <AppPagination v-model:page="query.page" v-model:size="query.size" :total="total" @change="load" />
    </div>
  </div>
</template>
<script setup>
import { ref, reactive, onMounted } from 'vue';
import AppSearchBar from '@/components/AppSearchBar.vue';
import AppPagination from '@/components/AppPagination.vue';
import { pageImportTasks } from '@/api/importing';
const rows = ref([]); const total = ref(0); const loading = ref(false);
const query = reactive({ page: 1, size: 20, importType: '' });
const statusText = { PENDING: '待处理', PROCESSING: '处理中', COMPLETED: '已完成', FAILED: '失败' };
async function load() { loading.value = true; try { const res = await pageImportTasks(query); rows.value = res.data?.records || []; total.value = Number(res.data?.total || 0); } finally { loading.value = false; } }
function reset() { query.page = 1; query.importType = ''; load(); }
onMounted(load);
</script>
