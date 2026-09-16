-- 客户跨团队转移两级审批：申请人团队负责人初审，老板/超级管理员终审。可重复执行。
SET @loan_schema = DATABASE();
SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@loan_schema AND table_name='t_client_allocation_approval' AND column_name='approval_stage'),
  'SELECT 1', 'ALTER TABLE t_client_allocation_approval ADD COLUMN approval_stage VARCHAR(20) NOT NULL DEFAULT ''TEAM_REVIEW'' COMMENT ''TEAM_REVIEW/BOSS_REVIEW'' AFTER approve_status');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@loan_schema AND table_name='t_client_allocation_approval' AND column_name='team_approver_staff_code'),
  'SELECT 1', 'ALTER TABLE t_client_allocation_approval ADD COLUMN team_approver_staff_code VARCHAR(32) NULL AFTER approval_stage');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@loan_schema AND table_name='t_client_allocation_approval' AND column_name='team_approved_at'),
  'SELECT 1', 'ALTER TABLE t_client_allocation_approval ADD COLUMN team_approved_at DATETIME NULL AFTER team_approver_staff_code');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
