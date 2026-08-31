---
name: frontend-ui
description: >-
  loan-platform 管理端 UI 与交互规范。新增/修改管理端页面布局、表格、多页签、业务ID展示、时间排序、
  操作列、侧边栏菜单、固定列、表格高度时使用；违反将出现标签重叠/业务ID泄露/列表空白/文字截断等
  （用户多次反馈并逐条确认的硬性规范，2026-08-26 汇总）。
---

# 前端 UI 与交互规范（管理端）

## 何时使用

- 新增 / 修改 Web 管理端页面的布局、表格、标签页、排序、业务 ID 展示、侧边栏
- 改 Layout（侧栏菜单 / 顶部多标签）前必读
- 画表格列 / 操作列 / 时间列 / 金额列前必读
- **每次代码改动前先读本文 + `frontend-development` + `business-id` 三份规范**

## 一、多标签页（Tab）布局

**目标**：左侧主菜单 → 点击生成子 tab；切主菜单不覆盖已有 tab；不同主菜单 tab 可来回切换；可关闭 / 刷新当前 tab。

实现（已落地 `src/layout/Layout.vue`）：
- `openTabs: [{ path, title }]`，**持久化**到 `localStorage('loan:openTabs')`；刷新页面恢复
- 监听 `route.path` 自动 `ensureTab`；`keep-alive :include` 缓存已开页面状态（切换不刷新）
- `el-tabs` card 样式：点击 tab → `router.push`；`×` 关闭（工作台 `/workbench` 常驻不可关）；顶部刷新按钮强制重载
- 关闭当前 tab 时跳相邻 tab；关闭最后一个非工作台 tab 回工作台
- **tab 关闭按钮必须用内联 SVG**（`<svg class="tab-close" @click.stop="onTabClose">`），不要用 `<i class="el-icon el-icon-close">`（字体图标可能不渲染，用户反馈过无 × 按钮）

**规则**：
- 新页面必须能被 `findTitle` 通过 `menus` 或 `route.meta.title` 解析出标题
- 不要绕过 Layout 直接 `window.location` 跳转（会丢 tab 状态）
- keep-alive 页面用 `onActivated` 回显数据（如返回列表刷新）

## 二、业务 ID 展示规范（用户硬性要求：业务 ID 不在业务系统显示，只显示中文描述）

- 表格**主列只展示名称/描述**：工单 → 客户名/产品名；员工 → 姓名；报告 → 企业名/联系人；部门 → 部门名；星级 → A/B/C/D
- 业务 ID（`orderNo/leadNo/productCode/staffCode/templateCode/reportNo/approvalNo/rewardNo` 等）：
  - 可保留为次要列：宽度 ≤ 120、`show-overflow-tooltip`、小字/次要色
  - 或收进操作列「复制编码」按钮
- 搜索 / 筛选仍按业务 ID 传参（后端支持）
- 表单回显、详情抽屉中业务 ID 用 `mono` 小字展示，主位放名称
- **客户（企业）视角列**：企业名/联系人/手机号/企业星级为主列；客户编码为次要小字

## 三、表格操作列（防重叠，用户反馈过的 bug）

- 统一用 `AppTableActions`（`components/AppTableActions.vue`），**不要手写操作按钮**
- `maxInline` 默认 **2**：超过 2 个操作自动折叠进「更多」下拉（3 个操作 = 2 内联 + 更多）
- 容器强制 `white-space: nowrap`、gap 4px、字体 12px、分隔符 `·`——**禁止换行**
- 操作列宽度：1-2 操作 110-140；3+ 操作 **180**（不要用 200+）
- 确认类操作传 `confirm` 文案（AppTableActions 内置二次确认）
- **不要**在状态列堆叠多个 tag（如「正常 + 已入全量库」），会撑高行与操作列错位——状态列只放一个主 tag

## 四、时间列排序

