<template>
  <div class="allocation-quota-page">
    <div class="loan-page-header">
      <div>
        <h2 class="loan-page-title">认领设置</h2>
        <p class="loan-page-subtitle">
          每人每日认领上限与持有上限 · 线索公海 / 客户公海常驻各一行，可按资源池追加专属配置 · 0 表示不限 · 保存后即时生效
        </p>
      </div>
      <div class="header-actions">
        <el-input v-model="filterKeyword" placeholder="筛选：公海 / 资源池名称" clearable style="width: 220px" />
        <el-button @click="openAddDialog">新增配置</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存设置</el-button>
      </div>
    </div>

    <div class="loan-card">
      <el-table :data="filteredRows" v-loading="loading" stripe>
        <template #empty>
          <AppEmpty
            title="暂无配额配置"
            desc="保存后自动创建配置行（线索公海 / 客户公海各一行，无需执行脚本）"
          />
        </template>
        <el-table-column prop="scopeName" label="适用公海" width="160">
          <template #default="{ row }">
            <span class="loan-tag loan-tag-info">{{ row.scopeName || '未指定范围' }}</span>
            <span v-if="isPoolScope(row.scope)" class="cell-sub">资源池专属</span>
          </template>
        </el-table-column>
        <el-table-column label="每日认领上限" width="210">
          <template #default="{ row }">
            <el-input-number
              v-model="row.dailyClaimLimit"
              :min="0"
              :step="10"
              controls-position="right"
              style="width: 150px"
            />
          </template>
        </el-table-column>
        <el-table-column label="持有上限" width="210">
          <template #default="{ row }">
            <el-input-number
              v-model="row.maxHolding"
              :min="0"
              :step="10"
              controls-position="right"
              style="width: 150px"
            />
          </template>
        </el-table-column>
        <el-table-column label="数据保护期(天)" width="180">
          <template #default="{ row }">
            <el-input-number
              v-model="row.protectDays"
              :min="0"
              :step="1"
              controls-position="right"
              style="width: 120px"
            />
          </template>
        </el-table-column>
        <el-table-column label="每日认领冷却(条)" width="180">
          <template #default="{ row }">
            <el-input-number
              v-model="row.dailyCooldownLimit"
              :min="0"
              :step="1"
              controls-position="right"
              style="width: 120px"
            />
          </template>
        </el-table-column>
        <el-table-column label="退回/回纳目标" width="180">
          <template #default="{ row }">
            <el-select v-model="row.returnTarget" style="width: 140px">
              <el-option label="公司公海" value="PUBLIC" />
              <el-option label="原部门团队池" value="ORIGINAL" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="说明" min-width="220">
          <template #default="{ row }">
            <el-input v-model="row.remark" placeholder="选填，如 新人保护期放宽" maxlength="100" show-word-limit />
          </template>
        </el-table-column>
        <el-table-column label="最近更新" width="200">
          <template #default="{ row }">
            <span v-if="row.updatedAt">{{ row.updatedAt }}<span class="cell-sub"> · {{ row.updatedBy || '—' }}</span></span>
            <span v-else class="cell-sub">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button v-if="isPoolScope(row.scope)" link type="danger" @click="onDeleteRow(row)">删除</el-button>
            <span v-else class="cell-sub">常驻</span>
          </template>
        </el-table-column>
      </el-table>

      <!-- 新增配置弹窗：公海类型常驻不可重复新增，仅资源池维度可追加；数值字段创建即填（D83 #7） -->
      <AppDialog v-model:visible="addDialogVisible" title="新增认领配置" width="520px" :loading="adding" @confirm="onAddConfirm">
        <el-form label-width="110px" label-position="right">
          <el-form-item label="适用范围">
            <el-radio-group v-model="addForm.scopeType">
              <el-radio value="POOL">指定资源池</el-radio>
            </el-radio-group>
            <p class="add-hint">线索公海 / 客户公海为常驻行，无需新增；此处仅用于给某个资源池单独配置认领上限。</p>
          </el-form-item>
          <el-form-item label="资源池" required>
            <el-select v-model="addForm.poolCode" placeholder="选择资源池" filterable style="width: 100%">
              <el-option v-for="p in poolOptions" :key="p.poolCode" :label="p.poolName" :value="p.poolCode" :disabled="usedPoolCodes.has(p.poolCode)" />
            </el-select>
          </el-form-item>
          <el-form-item label="每日认领上限">
            <el-input-number v-model="addForm.dailyClaimLimit" :min="0" :step="10" controls-position="right" style="width: 160px" />
          </el-form-item>
          <el-form-item label="持有上限">
            <el-input-number v-model="addForm.maxHolding" :min="0" :step="10" controls-position="right" style="width: 160px" />
          </el-form-item>
          <el-form-item label="数据保护期(天)">
            <el-input-number v-model="addForm.protectDays" :min="0" :step="1" controls-position="right" style="width: 160px" />
          </el-form-item>
          <el-form-item label="每日认领冷却(条)">
            <el-input-number v-model="addForm.dailyCooldownLimit" :min="0" :step="1" controls-position="right" style="width: 160px" />
          </el-form-item>
          <el-form-item label="退回/回纳目标">
            <el-select v-model="addForm.returnTarget" style="width: 160px">
              <el-option label="公司公海" value="PUBLIC" />
              <el-option label="原部门团队池" value="ORIGINAL" />
            </el-select>
          </el-form-item>
          <el-form-item label="说明">
            <el-input v-model="addForm.remark" placeholder="选填" maxlength="100" show-word-limit />
          </el-form-item>
        </el-form>
      </AppDialog>

      <ul class="quota-tip">
        <li><b>每日认领上限</b>：按「员工 × 公海类型 × 自然日」计数，跨天自动清零（口径对齐 tse <code>daily_assign_limit_per_user</code>，按人计数、不分资源池）；超出后当天不可再认领（提示「已达到今日认领上限」）。</li>
        <li><b>持有上限</b>：该员工名下归属客户 / 线索的总数上限（口径对齐 tse <code>max_new_customers_per_user</code>）；<b>线索侧无持有上限概念，保持 0（不限）</b>。</li>
        <li><b>数据保护期(天)</b>：客户 / 线索进入公海后 N 天内所有人不可认领（口径对齐 tse <code>claim_protect_days</code>）；0 表示不启用。</li>
        <li><b>每日认领冷却(条)</b>：与「每日认领上限」同时生效、取较小值；用于保护期结束后进一步收紧每日认领节奏；0 表示不启用（仅按每日认领上限）。</li>
        <li><b>退回/回纳目标</b>：客户被系统自动回收时的去向——公司公海（默认，现状）或归属人原部门团队池；手动回收 / 主动释放不受此配置影响。</li>
        <li><b>不计数的情况</b>：管理员 / 主管的手动指派与转移审批通过，不占用被分配人的每日认领额度。</li>
      </ul>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { listAllocationQuota, saveAllocationQuota } from '@/api/allocationQuota';
