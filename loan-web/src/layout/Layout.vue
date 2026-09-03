<template>
  <div class="layout">
    <!-- 无障碍:跳过重复导航,直接到主内容 -->
    <a href="#main-content" class="skip-link">跳到主内容</a>
    <!-- 左侧导航 -->
    <aside class="sider" :class="{ collapsed }">
      <div class="brand">
        <div class="brand-mark">
          <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.8">
            <path d="M12 2L3 7v10l9 5 9-5V7l-9-5z" />
            <path d="M12 12v10" />
            <path d="M3 7l9 5 9-5" />
          </svg>
        </div>
        <div v-if="!collapsed" class="brand-text">
          <div class="brand-name">企业贷款咨询</div>
          <div class="brand-sub">Loan Advisory</div>
        </div>
      </div>

      <nav class="menu" aria-label="主导航">
        <div v-if="noMenuPermission" class="menu-no-perm">
          当前角色暂无菜单权限，如需开通请联系管理员配置角色权限。
        </div>
        <template v-for="g in menuGroups" :key="g.title || 'workbench'">
          <!-- 工作台：永远显示且不参与分组折叠 -->
          <template v-if="!g.title">
            <el-tooltip
              v-for="item in g.items"
              :key="item.path"
              :content="item.title"
              placement="right"
              :disabled="!collapsed"
            >
              <router-link
                :to="item.path"
                class="menu-item workbench-item"
                :class="{ active: isActive(item.path) }"
              >
                <span class="menu-icon"><AppIcon :name="item.icon" :size="18" /></span>
                <span v-if="!collapsed" class="menu-text">{{ item.title }}</span>
              </router-link>
            </el-tooltip>
          </template>
          <!-- 分组主菜单：可点击折叠/展开 -->
          <template v-else>
            <div
              v-if="!collapsed"
              class="menu-group"
              :class="{ expanded: isGroupExpanded(g.title) }"
            >
              <div
                class="menu-group-title"
                role="button"
                tabindex="0"
                :aria-expanded="isGroupExpanded(g.title) ? 'true' : 'false'"
                :class="{ active: isGroupActive(g) }"
                :title="`点击${isGroupExpanded(g.title) ? '折叠' : '展开'}「${g.title}」`"
                @click="toggleGroup(g.title)"
                @keyup.enter="toggleGroup(g.title)"
                @keyup.space.prevent="toggleGroup(g.title)"
              >
                <span v-if="g.icon" class="menu-group-icon"><AppIcon :name="g.icon" :size="16" /></span>
                <span class="menu-group-label">{{ g.title }}</span>
                <span class="menu-group-line"></span>
                <svg
                  class="menu-group-arrow"
                  viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2"
                >
                  <path d="M6 9l6 6 6-6" />
                </svg>
              </div>
              <transition name="menu-collapse">
                <div
                  v-show="isGroupExpanded(g.title)"
                  class="menu-group-items"
                >
                  <template v-for="item in g.items" :key="item.path || item.title">
                    <!-- 分区标题（非点击项） -->
                    <div v-if="item.isSection" class="menu-section-header">
                      <span v-if="item.icon" class="menu-section-icon"><AppIcon :name="item.icon" :size="14" /></span>
                      <span class="menu-section-label">{{ item.title }}</span>
                    </div>
                    <!-- 普通菜单项 -->
                    <router-link
                      v-else
                      :to="item.path"
                      class="menu-item"
                      :class="{ active: isActive(item.path), 'menu-item--indented': item.indent }"
                      :title="item.title"
                    >
                      <span class="menu-icon"><AppIcon :name="item.icon" :size="18" /></span>
                      <span v-if="!collapsed" class="menu-text">{{ item.title }}</span>
                    </router-link>
                  </template>
                </div>
              </transition>
            </div>
            <!-- 侧栏整体折叠（64px）：分组只显示子项图标堆叠（跳过分区标题） -->
            <template v-if="collapsed">
              <div class="menu-group-collapsed">
                <el-tooltip
                  v-for="item in g.items.filter(i => i.path)"
                  :key="item.path"
                  :content="item.title"
                  placement="right"
                >
                  <router-link
                    :to="item.path"
                    class="menu-item"
                    :class="{ active: isActive(item.path) }"
                  >
                    <span class="menu-icon"><AppIcon :name="item.icon" :size="18" /></span>
                  </router-link>
                </el-tooltip>
              </div>
            </template>
          </template>
        </template>
      </nav>

      <div
        class="sider-foot"
        role="button"
        tabindex="0"
        :aria-expanded="collapsed ? 'false' : 'true'"
        aria-label="折叠/展开导航"
        @click="collapsed = !collapsed"
        @keyup.enter="collapsed = !collapsed"
        @keyup.space.prevent="collapsed = !collapsed"
      >
        <svg
          viewBox="0 0 24 24"
          width="16"
          height="16"
          fill="none"
          stroke="currentColor"
          stroke-width="1.8"
          class="collapse-icon"
          :class="{ rotated: collapsed }"
        >
          <path d="M15 18l-6-6 6-6" />
        </svg>
        <span v-if="!collapsed">收起导航</span>
      </div>
    </aside>

    <!-- 右侧主体 -->
    <div class="main">
      <header class="topbar">
        <div class="topbar-left">
          <button class="collapse-btn" type="button" aria-label="折叠/展开导航" @click="collapsed = !collapsed">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8">
              <path d="M4 6h16M4 12h16M4 18h16" />
            </svg>
          </button>
          <el-breadcrumb separator="/" aria-label="面包屑导航">
            <el-breadcrumb-item>企业贷款咨询</el-breadcrumb-item>
            <el-breadcrumb-item>{{ currentTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="topbar-right">
          <ThemeSwitch />
          <el-dropdown trigger="click" @command="onUserCommand">
          <button class="user" type="button" aria-label="用户菜单">
            <span class="user-avatar">{{ avatarText }}</span>
            <span class="user-info">
              <span class="user-name">{{ displayName }}</span>
              <span class="user-role">{{ roleText }}</span>
            </span>
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8">
              <path d="M6 9l6 6 6-6" />
            </svg>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">
                <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" style="margin-right: 6px; vertical-align: -2px">
                  <path d="M9 21H5a2 2 0 01-2-2V5a2 2 0 012-2h4" />
                  <path d="M16 17l5-5-5-5" />
                  <path d="M21 12H9" />
                </svg>
                退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
          </el-dropdown>
        </div>
      </header>

      <main id="main-content" class="content">
        <!-- 多标签栏：可切换 / 关闭 / 刷新（多主菜单独立保留） -->
        <div v-if="openTabs.length" class="tabs-bar">
          <el-tabs
            v-model="activeTab"
            type="card"
            :closable="openTabs.length > 1"
            @tab-click="onTabClick"
            @tab-remove="onTabRemove"
          >
            <el-tab-pane
              v-for="t in openTabs"
              :key="t.path"
              :name="t.path"
              :closable="false"
            >
              <template #label>
                <span class="tab-label" @contextmenu.prevent="onTabContextMenu($event, t)">
                  <span class="tab-label__text" :title="t.title">{{ t.title }}</span>
                  <svg
                    v-if="t.path !== '/workbench' && openTabs.length > 1"
                    class="tab-close"
                    viewBox="0 0 24 24"
                    width="12"
                    height="12"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="2"
                    @click.stop="onTabClose(t.path)"
                    aria-label="关闭标签"
                  >
                    <path d="M6 6l12 12M6 18L18 6" />
                  </svg>
                </span>
              </template>
            </el-tab-pane>
          </el-tabs>
          <el-tooltip content="刷新当前页" placement="bottom">
            <button class="tabs-refresh" @click="onRefresh" aria-label="刷新">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M21 12a9 9 0 11-2.6-6.4"/><path d="M21 3v6h-6"/></svg>
            </button>
          </el-tooltip>
        </div>
        <router-view v-slot="{ Component, route: r }">
          <transition name="page-fade" mode="out-in">
            <keep-alive :include="cachedViews">
              <component :is="Component" :key="r.fullPath" />
            </keep-alive>
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, nextTick } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useUserStore } from '@/store/user';
import { menuTree } from '@/api/org';
import { openContextMenu } from '@/utils/contextMenu';
import { findMenuItem } from '@/utils/menu';
import { KEYS, getStorageJSON, setStorage, setStorageJSON } from '@/utils/storage';
import ThemeSwitch from '@/components/ThemeSwitch.vue';
import AppIcon from '@/components/AppIcon.vue';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const collapsed = ref(false);

