<template>
  <div class="wiz-page">
    <div class="loan-page-header">
      <div>
        <h2 class="loan-page-title">渠道档案向导</h2>
        <p class="loan-page-subtitle">锁定单个渠道分步配置：选定渠道 → 渠道策略 → 规则编排 → 上线校验</p>
      </div>
      <el-button @click="backToList">返回渠道档案</el-button>
    </div>

    <!-- 渠道锁定 banner -->
    <div v-if="channelCode" class="loan-card wiz-channel-banner">
      <span class="wiz-channel-label">当前渠道</span>
      <span class="wiz-channel-name">{{ currentChannelName }}</span>
      <span class="loan-tag loan-tag-info">{{ channelCode }}</span>
      <el-button link type="primary" style="margin-left: auto" @click="changeChannel">更换渠道</el-button>
    </div>

    <el-steps :active="step" finish-status="success" align-center class="wiz-steps">
      <el-step title="选定渠道" description="锁定配置对象" @click.native="tryGoStep(0)" />
      <el-step title="渠道策略" description="产品 × 客群 → 计划" @click.native="tryGoStep(1)" />
      <el-step title="规则编排" description="模块 / 步骤 / 规则" @click.native="tryGoStep(2)" />
      <el-step title="上线校验" description="校验并开启" @click.native="tryGoStep(3)" />
    </el-steps>

    <!-- Step0 选定渠道 -->
    <div v-show="step === 0" class="loan-card">
      <el-table :data="channels" stripe row-key="channelCode" class="wiz-pick-table" @row-click="pickChannel">
        <el-table-column prop="bankName" label="银行渠道" min-width="200" />
        <el-table-column prop="channelCode" label="渠道编码" min-width="160" />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <AppTableActions :actions="[{ key: 'pick', label: '选择此渠道', onClick: () => pickChannel(row) }]" />
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- Step1 渠道策略 -->
    <div v-show="step === 1" class="loan-card">
      <div class="wiz-step-head">
        <span class="wiz-step-title">该渠道下的准入策略</span>
        <el-button type="primary" size="small" @click="openStrategyDialog()">新增策略</el-button>
        <el-button size="small" @click="openImportDialog()">从其他渠道复制</el-button>
        <el-button size="small" @click="openTemplateImportDialog()">从模版导入</el-button>
      </div>
      <el-table :data="strategies" v-loading="loading" stripe row-key="strategyCode">
        <el-table-column label="产品" min-width="160">
          <template #default="{ row }">{{ productName(row.bankProductCode) }}</template>
        </el-table-column>
        <el-table-column label="客群" width="90">
          <template #default="{ row }"><DictTag type="customerGroup" :value="row.customerGroup" /></template>
        </el-table-column>
        <el-table-column prop="strategyCode" label="策略编码" min-width="150" show-overflow-tooltip />
        <el-table-column prop="strategyName" label="策略名称" min-width="150" show-overflow-tooltip />
        <el-table-column label="执行计划" min-width="150">
          <template #default="{ row }">{{ planName(row.executionPlanCode) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <span class="loan-tag" :class="row.status === 'ACTIVE' ? 'loan-tag-success' : 'loan-tag-muted'">{{ row.status === 'ACTIVE' ? '已上线' : '草稿' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <AppTableActions :actions="strategyActions(row)" />
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- Step2 规则编排 -->
    <div v-show="step === 2" class="loan-card">
      <div class="wiz-step-head">
        <template v-if="editStrategy">
          <span class="wiz-step-title">编排执行计划：{{ editStrategy.strategyName }}</span>
          <span class="loan-tag loan-tag-muted">{{ editStrategy.executionPlanCode ? planName(editStrategy.executionPlanCode) : '未绑定计划' }}</span>
          <el-button v-if="editStrategy?.executionPlanCode" size="small" @click="onSaveAsTemplate">另存为模版</el-button>
          <el-button link type="primary" size="small" @click="editStrategyCode = ''">更换策略</el-button>
        </template>
        <template v-else>
          <span class="wiz-step-title">选择策略编排执行计划</span>
          <el-select v-model="editStrategyCode" placeholder="选择策略" filterable style="width: 300px" @change="loadOrchestration">
            <el-option v-for="s in strategies" :key="s.strategyCode" :label="s.strategyName" :value="s.strategyCode" />
          </el-select>
        </template>
      </div>

      <template v-if="editStrategy && !editStrategy.executionPlanCode">
        <el-empty description="该策略尚未绑定执行计划">
          <el-button type="primary" @click="openCreatePlanForStrategy">新建计划并绑定</el-button>
        </el-empty>
      </template>
      <template v-else-if="editStrategy">
        <div class="orch-layout">
          <!-- 左侧模块导航 -->
          <div class="orch-nav">
            <div class="orch-nav-head">
              <span>模块列表</span>
              <el-button v-if="!readOnly" link type="primary" size="small" @click="openModuleDialog()">+ 添加</el-button>
            </div>
            <div class="orch-nav-list">
              <div
                v-for="m in modules"
                :key="m.id"
                class="orch-nav-item"
                :class="{ active: activeModuleId === m.id }"
                @click="activeModuleId = m.id"
              >
                <span class="mod-order">M{{ m.sort }}</span>
                <span class="mod-name">{{ m.moduleName }}</span>
                <span v-if="m.isGlobalPre" class="loan-tag loan-tag-danger mod-tag">全局</span>
              </div>
              <el-empty v-if="!modules.length" description="暂无模块" :image-size="60" />
            </div>
          </div>

          <!-- 右侧步骤编辑 -->
          <div class="orch-editor">
            <template v-if="activeModule">
              <div class="orch-editor-head">
                <span class="orch-editor-title">
                  <span class="module-order" style="margin-right:8px">M{{ activeModule.sort }}</span>
                  {{ activeModule.moduleName }}
                </span>
                <span class="loan-tag" :class="activeModule.logicType === 'OR' ? 'loan-tag-warning' : 'loan-tag-info'" :title="activeModule.logicType === 'OR' ? '模块内遇PASS即短路后续步骤' : '模块内遇FAIL即短路后续步骤'">模块内 {{ activeModule.logicType }}</span>
                <span v-if="activeModule.joinWithNextModule === 'OR'" class="loan-tag loan-tag-warning" title="与下一模块为 OR 关系">↓ 或</span>
                <span v-else-if="hasNextModule(activeModule)" class="loan-tag loan-tag-info" title="与下一模块为 AND 关系">↓ 且</span>
                <span v-if="activeModule.isGlobalPre" class="loan-tag loan-tag-danger">全局风控</span>
                <div class="module-actions" style="margin-left:auto">
                  <el-button link type="primary" @click="openModuleDialog(activeModule)">编辑</el-button>
                  <el-button link type="danger" @click="onDeleteModule(activeModule)">删除</el-button>
                </div>
              </div>

              <el-table :data="activeModule.steps || []" size="small" stripe style="flex:1">
                <el-table-column prop="stepSort" label="序" width="48" />
                <el-table-column label="规则" min-width="160">
                  <template #default="{ row }">
                    <span :title="row.ruleCode">{{ row.ruleName || row.ruleCode }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="条件" min-width="160">
                  <template #default="{ row }">
                    <span :class="{ 'step-condition': true, muted: !row.conditionOperator }">
                      {{ row.conditionOperator ? `${row.conditionField} ${row.conditionOperator} ${row.conditionValue || ''}` : '—' }}
                    </span>
                  </template>
                </el-table-column>
                <el-table-column label="与下一步" width="80">
                  <template #default="{ row }">
                    {{ row.joinWithNext === 'OR' ? '或' : row.joinWithNext === 'AND' ? '且' : '—' }}
                  </template>
                </el-table-column>
                <el-table-column label="空跑" width="60">
                  <template #default="{ row }">
                    <span v-if="row.isDryRun === 1" class="loan-tag loan-tag-warning" style="font-size:11px">空跑</span>
                    <span v-else>—</span>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="130" fixed="right">
                  <template #default="{ row }">
                    <AppTableActions :actions="[
                      { key: 'edit', label: '编辑', onClick: () => openStepDialog(activeModule, row) },
                      { key: 'delete', label: '删除', type: 'danger', confirm: '确认删除该步骤？', onClick: () => onDeleteStep(activeModule, row) },
                    ]" />
                  </template>
                </el-table-column>
              </el-table>

              <div style="margin-top:12px">
                <el-button link type="primary" @click="openStepDialog(activeModule)">+ 添加步骤</el-button>
              </div>
            </template>
            <el-empty v-else description="请从左侧选择模块" />
          </div>
        </div>
      </template>
      <el-empty v-else description="请选择一条策略进行编排" />
    </div>

    <!-- Step3 上线校验 -->
    <div v-show="step === 3" class="loan-card">
      <div class="wiz-step-head">
        <span class="wiz-step-title">上线前校验</span>
      </div>
      <el-table :data="strategies" stripe row-key="strategyCode">
        <el-table-column prop="strategyName" label="策略名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="执行计划" min-width="150">
          <template #default="{ row }">{{ planName(row.executionPlanCode) }}</template>
        </el-table-column>
        <el-table-column label="校验结果" min-width="200">
          <template #default="{ row }">
            <template v-if="validateMap[row.strategyCode] === undefined">未校验</template>
            <template v-else-if="!validateMap[row.strategyCode].length"><span class="loan-tag loan-tag-success">通过</span></template>
            <template v-else>
              <el-tooltip :content="validateMap[row.strategyCode].join('；')" placement="top">
                <span class="loan-tag loan-tag-danger">未通过（{{ validateMap[row.strategyCode].length }}）</span>
              </el-tooltip>
            </template>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <span class="loan-tag" :class="row.status === 'ACTIVE' ? 'loan-tag-success' : 'loan-tag-muted'">{{ row.status === 'ACTIVE' ? '已上线' : '草稿' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <AppTableActions :actions="[
              { key: 'validate', label: '校验', onClick: () => onValidate(row) },
              ...(row.status !== 'ACTIVE' ? [{ key: 'enable', label: '上线', type: 'success', onClick: () => onEnable(row) }] : []),
            ]" />
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 底部固定导航 -->
    <div class="wiz-nav">
      <el-button plain :disabled="step === 0" @click="previousStep">
        <AppIcon name="arrowLeft" :size="14" />
        上一步
      </el-button>
      <el-button v-if="step === 1 || step === 2" @click="saveCurrentStep">
        <AppIcon name="save" :size="14" />
        保存草稿
      </el-button>
      <el-button v-if="step < 3" type="primary" @click="nextStep">
        下一步
        <AppIcon name="arrowRight" :size="14" />
      </el-button>
    </div>

    <!-- 策略弹窗 -->
    <AppDialog v-model:visible="strategyDialog.visible" :title="strategyDialog.title" width="520px" :loading="strategyDialog.saving" @confirm="onSaveStrategy">
      <el-form ref="strategyFormRef" :model="strategyDialog.form" :rules="strategyRules" label-width="90px">
        <el-form-item label="渠道">
          <el-input :model-value="channelCode" disabled />
        </el-form-item>
        <el-form-item label="产品" prop="bankProductCode">
          <RemoteProductSelect v-model="strategyDialog.form.bankProductCode" :customer-group="strategyDialog.form.customerGroup" />
        </el-form-item>
        <el-form-item label="客群" prop="customerGroup">
          <el-select v-model="strategyDialog.form.customerGroup" style="width: 100%">
            <el-option label="企业" value="ENTERPRISE" />
            <el-option label="个人" value="PERSONAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="策略编码" prop="strategyCode">
          <el-input v-model="strategyDialog.form.strategyCode" placeholder="渠道内唯一" :disabled="strategyDialog.editing" />
        </el-form-item>
        <el-form-item label="策略名称" prop="strategyName">
          <el-input v-model="strategyDialog.form.strategyName" />
        </el-form-item>
        <el-form-item label="执行计划" prop="executionPlanCode">
          <el-select v-model="strategyDialog.form.executionPlanCode" placeholder="选择计划(1:1)" filterable style="width: 100%">
            <el-option v-for="p in plans" :key="p.planCode" :label="`${p.planName}（${p.planCode}）`" :value="p.planCode" />
          </el-select>
        </el-form-item>
      </el-form>
    </AppDialog>

    <!-- 计划弹窗 -->
    <AppDialog v-model:visible="planDialog.visible" :title="planDialog.title" :loading="planDialog.saving" @confirm="onSavePlan">
      <el-form ref="planFormRef" :model="planDialog.form" :rules="planRules" label-width="90px">
        <el-form-item label="计划编码" prop="planCode"><el-input v-model="planDialog.form.planCode" placeholder="唯一业务编码" /></el-form-item>
        <el-form-item label="计划名称" prop="planName"><el-input v-model="planDialog.form.planName" /></el-form-item>
        <el-form-item label="版本"><el-input-number v-model="planDialog.form.version" :min="1" /></el-form-item>
      </el-form>
    </AppDialog>

    <!-- 模块弹窗 -->
    <AppDialog v-model:visible="moduleDialog.visible" :title="moduleDialog.title" :loading="moduleDialog.saving" @confirm="onSaveModule">
      <el-form ref="moduleFormRef" :model="moduleDialog.form" :rules="moduleRules" label-width="90px">
        <el-form-item label="模块编码" prop="moduleCode"><el-input v-model="moduleDialog.form.moduleCode" placeholder="模块内唯一" /></el-form-item>
        <el-form-item label="模块名称" prop="moduleName"><el-input v-model="moduleDialog.form.moduleName" /></el-form-item>
        <el-form-item label="逻辑">
          <el-select v-model="moduleDialog.form.logicType" style="width: 100%">
            <el-option label="AND（模块内遇FAIL短路）" value="AND" />
            <el-option label="OR（模块内遇PASS短路）" value="OR" />
          </el-select>
        </el-form-item>
        <el-form-item label="连接下模块">
          <el-select v-model="moduleDialog.form.joinWithNextModule" style="width: 100%">
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
    <AppDialog v-model:visible="stepDialog.visible" :title="stepDialog.title" :loading="stepDialog.saving" @confirm="onSaveStep">
      <el-form ref="stepFormRef" :model="stepDialog.form" :rules="stepRules" label-width="100px">
        <el-form-item label="规则" prop="ruleId">
          <el-select v-model="stepDialog.form.ruleId" placeholder="选择规则" filterable style="width: 100%">
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
        <p class="condition-hint">运行时若条件不满足，该步骤将标记为 SKIP（跳过）并继续后续步骤，不会阻断链路。</p>
      </el-form>
    </AppDialog>

    <!-- 从其他渠道复制弹窗 -->
    <AppDialog v-model:visible="importDialog.visible" title="从其他渠道复制策略" :loading="importDialog.saving" @confirm="onImportFromChannel">
      <el-form label-width="110px">
        <el-form-item label="源渠道">
          <el-select v-model="importDialog.sourceChannel" placeholder="选择源渠道" filterable style="width: 100%" @change="onSourceChannelChange">
            <el-option v-for="c in sourceChannels" :key="c.channelCode" :label="`${c.bankName}（${c.channelCode}）`" :value="c.channelCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="源策略">
          <RemoteStrategySelect v-model="importDialog.sourceStrategyCode" :channel-code="importDialog.sourceChannel" />
        </el-form-item>
        <el-form-item label="目标策略编码" required>
          <el-input v-model="importDialog.targetStrategyCode" placeholder="当前渠道内唯一" />
        </el-form-item>
        <p class="condition-hint">将复制源策略及其绑定的执行计划树（模块/步骤/规则），产品与客群沿用源策略，复制后为草稿状态。</p>
      </el-form>
    </AppDialog>

    <!-- 从模版导入弹窗 -->
    <AppDialog v-model:visible="tplImportDialog.visible" title="从策略模版导入" :loading="tplImportDialog.saving" @confirm="onTplImport">
      <el-form ref="tplImportFormRef" :model="tplImportDialog.form" :rules="tplImportRules" label-width="90px">
        <el-form-item label="模版" prop="templateCode">
          <RemoteTemplateSelect v-model="tplImportDialog.form.templateCode" :customer-group="tplImportDialog.form.customerGroup" @selected="selectedTemplate = $event" />
        </el-form-item>
        <el-form-item label="产品" prop="bankProductCode">
          <RemoteProductSelect v-model="tplImportDialog.form.bankProductCode" :customer-group="tplImportDialog.form.customerGroup" />
        </el-form-item>
        <el-form-item label="策略编码" prop="strategyCode">
          <el-input v-model="tplImportDialog.form.strategyCode" placeholder="当前渠道内唯一" />
        </el-form-item>
        <el-form-item label="策略名称">
          <el-input v-model="tplImportDialog.form.strategyName" placeholder="缺省用模版名" />
        </el-form-item>
        <el-form-item label="客群">
          <el-select v-model="tplImportDialog.form.customerGroup" style="width: 100%">
            <el-option label="企业" value="ENTERPRISE" />
            <el-option label="个人" value="PERSONAL" />
          </el-select>
        </el-form-item>
        <p class="condition-hint">将已上线模版的模块/步骤/规则实例化为本渠道的执行计划并绑定为新策略，复制后为草稿状态。</p>
      </el-form>
    </AppDialog>
  </div>
</template>

<script setup>
defineOptions({ name: '_channel_config_wizard' });
import { ref, reactive, computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import DictTag from '@/components/DictTag.vue';
import AppTableActions from '@/components/AppTableActions.vue';
import AppDialog from '@/components/AppDialog.vue';
import RemoteProductSelect from '@/components/RemoteProductSelect.vue';
import RemoteStrategySelect from '@/components/RemoteStrategySelect.vue';
import RemoteTemplateSelect from '@/components/RemoteTemplateSelect.vue';
import AppIcon from '@/components/AppIcon.vue';
import { appConfirm } from '@/utils/confirm';
import { listChannels } from '@/api/channel';
import { listPlans, planDetail, createPlan, updatePlan, deletePlan, createModule, updateModule, deleteModule, createStep, updateStep, deleteStep, saveAsTemplate } from '@/api/plan';
import { listRules } from '@/api/rule';
import { pageStrategy, createStrategy, updateStrategy, deleteStrategy, enableStrategy, disableStrategy, validateStrategy, importFromChannel, importFromTemplate } from '@/api/channelStrategy';

const route = useRoute();
const router = useRouter();

const step = ref(0);
const maxCompletedStep = ref(0);
const channelCode = ref(route.query.channelCode || '');
const channels = ref([]);
const plans = ref([]);
const rules = ref([]);
const strategies = ref([]);
const selectedTemplate = ref(null);
const loading = ref(false);
const validateMap = ref({});

const OPERATORS = [
  { value: 'EQ', label: '= 等于' },
  { value: 'NE', label: '≠ 不等于' },
  { value: 'IN', label: 'IN 属于' },
  { value: 'NOT_IN', label: 'NOT IN 不属于' },
  { value: 'IS_BLANK', label: 'IS BLANK 为空' },
  { value: 'IS_NOT_BLANK', label: 'IS NOT BLANK 非空' },
];

const currentChannelName = computed(() => channels.value.find((c) => c.channelCode === channelCode.value)?.bankName || '');

function productName(code) { return strategies.value.find((p) => p.bankProductCode === code)?.bankProductName || '产品信息待补充'; }
function planName(code) { return plans.value.find((p) => p.planCode === code)?.planName || code || '-'; }
/** 计划编码作为计划主资源定位；模块/步骤仍使用后端返回的内部 FK。 */
function planIdOf(code) { return plans.value.find((p) => p.planCode === code)?.id || null; }

function backToList() { router.push('/channel-config'); }

/** 能否跳转到指定步骤 */
function canNavigateToStep(targetStep) {
  if (targetStep === 0) return true;
  if (!channelCode.value) return false;
  return targetStep <= maxCompletedStep.value + 1;
}

/** 尝试跳转到指定步骤 */
function tryGoStep(targetStep) {
  if (!canNavigateToStep(targetStep)) {
    const nextRequired = Math.min(maxCompletedStep.value + 1, 3);
    const titles = ['选定渠道', '渠道策略', '规则编排', '上线校验'];
    ElMessage.warning(`请先完成「${titles[nextRequired]}」后再进入后续步骤`);
    return;
  }
  step.value = targetStep;
  // 进入上线校验步骤时，自动对全部策略执行校验（填充 validateMap）
  if (targetStep === 3) {
    autoValidateAll();
  }
}

/** 上一步 */
function previousStep() {
  if (step.value > 0) step.value--;
}

/** 下一步：保存当前步骤后推进 */
async function nextStep() {
  if (step.value === 0) {
    if (!channelCode.value) {
      ElMessage.warning('请先选择渠道');
      return;
    }
    step.value = 1;
    return;
  }
  if (step.value === 1) {
    // 渠道策略步骤：无需强制保存，直接进入编排
    if (!editStrategy.value) {
      ElMessage.warning('请先选择一条策略进行编排');
      return;
    }
    step.value = 2;
    return;
  }
  if (step.value === 2) {
    // 规则编排步骤：校验是否有模块/步骤
    if (!modules.value.length) {
      ElMessage.warning('请至少配置一个模块');
      return;
    }
    step.value = 3;
    return;
  }
}

/** 保存当前步骤 */
async function saveCurrentStep() {
  if (step.value === 1) {
    // Step 1 保存草稿：实际就是当前策略列表状态，无需额外保存
    ElMessage.success('草稿已保存');
    return true;
  }
  if (step.value === 2) {
    // Step 2 保存编排：可另存为模版
    if (editStrategy.value?.executionPlanCode) {
      ElMessage.success('编排已自动保存');
    }
    return true;
  }
  return true;
}

function changeChannel() {
  channelCode.value = '';
  step.value = 0;
  // 清空渠道相关旧数据，避免切换后残留上一渠道的策略/编排
  strategies.value = [];
  modules.value = [];
  editStrategyCode.value = '';
  strategyDialog.visible = false;
}

async function loadStrategies() {
  loading.value = true;
  try {
    strategies.value = await loadAllStrategies(channelCode.value);
  } finally {
    loading.value = false;
  }
  // 根据实际策略状态推算步骤完成度
  recalcMaxCompletedStep();
}

async function loadAllStrategies(selectedChannelCode) {
  const records = [];
  let page = 1;
  while (true) {
    const res = await pageStrategy({ channelCode: selectedChannelCode, page, size: 100 });
    const payload = res.data || {};
    records.push(...(payload.records || []));
    if (records.length >= Number(payload.total || 0) || !(payload.records || []).length) return records;
    page += 1;
  }
}

function pickChannel(row) {
  channelCode.value = row.channelCode;
  step.value = 1;
  loadStrategies();
}

// Step1 策略
function strategyActions(row) {
  const active = row.status === 'ACTIVE';
  return [
    { key: 'edit', label: '编辑', disabled: active, onClick: () => openStrategyDialog(row) },
    { key: 'orch', label: '编排', onClick: () => gotoOrchestration(row) },
    active
      ? { key: 'disable', label: '下线', type: 'warning', confirm: `确认下线「${row.strategyName}」？`, onClick: () => onDisable(row) }
      : { key: 'enable', label: '上线', type: 'success', onClick: () => onEnable(row) },
    { key: 'del', label: '删除', type: 'danger', confirm: `确认删除「${row.strategyName}」？`, onClick: () => onDelete(row) },
  ];
}

async function onEnable(row) {
  // 上线前先执行准入校验（mds v2：写锁 / 上线校验），校验失败弹窗展示明细并中止
  try {
    const res = await validateStrategy(row.strategyCode);
    const problems = res.data || [];
    if (problems.length) {
      ElMessageBox.alert(
        problems.map((p) => `· ${p}`).join('<br/>'),
        `「${row.strategyName}」未通过上线校验`,
        { confirmButtonText: '知道了', type: 'warning', dangerouslyUseHTMLString: true },
      );
      return;
    }
    await enableStrategy(row.strategyCode);
    ElMessage.success('已上线');
    loadStrategies();
  } catch (e) { /* 拦截器已提示 */ }
}
async function onDisable(row) { await disableStrategy(row.strategyCode); ElMessage.success('已下线'); loadStrategies(); }
async function onDelete(row) { await deleteStrategy(row.strategyCode); ElMessage.success('已删除'); loadStrategies(); }

// 从其他渠道复制
const importDialog = reactive({ visible: false, saving: false, sourceChannel: '', sourceStrategyCode: '', targetStrategyCode: '' });
const sourceChannels = computed(() => channels.value.filter((c) => c.channelCode !== channelCode.value));
function openImportDialog() {
  importDialog.sourceChannel = '';
  importDialog.sourceStrategyCode = '';
  importDialog.targetStrategyCode = '';
  importDialog.visible = true;
}
async function onSourceChannelChange() {
  importDialog.sourceStrategyCode = '';
  if (!importDialog.sourceChannel) return;
}
async function onImportFromChannel() {
  if (!importDialog.sourceChannel) { ElMessage.warning('请选择源渠道'); return; }
  if (!importDialog.sourceStrategyCode) { ElMessage.warning('请选择源策略'); return; }
  if (!importDialog.targetStrategyCode?.trim()) { ElMessage.warning('请输入目标渠道内的策略编码'); return; }
  importDialog.saving = true;
  try {
    await importFromChannel({
      sourceStrategyCode: importDialog.sourceStrategyCode,
      targetChannelCode: channelCode.value,
      targetStrategyCode: importDialog.targetStrategyCode.trim(),
    });
    ElMessage.success('已从其他渠道复制策略（含执行计划树）');
    importDialog.visible = false;
    loadStrategies();
  } catch (e) { /* 拦截器已提示 */ } finally {
    importDialog.saving = false;
  }
}

// 从策略模版导入（模版 → 渠道策略，对齐 mds v2 import-from-template）
const tplImportDialog = reactive({ visible: false, saving: false, form: { templateCode: '', bankProductCode: '', strategyCode: '', strategyName: '', customerGroup: 'ENTERPRISE' } });
const tplImportFormRef = ref();
const tplImportRules = {
  templateCode: [{ required: true, message: '请选择策略模版', trigger: 'change' }],
  bankProductCode: [{ required: true, message: '请选择产品', trigger: 'change' }],
  strategyCode: [{ required: true, message: '请输入策略编码', trigger: 'blur' }],
};
async function openTemplateImportDialog() {
  Object.assign(tplImportDialog.form, { templateCode: '', bankProductCode: '', strategyCode: '', strategyName: '', customerGroup: 'ENTERPRISE' });
  selectedTemplate.value = null;
  tplImportFormRef.value?.clearValidate();
  tplImportDialog.visible = true;
}
async function onTplImport() {
  try {
    await tplImportFormRef.value.validate();
  } catch (e) {
    return;
  }
  tplImportDialog.saving = true;
  try {
    const form = { ...tplImportDialog.form };
    if (!form.strategyName?.trim()) {
      const tpl = selectedTemplate.value;
      form.strategyName = tpl?.templateName || form.strategyCode;
    }
    await importFromTemplate({
      templateCode: form.templateCode,
      channelCode: channelCode.value,
      strategyCode: form.strategyCode,
      strategyName: form.strategyName,
      bankProductCode: form.bankProductCode,
      customerGroup: form.customerGroup,
    });
    ElMessage.success('已从模版导入为渠道准入策略');
    tplImportDialog.visible = false;
    loadStrategies();
  } catch (e) { /* 拦截器已提示 */ } finally {
    tplImportDialog.saving = false;
  }
}

const strategyDialog = reactive({ visible: false, title: '', saving: false, editing: false, editingId: null, form: { bankProductCode: '', customerGroup: 'ENTERPRISE', strategyCode: '', strategyName: '', executionPlanCode: '' } });
const strategyFormRef = ref();
const strategyRules = {
  bankProductCode: [{ required: true, message: '请选择产品', trigger: 'change' }],
  customerGroup: [{ required: true, message: '请选择客群', trigger: 'change' }],
  strategyCode: [{ required: true, message: '请输入策略编码', trigger: 'blur' }],
  strategyName: [{ required: true, message: '请输入策略名称', trigger: 'blur' }],
  executionPlanCode: [{ required: true, message: '请选择执行计划', trigger: 'change' }],
};
function openStrategyDialog(row) {
  strategyDialog.title = row ? '编辑策略' : '新增策略';
  strategyDialog.editing = !!row;
  strategyDialog.editingId = row?.strategyCode || '';
  Object.assign(strategyDialog.form, row
    ? { bankProductCode: row.bankProductCode, customerGroup: row.customerGroup, strategyCode: row.strategyCode, strategyName: row.strategyName, executionPlanCode: row.executionPlanCode }
    : { bankProductCode: '', customerGroup: 'ENTERPRISE', strategyCode: '', strategyName: '', executionPlanCode: '' });
  strategyFormRef.value?.clearValidate();
  strategyDialog.visible = true;
}
async function onSaveStrategy() {
  try {
    await strategyFormRef.value.validate();
  } catch (e) {
    return;
  }
  strategyDialog.saving = true;
  try {
    const payload = { channelCode: channelCode.value, ...strategyDialog.form };
    if (strategyDialog.editing) await updateStrategy(strategyDialog.editingId, payload);
    else await createStrategy(payload);
    ElMessage.success('已保存');
    strategyDialog.visible = false;
    loadStrategies();
  } finally { strategyDialog.saving = false; }
}

// Step2 编排
const editStrategyCode = ref('');
const editStrategy = computed(() => strategies.value.find((s) => s.strategyCode === editStrategyCode.value) || null);
const modules = ref([]);
const activeModuleId = ref(null);
const activeModule = computed(() => modules.value.find((m) => m.id === activeModuleId.value) || null);
function gotoOrchestration(row) { editStrategyCode.value = row.strategyCode; step.value = 2; loadOrchestration(); }
/** 判断模块是否为当前编排中的最后一个模块（用于控制 joinWithNextModule 标签显示） */
function hasNextModule(m) {
  const idx = modules.value.findIndex((mod) => mod.id === m.id);
  return idx >= 0 && idx < modules.value.length - 1;
}
async function loadOrchestration() {
  if (!editStrategy.value?.executionPlanCode) { modules.value = []; activeModuleId.value = null; return; }
  const planCode = editStrategy.value.executionPlanCode;
  if (!planCode) { modules.value = []; activeModuleId.value = null; return; }
  const res = await planDetail(planCode);
  modules.value = res.data?.modules || [];
  activeModuleId.value = modules.value.length ? modules.value[0].id : null;
}

/** 将当前策略绑定的执行计划另存为模版草稿（对齐 mds v2 save-as-template） */
async function onSaveAsTemplate() {
  if (!editStrategy.value?.executionPlanCode) return;
  const planCode = editStrategy.value.executionPlanCode;
  if (!planCode) { ElMessage.warning('未找到该策略绑定的计划'); return; }
  try {
    const { value } = await ElMessageBox.prompt(
      '输入模版编码（需唯一）与名称，将当前策略的执行计划保存为策略模版草稿',
      '另存为模版',
      {
        inputPlaceholder: '如 TPL_ENT_TAX_V1',
        inputValidator: (v) => (v && v.trim().length >= 2 ? true : '模版编码至少 2 个字符'),
        confirmButtonText: '保存',
        cancelButtonText: '取消',
      },
    );
    await saveAsTemplate({
      planCode,
      templateCode: value.trim(),
      templateName: `${editStrategy.value.strategyName}-模版`,
    });
    ElMessage.success('已另存为模版草稿');
  } catch { /* 取消或拦截器已提示 */ }
}

async function openCreatePlanForStrategy() {
  planDialog.mode = 'bind';
  Object.assign(planDialog.form, { planCode: '', planName: '', version: 1 });
  planDialog.visible = true;
}

const planDialog = reactive({ visible: false, title: '新建计划', saving: false, mode: 'standalone', form: { planCode: '', planName: '', version: 1 } });
const planFormRef = ref();
const planRules = {
  planCode: [{ required: true, message: '请输入计划编码', trigger: 'blur' }],
  planName: [{ required: true, message: '请输入计划名称', trigger: 'blur' }],
};
async function onSavePlan() {
  try {
    await planFormRef.value.validate();
  } catch (e) {
    return;
  }
  planDialog.saving = true;
  try {
    const res = await createPlan({ ...planDialog.form });
    const createdPlanCode = res.data;
    if (planDialog.mode === 'bind' && editStrategy.value) {
      const planCode = createdPlanCode || planDialog.form.planCode;
      if (!planCode) { ElMessage.error('计划已创建，但未能获取计划编码，请到「计划编排」页查看'); return; }
      await updateStrategy(editStrategy.value.strategyCode, { executionPlanCode: planCode });
      ElMessage.success('已创建计划并绑定');
      loadStrategies();
      loadOrchestration();
    } else {
      ElMessage.success('已创建计划');
    }
    planDialog.visible = false;
    loadPlans();
  } finally { planDialog.saving = false; }
}

const moduleDialog = reactive({ visible: false, title: '', saving: false, editingId: null, form: { moduleCode: '', moduleName: '', logicType: 'AND', joinWithNextModule: 'AND', isGlobalPre: 0, sort: 0 } });
const moduleFormRef = ref();
const moduleRules = {
  moduleCode: [{ required: true, message: '请输入模块编码', trigger: 'blur' }],
  moduleName: [{ required: true, message: '请输入模块名称', trigger: 'blur' }],
};
function openModuleDialog(m) {
  moduleDialog.title = m ? '编辑模块' : '添加模块';
  moduleDialog.editingId = m?.id || null;
  Object.assign(moduleDialog.form, m
    ? { moduleCode: m.moduleCode, moduleName: m.moduleName, logicType: m.logicType, joinWithNextModule: m.joinWithNextModule || 'AND', isGlobalPre: m.isGlobalPre || 0, sort: m.sort }
    : { moduleCode: '', moduleName: '', logicType: 'AND', joinWithNextModule: 'AND', isGlobalPre: 0, sort: 0 });
  moduleFormRef.value?.clearValidate();
  moduleDialog.visible = true;
}
async function onSaveModule() {
  try {
    await moduleFormRef.value.validate();
  } catch (e) {
    return;
  }
  moduleDialog.saving = true;
  try {
    const payload = { planId: planIdOf(editStrategy.value.executionPlanCode), ...moduleDialog.form };
    if (moduleDialog.editingId) await updateModule(moduleDialog.editingId, payload);
    else await createModule(payload);
    ElMessage.success('已保存');
    moduleDialog.visible = false;
    loadOrchestration();
  } finally { moduleDialog.saving = false; }
}
async function onDeleteModule(m) {
  try { await appConfirm(`确认删除模块「${m.moduleName}」？（将级联删除步骤）`); } catch { return; }
  await deleteModule(m.id);
  loadOrchestration();
}

// 步骤弹窗（添加/编辑）
function emptyStepForm() {
  return { ruleId: null, stepSort: 0, joinWithNext: 'AND', isDryRun: 0, conditionField: '', conditionOperator: '', conditionValue: '' };
}
const stepDialog = reactive({ visible: false, title: '添加步骤', saving: false, editingId: null, moduleId: null, form: emptyStepForm() });
const stepFormRef = ref();
const stepRules = {
  ruleId: [{ required: true, message: '请选择规则', trigger: 'change' }],
};

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
    // 运算符为空时清空条件字段/值，避免脏数据
    if (!form.conditionOperator) {
      form.conditionField = '';
      form.conditionValue = '';
    }
    if (stepDialog.editingId) {
      await updateStep(stepDialog.editingId, form);
      ElMessage.success('已保存');
    } else {
      await createStep({ moduleId: stepDialog.moduleId, ...form });
      ElMessage.success('已添加');
    }
    stepDialog.visible = false;
    loadOrchestration();
  } finally { stepDialog.saving = false; }
}
async function onDeleteStep(m, s) {
  try { await appConfirm(`确认删除步骤「${s.ruleName}」？`); } catch { return; }
  await deleteStep(s.id);
  loadOrchestration();
}

