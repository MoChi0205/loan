---
name: role-deptmgr
description: >-
  loan-main 业务角色「部门经理」的功能边界、数据范围与禁止项（全项目最高频权限误判点）。
  涉及部门经理的需求评审、PRODUCT/DOWNLOAD 审批、ALLOCATION 权限核对、团队归属数据范围、验收走查时使用；
  使用前必须先执行 loan-knowledge 的 Step 0（查 10-历史结论与决策日志）。
---

# 业务角色：部门经理（role=deptmgr）— 最容易踩坑的角色

## Step 0 · 前置门禁（强制，不可跳过）

1. **先查历史结论**：`grep -n "部门经理\|DEPT_MANAGER\|审批\|ALLOCATION" docs/knowledge-base/10-历史结论与决策日志.md#结论台账`，
   从**最新条目往下**读（台账按时间倒序，最新在最上方）；**命中即遵守**；状态为「已被 Dxx 替代」则跳读 Dxx。
   > ⚠️ 取号前必须自己刚跑 `grep -o "^| D[0-9-]*" docs/knowledge-base/10-历史结论与决策日志.md` 取实时最大编号（禁采信转述 / 记忆 / 分配表）。
2. **无结论且不确定 → 停下来问用户**，禁止臆断。
   > 🚨 **本角色是全项目最高频的权限误判点**。直觉上"经理能审"，但代码**不允许审 ALLOCATION**。
   > 任何涉及 deptmgr 审批能力的需求，**动手前必须读 `MiniRoleGuard.java` 原文**。
3. **再读元技能** `loan-knowledge`，按其 Step 1–5 执行。
4. 回复开头输出：`【结论核对】命中 Dx-x / Cx（…）/ 未命中（grep 关键词：…）`。

## 1. 角色标识与推导来源

| 项 | 值 |
|---|---|
| 角色标识 | `deptmgr` |
| `userType` | `STAFF` |
| `roleCode` | `DEPT_MANAGER` |
| 后端推导 | `MiniAuthController.resolveRoleInfo()`：`TYPE_STAFF` + `"DEPT_MANAGER"` → `role = "deptmgr"` |
| 前端推导 | `store/user.js resolveRole()`：同上 |
| getter | `isStaff` 含 deptmgr；`isChannel` 为 false；**`isApproverRole` 不含 deptmgr** |
| 展示名 | `roleLabel` = `部门经理` |

## 2. 功能边界（能做什么）

- **顾问（`role-adviser`）的全部能力**：替客匹配、查重 → 归属流转、全量报告四维、命中产品明细、经营诊断、全量服务单四维
- **团队归属**：管辖本部门员工及其客户
- ✅ **可审批 `PRODUCT` / `DOWNLOAD`**
  —— `MiniRoleGuard.requireApproverFor(type, user)` 的**非 ALLOCATION 分支**走 `APPROVAL_ROLES`，
  该白名单**含 `DEPT_MANAGER`**
- 我的账户（C8）：展示工号 / 部门 / 角色 / 入职时间（**deptmgr ≠ boss/super，会展示工号与部门**）

## 3. 禁止项（不能做什么）— 3 条

1. ✅ **可审批 `ALLOCATION`，但仅本人团队**
   - 依据：`MiniRoleGuard.APPROVER_ROLES` 已包含 `DEPT_MANAGER`；`ClientAllocationService.assertDeptManagerScope` 按申请人部门过滤
   - 代码位置：`loan-service/src/main/java/com/loan/mini/service/MiniRoleGuard.java`、`ClientAllocationService.java`
   - 跨团队待审单由 BOSS 等上级审批，部门经理调用会收到「非本团队客户，需由 BOSS 审批」
   - 来源结论：**D39 已覆盖 D0-4**
2. ❌ **小程序无审批中心入口**
   —— 当前审批中心仍仅在 Web 管理端承载；部门经理审批入口是 Web「审批中心」分配审批页签。
3. ❌ **不能改系统配置 / 角色权限菜单**
   —— 属 `operator` / `super` 在 **Web 管理端**（`views/config`、`views/org`）的能力

## 4. 可见数据范围

