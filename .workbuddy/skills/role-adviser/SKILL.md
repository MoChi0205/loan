---
name: role-adviser
description: >-
  loan-main 业务角色「顾问」的功能边界、数据范围与禁止项（员工侧基线 + 未识别 roleCode 兜底角色）。
  涉及顾问角色的需求评审、替客匹配、查重与归属流转、报告与工单全量查询、验收走查时使用；
  使用前必须先执行 loan-knowledge 的 Step 0（查 10-历史结论与决策日志）。
---

# 业务角色：顾问（role=adviser）— 员工侧基线 / 兜底角色

## Step 0 · 前置门禁（强制，不可跳过）

1. **先查历史结论**：`grep -n "顾问\|ADVISER\|STAFF\|替客\|查重\|归属" docs/knowledge-base/10-历史结论与决策日志.md#结论台账`，
   从**最新条目往下**读（台账按时间倒序，最新在最上方）；**命中即遵守**；状态为「已被 Dxx 替代」则跳读 Dxx。
   > ⚠️ 取号前必须自己刚跑 `grep -o "^| D[0-9-]*" docs/knowledge-base/10-历史结论与决策日志.md` 取实时最大编号（禁采信转述 / 记忆 / 分配表）。
2. **无结论且不确定 → 停下来问用户**，禁止臆断（角色边界属安全域）。
3. **再读元技能** `loan-knowledge`，按其 Step 1–5 执行。
4. 回复开头输出：`【结论核对】命中 Dx-x / Cx（…）/ 未命中（grep 关键词：…）`。

## 1. 角色标识与推导来源

| 项 | 值 |
|---|---|
| 角色标识 | `adviser` |
| `userType` | `STAFF` |
| `roleCode` | `ADVISER` |
| 后端推导 | `MiniAuthController.resolveRoleInfo()`：`TYPE_STAFF` + `roleCode.toUpperCase() == "ADVISER"` → `role = "adviser"` |
| 前端推导 | `store/user.js resolveRole()`：同上 |
| getter | `isStaff` 含 adviser；`isChannel` 为 false |
| 展示名 | `roleLabel` = `顾问` |

### ⚠️ 兜底特性（F7，本角色最关键的一点）

```java
// MiniAuthController.resolveRoleInfo() L120-122
} else {
    role = "adviser"; // 员工但未识别细分角色：最小可用权限
}
```
```js
// store/user.js resolveRole() L58-60
if (code === 'SUPER_ADMIN' || code === 'SUPER') return 'super';
// 员工但未识别细分角色：按顾问处理（最小可用权限）
return 'adviser';
```

**凡 `userType == STAFF` 但 `roleCode` 未识别（空 / 未知 / 新增未登记）→ 一律兜底为 `adviser`。**

推论：
- **本角色边界 = 员工侧基线**，deptmgr / boss / operator / super 都是在它之上叠加能力
- 新增 `roleCode` 时**必须先登记映射**，否则新角色会静默降级成 adviser（权限不足，且不易发现）
- 「顾问能做什么」不能反向推出「某未知员工角色能做什么」—— 只能推出**下界**

## 2. 功能边界（能做什么）

- **替客匹配**（C1）：`POST /api/mini/match/run`，body **传 `clientCode`** 指定目标客户
- **查重 → 归属流转**（C2 / C10）：`GET /api/mini/client/search`（≥2 字）、`POST /api/mini/client/{clientCode}/claim`、
  `GET /api/mini/client/{clientCode}/claim-status`
- **全量报告 + 四维筛选**（C3 / C11）：`GET /api/mini/report/list` → 后端走 `allReports(page,size,query,credit,owner,dateRange,userNo)`
- **报告命中产品明细**（C4）：`GET /api/mini/report/{reportNo}/products` —— **仅员工可见**
- **经营诊断**（C5）：`GET /api/mini/report/{reportNo}/diagnosis`
- **全量服务单四维**（C7）：`GET /api/mini/order/list`
- **我的账户**（C8）：员工差异化工号 / 部门 / 角色 / 入职时间
- 材料上传（`POST /api/mini/upload`）

## 3. 禁止项（不能做什么）— 3 条

1. ❌ **不能审批任何审批类型**
   - `ALLOCATION` 白名单 `MiniRoleGuard.APPROVER_ROLES = [OPERATOR, SUPER_ADMIN, SUPER, BOSS, DEPT_MANAGER]` —— **不含 ADVISER**
   - `PRODUCT` / `DOWNLOAD` 白名单 `APPROVAL_ROLES = [BOSS, DEPT_MANAGER, OPERATOR, SUPER_ADMIN, SUPER]` —— **也不含 ADVISER**
   - → **顾问只能"提交分配申请"（`/claim`），不能审批**
2. ❌ **无审批中心入口** —— `mine.vue` `isApproverRole = ['boss','operator','super']` 不含 adviser；08 矩阵对顾问为 ❌ / —
3. ❌ **无客户管理列表、无线索公海** —— 08 矩阵当前 ❌（**全部 7 角色均为 ❌**），规划在 Web 端阶段二