/** 用户显示名 */
const displayName = computed(() => userStore.displayName);
/** 角色显示名（含 OPERATOR / SUPER_ADMIN / SUPER 三角色建模） */
const roleText = computed(() => {
  const m = {
    BOSS: '老板',
    DEPT_MANAGER: '部门主管',
    ADVISER: '顾问',
    OPERATOR: '运营',
    SUPER_ADMIN: '超管',
    SUPER: '超级管理员',
    CHANNEL: '合作渠道管理员',
  };
  return m[userStore.roleCode] || userStore.roleCode || '管理员';
});
/** 头像首字 */
const avatarText = computed(() => (displayName.value ? displayName.value.slice(0, 1) : '管'));

/** 侧栏菜单全集（2026-09-01 按 D19 新 IA：业务链路排序 7 组；工作台常驻）。动态菜单基于此全集按角色后端树过滤。 */
const BASE_MENU_GROUPS = [
  {
    title: '',
    items: [
      { path: '/workbench', title: '工作台', icon: 'workbench' },
    ],
  },
  {
    title: '客户经营',
    short: '客户',
    icon: 'client',
    items: [
      { path: '/lead', title: '线索公海', icon: 'lead' },
      { path: '/client', title: '客户档案', icon: 'client' },
      { path: '/ocr', title: '材料识别', icon: 'ocr' },
    ],
  },
  {
    title: '匹配与规则',
    short: '规则',
    icon: 'strategy',
    items: [
      { path: '/screening', title: '初筛任务', icon: 'screening' },
      { path: '/rule-template', title: '规则库', icon: 'ruleTemplate' },
      { path: '/plan-edit', title: '执行计划', icon: 'plan' },
      { path: '/strategy-template', title: '策略方案', icon: 'strategy' },
      { path: '/rule', title: '规则集', icon: 'rule' },
    ],
  },
  {
    title: '服务与审批',
    short: '服务',
    icon: 'order',
    items: [
      { path: '/order', title: '服务工单', icon: 'order' },
      { path: '/approval', title: '审批中心', icon: 'approval' },
    ],
  },
  {
    title: '运营与激励',
    short: '运营',
    icon: 'reward',
    items: [
      { path: '/sms', title: '短信服务', icon: 'sms' },
      { path: '/reward', title: '奖励发放', icon: 'reward' },
      { path: '/reward-rule', title: '奖励规则', icon: 'reward' },
      { path: '/audit', title: '审计日志', icon: 'audit' },
    ],
  },
  {
    title: '数据与报表',
    short: '报表',
    icon: 'report',
    items: [
      { path: '/report/center', title: '经营概览', icon: 'report' },
      { path: '/report/trend', title: '趋势分析', icon: 'trend' },
      { path: '/report/screening', title: '初筛报告', icon: 'reportDoc' },
      { path: '/report-template', title: '报告模板', icon: 'reportDoc' },
    ],
  },
  {
    title: '产品与渠道',
    short: '产品',
    icon: 'product',
    items: [
      { path: '/product', title: '产品库', icon: 'product' },
      { path: '/channel-config', title: '渠道档案', icon: 'channel' },
      { path: '/channel-strategy', title: '渠道准入', icon: 'strategy' },
      { path: '/channel-user-list', title: '渠道名单', icon: 'ban' },
      { path: '/blacklist', title: '风控名单', icon: 'ban' },
    ],
  },
  {
    title: '系统管理',
    short: '系统',
    icon: 'config',
    items: [
      { path: '/org', title: '组织权限', icon: 'org' },
      { path: '/config-wizard', title: '系统配置', icon: 'config' },
      { path: '/debug', title: '调试中心', icon: 'debug' },
    ],
  },
];

