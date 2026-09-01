<template>
  <view class="edit-page">
    <view class="card">
      <text class="card-title">{{ isEdit ? '编辑产品' : '录入产品' }}</text>

      <view class="field">
        <text class="field-label">银行产品编码</text><text class="req"> *</text>
        <input
          class="field-input" v-model="form.bankProductCode"
          placeholder="如：ICBC-TAX-A" placeholder-class="ph"
          :disabled="isEdit"
        />
        <text v-if="isEdit" class="field-tip">产品编码创建后不可修改</text>
      </view>

      <view class="field">
        <text class="field-label">合作有效期至</text>
        <input class="field-input" v-model="form.cooperateUntil" placeholder="如：2027-08-30（默认 1 年）" placeholder-class="ph" />
      </view>

      <view class="field">
        <text class="field-label">额度下限（万元）</text>
        <input class="field-input" type="digit" v-model="form.amountMin" placeholder="如：100" placeholder-class="ph" />
      </view>

      <view class="field">
        <text class="field-label">额度上限（万元）</text>
        <input class="field-input" type="digit" v-model="form.amountMax" placeholder="如：500" placeholder-class="ph" />
      </view>

      <view class="field">
        <text class="field-label">进件要求（结构化 JSON，后端解析校验）</text>
        <textarea
          class="field-textarea" v-model="form.requirementText"
          placeholder="如：纳税10万以上、信用担保、营业执照加近一年流水（JSON 格式，可解析预览）"
          placeholder-class="ph"
        />
      </view>

      <!-- 解析预览（P1-5 Error 态：JSON 非法时给出字段级提示） -->
      <view v-if="parseError" class="error-box">{{ parseError }}</view>
      <view v-else-if="parseOk" class="success-box">
        解析成功 · 额度 {{ form.amountMin || '?' }}-{{ form.amountMax || '?' }} 万 · 进件要求结构校验通过
      </view>

      <view class="actions">
        <AppButton variant="secondary" size="md" @click="onParse">解析预览</AppButton>
        <AppButton variant="primary" size="md" :loading="submitting" @click="onSubmit">
          {{ isEdit ? '保存修改' : '保存草稿' }}
        </AppButton>
      </view>
    </view>

    <view class="card tip-card">
      <text class="tip-text">
        保存为草稿后可在列表页「提交审批」，由我司运营 / 超级管理员终审；
        驳回会显示原因，编辑后可重新提交。
      </text>
    </view>
  </view>
</template>

<script setup>
/**
 * 产品录入 / 编辑页（C9 入口）。
 *
 * - 新建 → 保存为 DRAFT 草稿
 * - 编辑 → DRAFT / REJECTED 可编辑重提
 *
 * 进件要求为结构化 JSON，保存前做本地解析校验（P1-5 Error 态：
 * 非法时给出字段级提示而非仅 toast）。
 */
import { ref, reactive, computed } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import { createProduct, updateProduct, getProductDetail } from '../../api/product';

const code = ref('');
const isEdit = computed(() => !!code.value);
const submitting = ref(false);

const form = reactive({
  bankProductCode: '',
  cooperateUntil: '',
  amountMin: '',
  amountMax: '',
  requirementText: '',
});

const parseOk = ref(false);
const parseError = ref('');

onLoad((query) => {
  if (query && query.code) {
    code.value = query.code;
    // 编辑态：按审批单号拉取详情回填表单（C9 编辑/重提）
    getProductDetail(query.code)
      .then((d) => {
        if (!d) return;
        form.bankProductCode = d.bankProductCode || '';
        form.cooperateUntil = d.cooperateUntil || '';
        form.amountMin = d.amountMin != null ? String(d.amountMin) : '';
        form.amountMax = d.amountMax != null ? String(d.amountMax) : '';
        const req = d.requirement;
        if (req != null) {
          form.requirementText = typeof req === 'string' ? req : JSON.stringify(req, null, 2);
        }
      })
      .catch((e) => {
        uni.showToast({ title: '加载产品详情失败', icon: 'none' });
      });
  }
});

