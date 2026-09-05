<template>
  <view class="auth-page">
    <!-- 页面头 -->
    <view class="page-head">
      <text class="head-title">身份认证</text>
      <text class="head-sub">企业营业执照 / 个人信息认证，二选一完成即可解锁智能匹配</text>
    </view>

    <!-- 客户类型切换：双卡片 -->
    <view class="type-row">
      <view
        class="type-card u-hover"
        :class="{ active: authType === 'enterprise' }"
        @click="authType = 'enterprise'"
      >
        <view class="type-icon-wrap" :class="{ 'icon-active': authType === 'enterprise' }">
          <AppIcon name="enterprise" size="lg" />
        </view>
        <text class="type-label">企业认证</text>
        <text class="type-desc">营业执照 · 对公经营</text>
        <view class="type-check" v-if="authType === 'enterprise'">✓</view>
      </view>
      <view
        class="type-card u-hover"
        :class="{ active: authType === 'personal' }"
        @click="authType = 'personal'"
      >
        <view class="type-icon-wrap" :class="{ 'icon-active': authType === 'personal' }">
          <AppIcon name="person" size="lg" />
        </view>
        <text class="type-label">个人认证</text>
        <text class="type-desc">实名 · 工薪 / 自由职业</text>
        <view class="type-check" v-if="authType === 'personal'">✓</view>
      </view>
    </view>

    <!-- 企业表单 -->
    <view class="card form-card" v-if="authType === 'enterprise'">
      <text class="sec-title">企业信息</text>
      <view class="field">
        <text class="field-label">统一社会信用代码</text><text class="req"> *</text>
        <input
          class="field-input"
          v-model="enterpriseForm.creditCode"
          placeholder="18 位统一社会信用代码"
          placeholder-class="ph"
          maxlength="18"
        />
      </view>
      <view class="field">
        <text class="field-label">企业名称</text><text class="req"> *</text>
        <input
          class="field-input"
          v-model="enterpriseForm.enterpriseName"
          placeholder="与营业执照一致"
          placeholder-class="ph"
        />
      </view>
      <view class="field">
        <text class="field-label">联系人姓名</text>
        <input
          class="field-input"
          v-model="enterpriseForm.contactName"
          placeholder="选填"
          placeholder-class="ph"
        />
      </view>
    </view>

    <!-- 个人表单 -->
    <view class="card form-card" v-if="authType === 'personal'">
      <text class="sec-title">个人信息</text>
      <view class="field">
        <text class="field-label">真实姓名</text><text class="req"> *</text>
        <input class="field-input" v-model="personalForm.realName" placeholder="与身份证一致" placeholder-class="ph" />
      </view>
      <view class="field">
        <text class="field-label">身份证号</text><text class="req"> *</text>
        <input class="field-input" v-model="personalForm.idCardNo" placeholder="18 位身份证号" placeholder-class="ph" maxlength="18" />
      </view>
      <view class="field-row">
        <view class="field field-half">
          <text class="field-label">城市</text><text class="req"> *</text>
          <input class="field-input" v-model="personalForm.city" placeholder="如：杭州" placeholder-class="ph" />
        </view>
        <view class="field field-half">
          <text class="field-label">年龄</text><text class="req"> *</text>
          <input class="field-input" type="number" v-model="personalForm.age" placeholder="如：32" placeholder-class="ph" />
        </view>
      </view>
      <view class="field">
        <text class="field-label">资质情况（有助于提升匹配精度）</text>
        <view class="switch-list">
          <view class="switch-row" v-for="opt in assetOptions" :key="opt.key">
            <text class="switch-label">{{ opt.label }}</text>
            <switch :checked="personalForm[opt.key] === 1" color="#0B1D3A" @change="onSwitch(opt.key, $event)" />
          </view>
        </view>
      </view>
    </view>

    <!-- 合规声明 -->
    <view class="compliance" @click="agreed = !agreed">
      <view class="checkbox" :class="{ checked: agreed }">
        <text v-if="agreed" class="check-mark">✓</text>
      </view>
      <text class="compliance-text">我已阅读并同意《用户授权与隐私协议》，授权平台使用上述信息进行贷款匹配分析</text>
    </view>

    <AppButton class="submit-btn" variant="primary" size="lg" block :loading="submitting" @click="onSubmit">
      {{ submitting ? '提交中…' : '提交认证' }}
    </AppButton>

    <text class="foot-note">三要素认证为模拟核验（开发环境），上线前接入真实服务商</text>
  </view>