/**
 * 角色可见菜单（按后端 org/menu/tree 动态过滤）。
 * - allowedCodes 为 null：树尚未加载 → 仅展示工作台，权限未知时禁止 fail-open
 * - allowedCodes 为空 Set：成功加载但角色无任何菜单 → 仅工作台 + 提示（T12 收口，防异常角色泄露全量）
 * - allowedCodes 为非空集合：仅保留 path 命中集合的菜单项（按角色动态菜单）
 */
const allowedCodes = ref(null);
/** 成功加载但该角色无任何菜单权限（T12） */
const noMenuPermission = computed(() => allowedCodes.value != null && allowedCodes.value.size === 0);
/** 调试中心前端开关（T17/D30）：与后端 loan.debug-center.enabled 配套，生产 build 隐藏 /debug 菜单项 */
const DEBUG_CENTER_VISIBLE = import.meta.env.VITE_DEBUG_CENTER === 'true';
/** 剔除调试中心的菜单全集（菜单渲染 / 空树回退 / 加载失败回退共用，避免生产环境泄漏入口） */
const SAFE_MENU_GROUPS = DEBUG_CENTER_VISIBLE
  ? BASE_MENU_GROUPS
  : BASE_MENU_GROUPS
      .map((g) => ({ ...g, items: g.items.filter((it) => !it.path || it.path.split('?')[0] !== '/debug') }))
      .filter((g) => g.items.length);
const menuGroups = computed(() => {
  const raw = userStore.roleCode === 'CHANNEL'
    ? SAFE_MENU_GROUPS.map((group) => ({
        ...group,
        items: group.items.map((item) => ({
          ...item,
          title: item.path === '/lead' ? '我的线索'
            : item.path === '/client' ? '我的客户'
              : item.path === '/report/screening' ? '客户分析报告' : item.title,
        })),
      }))
    : SAFE_MENU_GROUPS;
  if (allowedCodes.value == null) return [SAFE_MENU_GROUPS[0]];
  if (allowedCodes.value.size === 0) return [SAFE_MENU_GROUPS[0]]; // 仅工作台
  const set = allowedCodes.value;
  const filtered = raw
    .map((g) => ({ ...g, items: g.items.filter((it) => !it.path || set.has(it.path.split('?')[0])) }))
    .filter((g) => g.items.length);
  return filtered.length ? filtered : [SAFE_MENU_GROUPS[0]];
});
/** 扁平菜单（标题查找用；跳过分区标题） */
const menus = computed(() => menuGroups.value.flatMap((g) => g.items.filter((i) => i.path)));

/** 拉取当前角色的后端菜单树，构建可见 path 集合（去查询串）。 */
async function loadRoleMenu() {
  try {
    const roleCode = userStore.roleCode;
    if (!roleCode) {
      allowedCodes.value = null;
      return;
    }
    const res = await menuTree(roleCode);
    const nodes = res?.data || [];
    const set = new Set();
    const walk = (list) => {
      (list || []).forEach((n) => {
        if (n.code) set.add(String(n.code).split('?')[0]);
        if (n.children && n.children.length) walk(n.children);
      });
    };
    walk(nodes);
    // 成功加载：空 Set = 该角色无任何菜单（区别于 null=加载失败回退全集，T12）
    allowedCodes.value = set;
    // 按当前角色菜单过滤残留 tab（降权/角色切换后清理无权限 tab，T8）
    if (set.size) {
      const before = openTabs.value.length;
      openTabs.value = openTabs.value.filter((t) => {
        if (t.path === '/workbench') return true;
        return set.has(String(t.path).split('?')[0]);
      });
      if (openTabs.value.length !== before) saveTabs();
      if (!openTabs.value.some((t) => t.path === route.fullPath)) {
        const fallback = openTabs.value[openTabs.value.length - 1] || { path: '/workbench', title: '工作台' };
        activeTab.value = fallback.path;
      }
    }
  } catch (e) {
    // 权限数据不可用时 fail-closed：仅保留工作台，禁止展示未经授权的业务入口。
    allowedCodes.value = new Set();
  }
}

