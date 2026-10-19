<template>
  <div class="resource-pool-config page-shell">
    <div class="loan-page-header"><div><h2 class="loan-page-title">资源池回收规则</h2><p class="loan-page-subtitle">回收规则内嵌在每个资源池（对齐 tse）：一个池一条规则；仅部门经理、老板、超级管理员可创建与维护。</p></div></div>

    <div class="loan-card pool-manage-card">
      <div class="dept-head" style="margin-bottom: 12px">
        <span class="panel-title">资源池管理</span>
        <span class="pool-manage-note">客户导入时可选择目标资源池；停用的池不再出现在导入下拉。展开行可按跟进状态配置差异化回收天数。</span>
      </div>
      <div v-if="canManage" class="pool-create-row">
        <el-input v-model="poolForm.poolName" placeholder="资源池名称（必填）" style="width: 200px" maxlength="30" />
        <el-select v-model="poolForm.poolType" style="width: 130px" @change="poolForm.deptCode = ''">
          <el-option label="公司池" value="COMPANY" />
          <el-option label="团队池" value="TEAM" />
        </el-select>
        <el-select v-if="poolForm.poolType === 'TEAM'" v-model="poolForm.deptCode" placeholder="关联部门（必选）" clearable filterable style="width: 180px">
          <el-option v-for="d in deptOptions" :key="d.deptCode" :label="d.deptName" :value="d.deptCode" />
        </el-select>
        <el-input v-model="poolForm.description" placeholder="池说明（可选）" style="width: 200px" maxlength="60" />
        <span class="pool-create-field">自动回收<el-switch v-model="poolForm.recycleEnabled" :active-value="1" :inactive-value="0" /></span>
        <el-input-number v-model="poolForm.recycleDays" :min="1" :controls="false" :disabled="poolForm.recycleEnabled !== 1" style="width: 90px" />
        <span class="pool-create-field">天</span>
        <el-button type="primary" :loading="creatingPool" @click="onCreatePool">创建资源池</el-button>
      </div>
      <div v-else class="pool-manage-note" style="margin-bottom: 8px">当前角色仅可查看：资源池的创建与规则维护限部门经理、老板、超级管理员。</div>

      <el-table :data="pools" v-loading="poolsLoading" stripe size="small" style="margin-top: 12px" row-key="poolCode">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="status-days-editor">
              <span class="status-days-title">按跟进状态的无活跃回收天数（留空或 0 = 沿用默认回收天数；成交 / 已签约 / 无需求不可回收）：</span>
              <div class="status-days-fields">
                <label v-for="st in STATUS_KEYS" :key="st.key" class="status-days-item">
                  <span>{{ st.label }}</span>
                  <el-input-number v-model="row.statusDays[st.key]" :min="0" :controls="false" :disabled="row.recycleEnabled !== 1" style="width: 90px" />
                </label>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="资源池" min-width="150"><template #default="{ row }">{{ row.poolName }}</template></el-table-column>
        <el-table-column label="池编码" min-width="140"><template #default="{ row }"><span class="mono">{{ row.poolCode }}</span></template></el-table-column>
        <el-table-column label="类型" width="90"><template #default="{ row }">{{ row.poolType === 'TEAM' ? '团队池' : '公司池' }}</template></el-table-column>
        <el-table-column label="关联部门" min-width="130"><template #default="{ row }">{{ row.deptName || '—' }}</template></el-table-column>
        <el-table-column label="自动回收" width="100"><template #default="{ row }"><el-switch v-model="row.recycleEnabled" :active-value="1" :inactive-value="0" :disabled="!canManage" /></template></el-table-column>
        <el-table-column label="默认回收天数" width="130">
          <template #default="{ row }">
            <el-input-number v-model="row.recycleDays" :min="1" :controls="false" :disabled="row.recycleEnabled !== 1 || !canManage" style="width: 100px" />
          </template>
        </el-table-column>
        <el-table-column label="提前预警天数" width="130">
          <template #default="{ row }">
            <el-input-number v-model="row.warnDays" :min="0" :controls="false" :disabled="row.recycleEnabled !== 1 || !canManage" style="width: 100px" />
          </template>
        </el-table-column>
        <el-table-column label="冷却天数" width="110">
          <template #default="{ row }">
            <el-input-number v-model="row.cooldownDays" :min="0" :controls="false" :disabled="!canManage" style="width: 90px" />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }"><span :class="row.status === 'ACTIVE' ? 'loan-tag loan-tag-success' : 'loan-tag loan-tag-muted'">{{ row.status === 'ACTIVE' ? '启用' : '停用' }}</span></template>
        </el-table-column>
        <el-table-column v-if="canManage" label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="saveRecycle(row)">保存规则</el-button>
            <el-button link :type="row.status === 'ACTIVE' ? 'danger' : 'primary'" size="small" @click="onTogglePool(row)">{{ row.status === 'ACTIVE' ? '停用' : '启用' }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { listResourcePools, createResourcePool, toggleResourcePool, savePoolRecycle } from '@/api/importing';
import { departmentTree } from '@/api/org';
import { useUserStore } from '@/store/user';

/** 可创建/维护资源池的角色（与后端 ResourcePoolController.requirePoolManager 一致） */
const POOL_MANAGER_ROLES = ['DEPT_MANAGER', 'BOSS', 'SUPER_ADMIN', 'SUPER'];
/** 可差异化配置回收天数的跟进状态（成交/已签约/无需求为终态，禁止回收） */
const STATUS_KEYS = [
  { key: 'NEW', label: '新用户' },
  { key: 'NO_ANSWER', label: '无人接听' },
  { key: 'POTENTIAL', label: '潜在客户' },
  { key: 'INTENTION', label: '意向客户' },
  { key: 'VISITED', label: '已来访' },
  { key: 'APPOINTED', label: '已预约' },
];

const userStore = useUserStore();
const canManage = computed(() => POOL_MANAGER_ROLES.includes((userStore.roleCode || '').toUpperCase()));

const pools = ref([]);
const poolsLoading = ref(false);
const creatingPool = ref(false);
const poolForm = reactive({ poolName: '', poolType: 'COMPANY', description: '', deptCode: '', recycleEnabled: 1, recycleDays: 30 });
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

/** statusDaysJson → 行内可编辑对象（未知键忽略）。 */
function parseStatusDays(row) {
  const days = {};
  try {
    const raw = row.statusDaysJson ? JSON.parse(row.statusDaysJson) : {};
    STATUS_KEYS.forEach(({ key }) => { days[key] = Number(raw[key]) > 0 ? Number(raw[key]) : 0; });
  } catch { STATUS_KEYS.forEach(({ key }) => { days[key] = 0; }); }
  return days;
}

async function loadPools() {
  poolsLoading.value = true;
  try {
    const list = (await listResourcePools()).data || [];
    pools.value = list.map((row) => ({ ...row, statusDays: parseStatusDays(row) }));
  } catch { pools.value = []; } finally { poolsLoading.value = false; }
}

async function onCreatePool() {
  if (!poolForm.poolName.trim()) return ElMessage.warning('请填写资源池名称');
  if (poolForm.poolType === 'TEAM' && !poolForm.deptCode) return ElMessage.warning('团队池必须选择关联部门');
  const dept = deptOptions.value.find((d) => d.deptCode === poolForm.deptCode);
  creatingPool.value = true;
  try {
    await createResourcePool({
      ...poolForm,
      poolName: poolForm.poolName.trim(),
      deptName: dept?.deptName || '',
    });
    ElMessage.success('资源池已创建');
    poolForm.poolName = ''; poolForm.description = ''; poolForm.deptCode = '';
    loadPools();
  } catch (e) { /* 拦截器已提示 */ } finally { creatingPool.value = false; }
}

/** 保存单个池的回收规则（一个池一条规则；statusDays 全 0 时不下发按状态 JSON）。 */
async function saveRecycle(row) {
  const statusEntries = Object.entries(row.statusDays || {}).filter(([, v]) => Number(v) > 0);
  try {
    await savePoolRecycle(row.poolCode, {
      recycleEnabled: row.recycleEnabled,
      recycleDays: row.recycleDays,
      warnDays: row.warnDays,
      cooldownDays: row.cooldownDays,
      statusDaysJson: statusEntries.length ? JSON.stringify(Object.fromEntries(statusEntries)) : '',
    });
    ElMessage.success(`【${row.poolName}】回收规则已保存`);
    loadPools();
  } catch (e) { /* 拦截器已提示 */ }
}

async function onTogglePool(row) {
  try {
    await toggleResourcePool(row.poolCode);
    ElMessage.success(row.status === 'ACTIVE' ? '已停用' : '已启用');
    loadPools();
  } catch (e) { /* 拦截器已提示 */ }
}

loadDeptOptions();
loadPools();
onMounted(() => { /* pools 在 setup 即加载 */ });
</script>

<style scoped>
.pool-create-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.pool-create-field { font-size: 13px; color: var(--loan-text-secondary); white-space: nowrap; }
.pool-manage-note { font-size: 12px; color: var(--loan-text-secondary); }
.status-days-editor { padding: 4px 12px 8px 48px; }
.status-days-title { display: block; font-size: 12px; color: var(--loan-text-secondary); margin-bottom: 8px; }
.status-days-fields { display: flex; flex-wrap: wrap; gap: 12px 20px; }
.status-days-item { display: inline-flex; align-items: center; gap: 6px; font-size: 13px; color: var(--loan-text); }
</style>
