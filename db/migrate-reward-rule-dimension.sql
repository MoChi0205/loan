-- ============================================================
-- 奖励规则分层配置迁移（增量，不破坏现有数据）
-- 对应需求：按 产品×客群 分层配置直推/间推比例，无全局默认须显式配置
-- 适用范围：t_reward_rule 已存在（旧为全局单条规则），仅补充维度列与唯一约束
-- ============================================================

-- 0) 规则业务版本由 BizIdGenerator 生成（前缀 + 32 位随机），统一扩容为 varchar(64)。
SET @sql = IF(
  (SELECT character_maximum_length FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 't_reward_rule' AND column_name = 'rule_version') >= 64,
  'SELECT "SKIP rule_version: 列宽已满足"',
  'ALTER TABLE `t_reward_rule` MODIFY COLUMN `rule_version` varchar(64) NOT NULL COMMENT ''规则版本(业务唯一编码)'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 1) 补充维度列（通过 information_schema 判断，可重复执行）
--    旧全局规则的新列保持 NULL，不自动猜测产品/客群，避免错误结算。
SET @sql = IF(
  EXISTS(SELECT 1 FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 't_reward_rule' AND column_name = 'product_code'),
  'SELECT "SKIP product_code: 字段已存在"',
  'ALTER TABLE `t_reward_rule` ADD COLUMN `product_code` varchar(64) DEFAULT NULL COMMENT ''适用产品编码(精确匹配;NULL=历史未分配)'' AFTER `base_caliber`'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
  EXISTS(SELECT 1 FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 't_reward_rule' AND column_name = 'customer_group'),
  'SELECT "SKIP customer_group: 字段已存在"',
  'ALTER TABLE `t_reward_rule` ADD COLUMN `customer_group` varchar(16) DEFAULT NULL COMMENT ''适用客群(ENTERPRISE企业/PERSONAL个人;与product_code组合精确匹配)'' AFTER `product_code`'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) 唯一约束：同一 产品×客群 仅一条生效规则（NULL 在 MySQL 唯一键中视为不同值，允许旧全局行并存，不会冲突）
--    注意：若历史数据已存在 (product_code, customer_group) 同时为 NULL 的多行，先去重再执行本约束。
SET @cnt = (SELECT COUNT(*) FROM (SELECT product_code, customer_group FROM t_reward_rule GROUP BY product_code, customer_group HAVING COUNT(*) > 1) t);
SET @index_exists = (SELECT COUNT(*) FROM information_schema.statistics
                     WHERE table_schema = DATABASE() AND table_name = 't_reward_rule'
                       AND index_name = 'uk_product_group');
-- 索引已存在则跳过；仅当无重复时才加约束，避免 ALTER 失败。
SET @sql = IF(
  @index_exists > 0,
  'SELECT "SKIP uk_product_group: 索引已存在"',
  IF(@cnt = 0,
     'ALTER TABLE `t_reward_rule` ADD UNIQUE KEY `uk_product_group` (`product_code`, `customer_group`)',
     'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''t_reward_rule 存在重复 product_code/customer_group，拒绝创建唯一索引''')
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3)（可选）清理历史全局默认规则：若此前靠全局规则结算，需改为显式按产品×客群配置。
--    下列语句仅查询，不执行删除，供运维确认：
--    SELECT id, rule_version, product_code, customer_group, direct_rate, status FROM t_reward_rule WHERE product_code IS NULL OR customer_group IS NULL;
