<template>
  <div class="login-page">
    <!-- 左侧：品牌叙事（墨金主题配图 + 墨金蒙层） -->
    <section class="login-brand" :style="brandStyle">
      <div class="brand-overlay" />
      <div class="brand-hero">
        <h1 class="hero-title">企业资金方案智能匹配</h1>
        <p class="hero-desc">
          聚合多家合作银行产品，基于企业经营数据与银行准入条件，为企业提供匹配程度分析与专业咨询建议。
        </p>
      </div>

      <ul class="brand-points">
        <li>
          <AppIcon name="success" :size="16" />
          <span>多银行产品库统一匹配</span>
        </li>
        <li>
          <AppIcon name="success" :size="16" />
          <span>经营数据驱动准入分析</span>
        </li>
        <li>
          <AppIcon name="success" :size="16" />
          <span>专业顾问一对一咨询</span>
        </li>
      </ul>

      <footer class="brand-foot">合规声明：匹配程度分析不构成任何银行通过承诺</footer>
    </section>

    <!-- 右侧：登录卡片 -->
    <section class="login-panel">
      <div class="login-card">
        <div class="login-tabs" role="tablist" aria-label="登录方式">
          <button
            type="button"
            role="tab"
            class="login-tab"
            :class="{ active: mode === 'staff' }"
            @click="mode = 'staff'"
          >员工登录</button>
          <button
            type="button"
            role="tab"
            class="login-tab"
            :class="{ active: mode === 'channel' }"
            @click="mode = 'channel'"
          >渠道登录</button>
        </div>
        <h2 class="login-title">{{ mode === 'staff' ? '管理员登录' : '渠道合作方登录' }}</h2>
        <p class="login-sub">{{ mode === 'staff' ? '请使用 CRM 员工身份登录系统' : '渠道账号登录，进入沙箱工作台' }}</p>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          size="large"
          @submit.prevent="onLogin"
        >
          <el-form-item prop="username">
            <el-input
              v-model="form.username"
              placeholder="手机号"
              autocomplete="username"
            >
              <template #prefix>
                <AppIcon name="user" :size="16" />
              </template>
            </el-input>
          </el-form-item>

          <el-form-item v-if="loginType === 'password'" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="请输入密码"
              show-password
              autocomplete="current-password"
            >
              <template #prefix>
                <AppIcon name="lock" :size="16" />
              </template>
            </el-input>
          </el-form-item>
          <el-form-item v-else prop="code"><el-input v-model="form.code" placeholder="短信验证码"><template #append><el-button :disabled="codeCountdown > 0" @click="sendCode">{{ codeCountdown ? `${codeCountdown}s` : '获取验证码' }}</el-button></template></el-input></el-form-item>
          <el-form-item>
            <el-input v-model="form.captchaCode" placeholder="请输入图片中的 4 位验证码" maxlength="4">
              <template #append>
                <button type="button" class="captcha-image-btn" title="点击刷新验证码" @click="refreshCaptcha">
                  <img v-if="captcha.imageBase64" class="captcha-image" :src="captchaSrc(captcha)" alt="登录验证码" />
                  <span v-else class="captcha-placeholder">点击刷新</span>
                </button>
              </template>
            </el-input>
          </el-form-item>
          <div class="login-type-tabs" role="tablist" aria-label="账号登录方式">
            <button type="button" role="tab" :class="{ active: loginType === 'password' }" @click="loginType='password'">密码登录</button>
            <button type="button" role="tab" :class="{ active: loginType === 'code' }" @click="loginType='code'">验证码登录</button>
            <button type="button" class="login-link" @click="forgotPassword">忘记密码</button>
          </div>

          <el-button type="primary" size="large" class="login-btn" :loading="loading" native-type="submit">
            登 录
          </el-button>
        </el-form>

        <el-dialog v-model="resetVisible" title="验证码找回密码" width="420px" append-to-body>
          <el-form label-position="top">
            <el-form-item label="登录手机号"><el-input v-model="resetForm.phone" maxlength="11" /></el-form-item>
            <el-form-item label="随机验证码">
              <el-input v-model="resetForm.captchaCode" maxlength="4">
                <template #append>
                  <button type="button" class="captcha-image-btn" title="点击刷新验证码" @click="refreshResetCaptcha">
                    <img v-if="resetCaptcha.imageBase64" class="captcha-image" :src="captchaSrc(resetCaptcha)" alt="验证码" />
                    <span v-else class="captcha-placeholder">点击刷新</span>
                  </button>
                </template>
              </el-input>
            </el-form-item>
            <el-form-item label="短信验证码"><el-input v-model="resetForm.code"><template #append><el-button :disabled="resetCountdown > 0" @click="sendResetCode">{{ resetCountdown ? `${resetCountdown}s` : '获取验证码' }}</el-button></template></el-input></el-form-item>
            <el-form-item label="新密码"><el-input v-model="resetForm.password" type="password" show-password placeholder="8-64位，同时包含字母和数字" /></el-form-item>
            <el-form-item label="确认新密码"><el-input v-model="resetForm.confirmPassword" type="password" show-password /></el-form-item>
          </el-form>
          <template #footer><el-button @click="resetVisible=false">取消</el-button><el-button type="primary" :loading="resetting" @click="submitReset">设置新密码</el-button></template>
        </el-dialog>

      </div>
    </section>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted, watch } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { sceneries } from '@/assets/login-bg';
