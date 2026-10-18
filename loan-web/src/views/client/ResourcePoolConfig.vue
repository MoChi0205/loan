<template>
  <div class="resource-pool-config page-shell">
    <div class="loan-page-header"><div><h2 class="loan-page-title">资源池回收规则</h2><p class="loan-page-subtitle">按公司、团队、员工控制自动回收；员工离职可在组织中心执行数据交接。</p></div></div>

    <div class="loan-card pool-manage-card">
      <div class="dept-head" style="margin-bottom: 12px">
        <span class="panel-title">资源池管理</span>
        <span class="pool-manage-note">客户导入时可选择目标资源池；停用的池不再出现在导入下拉。</span>
      </div>
      <div class="pool-create-row">
        <el-input v-model="poolForm.poolName" placeholder="资源池名称（必填）" style="width: 220px" maxlength="30" />
        <el-select v-model="poolForm.poolType" style="width: 140px" @change="poolForm.deptCode = ''">
          <el-option label="公司池" value="COMPANY" />
          <el-option label="团队池" value="TEAM" />
        </el-select>
        <el-select v-if="poolForm.poolType === 'TEAM'" v-model="poolForm.deptCode" placeholder="关联部门（必选）" clearable filterable style="width: 180px">
          <el-option v-for="d in deptOptions" :key="d.deptCode" :label="d.deptName" :value="d.deptCode" />
        </el-select>
        <el-input v-model="poolForm.description" placeholder="池说明（可选）" style="width: 240px" maxlength="60" />
        <el-button type="primary" :loading="creatingPool" @click="onCreatePool">创建资源池</el-button>
      </div>
      <el-table :data="pools" v-loading="poolsLoading" stripe size="small" style="margin-top: 12px">
        <el-table-column label="资源池" min-width="160"><template #default="{ row }">{{ row.poolName }}</template></el-table-column>
        <el-table-column label="池编码" min-width="150"><template #default="{ row }"><span class="mono">{{ row.poolCode }}</span></template></el-table-column>
        <el-table-column label="类型" width="90"><template #default="{ row }">{{ row.poolType === 'TEAM' ? '团队池' : '公司池' }}</template></el-table-column>
        <el-table-column label="关联部门" min-width="130"><template #default="{ row }">{{ row.deptName || '—' }}</template></el-table-column>
        <el-table-column label="说明" min-width="160" show-overflow-tooltip><template #default="{ row }">{{ row.description || '—' }}</template></el-table-column>
        <el-table-column label="状态" width="90"><template #default="{ row }"><span :class="row.status === 'ACTIVE' ? 'loan-tag loan-tag-success' : 'loan-tag loan-tag-muted'">{{ row.status === 'ACTIVE' ? '启用' : '停用' }}</span></template></el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link :type="row.status === 'ACTIVE' ? 'danger' : 'primary'" size="small" @click="onTogglePool(row)">{{ row.status === 'ACTIVE' ? '停用' : '启用' }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="loan-card"><el-table :data="rows" v-loading="loading" stripe>
      <el-table-column prop="poolName" label="资源池" min-width="180" />
      <el-table-column label="控制范围" width="120"><template #default="{ row }">{{ enumLabel(scopeNames, row.scopeType) }}</template></el-table-column>
      <el-table-column prop="scopeKey" label="范围对象" width="160"><template #default="{ row }">{{ row.scopeKey || '全公司' }}</template></el-table-column>
      <el-table-column label="自动回收" width="110"><template #default="{ row }"><el-switch v-model="row.recycleEnabled" :active-value="1" :inactive-value="0" /></template></el-table-column>
      <el-table-column label="默认回收天数" min-width="150"><template #default="{ row }"><el-input-number v-model="row.recycleDays" :min="1" :controls="false" :disabled="row.recycleEnabled !== 1" style="width: 100%" /></template></el-table-column>
      <el-table-column label="提前预警天数" min-width="150"><template #default="{ row }"><el-input-number v-model="row.warnDays" :min="0" :controls="false" :disabled="row.recycleEnabled !== 1" style="width: 100%" /></template></el-table-column>
      <el-table-column label="冷却天数" min-width="130"><template #default="{ row }"><el-input-number v-model="row.cooldownDays" :min="0" :controls="false" style="width: 100%" /></template></el-table-column>
      <el-table-column label="操作" width="100"><template #default="{ row }"><el-button link type="primary" @click="save(row)">保存</el-button></template></el-table-column>
    </el-table></div>
  </div>
</template>
<script setup>
import { enumLabel } from '@/utils/enumDisplay';
import { ref, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { listResourcePoolConfigs, saveResourcePoolConfig } from '@/api/resourcePoolConfig';
import { listResourcePools, createResourcePool, toggleResourcePool } from '@/api/importing';
import { departmentTree } from '@/api/org';
import { reactive } from 'vue';
const rows = ref([]); const loading = ref(false);
const scopeNames = { COMPANY: '公司', TEAM: '团队', STAFF: '员工' };
async function load() { loading.value = true; try { const res = await listResourcePoolConfigs(); rows.value = res?.data || []; } finally { loading.value = false; } }
async function save(row) { await saveResourcePoolConfig(row); ElMessage.success('资源池规则已保存'); }
/* ---- 资源池管理 ---- */
const pools = ref([]);
const poolsLoading = ref(false);
const creatingPool = ref(false);
const poolForm = reactive({ poolName: '', poolType: 'COMPANY', description: '', deptCode: '' });
const deptOptions = ref([]);
async function loadDeptOptions() {
  try {
    const flat = [];
    const walk = (nodes, prefix) => (nodes || []).forEach((n) => {
      const label = prefix ? `${prefix} / ${n.name}` : n.name;
      flat.push({ deptCode: n.code, deptName: label });
      walk(n.children, label);
    });
    walk((await departmentTree()).data || [], '');
    deptOptions.value = flat;
  } catch { deptOptions.value = []; }
}
loadDeptOptions();
async function loadPools() {
  poolsLoading.value = true;
  try { pools.value = (await listResourcePools()).data || []; } catch { pools.value = []; } finally { poolsLoading.value = false; }
}
async function onCreatePool() {
  if (!poolForm.poolName.trim()) return ElMessage.warning('请填写资源池名称');
  if (poolForm.poolType === 'TEAM' && !poolForm.deptCode) return ElMessage.warning('团队池必须选择关联部门');
  const dept = deptOptions.value.find((d) => d.deptCode === poolForm.deptCode);
  creatingPool.value = true;
  try {
    await createResourcePool({ ...poolForm, poolName: poolForm.poolName.trim(), deptName: dept?.deptName || '' });
    ElMessage.success('资源池已创建');
    poolForm.poolName = ''; poolForm.description = ''; poolForm.deptCode = '';
    loadPools();
  } catch (e) { /* 拦截器已提示 */ } finally { creatingPool.value = false; }
}
async function onTogglePool(row) {
  try {
    await toggleResourcePool(row.poolCode);
    ElMessage.success(row.status === 'ACTIVE' ? '已停用' : '已启用');
    loadPools();
  } catch (e) { /* 拦截器已提示 */ }
}
loadPools();
onMounted(load);
</script>
<style scoped>
.pool-create-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.pool-manage-note { font-size: 12px; color: var(--loan-text-secondary); }
</style>
