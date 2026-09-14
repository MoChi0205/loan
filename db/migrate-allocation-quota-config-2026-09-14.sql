-- ============================================================
-- 认领配额参数（每日认领上限 + 持有上限）+「认领设置」菜单
-- 参考 tse：t_enterprise_allocation_config
--   · daily_assign_limit_per_user 每坐席每日公海认领上限
--   · max_new_customers_per_user   坐席持有上限
--   两者同表、均可后台配置，不写死在代码里。
-- 本项目为单租户，故用 scope 分行承载「线索 / 客户」两套资源池。
-- 幂等：可重复执行。
-- ============================================================
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `t_allocation_quota_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `scope` varchar(16) NOT NULL COMMENT '适用范围(LEAD线索认领/CLIENT客户认领)',
  `daily_claim_limit` int NOT NULL DEFAULT '30' COMMENT '每员工每日公海认领上限(0=不限);参照tse daily_assign_limit_per_user',
  `max_holding` int NOT NULL DEFAULT '0' COMMENT '每员工持有上限(0=不限);参照tse max_new_customers_per_user',
  `remark` varchar(255) DEFAULT NULL COMMENT '说明',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scope` (`scope`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='认领配额参数(全参数化不写死;参考tse daily_assign_limit_per_user/max_new_customers_per_user)';

-- 幂等补列：MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，用 information_schema 守卫
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_allocation_quota_config' AND COLUMN_NAME = 'max_holding');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `t_allocation_quota_config` ADD COLUMN `max_holding` int NOT NULL DEFAULT 0 COMMENT ''每员工持有上限(0=不限);参照tse max_new_customers_per_user'' AFTER `daily_claim_limit`',
  'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 种子（不覆盖已改过的值）
INSERT INTO `t_allocation_quota_config` (`scope`, `daily_claim_limit`, `max_holding`, `remark`)
VALUES
  ('LEAD',   30, 0,   '线索：每日认领上限 30；持有上限不限'),
  ('CLIENT', 30, 100, '客户：每日认领上限 30；持有上限 100（原 loan.client.claim.max-holding 默认值）')
ON DUPLICATE KEY UPDATE `scope` = `scope`;

-- 「认领设置」菜单（按 uk_path 幂等）
INSERT INTO `t_menu` (`menu_name`, `path`, `component`, `menu_type`, `sort`, `status`, `created_by`) VALUES
('认领设置', '/allocation-quota', 'views/system/AllocationQuotaConfig', 'MENU', 30, 'ACTIVE', 'system')
ON DUPLICATE KEY UPDATE `menu_name` = VALUES(`menu_name`),
  `component` = VALUES(`component`), `status` = 'ACTIVE';

-- 菜单授权：老板 / 运营 / 超管 / 超级管理员（与网关 FULL_ACCESS_ROLES 一致）
INSERT INTO `t_role_permission` (`role_code`, `menu_id`, `created_by`)
SELECT r.`role_code`, m.`id`, 'system'
FROM (SELECT 'BOSS' AS `role_code`
      UNION ALL SELECT 'OPERATOR'
      UNION ALL SELECT 'SUPER_ADMIN'
      UNION ALL SELECT 'SUPER') r
JOIN `t_menu` m ON m.`path` = '/allocation-quota'
WHERE NOT EXISTS (SELECT 1 FROM `t_role_permission` p
                  WHERE p.`role_code` = r.`role_code` AND p.`menu_id` = m.`id`);