import { useUserStore } from '@/store/user';
import { codeLogin, sendLoginCode, passwordLogin, resetPassword, getCaptcha, getPublicKey } from '@/api/auth';
import JSEncrypt from 'jsencrypt';
import { KEYS, getStorage, setStorage, removeStorage } from '@/utils/storage';
import { getTheme } from '@/theme';
import AppIcon from '@/components/AppIcon.vue';

const router = useRouter();
const userStore = useUserStore();
const formRef = ref();
const loading = ref(false);
const remember = ref(false);

/** 员工与渠道都支持密码登录、短信验证码登录和验证码找回密码；默认走账号密码 + 4 位图片验证码。 */
const mode = ref('staff');
const loginType = ref('password');
const codeCountdown = ref(0);
const captcha = reactive({ captchaId: '', imageBase64: '' });
const resetCaptcha = reactive({ captchaId: '', imageBase64: '' });
const resetVisible = ref(false);
const resetCountdown = ref(0);
const resetting = ref(false);
const resetForm = reactive({ phone: '', code: '', password: '', confirmPassword: '', captchaCode: '' });

/** 登录表单 */
const form = reactive({ username: '', password: '', code: '', captchaCode: '' });

const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
};

/**
 * 主题感登录配图配置：
 *  - 默认按当前主题自动匹配墨金主题生成图（inkDark / inkLight）。
 *  - 可通过 .env 覆盖：VITE_LOGIN_SCENERY = skyline | office | twilight | inkDark | inkLight | none | custom
 *  - custom 需同时配置 VITE_LOGIN_BG_URL。
 */
const isDark = getTheme().mode === 'dark';
const defaultKey = isDark ? 'inkDark' : 'inkLight';
const sceneryKey = (import.meta.env.VITE_LOGIN_SCENERY || defaultKey).toLowerCase();
const customUrl = import.meta.env.VITE_LOGIN_BG_URL || '';
const sceneryUrl =
  sceneryKey === 'custom' ? customUrl : sceneryKey === 'none' ? '' : sceneries[sceneryKey] || sceneries[defaultKey];

/** 左侧品牌区背景 */
const brandStyle = computed(() => {
  if (sceneryUrl) {
    return {
      backgroundImage: `url(${sceneryUrl})`,
      backgroundSize: 'cover',
      backgroundPosition: 'center',
    };
  }
  return {};
});

watch([mode, loginType], () => { form.code=''; form.password=''; form.captchaCode=''; if (loginType.value === 'password') refreshCaptcha(); });

async function encryptPassword(password) {
  const res = await getPublicKey();
  const publicKey = res?.data?.publicKey;
  if (!publicKey) throw new Error('登录公钥不可用');
  const encryptor = new JSEncrypt();
  encryptor.setPublicKey(publicKey);
  const encrypted = encryptor.encrypt(password);
  if (!encrypted) throw new Error('密码加密失败');
  return encrypted;
}

