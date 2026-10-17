<template>
  <div class="resource-pool-config page-shell">
    <div class="loan-page-header"><div><h2 class="loan-page-title">资源池回收规则</h2><p class="loan-page-subtitle">按公司、团队、员工控制自动回收；员工离职可在组织中心执行数据交接。</p></div></div>
    <div class="loan-card"><el-table :data="rows" v-loading="loading" stripe>
      <el-table-column prop="poolName" label="资源池" min-width="180" />
      <el-table-column label="控制范围" width="120"><template #default="{ row }">{{ enumLabel(scopeNames, row.scopeType) }}</template></el-table-column>
      <el-table-column prop="scopeKey" label="范围对象" width="160"><template #default="{ row }">{{ row.scopeKey || '全公司' }}</template></el-table-column>
      <el-table-column label="自动回收" width="110"><template #default="{ row }"><el-switch v-model="row.recycleEnabled" :active-value="1" :inactive-value="0" /></template></el-table-column>
      <el-table-column label="默认回收天数" width="150"><template #default="{ row }"><el-input-number v-model="row.recycleDays" :min="1" :disabled="row.recycleEnabled !== 1" /></template></el-table-column>
      <el-table-column label="提前预警天数" width="150"><template #default="{ row }"><el-input-number v-model="row.warnDays" :min="0" :disabled="row.recycleEnabled !== 1" /></template></el-table-column>
      <el-table-column label="冷却天数" width="130"><template #default="{ row }"><el-input-number v-model="row.cooldownDays" :min="0" /></template></el-table-column>
      <el-table-column label="操作" width="100"><template #default="{ row }"><el-button link type="primary" @click="save(row)">保存</el-button></template></el-table-column>
    </el-table></div>
  </div>
</template>
<script setup>
import { enumLabel } from '@/utils/enumDisplay';
import { ref, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { listResourcePoolConfigs, saveResourcePoolConfig } from '@/api/resourcePoolConfig';
const rows = ref([]); const loading = ref(false);
const scopeNames = { COMPANY: '公司', TEAM: '团队', STAFF: '员工' };
async function load() { loading.value = true; try { const res = await listResourcePoolConfigs(); rows.value = res?.data || []; } finally { loading.value = false; } }
async function save(row) { await saveResourcePoolConfig(row); ElMessage.success('资源池规则已保存'); }
onMounted(load);
</script>
