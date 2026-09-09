<script>
/**
 * 小程序根组件。
 * 全局样式：墨金 Ink & Gold —— 深墨蓝 #16203A + 暖金 #D9A441、
 * 明暗双主题，来源 docs/design-system/DesignSystemManifest.md。
 */
import { useUserStore } from './store/user';
import { initWxJsSdk } from './utils/wx-jssdk';
import { captureInvitation, consumePendingInvitation } from './utils/invitation';
import { applyThemeOnLaunch } from './theme';

/**
 * H5 SPA 路由变化后重新注入 JS-SDK（签名须与当前页 URL 匹配）。
 * uni-app H5 使用 history API 导航，包裹 pushState/replaceState 并在 popstate 上重签。
 */
function bindH5RouteJsSdk() {
  // #ifdef H5
  if (typeof window === 'undefined' || !window.history) return;
  // 签名接口要求登录态。未登录落地页不应提前请求，否则会产生无意义的 401 告警；
  // 登录后页面按需调用 setWxShare/initWxJsSdk，后续路由变化再自动重签。
  const reinit = () => {
    if (useUserStore().token) initWxJsSdk();
  };
  window.addEventListener('popstate', reinit);
  const _push = window.history.pushState;
  window.history.pushState = function (...args) {
    const r = _push.apply(window.history, args);
    reinit();
    return r;
  };
  const _replace = window.history.replaceState;
  window.history.replaceState = function (...args) {
    const r = _replace.apply(window.history, args);
    reinit();
    return r;
  };
  // #endif
}