/** 解析预览：本地 JSON 校验 + 必填校验 */
function onParse() {
  parseError.value = '';
  parseOk.value = false;

  if (!form.bankProductCode.trim()) {
    parseError.value = '请填写银行产品编码';
    return;
  }
  if (!form.requirementText.trim()) {
    parseError.value = '请填写进件要求（结构化 JSON）';
    return;
  }
  try {
    JSON.parse(form.requirementText);
    parseOk.value = true;
  } catch (e) {
    parseError.value = `进件要求不是合法 JSON：${e.message}`;
  }
}

async function onSubmit() {
  if (submitting.value) return;
  // 提交前强制校验
  onParse();
  if (parseError.value) {
    uni.showToast({ title: parseError.value, icon: 'none' });
    return;
  }

  submitting.value = true;
  try {
    const payload = {
      bankProductCode: form.bankProductCode.trim(),
      cooperateUntil: form.cooperateUntil.trim() || undefined,
      amountRange: form.amountMin || form.amountMax
        ? `${form.amountMin || '?'}-${form.amountMax || '?'}万`
        : undefined,
      requirement: JSON.parse(form.requirementText),
    };
    if (isEdit.value) {
      await updateProduct(code.value, payload);
      uni.showToast({ title: '已保存，可提交审批', icon: 'none' });
    } else {
      await createProduct(payload);
      uni.showToast({ title: '已保存为草稿', icon: 'none' });
    }
    setTimeout(() => uni.navigateBack(), 800);
  } catch (e) { /* toast 已弹出 */ }
  finally { submitting.value = false; }
}
</script>

<style scoped>
.edit-page {
  min-height: 100vh;
  padding: var(--space-4);
  background: var(--bg-page);
  box-sizing: border-box;
  padding-bottom: calc(var(--space-12) + env(safe-area-inset-bottom));
}

.card {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  padding: var(--space-4);
  box-shadow: var(--shadow-md);
  margin-bottom: var(--space-3);
}
.card-title { font-size: var(--fs-lg); font-weight: 700; color: var(--text-primary); }

.field { margin-bottom: var(--space-3); }
.field-label {
  display: block; font-size: var(--fs-sm); font-weight: 600;
  color: var(--text-primary); margin-bottom: var(--space-1);
}
.req { color: var(--danger-text); }
.field-input, .field-textarea {
  width: 100%;
  background: var(--bg-input);
  border: 2rpx solid transparent;
  border-radius: var(--radius-md);
  padding: 24rpx 28rpx;
  font-size: var(--fs-md);
  color: var(--text-primary);
  min-height: 88rpx;          /* 44px 触控 */
  box-sizing: border-box;
}
.field-textarea { min-height: 200rpx; line-height: var(--lh-base); }
/* #ifdef H5 */
.field-input:focus, .field-textarea:focus { border-color: var(--gold); background: var(--bg-card); }
/* #endif */
.field-input:disabled { opacity: .6; }
.field-tip { display: block; font-size: var(--fs-xs); color: var(--text-secondary); margin-top: var(--space-1); }
.ph { color: var(--text-placeholder); }

/* P1-5 Error / Success 态 */
.error-box, .success-box {
  border-radius: var(--radius-sm);
  padding: var(--space-3);
  font-size: var(--fs-sm);
  line-height: var(--lh-base);
  margin-bottom: var(--space-3);
}
.error-box {
  background: rgba(239, 68, 68, .06);
  border: 2rpx solid rgba(239, 68, 68, .25);
  color: var(--danger-text);
}
.success-box {
  background: rgba(16, 185, 129, .08);
  border: 2rpx solid rgba(16, 185, 129, .25);
  color: var(--success-text);
}

.actions { display: flex; gap: var(--space-3); margin-top: var(--space-3); }
.actions view, .actions button { flex: 1; min-width: 0; }

.tip-card { background: var(--bg-input); box-shadow: var(--shadow-sm); }
.tip-text { font-size: var(--fs-sm); color: var(--text-secondary); line-height: var(--lh-base); }
</style>
