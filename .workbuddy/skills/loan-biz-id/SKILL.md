---
name: loan-biz-id
description: >-
  loan-main 业务唯一 ID 规范。新增或修改业务表 / 实体 / DTO / Controller 入参 / Service 查询 /
  Mapper XML / 外键引用列时使用；任何地方不得用自增 Long id 作为对外业务标识，
  引用列一律 varchar 存业务编码，配置项短码保留人工可读性。
---

# 业务唯一 ID 规范（loan-biz-id）

## Step 0 · 前置门禁（强制，不可跳过）

1. **先查历史结论**：`grep -n "业务ID\|bizId\|编码\|单号" docs/knowledge-base/10-历史结论与决策日志.md#结论台账`，
   从**最新条目往下**读（台账按时间倒序，最新在最上方）；**命中即遵守**；状态为「已被 Dxx 替代」则跳读 Dxx。
   > ⚠️ 取号前必须自己刚跑 `grep -o "^| D[0-9-]*" docs/knowledge-base/10-历史结论与决策日志.md` 取实时最大编号（禁采信转述 / 记忆 / 分配表）。
2. **无结论且不确定 → 停下来问用户**，禁止臆断（新增前缀 / 改列宽属契约变更，必须先确认）。
3. **再读元技能** `loan-knowledge`（`.workbuddy/skills/loan-knowledge/SKILL.md`），按其 Step 1–5 执行。
4. 回复开头输出：`【结论核对】命中 Dx-x（…）/ 未命中（grep 关键词：…）`。

## 何时使用

- 新增或修改业务表 / 实体 / DTO / Controller 接口 / Service 查询条件
- 定义或调整「编号 / 单号 / 编码」类字段（`lead_no` / `order_no` / `product_code` / `staff_code`…）
- 引用其他表（FK 关联）时选择引用列类型
- 审查代码：发现 `@PathVariable Long id`、`getById(Long)`、`{xxxId}` 等以自增 id 为业务标识的写法
- 写 Java 业务代码时与 `loan-backend` 交叉必读

## 核心准则（不可违背）

1. **自增 Long 主键只做物理主键**：`@TableId(type = IdType.AUTO) private Long id` 保留，
   但**不对外暴露、不作为业务查询条件、不参与跨系统契约**。
2. **对外身份一律业务 ID**：详情 / 更新 / 删除 / 认领 / 指派等入参用业务编码字符串；查询用业务编码列。
3. **业务 ID 格式**：`{小写业务前缀}{32 位十六进制随机}`，由
   `com.loan.common.util.BizIdGenerator.generate(prefix)` 生成（`SecureRandom`）。
   - 字段类型 `varchar(64)`（`db/migrate-bizid.sql` 已统一扩宽），配 `UNIQUE KEY uk_xxx`。
4. **FK 引用列存业务编码（不设物理外键）**：引用其他表的列用 `varchar` 存对方业务 ID / 编码
   （如 `owner_staff_code` 存 `staff_code`），**禁止 BIGINT 引用列**。
5. **配置项短码保留**：`dept_code` / `staff_code` / `channel_code` / `product_code` / `rule_code`
   等人工识别短码**不**改随机串，作为被引用方编码。

## 前缀映射（记录级业务 ID）

| 表 | 业务 ID 字段 | 前缀 |
|---|---|---|
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
|---|---|---|
| t_lead | recorder_staff_id / owner_staff_id | recorder_staff_code / owner_staff_code（staff_code） |
| t_lead | client_profile_id | client_profile_code（client_code） |
| t_lead_allocation_record | lead_id / from_staff_id / to_staff_id | lead_no / from_staff_code / to_staff_code |
| t_staff | dept_id | dept_code（dept_code） |
| t_department | parent_id / leader_staff_id | parent_code / leader_staff_code |
| t_service_attachment | order_id / client_profile_id | order_no / client_profile_code |
| t_bank_product | bank_channel_id | bank_channel_code（channel_code） |

命名规律：`<业务语义>_code`（员工类）或 `<业务语义>_no`（单据类），值就是被引用表的业务 ID / 短码。

## 配置项编码（可读短码，保留人工可识别）

