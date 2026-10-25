<template>
  <div class="strategy-page">
    <div class="loan-page-header">
      <div>
        <h2 class="loan-page-title">渠道准入</h2>
        <p class="loan-page-subtitle">渠道 × 产品 × 客群 → 策略 → 计划(1:1)；不同渠道可配置不同执行计划</p>
      </div>
      <el-button type="primary" @click="openCreate">
        <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" style="margin-right: 4px; vertical-align: -2px"><path d="M12 5v14M5 12h14"/></svg>
        新增策略
      </el-button>
    </div>

    <div class="loan-card">
      <AppSearchBar :loading="loading" @search="onSearch" @reset="onReset">
        <el-select v-model="query.channelCode" placeholder="渠道" clearable style="width: 180px">
          <el-option v-for="c in channels" :key="c.channelCode" :label="c.bankName" :value="c.channelCode" />
        </el-select>
        <el-select v-model="query.customerGroup" placeholder="客群" clearable style="width: 130px">
          <el-option label="企业" value="ENTERPRISE" />
          <el-option label="个人" value="PERSONAL" />
        </el-select>
        <el-input v-model="query.keyword" placeholder="策略名称" clearable style="width: 200px" @keyup.enter="onSearch" />
      </AppSearchBar>

      <el-table :data="data" v-loading="loading" stripe row-key="strategyCode" style="height: calc(100vh - 320px); min-height: 360px">
        <template #empty>
          <AppEmpty title="暂无策略" desc="新增渠道策略，配置产品与执行计划" />
        </template>
        <el-table-column label="渠道" width="160">
          <template #default="{ row }">{{ channelName(row.channelCode) }}</template>
        </el-table-column>
        <el-table-column label="产品" width="180">
          <template #default="{ row }">{{ productName(row.bankProductCode) }}</template>
        </el-table-column>
        <el-table-column label="客群" width="90">
          <template #default="{ row }">
            <DictTag type="customerGroup" :value="row.customerGroup" />
          </template>
        </el-table-column>
        <el-table-column prop="strategyName" label="策略名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="执行计划" width="180">
          <template #default="{ row }">{{ planName(row.executionPlanCode) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <span class="loan-tag" :class="row.status === 'ACTIVE' ? 'loan-tag-success' : 'loan-tag-muted'">
              {{ row.status === 'ACTIVE' ? '已上线' : '草稿' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <AppTableActions :actions="rowActions(row)" />
          </template>
        </el-table-column>
      </el-table>

      <AppPagination v-model:page="query.page" v-model:size="query.size" :total="total" @change="load" />
    </div>

    <!-- 新增/编辑弹窗 -->
    <AppDialog v-model:visible="dialogVisible" :title="editing ? '编辑策略' : '新增策略'" width="520px" :loading="saving" @confirm="onSave">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px">
        <el-form-item label="渠道" prop="channelCode">
          <AppSelect v-model="form.channelCode" placeholder="选择渠道" style="width: 100%" :disabled="editing">
            <el-option v-for="c in channels" :key="c.channelCode" :label="c.bankName" :value="c.channelCode" />
          </AppSelect>
        </el-form-item>
        <el-form-item label="产品" prop="bankProductCode">
          <RemoteProductSelect v-model="form.bankProductCode" :customer-group="form.customerGroup" />
        </el-form-item>
        <el-form-item label="客群" prop="customerGroup">
          <AppSelect v-model="form.customerGroup" placeholder="选择客群" style="width: 100%">
            <el-option label="企业" value="ENTERPRISE" />
            <el-option label="个人" value="PERSONAL" />
          </AppSelect>
        </el-form-item>
        <el-form-item label="策略编码" prop="strategyCode">
          <el-input v-model="form.strategyCode" placeholder="渠道内唯一，如 WH_BANK_TAX" :disabled="editing" />
        </el-form-item>
        <el-form-item label="策略名称" prop="strategyName">
          <el-input v-model="form.strategyName" placeholder="如 武汉某行企业税贷准入策略（不超过128个字符）" maxlength="128" show-word-limit />
        </el-form-item>
        <el-form-item label="执行计划" prop="executionPlanCode">
          <AppSelect v-model="form.executionPlanCode" placeholder="选择计划(1:1)" style="width: 100%">
            <el-option v-for="p in plans" :key="p.planCode" :label="p.planName || '执行计划名称待补充'" :value="p.planCode" />
          </AppSelect>
        </el-form-item>
      </el-form>
    </AppDialog>

    <AppDialog v-model:visible="flowVisible" :title="`准入流程 · ${flowStrategy?.strategyName || ''}`" width="920px">
      <div v-loading="flowLoading" class="admission-flow">
        <el-alert v-if="flowPlan" type="info" :closable="false" show-icon>
          执行计划：{{ flowPlan.planName || flowPlan.planCode }} · {{ flowPlan.status === 'ACTIVE' ? '已上线' : '草稿' }}
        </el-alert>
        <AppEmpty v-if="!flowLoading && !flowModules.length" title="暂无准入步骤" desc="该策略尚未配置执行计划模块或步骤" />
        <div v-for="(m, index) in flowModules" :key="m.moduleBizCode || m.id || index" class="flow-module">
          <div class="flow-module-head">
            <span class="flow-index">{{ index + 1 }}</span>
            <strong>{{ m.moduleName || m.categoryName || '未命名模块' }}</strong>
            <span class="loan-tag loan-tag-info">{{ m.logicType || 'AND' }}</span>
            <span class="flow-join">与下一模块：{{ m.joinWithNextModule || 'AND' }}</span>
          </div>
          <div v-for="(s, stepIndex) in (m.steps || [])" :key="s.stepCode || s.id || stepIndex" class="flow-step">
            <span class="flow-step-order">{{ s.stepOrder ?? s.stepSort ?? stepIndex + 1 }}</span>
            <span class="flow-step-name">{{ s.ruleName || s.ruleCode || '未命名规则' }}</span>
            <span v-if="s.conditionField || s.conditionOperator || s.conditionValue" class="flow-condition">
              {{ s.conditionField || '条件' }} {{ s.conditionOperator || '' }} {{ s.conditionValue || '' }}
            </span>
            <span class="loan-tag loan-tag-muted">与下一步：{{ s.joinWithNext || 'AND' }}</span>
            <span v-if="s.isDryRun === 1" class="loan-tag loan-tag-warning">空跑</span>
          </div>
        </div>
      </div>
    </AppDialog>
  </div>
</template>

<script setup>
import AppSelect from '@/components/AppSelect.vue';
defineOptions({ name: '_channel_strategy' });
import { ref, reactive, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import DictTag from '@/components/DictTag.vue';
import AppSearchBar from '@/components/AppSearchBar.vue';
import AppPagination from '@/components/AppPagination.vue';
import AppEmpty from '@/components/AppEmpty.vue';
import AppTableActions from '@/components/AppTableActions.vue';
import AppDialog from '@/components/AppDialog.vue';
import RemoteProductSelect from '@/components/RemoteProductSelect.vue';
import { useTable } from '@/composables/useTable';
import { listChannels } from '@/api/channel';
import { listPlans, planDetail } from '@/api/plan';
import {
  pageStrategy, createStrategy, updateStrategy, deleteStrategy, enableStrategy, disableStrategy, validateStrategy,
} from '@/api/channelStrategy';


const channels = ref([]);
const plans = ref([]);
const route = useRoute();
const router = useRouter();

const { loading, data, total, query, load, onSearch, onReset } = useTable(pageStrategy, {
  channelCode: String(route.query.channelCode || ''),
  customerGroup: '',
  keyword: '',
});

function channelName(code) {
  return channels.value.find((c) => c.channelCode === code)?.bankName || '渠道名称待补充';
}
function productName(code) {
  return data.value.find((p) => p.bankProductCode === code)?.bankProductName || '产品信息待补充';
}
function planName(code) {
  return plans.value.find((p) => p.planCode === code)?.planName || '执行计划名称待补充';
}

/** 操作列 */
function rowActions(row) {
  const active = row.status === 'ACTIVE';
  return [
    { key: 'flow', label: '编排计划', type: 'primary', onClick: () => goStrategy(row) },
    { key: 'view', label: '查看流程', onClick: () => openFlow(row) },
    { key: 'edit', label: '编辑', disabled: active, onClick: () => openEdit(row) },
    active
      ? { key: 'disable', label: '下线', type: 'warning', confirm: `确认下线「${row.strategyName}」？`, onClick: () => onDisable(row) }
      : { key: 'enable', label: '上线', type: 'success', onClick: () => onEnable(row) },
    { key: 'del', label: '删除', type: 'danger', confirm: `确认删除「${row.strategyName}」？（将级联删除计划树）`, onClick: () => onDelete(row) },
  ];
}

function goStrategy(row) {
  if (!row.executionPlanCode) {
    ElMessage.warning('该策略尚未绑定执行计划');
    return;
  }
  router.push({ name: 'PlanEdit', query: {
    planCode: row.executionPlanCode,
    strategyCode: row.strategyCode,
    channelCode: row.channelCode,
    bankProductCode: row.bankProductCode,
    customerGroup: row.customerGroup,
  } });
}

const flowVisible = ref(false);
const flowLoading = ref(false);
const flowStrategy = ref(null);
const flowPlan = ref(null);
const flowModules = ref([]);
async function openFlow(row) {
  flowStrategy.value = row;
  flowVisible.value = true;
  flowLoading.value = true;
  flowPlan.value = null;
  flowModules.value = [];
  try {
    if (!row.executionPlanCode) return;
    const res = await planDetail(row.executionPlanCode);
    const detail = res.data || {};
    flowPlan.value = detail.plan || detail.executionPlan || detail;
    flowModules.value = detail.modules || detail.moduleList || [];
  } finally {
    flowLoading.value = false;
  }
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
    load();
  } catch (e) { /* 拦截器已提示 */ }
}
async function onDisable(row) {
  try {
    await disableStrategy(row.strategyCode);
    ElMessage.success('已下线');
    load();
  } catch (e) { /* 拦截器已提示 */ }
}
async function onDelete(row) {
  try {
    await deleteStrategy(row.strategyCode);
    ElMessage.success('已删除');
    load();
  } catch (e) { /* 拦截器已提示 */ }
}

// 新增/编辑
const dialogVisible = ref(false);
const editing = ref(false);
const editingCode = ref('');
const saving = ref(false);
const formRef = ref();
const form = reactive({ channelCode: '', bankProductCode: '', customerGroup: 'ENTERPRISE', strategyCode: '', strategyName: '', executionPlanCode: '' });
const formRules = {
  channelCode: [{ required: true, message: '请选择渠道', trigger: 'change' }],
  bankProductCode: [{ required: true, message: '请选择产品', trigger: 'change' }],
  customerGroup: [{ required: true, message: '请选择客群', trigger: 'change' }],
  strategyCode: [{ required: true, message: '请输入策略编码', trigger: 'blur' }],
  strategyName: [{ required: true, message: '请输入策略名称', trigger: 'blur' }, { max: 128, message: '策略名称不能超过128个字符', trigger: 'blur' }],
  executionPlanCode: [{ required: true, message: '请选择执行计划', trigger: 'change' }],
};

function openCreate() {
  editing.value = false;
  editingCode.value = '';
  Object.assign(form, { channelCode: '', bankProductCode: '', customerGroup: 'ENTERPRISE', strategyCode: '', strategyName: '', executionPlanCode: '' });
  dialogVisible.value = true;
}
function openEdit(row) {
  editing.value = true;
  editingCode.value = row.strategyCode;
  Object.assign(form, {
    channelCode: row.channelCode,
    bankProductCode: row.bankProductCode,
    customerGroup: row.customerGroup,
    strategyCode: row.strategyCode,
    strategyName: row.strategyName,
    executionPlanCode: row.executionPlanCode,
  });
  dialogVisible.value = true;
}

async function onSave() {
  await formRef.value.validate();
  saving.value = true;
  try {
    if (editing.value) {
      await updateStrategy(editingCode.value, { ...form });
    } else {
      await createStrategy({ ...form });
    }
    ElMessage.success(editing.value ? '已保存' : '新增成功');
    dialogVisible.value = false;
    load();
  } catch (e) { /* 拦截器已提示 */ } finally {
    saving.value = false;
  }
}

onMounted(async () => {
  try {
    const [ch, pl] = await Promise.all([listChannels(), listPlans()]);
    channels.value = ch.data || [];
    plans.value = pl.data || [];
  } catch (e) { /* 忽略 */ }
  load();
});
</script>

<style scoped>
.admission-flow { min-height: 180px; }
.flow-module { margin-top: 14px; border: 1px solid var(--loan-border); border-radius: var(--loan-radius); overflow: hidden; }
.flow-module-head { display: flex; align-items: center; gap: 10px; padding: 10px 14px; background: var(--loan-surface); border-bottom: 1px solid var(--loan-border); }
.flow-index { width: 22px; height: 22px; border-radius: 50%; display: inline-flex; align-items: center; justify-content: center; background: var(--loan-primary); color: white; font-size: 12px; }
.flow-join { margin-left: auto; color: var(--loan-text-muted); font-size: 12px; }
.flow-step { display: flex; align-items: center; gap: 10px; padding: 9px 14px; border-bottom: 1px solid var(--loan-border-soft); }
.flow-step:last-child { border-bottom: 0; }
.flow-step-order { width: 24px; color: var(--loan-text-muted); text-align: center; }
.flow-step-name { min-width: 150px; color: var(--loan-text); }
.flow-condition { flex: 1; color: var(--loan-text-muted); font-size: 12px; font-family: monospace; }
</style>
