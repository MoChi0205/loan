-- 产品审批：新增「申请理由」字段（下线删除审批必填）
-- 迁移日期：2026-10-18
SET NAMES utf8mb4;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 't_product_approval' AND column_name = 'apply_reason');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE t_product_approval ADD COLUMN apply_reason VARCHAR(255) NULL COMMENT ''申请理由(下线删除必填;变更填变更说明)'' AFTER apply_type',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
