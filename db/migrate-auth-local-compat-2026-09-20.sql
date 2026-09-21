-- 本地开发旧库兼容迁移：供全角色密码登录与本地测试账号重复执行。
-- 仅修改缺失列/索引，可重复执行；正式环境仍须走数据库变更审批。
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS add_column_if_missing;
DELIMITER $$
CREATE PROCEDURE add_column_if_missing(
  IN table_name_arg varchar(64),
  IN column_name_arg varchar(64),
  IN column_definition_arg varchar(512)
)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = table_name_arg
      AND column_name = column_name_arg
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE `', table_name_arg, '` ADD COLUMN `',
                      column_name_arg, '` ', column_definition_arg);
    PREPARE statement_to_run FROM @ddl;
    EXECUTE statement_to_run;
    DEALLOCATE PREPARE statement_to_run;
  END IF;
END$$
DELIMITER ;

CALL add_column_if_missing('t_staff', 'dept_code',
  'varchar(32) DEFAULT NULL COMMENT ''所属部门编码(业务编码,与t_department.dept_code关联)'' AFTER `staff_name`');
CALL add_column_if_missing('t_staff', 'password',
  'varchar(128) DEFAULT NULL COMMENT ''密码(BCrypt，验证码重置后设置)'' AFTER `phone_hash`');
CALL add_column_if_missing('t_client_profile', 'password',
  'varchar(128) DEFAULT NULL COMMENT ''密码(BCrypt，验证码重置后设置)'' AFTER `phone_hash`');
CALL add_column_if_missing('t_client_profile', 'owner_staff_code',
  'varchar(64) DEFAULT NULL COMMENT ''归属顾问工号'' AFTER `credit_code_hash`');
CALL add_column_if_missing('t_client_profile', 'last_followed_at',
  'datetime DEFAULT NULL COMMENT ''最后跟进时间'' AFTER `owner_staff_code`');
CALL add_column_if_missing('t_client_profile', 'assign_blocked_until',
  'datetime DEFAULT NULL COMMENT ''回收冷却到期时间'' AFTER `last_followed_at`');
CALL add_column_if_missing('t_client_profile', 'sea_level',
  'varchar(16) DEFAULT NULL COMMENT ''公海层级'' AFTER `assign_blocked_until`');
CALL add_column_if_missing('t_client_profile', 'sea_dept_code',
  'varchar(16) DEFAULT NULL COMMENT ''团队公海部门编码'' AFTER `sea_level`');
CALL add_column_if_missing('t_client_profile', 'wx_openid',
  'varchar(128) DEFAULT NULL COMMENT ''微信小程序openid'' AFTER `credit_code_hash`');
CALL add_column_if_missing('t_client_profile', 'wx_openid_hash',
  'varchar(64) DEFAULT NULL COMMENT ''openid SHA-256哈希'' AFTER `wx_openid`');
CALL add_column_if_missing('t_client_profile', 'lead_no',
  'varchar(64) DEFAULT NULL COMMENT ''来源线索编号'' AFTER `source`');

DROP PROCEDURE add_column_if_missing;

SET @dept_index_exists = (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 't_staff'
    AND index_name = 'idx_dept_code'
);
SET @dept_index_ddl = IF(
  @dept_index_exists = 0,
  'ALTER TABLE `t_staff` ADD KEY `idx_dept_code` (`dept_code`)',
  'SELECT 1'
);
PREPARE statement_to_run FROM @dept_index_ddl;
EXECUTE statement_to_run;
DEALLOCATE PREPARE statement_to_run;

-- 旧本地镜像缺少后续加入真值 schema 的接口权限表；后端启动同步依赖它们。
CREATE TABLE IF NOT EXISTS `t_api_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `api_key` varchar(128) NOT NULL COMMENT '接口权限键',
  `http_method` varchar(16) NOT NULL DEFAULT 'ALL' COMMENT 'HTTP方法',
  `path_pattern` varchar(255) NOT NULL COMMENT '路径模式',
  `module_group` varchar(32) DEFAULT NULL COMMENT '模块分组',
  `client_types` varchar(64) NOT NULL DEFAULT 'WEB,MINI_APP' COMMENT '可用端',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_api_key` (`api_key`),
  KEY `idx_path` (`path_pattern`),
  KEY `idx_group` (`module_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `t_role_api` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `role_code` varchar(32) NOT NULL COMMENT '角色编码',
  `api_key` varchar(128) NOT NULL COMMENT '接口权限键',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_api` (`role_code`,`api_key`),
  KEY `idx_role` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `t_client_lifecycle_event` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `client_code` varchar(64) NOT NULL COMMENT '客户业务编码',
  `event_type` varchar(32) NOT NULL COMMENT '生命周期事件类型',
  `staff_code` varchar(32) DEFAULT NULL COMMENT '归属或跟进员工工号',
  `sea_level` varchar(16) DEFAULT NULL COMMENT 'ENTERPRISE/TEAM',
  `sea_dept_code` varchar(32) DEFAULT NULL,
  `episode_no` int NOT NULL DEFAULT 1 COMMENT '生命周期周期号',
  `event_at` datetime NOT NULL COMMENT '事件发生时间',
  `operator_staff_code` varchar(32) DEFAULT NULL,
  `reason_code` varchar(64) DEFAULT NULL COMMENT '原因',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_client_event_episode` (`client_code`,`event_type`,`episode_no`),
  KEY `idx_client_event_time` (`client_code`,`event_type`,`event_at`),
  KEY `idx_event_time` (`event_type`,`event_at`),
  KEY `idx_staff_event_time` (`staff_code`,`event_type`,`event_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
