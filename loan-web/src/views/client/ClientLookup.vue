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
          <!-- 经理/老板/超管/运营：本团队可强制转移；跨团队走申请（负责人初审+老板终审，2026-10-19 规则） -->
          <el-button v-if="canForceAssign" @click="openForceTransfer">强制转移（本团队直接生效）</el-button>
          <el-button type="primary" @click="claim">申请转移{{ canForceAssign ? '（跨团队）' : '' }}</el-button>
        </template>
        <el-tag v-if="result.ownedByMe" type="success" size="large">当前已是我的客户</el-tag>
      </div>
    </div>

    <!-- 强制转移弹窗：经理/老板/超管/运营选目标顾问，直接落归属（本团队） -->
    <AppDialog v-model:visible="transferVisible" title="强制转移客户归属" width="560px" :loading="transferring" @confirm="onForceTransferConfirm">
      <p class="assign-dialog-hint">从在职人员中选择新归属人；本团队内直接生效，跨团队需走转移申请审批。</p>
      <el-form label-width="72px" label-position="right">
        <el-form-item label="新归属人">
          <el-select v-model="transferTarget" filterable remote :remote-method="searchTransferStaff" :loading="transferStaffLoading" placeholder="搜索姓名或工号" style="width: 100%" placement="bottom-start" @visible-change="(v) => { if (v) searchTransferStaff('') }">
            <el-option v-for="o in transferStaffOptions" :key="o.staffCode" :label="`${o.staffName || '员工姓名待补充'}（${o.roleName || '员工'} · ${o.deptName || '未分部门'}）`" :value="o.staffCode" :disabled="o.status && o.status !== 'ACTIVE'" />
          </el-select>
        </el-form-item>
      </el-form>
    </AppDialog>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { lookupClient, claimUnassignedClient, assignClient } from '@/api/client';
import { desensitizePhone } from '@/utils/format';
import { applyClientPhoneView } from '@/api/sensitive';
import { staffPage } from '@/api/org';
import { useUserStore } from '@/store/user';
import AppDialog from '@/components/AppDialog.vue';
const keyword = ref(''); const loading = ref(false); const searched = ref(false); const result = ref(null); const phoneRevealed = ref(false);
const userStore = useUserStore();
/** 强制转移角色：部门经理 / 老板 / 运营 / 超管（与后端 directAssign 可操作角色一致）。 */
const canForceAssign = computed(() => ['DEPT_MANAGER', 'BOSS', 'OPERATOR', 'SUPER_ADMIN', 'SUPER'].includes(userStore.roleCode));
const actionHint = computed(() => {
  if (!result.value) return '—';
  if (result.value.ownedByMe) return '已归属本人，无需重复认领';
  if (!result.value.hasOwner) return '直接认领，无需审批';
  // 预计审批链路（2026-10-19 规则）：同团队单级审批；跨团队两级审批。
  const ownerDept = result.value.ownerDeptName ? `（现归属部门：${result.value.ownerDeptName}）` : '';
  return `提交转移申请${ownerDept}：同团队由本团队负责人审批；跨团队由负责人初审 + 老板终审`;
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
  if (data.phonePlain) { result.value.contactPhone = data.phonePlain; phoneRevealed.value = true; ElMessage.success(`已授权查看，本日剩余 ${data.remaining ?? '—'} 次`); }
  else if (data.approvalNo) ElMessage.warning(data.message || '已提交审批，审批通过后可再次查看');
}
async function claim() {
  const direct = !result.value.hasOwner;
  await ElMessageBox.confirm(direct ? '确认将该用户直接认领到自己名下？' : '该用户已有顾问，确认提交认领申请？', direct ? '确认认领' : '申请转移');
  const res = await claimUnassignedClient(result.value.clientCode);
  ElMessage.success(res.data?.direct ? '认领成功' : '申请已提交，请等待审批');
  await search();
}
// ============ 强制转移（经理/老板/超管/运营） ============
const transferVisible = ref(false);
const transferring = ref(false);
const transferTarget = ref('');
const transferStaffOptions = ref([]);
const transferStaffLoading = ref(false);
let transferSeq = 0;
let transferTimer;
function openForceTransfer() {
  transferTarget.value = '';
  transferStaffOptions.value = [];
  transferVisible.value = true;
  searchTransferStaff('');
}
function searchTransferStaff(keyword) {
  clearTimeout(transferTimer);
  transferTimer = setTimeout(async () => {
    const seq = ++transferSeq;
    transferStaffLoading.value = true;
    try {
      const query = { keyword: keyword.trim() || undefined, page: 1, size: 50 };
      const [advisers, managers] = await Promise.all([
        staffPage({ ...query, roleCode: 'ADVISER' }),
        staffPage({ ...query, roleCode: 'DEPT_MANAGER' }),
      ]);
      if (seq !== transferSeq) return;
      const merged = new Map();
      [...(advisers.data?.records || []), ...(managers.data?.records || [])].forEach((s) => merged.set(s.staffCode, s));
      transferStaffOptions.value = [...merged.values()].map((s) => ({
        staffCode: s.staffCode, staffName: s.staffName, deptName: s.deptName, roleCode: s.roleCode, roleName: s.roleName, status: s.status,
      }));
    } catch (e) {
      if (seq === transferSeq) transferStaffOptions.value = [];
    } finally {
      if (seq === transferSeq) transferStaffLoading.value = false;
    }
  }, 250);
}
async function onForceTransferConfirm() {
  if (!transferTarget.value) return ElMessage.warning('请选择新归属人');
  const picked = transferStaffOptions.value.find((o) => o.staffCode === transferTarget.value);
  try {
    await ElMessageBox.confirm(`确认将客户【${result.value?.entName || result.value?.contactName || '未命名客户'}】强制转移给「${picked ? picked.staffName : '目标顾问'}」？本团队内立即生效。`, '强制转移确认', { type: 'warning' });
  } catch { return; }
  transferring.value = true;
  try {
    await assignClient(result.value.clientCode, transferTarget.value);
    ElMessage.success('强制转移成功');
    transferVisible.value = false;
    await search();
  } catch (e) {
    // 跨团队客户不可直接分配：降级引导转为提交转移申请（负责人初审 + 老板终审）
    if (String(e?.message || '').includes('跨团队')) {
      try {
        await ElMessageBox.confirm('该客户属于其他团队，不能直接分配。是否转为提交转移申请？本团队负责人初审通过后由老板终审。', '转为转移申请？', { type: 'warning', confirmButtonText: '提交申请' });
        const res = await claimUnassignedClient(result.value.clientCode);
        ElMessage.success(res.data?.direct ? '已直接认领' : '转移申请已提交，等待审批');
        transferVisible.value = false;
        await search();
      } catch (e2) { /* 用户取消或提交失败，拦截器已提示 */ }
    }
    /* 其余错误拦截器已提示 */
  }
  finally { transferring.value = false; }
}
</script>
<style scoped>
.lookup-card{max-width:820px}
.result{margin-top:20px}
.actions{display:flex;justify-content:flex-end;margin-top:18px;gap:10px;align-items:center}
.phone-revealed{margin-left:8px;color:var(--loan-success-text,var(--loan-success));font-size:12px}
.assign-dialog-hint{margin:0 0 18px;padding:10px 12px;border-radius:8px;color:var(--loan-text-secondary,var(--loan-text-muted));background:var(--loan-fill-light,rgba(148,163,184,.1));font-size:13px;line-height:1.6}
</style>
