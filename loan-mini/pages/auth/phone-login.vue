<template>
  <view class="phone-login-page theme-root" :class="{ 'u-shell': store.isTablet }" :data-theme="themeMode">
    <view class="head">
      <text class="head-title">手机号验证码登录</text>
      <text class="head-sub">微信一键登录为主，手机号验证码仅作为备用登录方式</text>
    </view>

    <view class="card">
      <view class="field">
        <AppIcon name="phone" size="sm" />
        <input v-model="phone" type="number" maxlength="11" placeholder="请输入手机号" />
      </view>
      <view class="field">
        <AppIcon name="shield" size="sm" />
        <input v-model="smsCode" type="number" maxlength="6" placeholder="请输入验证码" />
        <button class="code-btn" @click="sendCode('LOGIN')" :disabled="countdown > 0">{{ countdown ? `${countdown}s` : '获取验证码' }}</button>
      </view>
      <view class="field">
        <AppIcon name="shield" size="sm" />
        <input v-model="captchaCode" type="number" maxlength="4" placeholder="图片验证码" />
        <button class="captcha-btn" @click="refreshCaptcha">
          <image v-if="captcha.imageBase64" class="captcha-image" :src="captchaSrc" mode="aspectFit" />
          <text v-else class="captcha-text">刷新</text>
        </button>
      </view>
      <AppButton class="submit" variant="primary" size="lg" block :loading="submitting" :disabled="!agreementChecked" @click="onSubmit">手机号登录</AppButton>
      <LoginConsent v-model="agreementChecked" @open="showAgreement" />
    </view>
  </view>
</template>

<script setup>
import { reactive, ref, computed, onMounted, onUnmounted } from 'vue';
import { loginByCode, sendLoginCode, getCaptcha } from '../../api/auth';
import { useUserStore } from '../../store/user';
import { useThemeMode } from '../../theme';
import LoginConsent from '../../components/LoginConsent.vue';
import { clearPendingInviteCode, getPendingInviteCode } from '../../utils/invitation';

/**
 * 手机号验证码登录（独立切换页，P2 定稿）。
 *
 * <p>与落地页的微信一键登录分离：由落地页「使用手机号验证码登录」入口进入。
 * 登录成功 → 写入 token / user → reLaunch 首页。邀请码沿用本地 pending（App.vue 已捕获）。
 */
const store = useUserStore();
const themeMode = useThemeMode();

const phone = ref('');
const smsCode = ref('');
const captchaCode = ref('');
const countdown = ref(0);
const submitting = ref(false);
const agreementChecked = ref(false);
const captcha = reactive({ captchaId: '', imageBase64: '' });
/** 验证码图片由服务端渲染为 Base64 PNG，答案不下发到客户端。 */
const captchaSrc = computed(() => (captcha.imageBase64 ? `data:image/png;base64,${captcha.imageBase64}` : ''));
const timers = [];

async function refreshCaptcha() { Object.assign(captcha, await getCaptcha()); captchaCode.value=''; }
function startCountdown(target) { target.value=60; const timer=setInterval(()=>{target.value-=1;if(target.value<=0)clearInterval(timer);},1000);timers.push(timer); }

/** 发送验证码（60s 倒计时防重复） */
async function sendCode(scene = 'LOGIN') {
  if (!ensureAgreement()) return;
  if (scene !== 'LOGIN' || !/^1\d{10}$/.test(phone.value)) {
    uni.showToast({ title: '请输入正确手机号', icon: 'none' });
    return;
  }
  const answer = captchaCode.value;
  if(!answer){ uni.showToast({title:'请输入随机验证码',icon:'none'}); return; }
  try {
    const response=await sendLoginCode(phone.value,captcha.captchaId,answer,'LOGIN');
    if(response?.devCode) smsCode.value=response.devCode;
    startCountdown(countdown);
    await refreshCaptcha();
    uni.showToast({ title: response?.devCode?`测试码已填入 ${response.devCode}`:'验证码已发送', icon: 'none' });
  } catch (e) {
    uni.showToast({ title: e.message || '发送失败', icon: 'none' });
  }
}

/** 提交登录 */
async function onSubmit() {
  if (!ensureAgreement()) return;
  if (!phone.value || !smsCode.value || !captchaCode.value) {
    uni.showToast({ title: '请完整填写登录信息', icon: 'none' });
    return;
  }
  submitting.value = true;
  try {
    const data = await loginByCode(phone.value, smsCode.value, getPendingInviteCode());
    store.setToken(data.token);
    store.setUser(data.user);
    clearPendingInviteCode();
    uni.reLaunch({ url: '/pages/home/home' });
  } catch (e) {
    uni.showToast({ title: e.message || '验证码登录失败', icon: 'none' });
    refreshCaptcha();
  } finally {
    submitting.value = false;
  }
}

function ensureAgreement() {
  if (agreementChecked.value) return true;
  uni.showToast({ title: '请先阅读并同意用户协议与隐私政策', icon: 'none' });
  return false;
}
function showAgreement(title) {
  const content = title === '隐私政策' ? '我们仅在提供登录、身份认证和资质风险分析所必需的范围内处理您的信息。' : '使用本服务即表示您接受平台服务规则。本服务不推荐具体金融产品，不预测审批结果。';
  uni.showModal({ title, content, showCancel: false, confirmText: '我知道了' });
}

onMounted(refreshCaptcha);
onUnmounted(() => { timers.forEach(clearInterval); });
</script>

<style scoped>
.phone-login-page {
  min-height: 100vh;
  background: var(--bg-page);
  padding: var(--space-page-gutter);
  box-sizing: border-box;
}
.head { padding: 24rpx 8rpx 32rpx; }
.head-title { font-size: 44rpx; font-weight: 800; color: var(--text-primary); }
.head-sub { display: block; margin-top: 12rpx; font-size: 24rpx; color: var(--text-secondary); }

.card {
  background: var(--bg-card);
  border: 1rpx solid var(--line);
  border-radius: var(--radius-md);
  padding: 32rpx;
  box-shadow: var(--shadow-md);
}
.captcha-btn{min-width:170rpx;display:flex;align-items:center;justify-content:center;height:72rpx;padding:0;background:var(--bg-input)}
.captcha-image{width:170rpx;height:72rpx;border-radius:var(--radius-sm)}
.captcha-text{font-size:24rpx;color:var(--text-secondary)}
.field {
  display: flex;
  align-items: center;
  gap: 18rpx;
  min-height: 92rpx;
  margin-bottom: 20rpx;
  padding: 0 26rpx;
  background: var(--bg-input);
  border: 1rpx solid var(--line);
  border-radius: var(--radius-md);
  transition: border-color 0.15s;
}
/* 输入字号 ≥ 16px（32rpx）：低于 16px 会被 iOS Safari 聚焦自动放大视口（布局跳动根因） */
.field input { flex: 1; min-width: 0; height: 92rpx; font-size: 32rpx; color: var(--text-primary); }
/* 聚焦触控高亮：只改边框色不改宽高，避免布局跳动 */
.field:focus-within { border-color: var(--brand-deep); }
.code-btn {
  flex-shrink: 0;
  margin: 0;
  padding: 0 0 0 20rpx;
  border: 0;
  border-left: 1rpx solid var(--line);
  border-radius: 0;
  background: transparent;
  color: var(--brand-deep);
  font-size: 25rpx;
  font-weight: 700;
  line-height: 44rpx;
}
.code-btn::after { border: 0; }
.code-btn[disabled] { color: var(--text-placeholder); }
.code-btn:active { opacity: 0.6; }
.submit { margin-top: 8rpx; }
</style>