- 时间列（createdAt/updatedAt/dealTime/sendTime/approvedAt 等）加 `sortable` 属性 + el-table 绑定 `@sort-change="handleSortChange"`（useTable 提供）→ 点击列头正/倒序，**跨页生效**（query.sortBy/sortDir 传给后端）
- 后端 page 接口必须支持 `orderBy/orderDir` 参数（走 `PageOrder` 白名单，见 `database-optimization/SKILL.md`）
- 时间显示统一 `formatDateTime(row.xxx)`（`src/utils/format.js`）
- **禁止**只加 `sortable` 不加 `@sort-change`（会退化为客户端当前页排序）

## 五、侧边栏菜单（三级折叠 + 按业务分组 + 样式规范）

**分组（业务合理规划）**：工作台（常驻，无分组标题）/ 客户经营（线索/工单/初筛/报表）/ 产品与规则（产品/规则/模板）/ 运营支撑（审批/短信/推荐奖励）/ 系统管理（组织/配置/黑名单/调试/审计）

**三级折叠**：
1. **主菜单分组折叠（NEW）**：每个分组可点击标题折叠/展开子菜单，`groupExpanded` 状态持久化 `localStorage('loan:menuGroupExpanded')`；折叠用 `v-show` + transition（max-height+opacity 0.22s）；箭头 `menu-group-arrow` 0.18s 旋转
2. **整体折叠**：`.sider.collapsed`（64px）——菜单项图标居中、分组折叠为图标堆叠（`.menu-group-collapsed`）、品牌 logo 居中、底部按钮/顶部按钮双向切换
3. **明细折叠**：分组内子项 v-show 折叠（同 1）

**样式规范**（用户要求：字样不要过大、边界清晰）：
- 菜单项 `font-size: 12.5px`、`letter-spacing: 0.2px`、`padding: 8px 12px`、`margin: 1px 8px`、圆角 7px
- hover 用 `color-mix(in srgb, var(--loan-primary) 10%, transparent)` 底 + 图标点亮主色
- 激活项用品牌渐变底（`linear-gradient(90deg, rgba(59,130,246,.22), rgba(59,130,246,.08))`）+ 白字 + 左侧发光指示条（`box-shadow: 0 0 8px rgba(59,130,246,.6)`）
- 分组标题：标签（11px 大写）+ 弹性细线 + ▼ 箭头，可点击
- 菜单来自后端 `/api/admin/org/menu/tree?roleCode=`（按角色动态），`t_menu.path` 与前端路由 path 一一对应
- 新模块：先在 `src/router` 注册路由 → 后端 `t_menu` 插入菜单 → 角色授权后可见
- **禁止**硬编码颜色（走 --loan-* 变量）

## 六、表格固定列透传 bug（用户反复反馈，2026-08-26 根治）

**根因**：Element Plus 2.6 固定列为 **sticky 定位的 td**（类名 `el-table-fixed-column--left/--right`，`background: inherit` 继承行背景），
而全局样式把 `--el-table-tr-bg-color` 设为 `transparent` → 横向滚动时下方行内容透过固定列显示（重叠透传）。
旧版 `.el-table__fixed-right` 容器选择器（EP ≤2.3）在 2.6 **全部失效**。

**修复（`src/styles/index.css`「表格固定列遮蔽」段，勿删）**：
- 用 `td.el-table-fixed-column--left/--right` 选择器显式背景：浅色 `#fff`、斑马纹 `#f9fafb`、hover 跟随 `--el-table-row-hover-bg-color`、暗色 `#131e33`（暗色斑马纹 `#0e1828`）
- 滚动阴影：`.el-table.is-scrolling-right td.el-table-fixed-column--right` 加 `--el-table-fixed-right-column` 阴影
- 表头固定列跟随 `--el-table-header-bg-color`
- **新增表格时**：操作列 `fixed="right"` 无需额外处理（全局已修）；若自定义背景色表格需同步覆盖固定列
- **禁止**再次把 `--el-table-tr-bg-color` 设透明且不补固定列背景

## 七、表格填充满容器（用户反馈：数据少时列表"未填充满"）

