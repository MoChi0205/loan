---
name: role-super
description: >-
  loan-main 业务角色「超级管理员」的功能边界、数据范围与禁止项（全量 + 系统级配置，须同时兼容
  SUPER 与 SUPER_ADMIN 两种 roleCode 写法）。涉及超管角色的需求评审、全部审批类型、审批中心入口、
  角色/菜单/接口授权配置、新增角色校验代码、验收走查时使用；使用前必须先执行 loan-knowledge 的 Step 0。
---

# 业务角色：超级管理员（role=super）

## Step 0 · 前置门禁（强制，不可跳过）

1. **先查历史结论**：`grep -n "超管\|SUPER\|SUPER_ADMIN\|配置\|授权" docs/knowledge-base/10-历史结论与决策日志.md#结论台账`，
   从**最新条目往下**读（台账按时间倒序，最新在最上方）；**命中即遵守**；状态为「已被 Dxx 替代」则跳读 Dxx。
   > ⚠️ 取号前必须自己刚跑 `grep -o "^| D[0-9-]*" docs/knowledge-base/10-历史结论与决策日志.md` 取实时最大编号（禁采信转述 / 记忆 / 分配表）。
2. **无结论且不确定 → 停下来问用户**，禁止臆断。
3. **再读元技能** `loan-knowledge`，按其 Step 1–5 执行。
4. 回复开头输出：`【结论核对】命中 Dx-x / Cx（…）/ 未命中（grep 关键词：…）`。

## 1. 角色标识与推导来源

| 项 | 值 |
|---|---|
| 角色标识 | `super` |
| `userType` | `STAFF` |
| `roleCode` | **`SUPER_ADMIN` 或 `SUPER`（两种都映射到 super）** |
| 后端推导 | `MiniAuthController.resolveRoleInfo()` L118：`"SUPER_ADMIN".equals(code) \|\| "SUPER".equals(code)` → `role = "super"` |
| 前端推导 | `store/user.js resolveRole()` L58：同上 |
| getter | `isStaff` 含 super；**`isApproverRole` 含 super**（`mine.vue:184`） |
| 展示名 | `roleLabel` = `超级管理员` |
| 承载侧重 | 小程序侧承载轻；**系统级配置、角色 / 菜单 / 接口授权主体在 Web 管理端** |

### ⚠️ 双写兼容（F6，本角色最易出的代码缺陷）

`SUPER` 与 `SUPER_ADMIN` **两个 roleCode 并存于真值表**，因此**任何新增的角色校验都必须同时写两种**：

```java
// MiniRoleGuard.java（两个白名单都双写）
private static final List<String> APPROVER_ROLES =
        Arrays.asList("OPERATOR", "SUPER_ADMIN", "SUPER", "BOSS");          // L25-26
private static final List<String> APPROVAL_ROLES =
        Arrays.asList("BOSS", "DEPT_MANAGER", "OPERATOR", "SUPER_ADMIN", "SUPER");  // L29-30
```
```java
// MiniAuthController.resolveRoleInfo() L118
} else if ("SUPER_ADMIN".equals(code) || "SUPER".equals(code)) {
    role = "super";
}
```
```js
// store/user.js resolveRole() L58
if (code === 'SUPER_ADMIN' || code === 'SUPER') return 'super';
```

## 2. 功能边界（能做什么）

- **与 `role-operator` 同**（两个白名单**双含** `SUPER_ADMIN` 与 `SUPER`）
  - ✅ **全部审批类型**：`ALLOCATION` + `PRODUCT` / `DOWNLOAD`
  - ✅ 审批中心入口（`pages/approval/list.vue` + `mine.vue`）
  - ✅ 待删产品终审（`partner-product/delete/*`）
- ✅ **系统级配置、角色 / 菜单 / 接口授权** —— Web 管理端（`views/config`、`views/org`、`组织权限 → 接口权限`）
- 顾问（`role-adviser`）的全部能力
- 我的账户（C8）：**不展示「工号 / 部门」**（`mine.vue:224`，与 boss 同）

## 3. 禁止项（不能做什么）— 2 条

1. ❌ **小程序侧能力边界与 operator 一致**：无客户管理列表、无线索公海
   —— 08 矩阵对**全部 7 角色**均为 ❌（规划在 Web 阶段二）；超管的系统级能力**在 Web 管理端，不在小程序**
2. ❌ **禁止只写 `SUPER_ADMIN` 或只写 `SUPER` 一种判断**
   —— 新增角色校验必须**同时兼容两种写法**。只写一个会**静默漏判**：
   真实数据里两种 roleCode 都存在，漏掉的那个会被判成 adviser（兜底）或直接 403。
   ⚠️ **本角色最易出的代码缺陷**（F6）。自查：凡出现 `equals("SUPER_ADMIN")` 的地方，
   同处应能看到 `"SUPER"`；反之亦然。

