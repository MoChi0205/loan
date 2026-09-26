-- 手机号安全存储与超限审批：phone 保持 AES 密文，phone_plain 仅供受控内部任务使用。
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='t_client_profile' AND column_name='phone_plain')=0,
  'ALTER TABLE t_client_profile ADD COLUMN phone_plain varchar(32) DEFAULT NULL COMMENT ''手机号内部原值，禁止对外返回'' AFTER phone_hash', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS t_sensitive_view_approval (
  id bigint NOT NULL AUTO_INCREMENT,
  approval_no varchar(64) NOT NULL COMMENT '敏感数据查看审批业务ID',
  client_code varchar(64) NOT NULL COMMENT '客户业务编码',
  applicant_staff_code varchar(64) NOT NULL COMMENT '申请员工工号',
  applicant_role_code varchar(32) NOT NULL,
  applicant_dept_code varchar(64) DEFAULT NULL,
  view_date date NOT NULL COMMENT '触发额度日期',
  approver_staff_code varchar(64) DEFAULT NULL,
  approval_stage varchar(20) NOT NULL COMMENT 'MANAGER_REVIEW/BOSS_REVIEW',
  approve_status varchar(20) NOT NULL DEFAULT 'PENDING',
  approve_opinion varchar(500) DEFAULT NULL,
  approved_at datetime DEFAULT NULL,
  created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  pending_unique_key varchar(255) GENERATED ALWAYS AS (
    CASE WHEN approve_status = 'PENDING'
      THEN CONCAT(client_code, '#', applicant_staff_code, '#', view_date)
      ELSE NULL END
  ) STORED,
  PRIMARY KEY (id), UNIQUE KEY uk_sensitive_view_approval_no (approval_no),
  UNIQUE KEY uk_sensitive_view_pending_only (pending_unique_key),
  KEY idx_sensitive_view_stage_status (approval_stage, approve_status),
  KEY idx_sensitive_view_applicant (applicant_staff_code, view_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='超出手机号查看额度审批单';

-- 修复旧版唯一键：旧键包含 approve_status，第二次申请被驳回时会与历史 REJECTED 冲突。
SET @sql := IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE()
  AND table_name='t_sensitive_view_approval' AND index_name='uk_sensitive_view_pending')>0,
  'ALTER TABLE t_sensitive_view_approval DROP INDEX uk_sensitive_view_pending', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='t_sensitive_view_approval' AND column_name='pending_unique_key')=0,
  'ALTER TABLE t_sensitive_view_approval ADD COLUMN pending_unique_key varchar(255) GENERATED ALWAYS AS (CASE WHEN approve_status = ''PENDING'' THEN CONCAT(client_code, ''#'', applicant_staff_code, ''#'', view_date) ELSE NULL END) STORED',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE()
  AND table_name='t_sensitive_view_approval' AND index_name='uk_sensitive_view_pending_only')=0,
  'ALTER TABLE t_sensitive_view_approval ADD UNIQUE KEY uk_sensitive_view_pending_only (pending_unique_key)',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
