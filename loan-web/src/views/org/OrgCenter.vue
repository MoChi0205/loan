<template>
  <div class="org-page">
    <div class="loan-page-header">
      <div>
        <h2 class="loan-page-title">组织权限</h2>
        <p class="loan-page-subtitle">部门树 / 员工管理 / 角色权限配置</p>
      </div>
    </div>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="员工管理" name="staff" />
      <el-tab-pane label="角色权限" name="role" />
      <el-tab-pane label="接口权限" name="api" />
    </el-tabs>

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
          <el-input v-model="query.keyword" placeholder="姓名 / 工号" clearable style="width: 200px" @keyup.enter="onSearch" />
          <template #append>
            <el-button type="primary" plain @click="openStaff()">
              <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" style="margin-right: 4px; vertical-align: -2px"><path d="M12 5v14M5 12h14"/></svg>
              新增员工
            </el-button>
          </template>
        </AppSearchBar>

        <el-table :data="staffs" v-loading="loading" stripe row-key="id" @sort-change="handleSortChange">
          <el-table-column prop="staffCode" label="工号" width="110"  show-overflow-tooltip />
          <el-table-column prop="staffName" label="姓名" min-width="130" />
          <el-table-column prop="deptName" label="部门" width="130">
            <template #default="{ row }">{{ row.deptName || '—' }}</template>
          </el-table-column>
          <el-table-column label="角色" width="110">
            <template #default="{ row }">
              <span class="loan-tag" :class="roleTag(row.roleCode)">{{ row.roleName || row.roleCode }}</span>
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
            <span class="role-code mono">{{ r.roleCode }}</span>
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
          <el-select v-model="staffForm.deptCode" clearable filterable placeholder="选择部门" style="width: 100%">
            <el-option v-for="d in deptFlat" :key="d.code" :label="d.name" :value="d.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="角色" prop="roleCode">
          <el-select v-model="staffForm.roleCode" style="width: 100%">
            <el-option v-for="r in roles" :key="r.roleCode" :label="r.roleName" :value="r.roleCode" />
          </el-select>
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
          <el-input v-model="deptForm.deptCode" :disabled="editingDept" placeholder="如 CONSULT" />
        </el-form-item>
        <el-form-item label="部门名称" prop="deptName">
          <el-input v-model="deptForm.deptName" placeholder="如 咨询部" />
        </el-form-item>
        <el-form-item label="上级部门">
          <el-select v-model="deptForm.parentCode" clearable filterable placeholder="顶级" style="width: 100%">
            <el-option v-for="d in deptFlat" :key="d.code" :label="d.name" :value="d.code" />
          </el-select>
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
            <span class="api-role-code">{{ r.roleCode }}</span>
          </div>
        </div>
      </div>

      <div class="loan-card">
        <div class="dept-head" style="margin-bottom: 10px">
          <span class="panel-title">
            接口授权：{{ currentApiRole?.roleName || apiRoleCode || '—' }}
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
        <el-tree
          ref="apiTreeRef"
          :data="apiTree"
          :props="treeProps"
          node-key="key"
          show-checkbox
          default-expand-all
          style="max-height: 460px; overflow: auto"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
defineOptions({ name: '_org' });
import { ref, reactive, computed, onMounted, nextTick } from 'vue';
import { ElMessage } from 'element-plus';
import AppSearchBar from '@/components/AppSearchBar.vue';
import AppPagination from '@/components/AppPagination.vue';
import AppTableActions from '@/components/AppTableActions.vue';
import AppDialog from '@/components/AppDialog.vue';
import { useTable } from '@/composables/useTable';
import { desensitizePhone } from '@/utils/format';
import { departmentTree, roleList, staffPage, menuTree as fetchMenuTree, rolePermissionMenuIds as fetchRolePermissionMenuIds, saveStaff, disableStaff, saveDepartment, disableDepartment, saveRolePermission } from '@/api/org';
import { pageApiPerm, roleApiPerm, saveRoleApiPerm } from '@/api/apiperm';

const activeTab = ref('staff');
const deptTree = ref([]);
const roles = ref([]);
const menuTree = ref([]);
const currentDeptCode = ref(null);
const treeProps = { label: 'name', children: 'children' };

/** 员工列表 */
const { loading, data: staffs, total, query, load, onSearch, onReset, handleSortChange } = useTable(
  (q) => staffPage({ ...q, deptCode: currentDeptCode.value }),
  { roleCode: '', keyword: '' },
);

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
  deptCode: [{ required: true, message: '请输入部门编码', trigger: 'blur' }],
  deptName: [{ required: true, message: '请输入部门名称', trigger: 'blur' }],
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
        name: `${a.apiKey}  [${a.httpMethod}] ${a.pathPattern}`,
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
    const [role, menu] = await Promise.all([roleList(), fetchMenuTree('BOSS')]);
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
