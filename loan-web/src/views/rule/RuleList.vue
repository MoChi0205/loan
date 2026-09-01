<template>
  <div class="rule-page">
    <div class="loan-page-header">
      <div>
        <h2 class="loan-page-title">规则目录</h2>
        <p class="loan-page-subtitle">企业 / 个人准入规则，四分类管理；规则表达式后台可配</p>
      </div>
      <el-button type="primary" @click="onAdd">
        <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" style="margin-right: 4px; vertical-align: -2px"><path d="M12 5v14M5 12h14"/></svg>
        新增规则
      </el-button>
    </div>

    <div class="cg-switch">
      <el-segmented v-model="activeCG" :options="cgOptions" size="default" @change="onCGChange" />
      <span class="cg-switch__hint">规则按客群分开维护，切换后自动过滤（URL ?cg= 同步）</span>
    </div>

    <div class="loan-card">
      <AppSearchBar :loading="loading" @search="onSearch" @reset="onReset">
        <el-select v-model="query.customerGroup" placeholder="客群" clearable style="width: 120px">
          <el-option label="企业" value="ENTERPRISE" />
          <el-option label="个人" value="PERSONAL" />
        </el-select>
        <el-select v-model="query.category" placeholder="分类" style="width: 140px" clearable>
          <el-option label="基础风控" value="基础风控" />
          <el-option label="经营能力" value="经营能力" />
          <el-option label="资质准入" value="资质准入" />
          <el-option label="额度测算" value="额度测算" />
        </el-select>
        <el-input v-model="query.keyword" placeholder="规则编码 / 名称" style="width: 220px" clearable @keyup.enter="onSearch" />
        <DictSelect v-model="query.status" type="ruleStatus" placeholder="状态" style="width: 130px" />
      </AppSearchBar>

      <!-- 批量操作栏 -->
      <div v-if="selectedRows.length" class="batch-bar">
        <span class="batch-count">
          已选 <b>{{ selectedRows.length }}</b> 项
        </span>
        <el-button size="small" type="success" plain @click="onBatch('ONLINE')">批量启用</el-button>
        <el-button size="small" type="warning" plain @click="onBatch('DISABLED')">批量停用</el-button>
        <el-button size="small" text @click="clearSelection">取消选择</el-button>
      </div>

      <el-table
        ref="tableRef"
        :data="paginatedData"
        v-loading="loading"
        stripe
        row-key="ruleCode"
        @selection-change="onSelectionChange"
      >
        <el-table-column type="selection" width="44" fixed="left" />
        <el-table-column type="expand" width="44">
          <template #default="{ row }">
            <div class="rule-detail">
              <div class="rule-detail__row">
                <span class="rule-detail__label">表达式</span>
                <code class="rule-detail__expr">{{ row.expression }}</code>
              </div>
              <div class="rule-detail__row">
                <span class="rule-detail__label">说明</span>
                <span class="rule-detail__text">{{ row.description }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="categoryName" label="分类" width="120">
          <template #default="{ row }">
            <span class="loan-tag" :class="categoryTag(row.categoryName)">{{ row.categoryName || '未分类' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="ruleCode" label="规则编码" width="120"  show-overflow-tooltip />
        <el-table-column prop="ruleName" label="规则名称" width="180" show-overflow-tooltip />
        <el-table-column label="客群" width="90">
          <template #default="{ row }">
            <span class="loan-tag" :class="row.customerGroup === 'PERSONAL' ? 'loan-tag-warning' : 'loan-tag-info'">{{ row.customerGroup === 'PERSONAL' ? '个贷' : '企业' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="expression" label="表达式" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <code class="rule-expr">{{ row.expression }}</code>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <DictTag type="ruleStatus" :value="row.status" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <AppTableActions :actions="rowActions(row)" />
          </template>
        </el-table-column>
      </el-table>

      <AppPagination
        v-model:page="query.page"
        v-model:size="query.size"
        :total="filteredTotal"
        @change="onPageChange"
      />
    </div>

    <!-- 规则详情弹窗 -->
    <AppDialog
      v-model:visible="detailVisible"
      :title="detailTitle"
      width="640px"
    >
      <div v-if="currentRule" class="rule-dialog">
        <div class="rule-dialog__row">
          <span class="rule-dialog__label">分类</span>
          <span class="loan-tag" :class="categoryTag(currentRule.categoryName)">{{ currentRule.categoryName || '未分类' }}</span>
        </div>
        <div class="rule-dialog__row">
          <span class="rule-dialog__label">编码</span>
          <code>{{ currentRule.ruleCode }}</code>
        </div>
        <div class="rule-dialog__row">
          <span class="rule-dialog__label">名称</span>
          <span>{{ currentRule.ruleName }}</span>
        </div>
        <div class="rule-dialog__row rule-dialog__row--block">
          <span class="rule-dialog__label">表达式</span>
          <pre class="rule-dialog__expr">{{ currentRule.expression }}</pre>
        </div>
        <div class="rule-dialog__row rule-dialog__row--block">
          <span class="rule-dialog__label">说明</span>
          <p class="rule-dialog__text">{{ currentRule.description }}</p>
        </div>
      </div>
    </AppDialog>

    <!-- 新增/编辑弹窗 -->
    <AppDialog
      v-model:visible="dialogVisible"
      :title="editing ? '编辑规则' : '新增规则'"
      :loading="saving"
      width="640px"
      @confirm="onSave"
    >
      <el-form ref="formRef" :model="ruleForm" :rules="ruleRules" label-width="110px" label-position="right">
        <el-form-item label="规则编码" prop="ruleCode">
          <el-input v-model="ruleForm.ruleCode" placeholder="如 ENT_REJECT_DISHONEST" :disabled="editing" />
        </el-form-item>
        <el-form-item label="规则名称" prop="ruleName">
          <el-input v-model="ruleForm.ruleName" placeholder="如 失信被执行人拦截" />
        </el-form-item>
        <el-form-item label="字段编码" prop="fieldCode">
          <el-input v-model="ruleForm.fieldCode" placeholder="如 dishonest_flag" />
        </el-form-item>
        <el-form-item label="字段名称" prop="fieldName">
          <el-input v-model="ruleForm.fieldName" placeholder="如 失信标记" />
        </el-form-item>
        <el-form-item label="运算符" prop="operator">
          <DictSelect v-model="ruleForm.operator" type="ruleOperator" placeholder="请选择运算符" />
        </el-form-item>
        <el-form-item label="值类型" prop="valueType">
          <DictSelect v-model="ruleForm.valueType" type="ruleValueType" placeholder="请选择值类型" />
        </el-form-item>
        <el-form-item label="规则值" prop="valueText">
          <el-input v-model="ruleForm.valueText" type="textarea" :rows="2" placeholder="如 true / 100000 / [A,B]" />
        </el-form-item>
        <el-form-item label="客群" prop="customerGroup">
          <DictSelect v-model="ruleForm.customerGroup" type="customerGroup" placeholder="请选择客群" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <DictSelect v-model="ruleForm.status" type="ruleStatus" />
        </el-form-item>
        <el-form-item label="规则说明" prop="description">
          <el-input v-model="ruleForm.description" type="textarea" :rows="2" placeholder="规则的业务含义说明" />
        </el-form-item>
      </el-form>
    </AppDialog>
  </div>
</template>

<script setup>
defineOptions({ name: '_rule' });
import { ref, computed, reactive, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import DictTag from '@/components/DictTag.vue';
import DictSelect from '@/components/DictSelect.vue';
import AppSearchBar from '@/components/AppSearchBar.vue';
import AppPagination from '@/components/AppPagination.vue';
import AppTableActions from '@/components/AppTableActions.vue';
import AppDialog from '@/components/AppDialog.vue';
import { listRules, createRule, updateRule, deleteRule, batchUpdateRuleStatus } from '@/api/rule';
import { appConfirm } from '@/utils/confirm';
import { copyText } from '@/utils/clipboard';

const route = useRoute();
const router = useRouter();
const loading = ref(false);
const rules = ref([]);

/** 客群页内切换（6合3 · T10：企业/个贷单入口，URL ?cg= 同步） */
const cgOptions = [
  { label: '🏢 企业贷', value: 'ENTERPRISE' },
  { label: '👤 个贷', value: 'PERSONAL' },
];
const activeCG = ref(route.query.cg === 'PERSONAL' ? 'PERSONAL' : 'ENTERPRISE');
function onCGChange(val) {
  query.value.customerGroup = val;
  router.replace({ query: { ...route.query, cg: val } });
  onSearch();
}

/** 加载规则（支持按客群过滤） */
async function loadRules() {
  loading.value = true;
  try {
    const params = {};
    // 如果有路由参数 cg，则按客群过滤
    if (query.value.customerGroup) {
      params.customerGroup = query.value.customerGroup;
    }
    const res = await listRules(params);
    rules.value = res.data || [];
  } finally {
    loading.value = false;
  }
}

/** 查询条件 */
const query = ref({ page: 1, size: 10, category: '', keyword: '', status: '', customerGroup: route.query.cg || '' });

/** 前端过滤（分类 + 客群 + 关键词 + 状态） */
const filteredData = computed(() => {
  const kw = query.value.keyword.trim().toLowerCase();
  return rules.value.filter((r) => {
    const okCategory = !query.value.category || (r.categoryName || '未分类') === query.value.category;
    const okCG = !query.value.customerGroup || r.customerGroup === query.value.customerGroup;
    const okKeyword =
      !kw ||
      String(r.ruleCode || '').toLowerCase().includes(kw) ||
      String(r.ruleName || '').toLowerCase().includes(kw);
    const okStatus = !query.value.status || r.status === query.value.status;
    return okCategory && okCG && okKeyword && okStatus;
  });
});

/** 过滤后分页（前端处理） */
const paginatedData = computed(() => {
  const start = (query.value.page - 1) * query.value.size;
  return filteredData.value.slice(start, start + query.value.size);
});
const filteredTotal = computed(() => filteredData.value.length);

function onSearch() {
  query.value.page = 1;
}

function onReset() {
  query.value = { page: 1, size: 10, category: '', keyword: '', status: '', customerGroup: route.query.cg || '' };
}

function onPageChange() {
  // useTable 不适用（前端分页），AppPagination 已通过 v-model 改 page，computed 自动响应
}

/** 批量操作 */
const selectedRows = ref([]);
const tableRef = ref();

function onSelectionChange(rows) {
  selectedRows.value = rows;
}

function clearSelection() {
  tableRef.value?.clearSelection();
}

/** 批量启用/停用 */
async function onBatch(status) {
  if (!selectedRows.value.length) return;
  const names = selectedRows.value.map((r) => r.ruleName).join('、');
  const actionText = status === 'ONLINE' ? '启用' : '停用';
  try {
    await appConfirm(
      `确认${actionText}以下 ${selectedRows.value.length} 条规则？\n${names}`,
      '批量操作',
    );
  } catch {
    return;
  }
  try {
    await batchUpdateRuleStatus({
      ruleCodes: selectedRows.value.map((r) => r.ruleCode),
      status,
    });
    ElMessage.success(`已${actionText} ${selectedRows.value.length} 条规则`);
    clearSelection();
    loadRules();
  } catch (e) {
    // 拦截器已提示
  }
}

/** 操作列 */
function rowActions(row) {
  return [
    { key: 'detail', label: '详情', onClick: () => onDetail(row) },
    { key: 'edit', label: '编辑', onClick: () => onEdit(row) },
    {
      key: 'delete',
      label: '删除',
      type: 'danger',
      confirm: `确认删除规则「${row.ruleName}」？删除后不可恢复。`,
      onClick: () => onDelete(row),
    },
    {
      key: 'copy',
      label: '复制表达式',
      onClick: () => onCopyExpr(row),
    },
  ];
}

function onAdd() {
  editing.value = false;
  Object.assign(ruleForm, {
    id: null,
    ruleCode: '',
    ruleName: '',
    fieldCode: '',
    fieldName: '',
    operator: '',
    valueType: 'STRING',
    valueText: '',
    customerGroup: 'ENTERPRISE',
    description: '',
    status: 'DRAFT',
  });
  dialogVisible.value = true;
}

function onEdit(row) {
  editing.value = true;
  Object.assign(ruleForm, {
    ruleCode: row.ruleCode,
    ruleName: row.ruleName,
    fieldCode: row.fieldCode,
    fieldName: row.fieldName,
    operator: row.operator,
    valueType: row.valueType,
    valueText: row.valueText,
    customerGroup: row.customerGroup,
    description: row.description,
    status: row.status,
  });
  dialogVisible.value = true;
}

async function onDelete(row) {
  try {
    await deleteRule(row.ruleCode);
    ElMessage.success('删除成功');
    loadRules();
  } catch (e) {
    // 拦截器已提示
  }
}

async function onCopyExpr(row) {
  try {
    await copyText(row.expression);
    ElMessage.success('表达式已复制');
  } catch {
    ElMessage.warning('复制失败');
  }
}

const detailVisible = ref(false);
const detailTitle = ref('');
const currentRule = ref(null);

function onDetail(row) {
  currentRule.value = row;
  detailTitle.value = `规则详情 · ${row.ruleName}`;
  detailVisible.value = true;
}

/** 分类标签样式 */
function categoryTag(c) {
  const m = {
    基础风控: 'loan-tag-danger',
    经营能力: 'loan-tag-info',
    资质准入: 'loan-tag-warning',
    额度测算: 'loan-tag-primary',
  };
  return m[c] || 'loan-tag-muted';
}

// ============================================================
// 新增/编辑弹窗
// ============================================================
const dialogVisible = ref(false);
const editing = ref(false);
const saving = ref(false);
const formRef = ref();
const ruleForm = reactive({
  id: null,
  ruleCode: '',
  ruleName: '',
  fieldCode: '',
  fieldName: '',
  operator: '',
  valueType: 'STRING',
  valueText: '',
  customerGroup: 'ENTERPRISE',
  description: '',
  status: 'DRAFT',
});
const ruleRules = {
  ruleCode: [{ required: true, message: '请输入规则编码', trigger: 'blur' }],
  ruleName: [{ required: true, message: '请输入规则名称', trigger: 'blur' }],
  fieldCode: [{ required: true, message: '请输入字段编码', trigger: 'blur' }],
  operator: [{ required: true, message: '请选择运算符', trigger: 'change' }],
  customerGroup: [{ required: true, message: '请选择客群', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
};

async function onSave() {
  await formRef.value.validate();
  saving.value = true;
  try {
    const payload = { ...ruleForm };
    if (editing.value) {
      await updateRule(payload);
      ElMessage.success('编辑成功');
    } else {
      await createRule(payload);
      ElMessage.success('新增成功');
    }
    dialogVisible.value = false;
    loadRules();
  } catch (e) {
    // 拦截器已提示
  } finally {
    saving.value = false;
  }
}

onMounted(loadRules);
</script>

<style scoped>
/* 批量操作栏 */
.batch-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  margin-bottom: 12px;
  background: var(--loan-primary-soft);
  border: 1px solid color-mix(in srgb, var(--loan-primary) 24%, transparent);
  border-radius: var(--loan-radius-sm);
}

.batch-count {
  font-size: 13px;
  color: var(--loan-text);
  margin-right: 8px;
}

.batch-count b {
  color: var(--loan-primary);
}

.rule-expr {
  font-family: "SF Mono", Menlo, Consolas, monospace;
  font-size: 12px;
  color: var(--loan-text-secondary);
  background: var(--loan-surface);
  padding: 1px 6px;
  border-radius: 4px;
}

/* 展开行：表达式 + 说明 */
.rule-detail {
  padding: 8px 12px 12px 56px;
  background: var(--loan-surface);
  border-radius: var(--loan-radius-sm);
}

.rule-detail__row {
  display: flex;
  gap: 12px;
  padding: 4px 0;
  font-size: 13px;
}

.rule-detail__label {
  color: var(--loan-text-muted);
  flex-shrink: 0;
  width: 56px;
}

.rule-detail__expr {
  font-family: "SF Mono", Menlo, Consolas, monospace;
  font-size: 12px;
  color: var(--loan-text);
  background: var(--loan-card-bg);
  padding: 2px 8px;
  border: 1px solid var(--loan-border);
  border-radius: 4px;
}

.rule-detail__text {
  color: var(--loan-text-secondary);
  line-height: 1.7;
}

/* 详情弹窗 */
.rule-dialog {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.rule-dialog__row {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  font-size: 14px;
}

.rule-dialog__row--block {
  flex-direction: column;
  gap: 6px;
}

.rule-dialog__label {
  color: var(--loan-text-muted);
  flex-shrink: 0;
  width: 72px;
}

.rule-dialog__expr {
  margin: 0;
  padding: 12px 14px;
  font-family: "SF Mono", Menlo, Consolas, monospace;
  font-size: 13px;
  color: var(--loan-text);
  background: var(--loan-surface);
  border: 1px solid var(--loan-border);
  border-radius: var(--loan-radius-sm);
  white-space: pre-wrap;
  word-break: break-all;
}

.rule-dialog__text {
  margin: 0;
  color: var(--loan-text-secondary);
  line-height: 1.7;
}
.cg-switch {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 0 0 12px;
}
.cg-switch__hint {
  font-size: 12px;
  color: var(--loan-text-muted);
}
</style>
