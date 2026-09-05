-- 分享引荐业务编码补齐。通过 information_schema 判断，可重复执行。
-- 分享关系只用于引荐归因，不回写客户 owner_staff_code。

SET @loan_schema = DATABASE();

SET @ddl = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = @loan_schema AND table_name = 't_invitation'
             AND column_name = 'referrer_client_code'),
    'SELECT 1',
    'ALTER TABLE t_invitation ADD COLUMN referrer_client_code VARCHAR(64) NULL COMMENT ''引荐人客户编码(仅CUSTOMER类型,业务ID)'' AFTER referrer_id'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = @loan_schema AND table_name = 't_invitation'
             AND column_name = 'used_by_client_code'),
    'SELECT 1',
    'ALTER TABLE t_invitation ADD COLUMN used_by_client_code VARCHAR(64) NULL COMMENT ''使用者客户编码(业务ID)'' AFTER used_by_client_id'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
           WHERE table_schema = @loan_schema AND table_name = 't_invitation'
             AND index_name = 'idx_referrer_client'),
    'SELECT 1',
    'ALTER TABLE t_invitation ADD INDEX idx_referrer_client (referrer_client_code)'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
           WHERE table_schema = @loan_schema AND table_name = 't_invitation'
             AND index_name = 'idx_used_client'),
    'SELECT 1',
    'ALTER TABLE t_invitation ADD INDEX idx_used_client (used_by_client_code, used_at)'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