// Step3 校验
async function goValidate() { step.value = 3; }

/** 自动对全部策略执行校验（进入上线校验步骤时调用，或已上线策略初始化） */
async function autoValidateAll() {
  if (!strategies.value.length) return;
  // 并发校验所有策略
  const results = await Promise.allSettled(
    strategies.value.map((s) => validateStrategy(s.strategyCode).then((res) => ({ strategyCode: s.strategyCode, problems: res.data || [] })).catch(() => ({ strategyCode: s.strategyCode, problems: ['校验请求失败'] }))),
  );
  const map = {};
  results.forEach((r) => {
    if (r.status === 'fulfilled') {
      map[r.value.strategyCode] = r.value.problems;
    }
  });
  validateMap.value = map;
}

/** 根据策略实际状态推算最大已完成步骤 */
function recalcMaxCompletedStep() {
  if (!channelCode.value) { maxCompletedStep.value = 0; return; }
  // 有任一策略已上线 → 全部步骤视为可访问（用户可能从渠道列表直接跳到校验页）
  const hasActive = strategies.value.some((s) => s.status === 'ACTIVE');
  if (hasActive) {
    maxCompletedStep.value = 3;
    return;
  }
  // 有策略且绑定了计划 → 至少完成到步骤2（规则编排）
  const hasPlan = strategies.value.some((s) => s.executionPlanCode);
  if (hasPlan) {
    maxCompletedStep.value = 2;
    return;
  }
  // 有策略 → 至少完成到步骤1（渠道策略）
  if (strategies.value.length) {
    maxCompletedStep.value = 1;
    return;
  }
  maxCompletedStep.value = 0;
}

