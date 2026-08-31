---
name: loan-database
description: >-
  loan-main 数据库优化规范。设计新表、新增索引、写报表统计、写聚合 SQL、实现列表分页与排序时使用；
  核心：日期字段必建索引、报表统计用聚合 SQL 避免全表扫描、分页排序走 PageOrder 白名单防注入。
---

# 数据库优化规范（loan-database）

## Step 0 · 前置门禁（强制，不可跳过）

1. **先查历史结论**：`grep -n "索引\|建表\|表数\|schema\|SQL" docs/knowledge-base/10-历史结论与决策日志.md#结论台账`，
   从**最新条目往下**读（台账按时间倒序，最新在最上方）；**命中即遵守**；状态为「已被 Dxx 替代」则跳读 Dxx。
   > ⚠️ 取号前必须自己刚跑 `grep -o "^| D[0-9-]*" docs/knowledge-base/10-历史结论与决策日志.md` 取实时最大编号（禁采信转述 / 记忆 / 分配表）。
2. **无结论且不确定 → 停下来问用户**，禁止臆断（改表结构属契约变更，必须先确认）。
3. **再读元技能** `loan-knowledge`（`.workbuddy/skills/loan-knowledge/SKILL.md`），按其 Step 1–5 执行。
4. 回复开头输出：`【结论核对】命中 Dx-x（…）/ 未命中（grep 关键词：…）`。

## 何时使用

- 新增或修改表结构、索引（DBA / 开发）
- 写报表统计、列表分页、趋势查询（Report / Overview / Trend）
- 给 page 接口加排序参数
- **改 SQL 或实体前先读本规范 + `loan-backend` + `loan-biz-id`**

## 一、日期字段索引（用户硬性要求：报表统计 / 页面查询避免全表扫描）

**规则**：所有会被 `ORDER BY` / 范围查询 / 聚合（`GROUP BY` 月份）的日期列必须建索引：

| 列 | 索引 | 覆盖查询 |
|---|---|---|
| created_at / updated_at | `idx_created_at` / `idx_updated_at` | 列表默认倒序、按时间过滤 |
| deal_time | `idx_status_dealtime`(status, deal_time) | 成交趋势按成交时间聚合 |
| send_time | `idx_send_time` | 短信发送时间查询 |
| settle_time | `idx_settle_time` | 奖励结算时间查询 |
| executed_at | `idx_executed` | 匹配审计时间线 |
| timeout_at | `idx_status_timeout`(approve_status, timeout_at) | 审批超时扫描 |

已落地脚本：`db/migrate-index-dates.sql`（14 个索引）。**新增业务表时必须给时间列补索引**
（MySQL 8 无 `ADD INDEX IF NOT EXISTS`，执行前用 `information_schema.statistics` 检查幂等）。

## 二、报表统计：聚合 SQL，禁止全表扫描

**禁止** `selectList(全表) + 内存 sum/count`（数据量大时拖垮 DB）。

**必须**用聚合 SQL（MyBatis-Plus `QueryWrapper.select(...)` + `selectMaps`）：

```java
QueryWrapper<ServiceOrder> wrapper = new QueryWrapper<ServiceOrder>()
        .select("COUNT(*) AS cnt", "IFNULL(SUM(deal_amount),0) AS amt")
        .eq("status", ServiceOrder.STATUS_DEAL)
        .ge("deal_time", from).lt("deal_time", to);
List<Map<String, Object>> rows = orderMapper.selectMaps(wrapper);
```

- 月度趋势：按月 `ge/lt` 边界分 12/24 段聚合（走日期索引）
- 总览 COUNT：`selectCount(null)` 走主键 / 统计索引，可接受
- 参考实现：`ReportService.orderTrend / rewardTrend / overview / orderSum / rewardSum`

## 三、分页排序：PageOrder 白名单（防注入 + 跨页生效）

**规则**：所有 page 接口支持 `orderBy / orderDir` 参数（前端列头点击排序跨页生效）。

- 工具：`com.loan.common.util.PageOrder.apply(wrapper, orderBy, orderDir, ORDER_FIELDS, defaultColumn)`
- 每个 Service 定义静态 `ORDER_FIELDS`（`Map<String, SFunction<实体, ?>>`，**白名单**，非法字段回退默认倒序）
- Controller 加两个可选参数：`@RequestParam(required=false) String orderBy, String orderDir`
- 前端：`useTable` 提供 `handleSortChange`（el-table `@sort-change` 绑定），时间列加 `sortable` 属性
- 一个 Service 多个 page 方法且实体不同 → 各自独立的 `XXX_ORDER_FIELDS`
  （如 `SmsAdminService` 的 `ORDER_FIELDS` / `RECORD_ORDER_FIELDS`、`ApprovalService` 的 `ORDER_FIELDS` / `DOWNLOAD_ORDER_FIELDS`）

## 四、分页查询其他注意

- 列表默认 `orderByDesc(createdAt)`（走 `idx_created_at`）
- 涉及多表关联取名称（客户名 / 产品名）时：主表分页（走主键 / 索引）→ 一次性 `in` 查名称映射，**禁止逐行子查询**
- 筛选字段（status / 客群 / 维度）组合查询时建复合索引（如 `idx_owner_status_follow`）

## 契约红线速查

- **契约真源（D7 / D0-2）**：`db/loan-db-schema.sql`（**68** 张 `t_` 表，以 `grep -c "CREATE TABLE"` 为准）
  → `loan-service` 代码 → `docs/knowledge-base/`
- **「65 表 / 66 表」是错的**，任何新增文档一律写 **68**
- **禁止引用** `output/` 目录与两张不存在的「逻辑蓝图.html」（见 `loan-knowledge`）

## 自检清单

- [ ] Step 0 结论核对是否已输出？
- [ ] 新表的时间列是否建索引？`db/migrate-index-dates.sql` 是否补充？
- [ ] 报表 / 统计是否用聚合 SQL（`selectMaps`）？有无 `selectList 全表 + 内存聚合`？
- [ ] page 接口是否支持 `orderBy` / `orderDir`？是否走 `PageOrder` 白名单？
- [ ] 分页是否默认走 `created_at` 索引排序？
- [ ] 名称映射是否批量 `in` 查询（非逐行查）？
- [ ] 新增表是否同步 `db/loan-db-schema.sql` 与 `docs/knowledge-base/03-*`？表数是否为 68+N？

## 相关文档

- `docs/knowledge-base/10-历史结论与决策日志.md#结论台账`（**Step 0 必查**，D0-2 表数真值）
- `docs/knowledge-base/03-数据模型（DB schema 索引）.md#关键业务表`
- `docs/knowledge-base/03-数据模型（DB schema 索引）.md#业务 ID 与索引`
- `docs/knowledge-base/03-数据模型（DB schema 索引）.md#建表规范`
- `docs/knowledge-base/03-数据模型（DB schema 索引）.md#敏感字段处理`
- `docs/knowledge-base/06-业务结论沉淀索引（C1-C19）.md#结论速查`
- `db/loan-db-schema.sql`（全量 DDL，表数以此为准：`grep -c "CREATE TABLE" db/loan-db-schema.sql`）、`db/migrate-index-dates.sql`（日期索引脚本）
- 参考实现：`ReportService` / `PageOrder` / 各 `*Service.page`