/** 当前菜单标题（动态面包屑：优先 route.meta.title，支持子页面如 /report/overview） */
const currentTitle = computed(() => {
  if (route.meta?.title) return route.meta.title;
  const list = Array.isArray(menus.value) ? menus.value : [];
  const m = list.find((item) => item?.path === route.path);
  return m ? m.title : '企业贷款咨询服务';
});

/** 菜单激活态（前缀匹配，支持子页面；带 ?cg= 的菜单项按 fullPath 精确命中或 path 兜底，T1 修复） */
function isActive(path) {
  if (!path) return false;
  const target = path.split('?')[0];
  // 带 query 的菜单项（如 /plan-edit?cg=ENTERPRISE）：fullPath 精确命中；或当前页为该 path（未带 cg 参数访问时也高亮）
  if (path.includes('?')) {
    return route.fullPath === path || route.path === target;
  }
  return route.path === path || route.path.startsWith(path + '/');
}

/** 分组激活态：当前路由命中该分组任一菜单项 */
function isGroupActive(g) {
  return g.items.some((item) => isActive(item.path));
}

/** 用户菜单命令 */
function onUserCommand(command) {
  if (command === 'logout') {
    userStore.doLogout().then(() => {
      // 退出登录时清空标签栏
      openTabs.value = [{ path: '/workbench', title: '工作台' }];
      setStorageJSON(KEYS.LAYOUT_TABS, []);
      router.push('/login');
    });
  }
}

// ============================================================
// 多标签栏：主菜单点击/路由切换 → 自动开 tab；可切换/关闭/刷新
// ============================================================
// 多标签栏 + 分组折叠状态：统一走 storage 封装持久化
const activeTab = ref(route.fullPath);
const openTabs = ref([{ path: '/workbench', title: '工作台' }]);
/** 已缓存页面（用于 keep-alive；name 与组件 defineOptions 对齐：去 query 后 path 的 / 与 - 转 _） */
const cachedViews = ref(openTabs.value.map((t) => t.path.split('?')[0].replace(/[/-]/g, '_')));
watch(openTabs, (tabs) => {
  cachedViews.value = tabs.map((t) => t.path.split('?')[0].replace(/[/-]/g, '_'));
}, { deep: true });
/** 主菜单分组折叠状态：title -> 是否展开（默认全展开） */
const groupExpanded = ref({});

function isGroupExpanded(title) {
  return groupExpanded.value[title] !== false;
}

function toggleGroup(title) {
  groupExpanded.value[title] = !isGroupExpanded(title);
  setStorageJSON(KEYS.LAYOUT_GROUP, groupExpanded.value);
}

function loadTabs() {
  const arr = getStorageJSON(KEYS.LAYOUT_TABS, null);
  if (Array.isArray(arr) && arr.length) {
    openTabs.value = arr;
  }
  // 加载分组折叠状态
  const obj = getStorageJSON(KEYS.LAYOUT_GROUP, null);
  if (obj && typeof obj === 'object') groupExpanded.value = obj;
}
function saveTabs() {
  setStorageJSON(KEYS.LAYOUT_TABS, openTabs.value);
}

