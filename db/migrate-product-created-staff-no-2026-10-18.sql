-- 产品库：新增「创建人工号」字段，用于员工数据范围隔离（普通员工仅可见本人创建产品）
-- 迁移日期：2026-10-18
SET NAMES utf8mb4;

-- 幂等：仅当字段不存在时新增
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 't_bank_product' AND column_name = 'created_staff_no');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE t_bank_product ADD COLUMN created_staff_no VARCHAR(32) NULL COMMENT ''创建人工号(员工数据范围隔离)'' AFTER created_by',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
