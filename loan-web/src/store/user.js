import { defineStore } from 'pinia';
import { login as loginApi, logout as logoutApi } from '@/api/auth';
import { KEYS, getStorage, getStorageJSON, setStorage, setStorageJSON, removeStorage } from '@/utils/storage';

/**
 * 用户状态 Store：token + 用户信息（持久化到 localStorage）。
 *
 * <p>权限模型（deny-by-default）：
 * - 路由/指令「未声明权限」→ 放行；
 * - 路由/指令「声明了权限但用户未授权」→ 拦截（403 / 隐藏按钮）；
 * - 后端登录下发的 permissions 优先；未下发时按角色映射：
 *   BOSS / OPERATOR / SUPER_ADMIN / SUPER 全量通配 '*'；
 *   DEPT_MANAGER / ADVISER 留空（= 仅可见未声明权限的页面，受菜单按角色过滤约束）。
 */
const TOKEN_KEY = KEYS.TOKEN;
const USER_KEY = KEYS.USER;

/**
 * 角色默认权限映射（未下发 permissions 时的兜底；T9 对齐菜单矩阵与 4 条 meta.permission）：
 * - 管理/运营角色（BOSS/OPERATOR/SUPER_ADMIN/SUPER）全量通配 '*'（可进全部页）；
 * - DEPT_MANAGER：部门视角，可见 客户档案/审计日志（page:client/page:audit），不可见 组织权限/风控名单；
 * - ADVISER：本人视角，仅 客户档案（page:client）；
 * - CHANNEL：沙箱，无页面级权限。
 */
const ROLE_PERMISSIONS = {
  BOSS: ['*'],
  OPERATOR: ['*'],
  SUPER_ADMIN: ['*'],
  SUPER: ['*'],
  DEPT_MANAGER: ['page:client', 'page:audit'],
  ADVISER: ['page:client'],
  CHANNEL: [],
};

export const useUserStore = defineStore('user', {
  state: () => {
    // 本地用户信息容错：数据损坏时忽略并清理，避免应用启动即崩溃
    let cachedUser = null;
    try {
      cachedUser = getStorageJSON(USER_KEY, null);
    } catch (e) {
      removeStorage(USER_KEY);
    }
    return {
      token: getStorage(TOKEN_KEY) || '',
      user: cachedUser,
      /** 权限码数组：登录下发；未下发时按角色默认（T3 修复：空数组真值 bug——permissions 为空数组时落入角色兜底，避免管理员刷新后自 403） */
      permissions:
        (cachedUser?.permissions && cachedUser.permissions.length)
          ? cachedUser.permissions
          : ROLE_PERMISSIONS[cachedUser?.roleCode] || [],
    };
  },
  getters: {
    /** 是否已登录 */
    isLogin: (state) => !!state.token,
    /** 用户姓名 */
    displayName: (state) => state.user?.name || '管理员',
    /** 角色（渠道无 roleCode——01 角色模型：roleCode 仅员工有；按 userType 推导 CHANNEL，D20 渠道沙箱菜单依赖） */
    roleCode: (state) => {
      if (!state.user) return '';
      if (state.user.roleCode) return state.user.roleCode;
      if (state.user.userType === 'CHANNEL') return 'CHANNEL';
      return '';
    },
  },
  actions: {
    /**
     * 登录：调后端登录接口，存 token + 用户信息。
     */
    async doLogin(payload) {
      const res = await loginApi(payload);
      return this.applyLogin(res);
    },

    /**
     * 应用登录响应（token/user/permissions 落库 + 持久化），员工与渠道登录共用（T11/D21）。
     * @param res 后端返回体（含 data: { token, user, permissions? }）
     */
    applyLogin(res) {
      const data = res?.data || {};
      this.token = data.token || '';
      this.user = data.user || null;
      // 权限：登录下发优先，否则按角色默认（BOSS 全通 / 其余留空=未配置）
      const rolePerms = ROLE_PERMISSIONS[this.user?.roleCode] || [];
      this.permissions = Array.isArray(data.permissions) && data.permissions.length
        ? data.permissions
        : rolePerms;
      if (this.user && this.permissions.length) this.user = { ...this.user, permissions: this.permissions };
      setStorage(TOKEN_KEY, this.token);
      setStorageJSON(USER_KEY, this.user);
      return data;
    },

    /**
     * 登出：清 token + 用户信息（尽力调后端登出，不阻塞）。
     */
    async doLogout() {
      try {
        await logoutApi();
      } catch (e) {
        // 忽略登出失败
      }
      this.token = '';
      this.user = null;
      this.permissions = [];
      removeStorage(TOKEN_KEY);
      removeStorage(USER_KEY);
    },

    /**
     * 权限判定（deny-by-default）：
     * - 未声明权限（code 为空）→ 放行（路由/指令未要求权限）
     * - 声明了权限但用户权限列表为空（后端未下发 / 角色无授权）→ 拦截
     * - code 为 '*' 或存在于 permissions → 通过；数组则全部命中才通过
     * @param {string|string[]} code 权限码
     */
    hasPerm(code) {
      if (!code) return true;
      const list = this.permissions || [];
      if (!list.length) return false;
      const pass = (c) => list.includes('*') || list.includes(c);
      if (Array.isArray(code)) return code.every(pass);
      return pass(code);
    },
  },
});