/** 用菜单或当前 route 找到标题（支持带 query 的菜单项，T7） */
function findTitle(path) {
  const targetPath = String(path || '');
  const m = findMenuItem(menus.value, targetPath);
  if (m) return m.title;
  // route meta.title
  const r = router.resolve(path);
  return (r.meta && r.meta.title) || String(path).replace(/^\//, '').replace(/^./, (c) => c.toUpperCase());
}

function ensureTab(path) {
  if (!openTabs.value.find((t) => t.path === path)) {
    openTabs.value.push({ path, title: findTitle(path) });
    saveTabs();
  }
}

/** 点击 tab 切换路由（tab 键 = fullPath，支持 ?cg= 区分，T7） */
function onTabClick(pane) {
  const path = pane?.props?.name || pane?.name;
  if (path && path !== route.fullPath) {
    router.push(path);
  }
}
/** el-tabs tab-remove 事件 */
function onTabRemove(name) {
  onTabClose(name);
}
/** 自定义关闭按钮 */
function onTabClose(path) {
  if (path === '/workbench') return; // 工作台常驻
  const idx = openTabs.value.findIndex((t) => t.path === path);
  if (idx < 0) return;
  openTabs.value.splice(idx, 1);
  saveTabs();
  // 若关闭的是当前 tab，跳到相邻 tab
  if (route.fullPath === path) {
    const next = openTabs.value[Math.min(idx, openTabs.value.length - 1)];
    router.push(next ? next.path : '/workbench');
  }
}
/** 刷新当前 tab：从 keep-alive include 临时剔除当前页 name → nextTick 恢复，强制组件销毁重建（不依赖未注册路由） */
function onRefresh() {
  const curName = route.fullPath.split('?')[0].replace(/[/-]/g, '_');
  cachedViews.value = cachedViews.value.filter((n) => n !== curName);
  nextTick(() => {
    cachedViews.value = openTabs.value.map((t) => t.path.split('?')[0].replace(/[/-]/g, '_'));
  });
}
/** 右键 tab 标签：弹出全局下拉菜单（刷新 / 关闭当前 / 关闭其他 / 全部关闭） */
function onTabContextMenu(ev, t) {
  openContextMenu(ev, [
    { label: '刷新页面', icon: 'refresh', onClick: () => {
      if (t.path === route.fullPath) onRefresh();
      else router.push(t.path).then(() => onRefresh());
    } },
    { label: '关闭当前', icon: 'close', disabled: t.path === '/workbench', onClick: () => onTabClose(t.path) },
    { label: '关闭其他', icon: 'layers', disabled: openTabs.value.length <= 1, onClick: () => {
      openTabs.value = openTabs.value.filter((x) => x.path === t.path || x.path === '/workbench');
      saveTabs();
    } },
    { label: '全部关闭', icon: 'delete', danger: true, disabled: openTabs.value.length <= 1, onClick: () => {
      openTabs.value = openTabs.value.filter((x) => x.path === '/workbench');
      if (route.path !== '/workbench') router.push('/workbench');
      saveTabs();
    } },
  ]);
}

/** 监听路由变化：自动加入 tab 并切到 active（键 = fullPath，T7） */
watch(
  () => route.fullPath,
  (path) => {
    ensureTab(path);
    activeTab.value = path;
  },
);

/** 菜单点击 → 路由跳转（router-link 自动 push） */
function onMenuClick(ev, item) {
  // router-link 自身已处理；这里只做主动聚焦效果
  if (ev) ev.preventDefault();
  router.push(item.path);
}

onMounted(() => {
  loadTabs();
  ensureTab(route.fullPath);
  activeTab.value = route.fullPath;
  // 按当前角色加载后端菜单树，驱动动态菜单
  loadRoleMenu();
});

// 角色切换（如登录态刷新）后重新拉取菜单树
watch(() => userStore.roleCode, () => loadRoleMenu());
</script>

<style scoped>
/* 页面切换过渡：淡入 + 轻微上移（keep-alive 缓存页切换同样生效） */
.page-fade-enter-active,
.page-fade-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}
.page-fade-enter-from {
  opacity: 0;
  transform: translateY(6px);
}
.page-fade-leave-to {
  opacity: 0;
}
.layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
  background: var(--loan-bg);
}

/* 侧栏（深蓝金融暗色 / 浅色商务 双主题） */
.sider {
  width: 210px;                              /* 收窄 220→210，给主内容更多空间 */
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  /* 极淡渐变背景，纵深感（替代纯灰平铺） */
  background: linear-gradient(180deg,
    color-mix(in srgb, var(--loan-sider-bg) 96%, var(--loan-primary) 4%) 0%,
    var(--loan-sider-bg) 60%);
  border-right: 1px solid var(--loan-border);
  transition: width var(--loan-transition), background var(--loan-transition);
  overflow: hidden;
  position: relative;
}
.sider::before {                              /* 右侧 1px 渐变细线（更精致） */
  content: '';
  position: absolute;
  right: 0;
  top: 8%;
  bottom: 8%;
  width: 1px;
  background: linear-gradient(180deg,
    transparent 0%, var(--loan-border) 30%, var(--loan-border) 70%, transparent 100%);
  pointer-events: none;
}

.sider.collapsed {
  width: 64px;
  background: var(--loan-sider-bg);          /* 折叠态用纯色（窄条背景不需要纵深） */
}
.sider.collapsed::before { display: none; }

/* 品牌区 */
.brand {
  height: 60px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 14px;
  border-bottom: 1px solid var(--loan-border);
  overflow: hidden;
  flex-shrink: 0;
}

.sider.collapsed .brand {
  justify-content: center;
  padding: 0 10px;
}

.brand-mark {
  width: 34px;
  height: 34px;
  border-radius: var(--loan-radius-sm);
  background: var(--loan-gradient);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 2px 8px rgba(59, 130, 246, 0.35);
}

.brand-name {
  color: var(--loan-text);
  font-size: 14px;
  font-weight: 700;
  white-space: nowrap;
  letter-spacing: 0.3px;
}

.brand-sub {
  color: var(--loan-text-muted);
  font-size: 10px;
  letter-spacing: 0.8px;
  white-space: nowrap;
  text-transform: uppercase;
}

/* 菜单滚动区 */
.menu {
  flex: 1;
  padding: 6px 0 12px;
  overflow-y: auto;
  overflow-x: hidden;
}
/* el-tooltip 包裹的菜单项:trigger 需要 flex 撑满行宽(折叠态 64px 也生效) */
.menu .el-tooltip__trigger,
.menu-group-collapsed .el-tooltip__trigger {
  display: flex;
  width: 100%;
}
.menu::-webkit-scrollbar {
  width: 4px;
}
.menu::-webkit-scrollbar-thumb {
  background: var(--loan-border-strong);
  border-radius: 4px;
}

