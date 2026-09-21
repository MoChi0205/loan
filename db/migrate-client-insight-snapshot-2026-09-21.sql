-- 客户画像版本快照（09-21 方案 §3.1）。
-- 边界：只新增一张画像版本表，不改客户主档、报告、材料、渠道合作与审批表。
-- 幂等：CREATE TABLE IF NOT EXISTS + information_schema 守卫补列，可重复执行。
--
-- 设计要点：
--   1. 画像不覆盖旧值，按 snapshot_version 递增形成版本链；
--   2. 同一客户最多一个 status='CURRENT'，由生成列 current_flag + 唯一键在数据库层保证；
--   3. risk_flags_json 只写风险提示，禁止写成审批结论；customer_summary 只放客户可读的脱敏摘要；
--   4. 员工复核结果（复核人/时间/意见）落库留痕，操作日志另经 @OpLog 记录。
--
-- 执行后需重启后端：接口清单自动登记，并补齐顾问/主管默认授权。

CREATE TABLE IF NOT EXISTS `t_client_insight_snapshot` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理主键，不对外暴露',
  `snapshot_no` varchar(64) NOT NULL COMMENT '画像快照业务编号',
  `client_code` varchar(64) NOT NULL COMMENT '客户业务编号',
  `report_no` varchar(64) DEFAULT NULL COMMENT '关联客户报告号，可为空',
  `snapshot_version` int NOT NULL COMMENT '同一客户递增版本',
  `dimension_json` json DEFAULT NULL COMMENT '经营规模/现金流/负债/回款/客户集中度/资料完整度等维度',
  `risk_flags_json` json DEFAULT NULL COMMENT '风险提示（不得写成审批结论）',
  `advice_json` json DEFAULT NULL COMMENT '经营改善建议（仅员工视角）',
  `source_summary_json` json DEFAULT NULL COMMENT '来源材料/授权时间/数据期间/可信度/核验状态',
  `customer_summary` varchar(500) DEFAULT NULL COMMENT '客户端可读的裁剪摘要（非承诺性措辞）',
  `generated_at` datetime NOT NULL COMMENT '生成时间',
  `generated_by` varchar(16) NOT NULL DEFAULT 'RULE' COMMENT 'AI/RULE/STAFF',
  `status` varchar(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/REVIEWED/CURRENT/ARCHIVED',
  `reviewed_by` varchar(64) DEFAULT NULL COMMENT '复核人姓名（到人留痕）',
  `reviewed_at` datetime DEFAULT NULL COMMENT '复核时间',
  `review_remark` varchar(500) DEFAULT NULL COMMENT '复核意见',
  `current_flag` tinyint GENERATED ALWAYS AS (CASE WHEN `status` = 'CURRENT' THEN 1 ELSE NULL END) STORED COMMENT '唯一当前版本约束用生成列（仅 CURRENT 置 1）',
  `created_by` varchar(64) DEFAULT NULL,
  `updated_by` varchar(64) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_insight_snapshot_no` (`snapshot_no`),
  UNIQUE KEY `uk_insight_client_version` (`client_code`,`snapshot_version`),
  UNIQUE KEY `uk_insight_client_current` (`client_code`,`current_flag`),
  KEY `idx_insight_client_status` (`client_code`,`status`),
  KEY `idx_insight_client_generated` (`client_code`,`generated_at`),
  KEY `idx_insight_report_no` (`report_no`),
  CONSTRAINT `chk_insight_status` CHECK (`status` IN ('DRAFT','REVIEWED','CURRENT','ARCHIVED')),
  CONSTRAINT `chk_insight_generated_by` CHECK (`generated_by` IN ('AI','RULE','STAFF'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户画像版本快照（版本链非覆盖，同一客户仅一个 CURRENT）';

-- 守卫补列：若表已由更早的手工变更创建，补齐缺失列，避免再次执行报错。
SET @db := DATABASE();

SET @sql := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `t_client_insight_snapshot` ADD COLUMN `customer_summary` varchar(500) DEFAULT NULL COMMENT ''客户端可读的裁剪摘要（非承诺性措辞）'' AFTER `source_summary_json`',
  'SELECT ''customer_summary exists''')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_client_insight_snapshot' AND COLUMN_NAME = 'customer_summary');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `t_client_insight_snapshot` ADD COLUMN `reviewed_by` varchar(64) DEFAULT NULL COMMENT ''复核人姓名（到人留痕）'' AFTER `status`',
  'SELECT ''reviewed_by exists''')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_client_insight_snapshot' AND COLUMN_NAME = 'reviewed_by');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `t_client_insight_snapshot` ADD COLUMN `reviewed_at` datetime DEFAULT NULL COMMENT ''复核时间'' AFTER `reviewed_by`',
  'SELECT ''reviewed_at exists''')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_client_insight_snapshot' AND COLUMN_NAME = 'reviewed_at');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `t_client_insight_snapshot` ADD COLUMN `review_remark` varchar(500) DEFAULT NULL COMMENT ''复核意见'' AFTER `reviewed_at`',
  'SELECT ''review_remark exists''')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_client_insight_snapshot' AND COLUMN_NAME = 'review_remark');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
