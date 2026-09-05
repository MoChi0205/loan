<template>
  <div class="plan-edit-page">
    <div class="loan-page-header">
      <div>
        <h2 class="loan-page-title">计划编排</h2>
        <p class="loan-page-subtitle">按客群维度（企业贷 / 个贷）管理执行计划；计划 → 模块（AND/OR）→ 步骤（单条规则）；支持模块/步骤连接、空跑与步骤前置条件</p>
      </div>
    </div>

    <!-- 客群切换（与策略模板页保持一致） -->
    <div class="cg-switch">
      <el-segmented v-model="activeCustomerGroup" :options="cgOptions" size="default" @change="onCustomerGroupChange" />
      <span class="cg-switch__hint">执行计划按客群分开维护，切换后自动过滤（URL ?cg= 同步）</span>
    </div>

    <!-- 计划列表 -->
    <div class="loan-card">
      <AppSearchBar :loading="loading" @search="onSearch" @reset="onReset">
        <el-input v-model="query.keyword" placeholder="计划编码 / 名称" clearable style="width: 260px" @keyup.enter="onSearch" />
        <template #append>
          <el-button type="primary" @click="openPlanDialog()">
            <AppIcon name="add" :size="14" />
            新建计划
          </el-button>
          <el-button v-if="planId" @click="openPlanDialog(currentPlan)">编辑计划</el-button>
          <el-button v-if="planId" @click="onCopyPlan(currentPlan)">复制计划</el-button>
          <el-button v-if="planId" @click="onSaveAsTemplate(currentPlan)">另存为模版</el-button>
          <el-button v-if="planId" type="danger" plain @click="onDeletePlan(currentPlan)">删除计划</el-button>
        </template>
      </AppSearchBar>

      <el-table
        ref="planTableRef"
        :data="filteredPlans"
        v-loading="loading"
        highlight-current-row
        :row-key="rowKey"
        @row-click="onRowClick"
        @sort-change="handleSortChange"
        class="plan-table"
        style="height: calc(100vh - 320px); min-height: 360px"
      >
        <el-table-column
          v-for="col in columns"
          :key="col.key"
          :prop="col.key"
          :label="col.label"
          :width="col.width"
          :sortable="col.sortable"
        >
          <template #default="{ row }">
            <span v-if="col.key === 'customerGroup'">
              <span class="loan-tag" :class="row.customerGroup === 'PERSONAL' ? 'loan-tag-warning' : 'loan-tag-primary'">
                {{ row.customerGroup === 'PERSONAL' ? '个贷' : '企业' }}
              </span>
            </span>
            <span v-else-if="col.key === 'createdAt' || col.key === 'updatedAt'">
              {{ formatDateTime(row[col.key]) }}
            </span>
            <span v-else>{{ row[col.key] }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click.stop="openPlanDialog(row)">编辑</el-button>
            <el-button link type="primary" size="small" @click.stop="onCopyPlan(row)">复制</el-button>
            <el-button link type="primary" size="small" @click.stop="onSaveAsTemplate(row)">另存为模板</el-button>
            <el-button link type="danger" size="small" @click.stop="onDeletePlan(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <AppEmpty title="暂无执行计划" desc="点击「新建计划」创建第一个执行计划" />
        </template>
      </el-table>
    </div>

    <!-- 计划弹窗 -->
    <AppDialog v-model:visible="planDialog.visible" :title="planDialog.title" :loading="planDialog.saving" @confirm="onSavePlan">
      <el-form ref="planFormRef" :model="planDialog.form" :rules="planRules" label-width="90px">
        <el-form-item label="客群" prop="customerGroup">
          <el-select v-model="planDialog.form.customerGroup" style="width: 100%" teleported>
            <el-option label="企业贷（ENTERPRISE）" value="ENTERPRISE" />
            <el-option label="个贷（PERSONAL）" value="PERSONAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="计划编码" prop="planCode"><el-input v-model="planDialog.form.planCode" placeholder="如 ENT_TAX_PLAN_V2" /></el-form-item>
        <el-form-item label="计划名称" prop="planName"><el-input v-model="planDialog.form.planName" placeholder="计划名称" /></el-form-item>
        <el-form-item label="版本"><el-input-number v-model="planDialog.form.version" :min="1" /></el-form-item>
      </el-form>
    </AppDialog>

    <!-- 模块弹窗 -->
    <AppDialog v-model:visible="moduleDialog.visible" :title="moduleDialog.title" :loading="moduleDialog.saving" @confirm="onSaveModule">
      <el-form ref="moduleFormRef" :model="moduleDialog.form" :rules="moduleRules" label-width="90px">
        <el-form-item label="模块编码" prop="moduleCode"><el-input v-model="moduleDialog.form.moduleCode" placeholder="如 OPERATION" /></el-form-item>
        <el-form-item label="模块名称" prop="moduleName"><el-input v-model="moduleDialog.form.moduleName" placeholder="模块名称" /></el-form-item>
        <el-form-item label="逻辑">
          <el-select v-model="moduleDialog.form.logicType" style="width: 100%" teleported>
            <el-option label="AND（模块内遇FAIL短路）" value="AND" />
            <el-option label="OR（模块内遇PASS短路）" value="OR" />
          </el-select>
        </el-form-item>
        <el-form-item label="连接下模块">
          <el-select v-model="moduleDialog.form.joinWithNextModule" style="width: 100%" teleported>
            <el-option label="AND（串行，任一FAIL即短路）" value="AND" />
            <el-option label="OR（并行，全FAIL才失败）" value="OR" />
          </el-select>
        </el-form-item>
        <el-form-item label="全局风控">
          <el-switch v-model="moduleDialog.form.isGlobalPre" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="顺序"><el-input-number v-model="moduleDialog.form.sort" :min="0" /></el-form-item>
      </el-form>
    </AppDialog>

    <!-- 步骤弹窗（选规则 + 连接 + 空跑 + 前置条件） -->
    <AppDialog v-model:visible="stepDialog.visible" :title="stepDialog.title" :loading="stepDialog.saving" @confirm="onSaveStep" width="760px">
      <el-form ref="stepFormRef" :model="stepDialog.form" :rules="stepRules" label-width="100px" class="step-dialog-form">
        <!-- 规则选择：按分类级联 -->
        <el-form-item label="规则分类" prop="categoryCode">
          <el-select v-model="stepDialog.form.categoryCode" placeholder="先选择规则分类" clearable style="width: 100%" @change="onCategoryChange">
            <el-option v-for="c in ruleCategories" :key="c.code" :label="c.name" :value="c.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="规则" prop="ruleId">
          <el-select v-model="stepDialog.form.ruleId" :placeholder="stepDialog.form.categoryCode ? '选择规则' : '请先选择规则分类'" filterable :disabled="!stepDialog.form.categoryCode" style="width: 100%" teleported>
            <el-option v-for="r in filteredRules" :key="r.ruleId || r.ruleCode" :label="`${r.ruleName}（${r.ruleCode}）`" :value="r.ruleId || r.ruleCode" />
          </el-select>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="顺序"><el-input-number v-model="stepDialog.form.stepSort" :min="0" style="width: 100%" /></el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="与下一步">
              <el-select v-model="stepDialog.form.joinWithNext" clearable style="width: 100%" teleported>
                <el-option label="AND（串行，FAIL即短路）" value="AND" />
                <el-option label="OR（本步PASS短路后续）" value="OR" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="空跑">
              <el-switch v-model="stepDialog.form.isDryRun" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-divider content-position="left">步骤前置条件（选填；字段 + 运算符非空时生效）</el-divider>
        <el-row :gutter="16">
          <el-col :span="7">
            <el-form-item label="条件字段">
              <el-select v-model="stepDialog.form.conditionField" filterable allow-create default-first-option clearable placeholder="fact 字段码" style="width: 100%" teleported>
                <el-option v-for="f in conditionFieldOptions" :key="f.value" :label="f.label" :value="f.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="运算符">
              <el-select v-model="stepDialog.form.conditionOperator" clearable placeholder="如 EQ" style="width: 100%" teleported>
                <el-option v-for="o in OPERATORS" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="11">
            <el-form-item label="条件值">
              <el-input
                v-model="stepDialog.form.conditionValue"
                :disabled="valueDisabled"
                :placeholder="valueDisabled ? 'IS_BLANK 可不填' : '输入条件值'"
                :style="!valueDisabled ? 'width: 100%' : ''"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <p class="condition-hint">运行时若条件不满足，该步骤将标记为 SKIP（跳过）并继续后续步骤，不会阻断链路。</p>
      </el-form>
    </AppDialog>

    <!-- 计划详情弹窗：左模块列表 + 右步骤详情 -->
    <AppDialog v-model:visible="detailDialog.visible" title="计划编排详情" width="960px">
      <template #footer>
        <el-button @click="detailDialog.visible = false">关闭</el-button>
      </template>
      <div class="detail-dialog-body">
        <div class="detail-header">
          <span class="detail-title">{{ currentPlan?.planName }}</span>
          <span class="detail-code">{{ currentPlan?.planCode }}</span>
          <span class="detail-ver">v{{ currentPlan?.version }}</span>
          <span class="loan-tag" :class="currentPlan?.customerGroup === 'PERSONAL' ? 'loan-tag-warning' : 'loan-tag-primary'">
            {{ currentPlan?.customerGroup === 'PERSONAL' ? '个贷' : '企业' }}
          </span>
        </div>
        <div class="detail-layout">
          <!-- 左侧模块列表 -->
          <div class="module-sidebar">
            <div
              v-for="(m, mi) in modules"
              :key="m.moduleBizCode"
              class="module-item"
              :class="{ active: detailDialog.activeModuleCode === m.moduleBizCode }"
              @click="detailDialog.activeModuleCode = m.moduleBizCode"
            >
              <span class="mod-index">M{{ mi + 1 }}</span>
              <div class="module-item-info">
                <div class="module-item-name">{{ m.moduleName || m.moduleCode }}</div>
                <div class="module-item-code">{{ m.moduleCode }}</div>
              </div>
            </div>
            <AppEmpty v-if="!modules.length" title="暂无模块" desc="该计划下未配置任何模块" />
          </div>

          <!-- 右侧步骤详情 -->
          <div class="module-content">
            <div v-if="activeModule" class="module-content-inner">
              <div class="mod-head">
                <div class="mod-head-left">
                  <span class="mod-index">M{{ activeModuleIndex + 1 }}</span>
                  <span class="mod-name">{{ activeModule.moduleName || activeModule.moduleCode }}</span>
                  <span class="mod-code">{{ activeModule.moduleCode }}</span>
                </div>
                <div class="mod-head-right">
                  <span class="mod-tag" :class="activeModule.logicType === 'OR' ? 'tag-warn' : 'tag-info'">{{ activeModule.logicType }}</span>
                  <span v-if="activeModule.joinWithNextModule === 'OR'" class="mod-tag tag-warn">↓ OR</span>
                  <span v-else class="mod-tag tag-info">↓ AND</span>
                  <span v-if="activeModule.isGlobalPre" class="mod-tag tag-danger">全局风控</span>
                  <el-button link type="primary" size="small" @click="openModuleDialog(activeModule)">编辑</el-button>
                  <el-button link type="danger" size="small" @click="onDeleteModule(activeModule)">删除</el-button>
                </div>
              </div>

              <div v-if="activeModule.steps && activeModule.steps.length" class="step-table-wrap">
                <table class="step-table">
                  <thead>
                    <tr>
                      <th style="width:50px">#</th>
                      <th>规则名称</th>
                      <th style="width:180px">规则编码</th>
                      <th style="width:70px">连接</th>
                      <th style="width:60px">空跑</th>
                      <th style="width:220px">前置条件</th>
                      <th style="width:120px">操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="(s, si) in activeModule.steps" :key="s.stepCode">
                      <td class="col-num">{{ s.stepSort ?? si + 1 }}</td>
                      <td class="col-rule-name">{{ s.ruleName || s.ruleCode || '—' }}</td>
                      <td class="col-code"><code>{{ s.ruleCode || '—' }}</code></td>
                      <td>
                        <span v-if="s.joinWithNext === 'OR'" class="mod-tag tag-warn tag-sm">OR</span>
                        <span v-else-if="s.joinWithNext === 'AND'" class="mod-tag tag-info tag-sm">AND</span>
                        <span v-else class="text-muted">—</span>
                      </td>
                      <td>
                        <span v-if="s.isDryRun === 1" class="mod-tag tag-danger tag-sm">空跑</span>
                        <span v-else class="text-muted">—</span>
                      </td>
                      <td class="col-condition">
                        <span v-if="s.conditionOperator" class="condition-chip">
                          {{ s.conditionField }} <b>{{ s.conditionOperator }}</b> {{ s.conditionValue || '' }}
                        </span>
                        <span v-else class="text-muted">—</span>
                      </td>
                      <td class="col-actions">
                        <el-button link type="primary" size="small" @click="openStepDialog(activeModule, s)">编辑</el-button>
                        <el-button link type="danger" size="small" @click="onDeleteStep(activeModule, s)">删除</el-button>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
              <div v-else class="orch-empty">
                <AppIcon name="empty" :size="48" color="var(--loan-border)" />
                <p class="orch-empty-title">暂无步骤</p>
                <p class="orch-empty-desc">点击下方按钮添加步骤</p>
              </div>

              <div class="add-step-bar">
                <el-button size="small" @click="openStepDialog(activeModule)">
                  <AppIcon name="add" :size="12" />
                  添加步骤
                </el-button>
              </div>
            </div>
            <div v-else class="orch-empty">
              <AppIcon name="empty" :size="48" color="var(--loan-border)" />
              <p class="orch-empty-title">请选择模块</p>
              <p class="orch-empty-desc">在左侧选择要查看的模块</p>
            </div>
          </div>
        </div>
      </div>
    </AppDialog>
  </div>
</template>

<script setup>
defineOptions({ name: '_plan_edit' });
import { ref, reactive, computed, onMounted, nextTick } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import Sortable from 'sortablejs';
import AppDialog from '@/components/AppDialog.vue';
import AppEmpty from '@/components/AppEmpty.vue';
import AppSearchBar from '@/components/AppSearchBar.vue';
import AppIcon from '@/components/AppIcon.vue';
import { appConfirm } from '@/utils/confirm';
import { formatDateTime } from '@/utils/format';
import {
  listPlans, planDetail, createPlan, updatePlan, deletePlan,
  createModule, updateModule, deleteModule, createStep, updateStep, deleteStep,
  applyTemplate, saveAsTemplate, copyPlan,
} from '@/api/plan';
import { listRules } from '@/api/rule';
import { strategyExistsByPlan } from '@/api/channelStrategy';

const route = useRoute();
const router = useRouter();

/** 客群选项（el-segmented） */
const cgOptions = [
  { label: '🏢 企业贷', value: 'ENTERPRISE' },
  { label: '👤 个贷', value: 'PERSONAL' },
];
/** 当前选中的客群（从路由 query 参数 ?cg= 初始化，默认 ENTERPRISE） */
const activeCustomerGroup = ref(route.query.cg === 'PERSONAL' ? 'PERSONAL' : 'ENTERPRISE');

const OPERATORS = [
  { value: 'EQ', label: '= 等于' },
  { value: 'NE', label: '≠ 不等于' },
  { value: 'IN', label: 'IN 属于' },
  { value: 'NOT_IN', label: 'NOT IN 不属于' },
  { value: 'IS_BLANK', label: 'IS BLANK 为空' },
  { value: 'IS_NOT_BLANK', label: 'IS NOT BLANK 非空' },
];

/** 规则分类（四分类，与后端 RuleCatalog.RuleCategory 对齐） */
const ruleCategories = [
  { code: 'RISK', name: '基础风控（全局前置）' },
  { code: 'OPERATION', name: '经营能力' },
  { code: 'QUALIFICATION', name: '资质准入' },
  { code: 'PERSONAL', name: '个人基础' },
];

const plans = ref([]);
const rules = ref([]);
const planId = ref(null);
const modules = ref([]);
const currentPlan = ref(null);
const loading = ref(false);

/** 搜索关键字 + 排序参数（跨页后端排序） */
const query = reactive({ keyword: '', sortBy: '', sortDir: '' });

/** 列表表格实例 */
const planTableRef = ref();

/** 列配置（支持拖拽排序；操作列固定在最右，不参与拖拽） */
const columns = ref([
  { key: 'planName', label: '计划名称', width: 200, sortable: false },
  { key: 'planCode', label: '计划编码', width: 260, sortable: false },
  { key: 'version', label: '版本', width: 80, sortable: false },
  { key: 'customerGroup', label: '客群', width: 90, sortable: false },
  { key: 'createdAt', label: '创建时间', width: 170, sortable: 'custom' },
  { key: 'updatedAt', label: '更新时间', width: 170, sortable: 'custom' },
]);

/** 过滤后的计划列表 */
const filteredPlans = computed(() => {
  if (!query.keyword) return plans.value;
  const kw = query.keyword.toLowerCase();
  return plans.value.filter((p) =>
    p.planName?.toLowerCase().includes(kw) || p.planCode?.toLowerCase().includes(kw),
  );
});

/** 切换客群时：清空选中、重新加载计划和规则（URL ?cg= 同步） */
function onCustomerGroupChange(val) {
  planId.value = null;
  modules.value = [];
  currentPlan.value = null;
  router.replace({ query: { ...route.query, cg: val } });
  loadPlans();
  loadRulesByCG(val);
}

/** 按客群加载规则（个人/企业规则分开维护） */
async function loadRulesByCG(customerGroup) {
  try {
    const res = await listRules({ customerGroup: customerGroup || 'ENTERPRISE' });
    rules.value = res.data || [];
  } catch { rules.value = []; }
}

const rowKey = (row) => row.planCode;

/** 点击行：选中计划并打开详情弹窗 */
async function onRowClick(p) {
  selectPlan(p);
  detailDialog.visible = true;
  await loadDetail();
  detailDialog.activeModuleCode = modules.value[0]?.moduleBizCode || null;
}

function selectPlan(p) {
  planId.value = p.planCode;
  currentPlan.value = p;
}

function onSearch() {
  // 搜索已通过 computed 实时过滤
}

function onReset() {
  query.keyword = '';
}

/** 列头排序（时间列走后端跨页排序：sortBy/sortDir 传 list 接口） */
function handleSortChange({ prop, order }) {
  if (!prop || !order) {
    // 清除排序：恢复后端默认 id 倒序
    query.sortBy = '';
    query.sortDir = '';
    loadPlans();
    return;
  }
  query.sortBy = prop;
  query.sortDir = order === 'ascending' ? 'asc' : 'desc';
  loadPlans();
}

/** 初始化表头列拖拽（操作列固定不参与） */
function initColumnDrag() {
  const headerRow = planTableRef.value?.$el.querySelector('.el-table__header-wrapper tr');
  if (!headerRow) return;
  Sortable.create(headerRow, {
    animation: 150,
    filter: '.el-table__cell:last-child',
    preventOnFilter: false,
    onEnd: (evt) => {
      let { newIndex, oldIndex } = evt;
      if (oldIndex == null || newIndex == null) return;
      // 操作列固定在最右侧，拖拽目标限制在数据列范围内
      if (oldIndex >= columns.value.length) return;
      if (newIndex >= columns.value.length) newIndex = columns.value.length - 1;
      if (newIndex === oldIndex) return;
      const item = columns.value.splice(oldIndex, 1)[0];
      columns.value.splice(newIndex, 0, item);
    },
  });
}

/** 条件字段候选：来自规则库 fieldCode（去重）+ 可输入自定义 fact 字段码 */
const conditionFieldOptions = computed(() => {
  const seen = new Set();
  const opts = [];
  (rules.value || []).forEach((r) => {
    const code = r.fieldCode;
    if (code && !seen.has(code)) {
      seen.add(code);
      opts.push({ value: code, label: code });
    }
  });
  return opts;
});

/** 按分类过滤后的规则列表 */
const filteredRules = computed(() => {
  const cat = stepDialog.form.categoryCode;
  if (!cat) return [];
  return (rules.value || []).filter((r) => r.categoryCode === cat);
});

/** 切换分类时清空已选规则 */
function onCategoryChange() {
  stepDialog.form.ruleId = null;
}

const valueDisabled = computed(() =>
  ['IS_BLANK', 'IS_NOT_BLANK'].includes(stepDialog.form.conditionOperator));

async function loadDetail() {
  if (!planId.value) return;
  try {
    const res = await planDetail(planId.value);
    currentPlan.value = res.data?.plan;
    modules.value = res.data?.modules || [];
  } catch (e) { /* 拦截器已提示 */ }
}

// ============================================================
// 详情弹窗
// ============================================================
const detailDialog = reactive({ visible: false, activeModuleCode: null });

const activeModule = computed(() =>
  modules.value.find((m) => m.moduleBizCode === detailDialog.activeModuleCode) || null,
);

const activeModuleIndex = computed(() =>
  modules.value.findIndex((m) => m.moduleBizCode === detailDialog.activeModuleCode),
);

// 计划弹窗
const planDialog = reactive({ visible: false, title: '', saving: false, editing: false, form: { customerGroup: 'ENTERPRISE', planCode: '', planName: '', version: 1 } });
const planFormRef = ref();
const planRules = {
  customerGroup: [{ required: true, message: '请选择客群', trigger: 'change' }],
  planCode: [{ required: true, message: '计划编码必填', trigger: 'blur' }, { min: 2, max: 64, message: '2-64 字符', trigger: 'blur' }],
  planName: [{ required: true, message: '计划名称必填', trigger: 'blur' }, { min: 2, max: 64, message: '2-64 字符', trigger: 'blur' }],
};
function openPlanDialog(p) {
  planDialog.title = p ? '编辑计划' : '新建计划';
  planDialog.editing = !!p;
  const cg = p?.customerGroup || activeCustomerGroup.value;
  Object.assign(planDialog.form, p
    ? { customerGroup: cg, planCode: p.planCode, planName: p.planName, version: p.version }
    : { customerGroup: activeCustomerGroup.value, planCode: '', planName: '', version: 1 });
  planFormRef.value?.clearValidate();
  planDialog.visible = true;
}
async function onSavePlan() {
  try {
    await planFormRef.value.validate();
  } catch (e) {
    return;
  }
  planDialog.saving = true;
  try {
    const payload = { customerGroup: planDialog.form.customerGroup, ...planDialog.form };
    if (planDialog.editing && currentPlan.value) {
      await updatePlan(currentPlan.value.planCode, payload);
    } else {
      await createPlan(payload);
    }
    ElMessage.success('已保存');
    planDialog.visible = false;
    loadPlans();
  } finally { planDialog.saving = false; }
}

/** 检查计划是否已被渠道准入策略引用（executionPlanCode 关联） */
async function checkPlanReferenced(planCode) {
  try {
    const res = await strategyExistsByPlan(planCode);
    return res.data === true;
  } catch (e) {
    return false;
  }
}
async function onDeletePlan(plan) {
  const target = plan || currentPlan.value;
  if (!target) return;
  // 删除保护：被策略引用时禁止删除，避免线上渠道准入失效
  const planCode = target.planCode;
  if (planCode) {
    const referenced = await checkPlanReferenced(planCode);
    if (referenced) {
      ElMessageBox.alert('该计划已被渠道准入策略引用，请先在「渠道配置」中解绑该策略后再删除', '无法删除', { type: 'warning', confirmButtonText: '知道了' });
      return;
    }
  }
  try {
    await appConfirm(`确认删除计划「${target.planName}」？（将级联删除模块/步骤）`);
  } catch { return; }
  await deletePlan(planCode);
  ElMessage.success('已删除');
  if (planId.value === planCode) {
    planId.value = null;
    modules.value = [];
    currentPlan.value = null;
  }
  loadPlans();
}

/** 复制计划为草稿（对齐 mds v2 copy） */
async function onCopyPlan(plan) {
  const target = plan || currentPlan.value;
  if (!target) return;
  try {
    await appConfirm(`确认复制计划「${target.planName}」为新的草稿计划？`);
  } catch { return; }
  try {
    await copyPlan({ planCode: target.planCode });
    ElMessage.success('已复制为新草稿计划');
    loadPlans();
  } catch { /* 拦截器已提示 */ }
}

/** 另存为模版（对齐 mds v2 save-as-template） */
async function onSaveAsTemplate(plan) {
  const target = plan || currentPlan.value;
  if (!target) return;
  try {
    const { value } = await ElMessageBox.prompt(
      '输入模版编码（需唯一）与名称，将当前计划保存为策略模版草稿',
      '另存为模版',
      {
        inputPlaceholder: '如 TPL_ENT_TAX_V1',
        inputValidator: (v) => (v && v.trim().length >= 2 ? true : '模版编码至少 2 个字符'),
        confirmButtonText: '保存',
        cancelButtonText: '取消',
      },
    );
    await saveAsTemplate({
      planCode: target.planCode,
      templateCode: value.trim(),
      templateName: `${target.planName}-模版`,
    });
    ElMessage.success('已另存为模版草稿');
  } catch { /* 取消或拦截器已提示 */ }
}

// 模块弹窗
const moduleDialog = reactive({ visible: false, title: '', saving: false, editingId: null, form: { moduleCode: '', moduleName: '', logicType: 'AND', joinWithNextModule: 'AND', isGlobalPre: 0, sort: 0 } });
const moduleFormRef = ref();
const moduleRules = {
  moduleCode: [{ required: true, message: '模块编码必填', trigger: 'blur' }, { min: 2, max: 64, message: '2-64 字符', trigger: 'blur' }],
  moduleName: [{ required: true, message: '模块名称必填', trigger: 'blur' }, { min: 2, max: 64, message: '2-64 字符', trigger: 'blur' }],
};
function openModuleDialog(m) {
  moduleDialog.title = m ? '编辑模块' : '添加模块';
  moduleDialog.editingId = m?.moduleBizCode || null;
  Object.assign(moduleDialog.form, m
    ? { moduleCode: m.moduleCode, moduleName: m.moduleName, logicType: m.logicType, joinWithNextModule: m.joinWithNextModule || 'AND', isGlobalPre: m.isGlobalPre || 0, sort: m.sort }
    : { moduleCode: '', moduleName: '', logicType: 'AND', joinWithNextModule: 'AND', isGlobalPre: 0, sort: 0 });
  moduleFormRef.value?.clearValidate();
  moduleDialog.visible = true;
}

/** 前端 FR-03 校验：模块 joinWithNextModule 聚合合法性（与后端 validatePlanStructure 对齐）。
 *  校验：sort 唯一 / 末位模块不可 OR / 模块级禁止连续 OR（OR 仅相邻二元组）。
 *  违规时抛 Error（message 即提示语）。 */
function validateModuleJoin(form, editingId) {
  const list = (modules.value || []).map((m) => ({
    id: m.moduleBizCode,
    sort: m.moduleBizCode === editingId ? form.sort : m.sort,
    moduleName: m.moduleName,
    joinWithNextModule: m.moduleBizCode === editingId ? form.joinWithNextModule : m.joinWithNextModule,
  }));
  if (!editingId) {
    list.push({ id: '__new__', sort: form.sort, moduleName: form.moduleName, joinWithNextModule: form.joinWithNextModule });
  }
  const sorted = [...list].sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0));
  const seen = new Set();
  for (const m of sorted) {
    if (m.sort != null && seen.has(m.sort)) {
      throw new Error('模块顺序 sort 重复：' + m.sort);
    }
    seen.add(m.sort);
  }
  for (let i = 0; i < sorted.length; i++) {
    const m = sorted[i];
    const isLast = i === sorted.length - 1;
    const join = (m.joinWithNextModule || 'AND').toUpperCase();
    if (isLast && join === 'OR') {
      throw new Error('末位模块「' + (m.moduleName || '') + '」joinWithNextModule 不可为 OR（悬空连接符）');
    }
    if (!isLast && join === 'OR') {
      const next = sorted[i + 1];
      if ((next.joinWithNextModule || 'AND').toUpperCase() === 'OR') {
        throw new Error('模块「' + (m.moduleName || '') + '」与「' + (next.moduleName || '') + '」连续 OR：OR 仅支持相邻二元组，禁止 ≥3 模块 OR 组');
      }
    }
  }
}

