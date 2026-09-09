<template>
  <view class="edit-page theme-root" :data-theme="themeMode">
    <AppEmpty v-if="!canEdit" title="暂无权限" desc="当前账号没有产品录入或编辑权限，请联系管理员" />
    <template v-else>
    <view class="card">
      <text class="card-title">{{ isEdit ? '编辑产品' : '录入产品' }}</text>

      <view class="field">
        <text class="field-label">产品名称 <text class="req">*</text></text>
        <input class="field-input" v-model="form.productName" maxlength="128"
          placeholder="请输入对外产品名称" placeholder-class="ph" />
      </view>

      <view class="field">
        <text class="field-label">适用客群 <text class="req">*</text></text>
        <picker :range="customerGroupOptions" range-key="label" @change="onCustomerGroupChange">
          <view class="field-input picker-value">{{ customerGroupLabel }}</view>
        </picker>
      </view>

      <view class="field">
        <text class="field-label">合作有效期至</text>
        <picker mode="date" :value="form.cooperateUntil" :start="tomorrow" @change="form.cooperateUntil = $event.detail.value">
          <view class="field-input picker-value" :class="{ 'is-placeholder': !form.cooperateUntil }">
            {{ form.cooperateUntil || '未选择时默认审核通过日起 1 年' }}
          </view>
        </picker>
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
        <text class="field-label">利率区间（%）</text>
        <view class="range-row">
          <input class="field-input" type="digit" v-model="form.rateMin" placeholder="下限" placeholder-class="ph" />
          <text>至</text>
          <input class="field-input" type="digit" v-model="form.rateMax" placeholder="上限" placeholder-class="ph" />
        </view>
      </view>

      <view class="field">
        <text class="field-label">期限区间（月）</text>
        <view class="range-row">
          <input class="field-input" type="number" v-model="form.termMin" placeholder="下限" placeholder-class="ph" />
          <text>至</text>
          <input class="field-input" type="number" v-model="form.termMax" placeholder="上限" placeholder-class="ph" />
        </view>
      </view>

      <view class="field">
        <text class="field-label">纳税门槛（万元/年）</text>
        <input class="field-input" type="digit" v-model="form.taxThreshold" placeholder="例如：10" placeholder-class="ph" />
      </view>

      <view class="field">
        <text class="field-label">开票要求</text>
        <input class="field-input" v-model="form.invoiceRequire" maxlength="255"
          placeholder="例如：近12个月开票额不低于100万" placeholder-class="ph" />
      </view>

      <view class="field">
        <text class="field-label">进件要求</text>
        <textarea class="field-textarea" v-model="form.requirementText" maxlength="1000"
          placeholder="请用业务语言说明准入条件、担保方式和材料清单"
          placeholder-class="ph" />
      </view>

      <view v-if="formError" class="error-box">{{ formError }}</view>

      <AppButton variant="primary" size="lg" block :loading="submitting" @click="onSubmit">
        {{ isEdit ? '保存修改' : '保存草稿' }}
      </AppButton>
    </view>

    <view class="card tip-card">
      <text class="tip-text">
        保存为草稿后可在列表页「提交审核」，由我司老板 / 超级管理员终审；
        驳回会显示原因，编辑后可重新提交。
      </text>
    </view>
    </template>
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
import { useUserStore } from '../../store/user';
import { useThemeMode } from '../../theme';
import AppEmpty from '../../components/AppEmpty.vue';

const store = useUserStore();
const themeMode = useThemeMode();
const canEdit = computed(() => store.hasPermission('mini:product:create'));
const code = ref('');
const isEdit = computed(() => !!code.value);
const submitting = ref(false);

const form = reactive({
  productName: '',
  customerGroup: 'ENTERPRISE',
  cooperateUntil: '',
  amountMin: '',
  amountMax: '',
  rateMin: '',
  rateMax: '',
  termMin: '',
  termMax: '',
  taxThreshold: '',
  invoiceRequire: '',
  requirementText: '',
});

const formError = ref('');
const customerGroupOptions = [
  { label: '企业客户', value: 'ENTERPRISE' },
  { label: '个人客户', value: 'PERSONAL' },
];
const customerGroupLabel = computed(() => customerGroupOptions.find(item => item.value === form.customerGroup)?.label || '请选择');
const tomorrow = new Date(Date.now() + 86400000).toISOString().slice(0, 10);

