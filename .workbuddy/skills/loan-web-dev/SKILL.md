---
name: loan-web-dev
description: >-
  loan-main Web 管理端开发规范。编写或修改 Vue 3 + Element Plus 管理端页面、按功能分包、
  公共 utils 提取、路由与动态菜单、权限指令、request 拦截器时使用；涵盖目录结构、
  路由菜单规则、菜单级/操作级/数据范围三级权限控制与 tse-frontend 教训。
---

# Web 管理端开发规范（loan-web-dev）

## Step 0 · 前置门禁（强制，不可跳过）

1. **先查历史结论**：`grep -n "前端\|页面\|路由\|菜单\|权限" docs/knowledge-base/10-历史结论与决策日志.md#结论台账`，
   从**最新条目往下**读（台账按时间倒序，最新在最上方）；**命中即遵守**；状态为「已被 Dxx 替代」则跳读 Dxx。
   > ⚠️ 取号前必须自己刚跑 `grep -o "^| D[0-9-]*" docs/knowledge-base/10-历史结论与决策日志.md` 取实时最大编号（禁采信转述 / 记忆 / 分配表）。
2. **无结论且不确定 → 停下来问用户**，禁止臆断。
   > ⚠️ **分工（D83 已更新，取代 D0-1）**：自 2026-10-20 起，**loan-web UI 缺陷修复已授权助手**（后端仍以助手为主）。
   > 改前端仍须先过本门禁并按用户指定范围改动；用户发截图反馈时**必须请其用文字补现象，禁止猜测截图内容**（助手读图会被过滤）。
3. **再读元技能** `loan-knowledge`（`.workbuddy/skills/loan-knowledge/SKILL.md`），按其 Step 1–5 执行。
4. 回复开头输出：`【结论核对】命中 Dx-x（…）/ 未命中（grep 关键词：…）`。

## 何时使用

- 新增或修改 Web 管理端（Vue 3 + Element Plus）模块页面
- 编写公共组件 / 工具方法 / 路由 / 权限指令前 —— **先读本文件**
- **每次代码改动前交叉必读**：
  - 分层复用、接口契约、并发请求、名称主显与编码降级 → **必读** `loan-code-standard` 的 `references/frontend-standard.md`
  - UI / 布局 / 表格 / 多页签 / 排序 / 侧边栏 / 固定列 / 中文化 → **`loan-web-ui`**（用户逐条确认过的硬性 UI 规范）
  - 用公共组件 / `useTable` / `appConfirm` / `v-permission` → **`loan-web-components`**
  - 业务 ID 展示与接口字段 → **`loan-biz-id`**
  - 请求被网关 401/403 拦截 → **`loan-gateway-auth`**

## 核心原则：按功能分包 + 提取 utils + 统一工具

### 禁止

- ❌ 页面内重复的格式化 / 脱敏 / 金额 / 状态映射逻辑（各写一份）
- ❌ 硬编码文案 / 状态值 / 字典（魔法值散落）
- ❌ 裸 `:visible.sync`（Vue 2 语法）、`center: true` 的 `appAlert`（布局错乱）
- ❌ 改 `<script>` 不同步删 `<template>` 引用（整页白屏）
- ❌ 父级菜单挂 `component`、同一 `path` 挂两个父菜单、重复 path
- ❌ 无注释组件 / 无 props 说明 / 无事件说明

### 应该

- ✅ **按功能分包**：`views` 按模块分包（客户 / 线索 / 工单 / 奖励 / 短信 / 组织权限 / 产品…），
  组件、工具、API 分层；页面命名与菜单路由一致
- ✅ **公共方法提取 `src/utils`**：日期格式化、手机号脱敏、金额格式化、状态映射、字典转换统一进 utils
- ✅ **前端工具类**：request 拦截器（token / traceUuid / 错误处理）、`formatDateTime` / `AppDateTime`、
  `appConfirm`、`AppPagination`、`v-permission` 指令
- ✅ **注释与后端一致**：组件 / 方法注释、props 入参说明、事件说明；代码简洁、不硬编码
- ✅ **遵循 tse-frontend 教训**：el-* 统一、父级仅 `redirect` 叶子挂 `component`、keep-alive 用 `onActivated` 回显