/** 前端 FR-03 校验：步骤 joinWithNext 聚合合法性（与后端对齐）。
 *  校验：stepSort 唯一 / 末位步骤不可 OR。步骤级连续 OR 允许（如 A OR B OR C 合法）。 */
function validateStepJoin(form, moduleId, editingId) {
  const module = (modules.value || []).find((m) => m.moduleBizCode === moduleId);
  const steps = (module?.steps || []).map((s) => ({
    id: s.stepCode,
    stepSort: s.stepCode === editingId ? form.stepSort : s.stepSort,
    joinWithNext: s.stepCode === editingId ? form.joinWithNext : s.joinWithNext,
  }));
  if (!editingId) {
    steps.push({ id: '__new__', stepSort: form.stepSort, joinWithNext: form.joinWithNext });
  }
  const sorted = [...steps].sort((a, b) => (a.stepSort ?? 0) - (b.stepSort ?? 0));
  const seen = new Set();
  for (const s of sorted) {
    if (s.stepSort != null && seen.has(s.stepSort)) {
      throw new Error('步骤顺序 stepSort 重复：' + s.stepSort);
    }
    seen.add(s.stepSort);
  }
  for (let j = 0; j < sorted.length; j++) {
    const isLast = j === sorted.length - 1;
    if (isLast && (sorted[j].joinWithNext || 'AND').toUpperCase() === 'OR') {
      throw new Error('末位步骤 joinWithNext 不可为 OR（悬空连接符）');
    }
  }
}

