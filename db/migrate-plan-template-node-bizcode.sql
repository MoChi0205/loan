-- 模块/步骤/规则模板字段业务编码迁移（幂等执行）
-- 说明：保留用户可读 module_code/field_code，同时补充全局唯一节点业务编码；step_code 直接作为步骤业务编码。
SET @db = DATABASE();

SET @sql = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE t_admission_plan_module ADD COLUMN module_biz_code VARCHAR(64) NULL COMMENT ''模块业务编码'' AFTER id',
  'SELECT 1') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='t_admission_plan_module' AND COLUMN_NAME='module_biz_code'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE t_strategy_template_module ADD COLUMN module_biz_code VARCHAR(64) NULL COMMENT ''模块业务编码'' AFTER id',
  'SELECT 1') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='t_strategy_template_module' AND COLUMN_NAME='module_biz_code'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE t_rule_template_field ADD COLUMN field_biz_code VARCHAR(64) NULL COMMENT ''字段业务编码'' AFTER id',
  'SELECT 1') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='t_rule_template_field' AND COLUMN_NAME='field_biz_code'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE t_admission_plan_step ADD COLUMN step_code VARCHAR(64) NULL COMMENT ''步骤业务编码'' AFTER id',
  'SELECT 1') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='t_admission_plan_step' AND COLUMN_NAME='step_code'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE t_strategy_template_step ADD COLUMN step_code VARCHAR(64) NULL COMMENT ''步骤业务编码'' AFTER id',
  'SELECT 1') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='t_strategy_template_step' AND COLUMN_NAME='step_code'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

UPDATE t_admission_plan_step SET step_code = CONCAT('step', REPLACE(UUID(), '-', '')) WHERE step_code IS NULL OR step_code = '';
UPDATE t_strategy_template_step SET step_code = CONCAT('step', REPLACE(UUID(), '-', '')) WHERE step_code IS NULL OR step_code = '';
UPDATE t_admission_plan_module SET module_biz_code = CONCAT('module', REPLACE(UUID(), '-', '')) WHERE module_biz_code IS NULL OR module_biz_code = '';
UPDATE t_strategy_template_module SET module_biz_code = CONCAT('module', REPLACE(UUID(), '-', '')) WHERE module_biz_code IS NULL OR module_biz_code = '';
UPDATE t_rule_template_field SET field_biz_code = CONCAT('field', REPLACE(UUID(), '-', '')) WHERE field_biz_code IS NULL OR field_biz_code = '';

ALTER TABLE t_admission_plan_step MODIFY COLUMN step_code VARCHAR(64) NOT NULL;
ALTER TABLE t_strategy_template_step MODIFY COLUMN step_code VARCHAR(64) NOT NULL;
ALTER TABLE t_admission_plan_module MODIFY COLUMN module_biz_code VARCHAR(64) NOT NULL;
ALTER TABLE t_strategy_template_module MODIFY COLUMN module_biz_code VARCHAR(64) NOT NULL;
ALTER TABLE t_rule_template_field MODIFY COLUMN field_biz_code VARCHAR(64) NOT NULL;

SET @sql = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE t_admission_plan_step ADD UNIQUE KEY uk_plan_step_code (module_id, step_code)',
  'SELECT 1') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='t_admission_plan_step' AND INDEX_NAME='uk_plan_step_code'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE t_strategy_template_step ADD UNIQUE KEY uk_template_step_code (template_module_id, step_code)',
  'SELECT 1') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='t_strategy_template_step' AND INDEX_NAME='uk_template_step_code'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE t_admission_plan_module ADD UNIQUE KEY uk_plan_module_biz_code (module_biz_code)',
  'SELECT 1') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='t_admission_plan_module' AND INDEX_NAME='uk_plan_module_biz_code'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE t_strategy_template_module ADD UNIQUE KEY uk_template_module_biz_code (module_biz_code)',
  'SELECT 1') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='t_strategy_template_module' AND INDEX_NAME='uk_template_module_biz_code'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE t_rule_template_field ADD UNIQUE KEY uk_field_biz_code (field_biz_code)',
  'SELECT 1') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='t_rule_template_field' AND INDEX_NAME='uk_field_biz_code'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 父级模块/字段编码已存在，补充范围唯一索引（重复数据需先人工清理）。
SET @sql = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE t_admission_plan_module ADD UNIQUE KEY uk_plan_module_code (plan_id, module_code)',
  'SELECT 1') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='t_admission_plan_module' AND INDEX_NAME='uk_plan_module_code'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE t_strategy_template_module ADD UNIQUE KEY uk_template_module_code (template_id, module_code)',
  'SELECT 1') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='t_strategy_template_module' AND INDEX_NAME='uk_template_module_code'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE t_rule_template_field ADD UNIQUE KEY uk_template_field_code (template_id, field_code)',
  'SELECT 1') FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='t_rule_template_field' AND INDEX_NAME='uk_template_field_code'); PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