async function onLogin() {
  await formRef.value.validate();
  loading.value = true;
  try {
    const accountType = mode.value === 'staff' ? 'STAFF' : 'CHANNEL';
    if (loginType.value === 'code') {
      const res = await codeLogin({ phone: form.username, code: form.code, accountType });
      userStore.applyLogin(res);
    } else {
      if (!form.captchaCode) return ElMessage.warning('请输入随机验证码');
      const encryptedPassword = await encryptPassword(form.password);
      const res = await passwordLogin({ phone: form.username, password: encryptedPassword, accountType, captchaId: captcha.captchaId, captchaCode: form.captchaCode });
      userStore.applyLogin(res);
    }
    if (remember.value) {
      setStorage(KEYS.REMEMBER_USERNAME, form.username);
    } else {
      removeStorage(KEYS.REMEMBER_USERNAME);
    }
    ElMessage.success('登录成功');
    router.push('/');
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false;
  }
}
async function refreshCaptcha() { const res=await getCaptcha(); Object.assign(captcha,res.data||res); form.captchaCode=''; }
async function refreshResetCaptcha() { const res=await getCaptcha(); Object.assign(resetCaptcha,res.data||res); resetForm.captchaCode=''; }
/** 验证码图片是服务端渲染的 Base64 PNG，答案不下发到前端。 */
function captchaSrc(item) { return item.imageBase64 ? `data:image/png;base64,${item.imageBase64}` : ''; }
function startCountdown(target) { target.value=60; const t=setInterval(()=>{ target.value--; if(target.value<=0) clearInterval(t); },1000); }
async function sendCode() { if (!/^1\d{10}$/.test(form.username)) return ElMessage.warning('请输入正确手机号'); if(!form.captchaCode)return ElMessage.warning('请输入随机验证码'); const res=await sendLoginCode(form.username,captcha.captchaId,form.captchaCode,'LOGIN'); const devCode=res?.data?.devCode; if(devCode)form.code=devCode; startCountdown(codeCountdown); form.captchaCode=''; await refreshCaptcha(); ElMessage.success(devCode?`测试验证码已自动填入：${devCode}`:'验证码已发送'); }
function forgotPassword() { resetForm.phone=form.username; resetVisible.value=true; refreshResetCaptcha(); }
async function sendResetCode() { if(!/^1\d{10}$/.test(resetForm.phone))return ElMessage.warning('请输入正确手机号'); if(!resetForm.captchaCode)return ElMessage.warning('请输入随机验证码'); const res=await sendLoginCode(resetForm.phone,resetCaptcha.captchaId,resetForm.captchaCode,'RESET_PASSWORD'); const devCode=res?.data?.devCode; if(devCode)resetForm.code=devCode; startCountdown(resetCountdown); resetForm.captchaCode=''; await refreshResetCaptcha(); ElMessage.success(devCode?`测试验证码已自动填入：${devCode}`:'验证码已发送'); }
async function submitReset() { if(!resetForm.code)return ElMessage.warning('请输入短信验证码'); if(!/^(?=.*[A-Za-z])(?=.*\d).{8,64}$/.test(resetForm.password))return ElMessage.warning('密码须为8-64位且包含字母和数字'); if(resetForm.password!==resetForm.confirmPassword)return ElMessage.warning('两次密码不一致'); resetting.value=true; try{ const password=await encryptPassword(resetForm.password); await resetPassword({phone:resetForm.phone,code:resetForm.code,password,accountType:mode.value==='staff'?'STAFF':'CHANNEL'}); resetVisible.value=false; form.username=resetForm.phone; loginType.value='password'; ElMessage.success('密码设置成功，请使用新密码登录'); }finally{resetting.value=false;} }

onMounted(() => {
  refreshCaptcha();
  const saved = getStorage(KEYS.REMEMBER_USERNAME);
  if (saved) {
    form.username = saved;
    remember.value = true;
  }
});
</script>

<style scoped>
.login-page {
  display: flex;
  flex-direction: row;
  min-height: 100vh;
  background: var(--loan-bg);
  /* Prevent mobile Safari text autosizing from changing the login layout. */
  -webkit-text-size-adjust: 100%;
  text-size-adjust: 100%;
}

/* ============================================================
 * 左侧：品牌叙事（60%，可配置商务风景图）
 * ============================================================ */
