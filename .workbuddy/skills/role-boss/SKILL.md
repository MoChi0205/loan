---
name: role-boss
description: >-
  loan-main 业务角色「老板」的功能边界、数据范围与禁止项（全量数据 + 全部审批类型）。
  涉及老板角色的需求评审、ALLOCATION/PRODUCT/DOWNLOAD 审批、审批中心入口、验收走查时使用；
  使用前必须先执行 loan-knowledge 的 Step 0（查 10-历史结论与决策日志）。
---

# 业务角色：老板（role=boss）

## Step 0 · 前置门禁（强制，不可跳过）

1. **先查历史结论**：`grep -n "老板\|BOSS\|审批\|ALLOCATION" docs/knowledge-base/10-历史结论与决策日志.md#结论台账`，
   从**最新条目往下**读（台账按时间倒序，最新在最上方）；**命中即遵守**；状态为「已被 Dxx 替代」则跳读 Dxx。
   > ⚠️ 取号前必须自己刚跑 `grep -o "^| D[0-9-]*" docs/knowledge-base/10-历史结论与决策日志.md` 取实时最大编号（禁采信转述 / 记忆 / 分配表）。
2. **无结论且不确定 → 停下来问用户**，禁止臆断。
3. **再读元技能** `loan-knowledge`，按其 Step 1–5 执行。
4. 回复开头输出：`【结论核对】命中 Dx-x / Cx（…）/ 未命中（grep 关键词：…）`。

## 1. 角色标识与推导来源

| 项 | 值 |
|---|---|
| 角色标识 | `boss` |
| `userType` | `STAFF` |
| `roleCode` | `BOSS` |
| 后端推导 | `MiniAuthController.resolveRoleInfo()`：`TYPE_STAFF` + `"BOSS"` → `role = "boss"` |
| 前端推导 | `store/user.js resolveRole()`：同上 |
| getter | `isStaff` 含 boss；**`isApproverRole` 含 boss**（`mine.vue:184`） |
| 展示名 | `roleLabel` = `老板` |
| 特殊 | 网关层：BOSS 业务接口默认全量（`t_role_api` 不落库），但系统配置显式拒绝规则优先 |

## 2. 功能边界（能做什么）

- **顾问（`role-adviser`）的全部能力** + **全量数据**
- ✅ **可审批 `ALLOCATION`** —— `MiniRoleGuard.APPROVER_ROLES = [OPERATOR, SUPER_ADMIN, SUPER, BOSS, DEPT_MANAGER]` **含 BOSS**；DM 仅本团队，BOSS 可审全公司
- ✅ **可审批 `PRODUCT` / `DOWNLOAD`** —— `APPROVAL_ROLES = [BOSS, DEPT_MANAGER, OPERATOR, SUPER_ADMIN, SUPER]` **含 BOSS**
- ✅ **有审批中心入口** —— `pages/approval/list.vue` + `mine.vue`（`isApproverRole` 含 boss）
- 我的账户（C8）：**不展示「工号 / 部门」**（`mine.vue:224`，层级最高，无归属部门概念），仅展示角色 + 入职时间

## 3. 禁止项（不能做什么）— 2 条

1. ❌ **不等于超级管理员**：**不能改系统配置、不能配角色 / 菜单 / 接口授权**
   —— 这些属 `operator` / `super` 在 **Web 管理端**（`views/config`、`views/org`、`组织权限 → 接口权限`）的能力。
   网关 `roleDenyApiRules.BOSS` 与后端 `AdminRoleGuard` 双层强制收紧：`api-perm:*`、角色/菜单权限与组织写接口、调试中心均拒绝；仅保留客户分配依赖的员工/部门只读选择器。
2. ❌ **小程序端无客户管理列表、无线索公海**
   —— 08 矩阵对**全部 7 角色**均为 ❌，规划在 Web 端阶段二

## 4. 可见数据范围

| 维度 | 范围 |
|---|---|
| 报告 / 工单 / 客户 | **全公司全量** |
| 命中产品明细 | 可见 |
| 审批 | **全部类型**（ALLOCATION + PRODUCT + DOWNLOAD） |
| 配置域 | ❌ 不可见 |

