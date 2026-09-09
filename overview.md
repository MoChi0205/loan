# 贷款系统前端 · 墨金 Ink & Gold 主题重构（实现进度）

## 本次完成（可直接预览）

### 一、loan-web 管理端（已验证）
- 主题引擎保留：`src/theme/index.js` 运行时注入 CSS 变量 + `ThemeSwitch.vue` 一键切换 + localStorage 持久化。
- 墨金色板落地：`primary` 暖金 `#D9A441`、`accent` 信息蓝 `#4C7BD9`；`src/styles/index.css` 静态令牌 + Element Plus 暗色覆盖 + 主按钮「暖金底/深墨蓝字」对比度修正。
- 图标统一：`src/components/icons.js`（36 枚单一来源）+ `AppIcon.vue` 接入 PATHS 分支；`Layout.vue` 8 处内联 SVG 收敛为 `<AppIcon>`；`RewardList.vue` 修复 `:global(html.dark)` → `:global([data-theme="dark"])`。
- 预览：**http://localhost:5180/**（dev server 运行中，HTTP 200）

### 二、loan-mini 小程序端（本轮全部完成，含真机标记）
新建/改造：
- `theme.js`：明暗双主题模块，`getThemeMode / setThemeMode / toggleThemeMode / onThemeChange / applyThemeOnLaunch`，持久化 `uni.setStorageSync('loan_theme_mode')`；**新增模块级单例 `useThemeMode()`（响应式 ref），所有页面/组件共享同一主题，一处切换全局同步**。H5 端把 `data-theme` 写到 `<html>` 与 `.uni-page-body`。
- `App.vue`：补齐 `import { applyThemeOnLaunch } from './theme';`；暗色令牌选择器由 `page[data-theme="dark"]` 改为通用 `:root[data-theme="dark"], [data-theme="dark"]`；新增全局 `.theme-root{ min-height:100vh; background:var(--bg-page) }` 辅助类，保证暗色背景铺满（小程序 `page` 元素本身无法在模板接收 `data-theme` 属性）。
- **13 个 `pages/*.vue` 根视图统一加 `theme-root` + `:data-theme="themeMode"`，script 注入 `import { useThemeMode } from '../../theme'; const themeMode = useThemeMode();`** —— 这是「小程序真机标记」的落地，使 `[data-theme="dark"]` 在 mp-weixin 端也能命中。（order/detail 无 store 导入，单独处理。）
- `components/AppIcon.vue`：默认色随主题变化（浅底 `#1A2336` / 暗底 `#E8EDF5`），SVG `stroke` 不解析 `var()` 故运行时注入真实色；修复其主题导入路径 `./theme` → `../theme`（此前全量构建失败的根因）。
- `components/TabBar.vue`：未选中/选中配色随主题（暗底选中转暖金 `#E0AE4E`），背景改 `var(--bg-card)`。
- `pages/mine/mine.vue`：一键切换主题开关（改用 `useThemeMode` 单例）；`.card` 背景改 `var(--bg-card)`。
- 预览：**http://localhost:5173/**（H5 dev server 运行中，HTTP 200；「我的」页点开关实时切换明暗）。`npm run build:h5` 生产构建已通过。

### 三、待办（交给另一 AI / 下一轮，未在本会话实现）
1. ~~微信小程序端 page 元素标记（mp-weixin 真机）~~ —— **已完成**：见上「13 个 pages 根视图」。真机需 `npm run build:mp-weixin` 后导入微信开发者工具预览。
2. 其余上游 TODO（见 `docs/design-system/待处理事项.md` / `TODO.en.md` / `AI-TASK-BRIEF.md`）：散落内联 SVG 全量收敛、AppButton/AppSearchBar 等组件暗色适配、各业务页暗色巡检——保持为待办，留给另一 AI 按清单执行。

## 验证方式
- Web：浏览器打开 http://localhost:5180/，用右上角主题切换验证暖金深墨蓝双主题。
- Mini（H5 代理预览）：浏览器打开 http://localhost:5173/，进入「我的」页，点「主题模式」开关，整页（含 TabBar、图标、卡片）实时切换浅色/暗色（墨金）。
- Mini（真机）：`npm run build:mp-weixin` → 微信开发者工具导入 `dist/build/mp-weixin` 预览暗色效果。
