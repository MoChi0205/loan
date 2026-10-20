<template>
  <div>
    <div class="loan-page-header">
      <div><h2 class="loan-page-title">用户查询</h2><p class="loan-page-subtitle">查询客户归属，并按规则直接认领、强制转移或提交转移申请</p></div>
    </div>
    <div class="loan-card lookup-card">
      <el-input v-model="keyword" clearable placeholder="姓名 / 企业 / 手机号 / 身份证 / 统一社会信用代码" @keyup.enter="search">
        <template #append><el-button :loading="loading" @click="search">查询</el-button></template>
      </el-input>
      <el-empty v-if="searched && !result" description="未查询到已存在用户" />
      <el-descriptions v-if="result" :column="2" border class="result">
        <el-descriptions-item label="企业名称">{{ result.entName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="联系人">{{ result.contactName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="联系电话"><span>{{ desensitizePhone(result.contactPhone) }}</span><el-button v-if="!phoneRevealed" link type="primary" size="small" @click="requestPhoneView">申请查看</el-button><span v-else class="phone-revealed">已授权查看</span></el-descriptions-item>
        <el-descriptions-item label="当前归属">{{ result.hasOwner ? (result.ownerStaffName || '已有顾问') : '未分配' }}</el-descriptions-item>
        <el-descriptions-item label="处理方式">{{ actionHint }}</el-descriptions-item>
      </el-descriptions>
      <div v-if="result" class="actions">
        <!-- 未分配：任何角色直接认领（无需审批） -->
        <el-button v-if="!result.hasOwner && !result.ownedByMe" type="primary" @click="claim">直接认领</el-button>
        <template v-else-if="result.hasOwner && !result.ownedByMe">
          <!-- 经理/老板/超管/运营：本团队可强制转移；跨团队走申请（目标团队初审+老板终审，D83 规则） -->
          <el-button v-if="canForceAssign" @click="openForceTransfer">强制转移（本团队直接生效）</el-button>
          <el-button type="primary" @click="openTransferApply">申请转移（跨团队）</el-button>
        </template>
        <el-tag v-if="result.ownedByMe" type="success" size="large">当前已是我的客户</el-tag>
      </div>
    </div>

    <!-- 强制转移弹窗：经理/老板/超管/运营选本团队目标顾问，直接落归属（D83：目标排除原归属人） -->
    <AppDialog v-model:visible="transferVisible" title="强制转移客户归属" width="560px" :loading="transferring" @confirm="onForceTransferConfirm">
      <p class="assign-dialog-hint">从本团队在职人员中选择新归属人（不含原归属人）；本团队内直接生效，跨团队需走转移申请审批。</p>
      <el-form label-width="72px" label-position="right">
        <el-form-item label="新归属人">
          <DialogSelect v-model="transferTarget" :fetcher="fetchForceAssignStaff" placeholder="搜索本团队姓名或工号" />
        </el-form-item>
      </el-form>
    </AppDialog>

    <!-- 申请转移弹窗（跨团队）：先选非本团队目标团队，再选该团队目标员工（D83 规则） -->
    <AppDialog v-model:visible="transferApplyVisible" title="申请转移客户（跨团队）" width="560px" :loading="transferApplying" @confirm="onTransferApplyConfirm">
      <p class="assign-dialog-hint">跨团队转移需提交申请：第一步选择非本团队的目标团队，第二步选择该团队的目标员工；提交后由目标团队负责人初审、老板终审。</p>
      <el-form label-width="90px" label-position="right">
        <el-form-item label="目标团队" required>
          <AppSelect v-model="transferApplyForm.deptCode" filterable placeholder="选择非本团队的团队" style="width: 100%" @change="onTransferDeptChange">
            <el-option v-for="d in transferDeptOptions" :key="d.value" :label="d.label" :value="d.value" />
          </AppSelect>
        </el-form-item>
        <el-form-item label="目标员工" required>
          <DialogSelect v-model="transferApplyForm.targetStaffCode" :fetcher="fetchTransferTargetStaff" :disabled="!transferApplyForm.deptCode" placeholder="先选择团队，再搜索该团队员工" />
        </el-form-item>
      </el-form>
    </AppDialog>
  </div>
</template>

<script setup>
import AppSelect from '@/components/AppSelect.vue';
import { computed, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { lookupClient, claimUnassignedClient, assignClient } from '@/api/client';
import { desensitizePhone } from '@/utils/format';
import { applyClientPhoneView } from '@/api/sensitive';
import { staffPage, departmentTree } from '@/api/org';
import { useUserStore } from '@/store/user';
import AppDialog from '@/components/AppDialog.vue';
import DialogSelect from '@/components/DialogSelect.vue';
const keyword = ref(''); const loading = ref(false); const searched = ref(false); const result = ref(null); const phoneRevealed = ref(false);
const userStore = useUserStore();
/** 当前登录员工所属部门编码（本团队）。 */
const myDeptCode = computed(() => userStore.user?.deptCode || '');
/** 强制转移角色：部门经理 / 老板 / 运营 / 超管（与后端 directAssign 可操作角色一致）。 */
const canForceAssign = computed(() => ['DEPT_MANAGER', 'BOSS', 'OPERATOR', 'SUPER_ADMIN', 'SUPER'].includes(userStore.roleCode));
const actionHint = computed(() => {
  if (!result.value) return '—';
  if (result.value.ownedByMe) return '已归属本人，无需重复认领';
  if (!result.value.hasOwner) return '直接认领，无需审批';
  return `跨团队转移申请：先选非本团队目标团队，再选该团队目标员工；由目标团队负责人初审、老板终审`;
});
async function search() {
  if (keyword.value.trim().length < 2) return ElMessage.warning('请输入至少 2 个字符');
  loading.value = true;
  try { const res = await lookupClient(keyword.value.trim()); result.value = res.data || null; phoneRevealed.value = false; searched.value = true; }
  finally { loading.value = false; }
}
async function requestPhoneView() {
  if (!result.value?.clientCode) return;
  const data = (await applyClientPhoneView(result.value.clientCode)).data || {};
  if (data.phonePlain) { result.value.contactPhone = data.phonePlain; phoneRevealed.value = true; ElMessage.success(`已授权查看，${(data.unlimited || data.remaining < 0) ? '当前角色不受次数限制' : `本日剩余 ${data.remaining} 次`}`); }
  else if (data.approvalNo) ElMessage.warning(data.message || '已提交审批，审批通过后可再次查看');
}
/** 未归属：直接认领（确认后提交）。 */
async function claim() {
  await ElMessageBox.confirm('确认将该用户直接认领到自己名下？', '确认认领');
  const res = await claimUnassignedClient(result.value.clientCode);
  ElMessage.success(res.data?.direct ? '认领成功' : '申请已提交，请等待审批');
  await search();
}

// ============ 强制转移（经理/老板/超管/运营，本团队内直接生效） ============
const transferVisible = ref(false);
const transferring = ref(false);
const transferTarget = ref('');
/** 强制转移候选人：本团队在职顾问/经理，排除原归属人。 */
async function fetchForceAssignStaff(keyword = '') {
  const kw = String(keyword || '').trim() || undefined;
  const owner = result.value?.ownerStaffCode;
  const [advisers, managers] = await Promise.all([
    staffPage({ roleCode: 'ADVISER', deptCode: myDeptCode.value || undefined, keyword: kw, page: 1, size: 50 }),
    staffPage({ roleCode: 'DEPT_MANAGER', deptCode: myDeptCode.value || undefined, keyword: kw, page: 1, size: 50 }),
  ]);
  const merged = new Map();
  [...(advisers.data?.records || []), ...(managers.data?.records || [])].forEach((s) => {
    if (owner && s.staffCode === owner) return; // D83：排除原归属人
    merged.set(s.staffCode, s);
  });
  return [...merged.values()].map((s) => ({
    value: s.staffCode,
    label: s.staffName || '员工姓名待补充',
    sub: [s.roleName || '员工', s.deptName].filter(Boolean).join(' · '),
  }));
}
function openForceTransfer() {
  transferTarget.value = '';
  transferVisible.value = true;
}
async function onForceTransferConfirm() {
  if (!transferTarget.value) return ElMessage.warning('请选择新归属人');
  await ElMessageBox.confirm(`确认将客户【${result.value?.entName || result.value?.contactName || '未命名客户'}】强制转移给目标员工？本团队内立即生效。`, '强制转移确认', { type: 'warning' });
  transferring.value = true;
  try {
    await assignClient(result.value.clientCode, transferTarget.value);
    ElMessage.success('强制转移成功');
    transferVisible.value = false;
    await search();
  } catch (e) {
    // D83：按后端 code 分支（1005 = CLIENT_CROSS_TEAM），禁止按中文文案匹配。
    if (e?.code === 1005) {
      transferVisible.value = false;
      ElMessage.warning('该客户属于其他团队，不能直接分配，请改走跨团队转移申请');
      openTransferApply();
      return;
    }
    ElMessage.error(e?.message || '强制转移失败，请稍后重试');
  } finally {
    transferring.value = false;
  }
}

// ============ 申请转移（跨团队，两段式：非本团队 → 该团队员工） ============
const transferApplyVisible = ref(false);
const transferApplying = ref(false);
const transferApplyForm = ref({ deptCode: '', targetStaffCode: '' });
const transferDeptOptions = ref([]);
/** 目标团队候选：全部部门中排除本团队。 */
async function loadTransferDepts() {
  try {
    const res = await departmentTree();
    const flat = [];
    const walk = (list) => {
      (list || []).forEach((d) => { flat.push(d); if (d.children?.length) walk(d.children); });
    };
    walk(res.data || []);
    transferDeptOptions.value = flat
      .filter((d) => d.code && d.code !== myDeptCode.value)
      .map((d) => ({ value: d.code, label: d.name || d.code }));
  } catch { transferDeptOptions.value = []; }
}
/** 目标员工候选：所选团队在职员工，排除原归属人。 */
async function fetchTransferTargetStaff(keyword = '') {
  if (!transferApplyForm.value.deptCode) return [];
  const kw = String(keyword || '').trim() || undefined;
  const owner = result.value?.ownerStaffCode;
  const res = await staffPage({ deptCode: transferApplyForm.value.deptCode, keyword: kw, page: 1, size: 50 });
  return (res.data?.records || [])
    .filter((s) => !owner || s.staffCode !== owner)
    .map((s) => ({ value: s.staffCode, label: s.staffName || '员工姓名待补充', sub: [s.roleName || '员工', s.deptName].filter(Boolean).join(' · ') }));
}
function onTransferDeptChange() {
  transferApplyForm.value.targetStaffCode = '';
}
function openTransferApply() {
  transferApplyForm.value = { deptCode: '', targetStaffCode: '' };
  loadTransferDepts();
  transferApplyVisible.value = true;
}
async function onTransferApplyConfirm() {
  const f = transferApplyForm.value;
  if (!f.deptCode) return ElMessage.warning('请先选择非本团队的目标团队');
  if (!f.targetStaffCode) return ElMessage.warning('请选择该团队的目标员工');
  transferApplying.value = true;
  try {
    const res = await claimUnassignedClient(result.value.clientCode, f.targetStaffCode);
    ElMessage.success(res.data?.direct ? '已直接认领' : '转移申请已提交，等待审批');
    transferApplyVisible.value = false;
    await search();
  } catch (e) {
    ElMessage.error(e?.message || '提交失败，请稍后重试');
  } finally {
    transferApplying.value = false;
  }
}
</script>
<style scoped>
.lookup-card{max-width:820px}
.result{margin-top:20px}
.actions{display:flex;justify-content:flex-end;margin-top:18px;gap:10px;align-items:center}
.phone-revealed{margin-left:8px;color:var(--loan-success-text,var(--loan-success));font-size:12px}
.assign-dialog-hint{margin:0 0 18px;padding:10px 12px;border-radius:8px;color:var(--loan-text-secondary,var(--loan-text-muted));background:var(--loan-fill-light,rgba(148,163,184,.1));font-size:13px;line-height:1.6}
</style>
