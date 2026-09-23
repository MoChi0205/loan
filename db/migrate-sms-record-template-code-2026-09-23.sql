-- 短信记录模板关联修复：统一使用 template_code 业务编码，不依赖模板自增主键。
-- 必须通过 scripts/apply-sql-nacos.py 从当前 Nacos 数据源执行。
SET @db := DATABASE();

SET @sql := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE t_sms_record ADD COLUMN template_code varchar(64) DEFAULT NULL COMMENT ''模板编码'' AFTER sms_type',
  'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = @db AND table_name = 't_sms_record' AND column_name = 'template_code');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 老表若仍有 template_id，则按模板表回填业务编码；没有该列时不执行引用旧列的 SQL。
SET @has_template_id := (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = @db AND table_name = 't_sms_record' AND column_name = 'template_id');
SET @sql := IF(@has_template_id > 0,
  'UPDATE t_sms_record r LEFT JOIN t_sms_template t ON t.id = r.template_id SET r.template_code = t.template_code WHERE r.template_code IS NULL',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE t_sms_record ADD KEY idx_template_code (template_code)',
  'SELECT 1')
  FROM information_schema.statistics
  WHERE table_schema = @db AND table_name = 't_sms_record' AND index_name = 'idx_template_code');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
