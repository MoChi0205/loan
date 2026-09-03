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
   > ⚠️ **分工红线（D0-1）**：前端（loan-web / 小程序）由**用户自己**负责，助手默认只做后端 `loan-service`。
   > 助手被明确要求改前端时，仍须先过本门禁并按用户指定范围改动，不得顺带扩大改动面。
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
- **审批入口真值（D39 覆盖 D0-4）**：`ALLOCATION` 审批按钮对 `DEPT_MANAGER` 也可见可点，但后端仅允许本人团队；跨团队由 BOSS 等上级审批。`PRODUCT` / `DOWNLOAD` 同样包含 `DEPT_MANAGER`。
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