| 维度 | 范围 |
|---|---|
| 报告 / 工单 | **本部门**（`deptCode` 子树）全量 + 本人全量 |
| 命中产品明细 | 可见（员工权限） |
| 客户 | 本部门归属 + 可查重 / 申请流转 |
| 审批可见 | `PRODUCT` / `DOWNLOAD` 全部待审；`ALLOCATION` 仅本人团队 |
| 配置域 | 不可见 |

## 5. tabBar 结构

**5 tab**：首页 · 智能匹配 · 我的报告 · 服务单 · 我的

依据 `loan-mini/components/TabBar.vue:55-61`（非渠道分支，deptmgr 走 `isStaff` 默认分支）。

> ⚠️ **无 mine 页「审批中心」入口**（`isApproverRole` 不含 deptmgr）—— 这是与 boss/operator/super 的**可见差异**。
> 自绘 TabBar（C17）；切换一律 `uni.reLaunch`，禁用 `uni.switchTab`。

## 6. 相关接口清单

**顾问全部接口**，另加：

| 接口 | 说明 | 权限约束 |
|---|---|---|
| `GET /api/mini/approval/pending?type=...` | 待审列表 | **仅 `type ≠ ALLOCATION`**（type=PRODUCT / DOWNLOAD） |
| `POST /api/mini/approval/{type}/{approvalNo}/audit` | 审批（body `{approve, opinion}`） | **仅 `type ≠ ALLOCATION`** |
| `GET /api/admin/approval/allocation/pending` | 管理端 allocation 待审 | DEPT_MANAGER 可见本人团队；后端按申请人部门过滤 |

> 权限说明以 D39 与 `MiniRoleGuard.APPROVER_ROLES` 为准：ALLOCATION 含 DEPT_MANAGER，但仅本人团队范围；跨团队必须由 BOSS 等上级处理。
>
> 白名单现状：`loan.mini.approval.types=ALLOCATION`（`loan-service/src/main/resources/application.properties:98`）
> —— 当前**仅开放 ALLOCATION 类型**，PRODUCT / DOWNLOAD 待阶段四。因此 deptmgr 的 `PRODUCT`/`DOWNLOAD`
> 审批能力在当前白名单下**实际不可达**（属前瞻能力）。

## 7. 相关结论编号

`D39`（**DM 仅本团队 ALLOCATION 审批**，覆盖 D0-4）· `C19`（B 组数据模型缺口 / 分配审批）· `C22`（审批中心统一，方案 A 视图层统一）· `C1` `C2` `C3` `C4` `C7`

## 8. 必读文档指针

- `docs/knowledge-base/10-历史结论与决策日志.md#结论台账`（**Step 0 必查**；**D0-4 为本角色第一依据**）
- `docs/knowledge-base/01-角色权限模型.md#7 角色体系（必须严格区分）`
- `docs/knowledge-base/08-小程序角色功能矩阵.md#矩阵`（部门经理列 + 审批中心行）
- `docs/knowledge-base/04-后端 API 契约.md#审批中心（统一，T5）`
- `docs/knowledge-base/06-业务结论沉淀索引（C1-C19）.md#结论速查`
- 代码真源（**权限真值唯一来源**）：
  `loan-service/src/main/java/com/loan/mini/service/MiniRoleGuard.java`（`APPROVER_ROLES` vs `APPROVAL_ROLES`）
- 代码真源（入口守卫）：`loan-mini/pages/mine/mine.vue`（`isApproverRole`）
- 交叉技能：`role-adviser`（能力基集）、`role-boss` / `role-operator` / `role-super`（差异对比）

## 改动自检清单

- [ ] Step 0 结论核对是否已输出？**是否读过 `MiniRoleGuard.java` 原文**？
- [ ] 是否给 deptmgr 的 ALLOCATION 审批增加了团队范围过滤？（必须）
- [ ] 是否误把 deptmgr 放宽为跨团队审批？（❌ 禁止）
- [ ] 涉及审批类型时是否区分了 `APPROVER_ROLES`（ALLOCATION）与 `APPROVAL_ROLES`（PRODUCT/DOWNLOAD）？
- [ ] 是否注意到当前白名单只开了 ALLOCATION（故 PRODUCT/DOWNLOAD 审批暂不可达）？
- [ ] tabBar 是否以 `TabBar.vue` 为准（5 tab，无审批入口）？
