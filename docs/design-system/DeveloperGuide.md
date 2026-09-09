# DeveloperGuide · 墨金 Ink & Gold 接入指南

> 给 `loan-web` 与 `loan-mini` 开发者的落地说明。目标：用统一设计系统 + 原创图标替换现有"三套图标来源 + 散落内联 SVG + 硬编码色值"。

## 一、文件清单（本目录）
- `DesignSystemManifest.md` — 令牌与规范（唯一权威）
- `ComponentSpec.md` — 组件与状态
- `QAReport.md` — 质量检查
- `code/icons.js` — 36 枚图标路径（**单一事实来源**）
- `code/AppIcon.web.vue` — Web 端图标组件
- `code/AppIcon.mini.vue` — Mini 端图标组件
- `prototype/design-showcase.html` — 可交互预览（含令牌板/图标库/双端原型）

## 二、Web 管理端（loan-web / Vue3）
1. **放置组件**：把 `code/AppIcon.web.vue` 与 `code/icons.js` 放到 `src/components/`（如 `src/components/AppIcon.vue`、`src/components/icons.js`）。全局注册或在用到的页面局部 import。
2. **替换散落内联 SVG**：检索 `src/**/*.vue` 中的手写 `<svg ... stroke-width="1.7/1.8/2/2.4">`，一律改为 `<AppIcon name="xxx" :size="18" />`。尺寸/线宽不再散写。
3. **替换 Element Plus 内置图标**：如 `el-alert show-icon`、按钮里的 `@element-plus/icons-vue`，尽量改用 `<AppIcon>` 以统一风格（保留 EP 图标仅限其组件强耦合处）。
4. **着色随主题**：`<AppIcon color="var(--loan-primary)" />` 即可随 runtime 换肤；把 `src/styles/index.css` 中硬编码 hex 逐步替换为 `--loan-*` 令牌。
5. **图标名映射**（旧 `AppIcon.vue` 的 45 枚 → 新统一名，示例）：
   - 工作台 `workbench` → `dashboard`；线索 `lead` → `client`；审批 `approval` → `approval`；报表 `report` → `report`；趋势 `trend` → `trendUp`；锁 `lock` → `lock`；时钟 `clock` → `clock`；加 `add` → `plus`。

## 三、小程序端（loan-mini / uni-app）
1. **放置组件**：`code/AppIcon.mini.vue` + `code/icons.js` 放到 `components/`（与现有 `AppIcon.vue` 共存或替换）。
2. **关键修复——颜色脱钩**：因小程序 SVG `stroke` 不解析 `var()`，旧实现把 TabBar 等写死 `#2443C2`。新组件在**运行时注入真实色值**：
   ```js
   // 维护一份与 CSS 令牌同步的真实色值（改主题只改这里）
   const THEME = { ink:'#16203A', gold:'#2443C2', muted:'rgba(26,35,54,.55)' };
   // 使用：<AppIcon name="home" :color="active? THEME.gold : THEME.muted" :size="22" />
   ```
   换肤时只需更新 `THEME` 真实值，TabBar/列表图标即联动，**不再有写死的 `#2443C2`**。
3. **替换旧 v8 实现**：用新 `AppIcon.mini.vue` 覆盖现有 `components/AppIcon.vue`；原有 `static/icons/preview.html` 为失效 PNG 稿（PNG 不存在），可删除。
4. **图标大小/线宽**：统一 22–28px、线宽 1.75，不再混用。

## 四、统一令牌落地
- Web：在 `src/theme/index.js` 的 `--loan-*` 基础上，增列 `--gold-500` 等，保证与 Mini `--brand-*` 语义对齐（可建一份 `tokens.json` 双端共享）。
- Mini：在 `App.vue` 的 `page{}` 令牌中补充 `--gold-*`；`THEME` 真实值与之一致。

## 五、验收
- 全站图标来自 `AppIcon` 单一来源，无内联散落、无 emoji。
- 明暗/换肤时 Web 图标随 `var()` 变化，Mini 图标随 `THEME` 真实值变化。
- 无硬编码与令牌重复的 hex（除一次性主题注入点）。

## 六、新增图标流程
1. 在 `icons.js` 的 `ICON_PATHS` 按 24×24 / 线宽 1.75 / round 规范新增路径。
2. Web、Mini 组件无需改动（自动读取）。
3. 命名语义化英文，更新 `ComponentSpec` 图标清单（如需）。
