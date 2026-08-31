# 业务 ID 设计规范（架构准则）

> 版本 v1.1 ｜ 2026-08-26 ｜ 适用范围：loan-platform 全部业务表
> v1.1：补充「FK 引用列统一为业务编码」（Wave 2 已落地，`db/migrate-bizid-fk.sql`）

## 一、核心准则

1. **业务主键不以自增 id 为查询条件**：对外暴露、跨系统关联、业务查询一律使用「业务 ID」。
   自增主键 `id` 仅作物理主键与内部关联，不对外暴露、不参与业务语义。
2. **每张业务表都必须有业务 ID 字段 + 唯一索引**（`uk_xxx`）。
3. **业务 ID = 业务前缀编码 + 32 位随机字符串**（如 `client3f2a8b9c1d4e5f6a7b8c9d0e1f2a3b4c`）。

## 二、业务 ID 格式

```
{业务前缀}{32 位十六进制随机}
```

- 前缀：小写业务域缩写（见下方映射表），固定、可读、用于识别类型与路由。
- 随机：32 位十六进制（`SecureRandom`，等价 UUID 去横线），保证全局唯一、不可枚举。
- 字段类型：`varchar(64)`（前缀 ≤ 8 + 32 位随机，留余量）。
- 唯一索引：`UNIQUE KEY uk_xxx (字段名)`。

生成入口：`com.loan.common.util.BizIdGenerator.generate(prefix)`。

## 三、前缀映射表

### 业务 ID（记录级唯一标识，用「前缀 + 32 位随机」）

| 表 | 业务 ID 字段 | 前缀 | 示例 |
|----|-------------|------|------|
| t_client_profile | client_code | client | `client3f2a...` |
| t_lead | lead_no | lead | `lead8b1c...` |
| t_service_order | order_no | order | `ordera4e9...` |
| t_client_submission | submission_no | submit | `submitc7d2...` |
| t_product_approval | approval_no | prdapr | `prdapr5e8f...` |
| t_attachment_download_approval | approval_no | dldapr | `dldapr9a3b...` |
| t_reward_record | reward_no | reward | `reward1f6c...` |
| t_report（报告实例，若有独立表） | report_no | report | `report2d7e...` |
| t_match_trace | trace_uuid | trace | 已是 32 位 UUID，保留 |

### 配置项编码（可读短码，保留，不适用随机规则）

配置项需要人工识别与维护，保留可读短码，**不**改成随机串：

| 表 | 编码字段 | 说明 |
|----|---------|------|
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

## 四、查询与关联约定

- **禁止**在 Controller 入参 / DTO 出参 / 跨系统契约中用自增 `id` 作为业务标识。
- 业务查询（详情 / 更新 / 删除 / 关联）一律传「业务 ID」；后端内部再映射回物理 `id`。
- 前端展示的「编号/单号」列统一展示业务 ID（截断展示，完整可复制）。

## 四·五、FK 引用列统一为业务编码（Wave 2，v1.1）

引用其他表的列**不得用 BIGINT**，一律 `varchar` 存对方业务 ID / 短码，**不设物理外键**（2026-08-26 已迁移，`db/migrate-bizid-fk.sql`）：

| 表 | 旧 BIGINT 列（已删） | 新 VARCHAR 列 | 存的是 |
|----|---------------------|--------------|--------|
| t_lead | recorder_staff_id / owner_staff_id | recorder_staff_code / owner_staff_code | staff_code |
| t_lead | client_profile_id | client_profile_code | client_code |
| t_lead_allocation_record | lead_id / from_staff_id / to_staff_id | lead_no / from_staff_code / to_staff_code | lead_no / staff_code |
| t_staff | dept_id | dept_code | dept_code |
| t_department | parent_id / leader_staff_id | parent_code / leader_staff_code | dept_code / staff_code |
| t_service_attachment | order_id / client_profile_id | order_no / client_profile_code | order_no / client_code |
| t_bank_product | bank_channel_id | bank_channel_code | channel_code |

命名规律：`<业务语义>_code`（员工 / 部门 / 渠道类短码）或 `<业务语义>_no`（单据类业务 ID）。
Java 侧对应：实体字段同列名（`ownerStaffCode`），Service 按编码列查询，Controller 入参用编码字符串（详见 `.workbuddy/skills/loan-biz-id/SKILL.md`）。

## 五、改造策略（分两步，避免一次性大爆炸）

1. **立即生效（新代码）**：新写的业务表/业务 ID 一律按本规范；新增记录用 `BizIdGenerator` 生成。
2. **渐进迁移（存量）**：已有表先 `ALTER` 字段 `varchar(32)→varchar(64)` 并确认唯一索引；存量短码数据在迁移窗口批量替换为「前缀 + 32 位随机」，旧短码保留映射表（如需）。

## 六、唯一索引清单（待落地）

每张业务表确认具备 `UNIQUE KEY uk_<field> (<field>)`。DDL 中已存在的唯一索引保留；缺失的补建。