## 扩展点模式

### 目录结构

```
src/
├── views/            # 按模块分包（每个子菜单 path 一个页面）
├── components/       # 可复用组件（AppPagination、AppDateTime、CustomerFollowDialog…）
├── utils/            # 公共方法（formatDateTime、desensitize、money、statusMap、dict…）
├── api/              # 接口封装（按域拆分）
├── router/           # 动态路由 + 菜单树
├── stores/           # Pinia 状态
└── directives/       # 权限指令 v-permission
```

### 路由与菜单

- 动态路由：登录后按角色拉菜单树生成侧栏与路由
- 父级菜单仅 `redirect`，叶子才挂 `component`；`el-menu-item :index` 用菜单 id（非 path）
- Tab 多页签：每个子菜单 path 一个 Tab；顶栏标题用最长前缀匹配

### 权限控制（三级）

| 层级 | 手段 |
|---|---|
| 菜单级 | 无菜单无路由，URL 越权拦截 |
| 操作级 | `v-permission` 指令控制新增 / 认领 / 指派 / 审批 / 导出 / 解禁按钮 |
| 数据范围 | 列表后端按部门 + 角色过滤，前端**只做展示辅助**，不做安全边界 |

## 契约红线速查

- **契约真源**：`db/loan-db-schema.sql`（**表数以 `grep -c "CREATE TABLE" db/loan-db-schema.sql` 为准，不写死数字**，规则 9）→ `loan-service` 代码 → `docs/knowledge-base/`
- **禁止引用** `前端交互逻辑蓝图.html` / `后端逻辑蓝图.html` / `output/`（均不存在或已删除，见 `loan-knowledge`）
- **审批入口真值（D60）**：`ALLOCATION` 审批按钮对 `DEPT_MANAGER` 可见但后端仅允许本人团队；渠道 `PRODUCT` 页签/按钮仅 `BOSS/SUPER_ADMIN/SUPER`；`DOWNLOAD/MATERIAL_REVIEW` 可包含 `DEPT_MANAGER/OPERATOR`。
- **所有请求经网关**：请求头带 `X-Client-Type: WEB`，接口未登记会被 403（见 `loan-gateway-auth`）

## 自检清单（改完必过）

- [ ] Step 0 结论核对是否已输出？
- [ ] 是否有重复的格式化 / 脱敏 / 金额 / 状态逻辑？→ 提取到 `src/utils`
- [ ] 是否有硬编码文案 / 状态值 / 字典？→ 走配置或常量
- [ ] 是否用了 Vue 2 语法（`:visible.sync`）？→ 改 `v-model`
- [ ] 改 `<script>` 是否同步删 `<template>` 引用？是否跑过 `npm run build`？
- [ ] 父级菜单是否只挂 `redirect`？叶子才挂 `component`？path 是否唯一？
- [ ] 组件 / 方法是否有注释？props / 事件是否有说明？
- [ ] keep-alive 场景是否用 `onActivated` 拉库回显？
- [ ] 请求是否统一走 request 拦截器并带 `X-Client-Type`？
- [ ] 数据范围过滤是否由后端完成（前端不做安全边界）？
- [ ] 用户、企业、产品、部门等关联数据是否名称主显，内部 ID / 业务编码仅作必要的次要信息？
- [ ] 名称是否由后端批量补齐，前端无逐行查询名称的 N+1 请求？

## 实战坑位清单（2026-10-20 沉淀，改代码前必扫）

