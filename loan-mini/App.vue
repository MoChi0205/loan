<script>
/**
 * 小程序根组件。
 * 全局样式：瑞幸风格 —— 深色品牌主调 #0B1D3A、暖金点缀 #C8A96E、
 * 大圆角阴影卡片、无 AI 模板感。
 */
import { useUserStore } from './store/user';

export default {
  onLaunch() {
    // 阶段三：接入 wx.login 静默换 token + 手机号一键登录

    // 平板 / iPad 限宽判别（T3 · C 类修复）：
    // windowWidth > 768 视为平板，启用 600px 居中限宽（u-shell / tab-bar.is-tablet）。
    // 小程序端 wxss 不支持媒体查询宽屏治理（被 #ifdef H5 隔离），故走 JS + class 驱动；
    // 兼容取窗口信息：优先 uni.getWindowInfo，旧版回退 getSystemInfoSync。
    try {
      const info = (typeof uni.getWindowInfo === 'function')
        ? uni.getWindowInfo()
        : uni.getSystemInfoSync();
      const windowWidth = (info && Number(info.windowWidth)) || 0;
      useUserStore().setTablet(windowWidth > 768);
    } catch (e) {
      // 取窗口信息失败时不限宽，降级为全宽渲染
    }
  },
};
</script>

<style>
/* ============================================================
   企融通 loan-mini · 设计系统 v1.0 全局令牌
   来源：loan-mini-设计系统规范.md（唯一设计依据）
   单位：rpx（750rpx = 屏宽，1px 逻辑像素 = 2rpx）
   一套代码编译 微信小程序 + H5，禁止页面内写裸值
   ============================================================ */
/* 注意：微信 wxss 不支持 :root 选择器（只认 page）；uni-app H5 端 page 会匹配 uni-page 元素，
   故统一用 page 定义全局 CSS 变量，双端生效。勿改回 :root。 */
page {
  /* ===== 品牌色 ===== */
  --brand-deep: #0B1D3A;
  --brand-mid: #132D56;
  --brand-bright: #1A3A6E;
  --gold: #C8A96E;
  --gold-bg: #F5E6C4;

  /* ===== 中性色 ===== */
  --bg-page: #F8FAFC;
  --bg-card: #FFFFFF;
  --bg-input: #F1F5F9;
  --line: #E2E8F0;
  --text-primary: #1E293B;
  --text-body: #475569;
  --text-secondary: #64748B;
  --text-placeholder: #CBD5E1;
  --text-invert: #FFFFFF;

  /* ===== 语义色（仅用于图标/底色，文字请用 -text 变量） ===== */
  --success: #10B981;
  --warning: #F59E0B;
  --danger: #EF4444;
  --info: #06B6D4;

  /* ===== 无障碍文字色（对比度已验证 ≥4.5:1，WCAG AA） ===== */
  --warning-text: #B45309;
  --gold-text: #3A2E12;
  --success-text: #047857;
  --danger-text: #B91C1C;
  --info-text: #0E7490;

  /* ===== 语义浅底（badge / 提示卡底色，配 -text 文字） ===== */
  --success-bg: #ECFDF5;
  --warning-bg: #FFFBEB;
  --warning-line: #FDE68A;

  /* ===== 间距（4px 基准 → rpx） ===== */
  --space-1: 8rpx;    --space-2: 16rpx;   --space-3: 24rpx;  --space-4: 32rpx;
  --space-5: 40rpx;   --space-6: 48rpx;   --space-8: 64rpx;  --space-10: 80rpx;
  --space-12: 96rpx;  --space-16: 128rpx;
  /* 语义间距 */
  --space-page-gutter: var(--space-4);
  --space-card-pad: var(--space-4);
  --space-stack: var(--space-3);
  --space-field: var(--space-3);
  --space-inline: var(--space-2);

  /* ===== 圆角（4 级） ===== */
  --radius-sm: 16rpx;
  --radius-md: 24rpx;
  --radius-lg: 32rpx;
  --radius-full: 999rpx;

  /* ===== 字号（7 级，含 xxs 字号下限） ===== */
  --fs-xxs: 22rpx;  --fs-xs: 22rpx;   --fs-sm: 24rpx;   --fs-md: 26rpx;
  --fs-lg: 30rpx;   --fs-xl: 36rpx;   --fs-2xl: 44rpx;
  --lh-tight: 1.3;  --lh-base: 1.6;   --lh-loose: 1.7;

  /* ===== 阴影（3 级） ===== */
  --shadow-sm: 0 2rpx 4rpx rgba(15, 23, 42, 0.04);
  --shadow-md: 0 8rpx 24rpx rgba(15, 23, 42, 0.06);
  --shadow-lg: 0 16rpx 48rpx rgba(15, 23, 42, 0.08);

  /* ===== 过渡（3 级） ===== */
  --transition-fast: 150ms ease;
  --transition-base: 200ms ease;
  --transition-slow: 280ms ease;

  /* ===== 角色色板（7 角色，深色底 + 白字，ΔE > 20） ===== */
  --role-customer: #0B1D3A;
  --role-channel: #0E7490;
  --role-adviser: #8A6D3A;
  --role-deptmgr: #1D4ED8;
  --role-boss: #6D28D9;
  --role-operator: #3A2E12;
  --role-super: #1F2937;

  /* ============================================================
     向后兼容别名：旧页面仍在用的令牌名，映射到新体系
     TODO 全量迁移后可删除此区块
     ============================================================ */
  --color-primary: var(--brand-deep);
  --color-primary-light: var(--brand-bright);
  --color-accent: var(--gold);
  --color-bg: var(--bg-page);
  --color-card: var(--bg-card);
  --color-text: var(--text-primary);
  --color-text-secondary: var(--text-secondary);
  --color-text-hint: var(--text-placeholder);
  --color-border: var(--line);
  --color-success: var(--success);
  --color-warning: var(--warning);
  --color-danger: var(--danger);
  --radius-card: var(--radius-lg);
  --radius-btn: var(--radius-md);
  --radius-input: var(--radius-md);
  --shadow-card: var(--shadow-md);
}