## 4. 可见数据范围

| 维度 | 范围 |
|---|---|
| 报告 / 工单 / 客户 | **全量，无限制** |
| 审批 | **全部类型**（ALLOCATION + PRODUCT + DOWNLOAD） |
| 配置域 | ✅ 全量可改（Web 管理端：`views/config`、`views/org`、接口授权） |
| 审计 / 短信 / 模板 | ✅ |

## 5. tabBar 结构

**5 tab** + `mine` 页「审批中心」入口

- tabBar（依据 `loan-mini/components/TabBar.vue:55-61`）：
  `home(/pages/home/home)` · `match(/pages/match/match)` · `report(/pages/report/list)` · `order(/pages/order/list)` · `mine(/pages/mine/mine)`
- **额外入口**：`mine.vue`「审批中心」（带 `approvalTotal` 角标）→ `/pages/approval/list`

> 自绘 TabBar（C17）；切换一律 `uni.reLaunch`，禁用 `uni.switchTab`。
> 系统级配置能力**不在 tabBar 内**，在 **Web 管理端**。

## 6. 相关接口清单

**与 `role-operator` 相同**，另加 Web 管理端全量：

| 接口 | 说明 | 权限约束 |
|---|---|---|
| `GET /api/mini/approval/counts` | 待审数 | OPERATOR / SUPER_ADMIN / SUPER / BOSS |
| `GET /api/mini/approval/pending?type=...` | 待审列表 | 同上 |
| `POST /api/mini/approval/{type}/{approvalNo}/audit` | 审批 | 同上（**全类型**） |
| `GET /api/mini/partner-product/delete/pending` | 待删产品列表 | 仅运营 / 超管 |
| `POST /api/mini/partner-product/delete/{approvalNo}/audit` | 终审删除 | 仅运营 / 超管 |
| **Web `/api/admin/*` 全量** | 配置 / 组织 / 角色 / 菜单 / 接口授权 / 审计 | `AdminAuthInterceptor`：登录 + STAFF + 管理角色（**BOSS / DEPT_MANAGER / OPERATOR / SUPER_ADMIN / SUPER**） |

> 当前白名单 `loan.mini.approval.types=ALLOCATION`（`application.properties:98`）
> —— **仅开放 ALLOCATION**；PRODUCT / DOWNLOAD 待阶段四。

## 7. 相关结论编号

`C19`（分配审批）· `C22`（审批中心统一，方案 A）· `C9`（渠道产品删除终审）· `F6`（须同时兼容 `SUPER` 与 `SUPER_ADMIN`）

## 8. 必读文档指针

- `docs/knowledge-base/10-历史结论与决策日志.md#结论台账`（**Step 0 必查**）
- `docs/knowledge-base/01-角色权限模型.md#7 角色体系（必须严格区分）`
- `docs/knowledge-base/01-角色权限模型.md#后端：LoanUser 字段`（`roleCode` 枚举）
- `docs/knowledge-base/08-小程序角色功能矩阵.md#矩阵`（超管列）
- `docs/knowledge-base/04-后端 API 契约.md#审批中心（统一，T5）`
- `docs/knowledge-base/04-后端 API 契约.md#通用约定`（管理端鉴权）
- 代码真源（**双写兼容的唯一依据**）：
  `loan-service/src/main/java/com/loan/mini/service/MiniRoleGuard.java`（`APPROVER_ROLES` / `APPROVAL_ROLES` 均双写）
- 代码真源：`loan-service/src/main/java/com/loan/mini/controller/MiniAuthController.java`（`resolveRoleInfo` L118）、
  `loan-mini/store/user.js`（`resolveRole` L58）、`loan-mini/pages/mine/mine.vue`（`isApproverRole`）
- 交叉技能：`role-operator`（同构角色）、`role-boss`（对比：boss 无配置权）、`loan-gateway-auth`（接口授权）

## 改动自检清单

- [ ] Step 0 结论核对是否已输出？
- [ ] **新增角色校验是否同时写了 `SUPER` 与 `SUPER_ADMIN`？**（F6，最易漏）
- [ ] 是否在小程序端塞了系统配置能力？（❌ 应走 Web）
- [ ] 是否误以为有客户管理列表 / 线索公海？（当前全角色 ❌）
- [ ] 审批能力是否覆盖两个白名单？
- [ ] tabBar 是否以 `TabBar.vue` 为准（5 tab）？