async function onValidate(row) {
  const res = await validateStrategy(row.strategyCode);
  validateMap.value[row.strategyCode] = res.data || [];
  if (!validateMap.value[row.strategyCode].length) ElMessage.success('校验通过');
}

async function loadPlans() {
  try { const res = await listPlans(); plans.value = res.data || []; } catch { plans.value = []; }
}

onMounted(async () => {
  try {
    const [ch, pl, rl] = await Promise.all([
      listChannels(), listPlans(), listRules({ customerGroup: 'ENTERPRISE' }),
    ]);
    channels.value = ch.data || [];
    plans.value = pl.data || [];
    rules.value = rl.data || [];
  } catch { /* 忽略 */ }
  if (channelCode.value) {
    step.value = 1;
    await loadStrategies();
    // 断点续做：URL 携带 strategyCode 时直接定位到该策略的规则编排
    const resumeCode = String(route.query.strategyCode || '');
    if (resumeCode) {
      const target = strategies.value.find((s) => s.strategyCode === resumeCode);
      if (target) gotoOrchestration(target);
    }
    // 已上线渠道：自动填充校验结果
    if (maxCompletedStep.value >= 3) {
      autoValidateAll();
    }
  }
});
</script>

<style scoped>
/* 底部固定导航栏 */
.wiz-nav {
  position: sticky;
  bottom: 0;
  display: flex;
  justify-content: center;
  gap: 12px;
  padding: 16px 0;
  margin-top: 8px;
  background: var(--loan-bg);
  border-top: 1px solid var(--loan-border);
  z-index: 10;
}
.wiz-steps :deep(.el-step__head) { cursor: pointer; }
.wiz-steps :deep(.el-step__title) { cursor: pointer; }

