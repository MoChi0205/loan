---
name: frontend-development
description: >-
  loan-platform 前端开发规范。编写或修改 Vue 3 Web 管理端（Element Plus）与 uni-app 小程序页面时使用；
  涵盖按功能分包、公共方法提取 utils、前端工具类、注释与后端一致、tse-frontend 教训。
---

# 前端开发规范

## 何时使用

- 新增或修改 Web 管理端（Vue 3 + Element Plus）20 模块页面
- 新增或修改 uni-app 小程序页面（12+ 页）
- 编写公共组件 / 工具方法 / 路由 / 权限指令前——**先读本文**

## 核心原则：按功能分包 + 提取 utils + 统一工具

### 禁止

- ❌ 页面内重复的格式化 / 脱敏 / 金额 / 状态映射逻辑（各写一份）
- ❌ 硬编码文案 / 状态值 / 字典（魔法值散落）
- ❌ 裸 `:visible.sync`（Vue 2 语法）、`center: true` 的 appAlert（布局错乱）
- ❌ 改 `<script>` 不同步删 `<template>` 引用（整页白屏）
- ❌ 父级菜单挂 `component`、同一 `path` 挂两个父菜单、重复 path
- ❌ 无注释组件 / 无 props 说明 / 无事件说明

### 应该

- ✅ **按功能分包**：`views` 按模块分包（客户 / 线索 / 工单 / 奖励 / 短信 / 组织权限 / 产品…），组件、工具、API 分层；页面命名与菜单路由一致
- ✅ **公共方法提取 `src/utils`**：日期格式化、手机号脱敏、金额格式化、状态映射、字典转换统一进 utils
- ✅ **前端工具类**：request 拦截器（token / traceUuid / 错误处理）、`formatDateTime` / `AppDateTime`、`appConfirm`、`AppPagination`、`v-permission` 指令
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

### 权限控制

- 菜单级：无菜单无路由，URL 越权拦截
- 操作级：`v-permission` 指令控制新增 / 认领 / 指派 / 审批 / 导出 / 解禁按钮
- 数据范围：列表后端按部门 + 角色过滤，前端只做展示辅助

## 自检清单（改完必过）

- [ ] 是否有重复的格式化 / 脱敏 / 金额 / 状态逻辑？→ 提取到 `src/utils`
- [ ] 是否有硬编码文案 / 状态值 / 字典？→ 走配置或常量
- [ ] 是否用了 Vue 2 语法（`:visible.sync`）？→ 改 `v-model`
- [ ] 改 `<script>` 是否同步删 `<template>` 引用？是否跑过 `npm run build`？
- [ ] 父级菜单是否只挂 `redirect`？叶子才挂 `component`？path 是否唯一？
- [ ] 组件 / 方法是否有注释？props / 事件是否有说明？
- [ ] keep-alive 场景是否用 `onActivated` 拉库回显？

## 相关文档

- `../../output/方案评审定稿纪要.html` 第 11 章小程序页面、第 12 章 Web 20 模块、第 24 章前端规范
- `../../output/前端交互逻辑蓝图.html`（Web 20 模块 + 小程序交互流）
- 参考 tse-frontend：`/Users/admin/Documents/crm/tse-frontend`