- **el-table 加固定高度**：`style="height: calc(100vh - 320px); min-height: 360px"`（表头固定 + body 滚动，数据少时表格区撑满容器不留大空白）
- 所有列表页（LeadPool/ReportCenter/OrderList/ProductList 等）统一按此规则
- 若页面结构不同（如顶部指标卡 + 表格），高度基准可微调（`calc(100vh - 320px)` 为含顶栏 60 + tabs 40 + 页头 80 + 搜索栏 60 + 分页 60 + padding 的通用值）
- **金额列防截断**：金额文本（`¥100,000.00`）列宽 ≥ **130**（110 会 ellipsis 截掉末尾 0，用户反馈"¥100,000.0"少 0）

## 八、数据中文化 + 星级评级 + 脱敏防御（用户硬性要求）

**列名与取值一律中文**：
- 禁止英文列名：`PASS/COND/REJ` → `通过/有条件/拒绝`（绿/黄/红小字分别展示）
- 状态取值中文 tag：`已生成/已查看/新线索/待审核` 等
- 评级用**企业星级**：grade 映射 `A·优质 / B·良好 / C·一般 / D·暂不推荐`（绿/蓝/黄/灰 tag）
- 客群/来源/跟进状态等枚举统一 `statusText`/`DictTag` 中文

**脱敏防御（format.js）**：
- `desensitizePhone` 必须校验 `/^\d{11}$/`——**非纯数字 11 位（如 AES 密文）直接返 `-`**，禁止对密文做脱敏（会显示乱码 `xt****pRc=`，用户反馈过）
- 展示手机号前确保后端已解密；前端只做格式兜底

## 九、响应式与布局边界

- 页面多板块（指标卡 + 趋势表 + 列表）用 `.loan-card` 卡片分隔，卡片间 `gap: 16px`，板块边界清晰
- 多 tab 页面（OrgCenter 员工/角色/接口权限）用 `el-tabs` 分隔
- `AppSearchBar` 搜索栏 + 表格 + `AppPagination` 分页为列表页标准三段式
- 移动端断点（≤900px）：侧栏折叠、双栏 grid 转单列（`.org-body` 已适配）

## 十、全局右键菜单（禁止 window.prompt/confirm/alert 做交互）

**背景**：tabs 右键曾用浏览器原生 `window.prompt`（"1=关闭其他/2=..."），视觉丑陋且用户无法接受。已改为全局可复用下拉菜单。

**用法**（任意组件，如表格行右键 / 卡片右键 / 菜单按钮）：
```js
import { openContextMenu } from '@/utils/contextMenu';
function onRowContextMenu(ev, row) {
  openContextMenu(ev, [
    { label: '查看详情', icon: '<svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.8"><path d="..."/></svg>', onClick: () => openDetail(row) },
    { divider: true },
    { label: '删除', danger: true, disabled: row.locked, onClick: () => del(row) },
  ]);
}
```

**实现**（已落地）：
- `src/components/AppContextMenu.vue`：全局唯一渲染实例（App.vue 挂载一次 `<AppContextMenu />`），Teleport 到 body，暗色卡片 + 圆角 + 阴影 + 0.14s 进入动画
- `src/utils/contextMenu.js`：`openContextMenu(ev, items)` / `closeContextMenu()` 响应式单例
- 菜单项字段：`label` / `icon`(SVG 字符串) / `danger`(红色) / `disabled`(禁用) / `divider`(分隔线) / `onClick`(点击后自动关闭)
- 关闭时机：点击外部 / ESC / 窗口失焦 / 点击菜单项后自动处理
- 视口边界保护：菜单超出屏幕自动左/上偏移

**规则（硬性）**：
- **禁止**任何 `window.prompt` / `window.confirm` / `window.alert` 出现在交互流程（弹窗、右键、操作确认）；确认类用 `utils/confirm.js`（ElMessageBox），选择类用本右键菜单或 el-dropdown
- 右键菜单必须走 `openContextMenu`，不要在各页面内联复制菜单 DOM
- icon 必须内联 SVG（14x14 stroke 风格），与全站一致
- 模板引用状态名必须与 script setup 定义一致（历史 bug：模板写 `state`、script 定义 `contextMenuState` → 组件渲染失败，页面空白）

