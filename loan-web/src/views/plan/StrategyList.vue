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
        <el-input v-model="query.keyword" placeholder="策略编码 / 名称" clearable style="width: 200px" @keyup.enter="onSearch" />
      </AppSearchBar>

      <el-table :data="data" v-loading="loading" stripe row-key="strategyCode">
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
        <el-table-column prop="strategyCode" label="策略编码" width="160" />
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
        <el-table-column label="操作" width="220" fixed="right">
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
          <el-select v-model="form.channelCode" placeholder="选择渠道" style="width: 100%" :disabled="editing">
            <el-option v-for="c in channels" :key="c.channelCode" :label="c.bankName" :value="c.channelCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="产品" prop="bankProductCode">
          <el-select v-model="form.bankProductCode" placeholder="选择产品" filterable style="width: 100%">
            <el-option v-for="p in products" :key="p.productCode" :label="p.productName" :value="p.productCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="客群" prop="customerGroup">
          <el-select v-model="form.customerGroup" placeholder="选择客群" style="width: 100%">
            <el-option label="企业" value="ENTERPRISE" />
            <el-option label="个人" value="PERSONAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="策略编码" prop="strategyCode">
          <el-input v-model="form.strategyCode" placeholder="渠道内唯一，如 WH_BANK_TAX" :disabled="editing" />
        </el-form-item>
        <el-form-item label="策略名称" prop="strategyName">
          <el-input v-model="form.strategyName" placeholder="如 武汉某行企业税贷准入策略" />
        </el-form-item>
        <el-form-item label="执行计划" prop="executionPlanCode">
          <el-select v-model="form.executionPlanCode" placeholder="选择计划(1:1)" style="width: 100%">
            <el-option v-for="p in plans" :key="p.planCode" :label="`${p.planName}（${p.planCode}）`" :value="p.planCode" />
          </el-select>
        </el-form-item>
      </el-form>
    </AppDialog>
  </div>
</template>

<script setup>
defineOptions({ name: '_channel_strategy' });
import { ref, reactive, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import DictTag from '@/components/DictTag.vue';
import AppSearchBar from '@/components/AppSearchBar.vue';
import AppPagination from '@/components/AppPagination.vue';
import AppEmpty from '@/components/AppEmpty.vue';
import AppTableActions from '@/components/AppTableActions.vue';
import AppDialog from '@/components/AppDialog.vue';
import { useTable } from '@/composables/useTable';
import { listChannels } from '@/api/channel';
import { listPlans } from '@/api/plan';
import { pageProducts } from '@/api/product';
import {
  pageStrategy, createStrategy, updateStrategy, deleteStrategy, enableStrategy, disableStrategy,
} from '@/api/channelStrategy';

const channels = ref([]);
const products = ref([]);
const plans = ref([]);

const { loading, data, total, query, load, onSearch, onReset } = useTable(pageStrategy, {
  channelCode: '',
  customerGroup: '',
  keyword: '',
});

function channelName(code) {
  return channels.value.find((c) => c.channelCode === code)?.bankName || code || '-';
}
function productName(code) {
  return products.value.find((p) => p.productCode === code)?.productName || code || '-';
}
function planName(code) {
  return plans.value.find((p) => p.planCode === code)?.planName || code || '-';
}

/** 操作列 */
function rowActions(row) {
  const active = row.status === 'ACTIVE';
  return [
    { key: 'edit', label: '编辑', disabled: active, onClick: () => openEdit(row) },
    active
      ? { key: 'disable', label: '下线', type: 'warning', confirm: `确认下线「${row.strategyName}」？`, onClick: () => onDisable(row) }
      : { key: 'enable', label: '上线', type: 'success', onClick: () => onEnable(row) },
    { key: 'del', label: '删除', type: 'danger', confirm: `确认删除「${row.strategyName}」？（将级联删除计划树）`, onClick: () => onDelete(row) },
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
  strategyName: [{ required: true, message: '请输入策略名称', trigger: 'blur' }],
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
    const [ch, pl, pr] = await Promise.all([listChannels(), listPlans(), pageProducts({ page: 1, size: 100 })]);
    channels.value = ch.data || [];
    plans.value = pl.data || [];
    products.value = pr.data?.records || [];
  } catch (e) { /* 忽略 */ }
  load();
});
</script>
