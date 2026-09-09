-- 客户公司公海/团队公海初始化迁移（开发阶段，无历史兼容负担）
ALTER TABLE `t_client_profile`
  ADD COLUMN IF NOT EXISTS `sea_level` varchar(16) DEFAULT NULL COMMENT '公海层级: ENTERPRISE/TEAM; 已分配为空' AFTER `assign_blocked_until`,
  ADD COLUMN IF NOT EXISTS `sea_dept_code` varchar(16) DEFAULT NULL COMMENT '团队公海所属部门编码' AFTER `sea_level`;

UPDATE `t_client_profile`
SET `sea_level` = 'ENTERPRISE', `sea_dept_code` = NULL
WHERE `owner_staff_code` IS NULL AND (`sea_level` IS NULL OR `sea_level` = '');

UPDATE `t_client_profile`
SET `sea_level` = NULL, `sea_dept_code` = NULL
WHERE `owner_staff_code` IS NOT NULL;

CREATE INDEX `idx_client_sea_scope`
  ON `t_client_profile` (`sea_level`, `sea_dept_code`, `owner_staff_code`, `status`);
