<template>
  <!-- on-demand 引入下通过 ConfigProvider 注入中文语言包（替代原 app.use(ElementPlus,{locale})） -->
  <el-config-provider :locale="zhCn">
    <template v-if="!renderError">
      <router-view />
      <!-- 全局右键菜单（任意页面 openContextMenu 触发） -->
      <AppContextMenu />
    </template>
    <div v-else class="app-runtime-error" role="alert">
      <div class="app-runtime-error__card">
        <div class="app-runtime-error__icon">!</div>
        <h2>页面加载异常</h2>
        <p>当前页面组件发生异常，业务数据未被修改。可以先刷新页面，若持续出现请联系管理员。</p>
        <div class="app-runtime-error__actions">
          <el-button type="primary" @click="reloadPage">刷新页面</el-button>
          <el-button @click="goWorkbench">返回工作台</el-button>
        </div>
      </div>
    </div>
  </el-config-provider>
</template>

<script setup>
import { onMounted, onErrorCaptured, ref } from 'vue';
import zhCn from 'element-plus/es/locale/lang/zh-cn';
import { useDictStore } from '@/store/dict';
import AppContextMenu from '@/components/AppContextMenu.vue';
import router from '@/router';

/**
 * 根组件：应用启动时预加载枚举字典（后端统一定义枚举值），
 * 供全局 DictTag / DictSelect / dictLabel 解析中文语义。
 */
const dictStore = useDictStore();
const renderError = ref(false);

onErrorCaptured((error, instance, info) => {
  console.error('[loan-web] 页面渲染异常', { error, info, component: instance?.$?.type?.name });
  renderError.value = true;
  return false;
});

function reloadPage() { window.location.reload(); }
function goWorkbench() {
  renderError.value = false;
  router.replace('/workbench').catch(() => {});
}

onMounted(() => {
  dictStore.load();
});
</script>

<style scoped>
.app-runtime-error { min-height: 100vh; display: grid; place-items: center; padding: 24px; background: var(--loan-bg, #f5f7fb); }
.app-runtime-error__card { width: min(460px, 100%); padding: 32px; border: 1px solid var(--loan-border, #e5e7eb); border-radius: 16px; background: var(--loan-surface, #fff); box-shadow: 0 12px 36px rgba(15, 23, 42, .08); text-align: center; }
.app-runtime-error__icon { width: 42px; height: 42px; margin: 0 auto 14px; border-radius: 50%; display: grid; place-items: center; color: #fff; background: var(--loan-danger, #e5484d); font-weight: 700; font-size: 24px; }
.app-runtime-error h2 { margin: 0 0 10px; color: var(--loan-text, #1f2937); }
.app-runtime-error p { margin: 0; color: var(--loan-text-secondary, #64748b); line-height: 1.7; }
.app-runtime-error__actions { margin-top: 22px; display: flex; justify-content: center; gap: 10px; }
</style>
