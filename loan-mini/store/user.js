/**
 * 小程序用户状态（Pinia，对齐 design-three-terminal.md §7.4）。
 *
 * 管理：token / clientCode / user / profile / invitedFlag / authStatus。
 * - token 持久化于 uni.setStorageSync('loan_token')，与 api/request.js 共用 key
 * - init()：应用启动/首页加载时读取本地 token，并拉取 /api/mini/me 刷新档案摘要
 * - authStatus：UNAUTHED（未认证）/ ENTERPRISE（企业已认证）/ PERSONAL（个人已认证）
 */
import { defineStore } from 'pinia';
import { clearToken as clearTokenStorage } from '../api/request';
import { me } from '../api/auth';

/** 登录令牌 storage key（与 api/request.js 保持一致） */
const TOKEN_KEY = 'loan_token';

/** 角色标识 storage key（/api/mini/me 不返回角色，需本地持久化以支撑刷新后恢复） */
const ROLE_KEY = 'loan_role';

/**
 * 依据档案摘要推导认证状态。
 *
 * @param {Object|null} profile /api/mini/me 返回的档案摘要
 * @returns {'UNAUTHED'|'ENTERPRISE'|'PERSONAL'}
 */
function resolveAuthStatus(profile) {
  if (!profile || !profile.authenticated) {
    return 'UNAUTHED';
  }
  return profile.customerGroup === 'ENTERPRISE' ? 'ENTERPRISE' : 'PERSONAL';
}

/**
 * 依据后端 LoanUser 推导前端角色标识。
 *
 * 映射规则（userType 为主，STAFF 再按 roleCode 细分）：
 * - CUSTOMER              → customer
 * - CHANNEL               → channel
 * - STAFF + ADVISER       → adviser
 * - STAFF + DEPT_MANAGER  → deptmgr
 * - STAFF + BOSS          → boss
 * - STAFF + OPERATOR      → operator
 * - STAFF + SUPER_ADMIN   → super
 *
 * @param {Object|null} user LoanUser（含 userType / roleCode）
 * @returns {string} 角色标识；未知归为 customer（最小权限兜底）
 */
function resolveRole(user) {
  if (!user || !user.userType) return 'customer';
  const type = String(user.userType).toUpperCase();
  if (type === 'CUSTOMER') return 'customer';
  if (type === 'CHANNEL') return 'channel';
  if (type === 'STAFF') {
    const code = String(user.roleCode || '').toUpperCase();
    if (code === 'ADVISER') return 'adviser';
    if (code === 'DEPT_MANAGER') return 'deptmgr';
    if (code === 'BOSS') return 'boss';
    if (code === 'OPERATOR') return 'operator';
    if (code === 'SUPER_ADMIN' || code === 'SUPER') return 'super';
    // 员工但未识别细分角色：按顾问处理（最小可用权限）
    return 'adviser';
  }
  return 'customer';
}