## 4. 可见数据范围

| 维度 | 范围 |
|---|---|
| 报告 | **全量**（跨归属）+ 四维组合筛选（`query` / `credit` / `owner` / `dateRange`） |
| 报告命中产品 | **可见明细**（`bankName` / `productName`） |
| 工单 | **全量**四维（客户姓名 / 手机号 / 状态 / 时间） |
| 客户 | 可查重、可申请归属流转；归属自己的部分 |
| 产品 | 可录入（08 矩阵「✅ 录入」） |

## 5. tabBar 结构

**5 tab**：首页 · 智能匹配 · 我的报告 · 服务单 · 我的

依据 `loan-mini/components/TabBar.vue:55-61`（非渠道分支）：
`home(/pages/home/home)` · `match(/pages/match/match)` · `report(/pages/report/list)` · `order(/pages/order/list)` · `mine(/pages/mine/mine)`

> 自绘 TabBar（C17）；切换一律 `uni.reLaunch`，禁用 `uni.switchTab`。**无** mine 页审批中心入口。

## 6. 相关接口清单

| 接口 | 说明 | 权限约束 |
|---|---|---|
| `POST /api/mini/match/run` | 替客匹配（**传 `clientCode`**） | CUSTOMER / STAFF；**CHANNEL 禁入** |
| `GET /api/mini/match/history` | 匹配历史 | 已登录 |
| `GET /api/mini/report/list` | 全量报告（**四维**） | STAFF 分支 `allReports` |
| `GET /api/mini/report/{reportNo}/products` | 命中产品明细 | **仅 STAFF**（C4） |
| `GET /api/mini/report/{reportNo}/diagnosis` | 经营诊断 | 员工全量 |
| `GET /api/mini/order/list` | 服务单（**四维**） | 员工全量 |
| `GET /api/mini/client/search` | 查重（≥2 字） | 仅 STAFF；**CHANNEL 禁止** |
| `POST /api/mini/client/{clientCode}/claim` | 申请分配（AUTO_CLAIMED 或 PENDING_APPROVAL） | 仅 STAFF |
| `GET /api/mini/client/{clientCode}/claim-status` | 分配审批状态轮询 | 仅 STAFF |
| `POST /api/mini/upload` | 材料上传 | CUSTOMER / STAFF / CHANNEL |

> ⚠️ `POST /api/mini/client`（录入新客户）在 `MiniClientController` **不存在**
> （该 Controller 仅有 `/search`、`/{clientCode}/claim`、`/{clientCode}/claim-status`、`/allocation-approvals/*`）。
> `04-后端 API 契约.md` 第 45 行的该行**已过时**。客户录入实际走 `POST /api/mini/lead/submit`（线索，C20）。

## 7. 相关结论编号

`C1`（替客匹配资格）· `C2`（替客匹配 + 归属流转）· `C3`（报告查询维度角色二分）· `C4`（命中产品仅员工可见）· `C7`（服务单四维筛选）· `C10`（录入新客户自动查重）· `C19`（B 组数据模型缺口 / 分配审批）· `F7`（未识别 roleCode 兜底 adviser）

## 8. 必读文档指针

- `docs/knowledge-base/10-历史结论与决策日志.md#结论台账`（**Step 0 必查**）
- `docs/knowledge-base/01-角色权限模型.md#7 角色体系（必须严格区分）`
- `docs/knowledge-base/01-角色权限模型.md#前端：Pinia store（store/user.js）`
- `docs/knowledge-base/08-小程序角色功能矩阵.md#矩阵`（顾问列）
- `docs/knowledge-base/04-后端 API 契约.md#匹配（C15）`
- `docs/knowledge-base/04-后端 API 契约.md#客户查重 + 归属流转（C2/C10/C19）`
- `docs/knowledge-base/04-后端 API 契约.md#通用约定`（脱敏：员工可见产品名/银行名）
- `docs/knowledge-base/06-业务结论沉淀索引（C1-C19）.md#结论速查`
- `docs/knowledge-base/09-业务流程知识图谱.md#2. 角色节点（7）`
- 代码真源：`loan-service/src/main/java/com/loan/mini/service/MiniRoleGuard.java`、
  `loan-service/src/main/java/com/loan/mini/controller/MiniAuthController.java`（`resolveRoleInfo`）、`loan-mini/store/user.js`

## 改动自检清单

- [ ] Step 0 结论核对是否已输出？
- [ ] 新增 `roleCode` 是否已在 **后端 `resolveRoleInfo` + 前端 `resolveRole` 两侧同步登记**（否则静默降级 adviser）？
- [ ] 是否误给 adviser 加了审批能力（两个白名单都不含 ADVISER）？
- [ ] 替客匹配是否透传 `clientCode`（不传则匹配成自己）？
- [ ] 是否误用了不存在的 `POST /api/mini/client`？
- [ ] tabBar 是否以 `TabBar.vue` 为准（5 tab，无审批入口）？
