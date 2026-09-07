-- ============================================================
-- 缺口补齐补丁（幂等，可重复执行）
-- 背景：prd 库 loan_db 的 schema 落后于代码，运行时报两类错误：
--   1) Table 'loan_db.t_material_review' doesn't exist
--   2) Unknown column 'match_trace_no' in 't_client_submission'
-- 对应未执行的迁移：
--   - db/migrate-material-review-gating-2026-09-05.sql（建 t_material_review + t_ocr_record 加列 + 种子）
--   - db/migrate-mini-bizcode.sql（t_client_submission.match_trace_no）
-- 本文件只补缺口，且全部用 IF NOT EXISTS / information_schema 守卫，避免重跑报错。
-- ⚠️ 执行前请先备份数据库；在目标库（prd）执行需相关授权确认。
-- 执行方式：mysql -uroot -p loan_db --default-character-set=utf8mb4 < db/migrate-patch-missing-2026-09-07.sql
-- ============================================================
SET NAMES utf8mb4;

-- ---------- 1) 新建材料复核审批单表（若缺失） ----------
-- 与 migrate-material-review-gating-2026-09-05.sql 的 DDL 保持一致（最新意图）。
CREATE TABLE IF NOT EXISTS `t_material_review` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `review_no` varchar(64) NOT NULL COMMENT '复核单号(业务唯一ID: matrev + 32位随机)',
  `ocr_file_key` varchar(255) DEFAULT NULL COMMENT '关联OCR文件业务键(t_ocr_record.file_key)',
  `biz_type` varchar(32) DEFAULT NULL COMMENT '资料类型(ID_CARD/ BUSINESS_LICENSE/FINANCIAL_STATEMENT/CONTRACT/DUE_DILIGENCE/OTHER)',
  `client_profile_code` varchar(64) DEFAULT NULL COMMENT '客户编码(业务ID)',
  `report_no` varchar(64) DEFAULT NULL COMMENT '关联报告编号(诊断材料回灌用)',
  `pending_facts_json` json DEFAULT NULL COMMENT '待复核事实JSON(VLM抽取后经t_extract_field_def映射的规范facts)',
  `review_status` varchar(32) NOT NULL DEFAULT 'PENDING_REVIEW' COMMENT '复核状态(PENDING_REVIEW/APPROVED/REJECTED)',
  `reviewer_staff_code` varchar(64) DEFAULT NULL COMMENT '审批人工号(业务编码)',
  `review_opinion` varchar(512) DEFAULT NULL COMMENT '审批意见(驳回必填)',
  `review_time` datetime DEFAULT NULL COMMENT '复核完成时间',
  `submission_no` varchar(64) DEFAULT NULL COMMENT '回灌后的提交单号',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人(上传操作人)',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_review_no` (`review_no`),
  KEY `idx_review_status` (`review_status`),
  KEY `idx_review_ocr_file` (`ocr_file_key`),
  KEY `idx_review_client` (`client_profile_code`),
  KEY `idx_review_report` (`report_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='材料复核审批单(上传识别结果门控，审批通过才回灌客数据)';

-- ---------- 2) t_ocr_record 补门控列（若缺失） ----------
-- 同源 migrate-material-review-gating-2026-09-05.sql 第 1) 节，避免 OCR 门控路径后续报错。
SET @s = (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 't_ocr_record' AND column_name = 'review_status') = 0,
  'ALTER TABLE t_ocr_record ADD COLUMN review_status varchar(32) DEFAULT NULL COMMENT ''复核状态(PENDING_REVIEW/APPROVED/REJECTED)'' AFTER updated_at, ADD COLUMN visible_flag tinyint(1) DEFAULT 0 COMMENT ''客户可见标志(0不可见/1可见)'' AFTER review_status, ADD INDEX idx_ocr_review_status (review_status)',
  'SELECT 1'));
PREPARE stmt FROM @s;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 3) t_extract_field_def 映射种子（幂等，已存在则跳过） ----------
-- 与 migrate-material-review-gating-2026-09-05.sql 第 3) 节一致；INSERT IGNORE 防重复。
INSERT IGNORE INTO `t_extract_field_def` (`field_code`, `field_name`, `field_type`, `customer_group`, `extract_rule_json`, `status`, `created_by`, `created_at`)
VALUES
  ('entName',         '企业名称',     'STRING', 'COMMON',    '{"sourceKeys":["entName","企业名称","公司名称","单位名称"],"targetFactKey":"entName"}',                       'ACTIVE', 'system', NOW()),
  ('creditCode',      '统一社会信用代码', 'STRING', 'COMMON', '{"sourceKeys":["creditCode","统一社会信用代码","税号","社会信用代码"],"targetFactKey":"creditCode"}',          'ACTIVE', 'system', NOW()),
  ('industry',        '所属行业',     'STRING', 'COMMON',    '{"sourceKeys":["industry","所属行业","行业","经营范围"],"targetFactKey":"industry"}',                         'ACTIVE', 'system', NOW()),
  ('foundYears',      '成立年限',     'NUMBER', 'COMMON',    '{"sourceKeys":["foundYears","成立年限","经营年限","存续年限"],"transform":"YUAN","targetFactKey":"foundYears"}', 'ACTIVE', 'system', NOW()),
  ('annualTaxAmount',   '年纳税额',  'NUMBER', 'ENTERPRISE', '{"sourceKeys":["annualTaxAmount","年纳税额","年度纳税额","纳税总额","实缴税额"],"transform":"YUAN","targetFactKey":"annualTaxAmount"}', 'ACTIVE', 'system', NOW()),
  ('annualInvoiceAmount','年开票额', 'NUMBER', 'ENTERPRISE', '{"sourceKeys":["annualInvoiceAmount","年开票额","年度开票额","开票总额","销售开票金额"],"transform":"YUAN","targetFactKey":"annualInvoiceAmount"}', 'ACTIVE', 'system', NOW()),
  ('annualRevenue',   '年营收',       'NUMBER', 'ENTERPRISE', '{"sourceKeys":["annualRevenue","年营收","年度营收","营业收入","营业额"],"transform":"YUAN","targetFactKey":"annualRevenue"}', 'ACTIVE', 'system', NOW()),
  ('employeeCount',   '从业人数',     'NUMBER', 'ENTERPRISE', '{"sourceKeys":["employeeCount","从业人数","员工人数","在职人数","参保人数"],"transform":"YUAN","targetFactKey":"employeeCount"}', 'ACTIVE', 'system', NOW()),
  ('registeredCapital','注册资本',    'NUMBER', 'ENTERPRISE', '{"sourceKeys":["registeredCapital","注册资本","注册资金","认缴资本"],"transform":"YUAN","targetFactKey":"registeredCapital"}', 'ACTIVE', 'system', NOW());

-- ---------- 4) t_client_submission 补 match_trace_no（若缺失） ----------
-- 同源 migrate-mini-bizcode.sql 第 4) 节。仅补列，保留旧 match_trace_id（业务查询已走 match_trace_no）。
-- 如需彻底对齐，确认 match_trace_no 已回填后可手动：ALTER TABLE t_client_submission DROP COLUMN match_trace_id;
SET @s2 = (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 't_client_submission' AND column_name = 'match_trace_no') = 0,
  'ALTER TABLE t_client_submission ADD COLUMN match_trace_no varchar(64) DEFAULT NULL COMMENT ''关联匹配审计链路UUID(业务编码,替代原bigint match_trace_id)'' AFTER match_trace_id',
  'SELECT 1'));
PREPARE stmt2 FROM @s2;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;

-- ---------- 5)（可选）回填 match_trace_no ----------
-- 仅当旧 match_trace_id 列仍存在且需对齐历史数据时执行；缺失旧列则自动跳过。
SET @s3 = (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 't_client_submission' AND column_name = 'match_trace_id') > 0,
  'UPDATE t_client_submission s LEFT JOIN t_match_trace t ON t.id = s.match_trace_id SET s.match_trace_no = t.trace_uuid WHERE s.match_trace_no IS NULL',
  'SELECT 1'));
PREPARE stmt3 FROM @s3;
EXECUTE stmt3;
DEALLOCATE PREPARE stmt3;
