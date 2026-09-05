<template>
  <div class="ocr-page">
    <div class="loan-page-header">
      <div>
        <h2 class="loan-page-title">材料识别</h2>
        <p class="loan-page-subtitle">上传客户材料（营业执照 / 财报 / 流水等）提取结构化字段，供客户建档与匹配规则使用（Web 端 OCR，T16）</p>
      </div>
    </div>

    <div class="loan-card">
      <el-form label-position="top" class="ocr-form">
        <el-form-item label="材料文件">
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
            <div class="el-upload__text">拖拽文件到此处，或<em>点击选择</em></div>
            <template #tip>
              <div class="el-upload__tip">支持 PDF / 图片 / Excel，单文件不超过 20MB</div>
            </template>
          </el-upload>
        </el-form-item>

        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="资料类型">
              <el-select v-model="form.bizType" placeholder="选择资料类型">
                <el-option v-for="o in bizTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="客群">
              <el-select v-model="form.customerGroup" placeholder="客群（可选）" clearable>
                <el-option label="企业贷" value="ENTERPRISE" />
                <el-option label="个贷" value="PERSONAL" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8" class="ocr-submit-col">
            <el-button
              type="primary"
              :loading="loading"
              :disabled="!file"
              @click="onRecognize"
            >
              开始识别
            </el-button>
          </el-col>
        </el-row>
      </el-form>
    </div>

    <!-- 识别结果 -->
    <div v-if="result" class="loan-card">
      <div class="ocr-result-head">
        <h3 class="loan-card-title">识别结果</h3>
        <el-tag v-if="result.rulesMissing" type="warning" size="small">规则种子缺失（facts 可能为空）</el-tag>
        <el-tag v-else type="success" size="small">提取完成</el-tag>
        <span v-if="result.ocrRecordId" class="ocr-record-id">记录 ID：{{ result.ocrRecordId }}</span>
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
import { ref, reactive, computed } from 'vue';
import { ElMessage } from 'element-plus';
import { ocrRecognize } from '@/api/ocr';

/** 资料类型字典（对齐 t_ocr_record.biz_scene 映射） */
const bizTypeOptions = [
  { value: 'BUSINESS_LICENSE', label: '营业执照' },
  { value: 'ID_CARD', label: '身份证' },
  { value: 'FINANCIAL_STATEMENT', label: '财务报表' },
  { value: 'CONTRACT', label: '合同' },
  { value: 'DUE_DILIGENCE', label: '尽调资料' },
  { value: 'OTHER', label: '其他' },
];

const uploadRef = ref();
const loading = ref(false);
const file = ref(null);
const result = ref(null);

const form = reactive({
  bizType: 'BUSINESS_LICENSE',
  customerGroup: 'ENTERPRISE',
});

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
  loading.value = true;
  try {
    const fd = new FormData();
    fd.append('file', file.value);
    fd.append('bizType', form.bizType);
    fd.append('customerGroup', form.customerGroup);
    const res = await ocrRecognize(fd);
    result.value = res.data || {};
    const n = Object.keys(result.value.facts || {}).length;
    ElMessage.success(n ? `识别完成，提取 ${n} 个字段` : '识别完成，未提取到字段（可能需补规则映射）');
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
</script>

<style scoped>
.ocr-form {
  max-width: 860px;
}
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
</style>