以下编码需人工识别与维护，**不**改成随机串，作为被引用方编码（与「记录级业务 ID」区分）：

| 表 | 编码字段 | 说明 |
|---|---|---|
| t_department | dept_code | 部门编码 |
| t_staff | staff_code | 工号 |
| t_bank_channel | channel_code | 渠道编码 |
| t_bank_product | product_code | 产品代号 |
| t_rule_category | category_code | 规则分类 |
| t_rule | rule_code | 规则编码 |
| t_product_strategy | strategy_code | 策略编码 |
| t_admission_execution_plan | plan_code | 计划编码 |
| t_*_template | template_code | 各类模板编码 |
| t_*_plan_module | module_code | 模块编码 |
| t_invitation | invitation_code | 邀请码（短码便于分享，保留） |

## 改造策略（分两步，避免一次性大爆炸）

1. **立即生效（新代码）**：新写的业务表 / 业务 ID 一律按本规范；新增记录用 `BizIdGenerator` 生成。
2. **渐进迁移（存量）**：已有表先 `ALTER` 字段 `varchar(32)→varchar(64)` 并确认唯一索引；存量短码数据在迁移窗口批量替换为「前缀 + 32 位随机」，旧短码保留映射表（如需）。

## 唯一索引清单（待落地）

每张业务表确认具备 `UNIQUE KEY uk_<field> (<field>)`。DDL 中已存在的唯一索引保留；缺失的补建。

## 接口约定

- Controller 入参：`@PathVariable String leadNo` / `@RequestBody { "leadNo": "lead8b1c...", "toStaffCode": "ADV001" }`，**不用 Long**。
- Service：按业务编码列 `LambdaQueryWrapper.eq(Entity::getXxxCode, code)` 查询；创建返回业务 ID（`Result<String>`）。
- DTO：出参暴露业务编码，不暴露 `xxxId`；Dubbo 契约同理（如 `ProductDTO.bankChannelCode`）。
- 前端：编号 / 单号列展示业务 ID（截断展示、完整可复制）—— 展示细节见 `loan-web-ui`。

## 契约红线速查

- **契约真源**：`db/loan-db-schema.sql`（表数以该文件为准）→ `loan-service` 代码 → `docs/knowledge-base/`
- **禁止引用**已失效的 `前端交互逻辑蓝图.html` / `output/` 等路径（见 `loan-knowledge`）

## 自检清单

- [ ] Step 0 结论核对是否已输出？
- [ ] Controller / DTO / 跨系统契约里是否出现 `Long id` / `xxxId` 作业务标识？→ 改业务编码
- [ ] Service 是否用 `selectById` / `eq(id)` 做业务查询？→ 按业务编码列查询
- [ ] 新表 / 新实体是否有业务 ID 列 + UNIQUE 索引？
- [ ] 引用其他表的列是否 `varchar` 存业务编码（非 BIGINT）？
- [ ] 生成新业务 ID 是否走 `BizIdGenerator.generate(prefix)`？
- [ ] 新前缀是否在上表登记？未登记的新前缀是否先与用户确认？

## 相关文档

- `docs/knowledge-base/10-历史结论与决策日志.md#结论台账`（**Step 0 必查**）
- `docs/knowledge-base/03-数据模型（DB schema 索引）.md#业务 ID 与索引`
- `docs/knowledge-base/03-数据模型（DB schema 索引）.md#建表规范`
- `docs/knowledge-base/03-数据模型（DB schema 索引）.md#敏感字段处理`
- `docs/knowledge-base/04-后端 API 契约.md#字段命名红线`
- `docs/knowledge-base/04-后端 API 契约.md#关键接口索引`
- 本技能即业务 ID 规范的**唯一真源**。原《业务 ID 规范》md 的独有内容（配置项编码表 / 改造策略 / 唯一索引清单）已并入上文，该 md 已归档为 `docs/plans/archive/业务ID规范.md`（仅作历史留痕，**不再作为规范引用**）。
- `db/migrate-bizid.sql`（编码列宽 32→64）、`db/migrate-bizid-fk.sql`（FK 列编码化）
- 生成入口：`loan-service/src/main/java/com/loan/common/util/BizIdGenerator.java`