page {
  background-color: var(--bg-page);
  color: var(--text-primary);
  font-size: var(--fs-md);
  line-height: var(--lh-base);
  -webkit-font-smoothing: antialiased;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', sans-serif;
}

/* ===== 无障碍：键盘焦点可见（WCAG 2.4.7）=====
   #ifdef H5 独占：小程序无键盘，focus-visible 伪类微信 wxss 不支持 */
/* #ifdef H5 */
:focus-visible,
.app-focusable:focus-visible {
  outline: 4rpx solid var(--gold);
  outline-offset: 4rpx;
  border-radius: var(--radius-sm);
}
/* #endif */

/* ===== 无障碍：尊重系统"减少动效"偏好（WCAG 2.3.3）=====
   #ifdef H5 独占：微信 wxss 不支持 prefers-reduced-motion 媒体特性，
   且不支持 *::before / *::after 全局伪元素（会编译失败）。 */
/* #ifdef H5 */
@media (prefers-reduced-motion: reduce) {
  * {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
  }
}
/* #endif */

/* 去除 button 默认样式 */
button {
  margin: 0;
  padding: 0;
  background: transparent;
  border: none;
  line-height: inherit;
  font-size: inherit;
}

button::after {
  border: none;
}

/* ===== 通用布局工具类 =====
   注意：微信 wxss 不支持 `*` 通用选择器与 `+` 相邻兄弟选择器（报 error at token '*'），
   .u-stack 改用后代 + :first-child 实现（仅作用 view 子元素）。 */
.u-page { padding: var(--space-page-gutter); }
.u-stack view { margin-top: var(--space-stack); }
.u-stack view:first-child { margin-top: 0; }
.u-row { display: flex; align-items: center; gap: var(--space-inline); }
.u-row-between { display: flex; align-items: center; justify-content: space-between; }
.u-grow { flex: 1; min-width: 0; }
.u-ellipsis { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

/* ===== H5 桌面端悬停反馈（P1-8） =====
   小程序端无 hover 概念（仅 touch :active），此媒体查询仅 H5 桌面命中。
   按钮类：AppButton 自带 hover-class；此处兜底所有原生 button。
   卡片类：可点击卡片加 class="u-hover" 即可获得统一悬停反馈。
   #ifdef H5 独占：微信 wxss 不支持 hover 媒体特性与 :hover 伪类 */
/* #ifdef H5 */
@media (hover: hover) {
  button:not([disabled]):not([loading]):hover {
    opacity: 0.88;
  }
  .u-hover {
    transition: opacity var(--transition-fast), transform var(--transition-fast), box-shadow var(--transition-fast);
  }
  .u-hover:hover {
    opacity: 0.88;
    transform: translateY(-2rpx);
    box-shadow: var(--shadow-md);
  }
}
/* #endif */

/* ===== H5 宽屏限宽（阶段2 H1）：rpx 随视口等比放大，宽屏崩坏 =====
   uni-app H5 把 rpx 编译为 rem（根字号 = 视口宽/23.4375px），1280px 视口下整页约 3.4 倍。
   仅 H5 生效：≥768px 视口时页面限宽 600px 居中，容器外背景用品牌灰。 */
/* #ifdef H5 */
@media (min-width: 768px) {
  uni-app {
    background: var(--line);
  }
  uni-page-body {
    max-width: 600px;
    margin: 0 auto;
    min-height: 100vh;
    background: var(--bg-page);
    box-shadow: 0 0 32rpx rgba(15, 23, 42, 0.08);
  }
}
/* #endif */

/* ===== 平板 / iPad 限宽（T3 · C 类修复，双端通用，不依赖媒体查询） =====
   小程序端 wxss 不支持媒体查询宽屏治理（被 #ifdef H5 隔离），故用 JS 驱动的 class：
   - 各 tab 页根节点挂 u-shell：内容限宽 600px 居中
   - TabBar 根节点挂 is-tablet：底部导航同步限宽居中
   注意：max-width 用 px（不随 rpx 放大），正是限宽所需；
   !important 用于覆盖 TabBar 组件 scoped 基类 .tab-bar 的 left/right 设定，
   保证 mp-weixin 端也能正确居中（H5 端由上方 @media 块兜底）。 */
.u-shell {
  max-width: 600px;
  margin: 0 auto;
}
.tab-bar.is-tablet {
  max-width: 600px !important;
  left: 50% !important;
  right: auto !important;
  transform: translateX(-50%) !important;
}
</style>