- **弹窗内下拉用 `DialogSelect`，禁 `:teleported="false"`**：popper 塞进 `.el-dialog__body` 会被其 overflow 裁掉，且 body 滚动时 popper 位置不重算 → 表现为"下拉被隐藏 / 随滚动折叠"。统一走 `components/DialogSelect.vue`（挂 body + `fit-input-width` + 列表 max-height 240 内部滚动 + flip/preventOverflow）。同一控件绝不能出现"塞进弹窗 vs 放出去"两套矛盾实现。
- **日期控件 `AppDateTime` 缺省宽度已按 type 定死**（date 200 / datetime 220 / daterange 260 / datetimerange 360），根元素带 `flex:0 0 auto`。**筛选条里不要漏传也不会铺满**；只有表单内（el-form-item）需要铺满时才显式 `width="100%"`。别再在页面 style 里用 `:deep` 覆盖日期宽度（与组件 prop 打架）。
- **图标名必须真实存在**：`icons.js` 里没有的名字（`setting`、`success`、`sea` 等）会让 `AppIcon` 静默渲染空白，不报错。新增/改菜单 icon 前 grep 确认；`setting`→`settings`、`success`→`check`、`sea`→`layers`。
- **`useTable` 的 query 会自动透传为请求参数**，但**后端不接收的参数就是假筛选**（前端加了"筛了无效"）。加筛选前先核对后端 Controller 是否 `@RequestParam` 接收；后端没加就联动后端。
- **chip/计数与列表过滤必须同口径**：标签 chip 的 count 与点击后列表的 where 条件若口径不一致（如计数含 NULL 而列表精确匹配），会出现"计数 1、点进去 0"。
- **后端哨兵值不要裸显**：`remaining=-1` 等"不限/豁免"语义的哨兵值，前端展示前要判断（`< 0` → 显示"不限次数"），别直接打在 UI 上。
- **`Layout.initializeLayout` 先恢复标签再查权限**：顺序反了会导致"刷新任何非工作台页面都被弹回工作台"（权限兜底判断时 openTabs 还没从 localStorage 恢复）。
- **表格只有一列弹性（min-width）时，它会吸收全部剩余宽度**，观感就是"某列占了大半屏"。多个相关列一起 `min-width` 分摊，短字段固定 `width` + `show-overflow-tooltip`，操作列 `fixed="right"`。
- **错误分支按后端 `code` 判断，禁匹配中文文案**：`e.message.includes('跨团队')` 一类写法在文案变化 / 异常包装后即失效，落到静默兜底（用户只见"系统错误"）。后端业务异常会返回明确 `code`，前端 `e.code === 1005` 这类判断才稳。
- **审批/写操作必须防重**：后端用 CAS（`eq(status,PENDING)` 更新 0 行抛错）做幂等，前端连点/列表未刷新导致的重复提交会让后续请求报「审批单不存在或已处理」。单条/批量审批按钮都要加 loading 或 auditing 标志，请求进行中禁止重复触发。
- **审批类列表要带状态筛选**：待审列表若后端只返回 PENDING，审批后数据"消失"；前端每个审批 tab 都要有「审批状态」下拉（待审/已通过/已驳回），联动后端 `status` 参数，别只给部分 tab 加。
- **客户列主行企业名 + 副行联系人（企业/个人客群差异）**：服务域与客户列表的「客户」列，主行显示 `enterpriseName || contactName`（企业客群显企业名，个人客群显联系人），副行补 `联系人：{{contactName}}`（企业客群时）或脱敏手机号。后端 DTO 已补 `enterpriseName`/`contactName` 并由列表合并一次客户查询返回，前端**不得**逐行调接口查客户名称（N+1）；若接口未返回这两个字段，先联动后端补 DTO，不要前端自己查。

## 相关文档

- `docs/knowledge-base/10-历史结论与决策日志.md#结论台账`（**Step 0 必查**，D0-1 分工红线）
- `.workbuddy/skills/loan-code-standard/references/frontend-standard.md`（复用、契约与可理解展示唯一标准）
- `docs/knowledge-base/02-业务红线与编码规范.md#编码规范`
- `docs/knowledge-base/01-角色权限模型.md#7 角色体系（必须严格区分）`
- `docs/knowledge-base/04-后端 API 契约.md#前后端契约变更纪律`
- `docs/knowledge-base/04-后端 API 契约.md#通用约定`
- `docs/knowledge-base/08-小程序角色功能矩阵.md#结论`
- `docs/plans/archive/ui-design-spec.md`（设计令牌与视觉规范）
- `docs/plans/archive/方案评审定稿纪要.html`（契约基线：第 12 章 Web 模块、第 24 章前端规范；表数以 `db/loan-db-schema.sql` 为准）
- 参考 tse-frontend：`/Users/admin/Documents/crm/tse-frontend`
