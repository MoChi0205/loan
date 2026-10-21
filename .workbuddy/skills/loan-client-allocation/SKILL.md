---
name: loan-client-allocation
description: >-
  loan-main 客户认领 / 分配 / 转移 / 资源池 / 回收 / 配额 / 审批分级流转域规范。编写或修改客户归属、
  公海认领、经理指派、跨团队转移审批、资源池创建与回收规则、认领配额（每日上限 / 持有上限 / 保护期 / 冷却 /
  回纳目标）相关后端 Service / Controller 或前端 ClientLookup / ApprovalCenter / AllocationQuotaConfig 页面时使用。
  这是用户反复反馈「依旧没实现」的重灾区，必须按本规范的分级流转与权限对齐红线落地，禁止假实现。
---

# 客户认领 / 分配 / 转移 / 资源池（loan-client-allocation）

## Step 0 · 前置门禁（强制，不可跳过）

1. **先查历史结论**：`grep -n "认领\|分配\|转移\|资源池\|回收\|配额\|公海" docs/knowledge-base/10-历史结论与决策日志.md#结论台账`，
   从**最新条目往下**读（台账按时间倒序，最新在最上方）；**命中即遵守**，与旧文档冲突以 D 条目为准；状态「已被 Dxx 替代」则跳读 Dxx。
   > ⚠️ 取号前必须自己刚跑 `grep -o "^| D[0-9-]*" docs/knowledge-base/10-历史结论与决策日志.md` 取实时最大编号（禁采信转述 / 记忆 / 分配表）。
2. **无结论且不确定 → 停下来问用户**，禁止臆断（认领/转移属契约变更，必须先确认）。
3. **再读元技能** `loan-knowledge`（`.workbuddy/skills/loan-knowledge/SKILL.md`），按其 Step 1–5 执行。
4. 回复开头输出：`【结论核对】命中 Dx-x（…）/ 未命中（grep 关键词：…）`。

## 何时使用

- 修改客户归属：公海认领、经理指派（directAssign）、顾问申请跨团队转移（applyTransfer）、审批（approve）
- 资源池：创建 / 编辑 / 删除资源池、配置自动回收规则（保护期 / 冷却 / 回纳目标 / 标签维度）
- 认领配额：`t_allocation_quota_config`（LEAD / CLIENT / POOL:`<poolCode>` 三维度、每日认领上限、持有上限）
- 前端入口：`ClientLookup.vue`（角色分流：无归属直接认领 / 经理·老板·运营·超管强制转移 / 顾问申请转移）、`ApprovalCenter.vue`（allocation 审批 tab）、`AllocationQuotaConfig.vue`
- 任何「客户归属」相关改动前，**本规范 + `loan-backend` + `loan-gateway-auth` 必读**

## 核心业务规则（不可违背）

### 1. 认领 / 分配 / 转移 · 分级流转（唯一真源）

后端 `com.loan.client.service.ClientAllocationService`，入口经 `ClientController`（`/api/admin/client/*`）。
严禁「前端点了没反应 / 接口 403 / 假实现」——四类场景必须全部落地：

| 场景 | 触发角色 | 后端行为 | 审批 |
|---|---|---|---|
| **无归属客户认领** | 任意有权限员工 | 直接 `directAssign`（本人归属，乐观锁 CAS 防并发重复认领） | 无 |
| **本人客户转移给本人** | 本人 | 幂等返回，不产生审批 | 无 |
| **经理 / 老板 / 超管 指派** | DEPT_MANAGER / BOSS / SUPER_ADMIN / SUPER | `directAssign`：经理仅限本团队；BOSS/SUPER 全权跨团队 | 无 |
| **顾问申请转移给他人（跨团队）** | ADVISER | `applyTransfer` → `ADVISER_TRANSFER` 审批，跨团队两级 `TEAM_REVIEW → BOSS_REVIEW` | 是 |

- **前端角色分流**：`ClientLookup.vue` 的 `canForceAssign` 必须包含 `OPERATOR`（与后端白名单一致，见红线 2）。
- **禁止自审**：员工本人提交的外出/指派，审批人不得是本人（服务运营域 `t_staff_outing` 同源约束）。

### 2. 资源池创建权限（唯一真源）