import { listResourcePools } from '@/api/importing';
import AppDialog from '@/components/AppDialog.vue';
import { appConfirm } from '@/utils/confirm';

const rows = ref([]);
/** 加载时的原值快照，用于保存前列出差异。 */
const origin = ref([]);
const loading = ref(false);
const saving = ref(false);
const filterKeyword = ref('');
/** 本次待删除的资源池维度 scope（保存时一并提交）。 */
const pendingDeletes = ref([]);

const isPoolScope = (scope) => typeof scope === 'string' && scope.startsWith('POOL:');

/** 资源池下拉（新增用）。 */
const poolOptions = ref([]);
const addDialogVisible = ref(false);
const adding = ref(false);
const addForm = ref({ scopeType: 'POOL', poolCode: '', dailyClaimLimit: 0, maxHolding: 0, protectDays: 0, dailyCooldownLimit: 0, returnTarget: 'PUBLIC', remark: '' });

/** 已配置的资源池 scope（禁用重复新增）。 */
const usedPoolCodes = computed(() => {
  const set = new Set();
  rows.value.forEach((r) => { if (isPoolScope(r.scope)) set.add(r.scope.slice(5)); });
  return set;
});

/** 关键词过滤后的展示行。 */
const filteredRows = computed(() => {
  const kw = filterKeyword.value.trim().toLowerCase();
  if (!kw) return rows.value;
  return rows.value.filter((r) => (r.scopeName || '').toLowerCase().includes(kw) || (r.scope || '').toLowerCase().includes(kw));
});