## 5. tabBar 结构

**5 tab** + `mine` 页「审批中心」入口

- tabBar（依据 `loan-mini/components/TabBar.vue:55-61`）：
  `home(/pages/home/home)` · `match(/pages/match/match)` · `report(/pages/report/list)` · `order(/pages/order/list)` · `mine(/pages/mine/mine)`
- **额外入口**：`mine.vue` 的「审批中心」菜单项（带 `approvalTotal` 角标，来自 `GET /api/mini/approval/counts` 的 `TOTAL`）
  → 跳转 `/pages/approval/list`

> 自绘 TabBar（C17）；切换一律 `uni.reLaunch`，禁用 `uni.switchTab`。

## 6. 相关接口清单

**顾问全部接口**，另加：

| 接口 | 说明 | 权限约束 |
|---|---|---|
| `GET /api/mini/approval/counts` | 待审数 `{PRODUCT, DOWNLOAD, ALLOCATION, TOTAL}` | OPERATOR / SUPER_ADMIN / SUPER / **BOSS** |
| `GET /api/mini/approval/pending?type=ALL\|PRODUCT\|DOWNLOAD\|ALLOCATION&page&size` | 待审列表（每条带 `type`；`type=ALL` 为**分段分页**语义，`paginationHint:"SEGMENTED"`） | 同上 |
| `POST /api/mini/approval/{type}/{approvalNo}/audit` | 审批（body `{approve, opinion}`） | 同上（**全类型，含 ALLOCATION**） |
| `GET /api/admin/approval/counts` | 管理端统一计数 | OPERATOR / SUPER / BOSS |
| `GET /api/admin/approval/pending?type=...` | 管理端统一待审 | OPERATOR / SUPER / BOSS |

> 当前白名单 `loan.mini.approval.types=ALLOCATION`（`application.properties:98`）
> —— **仅开放 ALLOCATION**，PRODUCT / DOWNLOAD 待阶段四，故 `type=PRODUCT/DOWNLOAD` 现为前瞻能力。

## 7. 相关结论编号

`C19`（B 组数据模型缺口 / 分配审批）· `C22`（审批中心统一，方案 A：视图层统一 + 入口统一，**不建表、不迁移数据**）· `C1` `C2` `C3` `C4` `C7` · `D39`（ALLOCATION 白名单与团队范围真值，确认 BOSS 可审全部）

## 8. 必读文档指针

- `docs/knowledge-base/10-历史结论与决策日志.md#结论台账`（**Step 0 必查**）
- `docs/knowledge-base/01-角色权限模型.md#7 角色体系（必须严格区分）`
- `docs/knowledge-base/08-小程序角色功能矩阵.md#矩阵`（老板列）
- `docs/knowledge-base/08-小程序角色功能矩阵.md#结论`（审批中心入口已存在的修正说明）
- `docs/knowledge-base/04-后端 API 契约.md#审批中心（统一，T5）`
- `docs/knowledge-base/09-业务流程知识图谱.md#2. 角色节点（7）`
- 代码真源：`loan-service/src/main/java/com/loan/mini/service/MiniRoleGuard.java`、
  `loan-mini/pages/mine/mine.vue`（`isApproverRole`）、`loan-mini/pages/approval/list.vue`
- 交叉技能：`role-adviser`（能力基集）、`role-deptmgr`（对比：deptmgr **不能**审 ALLOCATION）

## 改动自检清单

- [ ] Step 0 结论核对是否已输出？
- [ ] 是否把 boss 当成 super 用（给了配置 / 角色 / 菜单 / 接口授权写权限）？（❌ 禁止）
- [ ] 是否同时验证 BOSS `api-perm/page`/`org/role/list` 被拒绝，而 `org/staff/page`/`org/department/tree`/`config/status` 仍可读？
- [ ] 审批能力是否覆盖了**全部类型**（boss 在两个白名单都在列）？
- [ ] `mine` 页审批中心入口是否保留（含角标）？
- [ ] 是否注意到当前白名单只开了 ALLOCATION？
- [ ] tabBar 是否以 `TabBar.vue` 为准（5 tab）？
