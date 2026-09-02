<template>
  <div class="cc-page">
    <div class="loan-page-header">
      <div>
        <h2 class="loan-page-title">渠道档案</h2>
        <p class="loan-page-subtitle">按银行合作渠道查看准入配置，进入向导分步配置该渠道的策略与执行计划</p>
      </div>
      <el-button type="primary" @click="goWizard(null)">
        <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" style="margin-right: 4px; vertical-align: -2px"><path d="M12 5v14M5 12h14"/></svg>
        进入向导
      </el-button>
    </div>

    <div class="loan-card">
      <el-table :data="rows" v-loading="loading" stripe row-key="channelCode" @expand-change="onExpandChange">
        <el-table-column type="expand" width="44">
          <template #default="{ row }">
            <div class="cc-expand">
              <el-skeleton v-if="strategyLoading[row.channelCode]" :rows="2" animated />
              <template v-else>
                <div v-if="!strategyMap[row.channelCode]?.length" class="cc-empty">该渠道暂无准入策略，点右上角「进入向导」创建</div>
                <el-table v-else :data="strategyMap[row.channelCode]" size="small" stripe>
                  <el-table-column prop="strategyCode" label="策略编码" min-width="150" show-overflow-tooltip />
                  <el-table-column prop="strategyName" label="策略名称" min-width="150" show-overflow-tooltip />
                  <el-table-column label="产品" min-width="160">
                    <template #default="{ row: s }">{{ s.bankProductName || '产品信息待补充' }}</template>
                  </el-table-column>
                  <el-table-column label="客群" width="90">
                    <template #default="{ row: s }"><DictTag type="customerGroup" :value="s.customerGroup" /></template>
                  </el-table-column>
                  <el-table-column label="执行计划" min-width="160">
                    <template #default="{ row: s }">{{ planName(s.executionPlanCode) }}</template>
                  </el-table-column>
                  <el-table-column label="状态" width="90">
                    <template #default="{ row: s }">
                      <span class="loan-tag" :class="s.status === 'ACTIVE' ? 'loan-tag-success' : 'loan-tag-muted'">{{ s.status === 'ACTIVE' ? '已上线' : '草稿' }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="操作" width="170" fixed="right">
                    <template #default="{ row: s }">
                      <el-button link type="primary" @click="gotoOrch(row, s)">编排</el-button>
                      <el-button link type="primary" @click="onValidate(s)">校验</el-button>
                      <el-button v-if="s.status !== 'ACTIVE'" link type="success" @click="onEnable(s)">上线</el-button>
                      <el-button v-else link type="warning" @click="onDisable(s)">下线</el-button>
                    </template>
                  </el-table-column>
                </el-table>
              </template>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="bankName" label="银行渠道" min-width="180" show-overflow-tooltip />
        <el-table-column prop="channelCode" label="渠道编码" width="140" />
        <el-table-column label="策略数" width="90" align="center">
          <template #default="{ row }">{{ row.strategyCount }}</template>
        </el-table-column>
        <el-table-column label="已上线" width="90" align="center">
          <template #default="{ row }">
            <span :class="row.activeCount > 0 ? 'cc-active' : 'cc-muted'">{{ row.activeCount }}</span>
          </template>
        </el-table-column>
        <el-table-column label="计划数" width="90" align="center">
          <template #default="{ row }">{{ row.planCount }}</template>
        </el-table-column>
        <el-table-column label="最近更新" width="170">
          <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="goWizard(row)">进入向导</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
defineOptions({ name: '_channel_config' });
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import DictTag from '@/components/DictTag.vue';
import { formatDateTime } from '@/utils/format';
import { channelSummary, pageStrategy, enableStrategy, disableStrategy, validateStrategy } from '@/api/channelStrategy';
import { listPlans } from '@/api/plan';

const router = useRouter();
const rows = ref([]);
const loading = ref(false);
const strategyMap = ref({});
const strategyLoading = ref({});
const plans = ref([]);

function planName(code) { return plans.value.find((p) => p.planCode === code)?.planName || code || '-'; }

async function onExpandChange(row, expandedRows) {
  if (!expandedRows.length) return;
  // 展开缓存：已拉取过（含空数组）直接复用，避免频繁展开/收起重复请求
  if (strategyMap.value[row.channelCode]) return;
  strategyLoading.value[row.channelCode] = true;
  try {
    strategyMap.value[row.channelCode] = await loadAllStrategies(row.channelCode);
  } finally {
    strategyLoading.value[row.channelCode] = false;
  }
}

function goWizard(row) {
  const query = row?.channelCode ? { channelCode: row.channelCode } : {};
  router.push({ path: '/channel-config-wizard', query });
}

/** 展开行「编排」：跳向导并携带策略业务编码，断点续做直接定位规则编排。 */
function gotoOrch(row, s) {
  router.push({ path: '/channel-config-wizard', query: { channelCode: row.channelCode, strategyCode: s.strategyCode } });
}

/** 强制重拉某渠道策略（绕过展开缓存，用于上线/下线后刷新状态） */
async function reloadStrategies(channelCode) {
  try {
    strategyMap.value[channelCode] = await loadAllStrategies(channelCode);
  } catch { /* 忽略 */ }
}

async function onValidate(s) {
  try {
    const res = await validateStrategy(s.strategyCode);
    const problems = res.data || [];
    if (!problems.length) {
      ElMessage.success(`「${s.strategyName}」校验通过`);
    } else {
      ElMessageBox.alert(
        problems.map((p) => `· ${p}`).join('<br/>'),
        `「${s.strategyName}」未通过上线校验`,
        { confirmButtonText: '知道了', type: 'warning', dangerouslyUseHTMLString: true },
      );
    }
  } catch (e) { /* 拦截器已提示 */ }
}

async function onEnable(s) {
  // 上线前先执行准入校验，失败弹窗展示明细并中止
  try {
    const res = await validateStrategy(s.strategyCode);
    const problems = res.data || [];
    if (problems.length) {
      ElMessageBox.alert(
        problems.map((p) => `· ${p}`).join('<br/>'),
        `「${s.strategyName}」未通过上线校验`,
        { confirmButtonText: '知道了', type: 'warning', dangerouslyUseHTMLString: true },
      );
      return;
    }
    await enableStrategy(s.strategyCode);
    ElMessage.success('已上线');
    await reloadStrategies(s.channelCode);
    await refreshSummary();
  } catch (e) { /* 拦截器已提示 */ }
}

async function onDisable(s) {
  await disableStrategy(s.strategyCode);
  ElMessage.success('已下线');
  await reloadStrategies(s.channelCode);
  await refreshSummary();
}

async function refreshSummary() {
  try {
    const res = await channelSummary();
    rows.value = res.data || [];
  } catch { /* 忽略 */ }
}

async function loadAllStrategies(channelCode) {
  const records = [];
  let page = 1;
  const size = 100;
  while (true) {
    const res = await pageStrategy({ channelCode, page, size });
    const payload = res.data || {};
    records.push(...(payload.records || []));
    if (records.length >= Number(payload.total || 0) || !(payload.records || []).length) return records;
    page += 1;
  }
}

onMounted(async () => {
  loading.value = true;
  try {
    const [s, pl] = await Promise.all([channelSummary(), listPlans()]);
    rows.value = s.data || [];
    plans.value = pl.data || [];
  } finally {
    loading.value = false;
  }
});
</script>

<style scoped>
.cc-expand { padding: 8px 16px; }
.cc-empty { padding: 12px; color: var(--loan-text-muted); font-size: 13px; }
.cc-active { color: var(--loan-success); font-weight: 600; }
.cc-muted { color: var(--loan-text-muted); }
</style>