async function loadPoolOptions() {
  try { poolOptions.value = (await listResourcePools()).data || []; } catch { poolOptions.value = []; }
}

function openAddDialog() {
  addForm.value = { scopeType: 'POOL', poolCode: '', dailyClaimLimit: 0, maxHolding: 0, protectDays: 0, dailyCooldownLimit: 0, returnTarget: 'PUBLIC', remark: '' };
  addDialogVisible.value = true;
}

/** 新增资源池维度配置行（仅本地追加，随保存提交）。 */
function onAddConfirm() {
  const f = addForm.value;
  if (!f.poolCode) return ElMessage.warning('请选择资源池');
  const pool = poolOptions.value.find((p) => p.poolCode === f.poolCode);
  const scope = 'POOL:' + f.poolCode;
  if (rows.value.some((r) => r.scope === scope)) return ElMessage.warning('该资源池已配置');
  rows.value.push({
    scope,
    scopeName: pool?.poolName || f.poolCode,
    dailyClaimLimit: Number(f.dailyClaimLimit) || 0,
    maxHolding: Number(f.maxHolding) || 0,
    protectDays: Number(f.protectDays) || 0,
    dailyCooldownLimit: Number(f.dailyCooldownLimit) || 0,
    returnTarget: f.returnTarget || 'PUBLIC',
    remark: f.remark || '',
    updatedBy: null,
    updatedAt: null,
  });
  addDialogVisible.value = false;
  ElMessage.success('已添加，保存后生效');
}

/** 删除资源池维度行（本地标记，随保存提交）。 */
async function onDeleteRow(row) {
  try {
    await ElMessageBox.confirm(`确认删除「${row.scopeName}」的专属认领配置？删除后该资源池将回落使用公海类型默认配额。`, '删除配置', { type: 'warning' });
  } catch (e) { return; }
  rows.value = rows.value.filter((r) => r.scope !== row.scope);
  pendingDeletes.value.push(row.scope);
  ElMessage.success('已标记删除，保存后生效');
}

/** 拉取配额配置。 */
async function load() {
  loading.value = true;
  try {
    const data = await listAllocationQuota();
    rows.value = (Array.isArray(data) ? data : []).map((item) => ({
      ...item,
      dailyClaimLimit: item.dailyClaimLimit == null ? 0 : Number(item.dailyClaimLimit),
      maxHolding: item.maxHolding == null ? 0 : Number(item.maxHolding),
      protectDays: item.protectDays == null ? 0 : Number(item.protectDays),
      dailyCooldownLimit: item.dailyCooldownLimit == null ? 0 : Number(item.dailyCooldownLimit),
      returnTarget: item.returnTarget || 'PUBLIC',
    }));
    origin.value = snapshot();
    pendingDeletes.value = [];
  } catch (e) {
    rows.value = [];
    origin.value = [];
  } finally {
    loading.value = false;
  }
}

/** 当前值的快照（用于变更对比）。 */
function snapshot() {
  return rows.value.map((row) => ({
    scope: row.scope,
    dailyClaimLimit: Number(row.dailyClaimLimit),
    maxHolding: Number(row.maxHolding),
    protectDays: Number(row.protectDays),
    dailyCooldownLimit: Number(row.dailyCooldownLimit),
    returnTarget: row.returnTarget || 'PUBLIC',
    remark: row.remark || '',
  }));
}

