-- 客户手机号受控查看：客户档案与线索共用留痕表，手机号继续 AES 加密落库。
-- 执行前请连接当前启动所选 Nacos application.properties 指向的数据源。
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema=DATABASE() AND table_name='t_sensitive_view_grant' AND column_name='client_code')=0,
  'ALTER TABLE t_sensitive_view_grant ADD COLUMN client_code varchar(64) DEFAULT NULL COMMENT ''客户业务编码'' AFTER lead_no',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT IS_NULLABLE FROM information_schema.columns
  WHERE table_schema=DATABASE() AND table_name='t_sensitive_view_grant' AND column_name='lead_no')='NO',
  'ALTER TABLE t_sensitive_view_grant MODIFY COLUMN lead_no varchar(64) DEFAULT NULL COMMENT ''线索业务编码，与客户编码二选一''',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT IS_NULLABLE FROM information_schema.columns
  WHERE table_schema=DATABASE() AND table_name='t_sensitive_view_log' AND column_name='lead_no')='NO',
  'ALTER TABLE t_sensitive_view_log MODIFY COLUMN lead_no varchar(64) DEFAULT NULL COMMENT ''线索业务编码，与客户编码二选一''',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema=DATABASE() AND table_name='t_sensitive_view_log' AND column_name='client_code')=0,
  'ALTER TABLE t_sensitive_view_log ADD COLUMN client_code varchar(64) DEFAULT NULL COMMENT ''客户业务编码'' AFTER lead_no',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema=DATABASE() AND table_name='t_sensitive_view_grant' AND index_name='uk_user_client')=0,
  'ALTER TABLE t_sensitive_view_grant ADD UNIQUE KEY uk_user_client (user_no, client_code)',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema=DATABASE() AND table_name='t_sensitive_view_log' AND index_name='idx_user_date_client')=0,
  'ALTER TABLE t_sensitive_view_log ADD KEY idx_user_date_client (user_no, view_date, client_code)',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
