import { createRouter, createWebHistory } from 'vue-router';
import { KEYS, getStorage } from '@/utils/storage';
import { hasPermission } from '@/directives/permission';
import { useUserStore } from '@/store/user';
import { canAccessRoute } from '@/utils/routeAccess';

/**
 * 路由表（阶段一最小闭环）。
 * 布局路由挂 Layout，子菜单按模块分包；正式接入登录后按角色拉菜单树动态生成（见纪要第 12 章）。
 */
const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录', public: true },
  },
  {
    path: '/',
    component: () => import('@/layout/Layout.vue'),
    redirect: '/workbench',
    children: [
      {
        path: 'workbench',
        name: 'Workbench',
        component: () => import('@/views/Workbench.vue'),
        meta: { title: '我的工作台' },
      },
      {
        path: 'product',
        name: 'ProductList',
        component: () => import('@/views/product/ProductList.vue'),
        meta: { title: '产品库' },
      },
      {
        path: 'rule',
        name: 'RuleList',
        component: () => import('@/views/rule/RuleList.vue'),
        meta: { title: '规则集' },
      },
      {
        path: 'rule-template',
        name: 'RuleTemplateList',
        component: () => import('@/views/rule/RuleTemplateList.vue'),
        meta: { title: '规则库' },
      },
      {
        path: 'channel-config',
        name: 'ChannelConfigList',
        component: () => import('@/views/plan/ChannelConfigList.vue'),
        meta: { title: '渠道档案' },
      },
      {
        path: 'channel-config-wizard',
        name: 'ChannelConfigWizard',
        component: () => import('@/views/plan/ChannelConfigWizard.vue'),
        meta: { title: '渠道配置向导' },
      },
      {
        path: 'channel-strategy',
        name: 'StrategyList',
        component: () => import('@/views/plan/StrategyList.vue'),
        meta: { title: '渠道准入' },
      },
      {
        path: 'channel-user-list',
        name: 'ChannelUserList',
        component: () => import('@/views/plan/ChannelUserList.vue'),
        meta: { title: '渠道名单' },
      },
      {
        path: 'plan-edit',
        name: 'PlanEdit',
        component: () => import('@/views/plan/PlanEdit.vue'),
        meta: { title: '执行计划' },
      },
      {
        path: 'strategy-template',
        name: 'StrategyTemplateList',
        component: () => import('@/views/plan/StrategyTemplateList.vue'),
        meta: { title: '策略方案' },
      },
      {
        path: 'debug',
        name: 'DebugCenter',
        component: () => import('@/views/debug/DebugCenter.vue'),
        meta: { title: '调试中心', permission: 'page:debug' },
      },
      {
        path: 'audit',
        name: 'AuditCenter',
        component: () => import('@/views/audit/AuditCenter.vue'),
        meta: { title: '审计日志', permission: 'page:audit' },
      },
      {
        path: 'org',
        name: 'OrgCenter',
        component: () => import('@/views/org/OrgCenter.vue'),
        meta: { title: '组织权限', permission: 'page:org' },
      },
      {
        path: 'lead',
        name: 'LeadPool',
        component: () => import('@/views/lead/LeadPool.vue'),
        meta: { title: '线索公海' },
      },
      {
        path: 'client',
        name: 'ClientProfile',
        component: () => import('@/views/client/ClientProfile.vue'),
        meta: { title: '客户档案', permission: 'page:client' },
      },
      {
        path: 'ocr',
        name: 'OcrCenter',
        component: () => import('@/views/ocr/OcrCenter.vue'),
        meta: { title: '材料识别' },
      },
      {
        path: 'order',
        name: 'OrderList',
        component: () => import('@/views/order/OrderList.vue'),
        meta: { title: '服务工单' },
      },
      {
        path: 'reward',
        name: 'RewardList',
        component: () => import('@/views/reward/RewardList.vue'),
        meta: { title: '奖励发放' },
      },
      {
        path: 'reward-rule',
        name: 'RewardRuleConfig',
        component: () => import('@/views/reward/RewardRuleConfig.vue'),
        meta: { title: '奖励规则' },
      },
      {
        path: 'approval',
        name: 'ApprovalCenter',
        component: () => import('@/views/approval/ApprovalCenter.vue'),
        meta: { title: '审批中心' },
      },
      {
        path: 'sms',
        name: 'SmsCenter',
        component: () => import('@/views/sms/SmsCenter.vue'),
        meta: { title: '短信服务' },
      },
      {
        path: 'report/overview',
        name: 'ReportOverview',
        component: () => import('@/views/report/Overview.vue'),
        meta: { title: '数据概览' },
      },
      {
        path: 'report/screening',
        name: 'ReportScreening',
        component: () => import('@/views/report/ScreeningReport.vue'),
        meta: { title: '初筛报告' },
      },
      {
        path: 'report/trend',
        name: 'ReportTrend',
        component: () => import('@/views/report/TrendAnalysis.vue'),
        meta: { title: '趋势分析' },
      },
      {
        path: 'report/center',
        name: 'ReportCenter',
        component: () => import('@/views/report/ReportCenter.vue'),
        meta: { title: '经营概览' },
      },
      {
        path: 'report',
        redirect: '/report/center',
      },
      {
        path: 'screening',
        name: 'ScreeningCenter',
        component: () => import('@/views/screening/ScreeningCenter.vue'),
        meta: { title: '初筛任务' },
      },
      {
        path: 'report-template',
        name: 'ReportTemplateList',
        component: () => import('@/views/template/ReportTemplateList.vue'),
        meta: { title: '报告模板' },
      },
      {
        path: 'config-wizard',
        name: 'ConfigurationWizard',
        component: () => import('@/views/config/ConfigurationWizard.vue'),
        meta: { title: '系统配置' },
      },
      {
        path: 'blacklist',
        name: 'BlacklistCenter',
        component: () => import('@/views/blacklist/BlacklistCenter.vue'),
        meta: { title: '风控名单', permission: 'page:blacklist' },
      },
      // 无权限页（Layout 子路由：保留侧栏/标签栏不丢失上下文，T8；未登录访问仍被守卫拦到登录页）
      {
        path: '403',
        name: 'Forbidden',
        component: () => import('@/views/error/ErrorPage.vue'),
        props: {
          code: '403',
          title: '没有访问权限',
          desc: '当前角色无权访问该页面，如需开通请联系管理员配置角色权限。',
          tone: 'warning',
        },
        meta: { title: '无权限' },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/ErrorPage.vue'),
    props: {
      code: '404',
      title: '页面不存在',
      desc: '您访问的页面可能已被移除、改名，或暂时不可用。',
      showBack: true,
      tone: 'info',
    },
    meta: { title: '页面不存在', public: true },
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

/**
 * 全局前置守卫：
 * 1. 未登录访问受保护页面 → 跳登录
 * 2. 已登录访问登录页 → 回工作台
 * 3. 路由声明了 meta.permission 且当前用户无该权限 → 跳 403
 */
router.beforeEach(async (to) => {
  const token = getStorage(KEYS.TOKEN);
  // 未登录且目标页非公开页 → 跳登录
  if (!token && !to.meta.public) {
    return { path: '/login', query: { redirect: to.fullPath } };
  }
  // 已登录访问登录页 → 回工作台
  if (token && to.path === '/login') {
    return { path: '/workbench' };
  }
  // 动态菜单是页面准入真值：无菜单即使手输 URL 也不可加载页面组件和首屏接口。
  if (token && !to.meta.public && to.path !== '/403') {
    try {
      const store = useUserStore();
      const menuPaths = await store.ensureMenuPaths();
      if (!canAccessRoute(to.path, menuPaths, {
        debugCenterEnabled: import.meta.env.VITE_DEBUG_CENTER === 'true',
      })) {
        return { path: '/403', query: { from: to.fullPath } };
      }
    } catch (e) {
      return { path: '/403', query: { from: to.fullPath } };
    }
  }
  // 权限校验（checker 已在 main.js 注入，未配置权限时放行）
  if (to.meta.permission && !hasPermission(to.meta.permission)) {
    return { path: '/403' };
  }
  return true;
});

export default router;
