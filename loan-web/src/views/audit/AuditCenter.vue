<template>
  <div class="audit-page">
    <div class="loan-page-header">
      <div>
        <h2 class="loan-page-title">审计中心</h2>
        <p class="loan-page-subtitle">按 traceUuid / 客群 / 结果 / 异常 / 时间检索匹配全链路审计记录（仅管理端可见）</p>
      </div>
    </div>

    <div class="loan-card">
      <AppSearchBar :loading="loading" @search="onSearch" @reset="onReset">
        <el-input
          v-model="query.traceUuid"
          placeholder="traceUuid 模糊查询"
          style="width: 220px"
          clearable
        >
          <template #prefix>
            <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="1.7"><circle cx="11" cy="11" r="7"/><path d="M21 21l-4.3-4.3"/></svg>
          </template>
        </el-input>
        <el-select v-model="query.customerGroup" placeholder="客群" clearable style="width: 120px">
          <el-option v-for="o in groupOptions" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select v-model="query.totalResult" placeholder="总结果" clearable style="width: 130px">
          <el-option v-for="o in resultOptions" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select v-model="query.mismatchFlag" placeholder="异常标记" clearable style="width: 120px">
          <el-option label="有异常" :value="1" />
          <el-option label="无异常" :value="0" />
        </el-select>
        <el-date-picker
          v-model="timeRange"
          type="datetimerange"
          value-format="YYYY-MM-DD HH:mm:ss"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          style="width: 360px"
        />
        <template #append>
          <el-button @click="load">刷新</el-button>
        </template>
      </AppSearchBar>

      <el-table :data="data" v-loading="loading" stripe row-key="id" @sort-change="handleSortChange">
        <template #empty>
          <AppEmpty title="暂无执行记录" desc="匹配 / 初筛执行后将在此生成审计轨迹" />
        </template>
        <el-table-column label="traceUuid" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <code class="mono uuid-cell">{{ row.traceUuid }}</code>
          </template>
        </el-table-column>
        <el-table-column label="客群" width="100">
          <template #default="{ row }">
            <DictTag type="customerGroup" :value="row.customerGroup" />
          </template>
        </el-table-column>
        <el-table-column label="总结果" width="126">
          <template #default="{ row }">
            <DictTag type="totalResult" :value="row.totalResult" />
          </template>
        </el-table-column>
        <el-table-column label="异常" width="80" align="center">
          <template #default="{ row }">
            <span v-if="row.mismatchFlag === 1" class="loan-tag loan-tag-danger">不一致</span>
            <span v-else class="loan-tag loan-tag-muted">正常</span>
          </template>
        </el-table-column>
        <el-table-column prop="hitCount" label="命中产品" width="90" align="center" />
        <el-table-column prop="stepCount" label="执行步骤" width="90" align="center" />
        <el-table-column label="耗时" width="90" align="center">
          <template #default="{ row }">{{ row.durationMs }}ms</template>
        </el-table-column>
        <el-table-column prop="executedAt" label="执行时间" width="170" sortable>
          <template #default="{ row }">{{ formatDateTime(row.executedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <AppTableActions :actions="[{ key: 'detail', label: '详情', onClick: () => onDetail(row) }]" />
          </template>
        </el-table-column>
      </el-table>

      <AppPagination
        v-model:page="query.page"
        v-model:size="query.size"
        :total="total"
        @change="load"
      />
    </div>

    <!-- 审计详情弹窗 -->
    <AppDialog
      v-model:visible="detailVisible"
      :title="`审计详情 · ${detailTrace?.traceUuid || ''}`"
      width="760px"
    >
      <div v-if="detailTrace" class="audit-detail">
        <div class="audit-meta">
          <div class="meta-item">
            <span class="meta-label">客群</span>
            <DictTag type="customerGroup" :value="detailTrace.customerGroup" />
          </div>
          <div class="meta-item">
            <span class="meta-label">总结果</span>
            <DictTag type="totalResult" :value="detailTrace.totalResult" />
          </div>
          <div class="meta-item">
            <span class="meta-label">命中产品</span>
            <span class="meta-value">{{ detailTrace.hitCount }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">执行步骤</span>
            <span class="meta-value">{{ detailTrace.stepCount }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">耗时</span>
            <span class="meta-value">{{ detailTrace.durationMs }}ms</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">执行时间</span>
            <span class="meta-value">{{ formatDateTime(detailTrace.executedAt) }}</span>
          </div>
        </div>

        <h4 class="detail-title">规则执行明细（{{ detailRules.length }}）</h4>
        <el-timeline v-if="detailRules.length">
          <el-timeline-item
            v-for="(r, i) in detailRules"
            :key="i"
            :type="stepType(r.stepResult)"
            :hollow="true"
          >
            <div class="tl-head">
              <code class="mono">{{ r.ruleCode }}</code>
              <DictTag type="stepResult" :value="r.stepResult" />
            </div>
            <div class="tl-expr">{{ r.expression }}</div>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="无规则执行记录" :image-size="80" />
      </div>
    </AppDialog>
  </div>
</template>

<script setup>
defineOptions({ name: '_audit' });
import { ref, computed, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import DictTag from '@/components/DictTag.vue';
import AppSearchBar from '@/components/AppSearchBar.vue';
import AppPagination from '@/components/AppPagination.vue';
import AppEmpty from '@/components/AppEmpty.vue';
import AppTableActions from '@/components/AppTableActions.vue';
import AppDialog from '@/components/AppDialog.vue';
import { useTable } from '@/composables/useTable';
import { dictOptions } from '@/utils/dict';
import { formatDateTime } from '@/utils/format';
import { pageAudit, queryAudit } from '@/api/audit';

/** 客群 / 总结果下拉选项（来自后端字典，不硬编码） */
const groupOptions = dictOptions('customerGroup');
const resultOptions = dictOptions('totalResult');

/** 请求前清洗：剔除空串 / null，避免 Integer 参数（mismatchFlag）空值导致 400 */
function pageAuditSafe(q) {
  const params = {};
  Object.entries(q).forEach(([k, v]) => {
    if (v !== '' && v !== null && v !== undefined) params[k] = v;
  });
  return pageAudit(params);
}

const { loading, data, total, query, load, onSearch, onReset, handleSortChange } = useTable(pageAuditSafe, {
  traceUuid: '',
  customerGroup: '',
  totalResult: '',
  mismatchFlag: '',
  startTime: '',
  endTime: '',
});

/** 时间范围（datetimerange 双值 ↔ query.startTime/endTime 双向同步） */
const timeRange = computed({
  get: () => (query.startTime && query.endTime ? [query.startTime, query.endTime] : []),
  set: (v) => {
    query.startTime = v?.[0] || '';
    query.endTime = v?.[1] || '';
  },
});

/** 详情弹窗 */
const detailVisible = ref(false);
const detailTrace = ref(null);
const detailRules = ref([]);

/** 打开详情（列表点击 或 路由带 trace 跳入共用） */
async function openDetail(uuid) {
  try {
    const res = await queryAudit(uuid);
    const d = res.data || {};
    detailTrace.value = d.trace;
    detailRules.value = d.rules || [];
    detailVisible.value = true;
  } catch (e) {
    // 拦截器已提示（DATA_NOT_FOUND 等）
  }
}

function onDetail(row) {
  openDetail(row.traceUuid);
}

/** 步骤结果 → 时间线颜色类型 */
function stepType(r) {
  const m = { PASS: 'success', FAIL: 'danger', SKIP: 'info', SKIP_SEGMENT_MISMATCH: 'info', ERROR: 'danger' };
  return m[r] || 'primary';
}

/** 支持从外部带 trace 跳入（如工作台「最近匹配」→ /audit?trace=uuid）自动打开详情 */
const route = useRoute();
onMounted(async () => {
  const trace = route.query?.trace ? String(route.query.trace) : '';
  if (trace) {
    query.traceUuid = trace;
    await openDetail(trace);
  } else {
    load();
  }
});
</script>

<style scoped>
.mono {
  font-family: "SF Mono", Menlo, Consolas, monospace;
  font-size: 12px;
  color: var(--loan-text);
}
/* traceUuid 单行 + 截断省略（hover 列 tooltip 看完整 UUID） */
.uuid-cell {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  display: inline-block;
  max-width: 100%;
  vertical-align: middle;
}

.audit-meta {
  display: flex;
  gap: 24px;
  flex-wrap: wrap;
  padding: 14px 16px;
  background: var(--loan-surface);
  border: 1px solid var(--loan-border);
  border-radius: var(--loan-radius);
  margin-bottom: 20px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.meta-label {
  font-size: 12px;
  color: var(--loan-text-muted);
}

.meta-value {
  font-size: 14px;
  color: var(--loan-text);
  font-weight: 500;
}

.detail-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--loan-text);
  margin: 0 0 16px;
}

.tl-head {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
}

.tl-expr {
  margin-top: 4px;
  font-size: 12px;
  font-family: "SF Mono", Menlo, Consolas, monospace;
  color: var(--loan-text-secondary);
  word-break: break-all;
}
</style>