</template>

<script setup>
import { ref, reactive } from 'vue';
import { enterpriseAuth, personalAuth } from '../../api/auth';
import { useUserStore } from '../../store/user';
import { isUnifiedSocialCreditCode, isIdCardNo } from '../../utils/validation';

/**
 * 认证页（P0-3）：企业 / 个人认证表单 + 合规声明。
 * 设计语言：瑞幸风格 —— 双卡片类型选择、阴影大圆角卡片、深色主按钮。
 */
const store = useUserStore();

const authType = ref('enterprise');
const submitting = ref(false);
const agreed = ref(false);

const enterpriseForm = reactive({ creditCode: '', enterpriseName: '', contactName: '' });
const personalForm = reactive({
  realName: '', idCardNo: '', city: '', age: '',
  houseFlag: 0, carFlag: 0, socialSecurityFlag: 0, fundFlag: 0,
});

const assetOptions = [
  { key: 'houseFlag', label: '名下有房产' },
  { key: 'carFlag', label: '名下有车辆' },
  { key: 'socialSecurityFlag', label: '连续缴纳社保' },
  { key: 'fundFlag', label: '连续缴纳公积金' },
];

function onSwitch(key, e) {
  personalForm[key] = e.detail && e.detail.value ? 1 : 0;
}

async function onSubmit() {
  if (submitting.value) return;
  if (!agreed.value) {
    uni.showToast({ title: '请先勾选并同意合规声明', icon: 'none' });
    return;
  }
  if (authType.value === 'enterprise') {
    if (!enterpriseForm.creditCode || !enterpriseForm.enterpriseName) {
      uni.showToast({ title: '请填写信用代码与企业名称', icon: 'none' });
      return;
    }
    if (!isUnifiedSocialCreditCode(enterpriseForm.creditCode)) {
      uni.showToast({ title: '统一社会信用代码格式不正确', icon: 'none' });
      return;
    }
  } else {
    if (!personalForm.realName || !personalForm.idCardNo || !personalForm.city || !personalForm.age) {
      uni.showToast({ title: '请完整填写个人认证信息', icon: 'none' });
      return;
    }
    if (!isIdCardNo(personalForm.idCardNo)) {
      uni.showToast({ title: '身份证号格式不正确', icon: 'none' });
      return;
    }
  }

  submitting.value = true;
  try {
    if (authType.value === 'enterprise') {
      await enterpriseAuth({
        creditCode: enterpriseForm.creditCode.trim(),
        enterpriseName: enterpriseForm.enterpriseName.trim(),
        contactName: enterpriseForm.contactName.trim() || undefined,
      });
    } else {
      await personalAuth({
        realName: personalForm.realName.trim(),
        idCardNo: personalForm.idCardNo.trim(),
        city: personalForm.city.trim(),
        age: Number(personalForm.age),
        houseFlag: personalForm.houseFlag,
        carFlag: personalForm.carFlag,
        socialSecurityFlag: personalForm.socialSecurityFlag,
        fundFlag: personalForm.fundFlag,
      });
    }
    await store.refreshProfile();
    uni.showToast({ title: '认证成功', icon: 'success' });
    setTimeout(() => {
      uni.reLaunch({ url: '/pages/home/home' });
    }, 600);
  } catch (e) { /* toast 已弹出 */ }
  finally { submitting.value = false; }
}
</script>