export const useUserStore = defineStore('user', {
  state: () => ({
    /** 登录令牌 */
    token: '',
    /** 客户业务编码 */
    clientCode: '',
    /** 登录用户信息（LoanUser：userNo=clientCode、name、invitedFlag 等） */
    user: null,
    /** 档案摘要（/api/mini/me，敏感字段已脱敏） */
    profile: null,
    /** 是否被邀请（0/1） */
    invitedFlag: 0,
    /** 认证状态：UNAUTHED / ENTERPRISE / PERSONAL */
    authStatus: 'UNAUTHED',
    /** 已绑定引荐人姓名（本地缓存，绑定成功时写入） */
    referrerName: '',
    /**
     * 是否平板 / iPad 宽屏（windowWidth > 768）。
     * 用于驱动 600px 居中限宽（T3 · C 类修复）：各 tab 页根挂 u-shell、TabBar 根挂 is-tablet。
     * 由 App.vue onLaunch 依据窗口宽度初始化，不持久化（每次启动重算）。
     */
    isTablet: false,
    /**
     * 当前角色标识（决定菜单 / 权限 / 页面差异化渲染）。
     *
     * 取值：customer 客户 / channel 渠道合作方 / adviser 顾问 /
     *      deptmgr 部门经理 / boss 老板 / operator 运营管理员 / super 超级管理员
     *
     * 由后端 LoanUser 的 userType + roleCode 推导（见 resolveRole），
     * 并持久化到 storage —— 因为 /api/mini/me 只返回客户档案、不含角色信息，
     * 若不持久化，刷新后角色会丢失导致所有差异化判定失效。
     */
    role: 'customer',
  }),

  getters: {
    /** 是否已登录 */
    isLoggedIn: (state) => !!state.token,
    /** 是否已完成身份认证（企业或个人任一） */
    isAuthed: (state) => state.authStatus !== 'UNAUTHED',
    /** 是否为渠道合作方（沙箱隔离：不可操作匹配、不可见客户报告） */
    isChannel: (state) => state.role === 'channel',
    /**
     * 是否为企业员工（顾问 / 经理 / 老板 / 运营 / 超管）。
     * 企业员工拥有替客匹配、全量报告、命中产品可见等能力。
     */
    isStaff: (state) => ['adviser', 'deptmgr', 'boss', 'operator', 'super'].indexOf(state.role) >= 0,
    /** 角色展示名（用于「我的」页与徽章） */
    roleLabel: (state) => ({
      customer: '客户', channel: '渠道合作方', adviser: '顾问',
      deptmgr: '部门经理', boss: '老板', operator: '运营管理员', super: '超级管理员',
    }[state.role] || '客户'),
  },

  actions: {
    /**
     * 写入 token 并持久化。
     *
     * @param {string} token 登录令牌
     */
    setToken(token) {
      this.token = token || '';
      try {
        uni.setStorageSync(TOKEN_KEY, this.token);
      } catch (e) {
        /* storage 异常忽略 */
      }
    },

    /**
     * 设置平板 / iPad 宽屏标记（T3 · C 类修复）。
     *
     * @param {boolean} v 是否为宽屏（windowWidth > 768）
     */
    setTablet(v) {
      this.isTablet = !!v;
    },

    /**
     * 写入登录用户信息（登录响应中的 user）。
     *
     * @param {Object|null} user LoanUser
     */
    setUser(user) {
      this.user = user || null;
      this.clientCode = (user && user.userNo) || '';
      if (user && typeof user.invitedFlag === 'boolean') {
        this.invitedFlag = user.invitedFlag ? 1 : 0;
      }
      // 推导角色并持久化：/api/mini/me 不返回角色信息，刷新后需从 storage 恢复
      this.role = resolveRole(user);
      try {
        uni.setStorageSync(ROLE_KEY, this.role);
      } catch (e) {
        /* storage 异常忽略 */
      }
    },

    /**
     * 从本地缓存恢复角色（init 时兜底，避免刷新后差异化判定失效）。
     */
    loadRole() {
      try {
        const cached = uni.getStorageSync(ROLE_KEY);
        if (cached) this.role = cached;
      } catch (e) {
        /* storage 异常忽略 */
      }
    },

    /**
     * 初始化：读本地 token；已登录则拉取档案摘要刷新状态。
     *
     * @returns {Promise<boolean>} 是否处于已登录状态
     */
    async init() {
      let token = '';
      try {
        token = uni.getStorageSync(TOKEN_KEY) || '';
      } catch (e) {
        token = '';
      }
      this.token = token;
      if (!token) {
        this.clear();
        return false;
      }
      // 先恢复本地缓存的角色，保证 /me 返回前差异化判定已可用
      this.loadRole();
      try {
        await this.refreshProfile();
        return true;
      } catch (e) {
        // token 失效（code=2000 时 request.js 已清 token 回落地页）
        this.clear();
        return false;
      }
    },

    /**
     * 拉取档案摘要并刷新派生状态。
     */
    async refreshProfile() {
      const profile = await me();
      this.profile = profile || null;
      this.clientCode = (profile && profile.clientCode) || this.clientCode;
      this.invitedFlag = (profile && profile.invitedFlag) || 0;
      this.authStatus = resolveAuthStatus(profile);
      this.referrerName = (profile && profile.referrerName) || '';
      // 后端 /api/mini/me 已返回 roleInfo：以服务端角色为准并刷新本地缓存，
      // 避免清缓存 / 换设备登录后角色停留在旧值
      const roleInfo = profile && profile.roleInfo;
      if (roleInfo && roleInfo.role) {
        this.role = roleInfo.role;
        try {
          uni.setStorageSync(ROLE_KEY, this.role);
        } catch (e) {
          /* storage 异常忽略 */
        }
      }
    },

    /**
     * 记录绑定成功的引荐人姓名。引荐人只用于邀请链展示，不代表服务顾问。
     *
     * @param {string} name 引荐人昵称/姓名
     */
    setReferrer(name) {
      this.referrerName = name || '';
    },

    /**
     * 登出 / 会话失效：清空内存态并移除本地 token。
     */
    clear() {
      this.token = '';
      this.clientCode = '';
      this.user = null;
      this.profile = null;
      this.invitedFlag = 0;
      this.authStatus = 'UNAUTHED';
      this.referrerName = '';
      this.role = 'customer';
      clearTokenStorage();
      try {
        uni.removeStorageSync(ROLE_KEY);
        uni.removeStorageSync('loan_dev_role');
      } catch (e) {
        /* storage 异常忽略 */
      }
    },
  },
});
