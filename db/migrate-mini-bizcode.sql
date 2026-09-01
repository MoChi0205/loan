-- ============================================================
-- 业务主键业务编码化（P0-5 评审决策 #1/#3）：合作库与小程序侧相关表 BIGINT FK → VARCHAR 业务编码
-- 依据：design-three-terminal.md §8-1 已决「业务主键一律用业务编码，不用自增 id」
--       业务唯一 ID = 小写前缀 + 32 位随机（BizIdGenerator 约定）
-- 执行方式：mysql -uroot -p loan_db --default-character-set=utf8mb4 < db/migrate-mini-bizcode.sql
-- ⚠️ 一次性迁移，执行前请先备份数据库；转换依赖关联表存量数据，确认 t_bank_product /
--    t_client_profile / t_match_trace / t_report_template 数据完整后再执行。
-- ============================================================
SET NAMES utf8mb4;
SET @OLD_FOREIGN_KEY_CHECKS = @@FOREIGN_KEY_CHECKS;
SET FOREIGN_KEY_CHECKS = 0;

-- ---------- 1. t_partner_product：bank_product_id(bigint) → bank_product_code(varchar 业务编码) ----------
-- 说明：原唯一键 uk_bank_product_id 建在 bigint 列上；迁移为 varchar 业务编码列后，
--       唯一键改为 uk_bank_product_code。存量数据按 id → product_code 从 t_bank_product 回填。
ALTER TABLE `t_partner_product`
  ADD COLUMN `bank_product_code` varchar(64) DEFAULT NULL COMMENT '银行产品业务编码(小写前缀+32位,替代原bigint bank_product_id)' AFTER `bank_product_id`;

UPDATE `t_partner_product` p LEFT JOIN `t_bank_product` b ON b.id = p.bank_product_id
   SET p.bank_product_code = b.product_code;

ALTER TABLE `t_partner_product`
  DROP INDEX `uk_bank_product_id`,
  DROP COLUMN `bank_product_id`,
  MODIFY COLUMN `bank_product_code` varchar(64) NOT NULL COMMENT '银行产品业务编码(小写前缀+32位,替代原bigint bank_product_id)',
  ADD UNIQUE KEY `uk_bank_product_code` (`bank_product_code`);

-- ---------- 2. t_personal_profile：client_profile_id(bigint) → client_profile_code(varchar 业务编码) ----------
ALTER TABLE `t_personal_profile`
  ADD COLUMN `client_profile_code` varchar(64) DEFAULT NULL COMMENT '客户编码(业务ID:client+32位随机,替代原bigint client_profile_id)' AFTER `client_profile_id`;

UPDATE `t_personal_profile` p LEFT JOIN `t_client_profile` c ON c.id = p.client_profile_id
   SET p.client_profile_code = c.client_code;

ALTER TABLE `t_personal_profile`
  DROP INDEX `uk_client_profile_id`,
  DROP COLUMN `client_profile_id`,
  MODIFY COLUMN `client_profile_code` varchar(64) NOT NULL COMMENT '客户编码(业务ID:client+32位随机)',
  ADD UNIQUE KEY `uk_client_profile_code` (`client_profile_code`);

-- ---------- 3. t_personal_auth：client_profile_id(bigint) → client_profile_code(varchar 业务编码) ----------
ALTER TABLE `t_personal_auth`
  ADD COLUMN `client_profile_code` varchar(64) DEFAULT NULL COMMENT '客户编码(业务ID:client+32位随机,替代原bigint client_profile_id)' AFTER `client_profile_id`;

UPDATE `t_personal_auth` a LEFT JOIN `t_client_profile` c ON c.id = a.client_profile_id
   SET a.client_profile_code = c.client_code;

ALTER TABLE `t_personal_auth`
  DROP INDEX `idx_client_type`,
  DROP COLUMN `client_profile_id`,
  MODIFY COLUMN `client_profile_code` varchar(64) NOT NULL COMMENT '客户编码(业务ID:client+32位随机)',
  ADD KEY `idx_client_type` (`client_profile_code`, `auth_type`);

-- ---------- 4. t_client_submission：match_trace_id(bigint) → match_trace_no(varchar 业务编码) ----------
-- 说明：match_trace_id 关联 t_match_trace.id，业务化后存 t_match_trace.trace_uuid（链路UUID，业务编码）。
ALTER TABLE `t_client_submission`
  ADD COLUMN `match_trace_no` varchar(64) DEFAULT NULL COMMENT '关联匹配审计链路UUID(业务编码,替代原bigint match_trace_id)' AFTER `match_trace_id`;

