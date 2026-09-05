# Web 界面设计规范审查报告

**审查对象**：`loan-web`（Vue3 + Element Plus 企业贷款咨询管理端）
**审查依据**：WCAG 2.1 A/AA、响应式设计最佳实践、Material/Apple HIG
**审查时间**：2026-08-26
**审查范围**：全局样式、Layout 布局、核心组件（DictTag / AppTableActions / ThemeSwitch）、index.html

---

## 一、汇总

| 严重等级 | 数量 | 含义 |
|----------|------|------|
| 🔴 关键 | 3 | 违反 WCAG A/AA，影响键盘/视障用户基本使用 |
| 🟡 重要 | 5 | 影响特定场景体验，建议尽快修复 |
| 🔵 建议 | 4 | 锦上添花，提升整体品质 |
| **合计** | **12** | |

**主要改进方向**：① 可交互 `<div>` 补齐键盘语义（role/tabindex/keyup）；② 浅色主题下激活菜单白字对比度不达标；③ 全局补 `prefers-reduced-motion` 与跳转链接。

---

## 二、🔴 关键问题（3 项）

### 🔴 1. 分组菜单标题为 `<div>` 但仅 `@click`，无键盘语义

**位置**：`src/layout/Layout.vue:42-57`
**违反**：WCAG 2.1.1 Keyboard（A 级）— 所有功能须可用键盘操作
**问题**：`.menu-group-title` 是 `<div @click="toggleGroup">`，无 `role`/`tabindex`/`@keyup`，键盘用户无法展开/折叠分组。
**修复**：
```html
<div
  class="menu-group-title"
  role="button"
  tabindex="0"
  :aria-expanded="isGroupExpanded(g.title)"
  @click="toggleGroup(g.title)"
  @keyup.enter="toggleGroup(g.title)"
  @keyup.space.prevent="toggleGroup(g.title)"
>
```

### 🔴 2. 侧栏折叠按钮 `<div>` 无键盘语义

**位置**：`src/layout/Layout.vue:96-110`
**违反**：WCAG 2.1.1 Keyboard（A）
**问题**：`.sider-foot` 是 `<div @click>`，键盘不可达。
**修复**：改 `<div role="button" tabindex="0" aria-label="折叠/展开导航" @click=... @keyup.enter=...>`，或直接用 `<button>`。

### 🔴 3. 浅色主题下激活菜单"白字"对比度不达标

**位置**：`src/layout/Layout.vue:606-613`（`.menu-group-title.active`）、`752-758`（`.menu-item.active`）
**违反**：WCAG 1.4.3 Contrast Minimum（AA）— 文本对比度 ≥ 4.5:1
**问题**：激活态 `color: #fff`，背景是 `linear-gradient(color-mix(primary 24%→10%, transparent))`。在**浅色主题**下侧栏底为白，渐变叠加后是极浅蓝，**白字在浅蓝上对比度约 1.5:1**，远低于 4.5:1。深色主题下（深底+白字）则达标。
**修复**：激活文字色应随主题切换，浅色下用主色深字而非白字：
```css
.menu-item.active { color: var(--loan-primary); }            /* 浅色：主色字 */
:root[data-theme="dark"] .menu-item.active { color: #fff; }  /* 深色：白字 */
```
（`.menu-group-title.active` 同理）

---

## 三、🟡 重要问题（5 项）

### 🟡 4. 缺少"跳过到主内容"链接

**位置**：`src/layout/Layout.vue`（全局）
**违反**：WCAG 2.4.1 Bypass Blocks（A）— 须提供跳过重复导航块的机制
**问题**：侧栏菜单很长，键盘用户每次进页都要 Tab 过整条菜单才能到内容区。
**修复**：在 `.layout` 最前加跳转链接：
```html
<a href="#main-content" class="skip-link">跳到主内容</a>
<main id="main-content" class="content">...</main>
```
```css
.skip-link{position:absolute;left:-9999px;top:8px;z-index:9999;}
.skip-link:focus{left:8px;background:var(--loan-primary);color:#fff;padding:8px 12px;border-radius:6px;}
```

### 🟡 5. 菜单/标签触摸目标偏小

**位置**：`src/layout/Layout.vue:721`（`.menu-item min-height:38px`）、`891`（`.tabs-refresh 28×28`）
**违反**：WCAG 2.5.8 Target Size Minimum（AA，2.2 新增，≥24px）/ 2.5.5（AAA，44px）
**问题**：38px 接近但未达 44px 触摸舒适值；刷新按钮 28×28 在触屏偏小。
**修复**：`.menu-item{min-height:44px}`、`.tabs-refresh{width:36px;height:36px}`（移动端优先 44px，桌面端可 36px+）。

