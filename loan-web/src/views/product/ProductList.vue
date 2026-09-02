<template>
  <div class="product-page">
    <div class="loan-page-header">
      <div>
        <h2 class="loan-page-title">产品库</h2>
        <p class="loan-page-subtitle">全量库（内部代号化） / 合作库（对客可见，有效期到期自动下架）</p>
      </div>
      <el-button type="primary" @click="onHeaderAction">
        <AppIcon name="add" :size="14" />
        {{ activeTab === 'cooperate' ? '录入合作库' : '新增产品' }}
      </el-button>
    </div>

    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <el-tab-pane label="全量库" name="all" />
      <el-tab-pane label="合作库" name="cooperate" />
    </el-tabs>

    <div class="loan-card">
      <!-- ============ 全量库 Tab ============ -->
      <template v-if="activeTab === 'all'">
        <AppSearchBar :loading="loadingAll" @search="searchAll" @reset="resetAll">
          <el-input v-model="queryAll.productName" placeholder="产品名称" style="width: 180px" clearable @keyup.enter="searchAll" />
          <el-input v-model="queryAll.bankName" placeholder="所属银行" style="width: 160px" clearable @keyup.enter="searchAll" />
          <el-input v-model="queryAll.city" placeholder="服务城市" style="width: 130px" clearable @keyup.enter="searchAll" />
          <DictSelect v-model="queryAll.status" type="productStatus" placeholder="状态" style="width: 120px" />
          <template #append>
            <el-button :loading="exporting" @click="onExport">
              <AppIcon name="download" :size="14" />
              导出
            </el-button>
          </template>
        </AppSearchBar>

        <AppTableState :error="errorAll" @retry="loadAll">
        <el-table :data="dataAll" v-loading="loadingAll" stripe @sort-change="handleSortChangeAll">
          <template #empty>
            <AppEmpty title="暂无产品" desc="点击右上角「新增产品」录入银行产品" />
          </template>
          <el-table-column prop="productCode" label="产品编码" width="120" show-overflow-tooltip />
          <el-table-column prop="bankName" label="所属银行" width="160" />
          <el-table-column prop="productName" label="产品名称" min-width="180" show-overflow-tooltip />
          <el-table-column label="服务地区" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.serviceCities">{{ row.serviceCities }}</span>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
          <el-table-column prop="amountRange" label="额度区间" width="150" />
          <el-table-column prop="rateRange" label="利率区间" width="120" />
          <el-table-column prop="termRange" label="期限" width="130" />
          <el-table-column label="来源" width="90" align="center">
            <template #default="{ row }">
              <DictTag type="productSource" :value="row.source" />
            </template>
          </el-table-column>
          <el-table-column label="启用状态" width="100" align="center">
            <template #default="{ row }">
              <DictTag type="productStatus" :value="row.status" />
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="录入时间" width="165" sortable="custom" show-overflow-tooltip>
            <template #default="{ row }">
              {{ formatDateTime(row.createdAt) }}
            </template>
          </el-table-column>
          <el-table-column prop="createdBy" label="录入人" width="90" show-overflow-tooltip />
          <!-- 操作列 -->
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <AppTableActions :actions="rowActions(row)" />
            </template>
          </el-table-column>
        </el-table>
        </AppTableState>

        <AppPagination
          v-if="!errorAll"
          v-model:page="queryAll.page"
          v-model:size="queryAll.size"
          :total="totalAll"
          @change="loadAll"
        />
      </template>

      <!-- ============ 合作库 Tab（t_partner_product） ============ -->
      <template v-else>
        <AppSearchBar :loading="loadingCo" @search="searchCo" @reset="resetCo">
          <el-select v-model="queryCo.status" placeholder="合作状态" clearable style="width: 140px">
            <el-option v-for="(v, k) in partnerStatusMap" :key="k" :label="v" :value="k" />
          </el-select>
          <el-input v-model="queryCo.keyword" placeholder="产品编码 / 产品名" style="width: 220px" clearable @keyup.enter="searchCo" />
        </AppSearchBar>

        <AppTableState :error="errorCo" @retry="loadCo">
        <el-table :data="dataCo" v-loading="loadingCo" stripe row-key="bankProductCode">
          <template #empty>
            <AppEmpty title="合作库暂无产品" desc="点击右上角「录入合作库」添加对客展示的银行产品，并设置合作到期日" />
          </template>
          <el-table-column prop="bankProductCode" label="产品编码" width="150" show-overflow-tooltip />
          <el-table-column label="产品名" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.productName || '—' }}
            </template>
          </el-table-column>
          <el-table-column label="合作到期日" width="170">
            <template #default="{ row }">
              <span :class="{ 'expiring-until': isNearExpire(row) }">{{ formatDateTime(row.cooperateUntil) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="剩余" width="90" align="center">
            <template #default="{ row }">
              <span v-if="remainDays(row) !== null" class="muted">{{ remainDays(row) >= 0 ? `${remainDays(row)} 天` : '已到期' }}</span>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="110" align="center">
            <template #default="{ row }">
              <span class="loan-tag" :class="partnerStatusTag(row.status)">{{ partnerStatusText(row.status) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="createdBy" label="录入人" width="90" show-overflow-tooltip />
          <el-table-column label="录入时间" width="165" show-overflow-tooltip>
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <AppTableActions :actions="partnerRowActions(row)" />
            </template>
          </el-table-column>
        </el-table>
        </AppTableState>

        <AppPagination
          v-if="!errorCo"
          v-model:page="queryCo.page"
          v-model:size="queryCo.size"
          :total="totalCo"
          @change="loadCo"
        />
      </template>
    </div>

    <!-- 新增/编辑产品弹窗（全量库） -->
    <AppDialog
      v-model:visible="dialogVisible"
      :title="editing ? '编辑产品' : '新增产品'"
      :loading="saving"
      @confirm="onSave"
    >
      <el-form ref="formRef" :model="dialogForm" :rules="formRules" label-width="110px" label-position="right">
        <el-form-item label="产品编码" prop="productCode">
          <el-input v-model="dialogForm.productCode" placeholder="如 WH_TAX_LOAN_A" />
        </el-form-item>
        <el-form-item label="产品名称" prop="productName">
          <el-input v-model="dialogForm.productName" placeholder="如 武汉某行企业税贷 A" />
        </el-form-item>
        <el-form-item label="所属银行" prop="bankName">
          <el-input v-model="dialogForm.bankName" placeholder="如 武汉某银行" />
        </el-form-item>
        <el-form-item label="客群" prop="customerGroup">
          <DictSelect v-model="dialogForm.customerGroup" type="customerGroup" placeholder="请选择客群" />
        </el-form-item>
        <el-form-item label="来源" prop="source">
          <DictSelect v-model="dialogForm.source" type="productSource" placeholder="请选择来源" />
        </el-form-item>
        <el-form-item label="额度区间(元)">
          <el-input-number v-model="dialogForm.amountMin" :precision="2" :controls="false" placeholder="下限" style="width: 130px" />
          <span style="margin: 0 8px">~</span>
          <el-input-number v-model="dialogForm.amountMax" :precision="2" :controls="false" placeholder="上限" style="width: 130px" />
        </el-form-item>
        <el-form-item label="利率区间(%)">
          <el-input-number v-model="dialogForm.rateMin" :precision="4" :controls="false" placeholder="下限" style="width: 130px" />
          <span style="margin: 0 8px">~</span>
          <el-input-number v-model="dialogForm.rateMax" :precision="4" :controls="false" placeholder="上限" style="width: 130px" />
        </el-form-item>
        <el-form-item label="期限(月)">
          <el-input-number v-model="dialogForm.termMin" :controls="false" placeholder="下限" style="width: 130px" />
          <span style="margin: 0 8px">~</span>
          <el-input-number v-model="dialogForm.termMax" :controls="false" placeholder="上限" style="width: 130px" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <DictSelect v-model="dialogForm.status" type="productStatus" />
        </el-form-item>
      </el-form>
    </AppDialog>

    <!-- 录入合作库弹窗 -->
    <AppDialog v-model:visible="partnerDialog.visible" title="录入合作库" width="520px" :loading="partnerDialog.saving" @confirm="onPartnerSave">
      <el-form ref="partnerFormRef" :model="partnerDialog.form" :rules="partnerRules" label-width="110px" label-position="right">
        <el-form-item label="银行产品" prop="bankProductCode">
          <RemoteProductSelect v-model="partnerDialog.form.bankProductCode" scope="all" placeholder="输入产品名称搜索" />
        </el-form-item>
        <el-form-item label="合作到期日" prop="cooperateUntil">
          <el-date-picker
            v-model="partnerDialog.form.cooperateUntil"
            type="datetime"
            placeholder="到期后自动下架"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="初始状态" prop="status">
          <el-select v-model="partnerDialog.form.status" style="width: 100%">
            <el-option v-for="(v, k) in partnerStatusMap" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
      </el-form>
    </AppDialog>

    <!-- 续签弹窗 -->
    <AppDialog v-model:visible="renewDialog.visible" title="续签合作库" width="460px" :loading="renewDialog.saving" @confirm="onRenewSave">
      <p class="renew-hint">为产品「{{ renewDialog.productName || renewDialog.code }}」续签，原到期日：{{ formatDateTime(renewDialog.oldUntil) }}</p>
      <el-form ref="renewFormRef" :model="renewDialog.form" :rules="renewRules" label-width="110px" label-position="right">
        <el-form-item label="新到期日" prop="cooperateUntil">
          <el-date-picker
            v-model="renewDialog.form.cooperateUntil"
            type="datetime"
            placeholder="选择新的合作到期日"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
    </AppDialog>

    <!-- 服务城市弹窗 -->
    <AppDialog v-model:visible="cityDialog.visible" :title="`服务城市 · ${cityDialog.productName}`" @confirm="cityDialog.visible = false">
      <div class="city-wrap">
        <div class="city-tags">
          <el-tag v-for="c in cityDialog.list" :key="c.productCityCode" closable class="city-tag" @click="onEditCity(c)" @close.stop="onUnbindCity(c)">
            {{ c.province }} {{ c.city }}
          </el-tag>
          <span v-if="!cityDialog.list.length" class="city-empty">暂无绑定城市</span>
        </div>
        <div class="city-add">
          <el-input v-model="cityDialog.province" placeholder="省（如 湖北省）" style="width: 170px" />
          <el-input v-model="cityDialog.city" placeholder="市（如 武汉市）" style="width: 170px" @keyup.enter="onSaveCity" />
          <el-button type="primary" @click="onSaveCity">{{ cityDialog.editingCode ? '保存' : '添加' }}</el-button>
          <el-button v-if="cityDialog.editingCode" @click="cancelEditCity">取消编辑</el-button>
        </div>
        <div class="city-tip">点击已有城市标签可编辑；市一级、省市名称字符串、精确匹配。</div>
      </div>
    </AppDialog>
  </div>
</template>

<script setup>
defineOptions({ name: '_product' });
import { ref, reactive, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import DictTag from '@/components/DictTag.vue';
import DictSelect from '@/components/DictSelect.vue';
import AppSearchBar from '@/components/AppSearchBar.vue';
import AppPagination from '@/components/AppPagination.vue';
import AppTableActions from '@/components/AppTableActions.vue';
import AppDialog from '@/components/AppDialog.vue';
import AppEmpty from '@/components/AppEmpty.vue';
import RemoteProductSelect from '@/components/RemoteProductSelect.vue';
import AppIcon from '@/components/AppIcon.vue';
import AppTableState from '@/components/AppTableState.vue';
import { useTable } from '@/composables/useTable';
import { formatDateTime } from '@/utils/format';
import { appConfirm } from '@/utils/confirm';
import { copyText } from '@/utils/clipboard';
import { pageProducts, createProduct, updateProduct, deleteProduct } from '@/api/product';
import {
  pagePartnerProducts,
  savePartnerProduct,
  renewPartnerProduct,
  updatePartnerProductStatus,
} from '@/api/partnerProduct';
import { listProductCities, bindProductCities, updateProductCity, unbindProductCity } from '@/api/productCity';
import { dictLabel } from '@/utils/dict';

const activeTab = ref('all');

// ============================================================
// 全量库（原有逻辑，scope=all 保持不变）
// ============================================================
const {
  loading: loadingAll, error: errorAll, data: dataAll, total: totalAll,
  query: queryAll, load: loadAll, onSearch: searchAll, onReset: resetAll, handleSortChange: handleSortChangeAll,
} = useTable(
  (q) => pageProducts({ ...q, scope: 'all' }),
  { productName: '', bankName: '', city: '', status: '' },
);

// ============================================================
// 合作库（t_partner_product，P0-5）
// ============================================================
const {
  loading: loadingCo, error: errorCo, data: dataCo, total: totalCo,
  query: queryCo, load: loadCo, onSearch: searchCo, onReset: resetCo,
} = useTable(
  (q) => pagePartnerProducts(q),
  { status: '', keyword: '' },
);

/** 合作状态枚举（本地映射，后端字典未覆盖前用；如后端下发字典可切 DictTag） */
const partnerStatusMap = {
  ACTIVE: '在库',
  EXPIRING: '即将到期',
  EXPIRED: '已到期',
  OFFLINE: '已下架',
};

function partnerStatusText(code) {
  return partnerStatusMap[code] || code || '-';
}
function partnerStatusTag(code) {
  const m = {
    ACTIVE: 'loan-tag-success',
    EXPIRING: 'loan-tag-warning',
    EXPIRED: 'loan-tag-muted',
    OFFLINE: 'loan-tag-danger',
  };
  return m[code] || 'loan-tag-muted';
}

/** 距到期剩余天数（null 表示无法计算） */
function remainDays(row) {
  if (!row.cooperateUntil) return null;
  const until = new Date(String(row.cooperateUntil).replace(' ', 'T'));
  if (Number.isNaN(until.getTime())) return null;
  return Math.ceil((until.getTime() - Date.now()) / 86400000);
}
function isNearExpire(row) {
  const d = remainDays(row);
  return d !== null && d <= 30;
}

function onTabChange() {
  queryAll.page = 1;
  queryCo.page = 1;
  if (activeTab.value === 'cooperate') loadCo();
  else loadAll();
}

/** 页头按钮：按 Tab 区分动作 */
function onHeaderAction() {
  if (activeTab.value === 'cooperate') openPartnerAdd();
  else onAdd();
}

// ============================================================
// 全量库操作列
// ============================================================
function rowActions(row) {
  return [
    { key: 'edit', label: '编辑', onClick: () => onEdit(row) },
    { key: 'city', label: '服务城市', onClick: () => openCity(row) },
    {
      key: 'delete',
      label: '删除',
      type: 'danger',
      confirm: `确认删除产品「${row.productName}」？删除后不可恢复，请谨慎操作。`,
      onClick: () => onDelete(row),
    },
    {
      key: 'more',
      label: '更多',
      children: [
        { key: 'copy', label: '复制编码', onClick: () => onCopyCode(row) },
      ],
    },
  ];
}

function onEdit(row) {
  editing.value = true;
  Object.assign(dialogForm, {
    productCode: row.productCode,
    productName: row.productName,
    bankName: row.bankName,
    customerGroup: row.customerGroup,
    source: row.source,
    amountMin: row.amountMin,
    amountMax: row.amountMax,
    rateMin: row.rateMin,
    rateMax: row.rateMax,
    termMin: row.termMin,
    termMax: row.termMax,
    status: row.status,
  });
  dialogVisible.value = true;
}

function onAdd() {
  editing.value = false;
  Object.assign(dialogForm, {
    id: null,
    productCode: '',
    productName: '',
    bankName: '',
    customerGroup: 'ENTERPRISE',
    source: 'OURS',
    amountMin: null,
    amountMax: null,
    rateMin: null,
    rateMax: null,
    termMin: null,
    termMax: null,
    status: 'DRAFT',
  });
  dialogVisible.value = true;
}

async function onDelete(row) {
  try {
    await deleteProduct(row.productCode);
    loadAll();
  } catch (e) {
    // 拦截器已提示
  }
}

async function onCopyCode(row) {
  try {
    await copyText(row.productCode);
    ElMessage.success('编码已复制');
  } catch {
    ElMessage.warning('复制失败，请手动复制');
  }
}

/** 导出当前筛选条件下的全部产品为 CSV */
const exporting = ref(false);

async function onExport() {
  exporting.value = true;
  try {
    const rows = [];
    const size = 100;
    for (let page = 1; page <= 50; page += 1) {
      const res = await pageProducts({ ...queryAll, page, size, scope: 'all' });
      const records = res.data?.records || [];
      rows.push(...records);
      if (records.length < size) break;
    }
    if (!rows.length) {
      ElMessage.warning('当前筛选无数据可导出');
      return;
    }
    downloadCsv(rows);
    ElMessage.success(`已导出 ${rows.length} 条产品`);
  } catch (e) {
    // 拦截器已提示
  } finally {
    exporting.value = false;
  }
}

function downloadCsv(rows) {
  const headers = ['产品编码', '所属银行', '产品名称', '服务地区', '额度区间', '利率区间', '期限', '来源', '状态', '录入时间', '录入人'];
  const lines = [headers.join(',')];
  rows.forEach((r) => {
    const cells = [
      r.productCode,
      r.bankName,
      r.productName,
      r.serviceCities || '',
      r.amountRange,
      r.rateRange,
      r.termRange,
      dictLabel('productSource', r.source),
      dictLabel('productStatus', r.status),
      formatDateTime(r.createdAt),
      r.createdBy || '',
    ];
    lines.push(cells.map(csvCell).join(','));
  });
  const csv = '﻿' + lines.join('\r\n');
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `产品库_${formatDate(new Date())}.csv`;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}

function csvCell(v) {
  const s = v == null ? '' : String(v);
  return /[",\r\n]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s;
}

function formatDate(d) {
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

// ============================================================
// 合作库操作列：续签 / 下架·上架 / 复制编码
// ============================================================
function partnerRowActions(row) {
  const offline = row.status === 'OFFLINE';
  return [
    { key: 'renew', label: '续签', onClick: () => openRenew(row) },
    {
      key: 'toggle',
      label: offline ? '上架' : '下架',
      type: offline ? 'success' : 'warning',
      confirm: offline ? `确认重新上架产品「${row.bankProductCode}」？` : `确认下架产品「${row.bankProductCode}」？下架后小程序/报告侧不再展示。`,
      onClick: () => onToggleStatus(row, offline),
    },
    {
      key: 'more',
      label: '更多',
      children: [
        { key: 'copy', label: '复制编码', onClick: () => onCopyCode(row) },
      ],
    },
  ];
}

async function onToggleStatus(row, toActive) {
  try {
    await updatePartnerProductStatus(row.bankProductCode, toActive ? 'ACTIVE' : 'OFFLINE');
    ElMessage.success(toActive ? '已上架' : '已下架');
    loadCo();
  } catch (e) {
    // 拦截器已提示
  }
}

// ============================================================
// 录入合作库弹窗
// ============================================================
const partnerFormRef = ref();
const partnerDialog = reactive({ visible: false, saving: false, form: { bankProductCode: '', cooperateUntil: null, status: 'ACTIVE' } });
const partnerRules = {
  bankProductCode: [{ required: true, message: '请选择银行产品', trigger: 'change' }],
  cooperateUntil: [{ required: true, message: '请选择合作到期日', trigger: 'change' }],
};

async function openPartnerAdd() {
  Object.assign(partnerDialog.form, { bankProductCode: '', cooperateUntil: null, status: 'ACTIVE' });
  partnerDialog.visible = true;
}

async function onPartnerSave() {
  await partnerFormRef.value.validate();
  partnerDialog.saving = true;
  try {
    await savePartnerProduct({ ...partnerDialog.form });
    ElMessage.success('已录入合作库');
    partnerDialog.visible = false;
    loadCo();
  } catch (e) {
    // 拦截器已提示
  } finally {
    partnerDialog.saving = false;
  }
}

// ============================================================
// 续签弹窗
// ============================================================
const renewFormRef = ref();
const renewDialog = reactive({ visible: false, saving: false, code: '', productName: '', oldUntil: '', form: { cooperateUntil: null } });
const renewRules = {
  cooperateUntil: [{ required: true, message: '请选择新的合作到期日', trigger: 'change' }],
};

function openRenew(row) {
  renewDialog.code = row.bankProductCode;
  renewDialog.productName = row.productName || '';
  renewDialog.oldUntil = row.cooperateUntil;
  renewDialog.form.cooperateUntil = null;
  renewDialog.visible = true;
}

async function onRenewSave() {
  await renewFormRef.value.validate();
  renewDialog.saving = true;
  try {
    await renewPartnerProduct(renewDialog.code, renewDialog.form.cooperateUntil);
    ElMessage.success('续签成功');
    renewDialog.visible = false;
    loadCo();
  } catch (e) {
    // 拦截器已提示
  } finally {
    renewDialog.saving = false;
  }
}

// ============================================================
// 新增/编辑产品弹窗（全量库，原有）
// ============================================================
const dialogVisible = ref(false);
const editing = ref(false);
const saving = ref(false);
const formRef = ref();
const dialogForm = reactive({
  id: null,
  productCode: '',
  productName: '',
  bankName: '',
  customerGroup: 'ENTERPRISE',
  source: 'OURS',
  amountMin: null,
  amountMax: null,
  rateMin: null,
  rateMax: null,
  termMin: null,
  termMax: null,
  status: 'DRAFT',
});
const formRules = {
  productCode: [{ required: true, message: '请输入产品编码', trigger: 'blur' }],
  productName: [{ required: true, message: '请输入产品名称', trigger: 'blur' }],
  bankName: [{ required: true, message: '请输入所属银行', trigger: 'blur' }],
  customerGroup: [{ required: true, message: '请选择客群', trigger: 'change' }],
  source: [{ required: true, message: '请选择来源', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
};

async function onSave() {
  await formRef.value.validate();
  saving.value = true;
  try {
    const payload = { ...dialogForm };
    if (editing.value) {
      await updateProduct(payload);
      ElMessage.success('编辑成功');
    } else {
      await createProduct(payload);
      ElMessage.success('新增成功');
    }
    dialogVisible.value = false;
    loadAll();
  } catch (e) {
    // 拦截器已提示
  } finally {
    saving.value = false;
  }
}

// ============================================================
// 服务城市（全量库，原有）
// ============================================================
const cityDialog = reactive({ visible: false, productCode: '', productName: '', province: '', city: '', editingCode: '', list: [] });

async function openCity(row) {
  cityDialog.productCode = row.productCode;
  cityDialog.productName = row.productName;
  cityDialog.province = '';
  cityDialog.city = '';
  cityDialog.editingCode = '';
  cityDialog.visible = true;
  await loadCityList();
}
async function loadCityList() {
  try {
    const res = await listProductCities(cityDialog.productCode);
    cityDialog.list = res.data || [];
  } catch { cityDialog.list = []; }
}
function onEditCity(item) {
  cityDialog.editingCode = item.productCityCode;
  cityDialog.province = item.province || '';
  cityDialog.city = item.city || '';
}
function cancelEditCity() {
  cityDialog.editingCode = '';
  cityDialog.province = '';
  cityDialog.city = '';
}
async function onSaveCity() {
  const province = cityDialog.province.trim();
  const city = cityDialog.city.trim();
  if (!city) { ElMessage.warning('请输入市名'); return; }
  if (!/(市|州|地区|盟|特别行政区)$/.test(city)) {
    ElMessage.warning('请按市一级填写完整城市名（如 武汉市），后端按市精确匹配');
    return;
  }
  cityDialog.province = province;
  cityDialog.city = city;
  if (cityDialog.editingCode) {
    await updateProductCity(cityDialog.editingCode, { productCode: cityDialog.productCode, province, city });
    ElMessage.success('修改成功');
  } else {
    await bindProductCities(cityDialog.productCode, [{ province, city }]);
    ElMessage.success('已添加');
  }
  cancelEditCity();
  await loadCityList();
}
async function onUnbindCity(c) {
  await unbindProductCity(c.productCityCode);
  await loadCityList();
}

onMounted(loadAll);
</script>

<style scoped>
.muted { color: var(--loan-text-secondary, #8a94a6); }
.expiring-until { color: var(--loan-warning, #f59e0b); }
.renew-hint { margin: 0 0 14px; font-size: 13px; color: var(--loan-text-secondary, #8a94a6); }
.city-wrap { display: flex; flex-direction: column; gap: 12px; }
.city-tags { display: flex; flex-wrap: wrap; gap: 8px; min-height: 32px; }
.city-tag { margin: 0; }
.city-empty { color: var(--loan-text-muted); font-size: 13px; }
.city-add { display: flex; gap: 8px; }
.city-tip { font-size: 12px; color: var(--loan-text-muted); line-height: 1.6; }
</style>
