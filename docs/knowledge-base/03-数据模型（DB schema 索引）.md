# 数据模型索引

## 真值与迁移

- 完整结构以 `db/loan-db-schema.sql` 为准。
- 增量变更位于 `db/migrate-*.sql` 及明确命名的初始化脚本，按脚本依赖顺序执行。
- 执行迁移前必须从本次启动所选 Nacos 读取实际数据源。
- 开发环境可以重建测试数据，但不得把远程开发库误认为本地库。

## 核心数据域

| 数据域 | 核心表 |
|---|---|
| 组织权限 | `t_staff`、`t_department`、`t_role`、`t_menu`、`t_role_permission`、`t_api_permission`、`t_role_api` |
| 客户线索 | `t_client_profile`、`t_lead`、`t_client_submission`、`t_client_allocation_approval` |
| 匹配报告 | `t_client_screening`、`t_screening_product`、`t_match_trace`、`t_match_rule_log`、`t_report_template` |
| 服务运营 | `t_client_appointment`、`t_staff_outing`、`t_client_follow_record`、`t_client_activity_event`、`t_service_order` |
| 敏感查看 | `t_sensitive_view_grant`、`t_sensitive_view_log`、`t_sensitive_view_approval`、`t_notification` |
| 渠道产品 | `t_channel_user`、`t_bank_channel`、`t_bank_product`、`t_partner_product`、`t_product_approval` |

## 关联规则

1. 跨表业务关联优先使用 `client_code`、`staff_code`、`dept_code`、`report_no`、`approval_no` 等唯一业务键。
2. 唯一业务键必须有唯一索引或满足业务唯一性的组合索引。
3. 列表过滤、数据范围、状态和时间字段按实际查询建立组合索引；不为低选择性字段单独堆叠索引。
4. `phone_plain` 是内部敏感列，应用 DTO、通用查询、导出和普通报表不得选择或序列化该列。
5. 新迁移应幂等，生产执行前在所选开发命名空间验证并记录结果。