<style scoped>
.auth-page{
  min-height:100vh;
  padding:0 32rpx 48rpx;
  background:var(--bg-page);
  box-sizing:border-box
}
.page-head{
  padding:48rpx 8rpx 32rpx
}
.head-title{
  display:block;
  font-size:44rpx;
  font-weight:800;
  color:var(--text-primary);
  letter-spacing:1rpx
}
.head-sub{
  display:block;
  margin-top:12rpx;
  font-size:25rpx;
  color:var(--text-body);
  line-height:1.5
}
.type-row{
  display:flex;
  gap:24rpx;
  margin-bottom:32rpx
}
.type-card{
  flex:1;
  position:relative;
  background:var(--bg-card);
  border:2rpx solid var(--line);
  border-radius: var(--radius-md);
  padding:32rpx 24rpx;
  display:flex;
  flex-direction:column;
  align-items:center;
  box-shadow: var(--shadow-md)
}
.type-card.active{
  border-color:var(--brand-deep);
  background:var(--bg-card);
  box-shadow:0 8rpx 24rpx rgba(11,29,58,.12)
}
.type-icon-wrap{
  width:88rpx;
  height:88rpx;
  border-radius: var(--radius-md);
  background:var(--bg-input);
  display:flex;
  align-items:center;
  justify-content:center;
  margin-bottom:20rpx
}
.type-icon-wrap.icon-active{
  background:linear-gradient(135deg,var(--brand-deep),var(--brand-bright))
}
.type-label{
  font-size:29rpx;
  font-weight:700;
  color:var(--text-primary)
}
.type-card.active .type-label{
  color:var(--brand-deep)
}
.type-desc{
  margin-top:8rpx;
  font-size:22rpx;
  color:var(--text-secondary);
  text-align:center
}
.type-check{
  position:absolute;
  top:16rpx;
  right:16rpx;
  width:36rpx;
  height:36rpx;
  border-radius:50%;
  background:var(--brand-deep);
  color:var(--bg-card);
  font-size:22rpx;
  font-weight:700;
  display:flex;
  align-items:center;
  justify-content:center
}
.card{
  background:var(--bg-card);
  border-radius: var(--radius-md);
  padding:32rpx;
  margin-bottom:32rpx;
  box-shadow: var(--shadow-md)
}
.sec-title{
  display:block;
  font-size:31rpx;
  font-weight:700;
  color:var(--text-primary);
  margin-bottom:28rpx
}
.field{
  margin-bottom:28rpx
}
.field:last-child{
  margin-bottom:0
}
.field-row{
  display:flex;
  gap:20rpx
}
.field-half{
  flex:1
}
.field-label{
  display:block;
  font-size:25rpx;
  color:var(--text-primary);
  margin-bottom:14rpx;
  font-weight:500
}
.req{
  color:var(--danger);
  margin-left:4rpx
}
.field-input{
  height:88rpx;
  padding:0 24rpx;
  background:var(--bg-input);
  border:2rpx solid var(--line);
  border-radius: var(--radius-sm);
  font-size:27rpx;
  color:var(--text-primary)
}
.ph{
  color:var(--text-secondary)
}
.switch-list{
  background:var(--bg-input);
  border-radius: var(--radius-sm);
  padding:0 24rpx
}
.switch-row{
  display:flex;
  align-items:center;
  justify-content:space-between;
  padding:24rpx 0;
  border-bottom:1rpx solid var(--line)
}
.switch-row:last-child{
  border-bottom:none
}
.switch-label{
  font-size:26rpx;
  color:var(--text-primary)
}
.compliance{
  display:flex;
  align-items:flex-start;
  margin-bottom:28rpx;
  padding:0 4rpx
}
.checkbox{
  width:36rpx;
  height:36rpx;
  border:2rpx solid var(--text-placeholder);
  border-radius:10rpx;
  background:var(--bg-card);
  display:flex;
  align-items:center;
  justify-content:center;
  flex-shrink:0;
  margin-top:2rpx
}
.checkbox.checked{
  background:var(--brand-deep);
  border-color:var(--brand-deep)
}
.check-mark{
  color:var(--bg-card);
  font-size:22rpx;
  font-weight:700
}
.compliance-text{
  flex:1;
  margin-left:16rpx;
  font-size:24rpx;
  color:var(--text-body);
  line-height:1.6
}
.submit-btn{ letter-spacing:2rpx; }
.foot-note{
  display:block;
  margin-top:28rpx;
  text-align:center;
  font-size:22rpx;
  color:var(--text-secondary);
  line-height:1.6
}

/* #ifdef H5 */
.field-input:focus, .field-textarea:focus { border-color: var(--gold); background: var(--bg-card); }
/* #endif */
</style>
