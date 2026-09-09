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
              :placeholder="mode === 'staff' ? 'CRM 员工 ID（如 crm-boss-001）' : '渠道账号手机号'"
              autocomplete="username"
            >
              <template #prefix>
                <AppIcon name="user" :size="16" />
              </template>
            </el-input>
          </el-form-item>

          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              type="password"
              :placeholder="mode === 'staff' ? '密码（阶段一 SSO 模拟暂不校验）' : '密码（阶段一模拟，任意填写）'"
              show-password
              autocomplete="current-password"
            >
              <template #prefix>
                <AppIcon name="lock" :size="16" />
              </template>
            </el-input>
          </el-form-item>

          <el-button type="primary" size="large" class="login-btn" :loading="loading" native-type="submit">
            登 录
          </el-button>
        </el-form>

        <div class="login-demo">
          <span class="demo-label">演示账号：</span>
          <button
            v-for="(d, i) in demoAccounts"
            :key="i"
            class="demo-chip"
            type="button"
            @click="fillDemo(d)"
          >
            {{ d.username }}
            <span class="demo-role">（{{ d.role }}）</span>
          </button>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { sceneries } from '@/assets/login-bg';
import { useUserStore } from '@/store/user';
import { channelLogin as channelLoginApi } from '@/api/auth';
import { KEYS, getStorage, setStorage, removeStorage } from '@/utils/storage';
import { getTheme } from '@/theme';
import AppIcon from '@/components/AppIcon.vue';

const router = useRouter();
const userStore = useUserStore();
const formRef = ref();
const loading = ref(false);
const remember = ref(false);

/** 登录模式：staff 员工 SSO 模拟 / channel 渠道账号（T11/D21 渠道沙箱） */
const mode = ref('staff');

/** 登录表单（阶段一演示；正式接入 RSA + SSO 走 /api/auth/login） */
const form = reactive({ username: '', password: '' });

const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  // SSO/模拟阶段：密码不校验（placeholder 已说明"暂不校验"），避免 required 语义矛盾
  password: [{ required: false, message: '', trigger: 'blur' }],
};

const demoAccounts = [
  { username: 'crm-boss-001', password: '123456', role: '张老板' },
  { username: 'crm-adv-001', password: '123456', role: '李顾问' },
  { username: '13911112222', password: 'loan-sim-pwd', role: '渠道-陈', channel: true },
];

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

function fillDemo(d) {
  mode.value = d.channel ? 'channel' : 'staff';
  form.username = d.username;
  form.password = d.password;
  ElMessage.info(`已填充演示账号：${d.username}`);
}

async function onLogin() {
  await formRef.value.validate();
  loading.value = true;
  try {
    if (mode.value === 'channel') {
      // 渠道登录：阶段一模拟（固定传约定模拟串，后端旁路 RSA+BCrypt，T11/D21；正式接入改为 RSA 加密密码）
      const res = await channelLoginApi({ phone: form.username, password: 'loan-sim-pwd' });
      userStore.applyLogin(res);
    } else {
      // 员工登录：SSO 模拟（username 为 CRM 员工 ID），后端签发 JWT + Redis 会话
      await userStore.doLogin({ crmUserId: form.username });
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

onMounted(() => {
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
}
</style>