## 自检清单（改完必过）

- [ ] 表格是否直接暴露业务 ID 主显？→ 改名称列 + ID 列缩窄 ≤120/tooltip/复制按钮
- [ ] 客户/企业列表是否展示企业名/联系人/手机号/星级？编码是否次要小字？
- [ ] 列名/取值是否有英文（PASS/REJECT/SUBMITTED 等）？→ 改中文
- [ ] 操作列是否手写按钮 / 宽度 >180 / 可能换行？→ 用 AppTableActions + 180
- [ ] 时间列是否加 `sortable` + `@sort-change`？时间展示是否统一 `formatDateTime`？
- [ ] 金额列宽是否 ≥130？金额是否统一 `formatMoney`/`fmtAmount`（两位小数）？
- [ ] 手机号是否用 `desensitizePhone`（带 `/^\d{11}$/` 校验）？密文是否返 `-`？
- [ ] 表格是否加 `height: calc(100vh - 320px)` 填充满容器？
- [ ] 新页面是否在 Layout menus / router meta.title 有标题？tab 是否自动开？
- [ ] 表格用了 `fixed="right"`？→ 确认全局固定列背景修复未被覆盖
- [ ] 是否有 `window.prompt/confirm/alert`？→ 改 `openContextMenu` / `ElMessageBox`（utils/confirm.js）
- [ ] 改 Layout 是否跑 `vite build`？折叠态（整体 64px + 分组折叠）是否适配？
- [ ] `<script setup>` 里用到的 Vue API（ref/reactive/computed/watch/onMounted/onBeforeUnmount/nextTick 等）**必须 import 完整**——历史上 RuleList 漏 import `reactive` 导致页面空白，错误信息只有"setup error"无具体行号
- [ ] dict store 是否已 loaded？使用 DictTag 的页面要么 store 已 loaded 要么有 localStorage 同步缓存（启动即可解析中文）
- [ ] **el-table 列总宽 > 容器宽 + fixed="right" 时**：Element Plus 2.6 不会创建 fixed-right wrapper（因为 scrollWidth === clientWidth），但 td 仍打 `el-table-fixed-column--right` className（z-index:1）→ 主表格 horizontal scroll 时 fixed 列与其他列位置错位，**视觉上"操作列覆盖其他列"**。三选一修法：
  1. **去掉 fixed="right"**（最稳，列参与 horizontal scroll）—— Reward 列表 10 列就是这种修法
  2. 缩列宽让列总 ≤ 容器宽（≤ 976px），让 EP 创建 fixed wrapper
  3. 全局 CSS 兜底（已在 styles/index.css 末尾加）：`td.el-table-fixed-column--right { position: static; z-index: auto }`
- [ ] 列总宽 > 容器宽时**el-table 不要加 `min-width: 1370px` 这类绝对值**——会撑破整个 layout（实测 el-table 1370 + content 1060 = 撑出 310px）。正确：width: 100% + overflow-x: auto + 缩列宽到列总 ≤ 1000
- [ ] **列表页表格填充满**：`[class$="-page"]`（lead-page / order-page / audit-page 等所有 *-page 类）必须 `display:flex; flex-direction:column; flex:1; min-height:0`，.loan-card flex:1，el-table flex:1 + min-height:0。**全站 10 个页面已实测填充满**（178-232px）
- [ ] **el-table "操作列"被压缩成两行**（fixed-right 不生效）：EP 2.6 在列总宽 ≤ bodyWrapper 宽时不创建 fixed-right wrapper，但 td 仍打 `el-table-fixed-column--right` className → fixed 列被压。**手动修法**（不依赖 EP wrapper 创建）：
  1. bodyWrapper/headerWrapper `width: 100% !important; overflow-x: auto !important` → table 内部按列总宽撑出 horizontal scroll
  2. "操作"列 `position: sticky !important; right: 0 !important; z-index: 3 !important; background: var(--el-bg-color) !important; box-shadow: -2px 0 4px rgba(0,0,0,0.06)` → horizontal scroll 时"操作"列固定在视口右
  3. 实测 ProductList（9 列 1320 > 容器 976）："操作"列 180 宽 sticky 在右，其他列水平滚可看