### 🟡 6. "更多"下拉触发器缺键盘语义

**位置**：`src/components/AppTableActions.vue:10-13`
**违反**：WCAG 2.1.1 Keyboard（A）
**问题**：下拉触发用 `<span>`，虽 `el-dropdown` 内部对菜单项有键盘支持，但触发器 span 本身无 `role="button"`/`tabindex`，部分读屏不识别。
**修复**：
```html
<span class="..." role="button" tabindex="0" :aria-haspopup="true" aria-expanded="false">
```

### 🟡 7. 全局缺 `prefers-reduced-motion` 兜底

**位置**：`src/styles/index.css`（全局）
**违反**：WCAG 2.2.2 Pause/Hide（A，动效）+ 2.3.3 交互动画（AAA）
**问题**：`AnimatedBg.vue:321` 有该媒体查询，但 Layout 的菜单折叠过渡（`:675` `transition`）等全局动效无 reduced-motion 兜底，前庭敏感用户无法关闭。
**修复**：在 `index.css` 末尾补：
```css
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    animation-duration: 0.001ms !important;
    transition-duration: 0.001ms !important;
    scroll-behavior: auto !important;
  }
}
```

### 🟡 8. 登录表单"密码必填"与 SSO 语义不符

**位置**：`src/views/Login.vue:113`（`password required`）+ `65`（placeholder "暂不校验"）
**违反**：WCAG 3.3.2 Labels or Instructions（A）
**问题**：密码字段标 `required` 且占位提示"暂不校验"，语义自相矛盾，用户困惑是否必填。
**修复**：SSO 模式下应去掉 password 的 required，或改为非必填并明确提示"SSO 模式无需密码"。

---

## 四、🔵 建议（4 项）

### 🔵 9. DictTag 可加 `role="img"` + `title` 供读屏

**位置**：`src/components/DictTag.vue:2-15`
**说明**：当前 SVG 用 `aria-hidden` 隐藏，状态信息只靠可见文字传递。可给外层 `<span>` 加 `role="img" :aria-label="text"`，让读屏一次性读出"状态：可进件"。

### 🔵 10. 主题切换按钮缺 `aria-pressed`

**位置**：`src/components/ThemeSwitch.vue`（明暗切换按钮）
**说明**：明暗切换是状态型按钮，建议加 `:aria-pressed="isDark ? 'true':'false'"` + `aria-label`，让读屏播报"已按下/深色"。

### 🔵 11. 面包屑缺 `aria-label`

**位置**：`src/layout/Layout.vue:122`
**说明**：`<el-breadcrumb>` 未加 `aria-label="面包屑导航"`，读屏无法识别导航性质。

### 🔵 12. 颜色令牌 `--loan-text-muted` 浅色值偏弱

**位置**：`src/styles/index.css:32`（`#6b7280`）
**说明**：浅色主题下 `#6b7280` on `#f5f7fa` 对比度约 4.6:1，刚过 AA 但偏弱；建议提到 `#5b6470`（≈5.5:1）更稳。

---

## 五、做得好的部分（肯定）

| 项 | 位置 | 合规点 |
|----|------|--------|
| 语义化导航 | `Layout.vue:19` `<nav aria-label="主导航">` | 1.3.1 信息与关系 |
| 装饰图标隐藏 | `DictTag.vue:10` `aria-hidden` | 1.4.1 不依赖感官 |
| 状态双重编码 | `DictTag.vue` 图标+文字 | 1.4.1 不单靠颜色 |
| 焦点可见 | `index.css:786` `:focus-visible` | 2.4.7 |
| 页面语言/标题 | `index.html:2,6` `lang=zh-CN` + title | 3.1.1 / 2.4.2 |
| 右键菜单语义 | `AppContextMenu` `role=menu/menuitem` | 1.3.1 |
| 操作列键盘 | `AppTableActions.vue:36-39` role=button+tabindex+keyup | 2.1.1 |
| 暗色高对比 | 主题令牌 文字 #f1f5f9 / 边框 #243148 | 1.4.3（深色） |

---

## 六、修复优先级建议

1. **先修 🔴 1/2**（键盘语义）— 工作量小、影响大，加几个属性即可
2. **再修 🔴 3**（浅色激活对比度）— 改 color 令牌随主题切换
3. **补 🟡 4/7**（skip-link + reduced-motion）— 全局兜底，一次到位
4. 其余 🟡/🔵 按迭代节奏推进

> 注：本报告仅审查、不修改代码。如需我直接落地修复，告知即可。
