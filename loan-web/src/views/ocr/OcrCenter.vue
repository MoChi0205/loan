<template>
  <div class="ocr-page">
    <div class="loan-page-header">
      <div>
        <h2 class="loan-page-title">客户材料</h2>
        <p class="loan-page-subtitle">按客户场景分类上传材料；AI识别启用时提取结构化数据，经我司复核后用于内部分析</p>
      </div>
    </div>

    <div class="loan-card">
      <el-form label-position="top" class="ocr-form">
        <el-form-item label="所属客户" required>
          <el-select v-model="form.clientCode" filterable remote :remote-method="searchClients" :loading="clientLoading"
            placeholder="先选择本人名下客户（姓名 / 手机号 / 企业名）" style="width: 100%">
            <el-option v-for="c in clientOptions" :key="c.clientCode" :label="clientDisplayLabel(c)" :value="c.clientCode" />
          </el-select>
          <div class="form-help">材料、识别结果、初筛报告和下载审批都将绑定到该客户。</div>
        </el-form-item>
        <el-form-item label="融资场景">
          <el-segmented v-model="form.scene" :options="sceneOptions" @change="resetMaterialSelection" />
        </el-form-item>

        <div class="material-plan">
          <div class="plan-head">
            <div><strong>材料准备清单</strong><span>按顺序上传，带“必传”的材料完成后可进入精准初筛</span></div>
            <el-progress type="circle" :percentage="completion" :width="54" :stroke-width="6" />
          </div>
          <div class="material-list">
            <button v-for="item in materialPlan" :key="item.value" type="button" class="material-item"
              :class="{ active: form.bizType === item.value, done: uploadedTypes.has(item.value) }" @click="form.bizType = item.value">
              <span class="material-order">{{ uploadedTypes.has(item.value) ? '✓' : item.order }}</span>
              <span class="material-copy"><strong>{{ item.label }}</strong><small>{{ item.desc }}</small></span>
              <span class="material-meta"><em v-if="item.required">必传</em><small>{{ item.validity }}</small></span>
            </button>
          </div>
        </div>

        <el-form-item :label="`上传：${selectedMaterial?.label || '请选择材料'}`">
          <el-upload
            ref="uploadRef"
            class="ocr-upload"
            drag
            :auto-upload="false"
            :limit="1"
            accept=".pdf,.jpg,.jpeg,.png,.xlsx,.xls"
            :on-change="onFileChange"
            :on-remove="onFileRemove"
          >
            <svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" stroke-width="1.5" class="upload-icon">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
              <path d="M17 8l-5-5-5 5" />
              <path d="M12 3v12" />
            </svg>
            <div class="el-upload__text">拖拽当前材料到此处，或<em>点击选择文件</em></div>
            <template #tip>
              <div class="el-upload__tip">支持 PDF / 图片 / Excel，单文件不超过 20MB</div>
            </template>
          </el-upload>
        </el-form-item>

        <el-row :gutter="16">
          <el-col :span="24" class="ocr-submit-col">
            <el-button
              type="primary"
              :loading="loading"
              :disabled="!file || !form.clientCode"
              @click="onRecognize"
            >
              上传并识别当前材料
            </el-button>
          </el-col>
        </el-row>
      </el-form>
    </div>

    <!-- 识别结果 -->
    <div v-if="result" class="loan-card">
      <div class="ocr-result-head">
        <h3 class="loan-card-title">识别结果</h3>
        <el-tag v-if="result.recognitionStatus === 'NOT_ENABLED'" type="info" size="small">AI识别未启用，仅完成分类上传</el-tag>
        <el-tag v-else-if="result.recognitionStatus === 'NO_FACTS'" type="warning" size="small">AI已调用，本次未提取到可用字段</el-tag>
        <el-tag v-else-if="result.recognitionStatus === 'EXTRACTED'" type="success" size="small">AI提取完成，待我司复核</el-tag>
        <el-tag v-else type="info" size="small">识别状态未知</el-tag>
        <el-tag v-if="result.rulesMissing" type="warning" size="small">字段映射规则缺失</el-tag>
        <span v-if="result.ocrFileKey" class="ocr-record-id">识别记录已安全留存</span>
      </div>

      <el-table :data="factRows" v-loading="loading" stripe size="default">
        <template #empty>
          <el-empty description="未提取到结构化字段（规则映射缺失或文件无有效内容）" />
        </template>
        <el-table-column prop="field" label="字段" min-width="200" />
        <el-table-column prop="value" label="值" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="fact-value">{{ row.value }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="confidence" label="置信度" width="110">
          <template #default="{ row }">
            <span v-if="row.confidence != null">{{ (row.confidence * 100).toFixed(0) }}%</span>
            <span v-else class="text-muted">—</span>
          </template>
        </el-table-column>
      </el-table>

      <el-button class="ocr-reset" plain @click="resetResult">清除结果</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue';
import { useRoute } from 'vue-router';
import { ElMessage } from 'element-plus';
import { ocrRecognize } from '@/api/ocr';
import { pageClientLite } from '@/api/order';
import { clientDisplayLabel } from '@/utils/display';
import { pageAttachments } from '@/api/attachment';

/** 资料类型字典（对齐 t_ocr_record.biz_scene 映射） */
const sceneOptions = [{ label: '企业经营贷', value: 'ENTERPRISE' }, { label: '个人经营/消费贷', value: 'PERSONAL' }];
const plans = {
  ENTERPRISE: [
    { value: 'BUSINESS_LICENSE', label: '营业执照', desc: '识别企业名称、信用代码、成立日期和经营状态', validity: '最新有效证照', required: true },
    { value: 'ID_CARD', label: '法人身份证', desc: '核验法定代表人身份与证照主体一致性', validity: '有效期内', required: true },
    { value: 'TAX_RECORD', label: '纳税证明', desc: '近 12 个月完税凭证或电子税务局纳税记录', validity: '近12个月', required: true },
    { value: 'INVOICE_RECORD', label: '开票记录', desc: '近 12 个月开票汇总或增值税申报表', validity: '近12个月', required: true },
    { value: 'BANK_STATEMENT', label: '经营流水', desc: '企业主要结算账户流水，用于验证真实经营规模', validity: '近6–12个月', required: true },
    { value: 'FINANCIAL_STATEMENT', label: '财务报表', desc: '资产负债表、利润表及现金流量表', validity: '最近年度/季度', required: false },
    { value: 'CONTRACT', label: '经营合同', desc: '上下游合同、订单或经营场所证明', validity: '当前有效', required: false },
  ],
  PERSONAL: [
    { value: 'ID_CARD', label: '身份证', desc: '核验借款人身份及证件有效期', validity: '有效期内', required: true },
    { value: 'CREDIT_REPORT', label: '个人征信报告', desc: '用于判断负债、查询次数和逾期情况', validity: '近30天', required: true },
    { value: 'BANK_STATEMENT', label: '收入/经营流水', desc: '工资卡或主要经营账户流水', validity: '近6个月', required: true },
    { value: 'INCOME_PROOF', label: '收入证明', desc: '在职收入、个税或经营收入证明', validity: '近3个月', required: true },
    { value: 'ASSET_PROOF', label: '资产证明', desc: '房产、车辆、保单等增信材料', validity: '当前有效', required: false },
    { value: 'SOCIAL_SECURITY', label: '社保/公积金', desc: '连续缴纳记录，用于稳定性评估', validity: '近12个月', required: false },
  ],
};

const uploadRef = ref();
const route = useRoute();
const loading = ref(false);
const file = ref(null);
const result = ref(null);
const clientLoading = ref(false);
const clientOptions = ref([]);
let clientTimer;

const form = reactive({
  bizType: 'BUSINESS_LICENSE',
  customerGroup: 'ENTERPRISE',
  scene: 'ENTERPRISE',
  clientCode: '',
});
const uploadedTypes = ref(new Set());
const materialPlan = computed(() => (plans[form.scene] || []).map((item, index) => ({ ...item, order: index + 1 })));
const selectedMaterial = computed(() => materialPlan.value.find((x) => x.value === form.bizType));
const completion = computed(() => {
  const required = materialPlan.value.filter((x) => x.required);
  return required.length ? Math.round(required.filter((x) => uploadedTypes.value.has(x.value)).length / required.length * 100) : 0;
});

function resetMaterialSelection() {
  form.customerGroup = form.scene;
  form.bizType = materialPlan.value[0]?.value || 'OTHER';
  resetResult();
}

async function loadUploadedTypes() {
  if (!form.clientCode) { uploadedTypes.value = new Set(); return; }
  try {
    const res = await pageAttachments({ clientProfileCode: form.clientCode, page: 1, size: 100 });
    uploadedTypes.value = new Set((res.data?.records || []).map((x) => x.attachmentType));
  } catch { uploadedTypes.value = new Set(); }
}
watch(() => form.clientCode, loadUploadedTypes);

function searchClients(keyword) {
  clearTimeout(clientTimer);
  clientTimer = setTimeout(async () => {
    clientLoading.value = true;
    try { const res = await pageClientLite({ keyword: keyword || '', page: 1, size: 20 }); clientOptions.value = res.data?.records || []; }
    catch { clientOptions.value = []; }
    finally { clientLoading.value = false; }
  }, 300);
}

/** 识别结果 facts → 表格行（保留原始字段顺序） */
const factRows = computed(() => {
  if (!result.value || !result.value.facts) return [];
  const fields = result.value.extractedFields || [];
  return Object.entries(result.value.facts).map(([k, v]) => {
    const conf = fields.find((f) => f.fieldCode === k);
    return { field: k, value: v == null ? '' : String(v), confidence: conf ? conf.confidence : null };
  });
});

function onFileChange(f) {
  file.value = f.raw || null;
}
function onFileRemove() {
  file.value = null;
}

async function onRecognize() {
  if (!file.value) {
    ElMessage.warning('请先选择材料文件');
    return;
  }
  if (!form.clientCode) {
    ElMessage.warning('请先选择材料所属客户');
    return;
  }
  loading.value = true;
  try {
    const fd = new FormData();
    fd.append('file', file.value);
    fd.append('bizType', form.bizType);
    fd.append('customerGroup', form.customerGroup);
    fd.append('clientCode', form.clientCode);
    const res = await ocrRecognize(fd);
    result.value = res.data || {};
    uploadedTypes.value = new Set([...uploadedTypes.value, form.bizType]);
    const n = Object.keys(result.value.facts || {}).length;
    if (result.value.recognitionStatus === 'NOT_ENABLED') {
      ElMessage.info('材料已分类上传；当前环境未启用真实 AI 识别');
    } else if (result.value.recognitionStatus === 'EXTRACTED') {
      ElMessage.success(`AI 已提取 ${n} 个字段，复核通过后才参与内部分析`);
    } else {
      ElMessage.warning('AI 已调用，但本次未提取到可用字段');
    }
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false;
  }
}

function resetResult() {
  result.value = null;
  file.value = null;
  if (uploadRef.value) uploadRef.value.clearFiles();
}

onMounted(async () => {
  if (route.query.clientCode) {
    form.clientCode = String(route.query.clientCode);
    await searchClients(form.clientCode);
  }
});
</script>

<style scoped>
.ocr-form {
  max-width: 1080px;
}
.form-help { margin-top: 6px; color: var(--loan-text-muted); font-size: 12px; }
.ocr-upload {
  width: 100%;
}
.upload-icon {
  color: var(--loan-text-muted);
  margin-bottom: 8px;
}
.ocr-submit-col {
  display: flex;
  align-items: flex-end;
}
.ocr-result-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}
.loan-card-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--loan-text);
  margin: 0;
}
.ocr-record-id {
  margin-left: auto;
  font-size: 12px;
  color: var(--loan-text-muted);
}
.fact-value {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 13px;
}
.text-muted {
  color: var(--loan-text-muted);
}
.ocr-reset {
  margin-top: 14px;
}
.material-plan { margin: 8px 0 22px; padding: 18px; border: 1px solid var(--loan-border); border-radius: 12px; background: var(--loan-surface); }
.plan-head { display:flex; align-items:center; justify-content:space-between; gap:16px; margin-bottom:14px; }
.plan-head strong,.plan-head span { display:block; }
.plan-head span { margin-top:5px; font-size:12px; color:var(--loan-text-muted); }
.material-list { display:grid; grid-template-columns:1fr 1fr; gap:10px; }
.material-item { display:flex; align-items:center; gap:12px; padding:12px; text-align:left; color:var(--loan-text); background:var(--loan-card-bg); border:1px solid var(--loan-border); border-radius:10px; cursor:pointer; }
.material-item:hover,.material-item.active { border-color:var(--loan-primary); box-shadow:0 0 0 2px color-mix(in srgb,var(--loan-primary) 10%,transparent); }
.material-item.done { border-color:color-mix(in srgb,var(--loan-success) 45%,var(--loan-border)); }
.material-order { display:grid; place-items:center; flex:0 0 28px; height:28px; border-radius:50%; background:var(--loan-surface); font-weight:700; }
.material-item.done .material-order { background:var(--loan-success); color:#fff; }
.material-copy { flex:1; min-width:0; }
.material-copy strong,.material-copy small,.material-meta small { display:block; }
.material-copy small,.material-meta small { margin-top:4px; color:var(--loan-text-muted); font-size:11px; line-height:1.4; }
.material-meta { flex:0 0 78px; text-align:right; }
.material-meta em { padding:2px 7px; border-radius:99px; color:var(--loan-danger); background:color-mix(in srgb,var(--loan-danger) 10%,transparent); font-size:11px; font-style:normal; }
@media(max-width:800px){.material-list{grid-template-columns:1fr}.material-meta{flex-basis:70px}}
</style>
