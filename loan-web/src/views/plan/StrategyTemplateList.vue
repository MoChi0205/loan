<template>
  <div class="tpl-page">
    <div class="loan-page-header">
      <div>
        <h2 class="loan-page-title">策略模版</h2>
        <p class="loan-page-subtitle">模版 → 模块 → 步骤；上线后可被渠道策略导入复用</p>
      </div>
      <el-button @click="openSnapshot">
        <AppIcon name="download" :size="14" />
        从渠道快照
      </el-button>
      <el-button type="primary" @click="openCreate">
        <AppIcon name="add" :size="14" />
        新建模版
      </el-button>
    </div>

    <div class="cg-switch">
      <el-segmented v-model="activeCG" :options="cgOptions" size="default" @change="onCGChange" />
      <span class="cg-switch__hint">策略模版按客群分开维护，切换后自动过滤（URL ?cg= 同步）</span>
    </div>

    <div class="loan-card">
      <AppSearchBar :loading="loading" @search="onSearch" @reset="onReset">
        <el-select v-model="query.customerGroup" placeholder="客群" clearable style="width: 130px">
          <el-option label="企业" value="ENTERPRISE" />
          <el-option label="个人" value="PERSONAL" />
        </el-select>
        <el-input v-model="query.keyword" placeholder="模版编码 / 名称" clearable style="width: 200px" @keyup.enter="onSearch" />
      </AppSearchBar>

      <el-table :data="data" v-loading="loading" stripe row-key="templateCode">
        <template #empty>
          <AppEmpty title="暂无策略模版" desc="创建策略模版后，可通过「导入到策略」一键应用到渠道" />
        </template>
        <el-table-column prop="templateCode" label="模版编码" width="180" />
        <el-table-column prop="templateName" label="模版名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="客群" width="90">
          <template #default="{ row }"><DictTag type="customerGroup" :value="row.customerGroup" /></template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <span class="loan-tag" :class="row.status === 'ACTIVE' ? 'loan-tag-success' : 'loan-tag-muted'">{{ row.status === 'ACTIVE' ? '已上线' : '草稿' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <AppTableActions :actions="rowActions(row)" />
          </template>
        </el-table-column>
      </el-table>

      <AppPagination v-model:page="query.page" v-model:size="query.size" :total="total" @change="load" />
    </div>

    <!-- 新建/编辑模版 -->
    <AppDialog v-model:visible="tplDialog.visible" :title="tplDialog.title" :loading="tplDialog.saving" @confirm="onSaveTpl">
      <el-form ref="tplFormRef" :model="tplDialog.form" :rules="tplRules" label-width="90px">
        <el-form-item label="模版编码" prop="templateCode"><el-input v-model="tplDialog.form.templateCode" placeholder="渠道内唯一" /></el-form-item>
        <el-form-item label="模版名称" prop="templateName"><el-input v-model="tplDialog.form.templateName" /></el-form-item>
        <el-form-item label="客群" prop="customerGroup">
          <el-select v-model="tplDialog.form.customerGroup" style="width: 100%">
            <el-option label="企业" value="ENTERPRISE" />
            <el-option label="个人" value="PERSONAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明"><el-input v-model="tplDialog.form.description" type="textarea" /></el-form-item>
      </el-form>
    </AppDialog>

    <!-- 编排弹窗 -->
    <AppDialog v-model:visible="editVisible" :title="`模版编排 · ${currentTpl?.templateName || ''}`" width="720px">
      <div class="tpl-edit">
        <div v-for="m in editModules" :key="m.id" class="tpl-module">
          <div class="tpl-module-head">
            <span class="tpl-module-name">{{ m.moduleName }}</span>
            <span class="loan-tag" :class="m.logicType === 'OR' ? 'loan-tag-warning' : 'loan-tag-info'">{{ m.logicType }}</span>
            <span v-if="m.joinWithNextModule === 'OR'" class="loan-tag loan-tag-warning">或(下模块)</span>
            <span v-else class="loan-tag loan-tag-info">且(下模块)</span>
            <div class="tpl-module-actions">
              <el-button link type="primary" @click="openModuleDialog(m)">编辑</el-button>
              <el-button link type="danger" @click="onDeleteModule(m)">删除</el-button>
            </div>
          </div>
          <div class="tpl-steps">
            <div v-for="s in m.steps" :key="s.id" class="tpl-step">
              <span class="tpl-step-rule">{{ s.ruleName || s.ruleCode }}</span>
              <span v-if="s.joinWithNext === 'OR'" class="loan-tag loan-tag-warning">或</span>
              <span v-else-if="s.joinWithNext === 'AND'" class="loan-tag loan-tag-info">且</span>
              <span v-if="s.isDryRun === 1" class="loan-tag loan-tag-danger" title="空跑模式：Handler REJECT 时不阻断流程">空跑</span>
              <span v-if="s.conditionOperator" class="tpl-step-condition" :title="`前置条件：${s.conditionField} ${s.conditionOperator} ${s.conditionValue || ''}`">{{ s.conditionField }} {{ s.conditionOperator }} {{ s.conditionValue || '' }}</span>
              <div class="tpl-step-actions">
                <el-button link type="primary" @click="openStepDialog(m, s)">编辑</el-button>
                <el-button link type="danger" @click="onDeleteStep(m, s)">删除</el-button>
              </div>
            </div>
            <el-button link type="primary" @click="openStepDialog(m)">+ 添加步骤</el-button>
          </div>
        </div>
        <el-button style="margin-top: 12px" @click="openModuleDialog()">+ 添加模块</el-button>
      </div>
    </AppDialog>

    <!-- 模块/步骤弹窗（复用简版） -->
    <AppDialog v-model:visible="modDialog.visible" :title="modDialog.title" :loading="modDialog.saving" @confirm="onSaveModule">
      <el-form ref="modFormRef" :model="modDialog.form" :rules="modRules" label-width="90px">
        <el-form-item label="模块编码" prop="moduleCode"><el-input v-model="modDialog.form.moduleCode" /></el-form-item>
        <el-form-item label="模块名称" prop="moduleName"><el-input v-model="modDialog.form.moduleName" /></el-form-item>
        <el-form-item label="逻辑">
          <el-select v-model="modDialog.form.logicType" style="width: 100%">
            <el-option label="AND" value="AND" /><el-option label="OR" value="OR" />
          </el-select>
        </el-form-item>
        <el-form-item label="连接下模块">
          <el-select v-model="modDialog.form.joinWithNextModule" style="width: 100%">
            <el-option label="AND（串行，任一FAIL即短路）" value="AND" />
            <el-option label="OR（并行，全FAIL才失败）" value="OR" />
          </el-select>
        </el-form-item>
        <el-form-item label="顺序"><el-input-number v-model="modDialog.form.sort" :min="0" /></el-form-item>
      </el-form>
    </AppDialog>

    <AppDialog v-model:visible="stepDialog.visible" :title="stepDialog.title" :loading="stepDialog.saving" @confirm="onSaveStep" width="720px">
      <el-form ref="stepFormRef" :model="stepDialog.form" :rules="stepRules" label-width="100px">
        <el-form-item label="规则" prop="ruleId">
          <el-select v-model="stepDialog.form.ruleId" placeholder="选择规则（必选）" filterable style="width: 100%">
            <el-option v-for="r in rules" :key="r.ruleId" :label="`${r.ruleName}（${r.ruleCode}）`" :value="r.ruleId" />
          </el-select>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="顺序"><el-input-number v-model="stepDialog.form.stepSort" :min="0" style="width: 100%" /></el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="与下一步">
              <el-select v-model="stepDialog.form.joinWithNext" clearable style="width: 100%">
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
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="条件字段">
              <el-select v-model="stepDialog.form.conditionField" filterable allow-create default-first-option clearable placeholder="fact 字段码" style="width: 100%">
                <el-option v-for="f in conditionFieldOptions" :key="f.value" :label="f.label" :value="f.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="运算符">
              <el-select v-model="stepDialog.form.conditionOperator" clearable placeholder="如 EQ" style="width: 100%">
                <el-option v-for="o in OPERATORS" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="条件值">
              <el-input v-model="stepDialog.form.conditionValue" :disabled="valueDisabled" placeholder="IS_BLANK 可不填" />
            </el-form-item>
          </el-col>
        </el-row>
        <p class="tpl-condition-hint">运行时若条件不满足，该步骤将标记为 SKIP（跳过）并继续后续步骤，不会阻断链路。</p>
      </el-form>
    </AppDialog>

    <!-- 导入到渠道准入策略 -->
    <AppDialog v-model:visible="importDialog.visible" title="导入到渠道准入策略" :loading="importDialog.saving" @confirm="onImport">
      <el-form ref="importFormRef" :model="importDialog.form" :rules="importRules" label-width="90px">
        <el-form-item label="渠道" prop="channelCode">
          <el-select v-model="importDialog.form.channelCode" placeholder="选择渠道" filterable style="width: 100%">
            <el-option v-for="c in channels" :key="c.channelCode" :label="c.bankName" :value="c.channelCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="产品" prop="bankProductCode">
          <RemoteProductSelect v-model="importDialog.form.bankProductCode" :customer-group="importDialog.form.customerGroup" />
        </el-form-item>
        <el-form-item label="策略编码" prop="strategyCode"><el-input v-model="importDialog.form.strategyCode" placeholder="渠道内唯一" /></el-form-item>
        <el-form-item label="策略名称"><el-input v-model="importDialog.form.strategyName" placeholder="缺省用模版名" /></el-form-item>
        <el-form-item label="客群">
          <el-select v-model="importDialog.form.customerGroup" style="width: 100%">
            <el-option label="企业" value="ENTERPRISE" />
            <el-option label="个人" value="PERSONAL" />
          </el-select>
        </el-form-item>
      </el-form>
    </AppDialog>

    <!-- 从渠道策略快照为模版 -->
    <AppDialog v-model:visible="snapshotDialog.visible" title="从渠道策略快照为模版" :loading="snapshotDialog.saving" @confirm="onSnapshot">
      <el-form label-width="110px">
        <el-form-item label="源渠道">
          <el-select v-model="snapshotDialog.channelCode" placeholder="选择渠道" filterable style="width: 100%" @change="onSnapshotChannelChange">
            <el-option v-for="c in channels" :key="c.channelCode" :label="`${c.bankName}（${c.channelCode}）`" :value="c.channelCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="源策略" v-loading="snapshotDialog.loadingStrategies">
          <RemoteStrategySelect v-model="snapshotDialog.strategyCode" :channel-code="snapshotDialog.channelCode" @selected="snapshotSource = $event" />
        </el-form-item>
        <el-form-item label="模版编码" required>
          <el-input v-model="snapshotDialog.templateCode" placeholder="需唯一" />
        </el-form-item>
        <el-form-item label="模版名称" required>
          <el-input v-model="snapshotDialog.templateName" placeholder="缺省用策略名" />
        </el-form-item>
        <p class="tpl-condition-hint">将渠道策略及其执行计划树（模块/步骤/规则）快照为模版草稿，可继续编排后上线供其他渠道导入。</p>
      </el-form>
    </AppDialog>
  </div>
