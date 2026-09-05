# 03 · 数据模型（DB schema 索引）

> 完整 schema 在 `db/loan-db-schema.sql`；当前表数用 `rg -c '^CREATE TABLE' db/loan-db-schema.sql` 获取。本索引列出**新增/修改必查**的关键表。

## 关键业务表

| 表 | 用途 | 新增/关注 |
|----|------|----------|
| `t_client_profile`（客户档案） | 客户主数据；`owner_staff_code IS NULL` 是未分配客户池唯一真源 | 含 `owner_staff_code`、`phone_hash`、`credit_code_hash`、`wx_openid_hash`；**C26 新增** `last_followed_at`（超期回收判定基准，归属/审批通过时 `touchAssignment` 刷新）、`assign_blocked_until`（回收冷却到期，冷却期内原归属人不可认领/不可被直接分配） |
| `t_client_screening`（初筛报告） | 报告汇总，档位 + 数量展示，不含银行/产品名 | **唯一与产品明细的关联点**（`report_no`）；渠道本人报告走 `idx_client_created(client_profile_code,created_at)` |
| `t_screening_product`（报告命中产品明细） | **C19 新增** | `report_no` + `product_code` + `hit_result` + `match_score` + uk_report_product |
| `t_match_trace`（匹配审计） | 全链路 trace_uuid | **仅管理端可见** |
| `t_match_rule_log`（匹配规则日志） | 双结果审计 | trace_id + rule_code + step_result |
| `t_bank_channel` / `t_bank_product` / `t_bank_contact` | 银行渠道/产品/联系人 | C4/C9 关联 |
| `t_bank_product_city`（产品服务城市关系） | 产品与省市的多值关系 | `product_city_code` 为 16 位业务编码；`uk_product_city_code` + `uk_product_city(product_code,province,city)` 双重唯一 |
| `t_channel_user_list`（渠道本地名单） | 渠道/客群维度的本地白黑名单 | `list_code` 为 16 位业务编码；`uk_list_code` + 自然业务键唯一索引保证幂等 |
| `t_partner_product`（合作库上架） | 银行产品上架合作库，含 `cooperate_until` 有效期 | T-30/T-7 到期提醒 |
| `t_product_approval`（产品审核工单） | C9 复用：applyType=CREATE/DELETE + approveStatus | 含 4 状态扩展 |
| `t_client_allocation_approval`（**分配审批**） | 顾问认领与管理指定共用审批单 | `approval_no` / `client_code` / `applicant_staff_code` / `from_owner_staff_code`（转移来源，P0-3）/ `approve_status` / `apply_source`(ADVISER_CLAIM/MANAGER_ASSIGN/ADVISER_TRANSFER) / `apply_operator_code`；`pending_key` 仅待审时等于客户编码 |
| `t_client_recycle_config`（**客户回收配置**） | **C26 新增** 全局单行回收规则 | `config_key=GLOBAL`；`recycle_enabled`(1/0) / `recycle_days`(超期天数) / `warn_days`(预警天数) / `cooldown_days`(回收后冷却)；无配置行时 `ClientAllocationService.recycleConfig()` 返回 null 由调用方按默认值兜底 |
| `t_invitation`（分享引荐） | 分享码生成、消费和引荐归因；不负责服务顾问归属 | `referrer_client_code` / `used_by_client_code` 使用客户业务编码；绑定不回写 `owner_staff_code` |
| `t_industry_benchmark`（**行业均值**） | **T1 新增** | dimensions（industry / annual_tax / annual_invoice / found_years / tax_rate）+ 均值；`IndustryBenchmarkService.avgByDimension`；缺失兜底硬编码 45/50/55/60/55 |
| `t_lead`（线索） | **T4 新增** | `source` 字典含 CHANNEL（旧 `MINI` 已废弃）；渠道录入先 `PENDING_APPROVAL`，终审通过后进公海 `owner_staff_code=NULL`；`recorder_staff_code` 保存稳定渠道 `userNo`；渠道本人数据查询走 `idx_recorder_source_status`，转化关联走 `idx_client_profile_code`；AES+SHA 处理 `phone`/`credit_code`；唯一索引冲突返友好文案不泄归属人 |
| `t_lead_ent_ext`（线索企业扩展字段） | **T4 新增** | `ent_name` / `credit_code` / `industry` / `found_years` / `annual_tax_amount` / `annual_invoice_amount`（对应 lead/submit body 企业字段） |
| `t_ocr_record`（OCR 回灌记录） | **T2 新增** | `biz_scene` / `biz_id` / `biz_code`（业务ID：reportNo/clientCode/productCode，红线#3）/ `extract_json` / `confidence_avg`；`data_json._ocrMeta.version` 派生 `materialVersion`（D26 修正：补录 biz_code 列，实体与表已对齐） |
| `t_service_attachment`（材料附件） | **T2 新增 `report_no` 列** | 关联诊断材料回灌；原有 `file_key` / `url` 不变（向后兼容） |
| `t_client_submission`（提交单） | 经营事实 facts（`data_json` JSON） | **C19 B2 诊断算法数据源** |
| `t_lead_allocation_record`（线索流转流水） | 归属流转记录（从属线索 ID） | action_type 字典：MANUAL/AUTO/CLAIM/RECYCLE/TRANSFER/CONVERT |
| `t_attachment_download_approval` | 资料下载审批（C9 之外的无水印下载审批） | 独立于产品审批 |
| `t_staff`（员工映射） | CRM SSO 员工 → `dept_code`（业务编码，非 dept_id，D24/D26 修正）→ roleCode 三级绑定 | **staffName 来源** |
| `t_role` / `t_menu` / `t_role_permission` / `t_dept_approver` / `t_fallback_consultant` | 角色权限菜单体系 | `t_dept_approver` 用于部门审核人配置 |

## 业务 ID 与索引

- 主键：`id bigint AUTO_INCREMENT`（内部自增，不外用）
- 业务 ID：`xxx_no varchar(64)` + `UNIQUE KEY uk_xxx_no`（`BizIdGenerator.generate("xxx")` 生成）
- 用户确认的短编码例外：`t_channel_user_list.list_code=culist+10 位小写字母数字`、`t_bank_product_city.product_city_code=pcity+11 位小写字母数字`，两者总长均固定 16，分别配置唯一索引；其余记录级业务 ID 仍按默认规则。
- 重要唯一约束：`t_product_approval.uk_rule_code`、`t_client_allocation_approval.uk_approval_no`、`t_client_allocation_approval.uk_pending_key`（同客户只允许一条待审单）、`t_screening_product.uk_report_product`

## 建表规范

- 必须有 `created_at` / `updated_at datetime DEFAULT CURRENT_TIMESTAMP [ON UPDATE]`
- 必须有 `created_by` / `updated_by varchar(64)`（存操作人姓名）
- 必须有中文 COMMENT（每列说明）
- 引擎 `InnoDB` + `utf8mb4_0900_ai_ci`
- 必须有业务唯一索引 + 关键查询索引（如 `idx_status_timeout`、`idx_client_status`）

## 敏感字段处理

- 手机号 / 信用代码：**SHA-256 摘要**存 `*_hash varchar(64)`，**明文存 AES 加密**字段（phone / credit_code）
- 查询前对入参同样摘要，明文不出现在 SQL

## 跳到

- 接口契约：`04-后端 API 契约.md`（哪些接口涉及哪些表）
- 业务结论：`06-业务结论沉淀索引（C1-C26）.md`