export default {
  onLaunch(options) {
    captureInvitation(options);
    // 墨金主题：应用已保存的明暗模式（H5 端写入根节点 data-theme 标记）
    applyThemeOnLaunch();
    // 阶段三：接入 wx.login 静默换 token + 手机号一键登录

    // #ifdef H5
    // 微信 JS-SDK 全局初始化：按当前页 URL 从后端拉签名并注入 wx.config，
    // 使各页可调用分享 / 支付等 jsApi。具体页面如需自定义分享内容，调用 setWxShare(shareData)。
    // SPA 路由变化后重新签名，保证每页 URL 与签名一致。
    if (useUserStore().token) initWxJsSdk();
    bindH5RouteJsSdk();
    // #endif

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
  onShow(options) {
    captureInvitation(options);
    const store = useUserStore();
    if (store.token) consumePendingInvitation(store);
  },
};
</script>

<style>
/* ============================================================
   企融通 loan-mini · 墨金 Ink & Gold 全局令牌
   来源：docs/design-system/DesignSystemManifest.md（已归档唯一设计依据）
   单位：rpx（750rpx = 屏宽，1px 逻辑像素 = 2rpx）
   一套代码编译 微信小程序 + H5，禁止页面内写裸值
   ============================================================ */
/* 注意：微信 wxss 不支持 :root 选择器（只认 page）；uni-app H5 端 page 会匹配 uni-page 元素，
   故统一用 page 定义全局 CSS 变量，双端生效。勿改回 :root。 */
/* stylelint-disable color-no-hex -- 设计令牌定义源：裸 hex 为规范唯一来源，禁止 lint 拦截 */
page {
  /* ===== 品牌色（墨金 Ink & Gold） ===== */
  /* 阶梯：navy(墨蓝渐变起点) < deep(主交互/按钮) < mid(悬停提亮) < bright(小面积点缀) */
  --brand-navy: #16203A;
  --brand-deep: #D9A441;
  --brand-mid: #E0AE4E;
  --brand-bright: #F2C879;
  /* Hero 渐变：大面积 Hero（首页通栏/我的档案头/匹配守卫/工单状态卡）统一引用 */
  --hero-gradient: linear-gradient(160deg, #16203A 0%, #1B2945 40%, #D9A441 100%);
  --gold: #D9A441;
  --gold-bg: #FFF5E0;

  /* ===== 中性色 ===== */
  --bg-page: #F4F6FA;
  --bg-card: #FFFFFF;
  --bg-input: #EEF2F8;
  --line: #E2E8F2;
  --text-primary: #16203A;
  --text-body: #243456;
  --text-secondary: #6B7689;
  --text-placeholder: #93A0B8;
  --text-invert: #FFFFFF;

  /* ===== 语义色（仅用于图标/底色，文字请用 -text 变量） ===== */
  --success: #2FA37A;
  --warning: #E0A441;
  --danger: #D8695F;
  --info: #4C7BD9;

  /* ===== 无障碍文字色（对比度已验证 ≥4.5:1，WCAG AA） ===== */
  --warning-text: #B8861A;
  --gold-text: #16203A;
  --success-text: #047857;
  --danger-text: #B91C1C;
  --info-text: #3D63E0;

  /* ===== 语义浅底（badge / 提示卡底色，配 -text 文字） ===== */
  --success-bg: #ECFDF5;
  --warning-bg: #FFF5E0;
  --warning-line: #F2C879;
  --brand-bg: #FFF5E0;

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
  /* 扩展（组件/页面实际使用的离阶梯尺寸，纳入令牌避免裸值；值不变） */
  --fs-em: 28rpx;     --fs-title: 32rpx;   --fs-arrow: 40rpx;   --fs-display: 48rpx;
  --lh-tight: 1.3;  --lh-base: 1.6;   --lh-loose: 1.7;

  /* ===== 阴影（3 级） ===== */
  --shadow-sm: 0 2rpx 4rpx rgba(15, 23, 42, 0.04);
  --shadow-md: 0 8rpx 24rpx rgba(15, 23, 42, 0.06);
  --shadow-lg: 0 16rpx 48rpx rgba(15, 23, 42, 0.08);

  /* ===== 过渡（3 级） ===== */
  --transition-fast: 150ms ease;
  --transition-base: 200ms ease;
  --transition-slow: 280ms ease;

  /* ===== 冷玻璃质感系列（首页 header / 我的页头 / 匹配守卫：磨砂油画 + 毛玻璃） =====
     小程序端 backdrop-filter 不支持 → 降级为多层径向渐变油画光斑；H5 端启用 blur 真毛玻璃 */
  --glass-bg: #EEF2F8;
  --glass-bg-deep: #E2E8F2;
  --glass-hi: rgba(255, 255, 255, 0.45);
  --glass-edge: rgba(255, 255, 255, 0.6);
  --glass-tint: #F2C879;
  --glass-gradient: radial-gradient(circle at 18% 20%, rgba(255,255,255,0.5) 0%, rgba(255,255,255,0) 38%),
                     radial-gradient(circle at 82% 88%, rgba(217,164,65,0.10) 0%, rgba(217,164,65,0) 42%),
                     linear-gradient(160deg, #F4F6FA 0%, #E8ECF5 55%, #E2E8F2 100%);

  /* ===== 按钮体系（墨金：主按钮暖金填充、墨蓝文字；次按钮玻璃/描边） =====
     所有按钮/类按钮控件（AppButton、胶囊、CTA）一律引用下列令牌，禁止页面内裸色值 */
  --btn-primary-bg: linear-gradient(135deg, #D9A441 0%, #E0AE4E 55%, #F2C879 100%);
  --btn-primary-shadow: 0 12rpx 28rpx rgba(217, 164, 65, 0.28);
  --btn-gold-bg: linear-gradient(135deg, #D9A441 0%, #F2C879 100%);
  --btn-gold-shadow: 0 12rpx 28rpx rgba(217, 164, 65, 0.30);
  --btn-danger-bg: linear-gradient(135deg, #B91C1C 0%, #D8695F 100%);
  --btn-danger-shadow: 0 12rpx 28rpx rgba(184, 28, 28, 0.26);
  /* 玻璃/描边按钮：浅底 + 细边，配冷玻璃卡片 */
  --btn-glass-bg: rgba(255, 255, 255, 0.72);
  --btn-glass-border: rgba(217, 164, 65, 0.28);
  --btn-ghost-bg: transparent;
  --btn-ghost-border: var(--brand-deep);
  --btn-disabled-bg: #E2E8F2;
  --btn-disabled-text: #93A0B8;

  /* ===== 角色色板（v2 明亮商务版：原深蓝/灰黑 → 鲜亮区分色，白字 ΔE>20） ===== */
  --role-customer: #2443C2;
  --role-channel: #0E9CB0;
  --role-adviser: #C8841A;
  --role-deptmgr: #4F46E5;
  --role-boss: #9333EA;
  --role-operator: #059669;
  --role-super: #DB2777;

  /* ============================================================
     向后兼容别名：旧页面仍在用的令牌名，映射到新体系。
     删除前必须先完成全仓引用归零检查，当前作为稳定兼容层保留。
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
/* stylelint-enable color-no-hex */

page {
  background-color: var(--bg-page);
  color: var(--text-primary);
  font-size: var(--fs-md);
  line-height: var(--lh-base);
  -webkit-font-smoothing: antialiased;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', sans-serif;
}

/* ===== 主题根（墨金暗色铺满）=====
   微信小程序端 page 元素无法在模板里接收 data-theme 属性（page[data-theme] 永不会命中），
   故改为「页面根 <view> 挂 .theme-root + :data-theme」驱动 [data-theme="dark"] 暗色令牌。
   .theme-root 负责让暗色背景铺满整屏（page 自身背景仍是浅色，会被它覆盖）。 */
.theme-root {
  min-height: 100vh;
  background-color: var(--bg-page);
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
  /* ⚠️ 居中禁止 left:50% + right:auto + translateX(-50%)：fixed 元素在
     right:auto 且无显式 width 时按 shrink-to-fit 收缩成内容宽（实测 381px），
     5 个 tab 挤压变形（用户 2026-09-07 报告叠影根因）；须定宽居中 */
  max-width: 600px !important;
  left: 0 !important;
  right: 0 !important;
  margin: 0 auto !important;
  transform: none !important;
}

/* ============================================================
 * 墨金 Ink & Gold · 暗色主题（与 loan-web 对齐）
 * 小程序 SVG 不解析 var()，故 AppIcon / TabBar 运行时按 mode 注入真实色；
 * 此处定义 CSS 变量层，由 page[data-theme="dark"] 生效。
 * ============================================================ */
:root[data-theme="dark"], [data-theme="dark"] {
  --bg-page: #0E1626;
  --bg-card: #131E33;
  --bg-input: #1B2945;
  --line: #26344F;
  --text-primary: #E8EDF5;
  --text-body: #B6C2D6;
  --text-secondary: #93A0B8;
  --text-placeholder: #64748B;
  --brand-navy: #0E1626;
  --brand-deep: #E0AE4E;   /* 暗底强调转暖金 */
  --brand-mid: #F2C879;
  --brand-bright: #FBD98C;
  --hero-gradient: linear-gradient(160deg, #0E1626 0%, #16233A 40%, #1B2945 100%);
  --gold: #F2C879;
  --gold-bg: #2A2310;
  --success: #3FB98A;
  --warning: #E0A441;
  --danger: #E0877D;
  --info: #6E9BE0;
  --success-text: #6EE7B7;
  --warning-text: #F2C879;
  --danger-text: #F0A89F;
  --info-text: #9FBEF0;
  --success-bg: rgba(47, 163, 122, 0.16);
  --warning-bg: rgba(224, 164, 65, 0.16);
  --brand-bg: rgba(224, 174, 78, 0.16);
  --glass-bg: #1B2945;
  --glass-bg-deep: #16233A;
  --glass-tint: #1B2945;
  --glass-gradient: radial-gradient(circle at 18% 20%, rgba(224, 174, 78, 0.10) 0%, rgba(224, 174, 78, 0) 38%),
                    linear-gradient(160deg, #131E33 0%, #16233A 55%, #1B2945 100%);
  --btn-primary-bg: linear-gradient(135deg, #C98A2B 0%, #E0AE4E 55%, #F2C879 100%);
  --btn-primary-shadow: 0 12rpx 28rpx rgba(224, 174, 78, 0.28);
  --btn-glass-border: rgba(224, 174, 78, 0.28);
  --shadow-md: 0 8rpx 24rpx rgba(0, 0, 0, 0.3);
}
</style>
