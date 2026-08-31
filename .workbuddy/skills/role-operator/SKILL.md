---
name: role-operator
description: >-
  loan-main 业务角色「运营管理员」的功能边界、数据范围与禁止项（全部审批类型 + 配置/审计主体在 Web）。
  涉及运营角色的需求评审、ALLOCATION/PRODUCT/DOWNLOAD 审批、审批中心入口、
  Web 管理端配置与审计能力、验收走查时使用；使用前必须先执行 loan-knowledge 的 Step 0。
---

# 业务角色：运营管理员（role=operator）

## Step 0 · 前置门禁（强制，不可跳过）

1. **先查历史结论**：`grep -n "运营\|OPERATOR\|审批\|配置\|审计" docs/knowledge-base/10-历史结论与决策日志.md#结论台账`，
   从**最新条目往下**读（台账按时间倒序，最新在最上方）；**命中即遵守**；状态为「已被 Dxx 替代」则跳读 Dxx。
   > ⚠️ 取号前必须自己刚跑 `grep -o "^| D[0-9-]*" docs/knowledge-base/10-历史结论与决策日志.md` 取实时最大编号（禁采信转述 / 记忆 / 分配表）。
2. **无结论且不确定 → 停下来问用户**，禁止臆断。
3. **再读元技能** `loan-knowledge`，按其 Step 1–5 执行。
4. 回复开头输出：`【结论核对】命中 Dx-x / Cx（…）/ 未命中（grep 关键词：…）`。

## 1. 角色标识与推导来源

| 项 | 值 |
|---|---|
| 角色标识 | `operator` |
| `userType` | `STAFF` |
| `roleCode` | `OPERATOR` |
| 后端推导 | `MiniAuthController.resolveRoleInfo()`：`TYPE_STAFF` + `"OPERATOR"` → `role = "operator"` |
| 前端推导 | `store/user.js resolveRole()`：同上 |
| getter | `isStaff` 含 operator；**`isApproverRole` 含 operator**（`mine.vue:184`） |
| 展示名 | `roleLabel` = `运营管理员` |
| 承载侧重 | **小程序侧承载轻，主体能力在 Web 管理端**（01 角色模型明示） |

## 2. 功能边界（能做什么）

- ✅ **全部审批类型** —— `ALLOCATION`（`APPROVER_ROLES` 含 OPERATOR）**与** `PRODUCT` / `DOWNLOAD`（`APPROVAL_ROLES` 含 OPERATOR）
  —— **两个白名单双含 OPERATOR**，是本角色区别于 deptmgr（仅后者）与 adviser（两者皆无）的关键
- ✅ **审批中心入口** —— `pages/approval/list.vue` + `mine.vue`（`isApproverRole` 含 operator）
- ✅ **配置 / 审计 / 短信 / 模板** —— **主体在 Web 管理端**（`/api/admin/**`，经 `AdminAuthInterceptor`）
- ✅ **待删产品终审**：`GET /api/mini/partner-product/delete/pending`、`POST /api/mini/partner-product/delete/{approvalNo}/audit`
- 顾问（`role-adviser`）的全部能力（替客匹配 / 查重流转 / 全量报告工单 / 命中产品明细 / 经营诊断）
- 我的账户（C8）：展示工号 / 部门 / 角色 / 入职时间（operator ≠ boss/super）

## 3. 禁止项（不能做什么）— 2 条

1. ❌ **小程序端不做客户管理列表、不做线索公海**
   —— 08 矩阵对**全部 7 角色**均为 ❌，规划在 **Web 端阶段二**；小程序可后续追加轻量入口，但**当前不存在**
2. ❌ **不在小程序改系统级配置**
   —— 走 Web 管理端 `views/config`、`views/org`；小程序侧只承载审批等轻量能力

## 4. 可见数据范围

| 维度 | 范围 |
|---|---|
| 报告 / 工单 / 客户 | **全公司全量** |
| 审批 | **全部类型**（ALLOCATION + PRODUCT + DOWNLOAD） |
| 配置域 | ✅ 可见可改（**Web 管理端**） |
| 审计 / 短信 / 模板 | ✅（Web 管理端） |

## 5. tabBar 结构

**5 tab** + `mine` 页「审批中心」入口

