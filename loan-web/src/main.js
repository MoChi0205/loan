import { createApp } from 'vue';
import { createPinia } from 'pinia';
import App from './App.vue';
import router from './router';
import { setAppRouter } from './utils/request';
import { applyTheme } from './theme';
import permission, { setPermissionChecker } from './directives/permission';
import { useUserStore } from './store/user';
import AppEmpty from './components/AppEmpty.vue';
import AppSkeleton from './components/AppSkeleton.vue';
import './styles/index.css';

/**
 * 应用入口：挂载 Vue + Element Plus + Pinia + 路由。
 * 挂载前先从环境配置注入主题变量（全局统一换肤）。
 */
applyTheme();

const app = createApp(App);
const pinia = createPinia();

app.use(pinia);
app.use(router);
// 将路由实例注入请求层，消除 request.js 对 @/router 的动态导入（避免 Vite 拆包告警）。
setAppRouter(router);

// 权限 checker 接线：v-permission 指令与路由守卫（meta.permission）共用同一判定。
// 登录下发权限码后按码校验；页面是否可进入独立由后端动态菜单决定。
setPermissionChecker((code) => {
  try {
    return useUserStore(pinia).hasPerm(code);
  } catch (e) {
    return true;
  }
});

// 全局自定义指令
app.directive('permission', permission);

// 全局组件：空状态插画 + 骨架屏（列表页直接 <AppEmpty> / <AppSkeleton> 使用）
app.component('AppEmpty', AppEmpty);
app.component('AppSkeleton', AppSkeleton);

app.mount('#app');
