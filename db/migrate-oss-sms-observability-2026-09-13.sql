-- OSS/短信预实现补充索引（可重复执行）
SET @db := DATABASE();
SET @sql := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE t_service_attachment ADD UNIQUE KEY uk_service_attachment_file_key (file_key), ADD KEY idx_service_attachment_report_no (report_no), ADD KEY idx_service_attachment_client_code (client_profile_code)',
  'SELECT 1') FROM information_schema.statistics WHERE table_schema=@db AND index_name='uk_service_attachment_file_key');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE t_sms_template ADD UNIQUE KEY uk_sms_template_code (template_code)',
  'SELECT 1') FROM information_schema.statistics WHERE table_schema=@db AND index_name='uk_sms_template_code');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
-- 统一记录接口耗时的字段无需数据库迁移，AccessLogFilter 写入响应头和日志。
