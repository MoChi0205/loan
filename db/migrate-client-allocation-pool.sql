-- 未分配客户池与统一归属审批迁移，可重复执行。
-- 分享引荐关系不写入 owner_staff_code；owner_staff_code IS NULL 即未分配客户。
-- 若历史数据中同一客户有多条 PENDING，本脚本明确中止，由业务人员确认保留项后再执行。

SET @loan_schema = DATABASE();

SET @ddl = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = @loan_schema AND table_name = 't_client_allocation_approval'
             AND column_name = 'from_owner_staff_code'),
    'SELECT 1',
    'ALTER TABLE t_client_allocation_approval ADD COLUMN from_owner_staff_code VARCHAR(32) NULL COMMENT ''申请时原归属顾问工号(转移审批并发校验)'' AFTER applicant_staff_code'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = @loan_schema AND table_name = 't_client_allocation_approval'
             AND column_name = 'pending_key'),
    'SELECT 1',
    'ALTER TABLE t_client_allocation_approval ADD COLUMN pending_key VARCHAR(64) NULL COMMENT ''待审唯一键(PENDING时=client_code,完成后NULL)'' AFTER applicant_staff_code'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = @loan_schema AND table_name = 't_client_allocation_approval'
             AND column_name = 'apply_source'),
    'SELECT 1',
    'ALTER TABLE t_client_allocation_approval ADD COLUMN apply_source VARCHAR(32) NOT NULL DEFAULT ''ADVISER_CLAIM'' COMMENT ''申请来源(ADVISER_CLAIM顾问认领/MANAGER_ASSIGN管理分配)'' AFTER pending_key'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = @loan_schema AND table_name = 't_client_allocation_approval'
             AND column_name = 'apply_operator_code'),
    'SELECT 1',
    'ALTER TABLE t_client_allocation_approval ADD COLUMN apply_operator_code VARCHAR(32) NULL COMMENT ''发起操作人员工工号'' AFTER apply_source'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @duplicate_pending = (
    SELECT COUNT(*) FROM (
        SELECT client_code
        FROM t_client_allocation_approval
        WHERE approve_status = 'PENDING'
        GROUP BY client_code
        HAVING COUNT(*) > 1
    ) duplicated
);
SET @ddl = IF(
    @duplicate_pending = 0,
    'SELECT 1',
    'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''同一客户存在多条待审分配单，请先确认并清理重复数据'''
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE t_client_allocation_approval
SET pending_key = CASE WHEN approve_status = 'PENDING' THEN client_code ELSE NULL END
WHERE (approve_status = 'PENDING' AND pending_key IS NULL)
   OR (approve_status <> 'PENDING' AND pending_key IS NOT NULL);

SET @ddl = IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
           WHERE table_schema = @loan_schema AND table_name = 't_client_allocation_approval'
             AND index_name = 'uk_pending_key'),
    'SELECT 1',
    'ALTER TABLE t_client_allocation_approval ADD UNIQUE KEY uk_pending_key (pending_key)'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
