-- 资源池规则与离职数据交接（开发环境迁移）
CREATE TABLE IF NOT EXISTS t_resource_pool_config (
  id BIGINT NOT NULL AUTO_INCREMENT,
  pool_code VARCHAR(64) NOT NULL,
  pool_name VARCHAR(128) NOT NULL,
  scope_type VARCHAR(16) NOT NULL COMMENT 'COMPANY/TEAM/STAFF',
  scope_key VARCHAR(64) DEFAULT NULL COMMENT '团队编码或员工工号',
  enabled TINYINT NOT NULL DEFAULT 1,
  recycle_enabled TINYINT NOT NULL DEFAULT 1,
  recycle_days INT DEFAULT 30,
  warn_days INT DEFAULT 15,
  cooldown_days INT DEFAULT 7,
  status_days_json VARCHAR(2000) DEFAULT NULL,
  updated_by VARCHAR(64) DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id), UNIQUE KEY uk_resource_pool_scope (pool_code),
  KEY idx_resource_pool_scope (scope_type, scope_key, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资源池回收规则';

INSERT INTO t_resource_pool_config(pool_code,pool_name,scope_type,enabled,recycle_enabled,recycle_days,warn_days,cooldown_days,status_days_json)
SELECT 'COMPANY','公司公共资源池','COMPANY',1,1,30,15,7,
       '{"NEW":30,"NO_ANSWER":15,"POTENTIAL":30,"INTENTION":30,"VISITED":45}'
WHERE NOT EXISTS (SELECT 1 FROM t_resource_pool_config WHERE pool_code='COMPANY');

CREATE TABLE IF NOT EXISTS t_staff_data_transfer_record (
  id BIGINT NOT NULL AUTO_INCREMENT,
  transfer_no VARCHAR(64) NOT NULL,
  from_staff_code VARCHAR(64) NOT NULL,
  to_staff_code VARCHAR(64) DEFAULT NULL,
  business_type VARCHAR(32) NOT NULL,
  business_code VARCHAR(128) NOT NULL,
  reason VARCHAR(255) DEFAULT NULL,
  operator VARCHAR(64) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY(id), UNIQUE KEY uk_staff_transfer_no(transfer_no),
  KEY idx_staff_transfer_from(from_staff_code, created_at),
  KEY idx_staff_transfer_business(business_type, business_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='离职员工数据回收与继承审计';
