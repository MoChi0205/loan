-- 运营短信 / 报告模板统一审批记录（幂等）
CREATE TABLE IF NOT EXISTS t_content_approval (
  id BIGINT NOT NULL AUTO_INCREMENT,
  approval_no VARCHAR(64) NOT NULL,
  approval_type VARCHAR(32) NOT NULL COMMENT 'SMS_TEMPLATE/REPORT_TEMPLATE',
  target_code VARCHAR(64) NOT NULL COMMENT '模板业务编码',
  target_version INT NULL,
  applicant_staff_code VARCHAR(64) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  opinion VARCHAR(500) NULL,
  approver_staff_code VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  approved_at DATETIME NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_content_approval_no (approval_no),
  KEY idx_content_approval_pending (approval_type, status, created_at),
  KEY idx_content_approval_target (target_code, target_version, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运营内容审批记录';