UPDATE `t_client_submission` s LEFT JOIN `t_match_trace` t ON t.id = s.match_trace_id
   SET s.match_trace_no = t.trace_uuid;

ALTER TABLE `t_client_submission`
  DROP COLUMN `match_trace_id`;

-- ---------- 5. t_client_screening：补业务编码列 client_profile_code / match_trace_no / template_code ----------
-- 说明：实体 com.loan.report.entity.ClientScreening 已按 clientProfileCode / matchTraceUuid / templateCode
--       字符串字段编程，本迁移为线上 bigint 列补齐对应业务编码列并回填，使 DDL 与实体对齐。
ALTER TABLE `t_client_screening`
  ADD COLUMN `client_profile_code` varchar(64) DEFAULT NULL COMMENT '客户编码(业务ID:client+32位随机)' AFTER `client_profile_id`,
  ADD COLUMN `match_trace_no` varchar(64) DEFAULT NULL COMMENT '匹配审计链路UUID(业务编码)' AFTER `match_trace_id`,
  ADD COLUMN `template_code` varchar(64) DEFAULT NULL COMMENT '报告模板编码(版本锁定)' AFTER `template_id`;

UPDATE `t_client_screening` s
  LEFT JOIN `t_client_profile` c ON c.id = s.client_profile_id
  LEFT JOIN `t_match_trace` t ON t.id = s.match_trace_id
  LEFT JOIN `t_report_template` r ON r.id = s.template_id
   SET s.client_profile_code = c.client_code,
       s.match_trace_no = t.trace_uuid,
       s.template_code = r.template_code;

-- 保留原 bigint 列作为内部审计回填口径，业务查询一律走业务编码列；
-- 如确认无历史依赖，可再执行：
--   ALTER TABLE `t_client_screening` DROP INDEX `idx_client`, DROP INDEX `idx_trace`,
--     DROP COLUMN `client_profile_id`, DROP COLUMN `match_trace_id`, DROP COLUMN `template_id`,
--     ADD KEY `idx_client` (`client_profile_code`), ADD KEY `idx_trace` (`match_trace_no`);

-- ---------- 6. t_match_trace：补业务编码列 client_profile_code / submission_no ----------
-- 说明：实体 com.loan.audit.entity.MatchTrace 当前仍为 clientProfileId / submissionId（Long，调试场景 0）；
--       本迁移补齐业务编码列供业务查询对齐，实体后续批次对齐（本次不强制删除 bigint 列）。
ALTER TABLE `t_match_trace`
  ADD COLUMN `client_profile_code` varchar(64) DEFAULT NULL COMMENT '客户编码(业务ID:client+32位随机)' AFTER `client_profile_id`,
  ADD COLUMN `submission_no` varchar(64) DEFAULT NULL COMMENT '提交单号(业务ID:submit+32位随机)' AFTER `submission_id`;

UPDATE `t_match_trace` t
  LEFT JOIN `t_client_profile` c ON c.id = t.client_profile_id
  LEFT JOIN `t_client_submission` s ON s.id = t.submission_id
   SET t.client_profile_code = c.client_code,
       t.submission_no = s.submission_no;

ALTER TABLE `t_match_trace`
  ADD KEY `idx_client_code` (`client_profile_code`),
  ADD KEY `idx_submission_no` (`submission_no`);

SET FOREIGN_KEY_CHECKS = @OLD_FOREIGN_KEY_CHECKS;

-- ============================================================
-- 迁移后校验（应全部返回空/0）：
--   SELECT COUNT(*) FROM t_partner_product WHERE bank_product_code IS NULL;        -- 0
--   SELECT COUNT(*) FROM t_personal_profile WHERE client_profile_code IS NULL;     -- 0
--   SELECT COUNT(*) FROM t_personal_auth WHERE client_profile_code IS NULL;        -- 0
--   SELECT COUNT(*) FROM t_client_screening WHERE client_profile_code IS NULL;     -- 0
--   SELECT COUNT(*) FROM t_match_trace WHERE client_profile_code IS NULL;          -- 允许存在历史 0 关联（调试留痕）
-- 与实体对齐说明：
--   t_partner_product → PartnerProduct.bankProductCode（varchar）
--   t_personal_profile / t_personal_auth → PersonalProfile.clientProfileCode / PersonalAuth.clientProfileCode（varchar）
--   t_client_submission → ClientSubmission.matchTraceNo（varchar）
--   t_client_screening → ClientScreening.clientProfileCode / matchTraceUuid / templateCode（varchar）
--   t_match_trace → MatchTrace.clientProfileCode / submissionNo（varchar，实体后续批次对齐）
-- ============================================================