/* Step2 左右布局 */
.orch-layout {
  display: grid;
  grid-template-columns: 260px 1fr;
  gap: 16px;
  min-height: 420px;
}
.orch-nav {
  border: 1px solid var(--loan-border);
  border-radius: var(--loan-radius);
  background: var(--loan-card-bg);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.orch-nav-head {
  padding: 12px 14px;
  border-bottom: 1px solid var(--loan-border);
  font-weight: 600;
  font-size: 14px;
  color: var(--loan-text);
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.orch-nav-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}
.orch-nav-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  margin-bottom: 4px;
  transition: background var(--loan-transition), color var(--loan-transition);
  color: var(--loan-text);
  font-size: 13px;
}
.orch-nav-item:hover {
  background: color-mix(in srgb, var(--loan-primary) 8%, transparent);
}
.orch-nav-item.active {
  background: linear-gradient(90deg, color-mix(in srgb, var(--loan-primary) 18%, transparent) 0%, color-mix(in srgb, var(--loan-primary) 8%, transparent) 100%);
  color: var(--loan-primary);
  font-weight: 600;
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--loan-primary) 25%, transparent);
}
.orch-nav-item .mod-order {
  font-size: 11px;
  font-weight: 700;
  color: var(--loan-primary);
  background: var(--loan-primary-soft);
  padding: 2px 6px;
  border-radius: 4px;
  flex-shrink: 0;
}
.orch-nav-item.active .mod-order {
  background: var(--loan-primary);
  color: #fff;
}
.orch-nav-item .mod-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.orch-nav-item .mod-tag {
  flex-shrink: 0;
}
.orch-editor {
  border: 1px solid var(--loan-border);
  border-radius: var(--loan-radius);
  background: var(--loan-card-bg);
  padding: 16px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.orch-editor-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--loan-border);
}
.orch-editor-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--loan-text);
}

