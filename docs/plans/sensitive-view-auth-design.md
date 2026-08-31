# 敏感数据查看授权流程 — 设计说明书

> 唯一真源：本文档。前端（用户负责）依此对接后端接口。
> 生成日期：2026-08-26 · 状态：后端已实现并通过 `mvn compile`

## 1. 背景与范围

线索手机号属敏感字段，DB 中以 AES 密文存储（`t_lead.phone`）。为保护客户隐私并满足合规，
对手机号查看实施**受控授权**：

- 受限角色（ADVISER 顾问）默认脱敏展示，需走「申请 → 授权 → 日限额 → 留痕」流程才能看明文。
- 豁免角色（BOSS 老板 / DEPT_MANAGER 主管）直接查看明文，不受授权与日限额约束。
- 授权持久化：同一线索已授权后再次查看不再消耗当日额度。
- 当日解锁线索数达上限（默认 30）即拒绝，并通知老板/主管。

**范围**：后端 `com.loan.sensitive` 包（授权/留痕/额度/超额通知）+ `LeadService.page` 列表脱敏策略集成。
前端页面（申请弹窗、额度提示）由用户负责，不在本文范围。

## 2. 角色与权限矩阵

| 角色 roleCode | 列表页手机号 | 申请查看明文 | 日限额 |
|---------------|--------------|--------------|--------|
| BOSS          | 明文         | 免申请       | 无     |
| DEPT_MANAGER  | 明文         | 免申请       | 无     |
| ADVISER       | 脱敏         | 需申请授权   | 30/天  |

豁免判定：`SensitiveViewService.isExemptRole()` —— `BOSS` / `DEPT_MANAGER` 视为豁免。

## 3. 核心流程

### 3.1 受限角色申请查看（apply-view）
1. 入参 `leadNo`，取当前用户 `userNo` / `roleCode`。
2. 豁免角色 → 直接返回明文，不写授权/留痕。
3. 已授权（`t_sensitive_view_grant` 存在）→ 直接返回明文，不重复消耗额度。
4. 否则校验当日已用次数：
   - 已达上限 → 通知老板/主管（站内 SYSTEM_NOTICE），抛 `SENSITIVE_QUOTA_EXCEEDED(1004)`。
   - 未达上限 → 写授权记录（UK 防并发重复）+ 写查看留痕（`t_sensitive_view_log`）→ 返回明文。
   - 写后若恰好达上限 → 额外发一次超额预警通知。

### 3.2 列表页（LeadService.page）
列表出参手机号：豁免角色返回明文，受限角色统一脱敏（`138****5678`）。
明文仅在 apply-view 单条接口按授权揭示。

## 4. 数据表（已存在于 loan_db）

- `t_sensitive_view_grant`（user_no, lead_no, created_at；UK(user_no,lead_no)）
- `t_sensitive_view_log`（user_no, lead_no, view_date, created_at；索引(user_no,view_date)）

## 5. 后端接口（base: `/api/admin/lead/sensitive`）

| 方法 | 路径 | 入参 | 说明 |
|------|------|------|------|
| POST | `/apply-view` | `{ "leadNo": "lead_xxx" }` | 申请查看明文（受限角色走授权+限额+留痕） |
| GET  | `/quota` | — | 查询当前用户当日额度 {limit,used,remaining} |

### apply-view 响应 `SensitiveApplyViewResp`
```
{ leadNo, phonePlain(明文), phoneMasked(脱敏), revealed(是否揭示), limit, used, remaining }
```

### quota 响应 `SensitiveQuotaVO`
```
{ limit, used, remaining }
```

所有接口均经 `@CurrentUser` 注入当前 `LoanUser`（userNo / roleCode），并受全局异常处理器兜底。

## 6. 日限额阈值处理

- 默认值以命名常量 `SensitiveViewService.DEFAULT_DAILY_LIMIT = 30` 承载（符合「不硬编码魔法值 → 常量类/枚举」规范）。
- 后续若需后台可配，建议接入 Nacos / `t_config`（参考 backend-development Skill「不硬编码」条款），
  通过 `@Value` 或配置 Service 覆盖该常量，无需改业务代码。

## 7. 已落地文件

- `sensitive/entity/SensitiveViewGrant.java`、`SensitiveViewLog.java`（已有实体）
- `sensitive/mapper/SensitiveViewGrantMapper.java`、`SensitiveViewLogMapper.java`（含 `countToday`）
- `sensitive/dto/SensitiveApplyViewResp.java`、`SensitiveQuotaVO.java`
- `sensitive/service/SensitiveViewService.java`（授权/留痕/额度/超额通知核心逻辑）
- `sensitive/controller/SensitiveViewController.java`
- `lead/service/LeadService.java`（page 注入角色，豁免直看明文）
- `lead/controller/LeadController.java`（page 透传 roleCode/userNo）
- `common/ResultCode.java`（新增 `SENSITIVE_QUOTA_EXCEEDED(1004)`）
- 顺带修复既有编译缺口：`rule/entity/Rule.java` 补 `createdBy/updatedBy/createdAt/updatedAt`（DB `t_rule` 已有列）

## 8. 验证

- `mvn -o -pl loan-service -am compile -DskipTests` → **BUILD SUCCESS**（JDK 8 约束：已规避 `Set.of` / `var` / 文本块）。
- 运行时冒烟（起 loan-service 连 loan_db/Redis）待用户侧或在具备基础设施环境执行：
  1. BOSS/DEPT_MANAGER 登录 → `/lead/page` 列表手机号明文；ADVISER 登录 → 脱敏。
  2. ADVISER 调 `/lead/sensitive/apply-view` → 返回明文；重复调同一 leadNo 不再增额度。
  3. 第 31 次申请 → 触发 `1004` 且老板/主管收到 SYSTEM_NOTICE。

## 9. 跟进 / 待办

- [ ] 前端：线索列表「查看号码」按钮 + 申请弹窗 + 额度提示（用户负责）。
- [ ] 是否将日限额接入 Nacos / `t_config` 后台可配（按需）。
- [ ] 操作日志：敏感查看动作是否纳入 `t_operation_log`（如需要补 `@OpLog`）。
