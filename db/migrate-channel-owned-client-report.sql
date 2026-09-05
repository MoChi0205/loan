-- ============================================================
-- 渠道本人客户/分析报告查询索引（D50）
-- 说明：脚本通过 information_schema 预检后动态补索引，可重复执行。
-- 查询链：channel userNo -> t_lead -> t_client_profile -> t_client_screening。
-- ============================================================
SET NAMES utf8mb4;
SET @schema_name = DATABASE();

SET @ddl = IF(EXISTS(
    SELECT 1 FROM information_schema.statistics
     WHERE table_schema = @schema_name AND table_name = 't_lead'
       AND index_name = 'idx_recorder_source_status'),
    'SELECT ''t_lead.idx_recorder_source_status 已存在'' AS result',
    'ALTER TABLE t_lead ADD KEY idx_recorder_source_status (recorder_staff_code, source, follow_status, created_at)');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(EXISTS(
    SELECT 1 FROM information_schema.statistics
     WHERE table_schema = @schema_name AND table_name = 't_lead'
       AND index_name = 'idx_client_profile_code'),
    'SELECT ''t_lead.idx_client_profile_code 已存在'' AS result',
    'ALTER TABLE t_lead ADD KEY idx_client_profile_code (client_profile_code)');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(EXISTS(
    SELECT 1 FROM information_schema.statistics
     WHERE table_schema = @schema_name AND table_name = 't_client_profile'
       AND index_name = 'idx_lead_no'),
    'SELECT ''t_client_profile.idx_lead_no 已存在'' AS result',
    'ALTER TABLE t_client_profile ADD KEY idx_lead_no (lead_no)');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(EXISTS(
    SELECT 1 FROM information_schema.statistics
     WHERE table_schema = @schema_name AND table_name = 't_client_screening'
       AND index_name = 'idx_client_created'),
    'SELECT ''t_client_screening.idx_client_created 已存在'' AS result',
    'ALTER TABLE t_client_screening ADD KEY idx_client_created (client_profile_code, created_at)');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT table_name, index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS columns
  FROM information_schema.statistics
 WHERE table_schema = @schema_name
   AND ((table_name = 't_lead' AND index_name IN ('idx_recorder_source_status','idx_client_profile_code'))
     OR (table_name = 't_client_profile' AND index_name = 'idx_lead_no')
     OR (table_name = 't_client_screening' AND index_name = 'idx_client_created'))
 GROUP BY table_name, index_name
 ORDER BY table_name, index_name;