async function onSaveModule() {
  try {
    await moduleFormRef.value.validate();
  } catch (e) {
    return;
  }
  try {
    validateModuleJoin(moduleDialog.form, moduleDialog.editingId);
  } catch (err) {
    ElMessage.warning(err.message);
    return;
  }
  moduleDialog.saving = true;
  try {
    const payload = {
      planCode: planId.value,
      ...moduleDialog.form,
      joinWithNextModule: (moduleDialog.form.joinWithNextModule || 'AND').toUpperCase(),
    };
    if (moduleDialog.editingId) {
      await updateModule(moduleDialog.editingId, payload);
    } else {
      await createModule(payload);
    }
    ElMessage.success('已保存');
    moduleDialog.visible = false;
    loadDetail();
  } finally { moduleDialog.saving = false; }
}
async function onDeleteModule(m) {
  try { await appConfirm(`确认删除模块「${m.moduleName}」？（将级联删除步骤）`); } catch { return; }
  await deleteModule(m.moduleBizCode);
  loadDetail();
}

// 步骤弹窗（添加/编辑）
function emptyStepForm() {
  return { categoryCode: '', ruleId: null, stepSort: 0, joinWithNext: 'AND', isDryRun: 0, conditionField: '', conditionOperator: '', conditionValue: '' };
}
const stepDialog = reactive({ visible: false, title: '添加步骤', saving: false, editingId: null, moduleId: null, form: emptyStepForm() });
const stepFormRef = ref();
const stepRules = {
  ruleId: [{ required: true, message: '请选择规则', trigger: 'change' }],
};
function openStepDialog(m, s) {
  stepDialog.moduleId = m.moduleBizCode;
  stepDialog.editingId = s?.stepCode || null;
  stepDialog.title = s ? '编辑步骤' : '添加步骤';
  // 编辑时从已有规则反推分类
  const existingRule = s ? (rules.value || []).find((r) => (r.ruleId && r.ruleId === s.ruleId) || r.ruleCode === s.ruleCode) : null;
  Object.assign(stepDialog.form, s
    ? { categoryCode: existingRule?.categoryCode || '', ruleId: s.ruleId, stepSort: s.stepSort, joinWithNext: s.joinWithNext || 'AND', isDryRun: s.isDryRun || 0, conditionField: s.conditionField || '', conditionOperator: s.conditionOperator || '', conditionValue: s.conditionValue || '' }
    : { ...emptyStepForm(), stepSort: (m.steps?.length || 0) });
  stepFormRef.value?.clearValidate();
  stepDialog.visible = true;
}
async function onSaveStep() {
  try {
    await stepFormRef.value.validate();
  } catch (e) {
    return;
  }
  try {
    validateStepJoin(stepDialog.form, stepDialog.moduleId, stepDialog.editingId);
  } catch (err) {
    ElMessage.warning(err.message);
    return;
  }
  stepDialog.saving = true;
  try {
    const form = { ...stepDialog.form };
    form.joinWithNext = (form.joinWithNext || 'AND').toUpperCase();
    // 运算符为空时清空条件字段/值，避免脏数据
    if (!form.conditionOperator) {
      form.conditionField = '';
      form.conditionValue = '';
    }
    if (stepDialog.editingId) {
      await updateStep(stepDialog.editingId, form);
      ElMessage.success('已保存');
    } else {
      await createStep({ moduleBizCode: stepDialog.moduleId, ...form });
      ElMessage.success('已添加');
    }
    stepDialog.visible = false;
    loadDetail();
  } finally { stepDialog.saving = false; }
}
async function onDeleteStep(m, s) {
  try { await appConfirm(`确认删除步骤「${s.ruleName}」？`); } catch { return; }
  await deleteStep(s.stepCode);
  loadDetail();
}