- **仅** `DEPT_MANAGER` / `BOSS` / `SUPER_ADMIN` / `SUPER` 可创建资源池（参考 TSE 回收逻辑）。
- 渠道（CHANNEL）只读本人录入客户，**无公海、无认领权限**（D50）。

### 3. 认领配额 · 三维度（唯一真源）

- 维度枚举：`LEAD`（线索公海）/ `CLIENT`（客户公海）/ `POOL:<poolCode>`（指定资源池）。
- 后端 `com.loan.allocation.service.ClaimQuotaService`：认领时按 **资源池优先 → 回落公海类型** 查配额。
- 前端 `AllocationQuotaConfig.vue` 必须支持「新增配置行 / 删除行 / 筛选」，不得固定两行静态渲染。
- 后端 `AllocationQuotaController.SCOPES` 校验必须与三维度对齐，禁止写死只收 LEAD/CLIENT 而拒绝 POOL。

### 4. 回收规则（唯一真源）

- 回收配置源优先级：**团队池 > 公司池 > 遗留 GLOBAL**（若已移除遗留兜底，则查不到池 = 不回收，单一配置源）。
- 语义矩阵：**显式配 0 → 0 天生效（当天即回收）；留空 → 不限制（该标签不参与回收）**。
  - 前端允许提交 0；后端 `parseStatusDays` 保留 0 项（`days != null` 即存），`recycleDays` 中 `map 无此标签(null) → 不回收`，`days >= 0 → 按 days 判定`。
  - **禁止** `days > 0` 才保留（会把 0 当未配置，与预期相悖）；**禁止** `DEAL/SIGNED/NO_NEED` 硬编码 `return 0`（改由「留空」表达永不回收）。
- 数据迁移风险：存量「已回收 0」在新语义下会变成当天回收，上线前需评估迁移为空。

## 契约红线速查（改代码前必读）

- **红线 1 · 后端单一真源**：列表 / 详情的 `canManage`、`canForceAssign` 等能力标志由后端按「角色白名单 + 池归属」计算返回，**前端不得本地硬编码角色判断**（历史 bug：三处名单打架导致运营进页面却永远只读）。
- **红线 2 · 权限白名单三处必须对齐**：① 前端 `canForceAssign`；② 后端 `ClientController.assign/directAssign` 的 `Arrays.asList(...)`；③ `MiniRoleGuard` 的 `APPROVER_ROLES` / `APPROVAL_ROLES`；④ 菜单 SQL 授权行。四处任一漏写角色（如漏 `OPERATOR`）即导致该角色 403 或只读。改角色权限必须四处一起改并 grep 复核。
- **红线 3 · 业务 ID / 编码**：客户归属操作入参一律业务编码（`clientCode` / `targetStaffCode`），禁 `@PathVariable Long id`。编码长度见 `loan-biz-id` 与 `loan-backend` 实战坑位（配置项短码 `varchar(32)` 必须 `BizIdGenerator.generate(prefix, 32)`）。
- **红线 4 · 企业名称 / 联系人展示**：列表 DTO 必须显式补 `enterpriseName` / `contactName`（企业客群显示企业名 + 副行联系人；个人客群仅联系人），由后端合并一次客户查询补齐，前端禁止逐行查名称（N+1）。涉及 DTO：`StaffAppointmentDTO` / `StaffOutingDTO` / `DailyFollowDTO` / `DailyOrderDTO` / `OrderService` 出参。
- **红线 5 · 审批权限真值（D60/D39）**：`ALLOCATION` 审批含 `DEPT_MANAGER` 但仅限本人团队；跨团队上收 BOSS。依据 `MiniRoleGuard` 的 `APPROVER_ROLES` vs `APPROVAL_ROLES`。

## 实战坑位清单（本轮沉淀，改代码前必扫）