- [ ] **卡片内容溢出**（如 ScreeningCenter "查看报告"按钮被挤出视口）：根因是 flex space-between + 长字符串（reportNo）撑出父容器。**三层兜底**（styles/index.css 末尾）：
  1. `[class$="-page"] > .loan-card { overflow: hidden }` —— 任何子元素不能撑出 card
  2. `[class$="-page"] .el-card, [class$="-page"] .el-form { max-width: 100%; min-width: 0 }` —— form/card 自身不撑出
  3. flex 容器内长文本用 `flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap`（按钮用 `flex-shrink: 0`）
- [ ] **无障碍关键项(WCAG A/AA)必须一次到位**（2026-08-26 已修）：
  1. menu-group-title 加 `role="button" tabindex="0" :aria-expanded @keyup.enter @keyup.space.prevent`
  2. sider-foot 加 `role="button" tabindex="0" aria-label :aria-expanded @keyup.enter @keyup.space.prevent`
  3. 浅色主题激活菜单字色改为 `var(--loan-primary)`(主色深蓝 AA 达标),深色主题 `:root[data-theme="dark"]` 保持 `#fff`
  4. Layout.vue 加 `<a href="#main-content" class="skip-link">跳到主内容</a>` + `<main id="main-content">`,CSS `position:absolute; left:-9999px`,`:focus { left:8px; ... }`
  5. styles/index.css 加 `@media (prefers-reduced-motion: reduce) { *, *::before, *::after { animation-duration: 0.001ms !important; transition-duration: 0.001ms !important; scroll-behavior: auto !important; } }`
  6. menu-item min-height 38→44px;tabs-refresh 28→36px
  7. AppTableActions.vue 下拉触发器 span 加 `role="button" tabindex="0" :aria-haspopup="true" :aria-expanded="false"`
  8. Login.vue SSO 模式 password rules `required: false`(消除与 placeholder"暂不校验"的矛盾)
  9. AppTableActions.vue onAction 加 `if (a.success) ElMessage.success(a.success)` 支持轻操作成功反馈
- [ ] **体验 P1 必检项（2026-08-26 Batch4 已修）**：
  1. **空状态插画组件 AppEmpty**（components/AppEmpty.vue + main.js 全局注册 `<AppEmpty>`）：
     ```vue
     <el-table :data="data">
       <template #empty>
         <AppEmpty title="暂无XX" desc="说明 + 操作引导">
           <el-button type="primary" size="small" @click="create">新增</el-button>
         </AppEmpty>
       </template>
     </el-table>
     ```
  2. **骨架屏 AppSkeleton**（loading && !data.length 时显示，替代 v-loading）：
     ```vue
     <AppSkeleton v-if="loading && !data.length" :rows="6" :cols="8" />
     <el-table v-else ...>
     ```
     内置 `prefers-reduced-motion` 兜底（关动画）
  3. **表格行高 44px** 全局：`td.el-table__cell { padding: 13px 0 }` + `th { padding: 11px 0 }`
  4. **删除撤销 AppTableActions**：
     ```js
     { key: 'del', label: '删除', type: 'danger',
       confirm: '确认删除?',
       onClick: () => onDelete(row),
       undo: () => recover(row),         // 5s 内可点撤销
       undoMessage: '已删除（5s 内可撤销）' }
     ```
     AppTableActions 内部用 h() 渲染 ElMessage 带"撤销"链接，duration 5000
  5. **折叠态菜单 tooltip**：menu-item 用 `<el-tooltip :content="item.title" placement="right" :disabled="!collapsed">` 包裹；trigger 加 CSS `display:flex; width:100%`
