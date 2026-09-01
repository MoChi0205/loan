<template>
  <div class="order-page">
    <div class="loan-page-header">
      <div>
        <h2 class="loan-page-title">服务工单</h2>
        <p class="loan-page-subtitle">业务订单主表 · 谁建单归谁 · DEAL 计入营收并触发奖励结算</p>
      </div>
      <el-button type="primary" @click="onAdd">
        <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" style="margin-right: 4px; vertical-align: -2px"><path d="M12 5v14M5 12h14"/></svg>
        新建工单
      </el-button>
    </div>

    <div class="loan-card">
      <AppSearchBar :loading="loading" @search="onSearch" @reset="onReset">
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 140px">
          <el-option v-for="(t, k) in statusText" :key="k" :label="t" :value="k" />
        </el-select>
        <el-input v-model="query.keyword" placeholder="工单号 / 客户姓名 / 手机号 / 企业名" style="width: 240px" clearable @keyup.enter="onSearch" />
        <el-checkbox v-model="query.mineOnly" style="margin-right: 8px">仅我的工单</el-checkbox>
      </AppSearchBar>

      <el-table :data="data" v-loading="loading" stripe row-key="orderNo" @sort-change="handleSortChange">
        <template #empty>
          <AppEmpty title="暂无工单" desc="为客户发起服务工单后在此跟踪进度" />
        </template>
        <el-table-column prop="orderNo" label="工单号" min-width="180" show-overflow-tooltip />
        <el-table-column label="客户" min-width="180">
          <template #default="{ row }">
            <div class="cell-main">{{ row.clientName || row.enterpriseName || row.contactName || '—' }}</div>
            <div class="cell-sub" v-if="row.phone">{{ desensitizePhone(row.phone) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="客群" width="90">
          <template #default="{ row }">
            <span class="loan-tag" :class="row.customerGroup === 'ENTERPRISE' ? 'loan-tag-info' : 'loan-tag-warning'">
              {{ row.customerGroup === 'ENTERPRISE' ? '企业' : '个人' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="bankProductName" label="关联产品" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.bankProductName || '—' }}</template>
        </el-table-column>
        <el-table-column prop="ownerStaffCode" label="归属顾问" width="100"  show-overflow-tooltip />
        <el-table-column label="成交金额" width="130" align="right">
          <template #default="{ row }">
            <span class="mono">{{ row.dealAmount ? '¥' + fmtAmount(row.dealAmount) : '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <span class="loan-tag" :class="statusTag(row.status)">{{ statusText[row.status] || row.status }}</span>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="110">
          <template #default="{ row }">{{ sourceText(row.source) }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="160" sortable>
          <template #default="{ row }">{{ formatDateTime(row.createdAt || row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <AppTableActions :actions="rowActions(row)" />
          </template>
        </el-table-column>
      </el-table>

      <AppPagination v-model:page="query.page" v-model:size="query.size" :total="total" @change="load" />
    </div>

    <!-- 新建工单弹窗 -->
    <AppDialog v-model:visible="createVisible" title="新建工单" :loading="creating" @confirm="onCreate">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="100px" label-position="right">
        <el-form-item label="客户" prop="clientCode">
          <el-select
            v-model="createForm.clientCode"
            filterable
            remote
            :remote-method="searchClients"
            :loading="clientLoading"
            placeholder="搜索客户（姓名 / 手机号 / 企业名）"
            style="width: 100%"
          >
            <el-option v-for="c in clientOptions" :key="c.clientCode" :label="clientLabel(c)" :value="c.clientCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="客群" prop="customerGroup">
          <el-radio-group v-model="createForm.customerGroup">
            <el-radio value="ENTERPRISE">企业</el-radio>
            <el-radio value="PERSONAL">个人</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="关联产品">
          <el-select v-model="createForm.bankProductCode" filterable clearable placeholder="可选，按产品编码搜索" style="width: 100%">
            <el-option v-for="p in productOptions" :key="p.productCode" :label="`${p.productName}（${p.productCode}）`" :value="p.productCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源">
          <el-select v-model="createForm.source" style="width: 100%">
            <el-option label="手工建单" value="MANUAL" />
            <el-option label="线下补录" value="OFFLINE_SUPPLEMENT" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源单号">
          <el-input v-model="createForm.sourceOrderNo" placeholder="CRM 合同号 / 线下成交单号" />
        </el-form-item>
        <el-form-item label="客户可见备注">
          <el-input v-model="createForm.customerRemark" type="textarea" :rows="2" placeholder="小程序我的服务单展示" />
        </el-form-item>
        <el-form-item label="内部备注">
          <el-input v-model="createForm.internalRemark" type="textarea" :rows="2" placeholder="仅管理端可见" />
        </el-form-item>
        <el-form-item label="支付方式">
          <el-input v-model="createForm.payType" placeholder="如 银行转账" />
        </el-form-item>
      </el-form>
    </AppDialog>

    <!-- 成交弹窗（IN_SERVICE → DEAL） -->
    <AppDialog v-model:visible="dealVisible" title="确认成交" :loading="dealing" @confirm="onDeal">
      <el-form label-width="100px" label-position="right">
        <el-form-item label="工单号">
          <span class="mono">{{ dealForm.orderNo }}</span>
        </el-form-item>
        <el-form-item label="成交金额" required>
          <el-input-number v-model="dealForm.dealAmount" :min="0.01" :precision="2" :controls="false" style="width: 200px" placeholder="元" />
        </el-form-item>
        <el-form-item label="成交时间">
          <el-date-picker v-model="dealForm.dealTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="缺省当前时间" style="width: 220px" />
        </el-form-item>
        <el-form-item label="奖励金额">
          <el-input-number v-model="dealForm.rewardAmount" :min="0" :precision="2" :controls="false" style="width: 200px" placeholder="留空按规则比例自动算" />
          <div class="cell-sub">留空则按「奖励规则」中匹配的比例自动计算；填写则跳过比例、直接以此金额结算（标记人工调整）</div>
        </el-form-item>
      </el-form>
    </AppDialog>

    <!-- 工单详情抽屉 -->
    <el-drawer v-model="detailVisible" title="工单详情" size="480px">
      <template v-if="detail">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="工单号">{{ detail.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="客户">{{ detail.clientName || detail.enterpriseName || detail.contactName || '—' }}<template v-if="detail.phone"><br><span class="cell-sub">{{ desensitizePhone(detail.phone) }}</span></template></el-descriptions-item>
          <el-descriptions-item label="客群">{{ detail.customerGroup === 'ENTERPRISE' ? '企业' : '个人' }}</el-descriptions-item>
          <el-descriptions-item label="关联产品">{{ detail.bankProductName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="归属顾问">{{ detail.ownerStaffCode || '—' }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusText[detail.status] || detail.status }}</el-descriptions-item>
          <el-descriptions-item label="来源">{{ sourceText(detail.source) }}</el-descriptions-item>
          <el-descriptions-item label="来源单号">{{ detail.sourceOrderNo || '—' }}</el-descriptions-item>
          <el-descriptions-item label="成交金额">{{ detail.dealAmount ? '¥' + fmtAmount(detail.dealAmount) : '—' }}</el-descriptions-item>
          <el-descriptions-item label="成交时间">{{ detail.dealTime ? formatDateTime(detail.dealTime) : '—' }}</el-descriptions-item>
          <el-descriptions-item label="客户可见备注">{{ detail.customerRemark || '—' }}</el-descriptions-item>
          <el-descriptions-item label="内部备注">{{ detail.internalRemark || '—' }}</el-descriptions-item>
          <el-descriptions-item label="支付方式">{{ detail.payType || '—' }}</el-descriptions-item>
          <el-descriptions-item label="奖励结算">{{ detail.rewardSettledFlag ? '已结算' : '待结算' }}</el-descriptions-item>
          <el-descriptions-item label="创建人">{{ detail.createdBy }}（{{ formatDateTime(detail.createdAt) }}）</el-descriptions-item>
        </el-descriptions>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
defineOptions({ name: '_order' });
import { ref, reactive, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import AppSearchBar from '@/components/AppSearchBar.vue';
import AppPagination from '@/components/AppPagination.vue';
import AppTableActions from '@/components/AppTableActions.vue';
import AppDialog from '@/components/AppDialog.vue';
import { useTable } from '@/composables/useTable';
import { appConfirm } from '@/utils/confirm';
import { copyText } from '@/utils/clipboard';
import { formatDateTime, desensitizePhone } from '@/utils/format';
import { pageOrders, createOrder, orderDetail, updateOrderStatus, pageClientLite } from '@/api/order';
import { pageProducts } from '@/api/product';

const statusText = {
  NEW: '新建',
  IN_SERVICE: '服务中',
  DEAL: '已成交',
  CANCEL: '已取消',
  REFUND: '已退款',
};
const statusTag = (s) => ({
  NEW: 'loan-tag-info',
  IN_SERVICE: 'loan-tag-warning',
  DEAL: 'loan-tag-success',
  CANCEL: 'loan-tag-muted',
  REFUND: 'loan-tag-danger',
}[s] || 'loan-tag-muted');

function sourceText(s) {
  return { MANUAL: '手工建单', OFFLINE_SUPPLEMENT: '线下补录', CRM_WRITEBACK: 'CRM回写' }[s] || s || '—';
}

function fmtAmount(v) {
  return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

/** useTable 接管列表 */
const { loading, data, total, query, load, onSearch, onReset, handleSortChange } = useTable(pageOrders, {
  status: '',
  keyword: '',
  mineOnly: false,
});

/** 操作列 */
function rowActions(row) {
  const actions = [
    { key: 'detail', label: '详情', onClick: () => onDetail(row) },
    { key: 'copy', label: '复制单号', onClick: () => onCopy(row) },
  ];
  if (row.status === 'NEW') {
    actions.push({ key: 'start', label: '开始服务', type: 'success', confirm: `确认开始服务「${row.orderNo}」？`, onClick: () => onStart(row) });
    actions.push({ key: 'cancel', label: '取消', type: 'danger', confirm: `确认取消工单「${row.orderNo}」？`, onClick: () => onCancel(row) });
  } else if (row.status === 'IN_SERVICE') {
    actions.push({ key: 'deal', label: '成交', type: 'success', onClick: () => openDeal(row) });
    actions.push({ key: 'cancel', label: '取消', type: 'danger', confirm: `确认取消工单「${row.orderNo}」？`, onClick: () => onCancel(row) });
  } else if (row.status === 'DEAL') {
    actions.push({ key: 'refund', label: '退款冲正', type: 'danger', confirm: `确认将成交工单「${row.orderNo}」退款冲正？营收与奖励将联动冲减。`, onClick: () => onRefund(row) });
  }
  return actions;
}

async function onStart(row) {
  try {
    await updateOrderStatus(row.orderNo, { status: 'IN_SERVICE' });
    ElMessage.success('已开始服务');
    load();
  } catch (e) { /* 拦截器已提示 */ }
}

async function onCancel(row) {
  try {
    await updateOrderStatus(row.orderNo, { status: 'CANCEL' });
    ElMessage.success('工单已取消');
    load();
  } catch (e) { /* 拦截器已提示 */ }
}

async function onRefund(row) {
  try {
    await updateOrderStatus(row.orderNo, { status: 'REFUND' });
    ElMessage.success('已退款冲正');
    load();
  } catch (e) { /* 拦截器已提示 */ }
}

async function onCopy(row) {
  try {
    await copyText(row.orderNo);
    ElMessage.success('单号已复制');
  } catch {
    ElMessage.warning('复制失败');
  }
}

// ============================================================
// 新建工单
// ============================================================
const createVisible = ref(false);
const creating = ref(false);
const createFormRef = ref();
const clientLoading = ref(false);
const clientOptions = ref([]);
const productOptions = ref([]);
const createForm = reactive({
  clientCode: '',
  customerGroup: 'ENTERPRISE',
  bankProductCode: '',
  customerRemark: '',
  internalRemark: '',
  source: 'MANUAL',
  sourceOrderNo: '',
  payType: '',
});
const createRules = {
  clientCode: [{ required: true, message: '请选择客户', trigger: 'change' }],
  customerGroup: [{ required: true, message: '请选择客群', trigger: 'change' }],
};

function clientLabel(c) {
  const name = c.enterpriseName || c.contactName || '—';
  return `${name}（${c.clientCode}）${c.customerGroup === 'ENTERPRISE' ? '企业' : '个人'}`;
}

async function searchClients(keyword) {
  clientLoading.value = true;
  try {
    const res = await pageClientLite({ keyword: keyword || '', page: 1, size: 20 });
    clientOptions.value = res.data?.records || [];
  } catch (e) {
    clientOptions.value = [];
  } finally {
    clientLoading.value = false;
  }
}

async function onAdd() {
  Object.assign(createForm, {
    clientCode: '',
    customerGroup: 'ENTERPRISE',
    bankProductCode: '',
    customerRemark: '',
    internalRemark: '',
    source: 'MANUAL',
    sourceOrderNo: '',
    payType: '',
  });
  clientOptions.value = [];
  createVisible.value = true;
  // 预拉产品（可选）
  try {
    const res = await pageProducts({ page: 1, size: 100 });
    productOptions.value = res.data?.records || [];
  } catch (e) {
    productOptions.value = [];
  }
  searchClients('');
}

async function onCreate() {
  await createFormRef.value.validate();
  creating.value = true;
  try {
    const res = await createOrder({ ...createForm });
    ElMessage.success(`建单成功：${res.data}`);
    createVisible.value = false;
    load();
  } catch (e) { /* 拦截器已提示 */ } finally {
    creating.value = false;
  }
}

// ============================================================
// 成交弹窗
// ============================================================
const dealVisible = ref(false);
const dealing = ref(false);
const dealForm = reactive({ orderNo: '', dealAmount: null, dealTime: '', rewardAmount: null });

function openDeal(row) {
  dealForm.orderNo = row.orderNo;
  dealForm.dealAmount = null;
  dealForm.dealTime = '';
  dealForm.rewardAmount = null;
  dealVisible.value = true;
}

async function onDeal() {
  if (!dealForm.dealAmount || dealForm.dealAmount <= 0) {
    ElMessage.warning('请填写成交金额');
    return;
  }
  dealing.value = true;
  try {
    await updateOrderStatus(dealForm.orderNo, {
      status: 'DEAL',
      dealAmount: dealForm.dealAmount,
      dealTime: dealForm.dealTime || null,
      rewardAmount: dealForm.rewardAmount && dealForm.rewardAmount > 0 ? dealForm.rewardAmount : null,
    });
    ElMessage.success('成交成功，奖励待结算');
    dealVisible.value = false;
    load();
  } catch (e) { /* 拦截器已提示 */ } finally {
    dealing.value = false;
  }
}

// ============================================================
// 详情
// ============================================================
const detailVisible = ref(false);
const detail = ref(null);

async function onDetail(row) {
  try {
    const res = await orderDetail(row.orderNo);
    detail.value = res.data;
    detailVisible.value = true;
  } catch (e) { /* 拦截器已提示 */ }
}

onMounted(load);
</script>

<style scoped>
.cell-main {
  font-weight: 500;
}
.cell-sub {
  font-size: 12px;
  color: var(--loan-text-secondary, #8a94a6);
}
.mono {
  font-family: "SF Mono", Menlo, Consolas, monospace;
}
</style>