- **运营 403 / 只读**：`ClientController.assign` 白名单漏 `OPERATOR` → 运营能进页面却永远只读或保存 403。改角色权限四处（前端 canForceAssign / Controller 白名单 / MiniRoleGuard / 菜单 SQL）必须一起改，grep 复核无遗漏。
- **「客户认领/转移依旧没实现」**：先查后端链路（`applyTransfer` / `directAssign` / `approve`）与前端入口（`ClientLookup` 角色分流 / `ApprovalCenter` allocation tab）是否齐全，再查红线 2 的权限白名单是否对齐——绝大多数「没实现」是白名单漏角色或前端分流漏分支，不是真缺代码。
- **NEW chip 计数与列表口径分裂**：计数 SQL 把 `customer_tag` 为 NULL/空/未知值都计入 newCount，列表过滤却用 `customer_tag = 'NEW'` 精确匹配（NULL 不命中）→「计数 1、列表 0」。改计数口径必须同步对齐列表 where。
- **回收 0 与未配置同义 bug**：`parseStatusDays` 用 `days > 0` 过滤会把 0 当未配置（不回收），与「显式 0 = 当天回收」预期相悖。保留 0 项、仅 `null` 视为未配置。
- **配额写死两行**：`AllocationQuotaConfig.vue` 静态渲染 LEAD/CLIENT 两行、`AllocationQuotaController.SCOPES` 拒绝 POOL → 资源池维度配额无法配置。三维度必须贯通前后端。
- **刷新非工作台页被弹回工作台**：`Layout.initializeLayout` 必须「先 `loadTabs()` 恢复持久化标签 → 再 `await loadRoleMenu()`」，顺序反了会导致权限兜底把任何非工作台页弹回。属前端初始化时序，但影响认领/分配页可用性。

## 自检清单（改完必过）

- [ ] Step 0 结论核对是否已输出？
- [ ] 四类认领/分配/转移场景（无归属认领 / 本人幂等 / 经理指派 / 顾问跨团队两级审批）是否全部落地，无假实现？
- [ ] 权限白名单四处（前端 canForceAssign / Controller / MiniRoleGuard / 菜单 SQL）是否一致？是否 grep 复核无漏角色？
- [ ] 资源池创建是否仅对 经理/老板/超管开放？渠道是否无公海无认领？
- [ ] 认领配额三维度（LEAD/CLIENT/POOL）是否前后端贯通？前端是否支持新增/删除/筛选行？
- [ ] 回收规则是否按「0=当天回收、null=不回收」语义？是否删除了 `days>0` 过滤与 `DEAL/SIGNED/NO_NEED` 硬编码 return 0？
- [ ] 入参是否走业务编码（禁 Long id）？编码长度是否按目标列宽（varchar(32) 用 generate(prefix,32)）？
- [ ] 列表 DTO 是否补 enterpriseName/contactName 并由后端合并查询（无 N+1）？
- [ ] 审批权限是否按 D60/D39 真值（DEPT_MANAGER 仅本团队，跨团队上收 BOSS）？
- [ ] 是否 `mvn -pl loan-service -am compile` 通过？前端是否 `npm run build` 通过？

## 相关文档与代码入口

- `docs/knowledge-base/10-历史结论与决策日志.md#结论台账`（**Step 0 必查**，D50/D60/D39 高频红线）
- `docs/2026-10-19-资源池与客户档案六问题修复方案.md`（六问题根因与修复，含回收 0 语义、权限白名单打架）
- `docs/2026-10-20-第三批问题清单.md`（NEW chip 口径、刷新弹回工作台时序等）
- `docs/2026-10-21-开发检查清单-前后端bug复盘.md`（全量前后端 bug 复盘，本域见「客户认领/分配/转移/资源池/回收」类）
- 后端入口：`loan-service/src/main/java/com/loan/client/service/ClientAllocationService.java`、`com.loan.client.controller.ClientController.java`、`com.loan.resourcepool.service.ResourcePoolConfigService.java`、`com.loan.allocation/{controller/AllocationQuotaController,service/ClaimQuotaService}.java`、`com.loan.mini.service.MiniRoleGuard.java`
- 前端入口：`loan-web/src/views/client/ClientLookup.vue`、`loan-web/src/views/approval/ApprovalCenter.vue`、`loan-web/src/views/system/AllocationQuotaConfig.vue`
- 交叉必读：`loan-backend`（分层/批量/二级缓存）、`loan-biz-id`（业务编码长度）、`loan-gateway-auth`（接口登记与 403）、`loan-web-dev`（前端 UI 坑位）
