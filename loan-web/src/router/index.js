import { createRouter, createWebHistory } from 'vue-router';

/**
 * 路由表（骨架）。
 * 正式开发时：登录后按角色拉菜单树动态生成侧栏与路由（见纪要第 12 章 / 前端交互蓝图）。
 */
const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录' },
  },
  {
    path: '/',
    name: 'Workbench',
    component: () => import('@/views/Workbench.vue'),
    meta: { title: '我的工作台' },
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

export default router;
