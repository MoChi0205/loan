<template>
  <div class="org-page">
    <div class="loan-page-header">
      <div>
        <h2 class="loan-page-title">{{ { staff: '员工管理', role: '角色权限', api: '接口权限' }[activeTab] }}</h2>
        <p class="loan-page-subtitle">{{ { staff: '部门与员工账号管理', role: '角色菜单与操作权限配置', api: '网关接口访问权限配置' }[activeTab] }}</p>
      </div>
    </div>

    <!-- ============ 员工管理 ============ -->
    <div v-show="activeTab === 'staff'" class="org-body">
      <div class="loan-card dept-card">
        <div class="dept-head">
          <span class="panel-title">部门</span>
          <el-button size="small" type="primary" plain @click="openDept()">
            <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2" style="margin-right: 2px; vertical-align: -1px"><path d="M12 5v14M5 12h14"/></svg>
            新增
          </el-button>
        </div>
        <el-tree
          :data="deptTree"
          :props="treeProps"
          node-key="code"
          highlight-current
          @node-click="onDeptClick"
        >
          <template #default="{ node, data }">
            <div class="dept-node">
              <span>{{ data.name }}</span>
              <el-dropdown trigger="click" @command="(cmd) => onDeptCmd(cmd, data)" style="margin-left: 6px">
                <span class="dept-more">···</span>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="edit">编辑</el-dropdown-item>
                    <el-dropdown-item command="disable" divided>停用</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-tree>
      </div>

      <div class="loan-card">
        <AppSearchBar :loading="loading" @search="onSearch" @reset="onReset">
          <el-select v-model="query.roleCode" placeholder="角色" clearable style="width: 130px">
            <el-option v-for="r in roles" :key="r.roleCode" :label="r.roleName" :value="r.roleCode" />
          </el-select>
          <el-input v-model="query.keyword" placeholder="姓名 / 手机号" clearable style="width: 200px" @keyup.enter="onSearch" />
          <el-select v-model="query.status" placeholder="在职状态" clearable style="width: 130px">
            <el-option label="在职" value="ACTIVE" />
            <el-option label="离职" value="LEAVE" />
          </el-select>
          <template #append>
            <el-button type="primary" plain @click="openStaff()">
              <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" style="margin-right: 4px; vertical-align: -2px"><path d="M12 5v14M5 12h14"/></svg>
              新增员工
            </el-button>
          </template>
        </AppSearchBar>

        <el-table :data="staffs" v-loading="loading" stripe row-key="id" @sort-change="handleSortChange">
          <el-table-column prop="staffName" label="姓名" min-width="130" />
          <el-table-column prop="deptName" label="部门" width="130">
            <template #default="{ row }">{{ row.deptName || '—' }}</template>
          </el-table-column>
          <el-table-column label="角色" width="110">
            <template #default="{ row }">
              <span class="loan-tag" :class="roleTag(row.roleCode)">{{ row.roleName || '角色名称待补充' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="手机号" min-width="130">
            <template #default="{ row }">{{ desensitizePhone(row.phone) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <AppTableActions :actions="[
                { key: 'edit', label: '编辑', onClick: () => openStaff(row) },
                { key: 'leave', label: '离职', type: 'danger', confirm: `确认将「${row.staffName}」置为离职停用？离职后其账号将停用，名下未跟进线索将回收至公海，且无法再登录系统。`, onClick: () => onStaffLeave(row) },
              ]" />
            </template>
          </el-table-column>
        </el-table>

        <AppPagination v-model:page="query.page" v-model:size="query.size" :total="total" @change="load" />
      </div>
    </div>

    <!-- ============ 角色权限 ============ -->
    <div v-show="activeTab === 'role'" class="loan-card">
      <div class="role-grid">
        <div v-for="r in roles" :key="r.roleCode" class="role-card">
          <div class="role-card__head">
            <span class="role-code">{{ r.roleName || '角色名称待补充' }}</span>
            <span class="loan-tag loan-tag-success">启用</span>
          </div>
          <div class="role-name">{{ r.roleName }}</div>
          <div class="role-desc">{{ r.description || '—' }}</div>
          <div class="role-actions">
            <el-button size="small" type="primary" plain @click="openPerm(r)">配置权限</el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 员工弹窗 -->
    <AppDialog v-model:visible="staffVisible" :title="editingStaff ? '编辑员工' : '新增员工'" :loading="savingStaff" @confirm="onSaveStaff">
      <el-form ref="staffFormRef" :model="staffForm" :rules="staffRules" label-width="110px" label-position="right">
        <el-form-item label="工号" prop="staffCode">
          <el-input v-model="staffForm.staffCode" :disabled="editingStaff" placeholder="如 ADV002" />
        </el-form-item>
        <el-form-item label="CRM员工ID" prop="crmUserId">
          <el-input v-model="staffForm.crmUserId" :disabled="editingStaff" placeholder="SSO 映射键" />
        </el-form-item>
        <el-form-item label="姓名" prop="staffName">
          <el-input v-model="staffForm.staffName" placeholder="员工姓名" />
        </el-form-item>
        <el-form-item label="部门">
          <AppSelect v-model="staffForm.deptCode" clearable filterable placeholder="选择部门" style="width: 100%">
            <el-option v-for="d in deptFlat" :key="d.code" :label="d.name" :value="d.code" />
          </AppSelect>
        </el-form-item>
        <el-form-item label="角色" prop="roleCode">
          <AppSelect v-model="staffForm.roleCode" style="width: 100%">
            <el-option v-for="r in roles" :key="r.roleCode" :label="r.roleName" :value="r.roleCode" />
          </AppSelect>
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="staffForm.phone" placeholder="11 位手机号" />
        </el-form-item>
        <el-form-item label="企微二维码">
          <el-input v-model="staffForm.wecomQrCode" placeholder="可选" />
        </el-form-item>
      </el-form>
    </AppDialog>

    <!-- 部门弹窗 -->
    <AppDialog v-model:visible="deptVisible" :title="editingDept ? '编辑部门' : '新增部门'" :loading="savingDept" @confirm="onSaveDept">
      <el-form ref="deptFormRef" :model="deptForm" :rules="deptRules" label-width="100px" label-position="right">
        <el-form-item label="部门编码" prop="deptCode">
          <el-input v-model="deptForm.deptCode" :disabled="editingDept" maxlength="32" show-word-limit placeholder="如 CONSULT（不超过32个字符）" />
        </el-form-item>
        <el-form-item label="部门名称" prop="deptName">
          <el-input v-model="deptForm.deptName" placeholder="如 咨询部" />
        </el-form-item>
        <el-form-item label="上级部门">
          <AppSelect v-model="deptForm.parentCode" clearable filterable placeholder="顶级" style="width: 100%">
            <el-option v-for="d in deptFlat" :key="d.code" :label="d.name" :value="d.code" />
          </AppSelect>
        </el-form-item>
        <el-form-item label="负责人">
          <el-input v-model="deptForm.leaderStaffCode" placeholder="负责人工号" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="deptForm.sort" :min="0" :controls="false" style="width: 140px" />
        </el-form-item>
      </el-form>
    </AppDialog>

    <!-- 权限配置弹窗 -->
    <AppDialog v-model:visible="permVisible" :title="`配置权限：${permRole?.roleName || ''}`" :loading="savingPerm" @confirm="onSavePerm">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px">
        勾选该角色可访问的菜单与操作按钮（祖先菜单自动带入）
      </el-alert>
      <el-tree
        ref="permTreeRef"
        :data="menuTree"
        :props="treeProps"
        node-key="id"
        show-checkbox
        default-expand-all
        style="max-height: 420px; overflow: auto"
      />
    </AppDialog>

    <!-- ============ 接口权限 ============ -->
    <div v-show="activeTab === 'api'" class="api-perm-body">
      <div class="loan-card api-role-card">
        <div class="dept-head">
          <span class="panel-title">角色</span>
        </div>
        <div class="api-role-list">
          <div
            v-for="r in roles"
            :key="r.roleCode"
            class="api-role-item"
            :class="{ active: apiRoleCode === r.roleCode }"
            @click="onApiRoleSelect(r.roleCode)"
          >
            <span class="loan-tag" :class="roleTag(r.roleCode)">{{ r.roleName }}</span>
          </div>
        </div>
      </div>

      <div class="loan-card">
        <div class="dept-head" style="margin-bottom: 10px">
          <span class="panel-title">
            接口授权：{{ currentApiRole?.roleName || '角色名称待补充' }}
          </span>
          <div>
            <el-button size="small" :loading="apiLoading" @click="loadApiPerms">
              <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2" style="margin-right: 4px; vertical-align: -1px"><path d="M21 12a9 9 0 11-2.6-6.4"/><path d="M21 3v6h-6"/></svg>
              刷新
            </el-button>
            <el-button size="small" type="primary" plain :loading="apiSaving" @click="onSaveApiPerm">保存授权</el-button>
          </div>
        </div>
        <el-alert v-if="apiRoleCode === 'BOSS'" type="warning" :closable="false" show-icon style="margin-bottom: 10px">
          老板为超级角色，默认拥有全部接口权限（无需配置，不可修改）
        </el-alert>
        <el-alert type="info" :closable="false" show-icon style="margin-bottom: 10px">
          勾选该角色可访问的接口；网关按「角色 × 接口 × 端(WEB/小程序)」统一鉴权，未勾选接口将被拦截
        </el-alert>
        <el-input
          v-model="apiSearch"
          placeholder="搜索接口（中文名称 / apiKey / 路径）"
          clearable
          style="max-width: 340px; margin-bottom: 10px"
        >
          <template #prefix>
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.7"><circle cx="11" cy="11" r="7"/><path d="M21 21l-4.3-4.3"/></svg>
          </template>
        </el-input>
        <el-tree
          ref="apiTreeRef"
          :data="apiTree"
          :props="treeProps"
          node-key="key"
          show-checkbox
          :filter-node-method="filterApiNode"
          style="max-height: 460px; overflow: auto"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import AppSelect from '@/components/AppSelect.vue';
defineOptions({ name: '_org' });
import { ref, reactive, computed, onMounted, nextTick, watch } from 'vue';
import { useRoute } from 'vue-router';
import { ElMessage } from 'element-plus';
import AppSearchBar from '@/components/AppSearchBar.vue';
import AppPagination from '@/components/AppPagination.vue';
import AppTableActions from '@/components/AppTableActions.vue';
import AppDialog from '@/components/AppDialog.vue';
import { useTable } from '@/composables/useTable';
import { desensitizePhone } from '@/utils/format';
import { departmentTree, roleList, staffPage, menuTreeAll as fetchMenuTreeAll, rolePermissionMenuIds as fetchRolePermissionMenuIds, saveStaff, disableStaff, saveDepartment, disableDepartment, saveRolePermission } from '@/api/org';
import { pageApiPerm, roleApiPerm, saveRoleApiPerm } from '@/api/apiperm';

const route = useRoute();
const activeTab = ref(String(route.meta.orgView || 'staff'));
watch(() => route.fullPath, () => {
  const next = String(route.meta.orgView || route.query.view || 'staff');
  if (next !== activeTab.value) activeTab.value = next;
});
const deptTree = ref([]);
const roles = ref([]);
const menuTree = ref([]);
const currentDeptCode = ref(null);
const treeProps = { label: 'name', children: 'children' };

/** 员工列表 */
const { loading, data: staffs, total, query, load, onSearch, onReset, handleSortChange } = useTable(
  (q) => staffPage({ ...q, deptCode: currentDeptCode.value }),
  { roleCode: '', keyword: '', status: '' },
);
watch(activeTab, (tab) => {
  if (tab === 'staff') load();
  if (tab === 'api') loadApiPerms();
});

function onDeptClick(data) {
  currentDeptCode.value = data.code;
  query.page = 1;
  load();
}

/** 部门扁平列表（下拉用） */
const deptFlat = computed(() => {
  const out = [];
  const walk = (list) => {
    list.forEach((d) => {
      out.push(d);
      if (d.children?.length) walk(d.children);
    });
  };
  walk(deptTree.value || []);
  return out;
});

function roleTag(roleCode) {
  const m = { BOSS: 'loan-tag-danger', DEPT_MANAGER: 'loan-tag-warning', ADVISER: 'loan-tag-info' };
  return m[roleCode] || 'loan-tag-muted';
}

// ============================================================
// 员工新增/编辑
// ============================================================
const staffVisible = ref(false);
const savingStaff = ref(false);
const editingStaff = ref(false);
const staffFormRef = ref();
const staffForm = reactive({
  staffCode: '', crmUserId: '', staffName: '', deptCode: '', roleCode: 'ADVISER', phone: '', wecomQrCode: '',
});
const staffRules = {
  staffCode: [{ required: true, message: '请输入工号', trigger: 'blur' }],
  crmUserId: [{ required: true, message: '请输入 CRM 员工 ID', trigger: 'blur' }],
  staffName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  roleCode: [{ required: true, message: '请选择角色', trigger: 'change' }],
};

function openStaff(row) {
  editingStaff.value = !!row;
  Object.assign(staffForm, {
    staffCode: row?.staffCode || '',
    crmUserId: row?.crmUserId || '',
    staffName: row?.staffName || '',
    deptCode: row?.deptCode || '',
    roleCode: row?.roleCode || 'ADVISER',
    phone: row?.phone || '',
    wecomQrCode: row?.wecomQrCode || '',
  });
  staffVisible.value = true;
}

async function onSaveStaff() {
  await staffFormRef.value.validate();
  savingStaff.value = true;
  try {
    await saveStaff({ ...staffForm, phone: staffForm.phone || null });
    ElMessage.success('保存成功');
    staffVisible.value = false;
    load();
  } catch (e) { /* 拦截器已提示 */ } finally {
    savingStaff.value = false;
  }
}

async function onStaffLeave(row) {
  try {
    await disableStaff({ staffCode: row.staffCode });
    ElMessage.success('已置为离职停用');
    load();
  } catch (e) { /* 拦截器已提示 */ }
}

// ============================================================
// 部门新增/编辑/停用
// ============================================================
const deptVisible = ref(false);
const savingDept = ref(false);
const editingDept = ref(false);
const deptFormRef = ref();
const deptForm = reactive({ deptCode: '', deptName: '', parentCode: '', leaderStaffCode: '', sort: 0 });
const deptRules = {
  deptCode: [{ required: true, message: '请输入部门编码', trigger: 'blur' }, { max: 32, message: '部门编码不能超过32个字符', trigger: 'blur' }],
  deptName: [{ required: true, message: '请输入部门名称', trigger: 'blur' }, { max: 64, message: '部门名称不能超过64个字符', trigger: 'blur' }],
};

function openDept(data) {
  editingDept.value = !!data;
  Object.assign(deptForm, {
    deptCode: data?.code || '',
    deptName: data?.name || '',
    parentCode: data?.parentCode || '',
    leaderStaffCode: data?.leaderStaffCode || '',
    sort: data?.sort ?? 0,
  });
  deptVisible.value = true;
}

async function onSaveDept() {
  await deptFormRef.value.validate();
  savingDept.value = true;
  try {
    await saveDepartment({ ...deptForm });
    ElMessage.success('保存成功');
    deptVisible.value = false;
    reloadDept();
  } catch (e) { /* 拦截器已提示 */ } finally {
    savingDept.value = false;
  }
}

async function onDeptCmd(cmd, data) {
  if (cmd === 'edit') {
    openDept(data);
  } else if (cmd === 'disable') {
    try {
      await disableDepartment({ deptCode: data.code });
      ElMessage.success('已停用');
      reloadDept();
    } catch (e) { /* 拦截器已提示 */ }
  }
}

// ============================================================
// 角色权限配置
// ============================================================
const permVisible = ref(false);
const savingPerm = ref(false);
const permRole = ref(null);
const permTreeRef = ref();

function openPerm(role) {
  permRole.value = role;
  permVisible.value = true;
  nextTick(async () => {
    if (!permTreeRef.value) return;
    // 回显：拉取该角色已授权 menuId 后勾选
    try {
      const res = await fetchRolePermissionMenuIds(role.roleCode);
      permTreeRef.value.setCheckedKeys(res.data || []);
    } catch (e) {
      permTreeRef.value.setCheckedKeys([]);
    }
  });
}

async function onSavePerm() {
  const checked = permTreeRef.value ? permTreeRef.value.getCheckedKeys(true) : [];
  savingPerm.value = true;
  try {
    await saveRolePermission({ roleCode: permRole.value.roleCode, menuIds: checked, permissionCodes: [] });
    ElMessage.success('权限已保存');
    permVisible.value = false;
  } catch (e) { /* 拦截器已提示 */ } finally {
    savingPerm.value = false;
  }
}

async function reloadDept() {
  try {
    const res = await departmentTree();
    deptTree.value = res.data || [];
  } catch (e) { /* 拦截器已提示 */ }
}

// ============================================================
// 接口权限（网关鉴权配置）
// ============================================================
const apiRoleCode = ref('');
const currentApiRole = ref(null);
const apiAll = ref([]);
const apiTree = ref([]);
const apiCheckedKeys = ref([]);
const apiLoading = ref(false);
const apiSaving = ref(false);
const apiTreeRef = ref();

/** 角色选中 */
function onApiRoleSelect(roleCode) {
  apiRoleCode.value = roleCode;
  currentApiRole.value = (roles.value || []).find((r) => r.roleCode === roleCode) || null;
  loadApiPerms();
}

/* ---- 接口中文语义命名：apiKey 段位 → 模块 / 动作中文 ---- */
const API_MODULE_ZH = {
  mini: '小程序端', auth: '认证', appointment: '预约', audit: '审计', blacklist: '风控名单',
  client: '客户', lead: '线索', order: '工单', product: '产品', channel: '渠道',
  approval: '审批', report: '报表', rule: '规则', strategy: '策略', plan: '执行计划',
  org: '组织', staff: '员工', role: '角色', menu: '菜单', notification: '通知',
  reward: '奖励', sms: '短信', ocr: '材料识别', screening: '智能匹配', service: '服务',
  import: '导入', exporting: '导出', allocation: '分配', recycle: '回收', config: '配置',
  workbench: '工作台', wechat: '微信', upload: '上传', dict: '字典', sensitive: '敏感信息',
  phone: '手机号', outing: '外出', download: '下载', memory: '备忘', insight: '洞察',
  gateway: '网关', dept: '部门', permission: '权限', api: '接口', user: '用户',
  quick: '快捷', common: '通用', export: '导出', template: '模板', tag: '标签',
  profile: '档案', personal: '个人', ent: '企业', pool: '公海', sea: '公海',
  team: '团队', mine: '我的', insightSnapshot: '客户洞察', cooperate: '合作库',
};
const API_ACTION_ZH = {
  page: '分页查询', list: '列表查询', detail: '详情', get: '查询', create: '新增', add: '新增',
  save: '保存', update: '修改', edit: '编辑', delete: '删除', remove: '删除', audit: '审核',
  approve: '审批通过', reject: '驳回', confirm: '确认', cancel: '取消', close: '关闭',
  toggle: '启停', enable: '启用', disable: '停用', export: '导出', sync: '同步',
  send: '发送', claim: '认领', assign: '分配', recycle: '回收', release: '释放',
  transfer: '交接', batch: '批量', count: '计数', stats: '统计', tree: '树形结构',
  preview: '预览', verify: '校验', check: '检查', apply: '申请', submit: '提交',
  withdraw: '撤回', request: '申请', reschedule: '改期', schedule: '排期', login: '登录',
  code: '验证码', read: '标记已读', unread: '未读数', execute: '执行', replay: '回放',
  publish: '发布', generate: '生成', match: '匹配', recommend: '推荐', refresh: '刷新',
  reset: '重置', active: '生效', activate: '激活', offline: '下线', freeze: '冻结',
  unfreeze: '解冻', flag: '标记', mark: '标记', logs: '日志', log: '日志', rule: '规则',
  rules: '规则', snapshot: '快照', trace: '轨迹', traces: '轨迹', reveal: '查看原值',
  'read-all': '全部已读', 'delete-all': '全部删除', 'delete-batch': '批量删除',
  'phone-login': '手机号登录', 'wechat-login': '微信登录', 'wechat-code': '微信换码',
  'enterprise-page': '企业分页', 'field-defs': '字段定义', 'save-record': '保存记录',
  'save-template': '保存模板', 'template-page': '模板分页', 'template-list': '模板列表',
  'record-page': '记录分页', 'role-list': '角色列表', 'permission-list': '权限清单',
  'staff-page': '员工分页', 'department-tree': '部门树', 'order-trend': '订单趋势',
  'reward-trend': '奖励趋势', 'overview-report': '经营总览', 'operations-report': '经营报表',
};
/** apiKey → 中文语义名：模块段 + 动作段拼接，未命中保留原文。 */
function apiZhName(apiKey) {
  return String(apiKey).split(':')
    .map((seg) => API_MODULE_ZH[seg] || API_ACTION_ZH[seg] || seg)
    .join(' · ');
}

/** 接口搜索关键字 */
const apiSearch = ref('');
watch(apiSearch, (val) => {
  apiTreeRef.value?.filter(val);
});
/** 树过滤：组节点按组名，接口节点按中文名 / apiKey / 路径。 */
function filterApiNode(value, data) {
  if (!value) return true;
  const kw = value.trim().toLowerCase();
  if (!kw) return true;
  if (!data.children) {
    const hay = `${data.name || ''} ${data.apiKey || ''}`.toLowerCase();
    return hay.includes(kw);
  }
  return (data.name || '').toLowerCase().includes(kw)
    || (data.children || []).some((c) => filterApiNode(value, c));
}

/** 加载接口清单（分组树） */
async function loadApiPerms() {
  if (!apiRoleCode.value) return;
  apiLoading.value = true;
  try {
    const [allRes, roleRes] = await Promise.all([
      pageApiPerm({ page: 1, size: 500 }),
      roleApiPerm(apiRoleCode.value),
    ]);
    const list = allRes.data?.records || [];
    apiAll.value = list;
    // 按 moduleGroup 分组为树
    const groupMap = {};
    list.forEach((a) => {
      const g = a.moduleGroup || '公共';
      if (!groupMap[g]) groupMap[g] = { key: 'g_' + g, name: g, children: [] };
      groupMap[g].children.push({
        key: 'a_' + a.apiKey,
        apiKey: a.apiKey,
        name: `${a.remark || apiZhName(a.apiKey)}  [${a.httpMethod}] ${a.pathPattern}`,
        clientTypes: a.clientTypes,
        disabled: a.status !== 'ACTIVE',
      });
    });
    apiTree.value = Object.keys(groupMap).map((k) => groupMap[k]);
    const checked = (roleRes.data || []).map((k) => 'a_' + k);
    apiCheckedKeys.value = checked;
    // nextTick 后设置勾选
    nextTick(() => {
      if (apiTreeRef.value) {
        apiTreeRef.value.setCheckedKeys(checked);
      }
    });
  } catch (e) { /* 拦截器已提示 */ } finally {
    apiLoading.value = false;
  }
}

/** 保存角色接口授权 */
async function onSaveApiPerm() {
  if (!apiRoleCode.value || apiRoleCode.value === 'BOSS') return;
  const checked = apiTreeRef.value ? apiTreeRef.value.getCheckedKeys(true).map((k) => String(k).replace(/^a_/, '')) : [];
  apiSaving.value = true;
  try {
    await saveRoleApiPerm(apiRoleCode.value, checked);
    ElMessage.success('接口授权已保存，网关即时生效');
  } catch (e) { /* 拦截器已提示 */ } finally {
    apiSaving.value = false;
  }
}

onMounted(async () => {
  reloadDept();
  try {
    const [role, menu] = await Promise.all([roleList(), fetchMenuTreeAll()]);
    roles.value = role.data || [];
    menuTree.value = menu.data || [];
  } catch (e) { /* 拦截器已提示 */ }
  load();
});
</script>

<style scoped>
.org-body {
  display: grid;
  grid-template-columns: 260px 1fr;
  gap: 16px;
  align-items: start;
}
.api-perm-body {
  display: grid;
  grid-template-columns: 220px 1fr;
  gap: 16px;
  align-items: start;
}
.api-role-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 8px;
}
.api-role-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 10px;
  border: 1px solid var(--loan-border);
  border-radius: 8px;
  cursor: pointer;
  transition: all var(--loan-transition);
}
.api-role-item .loan-tag {
  white-space: nowrap;
  flex-shrink: 0;
}
.api-role-item:hover {
  border-color: var(--loan-primary);
}
.api-role-item.active {
  border-color: var(--loan-primary);
  background: var(--loan-primary-soft);
}
.api-role-code {
  font-size: 12px;
  color: var(--loan-text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  min-width: 0;
}

/* 部门树：节点文字不截断（短名"市场部"等被 el-tree 默认 ellipsis 误伤） */
.org-body :deep(.el-tree-node__content) {
  white-space: normal !important;
  height: auto;
  min-height: 28px;
  align-items: center;
}
.org-body :deep(.el-tree-node__content > .el-tree-node__expand-icon) {
  margin-top: 0;
}
/* 深色模式：部门树文字、背景、高亮强制使用主题变量（el-tree 默认不跟随 data-theme） */
.org-body :deep(.el-tree) {
  background: transparent;
  color: var(--loan-text);
}
.org-body :deep(.el-tree-node__content:hover) {
  background: var(--loan-surface);
}
.org-body :deep(.el-tree-node.is-current > .el-tree-node__content) {
  background: var(--loan-primary-soft);
  color: var(--loan-primary);
}
.org-body :deep(.el-tree-node__label) {
  color: inherit;
}
/* 接口权限/推荐奖励等 tab 也保护 */
.api-perm-body :deep(.el-tree-node__content) {
  white-space: normal !important;
  min-height: 28px;
  align-items: center;
}
/* 深色模式：接口权限树 */
.api-perm-body :deep(.el-tree) {
  background: transparent;
  color: var(--loan-text);
}
.api-perm-body :deep(.el-tree-node__content:hover) {
  background: var(--loan-surface);
}
@media (max-width: 900px) {
  .org-body,
  .api-perm-body {
    grid-template-columns: 1fr;
  }
}
.panel-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--loan-text);
  margin: 0;
}
.dept-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--loan-border);
}
.dept-node {
  display: flex;
  align-items: center;
  font-size: 14px;
  color: var(--loan-text);
  flex: 1;
}
.dept-more {
  font-size: 14px;
  color: var(--loan-text-secondary);
  cursor: pointer;
  padding: 0 4px;
  border-radius: 4px;
}
.dept-more:hover {
  background: var(--loan-surface);
  color: var(--loan-primary);
}
.mono {
  font-family: "SF Mono", Menlo, Consolas, monospace;
}
.role-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 12px;
}
.role-card {
  border: 1px solid var(--loan-border);
  border-radius: var(--loan-radius-md);
  padding: 16px;
  background: var(--loan-card-bg);
}
.role-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.role-code {
  font-weight: 700;
  color: var(--loan-primary);
}
.role-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--loan-text);
  margin-bottom: 4px;
}
.role-desc {
  font-size: 12px;
  color: var(--loan-text-secondary);
  min-height: 32px;
}
.role-actions {
  margin-top: 12px;
}
</style>