@media (max-width: 900px) {
  .orch-layout { grid-template-columns: 1fr; }
}

.wiz-channel-banner { display: flex; align-items: center; gap: 12px; padding: 14px 20px; margin-bottom: 16px; }
.wiz-channel-label { color: var(--loan-text-secondary); font-size: 13px; }
.wiz-channel-name { font-size: 18px; font-weight: 600; color: var(--loan-text); }
.wiz-steps { margin: 8px 0 20px; }
.wiz-pick-table { cursor: pointer; }
.wiz-step-head { display: flex; align-items: center; gap: 16px; margin-bottom: 14px; }
.wiz-step-title { font-size: 15px; font-weight: 600; color: var(--loan-text); }
.wiz-step-actions { display: flex; justify-content: flex-end; gap: 12px; margin-top: 16px; }
.orchestration { display: flex; flex-direction: column; gap: 12px; }
.module-card { border: 1px solid var(--loan-border); border-radius: var(--loan-radius); overflow: hidden; }
.module-head { display: flex; align-items: center; gap: 10px; padding: 12px 14px; background: var(--loan-surface); border-bottom: 1px solid var(--loan-border); font-size: 14px; }
.module-order { color: var(--loan-primary); font-size: 12px; font-weight: 700; background: var(--loan-primary-soft); padding: 2px 7px; border-radius: 4px; }
.module-name { font-weight: 600; color: var(--loan-text); }
.module-actions { margin-left: auto; display: flex; gap: 4px; }
.step-list { padding: 8px 14px 8px 36px; display: flex; flex-direction: column; gap: 6px; border-left: 2px solid var(--loan-border); margin-left: 14px; }
.step-row { display: flex; align-items: center; gap: 10px; padding: 4px 0; }
.step-order { color: var(--loan-text-muted); font-size: 12px; min-width: 20px; text-align: right; }
.step-rule { color: var(--loan-text); font-weight: 500; }
.step-code { color: var(--loan-text-muted); font-size: 12px; font-family: monospace; }
.step-join { display: inline-flex; gap: 4px; }
.step-condition { color: var(--loan-text-muted); font-size: 12px; background: var(--loan-surface); padding: 0 6px; border-radius: 4px; font-family: monospace; }
.step-actions { margin-left: auto; display: flex; gap: 4px; }
.condition-hint { margin: 0; font-size: 12px; color: var(--loan-text-muted); }
</style>