.login-brand {
  position: relative;
  flex: 3;
  display: flex;
  flex-direction: column;
  padding: 56px 72px;
  background: linear-gradient(155deg, var(--loan-primary) 0%, var(--loan-primary) 50%, var(--loan-primary) 100%);
  background-color: var(--loan-primary);
  color: var(--loan-paper);
  overflow: hidden;
}

/* 蓝色蒙层：保证风景图上文字始终清晰 */
.brand-overlay {
  position: absolute;
  inset: 0;
  background: linear-gradient(
    155deg,
    rgba(37, 83, 212, 0.75) 0%,
    rgba(47, 108, 217, 0.6) 50%,
    rgba(60, 130, 230, 0.55) 100%
  );
  pointer-events: none;
}

/* ============================================================
 * 主题适配：配图 + 文字风格均随明暗主题联动
 * ============================================================ */

/* 浅色模式：左侧配图为明亮商务城市，文字改用深墨蓝以保证可读性 */
:root[data-theme="light"] .login-brand {
  color: var(--loan-ink);
}

:root[data-theme="light"] .brand-mark {
  background: rgba(22, 32, 58, 0.08);
  color: var(--loan-ink);
}

:root[data-theme="light"] .brand-points svg {
  background: rgba(22, 32, 58, 0.08);
}

:root[data-theme="light"] .hero-title,
:root[data-theme="light"] .hero-desc,
:root[data-theme="light"] .brand-points li,
:root[data-theme="light"] .brand-foot {
  text-shadow: none;
}

:root[data-theme="light"] .brand-overlay {
  background: linear-gradient(
    155deg,
    rgba(255, 255, 255, 0.82) 0%,
    rgba(255, 255, 255, 0.55) 50%,
    rgba(255, 255, 255, 0.22) 100%
  );
}

/* 深色模式：标题使用暖金渐变，整体保持高对比浅色文字 */
:root[data-theme="dark"] .brand-overlay {
  background: linear-gradient(
    155deg,
    rgba(14, 22, 38, 0.78) 0%,
    rgba(22, 32, 58, 0.62) 50%,
    rgba(14, 22, 38, 0.42) 100%
  );
}

:root[data-theme="dark"] .hero-title {
  background: linear-gradient(90deg, #F2C879 0%, #D9A441 50%, #E0AE4E 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  text-shadow: none;
}

:root[data-theme="dark"] .hero-desc {
  color: #E8EDF5;
  opacity: 0.92;
}

:root[data-theme="dark"] .brand-points li {
  color: #E8EDF5;
  opacity: 0.95;
}

:root[data-theme="dark"] .brand-foot {
  color: #B6C2D6;
  opacity: 0.8;
}

.brand-hero,
.brand-points,
.brand-foot {
  position: relative;
  z-index: 1;
}

.brand-mark {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.2);
  color: var(--loan-paper);
  display: flex;
  align-items: center;
  justify-content: center;
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
}

.brand-name {
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 0.5px;
}

.brand-hero {
  margin-top: 96px;
  max-width: 520px;
}

.hero-title {
  font-size: 34px;
  font-weight: 700;
  margin: 0 0 18px;
  line-height: 1.3;
  letter-spacing: 0.5px;
  text-shadow: 0 2px 16px rgba(15, 23, 42, 0.25);
}

.hero-desc {
  font-size: 15px;
  line-height: 1.75;
  margin: 0;
  opacity: 0.92;
  text-shadow: 0 1px 8px rgba(15, 23, 42, 0.2);
}

.brand-points {
  list-style: none;
  padding: 0;
  margin: 36px 0 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.brand-points li {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  opacity: 0.96;
  text-shadow: 0 1px 6px rgba(15, 23, 42, 0.2);
}

.brand-points svg {
  flex-shrink: 0;
  background: rgba(255, 255, 255, 0.18);
  border-radius: 50%;
  padding: 4px;
  width: 24px;
  height: 24px;
}

.brand-foot {
  margin-top: auto;
  font-size: 12px;
  opacity: 0.7;
  padding-top: 32px;
}

/* ============================================================
 * 右侧：登录卡片（40%）
 * ============================================================ */
.login-panel {
  flex: 2;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--loan-bg);
  padding: 24px;
}

