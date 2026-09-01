-- ============================================================
-- 奖励规则分层配置迁移（增量，不破坏现有数据）
-- 对应需求：按 产品×客群 分层配置直推/间推比例，无全局默认须显式配置
-- 适用范围：t_reward_rule 已存在（旧为全局单条规则），仅补充维度列与唯一约束
-- ============================================================

-- 1) 补充维度列（旧全局规则 product_code/customer_group 为 NULL，匹配时精确匹配不到 → 不结算，符合"无默认须全配"）
ALTER TABLE `t_reward_rule`
  ADD COLUMN `product_code`   varchar(64) DEFAULT NULL COMMENT '适用产品编码(精确匹配;NULL=全局默认)' AFTER `base_caliber`,
  ADD COLUMN `customer_group` varchar(16) DEFAULT NULL COMMENT '适用客群(ENTERPRISE企业/PERSONAL个人;与product_code组合精确匹配)' AFTER `product_code`;

-- 2) 唯一约束：同一 产品×客群 仅一条生效规则（NULL 在 MySQL 唯一键中视为不同值，允许旧全局行并存，不会冲突）
--    注意：若历史数据已存在 (product_code, customer_group) 同时为 NULL 的多行，先去重再执行本约束。
SET @cnt = (SELECT COUNT(*) FROM (SELECT product_code, customer_group FROM t_reward_rule GROUP BY product_code, customer_group HAVING COUNT(*) > 1) t);
-- 仅当无重复时才加约束，避免 ALTER 失败
SET @sql = IF(@cnt = 0, 'ALTER TABLE `t_reward_rule` ADD UNIQUE KEY `uk_product_group` (`product_code`, `customer_group`);', 'SELECT "SKIP uk_product_group: 存在重复 (product_code,customer_group) 组合，请先去重";');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3)（可选）清理历史全局默认规则：若此前靠全局规则结算，需改为显式按产品×客群配置。
--    下列语句仅查询，不执行删除，供运维确认：
--    SELECT id, rule_version, product_code, customer_group, direct_rate, status FROM t_reward_rule WHERE product_code IS NULL OR customer_group IS NULL;
