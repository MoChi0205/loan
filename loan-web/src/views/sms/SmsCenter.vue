<template>
  <div class="sms-page">
    <div class="loan-page-header">
      <div>
        <h2 class="loan-page-title">短信服务</h2>
        <p class="loan-page-subtitle">模板管理（三类场景）· 发送记录全量落库 · 手动发送（模拟通道）</p>
      </div>
      <el-button type="primary" @click="openSend">
        <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" style="margin-right: 4px; vertical-align: -2px"><path d="M22 2L11 13"/><path d="M22 2l-7 20-4-9-9-4 20-7z"/></svg>
        手动发送
      </el-button>
    </div>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="短信模板" name="template" />
      <el-tab-pane label="发送记录" name="record" />
    </el-tabs>

    <!-- 模板管理 -->
    <div v-show="activeTab === 'template'" class="loan-card">
      <AppSearchBar :loading="loadingT" @search="searchT" @reset="resetT">
        <el-input v-model="queryT.keyword" placeholder="模板编码 / 名称" style="width: 220px" clearable @keyup.enter="searchT" />
        <template #append>
          <el-button type="primary" plain @click="openTemplate()">
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" style="margin-right: 4px; vertical-align: -2px"><path d="M12 5v14M5 12h14"/></svg>
            新增模板
          </el-button>
        </template>
      </AppSearchBar>

      <el-table :data="dataT" v-loading="loadingT" stripe row-key="templateCode" @sort-change="handleSortChange">
        <template #empty>
          <AppEmpty title="暂无短信模板" desc="新增短信模板后，可在业务节点触发发送" />
        </template>
        <el-table-column prop="templateCode" label="模板编码" width="120"  show-overflow-tooltip />
        <el-table-column prop="templateName" label="模板名称" min-width="150" />
        <el-table-column prop="content" label="模板内容" min-width="220" show-overflow-tooltip />
        <el-table-column prop="signName" label="签名" width="110" />
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <span class="loan-tag loan-tag-info">{{ typeText[row.smsType] || row.smsType }}</span>
          </template>
        </el-table-column>
        <el-table-column label="启停" width="90">
          <template #default="{ row }">
            <el-switch :model-value="!!row.enabled" @change="(v) => onToggle(row, v)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <AppTableActions :actions="[
              { key: 'edit', label: '编辑', onClick: () => openTemplate(row) },
            ]" />
          </template>
        </el-table-column>
      </el-table>
      <AppPagination v-model:page="queryT.page" v-model:size="queryT.size" :total="totalT" @change="loadT" />
    </div>

    <!-- 发送记录 -->
    <div v-show="activeTab === 'record'" class="loan-card">
      <AppSearchBar :loading="loadingR" @search="searchR" @reset="resetR">
        <el-select v-model="queryR.smsType" placeholder="类型" clearable style="width: 140px">
          <el-option v-for="(t, k) in typeText" :key="k" :label="t" :value="k" />
        </el-select>
        <el-select v-model="queryR.status" placeholder="状态" clearable style="width: 120px">
          <el-option v-for="(t, k) in statusText" :key="k" :label="t" :value="k" />
        </el-select>
        <el-input v-model="queryR.phone" placeholder="手机号（精确）" style="width: 180px" clearable @keyup.enter="searchR" />
      </AppSearchBar>

      <el-table :data="dataR" v-loading="loadingR" stripe @sort-change="handleSortChangeR">
        <template #empty>
          <AppEmpty title="暂无发送记录" desc="短信发送后将在此记录送达状态" />
        </template>
        <el-table-column label="手机号" width="130">
          <template #default="{ row }">{{ desensitizePhone(row.phone) }}</template>
        </el-table-column>
        <el-table-column prop="templateCode" label="模板" width="110"  show-overflow-tooltip />
        <el-table-column prop="content" label="内容" min-width="240" show-overflow-tooltip />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <span class="loan-tag loan-tag-info">{{ typeText[row.smsType] || row.smsType }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <span class="loan-tag" :class="statusTag(row.status)">{{ statusText[row.status] || row.status }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="operator" label="操作人" width="100">
          <template #default="{ row }">{{ row.operator || '—' }}</template>
        </el-table-column>
        <el-table-column prop="sendTime" label="发送时间" width="160" sortable>
          <template #default="{ row }">{{ formatDateTime(row.sendTime || row.createdAt) }}</template>
        </el-table-column>
      </el-table>
      <AppPagination v-model:page="queryR.page" v-model:size="queryR.size" :total="totalR" @change="loadR" />
    </div>

    <!-- 模板编辑弹窗 -->
    <AppDialog v-model:visible="templateVisible" :title="editing ? '编辑模板' : '新增模板'" :loading="savingT" @confirm="onSaveTemplate">
      <el-form ref="templateFormRef" :model="templateForm" :rules="templateRules" label-width="110px" label-position="right">
        <el-form-item label="模板编码" prop="templateCode">
          <el-input v-model="templateForm.templateCode" placeholder="如 LOGIN_VERIFY" :disabled="editing" />
        </el-form-item>
        <el-form-item label="模板名称" prop="templateName">
          <el-input v-model="templateForm.templateName" placeholder="如 登录验证码" />
        </el-form-item>
        <el-form-item label="模板内容" prop="content">
          <el-input v-model="templateForm.content" type="textarea" :rows="3" placeholder="变量占位符如 ${code}" />
        </el-form-item>
        <el-form-item label="短信签名" prop="signName">
          <el-input v-model="templateForm.signName" placeholder="如 贷款服务平台" />
        </el-form-item>
        <el-form-item label="短信类型">
          <el-select v-model="templateForm.smsType" style="width: 100%">
            <el-option label="登录验证" value="LOGIN_VERIFY" />
            <el-option label="通知" value="NOTIFICATION" />
            <el-option label="业务营销" value="MARKETING" />
          </el-select>
        </el-form-item>
        <el-form-item label="腾讯云模板ID">
          <el-input v-model="templateForm.providerTemplateId" placeholder="可选" />
        </el-form-item>
        <el-form-item label="频控策略">
          <el-input v-model="templateForm.freqStrategy" placeholder="如 验证码60s间隔/单号日上限" />
        </el-form-item>
      </el-form>
    </AppDialog>

    <!-- 手动发送弹窗 -->
    <AppDialog v-model:visible="sendVisible" title="手动发送短信" :loading="sending" @confirm="onSend">
      <el-form label-width="100px" label-position="right">
        <el-form-item label="手机号" required>
          <el-input v-model="sendForm.phone" placeholder="11 位手机号" />
        </el-form-item>
        <el-form-item label="模板" required>
          <el-select v-model="sendForm.templateCode" filterable placeholder="选择模板" style="width: 100%">
            <el-option v-for="t in templateOptions" :key="t.templateCode" :label="`${t.templateName}（${t.templateCode}）`" :value="t.templateCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="sendForm.content" type="textarea" :rows="2" placeholder="缺省用模板内容" />
        </el-form-item>
      </el-form>
    </AppDialog>
  </div>
</template>

<script setup>
defineOptions({ name: '_sms' });
import { ref, reactive, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import AppSearchBar from '@/components/AppSearchBar.vue';
import AppPagination from '@/components/AppPagination.vue';
import AppEmpty from '@/components/AppEmpty.vue';
import AppTableActions from '@/components/AppTableActions.vue';
import AppDialog from '@/components/AppDialog.vue';
import { useTable } from '@/composables/useTable';
import { formatDateTime, desensitizePhone } from '@/utils/format';
import {
  pageSmsTemplates, listSmsTemplates, saveSmsTemplate, toggleSmsTemplate,
  pageSmsRecords, sendSms,
} from '@/api/sms';

const activeTab = ref('template');
const typeText = { LOGIN_VERIFY: '登录验证', NOTIFICATION: '通知', MARKETING: '业务营销' };
const statusText = { PENDING: '待发送', SENT: '已发送', SUCCESS: '成功', FAIL: '失败' };
const statusTag = (s) => ({ PENDING: 'loan-tag-muted', SENT: 'loan-tag-info', SUCCESS: 'loan-tag-success', FAIL: 'loan-tag-danger' }[s] || 'loan-tag-muted');

const { loading: loadingT, data: dataT, total: totalT, query: queryT, load: loadT, onSearch: searchT, onReset: resetT, handleSortChange } =
  useTable(pageSmsTemplates, { keyword: '' });
  const { loading: loadingR, data: dataR, total: totalR, query: queryR, load: loadR, onSearch: searchR, onReset: resetR, handleSortChange: handleSortChangeR } =
    useTable(pageSmsRecords, { smsType: '', status: '', phone: '' });

async function onToggle(row, v) {
  try {
    await toggleSmsTemplate({ templateCode: row.templateCode, enabled: v });
    ElMessage.success(v ? '已启用' : '已停用');
    loadT();
  } catch (e) { /* 拦截器已提示 */ }
}

// ============================================================
// 模板编辑
// ============================================================
const templateVisible = ref(false);
const savingT = ref(false);
const editing = ref(false);
const templateFormRef = ref();
const templateForm = reactive({
  templateCode: '', templateName: '', content: '', signName: '',
  smsType: 'NOTIFICATION', providerTemplateId: '', freqStrategy: '',
});
const templateRules = {
  templateCode: [{ required: true, message: '请输入模板编码', trigger: 'blur' }],
  templateName: [{ required: true, message: '请输入模板名称', trigger: 'blur' }],
  content: [{ required: true, message: '请输入模板内容', trigger: 'blur' }],
  signName: [{ required: true, message: '请输入短信签名', trigger: 'blur' }],
};

function openTemplate(row) {
  editing.value = !!row;
  Object.assign(templateForm, {
    templateCode: row?.templateCode || '',
    templateName: row?.templateName || '',
    content: row?.content || '',
    signName: row?.signName || '',
    smsType: row?.smsType || 'NOTIFICATION',
    providerTemplateId: row?.providerTemplateId || '',
    freqStrategy: row?.freqStrategy || '',
  });
  templateVisible.value = true;
}

async function onSaveTemplate() {
  await templateFormRef.value.validate();
  savingT.value = true;
  try {
    await saveSmsTemplate({ ...templateForm });
    ElMessage.success('保存成功');
    templateVisible.value = false;
    loadT();
  } catch (e) { /* 拦截器已提示 */ } finally {
    savingT.value = false;
  }
}

// ============================================================
// 手动发送
// ============================================================
const sendVisible = ref(false);
const sending = ref(false);
const templateOptions = ref([]);
const sendForm = reactive({ phone: '', templateCode: '', content: '' });

async function openSend() {
  sendForm.phone = '';
  sendForm.templateCode = '';
  sendForm.content = '';
  sendVisible.value = true;
  try {
    const res = await listSmsTemplates();
    templateOptions.value = res.data || [];
  } catch (e) {
    templateOptions.value = [];
  }
}

async function onSend() {
  if (!sendForm.phone || !sendForm.templateCode) {
    ElMessage.warning('手机号与模板必填');
    return;
  }
  sending.value = true;
  try {
    await sendSms({ ...sendForm });
    ElMessage.success('已发送（模拟通道）');
    sendVisible.value = false;
    if (activeTab.value === 'record') loadR();
  } catch (e) { /* 拦截器已提示 */ } finally {
    sending.value = false;
  }
}

onMounted(() => { loadT(); loadR(); });
</script>
