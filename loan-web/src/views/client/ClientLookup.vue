<template>
  <div>
    <div class="loan-page-header">
      <div><h2 class="loan-page-title">用户查询</h2><p class="loan-page-subtitle">查询客户归属，并按规则直接认领或提交转移申请</p></div>
    </div>
    <div class="loan-card lookup-card">
      <el-input v-model="keyword" clearable placeholder="姓名 / 企业 / 手机号 / 身份证 / 统一社会信用代码" @keyup.enter="search">
        <template #append><el-button :loading="loading" @click="search">查询</el-button></template>
      </el-input>
      <el-empty v-if="searched && !result" description="未查询到已存在用户" />
      <el-descriptions v-if="result" :column="2" border class="result">
        <el-descriptions-item label="客户">{{ result.entName || result.contactName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="联系电话">{{ result.contactPhone || '—' }}</el-descriptions-item>
        <el-descriptions-item label="当前归属">{{ result.hasOwner ? (result.ownerStaffName || '已有顾问') : '未分配' }}</el-descriptions-item>
        <el-descriptions-item label="处理方式">{{ result.ownedByMe ? '已归属本人，无需重复认领' : (result.hasOwner ? '提交转移申请' : '直接认领，无需审批') }}</el-descriptions-item>
      </el-descriptions>
      <div v-if="result" class="actions">
        <el-button v-if="!result.ownedByMe" type="primary" @click="claim">{{ result.hasOwner ? '申请认领' : '直接认领' }}</el-button>
        <el-tag v-else type="success" size="large">当前已是我的客户</el-tag>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { lookupClient, claimUnassignedClient } from '@/api/client';
const keyword = ref(''); const loading = ref(false); const searched = ref(false); const result = ref(null);
async function search() {
  if (keyword.value.trim().length < 2) return ElMessage.warning('请输入至少 2 个字符');
  loading.value = true;
  try { const res = await lookupClient(keyword.value.trim()); result.value = res.data || null; searched.value = true; }
  finally { loading.value = false; }
}
async function claim() {
  const direct = !result.value.hasOwner;
  await ElMessageBox.confirm(direct ? '确认将该用户直接认领到自己名下？' : '该用户已有顾问，确认提交认领申请？', direct ? '确认认领' : '申请转移');
  const res = await claimUnassignedClient(result.value.clientCode);
  ElMessage.success(res.data?.direct ? '认领成功' : '申请已提交，请等待审批');
  await search();
}
</script>
<style scoped>.lookup-card{max-width:820px}.result{margin-top:20px}.actions{display:flex;justify-content:flex-end;margin-top:18px}</style>