/* 分组主菜单（可折叠） */
.menu-group {
  margin-top: 8px;             /* T14: 分组间距加大，与子项层级区分 */
}
.menu-group:first-of-type { margin-top: 4px; }

/* T12: 无菜单权限提示（空树收口后仅工作台 + 提示） */
.menu-no-perm {
  margin: 8px 10px 4px;
  padding: 10px 12px;
  border-radius: 8px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--loan-text-muted);
  background: color-mix(in srgb, var(--loan-warning-bg, #fffbe6) 60%, transparent);
  border: 1px dashed var(--loan-warning-line, #e6d4a8);
}

/* ============================================================
 * 分组标题：一级菜单风格（T14 与子项差异化：加粗 + 顶距 + 字距）
 * ============================================================ */
.menu-group-title {
  position: relative;          /* T14: 激活竖条定位基准（原缺失导致竖条渲染失败） */
  display: flex;
  align-items: center;
  gap: 11px;                   /* 与 .menu-item 的 gap 一致 */
  padding: 10px 12px 10px 14px;/* 与 .menu-item 的 padding 一致 */
  white-space: nowrap;
  overflow: visible;           /* T14: 不再裁剪激活竖条（原 overflow:hidden 裁掉 ::before） */
  cursor: pointer;
  user-select: none;
  border-radius: 8px;
  margin: 2px 10px;
  min-height: 44px;            /* 触摸目标 44px(WCAG 2.5.8) */
  color: var(--loan-sider-text);
  font-size: 13.5px;
  font-weight: 600;            /* T14: 分组标题加粗，与子项(400)拉开层级 */
  letter-spacing: 0.4px;       /* T14: 标题字距略宽，强化层级 */
  transition: background var(--loan-transition), color var(--loan-transition);
}
/* 分组标题 hover：与菜单项一致的反馈 */
.menu-group-title:hover {
  background: color-mix(in srgb, var(--loan-primary) 8%, transparent);
  color: var(--loan-text);
}
/* 分组标题激活态（当前页在该分组内）：与 .menu-item.active 一致的渐变 + 左侧竖条 */
.menu-group-title.active {
  background: linear-gradient(90deg,
    color-mix(in srgb, var(--loan-primary) 24%, transparent) 0%,
    color-mix(in srgb, var(--loan-primary) 10%, transparent) 100%);
  color: color-mix(in srgb, var(--loan-primary) 62%, #000);   /* T15: 浅色主题深蓝字(AA≥4.5:1)，原 #3b82f6 仅 2.8:1 */
  font-weight: 600;
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--loan-primary) 30%, transparent);
}
/* 深色主题:白字(现状,达标) */
:root[data-theme="dark"] .menu-group-title.active {
  color: #fff;
}
.menu-group-title.active::before {
  content: "";
  position: absolute;
  left: -4px;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 22px;
  border-radius: 0 3px 3px 0;
  background: var(--loan-primary);
  box-shadow: 0 0 10px var(--loan-primary);
}
/* 分组图标：与菜单项图标一致（18px stroke 线条式，无背景方块） */
.menu-group-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 18px;                 /* 与 .menu-icon 一致 */
  color: var(--loan-text-secondary);
  opacity: 0.85;
  transition: opacity var(--loan-transition), color var(--loan-transition), transform var(--loan-transition);
}
/* 尺寸由 AppIcon 组件内联控制（T13 集中 registry，替代原 scoped+v-html 失效的 !important 规则） */
.menu-group-title:hover .menu-group-icon {
  opacity: 1;
  color: var(--loan-primary);
  transform: translateX(2px);  /* 与 .menu-item:hover .menu-icon 一致的"动起来" */
}
.menu-group-title.active .menu-group-icon {
  color: var(--loan-primary);
  opacity: 1;
}
/* 分组标题文字：与「工作台」完全一致（不再大写/变小/变灰） */
.menu-group-label {
  font-size: 13.5px;
  font-weight: inherit;
  letter-spacing: 0.2px;
  color: inherit;              /* 继承 title 颜色（hover/active 自动跟随） */
  flex-shrink: 0;
}
/* 折叠指示：右侧箭头（展开时朝下，折叠时朝右） */
.menu-group-line { display: none; }  /* 去掉中间线段，让标题看起来就是普通菜单项 */
.menu-group-arrow {
  flex-shrink: 0;
  color: var(--loan-text-muted);
  transition: transform var(--loan-transition), color var(--loan-transition);
  opacity: 0.6;
  width: 14px;
  height: 14px;
}
.menu-group-title:hover .menu-group-arrow {
  opacity: 1;
  color: var(--loan-primary);
}
/* 折叠态：箭头从下(展开)旋转到右(折叠) */
.menu-group:not(.expanded) .menu-group-arrow { transform: rotate(-90deg); }

/* 折叠/展开过渡 */
.menu-collapse-enter-active,
.menu-collapse-leave-active {
  transition: opacity 0.18s var(--loan-ease), max-height 0.22s var(--loan-ease);
  overflow: hidden;
}
.menu-collapse-enter-from,
.menu-collapse-leave-to {
  opacity: 0;
  max-height: 0;
}
.menu-collapse-enter-to,
.menu-collapse-leave-from {
  opacity: 1;
  max-height: 600px;
}