.login-card {
  width: 420px;
  max-width: 100%;
  padding: 48px 44px;
  background: var(--loan-card-bg);
  border: 1px solid var(--loan-border);
  border-radius: 16px;
  box-shadow: var(--loan-shadow-lg);
}

.login-tabs {
  display: inline-flex;
  gap: 4px;
  padding: 4px;
  margin-bottom: 28px;
  background: var(--loan-surface);
  border: 1px solid var(--loan-border);
  border-radius: 10px;
}

.login-tab {
  border: none;
  background: transparent;
  padding: 8px 20px;
  border-radius: 8px;
  font-size: 13px;
  color: var(--loan-text-secondary);
  cursor: pointer;
  transition: var(--loan-transition);
}

.login-tab.active {
  background: var(--loan-primary);
  color: var(--loan-paper);
  font-weight: 500;
}

.login-title {
  font-size: 24px;
  font-weight: 600;
  color: var(--loan-text);
  margin: 0 0 8px;
}

.login-sub {
  font-size: 13px;
  color: var(--loan-text-secondary);
  margin: 0 0 28px;
}

.login-btn {
  width: 100%;
  font-weight: 500;
  letter-spacing: 2px;
  margin-top: 8px;
  touch-action: manipulation;
}

/* 登录方式切换：避免浏览器原生 button 样式与墨金设计系统冲突 */
.login-type-tabs {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: -6px 0 18px;
  padding: 4px;
  background: var(--loan-surface);
  border: 1px solid var(--loan-border);
  border-radius: 12px;
}

.login-type-tabs button {
  min-height: 32px;
  border: 0;
  border-radius: 8px;
  padding: 0 12px;
  background: transparent;
  color: var(--loan-text-secondary);
  font: inherit;
  font-size: 12px;
  cursor: pointer;
  white-space: nowrap;
  transition: var(--loan-transition);
}

.login-type-tabs button.active {
  background: var(--loan-primary-soft);
  color: var(--loan-primary);
  font-weight: 600;
}

.login-type-tabs button.login-link {
  margin-left: auto;
  color: var(--loan-text-muted);
}

.login-type-tabs button.login-link:hover {
  color: var(--loan-primary);
}

.login-demo {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 28px;
  padding-top: 20px;
  border-top: 1px dashed var(--loan-border);
  font-size: 12px;
  color: var(--loan-text-secondary);
}

.demo-label {
  color: var(--loan-text-muted);
}

.demo-chip {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 3px 10px;
  border: 1px solid var(--loan-border);
  border-radius: 999px;
  background: var(--loan-surface);
  color: var(--loan-primary);
  font-size: 12px;
  cursor: pointer;
  transition: var(--loan-transition);
}

.demo-chip:hover {
  border-color: var(--loan-primary);
  background: var(--loan-primary-soft);
}

.demo-role {
  color: var(--loan-text-muted);
  font-size: 11px;
}

/* 表单项间距优化 */
.login-card :deep(.el-form-item) {
  margin-bottom: 22px;
}

.login-card :deep(.el-input__wrapper) {
  padding: 6px 12px;
}

/* 响应式：窄屏隐藏品牌区 */
@media (max-width: 900px) {
  .login-brand {
    display: none;
  }
  .login-panel {
    flex: 1;
  }
  .login-card {
    padding: 32px 24px;
  }

  /* iOS Safari zooms focused inputs whose computed font-size is below 16px. */
  .login-card :deep(.el-input__inner) {
    font-size: 16px;
  }

  .login-card :deep(.el-input__wrapper),
  .login-card :deep(.el-button) {
    touch-action: manipulation;
  }

  /* Keep press feedback visual-only; never scale the form or its card. */
  .login-card :deep(.el-button:active),
  .login-card .login-tab:active,
  .login-card .login-type-tabs button:active {
    transform: none;
  }
}

/* 随机 4 位验证码：服务端渲染的 Base64 PNG，点击图片即刷新。 */
.captcha-image-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 116px;
  height: 34px;
  padding: 0;
  border: 0;
  border-radius: var(--loan-radius-sm);
  background: var(--loan-surface);
  cursor: pointer;
}
.captcha-image {
  width: 116px;
  height: 34px;
  border-radius: var(--loan-radius-sm);
}
.captcha-placeholder {
  font-size: 12px;
  color: var(--loan-text-muted);
}
</style>