- [ ] **体验 P2 + 无障碍建议（2026-08-26 Batch5 已修）**：
  1. `.loan-page-title` font-size 18→24px font-weight 600→700（层级跳跃 2x）
  2. `--loan-text-muted` 浅色 `#6b7280` → `#5b6470`（WCAG AA 对比度更稳 ≈5.5:1）
  3. Overview.vue statCards 加 delta 字段（`{label, value, delta}`）+ `stat-delta` 绿涨红跌标签
  4. AppTrendChart.vue 零依赖 SVG 折线图（不引入 echarts），TrendAnalysis 接入
  5. ScreeningCenter 表单 label 已有单位（"年纳税额(元)"等），无需改
  6. DictTag 外层 span 加 `role="img" :aria-label="状态：${text}"`
  7. ThemeSwitch 按钮加 `:aria-pressed="isDark ? 'true' : 'false'"`
  8. el-breadcrumb 加 `aria-label="面包屑导航"`
  9. 404/403 错误页（views/error/NotFound + Forbidden.vue）+ router catchAll + /403 路由
  10. 动态面包屑：Layout currentTitle 优先 `route.meta.title`（支持子页面如 /report/overview）
  11. metric-grid gap 16→20px（卡片呼吸更舒展）
- [ ] **el-table 表头/数据列错位修复（关键）**：
  - **根因**：.el-table__body-wrapper > .el-scrollbar > .el-scrollbar__view 比父容器窄 15px（EP 默认给滚动条预留空间），导致 body table 比 header table 早 15px → **每列累积 5-6px 错位**，最后一列 offset 高达 41px
  - **修复**（styles/index.css 全局规则）：
    ```css
    .el-table__body-wrapper .el-scrollbar__view,
    .el-table__header-wrapper {
      width: 100% !important;
      box-sizing: border-box !important;
      padding-right: 0 !important;  /* 覆盖滚动条预留 15px */
    }
    .el-table .el-table__header-wrapper table,
    .el-table .el-table__body-wrapper table {
      width: 100% !important;
      margin: 0 !important;
    }
    ```
  - **实测**：8 列 offset 从 [0/-6/-11/-18/-22/-27/-32/-41] → 全部 0px
- [ ] **侧栏菜单主菜单 vs 子菜单视觉层级（2026-08-26 已修）**：
  - **层级区分**:主菜单(分组标题)与子菜单(叶子项)风格统一但视觉层级清晰
  - **子菜单缩进**:`.menu-group-items .menu-item { padding-left: 32px; margin-left: 4px; font-size: 13px; font-weight: 400 }`
  - **子菜单图标缩小**:`.menu-group-items .menu-icon svg { width: 14px }` (vs 主菜单 18px)
  - **左侧连接线**(树形视觉):`.menu-group-items .menu-item::after { content:""; position:absolute; left:18px; width:6px; height:1px; background:var(--loan-border) }`
  - **激活态**:连接线变主色 + 加宽 + 发光（`.active::after { width:8px; box-shadow:0 0 4px primary }`）— 用 ::after 不冲突激活竖条的 ::before
  - **结果**:分组标题(主)600 字重 + 小方块图标；子菜单(子)400 字重 + 缩进 + 小图标 + 连接线——一眼看出层级
- [ ] **拆分 ReportCenter 多功能到子页面**（避免单页 4+ section 撑出）：
  - Layout.vue menuGroups 加新分组"报表分析"含 3 子菜单（数据概览/初筛报告/趋势分析），同时从原"客户经营"分组删除"报表中心"item
  - router/index.js 删 /report，加 3 个新路由 + /report redirect 到 /report/overview
  - 复制 ReportCenter.vue 到 3 个新页面（Overview/ScreeningReport/TrendAnalysis），每个只保留相关 section
  - **拆分时必检**：
    1. **删 `@sort-change="handleSortChange"`**（如未定义）或**定义 handleSortChange**——未定义会 Vue warn 并影响渲染
    2. **加 `onMounted(() => loadS())`**——useTable 不自动加载，缺了页面空
    3. **删 ReportCenter.vue 旧文件**——避免路由混乱
