<template>
  <div class="allocation-quota-page">
    <div class="loan-page-header">
      <div>
        <h2 class="loan-page-title">认领设置</h2>
        <p class="loan-page-subtitle">
          各资源池的每日认领上限与持有上限 · 0 表示不限 · 保存后即时生效（无需重启或发版）
        </p>
      </div>
      <el-button type="primary" :loading="saving" @click="onSave">保存设置</el-button>
    </div>

    <div class="loan-card">
      <el-table :data="rows" v-loading="loading" stripe>
        <template #empty>
          <AppEmpty
            title="暂无配额配置"
            desc="执行 db/migrate-allocation-quota-config-2026-09-14.sql 后，线索与客户两行会自动出现"
          />
        </template>
        <el-table-column prop="scopeName" label="资源池" width="140">
          <template #default="{ row }">
            <span class="loan-tag loan-tag-info">{{ row.scopeName || row.scope }}</span>
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
        <el-table-column label="说明" min-width="200">
          <template #default="{ row }">
            <span :class="{ 'cell-sub': !row.remark }">{{ row.remark || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="最近更新" width="200">
          <template #default="{ row }">
            <span v-if="row.updatedAt">{{ row.updatedAt }}<span class="cell-sub"> · {{ row.updatedBy || '—' }}</span></span>
            <span v-else class="cell-sub">—</span>
          </template>
        </el-table-column>
      </el-table>

      <ul class="quota-tip">
        <li><b>每日认领上限</b>：按「员工 × 资源池 × 自然日」计数，跨天自动清零；超出后当天不可再认领（提示「已达到今日认领上限」）。</li>
        <li><b>持有上限</b>：该员工名下归属对象的总数上限；<b>线索侧无持有上限概念，保持 0（不限）</b>。</li>
        <li><b>不计数的情况</b>：管理员 / 主管的手动指派与转移审批通过，不占用被分配人的每日认领额度。</li>
      </ul>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { listAllocationQuota, saveAllocationQuota } from '@/api/allocationQuota';
import { appConfirm } from '@/utils/confirm';

const rows = ref([]);
/** 加载时的原值快照，用于保存前列出差异。 */
const origin = ref([]);
const loading = ref(false);
const saving = ref(false);

/** 拉取配额配置。 */
async function load() {
  loading.value = true;
  try {
    const data = await listAllocationQuota();
    rows.value = (Array.isArray(data) ? data : []).map((item) => ({
      ...item,
      dailyClaimLimit: item.dailyClaimLimit == null ? 0 : Number(item.dailyClaimLimit),
      maxHolding: item.maxHolding == null ? 0 : Number(item.maxHolding),
    }));
    origin.value = snapshot();
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
  }));
}

/** 计算本次改动的可读差异列表（无改动返回空数组）。 */
function diffOf() {
  const base = new Map(origin.value.map((item) => [item.scope, item]));
  const changes = [];
  rows.value.forEach((row) => {
    const before = base.get(row.scope);
    if (!before) return;
    const name = row.scopeName || row.scope;
    if (Number(before.dailyClaimLimit) !== Number(row.dailyClaimLimit)) {
      changes.push(`${name}：每日认领上限 ${before.dailyClaimLimit} → ${row.dailyClaimLimit}`);
    }
    if (Number(before.maxHolding) !== Number(row.maxHolding)) {
      changes.push(`${name}：持有上限 ${before.maxHolding} → ${row.maxHolding}`);
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
  if (!changes.length) {
    ElMessage.info('没有需要保存的修改');
    return;
  }
  try {
    await appConfirm(
      `保存后立即生效，0 表示不限。确认修改以下 ${changes.length} 项？<br>· ${changes.join('<br>· ')}`,
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
        remark: row.remark,
      })),
    );
    ElMessage.success('已保存，配额即时生效');
    await load();
  } finally {
    saving.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
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