- tabBar（依据 `loan-mini/components/TabBar.vue:55-61`）：
  `home(/pages/home/home)` · `match(/pages/match/match)` · `report(/pages/report/list)` · `order(/pages/order/list)` · `mine(/pages/mine/mine)`
- **额外入口**：`mine.vue`「审批中心」（带 `approvalTotal` 角标）→ `/pages/approval/list`

> 自绘 TabBar（C17）；切换一律 `uni.reLaunch`，禁用 `uni.switchTab`。
> 配置 / 审计等能力**不在 tabBar 内**，在 **Web 管理端**。

## 6. 相关接口清单

| 接口 | 说明 | 权限约束 |
|---|---|---|
| `GET /api/mini/approval/counts` | 待审数 `{PRODUCT, DOWNLOAD, ALLOCATION, TOTAL}` | OPERATOR / SUPER_ADMIN / SUPER / BOSS |
| `GET /api/mini/approval/pending?type=ALL\|PRODUCT\|DOWNLOAD\|ALLOCATION&page&size` | 待审列表（分段分页，`paginationHint:"SEGMENTED"`） | 同上 |
| `POST /api/mini/approval/{type}/{approvalNo}/audit` | 审批（body `{approve, opinion}`） | 同上（**全类型**） |
| `GET /api/mini/partner-product/delete/pending` | 待删产品列表 | **仅运营 / 超管** |
| `POST /api/mini/partner-product/delete/{approvalNo}/audit` | 终审删除 | **仅运营 / 超管** |
| `GET /api/admin/approval/counts` | 管理端统一计数 | OPERATOR / SUPER / BOSS |
| `GET /api/admin/approval/pending?type=...` | 管理端统一待审 | OPERATOR / SUPER / BOSS |
| Web `/api/admin/**` | 配置 / 组织 / 短信 / 模板 / 审计 | `AdminAuthInterceptor`：登录 + STAFF + 管理角色（**BOSS / DEPT_MANAGER / OPERATOR / SUPER_ADMIN / SUPER**），匿名 / 客户 / 渠道一律 403 |

> 当前白名单 `loan.mini.approval.types=ALLOCATION`（`application.properties:98`）
> —— **仅开放 ALLOCATION**；PRODUCT / DOWNLOAD 待阶段四。

## 7. 相关结论编号

`C19`（分配审批）· `C22`（审批中心统一，方案 A：视图层统一 + 入口统一，不建表）· `C9`（渠道产品删除走审批，运营/超管终审）

## 8. 必读文档指针

- `docs/knowledge-base/10-历史结论与决策日志.md#结论台账`（**Step 0 必查**）
- `docs/knowledge-base/01-角色权限模型.md#7 角色体系（必须严格区分）`
- `docs/knowledge-base/08-小程序角色功能矩阵.md#矩阵`（运营列）
- `docs/knowledge-base/04-后端 API 契约.md#审批中心（统一，T5）`
- `docs/knowledge-base/04-后端 API 契约.md#通用约定`（管理端 `AdminAuthInterceptor` 权限）
- `docs/knowledge-base/04-后端 API 契约.md#工单 / 产品`（待删终审接口）
- `docs/knowledge-base/07-沟通-上线-测试-部署清单.md#测试矩阵（验证清单）`
- 代码真源：`loan-service/src/main/java/com/loan/mini/service/MiniRoleGuard.java`、
  `loan-mini/pages/mine/mine.vue`（`isApproverRole`）、`loan-mini/pages/approval/list.vue`
- 交叉技能：`role-super`（本角色 + 系统级配置）、`role-deptmgr`（对比：deptmgr 不能审 ALLOCATION）、`loan-gateway-auth`

## 改动自检清单

- [ ] Step 0 结论核对是否已输出？
- [ ] 审批能力是否覆盖**两个白名单**（OPERATOR 双含，勿只写一个）？
- [ ] 是否在小程序端塞了系统配置能力？（❌ 应走 Web `views/config` / `views/org`）
- [ ] 是否误以为有客户管理列表 / 线索公海？（当前全角色 ❌）
- [ ] 新增 Web 管理端接口是否过了 `AdminAuthInterceptor` 与 `loan-gateway-auth` 登记？
- [ ] tabBar 是否以 `TabBar.vue` 为准（5 tab）？