/* 子菜单容器 */
.menu-group-items {
  display: flex;
  flex-direction: column;
  gap: 2px;                   /* 子项间 2px gap */
  padding: 2px 0;
}

/* 分区标题（企业贷 / 个贷 / 渠道与通用） */
.menu-section-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px 4px;
  font-size: 11.5px;
  font-weight: 700;
  letter-spacing: 0.5px;
  text-transform: uppercase;
  color: var(--loan-text-muted);
  cursor: default;
  user-select: none;
}
.menu-section-icon {
  display: inline-flex;
  align-items: center;
  color: var(--loan-primary);
  opacity: 0.7;
}
.menu-section-label {
  position: relative;
  padding-bottom: 2px;
}
/* 分区标题下划线装饰 */
.menu-section-label::after {
  content: '';
  position: absolute;
  left: 0;
  bottom: 0;
  width: 100%;
  height: 1.5px;
  background: linear-gradient(90deg, var(--loan-primary) 0%, transparent 80%);
  border-radius: 1px;
  opacity: 0.3;
}

/* 分组内的子菜单项:缩进 + 小图标,清晰区分层级(子项 vs 分组标题) */
.menu-group-items .menu-item {
  padding: 10px 12px 10px 32px;  /* 左 padding 32px(子菜单缩进) */
  margin: 1px 10px 1px 4px;       /* 左边距缩小到 4px 让缩进明显 */
  font-size: 13px;
  font-weight: 400;               /* 字体稍轻,区分分组标题 600 */
}
/* 二级缩进项（分区下的子项）：更深的缩进 */
.menu-group-items .menu-item--indented {
  padding-left: 42px;
}
.menu-group-items .menu-icon {
  width: 16px;
  opacity: 0.85;
}
/* 子菜单左侧连接线(树形视觉,不影响激活态 ::before) */
.menu-group-items .menu-item::after {
  content: "";
  position: absolute;
  left: 18px;
  top: 50%;
  transform: translateY(-50%);
  width: 6px;
  height: 1px;
  background: var(--loan-border, rgba(255,255,255,.12));
  pointer-events: none;
}
/* 激活态:连接线改为主色 + 加宽 */
.menu-group-items .menu-item.active::after {
  background: var(--loan-primary);
  width: 8px;
  box-shadow: 0 0 4px var(--loan-primary);
}
/* 侧栏整体折叠（64px）时：分组图标堆叠 */
.menu-group-collapsed {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin: 2px 8px;
}

/* 菜单项：卡片化 + 渐变 active + 左侧 3px 主色竖条 */
.menu-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 11px;
  margin: 1px 10px;          /* 留出 4px 给左侧竖条 */
  padding: 10px 12px 10px 14px;  /* 左 padding 大些，避开 active 竖条 */
  color: var(--loan-sider-text);
  text-decoration: none;
  font-size: 13.5px;
  font-weight: 500;
  border-radius: 8px;
  transition: background var(--loan-transition), color var(--loan-transition),
    transform var(--loan-transition);
  white-space: nowrap;
  letter-spacing: 0.2px;
  min-height: 44px;          /* 触摸目标 ≥ 44px(WCAG 2.5.8) */
}
/* 工作台：顶部分组，加点底部分隔留白 */
.menu-item.workbench-item {
  margin: 6px 10px 6px;
}

.menu-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  flex-shrink: 0;
  opacity: 0.85;
  color: var(--loan-text-secondary);
  transition: opacity var(--loan-transition), color var(--loan-transition), transform var(--loan-transition);
}
.menu-icon svg { width: 18px; height: 18px; }

/* hover：浅色背景 + 文字变主色 + 图标轻微右移（"动起来"反馈） */
.menu-item:hover {
  background: color-mix(in srgb, var(--loan-primary) 8%, transparent);
  color: var(--loan-text);
}
.menu-item:hover .menu-icon {
  opacity: 1;
  color: var(--loan-primary);
  transform: translateX(2px);
}

/* 激活项：主色渐变 + 白字 + 主色图标 + 左侧 3px 主色竖条（记忆点） */
.menu-item.active {
  background: linear-gradient(90deg,
    color-mix(in srgb, var(--loan-primary) 24%, transparent) 0%,
    color-mix(in srgb, var(--loan-primary) 10%, transparent) 100%);
  color: color-mix(in srgb, var(--loan-primary) 62%, #000);   /* T15: 浅色主题深蓝字(AA≥4.5:1)，原 #3b82f6 仅 2.8:1 */
  font-weight: 600;
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--loan-primary) 30%, transparent);
}
/* 深色主题:白字(现状,达标) */
:root[data-theme="dark"] .menu-item.active {
  color: #fff;
}
.menu-item.active .menu-icon {
  color: var(--loan-primary);
  opacity: 1;
}

/* 激活竖条（左侧 3px 主色发光条） */
.menu-item.active::before {
  content: "";
  position: absolute;
  left: -4px;                /* 越过 padding 显示在 menu 容器最左 */
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 22px;
  border-radius: 0 3px 3px 0;
  background: var(--loan-primary);
  box-shadow: 0 0 10px var(--loan-primary);
}

/* 折叠态：菜单项图标居中 */
.sider.collapsed .menu-item {
  justify-content: center;
  padding: 8px 0;
  margin: 1px 6px;
}