onLoad((query) => {
  if (!canEdit.value) return;
  if (query && query.code) {
    code.value = query.code;
    // 编辑态：按审核单号拉取详情回填表单（C9 编辑/重提）
    getProductDetail(query.code)
      .then((d) => {
        if (!d) return;
        form.productName = d.productName || '';
        form.customerGroup = d.customerGroup || 'ENTERPRISE';
        form.cooperateUntil = d.cooperateUntil || '';
        form.amountMin = toWanText(d.amountMin);
        form.amountMax = toWanText(d.amountMax);
        form.rateMin = toPercentText(d.rateMin);
        form.rateMax = toPercentText(d.rateMax);
        form.termMin = valueText(d.termMin);
        form.termMax = valueText(d.termMax);
        form.taxThreshold = toWanText(d.taxThreshold);
        form.invoiceRequire = d.invoiceRequire || '';
        form.requirementText = requirementDescription(d.bizTermsJson);
      })
      .catch((e) => {
        uni.showToast({ title: '加载产品详情失败', icon: 'none' });
      });
  }
});

function onCustomerGroupChange(event) {
  form.customerGroup = customerGroupOptions[Number(event.detail.value)]?.value || 'ENTERPRISE';
}

function validateForm() {
  formError.value = '';
  if (!form.productName.trim()) return '请填写产品名称';
  const pairs = [
    [form.amountMin, form.amountMax, '额度'],
    [form.rateMin, form.rateMax, '利率'],
    [form.termMin, form.termMax, '期限'],
  ];
  for (const [min, max, label] of pairs) {
    if (min !== '' && Number(min) < 0 || max !== '' && Number(max) < 0) return `${label}不能小于 0`;
    if (min !== '' && max !== '' && Number(min) > Number(max)) return `${label}下限不能大于上限`;
  }
  return '';
}

async function onSubmit() {
  if (submitting.value) return;
  formError.value = validateForm();
  if (formError.value) {
    uni.showToast({ title: formError.value, icon: 'none' });
    return;
  }

  submitting.value = true;
  try {
    const payload = {
      productName: form.productName.trim(),
      customerGroup: form.customerGroup,
      cooperateUntil: form.cooperateUntil.trim() || undefined,
      amountMin: toYuan(form.amountMin), amountMax: toYuan(form.amountMax),
      rateMin: toRate(form.rateMin), rateMax: toRate(form.rateMax),
      termMin: integerOrNull(form.termMin), termMax: integerOrNull(form.termMax),
      taxThreshold: toYuan(form.taxThreshold),
      invoiceRequire: form.invoiceRequire.trim() || undefined,
      bizTermsJson: JSON.stringify({ description: form.requirementText.trim() }),
    };
    if (isEdit.value) {
      await updateProduct(code.value, payload);
      uni.showToast({ title: '已保存，可提交审核', icon: 'none' });
    } else {
      await createProduct(payload);
      uni.showToast({ title: '已保存为草稿', icon: 'none' });
    }
    setTimeout(() => uni.navigateBack(), 800);
  } catch (e) { /* toast 已弹出 */ }
  finally { submitting.value = false; }
}

const valueText = value => value == null ? '' : String(value);
const toWanText = value => value == null ? '' : String(Number(value) / 10000);
const toPercentText = value => value == null ? '' : String(Number(value) * 100);
const numberOrNull = value => value === '' ? null : Number(value);
const integerOrNull = value => value === '' ? null : Math.trunc(Number(value));
const toYuan = value => value === '' ? null : Number(value) * 10000;
const toRate = value => value === '' ? null : Number(value) / 100;
function requirementDescription(value) {
  if (!value) return '';
  try { return JSON.parse(value)?.description || ''; } catch { return String(value); }
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
.picker-value { display: flex; align-items: center; }
.is-placeholder { color: var(--text-placeholder); }
.range-row { display: flex; align-items: center; gap: var(--space-2); }
.range-row .field-input { flex: 1; min-width: 0; }

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

.tip-card { background: var(--bg-input); box-shadow: var(--shadow-sm); }
.tip-text { font-size: var(--fs-sm); color: var(--text-secondary); line-height: var(--lh-base); }
</style>