async function loadPlans() {
  loading.value = true;
  try {
    const params = { customerGroup: activeCustomerGroup.value };
    if (query.sortBy) {
      params.orderBy = query.sortBy;
      params.orderDir = query.sortDir || 'desc';
    }
    const res = await listPlans(params);
    plans.value = res.data || [];
    // 不再自动选中第一个，由用户点击选择
  } catch { plans.value = []; } finally {
    loading.value = false;
  }
}

onMounted(async () => {
  loadPlans();
  await loadRulesByCG(activeCustomerGroup.value);
  nextTick(initColumnDrag);
});
</script>

<style scoped>
/* 客群切换（与策略模板页保持一致） */
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
.cg-switch :deep(.el-segmented) {
  background: var(--loan-surface, #f8fafc);
  padding: 3px;
  border-radius: var(--loan-radius);
}
.cg-switch :deep(.el-segmented__item) {
  font-weight: 500;
  padding: 8px 24px;
}

.plan-bar { display: flex; align-items: center; gap: 12px; }

/* 计划列表表格 */
.plan-table {
  margin-top: 12px;
}
.plan-table :deep(.el-table__header-wrapper th.el-table__cell) {
  cursor: grab;
}
.plan-table :deep(.el-table__header-wrapper th.el-table__cell:last-child) {
  cursor: default;
}
.plan-table :deep(.el-table__header-wrapper th.el-table__cell:active) {
  cursor: grabbing;
}

/* 详情弹窗 */
.detail-dialog-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.detail-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: var(--loan-surface, #f8fafc);
  border: 1px solid var(--loan-border);
  border-radius: var(--loan-radius);
}
.detail-title {
  font-weight: 600;
  font-size: 15px;
  color: var(--loan-text);
}
.detail-code {
  font-family: "SF Mono", Menlo, Consolas, monospace;
  font-size: 12px;
  color: var(--loan-text-secondary);
  background: #fff;
  padding: 2px 8px;
  border-radius: 4px;
}
.detail-ver {
  font-size: 12px;
  color: var(--loan-text-muted);
}

.detail-layout {
  display: flex;
  gap: 16px;
  min-height: 420px;
  border: 1px solid var(--loan-border);
  border-radius: var(--loan-radius);
  overflow: hidden;
}

/* 左侧模块列表 */
.module-sidebar {
  width: 220px;
  flex-shrink: 0;
  background: var(--loan-surface, #f8fafc);
  border-right: 1px solid var(--loan-border);
  padding: 12px;
  overflow-y: auto;
  max-height: 560px;
}
.module-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: var(--loan-radius);
  cursor: pointer;
  transition: all var(--loan-transition);
  margin-bottom: 6px;
}
.module-item:hover {
  background: var(--loan-primary-soft, #f0f7ff);
}
.module-item.active {
  background: var(--loan-primary-soft, #f0f7ff);
  border: 1px solid var(--loan-primary);
}
.module-item-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  overflow: hidden;
}
.module-item-name {
  font-weight: 600;
  font-size: 13px;
  color: var(--loan-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.module-item-code {
  font-family: "SF Mono", Menlo, Consolas, monospace;
  font-size: 11px;
  color: var(--loan-text-muted);
}

/* 右侧内容 */
.module-content {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
  max-height: 560px;
  background: var(--loan-card-bg, #fff);
}
.module-content-inner {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* 模块标签与表格（复用原编排树样式） */
.mod-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: var(--loan-surface, #f8fafc);
  border: 1px solid var(--loan-border);
  border-radius: var(--loan-radius);
}
.mod-head-left {
  display: flex;
  align-items: center;
  gap: 10px;
}
.mod-head-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
.mod-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 6px;
  background: var(--loan-primary);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
}
.mod-name {
  font-weight: 600;
  font-size: 14px;
  color: var(--loan-text);
}
.mod-code {
  font-family: "SF Mono", Menlo, Consolas, monospace;
  font-size: 12px;
  color: var(--loan-text-muted);
  background: var(--loan-surface);
  padding: 2px 8px;
  border-radius: 4px;
}

.mod-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
  line-height: 1.6;
  white-space: nowrap;
}
.mod-tag.tag-sm {
  padding: 0 6px;
  font-size: 10px;
}
.tag-info { background: #e8f4fd; color: #1677ff; }
.tag-warn { background: #fff7e6; color: #fa8c16; }
.tag-danger { background: #fff1f0; color: #f5222d; }
.text-muted { color: var(--loan-text-muted); font-size: 12px; }

/* 步骤表格 */
.step-table-wrap {
  overflow-x: auto;
  border: 1px solid var(--loan-border);
  border-radius: var(--loan-radius);
}
.step-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.step-table thead th {
  padding: 8px 12px;
  text-align: left;
  font-weight: 600;
  color: var(--loan-text-secondary);
  background: var(--loan-surface, #f8fafc);
  border-bottom: 1px solid var(--loan-border);
  white-space: nowrap;
  font-size: 12px;
}
.step-table tbody td {
  padding: 10px 12px;
  border-bottom: 1px solid var(--loan-border, #f0f0f0);
  vertical-align: middle;
  color: var(--loan-text);
}
.step-table tbody tr:last-child td { border-bottom: none; }
.step-table tbody tr:hover td { background: var(--loan-primary-soft, #f0f7ff); }

.col-num { font-weight: 600; color: var(--loan-text-muted); font-size: 12px; }
.col-rule-name { font-weight: 500; }
.col-code code {
  font-family: "SF Mono", Menlo, Consolas, monospace;
  font-size: 11px;
  color: var(--loan-text-muted);
  background: var(--loan-surface);
  padding: 1px 6px;
  border-radius: 3px;
}
.col-condition { font-size: 12px; }
.condition-chip {
  display: inline-block;
  padding: 2px 8px;
  background: var(--loan-surface);
  border-radius: 4px;
  font-family: "SF Mono", Menlo, Consolas, monospace;
  font-size: 11px;
  color: var(--loan-text-secondary);
}
.condition-chip b { color: var(--loan-primary); margin: 0 3px; }
.col-actions { white-space: nowrap; }

/* 添加步骤按钮栏 */
.add-step-bar {
  padding: 10px 16px;
  border: 1px dashed var(--loan-border);
  border-radius: var(--loan-radius);
}
.add-step-bar .el-button {
  border-style: dashed;
  color: var(--loan-text-secondary);
}
.add-step-bar .el-button:hover {
  color: var(--loan-primary);
  border-color: var(--loan-primary);
}

/* 空状态 */
.orch-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 20px;
  color: var(--loan-text-muted);
}
.orch-empty-title {
  margin-top: 16px;
  font-size: 15px;
  font-weight: 600;
  color: var(--loan-text-secondary);
}
.orch-empty-desc {
  margin-top: 4px;
  font-size: 13px;
}

.condition-hint { margin: 0; font-size: 12px; color: var(--loan-text-muted); }

/* 步骤弹窗表单 */
.step-dialog-form .el-col:last-child .el-form-item { margin-bottom: 0; }
</style>