/* 底部折叠按钮：精致化（半透明背景 + hover 主色） */
.sider-foot {
  height: 50px;
  padding: 0 14px;
  border-top: 1px solid var(--loan-border);
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--loan-text-muted);
  font-size: 12px;
  cursor: pointer;
  user-select: none;
  flex-shrink: 0;
  white-space: nowrap;
  transition: color var(--loan-transition), background var(--loan-transition);
  background: color-mix(in srgb, var(--loan-sider-bg) 60%, transparent);
}
.sider.collapsed .sider-foot {
  justify-content: center;
  padding: 0;
}

.sider-foot:hover {
  color: var(--loan-primary);
  background: color-mix(in srgb, var(--loan-primary) 8%, transparent);
}
.sider-foot:hover .collapse-icon { color: var(--loan-primary); }

.collapse-icon {
  flex-shrink: 0;
  transition: transform var(--loan-transition);
}

.collapse-icon.rotated {
  transform: rotate(180deg);
}

/* 多标签栏：可关闭、可刷新、切换不刷新已打开页面 */
.tabs-bar {
  display: flex;
  align-items: stretch;
  background: var(--loan-sider-bg);
  border-bottom: 1px solid var(--loan-border);
  padding: 0 8px;
  min-width: 0; /* 关键：flex 子项允许收缩，让 el-tabs__nav-wrap 可触发横向滚动 */
}
.tabs-bar :deep(.el-tabs) {
  flex: 1;
  min-width: 0; /* 关键：允许 el-tabs 收缩到 tabs-bar 宽度，nav 溢出时走 EP 内部滚动，
                   否则 nav-wrap 会被 nav 内容撑到 1129px 超出 tabs-bar 背景（12+ tab 时右侧溢出） */
  overflow: hidden;
}
.tabs-bar :deep(.el-tabs__header) {
  margin: 0;
  border: none;
}
.tabs-bar :deep(.el-tabs__nav-wrap::after) {
  display: none;
}
.tabs-bar :deep(.el-tabs__item) {
  border: 1px solid var(--loan-border);
  border-radius: 6px 6px 0 0;
  margin-right: 4px;
  height: 30px;
  line-height: 30px;
  padding: 0 12px;
  font-size: 12px;
  background: var(--loan-card-bg);
}
.tabs-bar :deep(.el-tabs__item.is-active) {
  background: var(--loan-primary);
  color: #fff;
  border-color: var(--loan-primary);
}
.tabs-bar :deep(.el-tabs__item.is-active .tab-close) {
  color: rgba(255, 255, 255, 0.85);
}
.tabs-bar :deep(.el-tabs__item .el-icon-close) {
  font-size: 12px;
  margin-left: 4px;
}
.tab-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.tab-label__text {
  max-width: 130px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tab-close {
  cursor: pointer;
  border-radius: 50%;
  padding: 2px;
  line-height: 0;
  color: currentColor;
  opacity: 0.7;
  flex-shrink: 0;
}
.tab-close:hover {
  opacity: 1;
  background: rgba(255, 255, 255, 0.18);
}
.tabs-refresh {
  align-self: center;
  width: 36px;
  height: 36px;
  border: 1px solid var(--loan-border);
  background: var(--loan-card-bg);
  border-radius: 6px;
  color: var(--loan-text-secondary);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-left: 4px;
}
.tabs-refresh:hover {
  color: var(--loan-primary);
  border-color: var(--loan-primary);
}

/* 右侧主体 */
.main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.topbar {
  height: 60px;
  background: var(--loan-card-bg);
  border-bottom: 1px solid var(--loan-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  flex-shrink: 0;
}

.topbar-left {
  display: flex;
  align-items: center;
  gap: 14px;
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.collapse-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: none;
  border-radius: var(--loan-radius-sm);
  background: transparent;
  color: var(--loan-text-secondary);
  cursor: pointer;
  transition: background var(--loan-transition), color var(--loan-transition);
}

.collapse-btn:hover {
  background: var(--loan-surface);
  color: var(--loan-text);
}

.user {
  display: flex;
  align-items: center;
  gap: 10px;
  border: none;
  background: transparent;
  cursor: pointer;
  padding: 4px 6px;
  border-radius: var(--loan-radius-sm);
  transition: background var(--loan-transition);
}

.user:hover {
  background: var(--loan-surface);
}

.user-avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: var(--loan-gradient);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  flex-shrink: 0;
}

.user-info {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  line-height: 1.2;
}

.user-name {
  font-size: 13px;
  color: var(--loan-text);
}

.user-role {
  font-size: 11px;
  color: var(--loan-primary);
  background: var(--loan-primary-soft);
  padding: 1px 6px;
  border-radius: 999px;
}

.content {
  flex: 1;
  display: flex;          /* 让 .loan-page 作为 flex 子项可以占满 */
  flex-direction: column;
  overflow-y: auto;
  padding: 20px 24px;
}

/* 移动端：侧栏收起为窄条，避免挤压内容 */
@media (max-width: 768px) {
  .sider {
    width: 64px;
  }
  .sider .brand-text,
  .sider .menu-text,
  .sider .sider-foot span {
    display: none;
  }
  .content {
    padding: 16px;
  }
}
</style>
