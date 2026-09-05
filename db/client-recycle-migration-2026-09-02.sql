-- =============================================================
-- 客户回收与预警：t_client_profile 加列 + 新建 t_client_recycle_config + 种子
-- 适用库：loan_db（远程 prd），通过 scripts 中的 db 执行通道一次性执行
-- 幂等：列新增用 information_schema 守卫，配置表用 CREATE TABLE IF NOT EXISTS + INSERT IGNORE
-- =============================================================

DELIMITER $$

DROP PROCEDURE IF EXISTS `add_client_recycle_columns`$$
CREATE PROCEDURE `add_client_recycle_columns`()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_client_profile' AND COLUMN_NAME = 'last_followed_at'
  ) THEN
    ALTER TABLE `t_client_profile` ADD COLUMN `last_followed_at` datetime DEFAULT NULL
      COMMENT '最后跟进时间(超期回收判定基准;归属/转移/跟进刷新)';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_client_profile' AND COLUMN_NAME = 'assign_blocked_until'
  ) THEN
    ALTER TABLE `t_client_profile` ADD COLUMN `assign_blocked_until` datetime DEFAULT NULL
      COMMENT '回收冷却到期时间(回收进公海后原归属人不可认领/不可被直接分配)';
  END IF;
END$$
DELIMITER ;

CALL `add_client_recycle_columns`();
DROP PROCEDURE IF EXISTS `add_client_recycle_columns`;

-- 新建客户回收配置表（与线索回收配置 t_lead_recycle_config 对齐）
CREATE TABLE IF NOT EXISTS `t_client_recycle_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `config_key` varchar(16) NOT NULL DEFAULT 'GLOBAL' COMMENT '配置键(当前仅 GLOBAL 单行)',
  `recycle_enabled` tinyint NOT NULL DEFAULT '1' COMMENT '回收开关(1开/0关)',
  `recycle_days` int NOT NULL DEFAULT '30' COMMENT '回收天数(超过该天数无跟进自动回收进公海)',
  `warn_days` int NOT NULL DEFAULT '3' COMMENT '预警天数(距回收剩余时站内预警归属人)',
  `cooldown_days` int NOT NULL DEFAULT '7' COMMENT '冷却天数(回收后原归属人不可认领)',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户回收规则参数(全参数化不写死;参考 t_lead_recycle_config 与 tse 资源池回收)';

-- 种子：默认全局配置（已存在则跳过）
INSERT IGNORE INTO `t_client_recycle_config` (`config_key`, `recycle_enabled`, `recycle_days`, `warn_days`, `cooldown_days`)
VALUES ('GLOBAL', 1, 30, 3, 7);
