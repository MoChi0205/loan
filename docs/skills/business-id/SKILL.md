---
name: business-id
description: >-
  loan-platform 业务唯一 ID 规范。新增/修改实体、Controller 入参、Service 查询、Mapper/XML、
  数据库表（含 FK 引用列）时使用；任何地方不得用自增 Long id 作为对外业务标识。
---

# 业务唯一 ID 规范（Biz ID）

## 何时使用

- 新增或修改业务表 / 实体 / DTO / Controller 接口 / Service 查询条件
- 定义或调整「编号 / 单号 / 编码」类字段（lead_no / order_no / product_code / staff_code…）
- 引用其他表（FK 关联）时选择引用列类型
- 审查代码：发现 `@PathVariable Long id`、`getById(Long)`、`{xxxId}` 等以自增 id 为业务标识的写法

## 核心准则（不可违背）

1. **自增 Long 主键只做物理主键**：`@TableId(type = IdType.AUTO) private Long id` 保留，但**不对外暴露、不作为业务查询条件、不参与跨系统契约**。
2. **对外身份一律业务 ID**：详情 / 更新 / 删除 / 认领 / 指派等入参用业务编码字符串；查询用业务编码列。
3. **业务 ID 格式**：`{小写业务前缀}{32 位十六进制随机}`，由 `com.loan.common.util.BizIdGenerator.generate(prefix)` 生成（SecureRandom）。
   - 字段类型 `varchar(64)`（migrate-bizid.sql 已统一扩宽），配 `UNIQUE KEY uk_xxx`。
4. **FK 引用列存业务编码（不设物理外键）**：引用其他表的列用 `varchar` 存对方业务 ID / 编码（如 `owner_staff_code` 存 `staff_code`），**禁止 BIGINT 引用列**。
5. **配置项短码保留**：dept_code / staff_code / channel_code / product_code / rule_code 等人工识别短码**不**改随机串，作为被引用方编码。

## 前缀映射（记录级业务 ID）

| 表 | 业务 ID 字段 | 前缀 |
|----|-------------|------|
| t_client_profile | client_code | client |
| t_lead / t_lead_archive | lead_no | lead |
| t_service_order / t_vip_order | order_no | order |
| t_client_submission | submission_no | submit |
| t_product_approval | approval_no | prdapr |
| t_attachment_download_approval | approval_no | dldapr |
| t_reward_record | reward_no | reward |
| t_report | report_no | report |
| t_match_trace | trace_uuid | trace（32 位 UUID 保留） |

## FK 引用列规范（Wave 2 落地，2026-08-26）

以下引用列已从 BIGINT 迁移为 VARCHAR 业务编码（`db/migrate-bizid-fk.sql`），新代码一律按此命名：

| 表 | 旧 BIGINT 列 | 新 VARCHAR 列（存） |
|----|-------------|--------------------|
| t_lead | recorder_staff_id / owner_staff_id | recorder_staff_code / owner_staff_code（staff_code） |
| t_lead | client_profile_id | client_profile_code（client_code） |
| t_lead_allocation_record | lead_id / from_staff_id / to_staff_id | lead_no / from_staff_code / to_staff_code |
| t_staff | dept_id | dept_code（dept_code） |
| t_department | parent_id / leader_staff_id | parent_code / leader_staff_code |
| t_service_attachment | order_id / client_profile_id | order_no / client_profile_code |
| t_bank_product | bank_channel_id | bank_channel_code（channel_code） |

命名规律：`<业务语义>_code`（员工类）或 `<业务语义>_no`（单据类），值就是被引用表的业务 ID / 短码。

## 接口约定

- Controller 入参：`@PathVariable String leadNo` / `@RequestBody { "leadNo": "lead8b1c...", "toStaffCode": "ADV001" }`，**不用 Long**。
- Service：按业务编码列 `LambdaQueryWrapper.eq(Entity::getXxxCode, code)` 查询；创建返回业务 ID（`Result<String>`）。
- DTO：出参暴露业务编码，不暴露 `xxxId`；Dubbo 契约同理（如 ProductDTO.bankChannelCode）。
- 前端：编号 / 单号列展示业务 ID（截断展示、完整可复制）。

## 自检清单

- [ ] Controller / DTO / 跨系统契约里是否出现 `Long id / xxxId` 作业务标识？→ 改业务编码
- [ ] Service 是否用 `selectById` / `eq(id)` 做业务查询？→ 按业务编码列查询
- [ ] 新表 / 新实体是否有业务 ID 列 + UNIQUE 索引？
- [ ] 引用其他表的列是否 `varchar` 存业务编码（非 BIGINT）？
- [ ] 生成新业务 ID 是否走 `BizIdGenerator.generate(prefix)`？

## 相关文档

- `../../docs/业务ID规范.md`（完整版：前缀映射 / 改造策略 / 唯一索引清单）
- `db/migrate-bizid.sql`（编码列宽 32→64）、`db/migrate-bizid-fk.sql`（FK 列编码化）
- 生成入口：`com.loan.common.util.BizIdGenerator`