/** 计算本次改动的可读差异列表（无改动返回空数组）。 */
function diffOf() {
  const base = new Map(origin.value.map((item) => [item.scope, item]));
  const changes = [];
  rows.value.forEach((row) => {
    const before = base.get(row.scope);
    if (!before) return;
    const name = row.scopeName || ({ SELF: '本人', DEPARTMENT: '本团队', COMPANY: '全公司' }[row.scope] || '未指定范围');
    if (Number(before.dailyClaimLimit) !== Number(row.dailyClaimLimit)) {
      changes.push(`${name}：每日认领上限 ${before.dailyClaimLimit} → ${row.dailyClaimLimit}`);
    }
    if (Number(before.maxHolding) !== Number(row.maxHolding)) {
      changes.push(`${name}：持有上限 ${before.maxHolding} → ${row.maxHolding}`);
    }
    if (Number(before.protectDays) !== Number(row.protectDays)) {
      changes.push(`${name}：数据保护期 ${before.protectDays} → ${row.protectDays} 天`);
    }
    if (Number(before.dailyCooldownLimit) !== Number(row.dailyCooldownLimit)) {
      changes.push(`${name}：每日认领冷却 ${before.dailyCooldownLimit} → ${row.dailyCooldownLimit} 条`);
    }
    if ((before.returnTarget || 'PUBLIC') !== (row.returnTarget || 'PUBLIC')) {
      const label = { PUBLIC: '公司公海', ORIGINAL: '原部门团队池' };
      changes.push(`${name}：回纳目标 ${label[before.returnTarget] || before.returnTarget} → ${label[row.returnTarget] || row.returnTarget}`);
    }
    if ((before.remark || '') !== (row.remark || '')) {
      changes.push(`${name}：说明 ${before.remark || '—'} → ${row.remark || '—'}`);
    }
  });
  return changes;
}

/**
 * 保存（提交全部行，后端按 scope 幂等 upsert）。
 *
 * <p>保存前先弹确认框并列出「原值 → 新值」，避免误改导致全公司认领口径被瞬间收紧
 * 或放开（配额保存后即时生效、无二次机会）。</p>
 */
async function onSave() {
  const changes = diffOf();
  const hasNew = rows.value.some((r) => !origin.value.some((o) => o.scope === r.scope));
  const hasDelete = pendingDeletes.value.length > 0;
  if (!changes.length && !hasNew && !hasDelete) {
    ElMessage.info('没有需要保存的修改');
    return;
  }
  const summary = [];
  if (changes.length) summary.push(`修改 ${changes.length} 项`);
  if (hasNew) summary.push('新增配置行');
  if (hasDelete) summary.push(`删除 ${pendingDeletes.value.length} 行`);
  try {
    await appConfirm(
      `保存后立即生效，0 表示不限。本次将${summary.join('、')}。<br>${changes.length ? '· ' + changes.join('<br>· ') : ''}`,
      '确认保存认领设置？',
      { confirmButtonText: '确认保存', dangerouslyUseHTMLString: true },
    );
  } catch (e) {
    return; // 用户取消
  }
  saving.value = true;
  try {
    await saveAllocationQuota(
      rows.value.map((row) => ({
        scope: row.scope,
        dailyClaimLimit: row.dailyClaimLimit,
        maxHolding: row.maxHolding,
        protectDays: row.protectDays,
        dailyCooldownLimit: row.dailyCooldownLimit,
        returnTarget: row.returnTarget || 'PUBLIC',
        remark: row.remark,
      })),
      pendingDeletes.value,
    );
    ElMessage.success('已保存，配额即时生效');
    await load();
  } finally {
    saving.value = false;
  }
}

onMounted(() => { load(); loadPoolOptions(); });
</script>

<style scoped>
.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
.add-hint {
  margin: 4px 0 0;
  color: var(--loan-text-muted);
  font-size: 12px;
  line-height: 1.5;
}
.quota-tip {
  margin: 18px 0 0;
  padding-left: 20px;
  color: var(--loan-text-secondary);
  font-size: 13px;
  line-height: 1.9;
}
.quota-tip b {
  color: var(--loan-text);
}
</style>