</template>

<script setup>
defineOptions({ name: '_strategy_template' });
import { ref, reactive, computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import DictTag from '@/components/DictTag.vue';
import AppSearchBar from '@/components/AppSearchBar.vue';
import AppPagination from '@/components/AppPagination.vue';
import AppEmpty from '@/components/AppEmpty.vue';
import AppTableActions from '@/components/AppTableActions.vue';
import AppDialog from '@/components/AppDialog.vue';
import RemoteProductSelect from '@/components/RemoteProductSelect.vue';
import RemoteStrategySelect from '@/components/RemoteStrategySelect.vue';
import AppIcon from '@/components/AppIcon.vue';
import { useTable } from '@/composables/useTable';
import { appConfirm } from '@/utils/confirm';
import { listRules } from '@/api/rule';
import { listChannels } from '@/api/channel';
import { importFromTemplate, pageStrategy } from '@/api/channelStrategy';
import {
  pageTemplate, createTemplate, updateTemplate, deleteTemplate, publishTemplate, offlineTemplate, templateDetail,
  createTemplateModule, updateTemplateModule, deleteTemplateModule, createTemplateStep, updateTemplateStep, deleteTemplateStep,
  snapshotFromChannel,
} from '@/api/strategyTemplate';

const OPERATORS = [
  { value: 'EQ', label: '= 等于' },
  { value: 'NE', label: '≠ 不等于' },
  { value: 'IN', label: 'IN 属于' },
  { value: 'NOT_IN', label: 'NOT IN 不属于' },
  { value: 'IS_BLANK', label: 'IS BLANK 为空' },
  { value: 'IS_NOT_BLANK', label: 'IS NOT BLANK 非空' },
];

const route = useRoute();
const router = useRouter();
const rules = ref([]);
const channels = ref([]);
const { loading, data, total, query, load, onSearch, onReset } = useTable(pageTemplate, { customerGroup: route.query.cg || '', keyword: '' });

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

function rowActions(row) {
  return [
    { key: 'edit', label: '编排', onClick: () => openEdit(row) },
    { key: 'import', label: '导入到策略', type: 'primary', onClick: () => openImport(row) },
    row.status === 'ACTIVE'
      ? { key: 'offline', label: '下线', type: 'warning', confirm: `确认下线「${row.templateName}」？`, onClick: () => onOffline(row) }
      : { key: 'publish', label: '上线', type: 'success', onClick: () => onPublish(row) },
    { key: 'editName', label: '编辑', onClick: () => openTplDialog(row) },
    { key: 'del', label: '删除', type: 'danger', confirm: `确认删除「${row.templateName}」？`, onClick: () => onDelete(row) },
  ];
}

async function onPublish(row) { await publishTemplate(row.templateCode); ElMessage.success('已上线'); load(); }
async function onOffline(row) { await offlineTemplate(row.templateCode); ElMessage.success('已下线'); load(); }
async function onDelete(row) { await deleteTemplate(row.templateCode); ElMessage.success('已删除'); load(); }

// 模版 CRUD
const tplDialog = reactive({ visible: false, title: '', saving: false, editingId: null, form: { templateCode: '', templateName: '', customerGroup: 'ENTERPRISE', description: '' } });
const tplFormRef = ref();
const tplRules = {
  templateCode: [{ required: true, message: '请输入模版编码', trigger: 'blur' }],
  templateName: [{ required: true, message: '请输入模版名称', trigger: 'blur' }],
  customerGroup: [{ required: true, message: '请选择客群', trigger: 'change' }],
};
function openCreate() {
  tplDialog.title = '新建模版';
  tplDialog.editingId = null;
  Object.assign(tplDialog.form, { templateCode: '', templateName: '', customerGroup: 'ENTERPRISE', description: '' });
  tplDialog.visible = true;
}
function openTplDialog(row) {
  tplDialog.title = '编辑模版';
  tplDialog.editingId = row.id;
  Object.assign(tplDialog.form, { templateCode: row.templateCode, templateName: row.templateName, customerGroup: row.customerGroup, description: row.description });
  tplDialog.visible = true;
}
async function onSaveTpl() {
  await tplFormRef.value.validate();
  tplDialog.saving = true;
  try {
    if (tplDialog.editingId) await updateTemplate(tplDialog.form.templateCode, { ...tplDialog.form });
    else await createTemplate({ ...tplDialog.form });
    ElMessage.success('已保存');
    tplDialog.visible = false;
    load();
  } finally { tplDialog.saving = false; }
}

// 编排
const editVisible = ref(false);
const currentTpl = ref(null);
const editModules = ref([]);
/** 按模版客群加载规则（个人/企业规则分开维护，避免个人模版选到企业规则） */
async function loadRules(customerGroup) {
  try {
    const res = await listRules({ customerGroup: customerGroup || 'ENTERPRISE' });
    rules.value = res.data || [];
  } catch { rules.value = []; }
}
async function openEdit(row) {
  currentTpl.value = row;
  editVisible.value = true;
  await loadRules(row.customerGroup);
  const res = await templateDetail(row.templateCode);
  editModules.value = res.data?.modules || [];
}

const modDialog = reactive({ visible: false, title: '', saving: false, editingId: null, form: { moduleCode: '', moduleName: '', logicType: 'AND', joinWithNextModule: 'AND', sort: 0 } });
const modFormRef = ref();
const modRules = {
  moduleCode: [{ required: true, message: '请输入模块编码', trigger: 'blur' }],
  moduleName: [{ required: true, message: '请输入模块名称', trigger: 'blur' }],
};
function openModuleDialog(m) {
  modDialog.title = m ? '编辑模块' : '添加模块';
  modDialog.editingId = m?.id || null;
  Object.assign(modDialog.form, m
    ? { moduleCode: m.moduleCode, moduleName: m.moduleName, logicType: m.logicType, joinWithNextModule: m.joinWithNextModule || 'AND', sort: m.sort }
    : { moduleCode: '', moduleName: '', logicType: 'AND', joinWithNextModule: 'AND', sort: 0 });
  modDialog.visible = true;
}
async function onSaveModule() {
  await modFormRef.value.validate();
  modDialog.saving = true;
  try {
    const payload = { templateId: currentTpl.value.id, ...modDialog.form };
    if (modDialog.editingId) await updateTemplateModule(modDialog.editingId, payload);
    else await createTemplateModule(payload);
    modDialog.visible = false;
    const res = await templateDetail(currentTpl.value.templateCode);
    editModules.value = res.data?.modules || [];
  } finally { modDialog.saving = false; }
}
async function onDeleteModule(m) {
  try { await appConfirm(`确认删除模块「${m.moduleName}」？`); } catch { return; }
  await deleteTemplateModule(m.id);
  const res = await templateDetail(currentTpl.value.templateCode);
  editModules.value = res.data?.modules || [];
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

const valueDisabled = computed(() =>
  ['IS_BLANK', 'IS_NOT_BLANK'].includes(stepDialog.form.conditionOperator));

function emptyStepForm() {
  return { ruleId: null, stepSort: 0, joinWithNext: 'AND', isDryRun: 0, conditionField: '', conditionOperator: '', conditionValue: '' };
}
const stepDialog = reactive({ visible: false, title: '添加步骤', saving: false, editingId: null, moduleId: null, form: emptyStepForm() });
const stepFormRef = ref();
const stepRules = {
  ruleId: [{ required: true, message: '请选择规则', trigger: 'change' }],
};
function openStepDialog(m, s) {
  stepDialog.moduleId = m.id;
  stepDialog.editingId = s?.id || null;
  stepDialog.title = s ? '编辑步骤' : '添加步骤';
  Object.assign(stepDialog.form, s
    ? { ruleId: s.ruleId, stepSort: s.stepSort, joinWithNext: s.joinWithNext || 'AND', isDryRun: s.isDryRun || 0, conditionField: s.conditionField || '', conditionOperator: s.conditionOperator || '', conditionValue: s.conditionValue || '' }
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
  stepDialog.saving = true;
  try {
    const form = { ...stepDialog.form };
    if (!form.conditionOperator) {
      form.conditionField = '';
      form.conditionValue = '';
    }
    if (stepDialog.editingId) {
      await updateTemplateStep(stepDialog.editingId, form);
      ElMessage.success('已保存');
    } else {
      await createTemplateStep({ templateModuleId: stepDialog.moduleId, ...form });
      ElMessage.success('已添加');
    }
    stepDialog.visible = false;
    const res = await templateDetail(currentTpl.value.templateCode);
    editModules.value = res.data?.modules || [];
  } finally { stepDialog.saving = false; }
}
async function onDeleteStep(m, s) {
  try { await appConfirm(`确认删除步骤「${s.ruleName}」？`); } catch { return; }
  await deleteTemplateStep(s.id);
  const res = await templateDetail(currentTpl.value.templateCode);
  editModules.value = res.data?.modules || [];
}

// 导入到渠道准入策略
const importDialog = reactive({ visible: false, saving: false, templateCode: '', form: { channelCode: '', bankProductCode: '', strategyCode: '', strategyName: '', customerGroup: 'ENTERPRISE' } });
const importFormRef = ref();
const importRules = {
  channelCode: [{ required: true, message: '请选择渠道', trigger: 'change' }],
  bankProductCode: [{ required: true, message: '请选择产品', trigger: 'change' }],
  strategyCode: [{ required: true, message: '请输入策略编码', trigger: 'blur' }],
};
function openImport(row) {
  importDialog.templateCode = row.templateCode;
  Object.assign(importDialog.form, { channelCode: '', bankProductCode: '', strategyCode: '', strategyName: '', customerGroup: row.customerGroup || 'ENTERPRISE' });
  importDialog.visible = true;
}
async function onImport() {
  await importFormRef.value.validate();
  importDialog.saving = true;
  try {
    await importFromTemplate({
      templateCode: importDialog.templateCode,
      channelCode: importDialog.form.channelCode,
      strategyCode: importDialog.form.strategyCode,
      strategyName: importDialog.form.strategyName,
      bankProductCode: importDialog.form.bankProductCode,
      customerGroup: importDialog.form.customerGroup,
    });
    ElMessage.success('已导入为渠道准入策略');
    importDialog.visible = false;
  } finally { importDialog.saving = false; }
}

// 从渠道策略快照为模版（对齐 mds v2 snapshot-from-channel）
const snapshotDialog = reactive({ visible: false, saving: false, loadingStrategies: false, channelCode: '', strategyCode: '', templateCode: '', templateName: '', strategies: [] });
const snapshotSource = ref(null);
function openSnapshot() {
  Object.assign(snapshotDialog, { channelCode: '', strategyCode: '', templateCode: '', templateName: '', strategies: [] });
  snapshotDialog.visible = true;
}
async function onSnapshotChannelChange() {
  snapshotDialog.strategyCode = '';
  snapshotSource.value = null;
  snapshotDialog.strategies = [];
  if (!snapshotDialog.channelCode) return;
  snapshotDialog.loadingStrategies = true;
  try {
    snapshotDialog.strategies = [];
  } catch (e) { /* 拦截器已提示 */ } finally {
    snapshotDialog.loadingStrategies = false;
  }
}
async function onSnapshot() {
  if (!snapshotDialog.channelCode) { ElMessage.warning('请选择源渠道'); return; }
  if (!snapshotDialog.strategyCode) { ElMessage.warning('请选择源策略'); return; }
  if (!snapshotDialog.templateCode?.trim()) { ElMessage.warning('请输入模版编码'); return; }
  const source = snapshotSource.value;
  if (source && !source.executionPlanCode) { ElMessage.warning('该策略尚未绑定执行计划，无法快照'); return; }
  snapshotDialog.saving = true;
  try {
    await snapshotFromChannel({
      channelCode: snapshotDialog.channelCode,
      strategyCode: snapshotDialog.strategyCode,
      templateCode: snapshotDialog.templateCode.trim(),
      templateName: snapshotDialog.templateName?.trim() || source?.strategyName || snapshotDialog.templateCode.trim(),
    });
    ElMessage.success('已快照为模版草稿');
    snapshotDialog.visible = false;
    load();
  } catch (e) { /* 拦截器已提示 */ } finally {
    snapshotDialog.saving = false;
  }
}

onMounted(async () => {
  await loadRules('ENTERPRISE');
  try {
    const [ch] = await Promise.all([listChannels()]);
    channels.value = ch.data || [];
  } catch { /* 忽略 */ }
  load();
});
</script>

<style scoped>
.tpl-edit { display: flex; flex-direction: column; gap: 12px; }
.tpl-module { border: 1px solid var(--loan-border); border-radius: var(--loan-radius); overflow: hidden; }
.tpl-module-head { display: flex; align-items: center; gap: 10px; padding: 10px 14px; background: var(--loan-surface); border-bottom: 1px solid var(--loan-border); }
.tpl-module-name { font-weight: 600; color: var(--loan-text); }
.tpl-module-actions { margin-left: auto; display: flex; gap: 4px; }
.tpl-steps { padding: 8px 14px; display: flex; flex-direction: column; gap: 4px; }
.tpl-step { display: flex; align-items: center; gap: 10px; }
.tpl-step-rule { color: var(--loan-text); }
.tpl-step-actions { margin-left: auto; display: flex; gap: 4px; }
.tpl-step-condition { color: var(--loan-text-muted); font-size: 12px; background: var(--loan-surface); padding: 0 6px; border-radius: 4px; font-family: monospace; }
.tpl-condition-hint { margin: 0; font-size: 12px; color: var(--loan-text-muted); }
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
