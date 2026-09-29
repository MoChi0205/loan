<template>
  <div class="client-profile-page">
    <div class="loan-page-header">
      <div>
        <h2 class="loan-page-title">{{ !clientCode ? listPageTitle : (detail.enterpriseName || detail.realName || detail.name || '客户档案') }}</h2>
        <p class="loan-page-subtitle">{{ !clientCode ? '按客户身份、来源、状态、跟进与成交情况筛选' : (isChannel ? '本人录入客户 · 档案只读 · 服务归属' : (isAdviser ? '本人归属客户 · 档案与服务记录' : '客户资料 · 认证信息 · 服务归属 · 分配/跟进历史')) }}</p>
      </div>
      <div class="header-actions">
        <el-button v-if="clientCode" @click="backToClientList">返回客户列表</el-button>
        <el-button v-if="userStore.hasPerm(ACTION_PERMISSION.CLIENT_SCREENING)" type="primary" :disabled="!clientCode" @click="goScreening">
          <AppIcon name="screening" :size="14" />
          发起初筛
        </el-button>
        <el-button v-if="userStore.hasPerm(ACTION_PERMISSION.CLIENT_UPDATE)" :loading="saving" :disabled="!clientCode" @click="openEdit">
          <AppIcon name="edit" :size="14" />
          编辑档案
        </el-button>
        <el-button v-if="userStore.hasPerm(ACTION_PERMISSION.CLIENT_ASSIGN)" type="warning" plain :disabled="!clientCode" @click="openAssign">分配归属</el-button>
        <el-button v-if="userStore.hasPerm(ACTION_PERMISSION.CLIENT_RECYCLE)" type="danger" plain :disabled="!detail.ownerStaffCode" @click="onRecycle">回收进公海</el-button>
      </div>
    </div>

    <div v-if="!clientCode && !loading" class="profile-empty loan-card">
      <div class="customer-segment-bar" role="tablist" aria-label="我的客户标签">
        <button v-for="item in customerTagSegments" :key="item.value" type="button" class="customer-segment"
          :class="{ active: clientQuery.customerTag === item.value }" @click="selectCustomerTag(item.value)">
          <span class="segment-dot" :class="`segment-dot--${item.tone}`"></span>{{ item.label }}
        </button>
        <span class="segment-hint">标签随预约、来访、服务工单实时更新</span>
      </div>
      <AppSearchBar :loading="listLoading" @search="searchClients" @reset="resetClients">
        <el-input v-model="clientQuery.keyword" placeholder="企业名 / 姓名 / 手机号 / 证件号码" clearable style="width: 320px" @keyup.enter="searchClients" />
        <el-select v-model="clientQuery.customerGroup" placeholder="客户类型" clearable style="width: 130px"><el-option label="企业客户" value="ENTERPRISE" /><el-option label="个人客户" value="PERSONAL" /></el-select>
        <el-select v-model="clientQuery.status" placeholder="客户状态" clearable style="width: 130px"><el-option label="有效" value="ACTIVE" /><el-option label="已停用" value="DISABLED" /></el-select>
        <el-select v-model="clientQuery.followState" placeholder="跟进情况" clearable style="width: 150px"><el-option label="从未跟进" value="NEVER" /><el-option label="30天未跟进" value="OVERDUE" /><el-option label="7天内已跟进" value="RECENT" /></el-select>
        <el-select v-model="clientQuery.hasDeal" placeholder="成交情况" clearable style="width: 130px"><el-option label="已成交" :value="true" /><el-option label="未成交" :value="false" /></el-select>
        <el-select v-if="clientScope === 'ALL'" v-model="clientQuery.ownerDeptCode" placeholder="归属团队" clearable filterable style="width: 160px" @change="onOwnerDepartmentChange"><el-option v-for="item in filterDepartments" :key="item.code" :label="item.name" :value="item.code" /></el-select>
        <el-select v-if="['TEAM', 'ALL'].includes(clientScope)" v-model="clientQuery.ownerStaffCode" placeholder="归属员工" clearable filterable remote :remote-method="searchFilterStaff" :loading="filterStaffLoading" style="width: 180px"><el-option v-for="item in filterStaffOptions" :key="item.staffCode" :label="`${item.staffName}（${item.roleName || item.roleCode || '员工'}）`" :value="item.staffCode" /></el-select>
        <el-select v-model="clientQuery.customerTag" placeholder="客户标签" clearable style="width: 140px"><el-option v-for="(label, code) in customerTagMap" :key="code" :label="label" :value="code" /></el-select>
        <el-button text type="primary" @click="moreFiltersVisible = !moreFiltersVisible">{{ moreFiltersVisible ? '收起筛选' : '更多筛选' }}</el-button>
        <template v-if="moreFiltersVisible">
          <el-input v-model="clientQuery.name" placeholder="联系人姓名" clearable style="width: 160px" @keyup.enter="searchClients" />
          <el-input v-model="clientQuery.phone" placeholder="手机号" clearable style="width: 160px" @keyup.enter="searchClients" />
          <el-input v-model="clientQuery.enterpriseName" placeholder="企业名称" clearable style="width: 200px" @keyup.enter="searchClients" />
          <el-input v-model="clientQuery.creditCode" placeholder="统一社会信用代码" clearable style="width: 220px" @keyup.enter="searchClients" />
          <el-input v-model="clientQuery.source" placeholder="客户来源" clearable style="width: 160px" @keyup.enter="searchClients" />
        <el-date-picker
          v-model="createdAtRange"
          type="daterange"
          range-separator="至"
          start-placeholder="建档起始"
          end-placeholder="建档截止"
          value-format="YYYY-MM-DD HH:mm:ss"
          style="width: 260px"
        />
        </template>
        <el-date-picker
          v-model="dealTimeRange"
          type="daterange"
          range-separator="至"
          start-placeholder="成交起始"
          end-placeholder="成交截止"
          value-format="YYYY-MM-DD HH:mm:ss"
          style="width: 260px"
        />
      </AppSearchBar>
      <div v-if="['COMPANY_SEA', 'TEAM_SEA'].includes(clientScope)" class="batch-bar">
        <el-button type="primary" :disabled="!selectedClientCodes.length" @click="onBatchClaim">批量认领（{{ selectedClientCodes.length }}）</el-button>
      </div>
      <el-table :data="clientRows" v-loading="listLoading" stripe row-key="clientCode" @selection-change="onClientSelection" @sort-change="handleClientSortChange">
        <template #empty>
          <AppEmpty
            :title="showOwnClientList ? '暂无客户' : '暂无符合条件的客户'"
            :desc="showOwnClientList ? (isChannel ? '本人录入的线索转化为客户后会显示在这里' : '本人归属的客户会显示在这里') : '试试调整筛选条件'"
          />
        </template>
        <el-table-column v-if="['COMPANY_SEA', 'TEAM_SEA'].includes(clientScope)" type="selection" width="46" />
        <el-table-column label="客户" min-width="180">
          <template #default="{ row }">
            <div class="cell-main">{{ row.enterpriseName || row.contactName || '—' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="联系方式" width="190">
          <template #default="{ row }">
            <span class="cell-main contact-phone">{{ clientPhoneDisplay(row) }}</span>
            <el-button v-if="!revealedClientPhones.has(row.clientCode)" link type="primary" size="small" @click.stop="viewClientPhone(row)">申请查看</el-button>
            <span v-else class="phone-revealed-label">已授权查看</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="100"><template #default="{ row }">{{ row.customerGroup === 'PERSONAL' ? '个人' : '企业' }}</template></el-table-column>
        <el-table-column label="来源" width="130"><template #default="{ row }">{{ sourceText(row.source) }}</template></el-table-column>
        <el-table-column label="客户标签" min-width="180"><template #default="{ row }"><span v-for="tag in row.customerTags || [row.customerTag]" :key="tag" class="loan-tag loan-tag-info customer-row-tag">{{ customerTagText(tag) }}</span></template></el-table-column>
        <el-table-column label="归属顾问" width="140"><template #default="{ row }">{{ row.ownerStaffName || '待分配' }}</template></el-table-column>
        <el-table-column label="最近跟进" width="190">
          <template #default="{ row }">
            <div v-if="row.lastFollowedAt">{{ formatDateTime(row.lastFollowedAt) }}</div>
            <span v-else class="loan-tag loan-tag-warning">尚未跟进</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="建档时间" width="170" sortable><template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template></el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <AppTableActions :actions="ownClientActions(row)" />
          </template>
        </el-table-column>
      </el-table>
      <AppPagination v-model:page="clientQuery.page" v-model:size="clientQuery.size" :total="clientTotal" @change="loadClients" />
    </div>
    <div v-else v-loading="loading" class="profile-body">
      <!-- ① 基础信息 -->
      <div class="loan-card section-card">
        <h3 class="panel-title">基础信息</h3>
        <el-descriptions :column="3" border>
          <el-descriptions-item label="客户姓名">{{ detail.name || '—' }}</el-descriptions-item>
          <el-descriptions-item label="手机号">
            <span>{{ detailPhoneDisplay }}</span>
            <el-button v-if="!detailPhonePlain" link type="primary" size="small" @click="viewClientPhone(detail)">申请查看原值</el-button>
          </el-descriptions-item>
          <el-descriptions-item label="来源">
            <span class="loan-tag" :class="sourceTag(detail.source)">{{ sourceText(detail.source) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="归属顾问">{{ detail.ownerStaffName || (detail.ownerStaffCode ? '姓名待补充' : '待分配') }}</el-descriptions-item>
          <el-descriptions-item label="认证状态">
            <span class="loan-tag" :class="authStatusTag(detail.authStatus)">{{ authStatusText(detail.authStatus) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="邀请链（引荐人）" :span="3">
            <span v-if="referrerText !== '-'">{{ referrerText }}<span v-if="detail.referrerType" class="cell-sub">（{{ referrerTypeText(detail.referrerType) }}）</span></span>
            <span v-else class="muted">—</span>
          </el-descriptions-item>
        </el-descriptions>
      </div>

      <!-- ② 企业档案 ↔ 个人档案 -->
      <div class="loan-card section-card">
        <el-tabs v-model="profileTab">
          <el-tab-pane label="企业档案" name="enterprise">
            <el-descriptions :column="2" border>
              <el-descriptions-item label="企业名称">{{ detail.enterpriseName || '—' }}</el-descriptions-item>
              <el-descriptions-item label="统一社会信用代码">{{ desensitizeCreditCode(detail.creditCode) }}</el-descriptions-item>
            </el-descriptions>
          </el-tab-pane>
          <el-tab-pane label="个人档案" name="personal">
            <el-descriptions :column="3" border>
              <el-descriptions-item label="姓名">{{ desensitizeName(detail.realName) }}</el-descriptions-item>
              <el-descriptions-item label="身份证号">{{ desensitizeIdCard(detail.idCardNo) }}</el-descriptions-item>
              <el-descriptions-item label="城市">{{ detail.city || '—' }}</el-descriptions-item>
              <el-descriptions-item label="年龄">{{ detail.age ?? '—' }}</el-descriptions-item>
              <el-descriptions-item label="房产">{{ flagText(detail.houseFlag) }}</el-descriptions-item>
              <el-descriptions-item label="车辆">{{ flagText(detail.carFlag) }}</el-descriptions-item>
              <el-descriptions-item label="社保">{{ flagText(detail.socialSecurityFlag) }}</el-descriptions-item>
              <el-descriptions-item label="公积金">{{ flagText(detail.fundFlag) }}</el-descriptions-item>
            </el-descriptions>
          </el-tab-pane>
        </el-tabs>
      </div>

      <!-- ③ 审计信息 -->
      <div v-if="!isChannel" class="loan-card section-card">
        <h3 class="panel-title">审计信息</h3>
        <el-descriptions :column="4" border>
          <el-descriptions-item label="创建人">{{ detail.createdByName || detail.createdBy || '—' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatDateTime(detail.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="最近更新人">{{ detail.updatedBy || '—' }}</el-descriptions-item>
          <el-descriptions-item label="最近更新时间">{{ formatDateTime(detail.updatedAt) }}</el-descriptions-item>
        </el-descriptions>
      </div>

      <!-- ④ 分配/跟进历史（复用 t_lead_allocation_record，所有角色可见） -->
      <div class="loan-card section-card">
        <h3 class="panel-title">
          分配 / 跟进历史
          <span class="panel-sub" v-if="historyTotal">共 {{ historyTotal }} 条</span>
        </h3>
        <div v-if="!historyTotal" class="muted">暂无流转记录</div>
        <el-table v-else :data="historyRows" size="small" stripe>
          <el-table-column label="时间" width="170"><template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template></el-table-column>
          <el-table-column label="动作" width="140">
            <template #default="{ row }">
              <span class="loan-tag" :class="historyActionTag(row.actionType)">{{ historyActionText(row.actionType) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="原归属" width="140"><template #default="{ row }">{{ row.fromStaffName || '姓名待补充' }}</template></el-table-column>
          <el-table-column label="新归属" width="140"><template #default="{ row }">{{ row.toStaffName || '姓名待补充' }}</template></el-table-column>
          <el-table-column label="操作人" width="120"><template #default="{ row }">{{ row.operator || '—' }}</template></el-table-column>
          <el-table-column label="备注" min-width="200" show-overflow-tooltip><template #default="{ row }">{{ row.remark || '—' }}</template></el-table-column>
        </el-table>
      </div>
    </div>

    <!-- 编辑弹窗：基础信息 + 个人档案合并编辑 -->
    <AppDialog v-model:visible="editVisible" title="编辑客户档案" width="640px" :loading="saving" @confirm="onSave">
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="110px" label-position="right">
        <el-divider content-position="left">基础信息</el-divider>
        <el-form-item label="客户姓名" prop="contactName">
          <el-input v-model="editForm.contactName" placeholder="客户姓名" />
        </el-form-item>
        <el-form-item label="来源" prop="source">
          <el-select v-model="editForm.source" clearable placeholder="来源" style="width: 100%">
            <el-option v-for="(v, k) in sourceMap" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
        <el-divider content-position="left">个人档案</el-divider>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="editForm.realName" placeholder="实名姓名" />
        </el-form-item>
        <el-form-item label="身份证号" prop="idCardNo">
          <el-input v-model="editForm.idCardNo" placeholder="身份证号（录入后加密存储）" />
        </el-form-item>
        <el-form-item label="城市">
          <el-input v-model="editForm.city" placeholder="如 武汉市" />
        </el-form-item>
        <el-form-item label="年龄">
          <el-input-number v-model="editForm.age" :min="18" :max="100" :controls="false" style="width: 100%" />
        </el-form-item>
        <div class="flag-grid">
          <el-form-item v-for="f in flagFields" :key="f.key" :label="f.label">
            <el-select v-model="editForm[f.key]" clearable placeholder="未填写" style="width: 100%">
              <el-option label="是" :value="1" />
              <el-option label="否" :value="0" />
            </el-select>
          </el-form-item>
          <el-form-item label="客户标签"><el-select v-model="editForm.customerTag" clearable placeholder="请选择客户标签" style="width: 100%"><el-option v-for="(label, code) in customerTagMap" :key="code" :label="label" :value="code" /></el-select></el-form-item>
        </div>
      </el-form>
    </AppDialog>

    <!-- 分配归属弹窗：角色门控（DEPT_MANAGER/BOSS/OPERATOR/SUPER_ADMIN/SUPER） -->
    <AppDialog v-model:visible="assignVisible" title="分配客户归属" width="560px" :loading="assigning" @confirm="onAssignConfirm">
      <p class="assign-dialog-hint">从我司在职人员中选择归属人，支持按姓名或工号搜索；分配后立即生效。</p>
      <el-form label-width="72px" label-position="right">
        <el-form-item label="归属人">
          <el-select
            v-model="assignTarget"
            filterable
            remote
            :remote-method="searchAssignStaff"
            :loading="assignLoading"
            placeholder="搜索姓名或工号"
            style="width: 100%"
            placement="bottom-start"
            @visible-change="(v) => { if (v) searchAssignStaff('') }"
          >
            <el-option
              v-for="o in assignOptions"
              :key="o.staffCode"
              :label="`${o.staffName || '员工姓名待补充'} · ${o.roleName || '角色待补充'} · ${o.deptName || '未分部门'}`"
              :value="o.staffCode"
              :disabled="o.status && o.status !== 'ACTIVE'"
            />
          </el-select>
        </el-form-item>
      </el-form>
    </AppDialog>

    <!-- 跟进弹窗：记录跟进内容并刷新跟进时间，避免超期自动回收 -->
    <AppDialog v-model:visible="followVisible" title="跟进客户" width="480px" :loading="following" @confirm="onFollowSubmit">
      <el-form label-width="80px" label-position="right">
        <el-form-item label="跟进内容">
          <el-input v-model="followForm.content" type="textarea" :rows="3" placeholder="记录本次跟进情况（可选）" />
        </el-form-item>
      </el-form>
    </AppDialog>
  </div>
</template>

<script setup>
defineOptions({ name: '_client_profile' });
import { ref, reactive, computed, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import AppDialog from '@/components/AppDialog.vue';
import AppIcon from '@/components/AppIcon.vue';
import AppEmpty from '@/components/AppEmpty.vue';
import AppSearchBar from '@/components/AppSearchBar.vue';
import AppPagination from '@/components/AppPagination.vue';
import { formatDateTime, desensitizePhone } from '@/utils/format';
import { getClientDetail, pageClients, getClientTagCounts, updateClientDetail, assignClient, recycleClient, releaseClient, followClient, getClientHistory, claimUnassignedClient, batchClaimClients } from '@/api/client';
import { staffPage, departmentTree } from '@/api/org';
import { useUserStore } from '@/store/user';
import { useTable } from '@/composables/useTable';
import { ACTION_PERMISSION } from '@/utils/access';
import AppTableActions from '@/components/AppTableActions.vue';
import { applyClientPhoneView, sensitiveQuota } from '@/api/sensitive';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const isChannel = computed(() => userStore.roleCode === 'CHANNEL');
const isAdviser = computed(() => userStore.roleCode === 'ADVISER');
const showOwnClientList = computed(() => isChannel.value || isAdviser.value);
const canViewTeamAssigned = computed(() => userStore.roleCode === 'DEPT_MANAGER');
const canViewCompanyAssigned = computed(() => ['BOSS', 'OPERATOR', 'SUPER_ADMIN', 'SUPER'].includes(userStore.roleCode));
const defaultClientScope = computed(() => {
  if (canViewCompanyAssigned.value) return 'ALL';
  return 'MY';
});
const clientScope = ref(defaultClientScope.value);
const clientCode = ref('');
const loading = ref(false);
const profileTab = ref('enterprise');
const moreFiltersVisible = ref(false);
const scopeTitle = Object.freeze({ MY: '我的客户', TEAM: '部门客户', ALL: '公司客户', COMPANY_SEA: '客户公海', TEAM_SEA: '团队公海' });
const listPageTitle = computed(() => scopeTitle[clientScope.value] || '客户档案');
const selectedClientCodes = ref([]);
const tagCounts = ref({});
function onClientSelection(rows) { selectedClientCodes.value = rows.map((row) => row.clientCode); }
async function onBatchClaim() {
  await ElMessageBox.confirm(`确认批量认领选中的 ${selectedClientCodes.value.length} 位客户？`, '批量认领');
  await batchClaimClients(selectedClientCodes.value);
  ElMessage.success('批量认领成功');
  selectedClientCodes.value = [];
  loadClients();
}
const {
  loading: listLoading,
  data: clientRows,
  total: clientTotal,
  query: clientQuery,
  load: loadClients,
  onSearch: searchClients,
  onReset: resetClients,
  handleSortChange: handleClientSortChange,
} = useTable(pageClients, {
  keyword: '',
  name: '',
  phone: '',
  enterpriseName: '',
  creditCode: '',
  customerGroup: '',
  source: '',
  customerTag: '',
  status: '',
  followState: '',
  hasDeal: '',
  ownerDeptCode: '',
  ownerStaffCode: '',
  createdAtStart: '',
  createdAtEnd: '',
  dealTimeStart: '',
  dealTimeEnd: '',
  scope: defaultClientScope.value,
});

const filterStaffOptions = ref([]); const filterStaffLoading = ref(false); const filterDepartments = ref([]);
function flattenDepartments(nodes, output = []) {
  (nodes || []).forEach((item) => { output.push({ code: item.code || item.deptCode, name: item.name || item.deptName }); flattenDepartments(item.children, output); });
  return output;
}
async function searchFilterStaff(keyword = '') {
  filterStaffLoading.value = true;
  try {
    const role = userStore.roleCode;
    const params = { page: 1, size: 50, keyword };
    if (role === 'DEPT_MANAGER') params.deptCode = userStore.user?.deptCode || undefined;
    else if (['BOSS', 'OPERATOR', 'SUPER_ADMIN', 'SUPER'].includes(role)) params.deptCode = clientQuery.ownerDeptCode || undefined;
    else if (role === 'ADVISER') params.keyword = userStore.displayName || keyword;
    const rows = (await staffPage(params)).data?.records || [];
    filterStaffOptions.value = rows.filter((row) => {
      if (role === 'ADVISER') return row.staffCode === userStore.user?.userNo;
      if (role === 'DEPT_MANAGER') return row.deptCode === userStore.user?.deptCode;
      return true;
    });
  }
  finally { filterStaffLoading.value = false; }
}
function onOwnerDepartmentChange() {
  clientQuery.ownerStaffCode = '';
  searchFilterStaff('');
  loadClientTagCounts();
}
async function loadFilterDepartments() {
  if (!['TEAM', 'ALL'].includes(clientScope.value)) return;
  if (clientScope.value === 'ALL' && !filterDepartments.value.length) {
    const res = await departmentTree(); filterDepartments.value = flattenDepartments(res.data || []);
  }
  await searchFilterStaff('');
}

async function loadClientTagCounts() {
  if (clientCode.value) return;
  try {
    const params = { scope: clientScope.value };
    if (clientScope.value === 'ALL') {
      params.ownerDeptCode = clientQuery.ownerDeptCode || undefined;
      params.ownerStaffCode = clientQuery.ownerStaffCode || undefined;
    }
    tagCounts.value = (await getClientTagCounts(params)).data || {};
  } catch (e) {
    tagCounts.value = {};
  }
}
watch(() => [clientScope.value, clientQuery.ownerDeptCode, clientQuery.ownerStaffCode], loadClientTagCounts);

/** 独立子菜单固定列表范围；旧 query 链接只作兼容，不再渲染页内范围 Tab。 */
function resolveRouteScope() {
  const requested = String(route.meta.clientScope || route.query.scope || '').toUpperCase();
  const allowed = new Set(['MY', 'COMPANY_SEA']);
  if (canViewTeamAssigned.value) {
    allowed.add('TEAM');
    allowed.add('TEAM_SEA');
  }
  if (canViewCompanyAssigned.value) allowed.add('ALL');
  return allowed.has(requested) ? requested : defaultClientScope.value;
}

/** 建档时间范围（daterange）→ 拆成起止两个查询参数 */
const createdAtRange = ref(null);
watch(createdAtRange, (val) => {
  clientQuery.createdAtStart = Array.isArray(val) && val[0] ? val[0] : '';
  clientQuery.createdAtEnd = Array.isArray(val) && val[1] ? val[1] : '';
});

/** 成交时间范围（daterange，联表 t_service_order 已成交工单）→ 拆成起止两个查询参数 */
const dealTimeRange = ref(null);
watch(dealTimeRange, (val) => {
  clientQuery.dealTimeStart = Array.isArray(val) && val[0] ? val[0] : '';
  clientQuery.dealTimeEnd = Array.isArray(val) && val[1] ? val[1] : '';
});

function openChannelClient(row) {
  router.push({ path: route.path, query: { clientCode: row.clientCode } });
}
/** 返回客户列表（所有角色统一入口） */
function backToClientList() {
  // 日期范围一并清空，避免返回列表后仍受旧筛选影响
  createdAtRange.value = null;
  dealTimeRange.value = null;
  clientQuery.createdAtStart = '';
  clientQuery.createdAtEnd = '';
  clientQuery.dealTimeStart = '';
  clientQuery.dealTimeEnd = '';
  router.push({ path: route.path });
}

// ============================================================
// 分配 / 跟进历史（所有角色可见，复用 t_lead_allocation_record）
// ============================================================
const historyRows = ref([]);
const historyTotal = ref(0);
const HISTORY_ACTION_MAP = {
  CLAIM_APPLY: { label: '申请认领', type: 'warning' },
  CLAIM_APPROVED: { label: '认领通过', type: 'success' },
  TRANSFER_APPLY: { label: '转移申请', type: 'warning' },
  MANAGER_ASSIGN: { label: '指派归属', type: 'primary' },
  FOLLOW_UP: { label: '跟进记录', type: 'primary' },
  CLIENT_RECYCLE: { label: '回收公海', type: 'danger' },
  CLIENT_RECYCLE_MANUAL: { label: '管理员回收', type: 'danger' },
  CLIENT_SELF_RELEASE: { label: '主动释放', type: 'danger' },
};
function historyActionText(code) {
  return (code && HISTORY_ACTION_MAP[code]?.label) || code || '—';
}
function historyActionTag(code) {
  const t = (code && HISTORY_ACTION_MAP[code]?.type) || 'muted';
  return { info: 'loan-tag-info', primary: 'loan-tag-primary', success: 'loan-tag-success', warning: 'loan-tag-warning', danger: 'loan-tag-danger', muted: 'loan-tag-muted' }[t] || 'loan-tag-muted';
}
async function loadHistory(code) {
  if (!code) {
    historyRows.value = [];
    historyTotal.value = 0;
    return;
  }
  try {
    const res = await getClientHistory(code);
    historyRows.value = res.data?.records || [];
    historyTotal.value = res.data?.total || 0;
  } catch (e) {
    historyRows.value = [];
    historyTotal.value = 0;
  }
}

/** 我的客户列表操作列：查看档案 +（顾问）跟进 / 释放回公海 */
function ownClientActions(row) {
  const actions = [{ key: 'detail', label: '查看档案', onClick: () => openChannelClient(row) }];
  if (['COMPANY_SEA', 'TEAM_SEA'].includes(clientScope.value)) {
    if (userStore.hasPerm(ACTION_PERMISSION.CLIENT_ASSIGN)) {
      actions.push({ key: 'assign-pool', label: '分配归属', type: 'primary', onClick: () => openAssignForRow(row) });
    } else if (userStore.hasPerm(ACTION_PERMISSION.CLIENT_CLAIM)) {
      actions.push({
        key: 'claim-pool',
        label: '认领',
        type: 'success',
        confirm: '确认认领该客户？认领成功后将进入「我的客户」。',
        onClick: () => onClaimPoolClient(row),
      });
    }
  }
  if (clientScope.value === 'TEAM' && canViewTeamAssigned.value) {
    actions.push({
      key: 'recycle-team',
      label: '回收至团队公海',
      type: 'warning',
      confirm: '确认将该客户回收至本团队公海？回收后本团队成员可再次认领。',
      onClick: () => onRecycleListRow(row),
    });
  }
  if (clientScope.value === 'MY' && row.ownerStaffCode === userStore.user?.userNo && !isChannel.value) {
    actions.push({ key: 'follow', label: '跟进', onClick: () => openFollow(row) });
    actions.push({
      key: 'release',
      label: '释放回公海',
      type: 'warning',
      confirm: '确认将该客户释放回公海？释放后不再归属你，冷却期内不可认领。',
      onClick: () => onReleaseOwnClient(row),
    });
  }
  return actions;
}

function openAssignForRow(row) {
  clientCode.value = row.clientCode;
  loadDetail(row.clientCode);
  openAssign();
}

async function onClaimPoolClient(row) {
  try {
    await claimUnassignedClient(row.clientCode);
    ElMessage.success('认领成功，已归入我的客户');
    loadClients();
  } catch (e) { /* 拦截器已提示 */ }
}

async function onRecycleListRow(row) {
  try {
    await recycleClient(row.clientCode);
    ElMessage.success('已回收至团队公海');
    loadClients();
  } catch (e) { /* 拦截器已提示 */ }
}

const followVisible = ref(false);
const following = ref(false);
const followForm = reactive({ clientCode: '', content: '' });
function openFollow(row) {
  followForm.clientCode = row.clientCode;
  followForm.content = '';
  followVisible.value = true;
}
async function onFollowSubmit() {
  following.value = true;
  try {
    await followClient(followForm.clientCode, followForm.content);
    ElMessage.success('跟进已记录');
    followVisible.value = false;
    loadClients();
  } catch (e) { /* 拦截器已提示 */ } finally {
    following.value = false;
  }
}
async function onReleaseOwnClient(row) {
  try {
    await releaseClient(row.clientCode);
    ElMessage.success('已释放回公海');
    loadClients();
  } catch (e) { /* 拦截器已提示 */ }
}

/** 档案详情（后端已脱敏敏感字段，前端再做一层兜底展示） */
const detail = reactive({
  clientCode: '',
  name: '',
  phone: '',
  source: '',
  customerTag: '',
  ownerStaffCode: '',
  ownerStaffName: '',
  authStatus: '',
  enterpriseName: '',
  creditCode: '',
  realName: '',
  idCardNo: '',
  city: '',
  age: null,
  houseFlag: null,
  carFlag: null,
  socialSecurityFlag: null,
  fundFlag: null,
  referrer: '',
  referrerType: '',
  createdBy: '',
  createdByName: '',
  createdAt: '',
  updatedBy: '',
  updatedAt: '',
});
const detailPhonePlain = ref('');
/**
 * 列表手机号的本次会话授权结果。
 * 不写 localStorage、不进入查询参数，也不回填普通列表 DTO，页面刷新后自动清除。
 */
const revealedClientPhones = reactive(new Map());
const phoneQuota = ref({ limit: 30, used: 0, remaining: 30 });
const detailPhoneDisplay = computed(() => detailPhonePlain.value || desensitizePhone(detail.phone));
function clientPhoneDisplay(row) {
  return revealedClientPhones.get(row?.clientCode) || desensitizePhone(row?.phone);
}
async function viewClientPhone(row) {
  const code = row?.clientCode || clientCode.value;
  if (!code) return;
  try {
    const data = (await applyClientPhoneView(code)).data || {};
    if (data.phonePlain) {
      detailPhonePlain.value = data.phonePlain;
      if (row && row !== detail) revealedClientPhones.set(code, data.phonePlain);
      phoneQuota.value = { limit: data.limit, used: data.used, remaining: data.remaining };
      ElMessage.success(`已授权查看，本日剩余 ${data.remaining} 次`);
    } else if (data.approvalNo) {
      ElMessage.warning(data.message || '已提交审批，审批通过后可再次查看');
    }
  } catch (e) { /* 拦截器已提示 */ }
}
async function loadPhoneQuota() {
  try { phoneQuota.value = (await sensitiveQuota()).data || phoneQuota.value; } catch { /* 静默 */ }
}

/** 拉取档案详情（兼容后端平铺 / 嵌套结构，逐字段兜底） */
async function loadDetail(code) {
  loading.value = true;
  try {
    const res = await getClientDetail(code);
    const d = res.data || {};
    const ent = d.enterprise || d.enterpriseInfo || {};
    const per = d.personal || d.personalProfile || {};
    Object.assign(detail, {
      clientCode: d.clientCode || code,
      name: d.name || per.realName || ent.enterpriseName || '',
      phone: d.phone,
      source: d.source,
      customerTag: d.customerTag,
      ownerStaffCode: d.ownerStaffCode,
      ownerStaffName: d.ownerStaffName,
      authStatus: d.authStatus ?? d.certStatus ?? d.verificationStatus,
      enterpriseName: ent.enterpriseName ?? d.enterpriseName,
      creditCode: ent.creditCode ?? d.creditCode,
      realName: per.realName ?? d.realName,
      idCardNo: per.idCardNo ?? d.idCardNo,
      city: per.city ?? d.city,
      age: per.age ?? d.age,
      houseFlag: per.houseFlag ?? d.houseFlag,
      carFlag: per.carFlag ?? d.carFlag,
      socialSecurityFlag: per.socialSecurityFlag ?? d.socialSecurityFlag,
      fundFlag: per.fundFlag ?? d.fundFlag,
      referrer: d.referrer ?? d.invitation?.referrer ?? d.referrerName,
      referrerType: d.referrerType ?? d.invitation?.referrerType,
      createdBy: d.createdBy,
      createdByName: d.createdByName || d.creatorName,
      createdAt: d.createdAt,
      updatedBy: d.updatedBy,
      updatedAt: d.updatedAt,
    });
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false;
  }
}

// ============================================================
// 枚举映射与脱敏兜底（后端已脱敏则原样展示）
// ============================================================
const sourceMap = { MINI: '小程序注册', INVITE: '小程序·邀请', WEB: 'Web 录入', BOSS: '老板', ADVISER: '顾问', CHANNEL: '渠道', VIP: 'VIP 客户', MINI_STAFF_CREATE: '员工移动端录入', SENSITIVE_VIEW_TEST: '测试数据' };
const customerTagMap = { NEW: '新用户', APPOINTED: '已预约', INTENTION: '意向客户', POTENTIAL: '潜在客户', VISITED: '已来访', DEAL: '已成交', NO_ANSWER: '无人接听', NO_NEED: '无需求' };
const customerTagSegmentSpecs = [
  { value: '', label: '全部客户', tone: 'all' },
  { value: 'APPOINTED', label: '已预约', tone: 'info' },
  { value: 'VISITED', label: '已来访', tone: 'visit' },
  { value: 'INTENTION', label: '意向客户', tone: 'intent' },
  { value: 'DEAL', label: '已成交', tone: 'deal' },
  { value: 'NO_ANSWER', label: '无人接听', tone: 'muted' },
  { value: 'NO_NEED', label: '无需求', tone: 'muted' },
];
const customerTagSegments = computed(() => customerTagSegmentSpecs.map((item) => ({
  ...item,
  count: item.value ? Number(tagCounts.value[item.value] || 0) : Number(tagCounts.value.total || 0),
  label: `${item.label}（${item.value ? Number(tagCounts.value[item.value] || 0) : Number(tagCounts.value.total || 0)}）`,
})));
function selectCustomerTag(tag) {
  clientQuery.customerTag = tag;
  clientQuery.page = 1;
  searchClients();
}
function customerTagText(code) { return customerTagMap[code] || (code ? '待标注' : '未标注'); }
function sourceText(code) {
  return sourceMap[code] || code || '-';
}
function sourceTag(code) {
  const m = { MINI: 'loan-tag-info', INVITE: 'loan-tag-primary', WEB: 'loan-tag-muted', BOSS: 'loan-tag-muted', ADVISER: 'loan-tag-primary', CHANNEL: 'loan-tag-info', VIP: 'loan-tag-warning' };
  return m[code] || 'loan-tag-muted';
}

const authStatusMap = { VERIFIED: '已认证', SUCCESS: '已认证', ACTIVE: '已认证', PENDING: '待复核', FAIL: '认证失败', UNVERIFIED: '未认证', NONE: '未认证' };
function authStatusText(code) {
  return authStatusMap[code] || ({ ENTERPRISE_AUTHED: '企业已认证', PERSONAL_AUTHED: '个人已认证' }[code] || (code ? '状态待确认' : '未认证'));
}
function authStatusTag(code) {
  const m = { VERIFIED: 'loan-tag-success', SUCCESS: 'loan-tag-success', ACTIVE: 'loan-tag-success', PENDING: 'loan-tag-warning', FAIL: 'loan-tag-danger', UNVERIFIED: 'loan-tag-muted', NONE: 'loan-tag-muted' };
  return m[code] || 'loan-tag-muted';
}

const referrerTypeMap = { STAFF: '顾问', ADVISER: '顾问', CUSTOMER: '客户', VIP: 'VIP 客户' };
function referrerTypeText(code) {
  return referrerTypeMap[code] || code || '';
}
const referrerText = computed(() => {
  const r = detail.referrer;
  if (!r) return '-';
  if (typeof r === 'string') return r;
  if (typeof r === 'object') return r.name || r.referrerName || r.referrerClientCode || r.staffName || '-';
  return String(r);
});

function flagText(v) {
  if (v === null || v === undefined || v === '') return '—';
  if (v === true || v === 'true' || v === 'Y' || v === 'y' || v === '1' || Number(v) === 1) return '是';
  return '否';
}
/** 身份证脱敏：18 位身份证 前3后4 星号；后端已脱敏（含*）则原样返回 */
function desensitizeIdCard(v) {
  if (!v) return '-';
  if (/[*＊]/.test(v)) return v;
  const s = String(v).trim();
  if (/^\d{15}(\d{2}[\dXx])?$/.test(s)) return `${s.slice(0, 3)}********${s.slice(-4)}`;
  return s;
}
/** 姓名脱敏：2 字「*名」，3 字「*名」，其余首尾保留 */
function desensitizeName(v) {
  if (!v) return '-';
  if (/[*＊]/.test(v)) return v;
  const s = String(v).trim();
  if (s.length <= 1) return s;
  if (s.length === 2) return `*${s.slice(-1)}`;
  return `${s[0]}${'*'.repeat(s.length - 2)}${s.slice(-1)}`;
}
/** 统一社会信用代码脱敏：18 位 前3后4 */
function desensitizeCreditCode(v) {
  if (!v) return '-';
  if (/[*＊]/.test(v)) return v;
  const s = String(v).trim();
  if (s.length >= 12) return `${s.slice(0, 3)}********${s.slice(-4)}`;
  return s;
}

// ============================================================
// 编辑档案
// ============================================================
const editVisible = ref(false);
const saving = ref(false);
const editFormRef = ref();
const editForm = reactive({
  contactName: '',
  source: '',
  customerTag: '',
  realName: '',
  idCardNo: '',
  city: '',
  age: null,
  houseFlag: null,
  carFlag: null,
  socialSecurityFlag: null,
  fundFlag: null,
});
const flagFields = [
  { key: 'houseFlag', label: '房产' },
  { key: 'carFlag', label: '车辆' },
  { key: 'socialSecurityFlag', label: '社保' },
  { key: 'fundFlag', label: '公积金' },
];
const editRules = {};

function openEdit() {
  Object.assign(editForm, {
    contactName: detail.name,
    source: detail.source,
    customerTag: detail.customerTag,
    realName: detail.realName,
    idCardNo: detail.idCardNo,
    city: detail.city,
    age: detail.age ?? null,
    houseFlag: detail.houseFlag ?? null,
    carFlag: detail.carFlag ?? null,
    socialSecurityFlag: detail.socialSecurityFlag ?? null,
    fundFlag: detail.fundFlag ?? null,
  });
  editVisible.value = true;
}

async function onSave() {
  await editFormRef.value.validate();
  saving.value = true;
  try {
    await updateClientDetail(clientCode.value, { ...editForm });
    ElMessage.success('档案已更新');
    editVisible.value = false;
    loadDetail(clientCode.value);
  } catch (e) {
    // 拦截器已提示
  } finally {
    saving.value = false;
  }
}

/** 发起初筛：跳初筛中心并预填客户 */
function goScreening() {
  router.push({ path: '/screening', query: { clientCode: clientCode.value } });
}

// ============================================================
// 分配归属 / 回收（C23 / C26，角色门控：DEPT_MANAGER/BOSS/OPERATOR/SUPER_ADMIN/SUPER）
// ============================================================
const assignVisible = ref(false);
const assigning = ref(false);
const assignTarget = ref('');
const assignOptions = ref([]);
const assignLoading = ref(false);
let assignSeq = 0;
let assignTimer;

function openAssign() {
  assignTarget.value = '';
  assignOptions.value = [];
  assignVisible.value = true;
  searchAssignStaff('');
}
function searchAssignStaff(keyword) {
  clearTimeout(assignTimer);
  assignTimer = setTimeout(async () => {
    const seq = ++assignSeq;
    assignLoading.value = true;
    try {
      const query = { keyword: keyword.trim() || undefined, page: 1, size: 50 };
      const [advisers, managers] = await Promise.all([
        staffPage({ ...query, roleCode: 'ADVISER' }),
        staffPage({ ...query, roleCode: 'DEPT_MANAGER' }),
      ]);
      if (seq !== assignSeq) return;
      const merged = new Map();
      [...(advisers.data?.records || []), ...(managers.data?.records || [])].forEach((s) => merged.set(s.staffCode, s));
      assignOptions.value = [...merged.values()].map((s) => ({
        staffCode: s.staffCode,
        staffName: s.staffName,
        deptName: s.deptName,
        roleCode: s.roleCode,
        roleName: s.roleName,
        status: s.status,
      }));
    } catch (e) {
      if (seq === assignSeq) assignOptions.value = [];
    } finally {
      if (seq === assignSeq) assignLoading.value = false;
    }
  }, 250);
}
async function onAssignConfirm() {
  const picked = assignOptions.value.find((o) => o.staffCode === assignTarget.value);
  if (!assignTarget.value) {
    ElMessage.warning('请选择目标归属人');
    return;
  }
  try {
    await ElMessageBox.confirm(
      `确认将客户【${detail.enterpriseName || detail.realName || detail.name || '未命名客户'}】的归属调整为「${picked ? picked.staffName : '目标顾问'}」？此操作立即生效，无需审核。`,
      '分配归属确认',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  assigning.value = true;
  try {
    await assignClient(clientCode.value, assignTarget.value);
    ElMessage.success('归属已更新');
    assignVisible.value = false;
    loadDetail(clientCode.value);
  } catch (e) { /* 拦截器已提示 */ } finally { assigning.value = false; }
}
async function onRecycle() {
  try {
    await ElMessageBox.confirm(
      '确认回收该客户进公海？原归属将清空，冷却期内不可认领。',
      '回收确认',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  try {
    await recycleClient(clientCode.value);
    ElMessage.success('已回收进公海');
    loadDetail(clientCode.value);
  } catch (e) { /* 拦截器已提示 */ }
}

// 路由参数 clientCode（query 或 path 参数均可）变化时重载
// 所有角色均可看到客户列表（D67）：未选中客户时加载列表，选中时加载档案 + 历史
watch(
  () => [route.query.clientCode || route.params.clientCode, route.meta.clientScope, route.query.scope],
  ([code]) => {
    if (code) {
      clientCode.value = code;
      detailPhonePlain.value = '';
      loadDetail(code);
      loadPhoneQuota();
      loadHistory(code);
      return;
    }
    clientCode.value = '';
      clientScope.value = resolveRouteScope();
      clientQuery.scope = clientScope.value;
      loadFilterDepartments();
      loadClientTagCounts();
    loadHistory('');
    loadClients();
  },
  { immediate: true },
);
</script>

<style scoped>
.header-actions {
  display: flex;
  gap: 10px;
}
.customer-segment-bar { display:flex; align-items:center; gap:8px; flex-wrap:wrap; padding:12px 0 14px; border-bottom:1px solid var(--loan-border); margin-bottom:14px; }
.customer-segment { display:inline-flex; align-items:center; gap:6px; padding:7px 12px; border:1px solid var(--loan-border); border-radius:999px; background:var(--loan-surface); color:var(--loan-text-secondary); cursor:pointer; transition:all .18s ease; }
.customer-segment:hover, .customer-segment.active { border-color:var(--loan-primary); background:var(--loan-primary-soft); color:var(--loan-primary); }
.segment-dot { width:7px; height:7px; border-radius:50%; background:var(--loan-text-muted); }
.segment-dot--info { background:#4f7cff; }.segment-dot--visit { background:#7c5cff; }.segment-dot--intent { background:#e6a23c; }.segment-dot--deal { background:#21a366; }.segment-dot--muted { background:#9aa4b2; }.segment-dot--all { background:var(--loan-primary); }
.segment-hint { margin-left:auto; color:var(--loan-text-muted); font-size:12px; }
.customer-row-tag { margin:2px 4px 2px 0; display:inline-block; }
.profile-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 200px;
}
.section-card {
  padding: 18px 22px;
}
.cell-sub {
  font-size: 12px;
  color: var(--loan-text-secondary, var(--loan-text-muted));
}
.phone-revealed-label {
  margin-left: 8px;
  color: var(--loan-success-text, var(--loan-success));
  font-size: 12px;
}
.panel-sub {
  margin-left: 8px;
  font-size: 12px;
  font-weight: 400;
  color: var(--loan-text-secondary, var(--loan-text-muted));
}
.muted {
  color: var(--loan-text-secondary, var(--loan-text-muted));
}
.flag-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
}
.flag-grid .el-form-item { min-width: 0; }
.flag-grid .el-select,
.flag-grid .el-input { width: 100%; }
.assign-dialog-hint {
  margin: 0 0 18px;
  padding: 10px 12px;
  border-radius: 8px;
  color: var(--loan-text-secondary, var(--loan-text-muted));
  background: var(--loan-fill-light, rgba(148, 163, 184, 0.1));
  font-size: 13px;
  line-height: 1.6;
}
</style>