- [ ] **报告接口权限问题**（拆分后页面空"暂无数据"）：**报告接口（screeningPage/screeningDetail/overview/orderTrend/rewardTrend）的 clientTypes 在权限表里只有 WEB,MINI_APP 不含 ADMIN-WEB** → 管理端 403 拦截 → axios 拦截器吞错 → 表格"暂无数据"。**修复**：用 `updateClientTypes` API 给相关接口加 ADMIN-WEB：
  ```bash
  curl -X POST /loan/api/admin/api-perm/client-types -d '{"apiKey":"report:screeningPage","clientTypes":"ADMIN-WEB,WEB,MINI_APP"}'
  ```
  备注：`clientTypes` 参数是**逗号分隔 String**（不是数组），我之前传数组 5000 错误
- [ ] **el-table "操作列右+行下方的大白方块"修复**（重要）：
  - 根因：EP 2.6 `.el-table__body-wrapper` 默认 `flex: 1 1 0%` + el-table 容器 flex:1 撑满 → bodyWrapper 撑出容器高（如 320px），但内部 `.el-table__body` 只有 1 行（43px）→ **277px 空白显示为"白方块"**
  - 修法（styles/index.css 全局规则）：
    ```css
    .el-table__body-wrapper {
      flex: 0 0 auto !important;
      height: auto !important;
      min-height: 0 !important;
    }
    [class$="-page"] > .loan-card > .el-table {
      min-height: 0 !important;
      height: auto !important;
      margin-bottom: 12px;
    }
    ```
  - 数据少时 el-table 容器按内容自适应（83px = head 40 + body 43），下方是分页栏 + 页面自然留白，**没有突兀的白方块**
- [ ] **el-dialog 弹窗"确定"按钮左侧黑色矩形**——根因是 **.el-dialog__footer 自身带深色背景**（dark=#0e1828 / light=#fff），text-align:right 让按钮靠右 → footer 左侧大片空白显示深色背景 = "黑块"。全局修法（styles/index.css 末尾）：
  ```css
  .el-dialog__body { padding-bottom: 0; }
  .el-dialog__body .el-form { margin-bottom: 0; }
  .el-dialog__body .el-form-item:last-child { margin-bottom: 0; }
  .el-dialog__body .el-form-item { margin-bottom: 18px; }
  .el-dialog__footer {
    background: transparent;     /* 关键：去掉 footer 自带背景 */
    padding: 16px 24px;         /* 顶部 16px 间距（让按钮不贴 form）+ 左右 24px */
    text-align: right;
  }
  .el-dialog__footer .el-button + .el-button { margin-left: 8px; }
  ```
  **只看 gap 数据修不彻底**——必须看 footer 背景色。实测：bg rgb(14,24,40) → rgba(0,0,0,0) 后黑色矩形消失。
- [ ] **弹窗内 el-select dropdown "吐舌头"** = dropdown 默认 placement=bottom-start 在弹窗底部展开时**向下突破弹窗边界**，且 dropdown 自身透明无边框。修法：
  1. 给 el-select 加 `:placement="'top-start'"` 强制向上展开（LeadPool.vue 已加）
  2. 全局加 `.el-select__popper.el-popper, .el-select-dropdown { border + box-shadow + background }`（实测 EP 2.6 dropdown className 不含 .el-popper，但 EP 默认样式自带边框所以视觉正常）
  3. **不要相信 EP 的"自动 placement 翻转"**——弹窗内空间足够时 dropdown 不会自动 top，必须显式 placement="top-start"

## 相关文档

- `../frontend-development/SKILL.md`、`../frontend-components/SKILL.md`
- `../business-id/SKILL.md`（业务 ID 后端规范，前端展示必须对齐）
- `../gateway-auth/SKILL.md`（接口鉴权，前端请求带 X-Client-Type 头）
