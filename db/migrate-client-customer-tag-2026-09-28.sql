-- 客户经营标签：用于客户列表展示、筛选和服务跟进。
SET @loan_schema = DATABASE();
SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@loan_schema AND table_name='t_client_profile' AND column_name='customer_tag'),
  'SELECT 1',
  'ALTER TABLE t_client_profile ADD COLUMN customer_tag VARCHAR(32) NULL COMMENT ''客户经营标签(NO_NEED无需求/INTENTION意向/POTENTIAL潜在/DEAL成交/VISITED已来访/NO_ANSWER无人接听)'' AFTER source');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @idx = IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=@loan_schema AND table_name='t_client_profile' AND index_name='idx_customer_tag'), 'SELECT 1', 'ALTER TABLE t_client_profile ADD KEY idx_customer_tag (customer_tag)');
PREPARE stmt2 FROM @idx; EXECUTE stmt2; DEALLOCATE PREPARE stmt2;
