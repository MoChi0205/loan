<template>
  <div class="resource-pool-config page-shell">
    <div class="loan-page-header"><div><h2 class="loan-page-title">资源池回收规则</h2><p class="loan-page-subtitle">回收规则内嵌在每个资源池（对齐 tse）：一个池一条规则；仅部门经理、老板、超级管理员可创建与维护。</p></div></div>

    <div class="loan-card pool-manage-card">
      <div class="dept-head" style="margin-bottom: 12px">
        <span class="panel-title">资源池管理</span>
        <span class="pool-manage-note">客户导入时可选择目标资源池；停用的池不再出现在导入下拉。展开行可按跟进状态配置差异化回收天数。</span>
        <el-button v-if="canManage" type="primary" style="margin-left: auto" @click="createDialogVisible = true">创建资源池</el-button>
      </div>
      <div v-if="!canManage" class="pool-manage-note" style="margin-bottom: 8px">当前角色仅可查看：资源池的创建与规则维护限部门经理、老板、超级管理员。</div>

      <el-table :data="pools" v-loading="poolsLoading" stripe size="small" style="margin-top: 12px" row-key="poolCode">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="status-days-editor">
              <span class="status-days-title">按跟进状态的无活跃回收天数（0 = 当天回收；留空 = 不限制，该状态不参与超期回收；成交 / 已签约 / 无需求为终态不可回收）：</span>
              <div class="status-days-fields">
                <label v-for="st in STATUS_KEYS" :key="st.key" class="status-days-item">
                  <span>{{ st.label }}</span>
                  <el-input-number v-model="row.statusDays[st.key]" :min="0" :controls="false" placeholder="不限制" :disabled="row.recycleEnabled !== 1 || !canManage" style="width: 90px" />
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
            <el-tooltip content="线索回收与报告展示口径使用；客户的回收天数以展开行按状态配置为准" placement="top">
              <el-input-number v-model="row.recycleDays" :min="1" :controls="false" :disabled="row.recycleEnabled !== 1 || !canManage" style="width: 100px" />
            </el-tooltip>
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

    <el-dialog v-model="createDialogVisible" title="创建资源池" width="480px" :close-on-click-modal="false">
      <div class="pool-create-form">
        <label class="pool-form-item"><span class="pool-form-label">资源池名称<i class="req">*</i></span>
          <el-input v-model="poolForm.poolName" placeholder="必填" maxlength="30" style="flex: 1" />
        </label>
        <label class="pool-form-item"><span class="pool-form-label">池类型<i class="req">*</i></span>
          <el-select v-model="poolForm.poolType" style="flex: 1" @change="poolForm.deptCode = ''">
            <el-option label="公司池" value="COMPANY" />
            <el-option label="团队池" value="TEAM" />
          </el-select>
        </label>
        <label v-if="poolForm.poolType === 'TEAM'" class="pool-form-item"><span class="pool-form-label">关联部门<i class="req">*</i></span>
          <el-select v-model="poolForm.deptCode" placeholder="必选" clearable filterable style="flex: 1">
            <el-option v-for="d in deptOptions" :key="d.deptCode" :label="d.deptName" :value="d.deptCode" />
          </el-select>
        </label>
        <label class="pool-form-item"><span class="pool-form-label">池说明</span>
          <el-input v-model="poolForm.description" placeholder="可选" maxlength="60" style="flex: 1" />
        </label>
        <label class="pool-form-item"><span class="pool-form-label">自动回收</span>
          <span style="flex: 1; display: flex; align-items: center; gap: 8px">
            <el-switch v-model="poolForm.recycleEnabled" :active-value="1" :inactive-value="0" />
            <template v-if="poolForm.recycleEnabled === 1">
              <el-input-number v-model="poolForm.recycleDays" :min="1" :controls="false" style="width: 90px" />
              <span class="pool-form-label">天（作为各跟进状态初始预填值）</span>
            </template>
          </span>
        </label>
      </div>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creatingPool" @click="onCreatePool">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { createResourcePool, toggleResourcePool, savePoolRecycle, fetchPoolRules } from '@/api/importing';
import { departmentTree } from '@/api/org';

/**
 * 可差异化配置回收天数的跟进状态（成交/已签约/无需求为终态，禁止回收——后端 validateStatusDays 同步拦截）。
 * 语义（2026-10-19）：显式配置 0 = 当天回收；留空（null）= 不限制；与后端 parseStatusDays/recycleDays 口径一致。
 */
const STATUS_KEYS = [
  { key: 'NEW', label: '新用户' },
  { key: 'NO_ANSWER', label: '无人接听' },
  { key: 'POTENTIAL', label: '潜在客户' },
  { key: 'INTENTION', label: '意向客户' },
  { key: 'VISITED', label: '已来访' },
  { key: 'APPOINTED', label: '已预约' },
];

const pools = ref([]);
const poolsLoading = ref(false);
/** 权限单一真源：由后端 /rules 接口返回，前端不再本地判角色（2026-10-19）。 */
const canManage = ref(false);
const creatingPool = ref(false);
const createDialogVisible = ref(false);
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

/** statusDaysJson → 行内可编辑对象：未配置 = null（留空不限制）；0 = 当天回收；未知键忽略。 */
function parseStatusDays(row) {
  const days = {};
  try {
    const raw = row.statusDaysJson ? JSON.parse(row.statusDaysJson) : {};
    STATUS_KEYS.forEach(({ key }) => {
      const v = raw[key];
      const n = v == null ? NaN : Number(v);
      days[key] = Number.isFinite(n) && n >= 0 ? n : null;
    });
  } catch { STATUS_KEYS.forEach(({ key }) => { days[key] = null; }); }
  return days;
}

async function loadPools() {
  poolsLoading.value = true;
  try {
    const body = (await fetchPoolRules()).data || {};
    canManage.value = !!body.canManage;
    pools.value = (body.pools || []).map((row) => ({ ...row, statusDays: parseStatusDays(row) }));
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
    createDialogVisible.value = false;
    poolForm.poolName = ''; poolForm.description = ''; poolForm.deptCode = '';
    loadPools();
  } catch (e) { /* 拦截器已提示 */ } finally { creatingPool.value = false; }
}

/**
 * 保存单个池的回收规则（一个池一条规则）。
 * 显式配置项（含 0）均下发；留空（null）= 不限制，不写入 JSON。
 */
async function saveRecycle(row) {
  const statusEntries = Object.entries(row.statusDays || {}).filter(([, v]) => v != null && Number(v) >= 0);
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
.resource-pool-config { display: flex; flex-direction: column; gap: 16px; min-width: 0; }
.dept-head { display: flex; align-items: center; gap: 12px; }
.panel-title { color: var(--loan-text); font-size: 15px; font-weight: 500; }
.pool-manage-note { color: var(--loan-text-muted); font-size: 12px; }
.mono { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 12px; }
.status-days-editor { display: flex; flex-direction: column; gap: 10px; padding: 4px 12px 8px; }
.status-days-title { color: var(--loan-text-secondary); font-size: 12px; }
.status-days-fields { display: flex; flex-wrap: wrap; gap: 10px 18px; }
.status-days-item { display: flex; align-items: center; gap: 6px; color: var(--loan-text); font-size: 12px; }
.pool-create-form { display: flex; flex-direction: column; gap: 12px; }
.pool-form-item { display: flex; align-items: center; gap: 10px; }
.pool-form-label { flex: 0 0 84px; color: var(--loan-text-secondary); font-size: 13px; }
.pool-form-label .req { color: var(--el-color-danger); font-style: normal; margin-left: 2px; }
</style>
