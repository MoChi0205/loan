-- ============================================================
-- 渠道准入 V2：策略细粒度改造迁移脚本
-- 目标：渠道 × 产品 × 客群 → 策略 → 计划(1:1)
-- 参考：docs/channel-admission-v2-design.md（对齐 mds v2）
-- 注意：本脚本为结构变更 + 数据迁移，执行前请备份。
-- ============================================================

-- ------------------------------------------------------------
-- 1. t_product_strategy 改造为细粒度策略（渠道×产品×客群 → 计划 1:1）
-- ------------------------------------------------------------
ALTER TABLE `t_product_strategy`
  ADD COLUMN `bank_channel_id` bigint DEFAULT NULL COMMENT '渠道ID(t_bank_channel.id)' AFTER `id`,
  ADD COLUMN `channel_code` varchar(32) DEFAULT NULL COMMENT '渠道编码(冗余)' AFTER `bank_channel_id`,
  ADD COLUMN `bank_product_id` bigint DEFAULT NULL COMMENT '银行产品ID(产品挂策略)' AFTER `channel_code`,
  ADD COLUMN `execution_plan_id` bigint DEFAULT NULL COMMENT '执行计划ID(1:1)' AFTER `description`;

-- 唯一键：原 uk_strategy_code(全局唯一) 改为 渠道内唯一
ALTER TABLE `t_product_strategy` DROP KEY `uk_strategy_code`;
ALTER TABLE `t_product_strategy` ADD UNIQUE KEY `uk_channel_strategy` (`channel_code`, `strategy_code`);
ALTER TABLE `t_product_strategy` ADD UNIQUE KEY `uk_plan` (`execution_plan_id`);
ALTER TABLE `t_product_strategy` ADD KEY `idx_channel_product_group` (`bank_channel_id`, `bank_product_id`, `customer_group`);

-- ------------------------------------------------------------
-- 2. t_admission_execution_plan：加 strategy_id 反向 1:1；customer_group 上移策略层
-- ------------------------------------------------------------
ALTER TABLE `t_admission_execution_plan`
  ADD COLUMN `strategy_id` bigint DEFAULT NULL COMMENT '策略ID(1:1)' AFTER `id`,
  ADD UNIQUE KEY `uk_strategy` (`strategy_id`);
-- customer_group 保留为兼容冗余（迁移完成后可 DROP），运行以策略层为准
-- ALTER TABLE `t_admission_execution_plan` DROP COLUMN `customer_group`;

-- ------------------------------------------------------------
-- 3. 模块/步骤补 OR 组短路 + 步骤空跑字段
-- ------------------------------------------------------------
ALTER TABLE `t_admission_plan_module`
  ADD COLUMN `join_with_next_module` varchar(8) NOT NULL DEFAULT 'AND'
    COMMENT '与下一模块连接(AND/OR)' AFTER `sort`;

ALTER TABLE `t_admission_plan_step`
  ADD COLUMN `join_with_next` varchar(8) NOT NULL DEFAULT 'AND'
    COMMENT '与下一步骤连接(AND/OR)' AFTER `step_sort`,
  ADD COLUMN `is_dry_run` tinyint NOT NULL DEFAULT '0'
    COMMENT '步骤级空跑(命中REJECT时链上记PASS)' AFTER `join_with_next`;

-- ------------------------------------------------------------
-- 4. 渠道本地白/黑名单
-- 名单键：个人贷(PERSONAL)=手机号MD5(32hex)；企业贷(ENTERPRISE)=统一社会信用代码(18位)
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `t_channel_user_list`;
CREATE TABLE `t_channel_user_list` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `channel_code` varchar(32) NOT NULL COMMENT '渠道编码',
  `customer_group` varchar(16) NOT NULL COMMENT '客群(决定 list_key 语义)',
  `list_type` varchar(16) NOT NULL COMMENT 'LOCAL_WHITE/LOCAL_BLACK',
  `list_key` varchar(64) NOT NULL COMMENT '名单键：PERSONAL=手机号MD5；ENTERPRISE=统一社会信用代码',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_channel_group_type` (`channel_code`, `customer_group`, `list_type`),
  KEY `idx_key` (`list_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='渠道本地白/黑名单';

-- ------------------------------------------------------------
-- 5. 数据迁移：t_product_admission_config → t_product_strategy
-- 生产库 t_bank_product 用 bank_channel_code（字符串）指向渠道，据此自动迁移。
-- ------------------------------------------------------------
INSERT INTO `t_product_strategy`
  (`bank_channel_id`, `channel_code`, `bank_product_id`, `customer_group`,
   `strategy_code`, `strategy_name`, `execution_plan_id`, `status`, `created_by`)
SELECT
  c.id, c.channel_code, p.id, p.customer_group,
  CONCAT(c.channel_code, '_', p.product_code), CONCAT(c.bank_name, '-', p.product_name, '准入策略'),
  cfg.execution_plan_id, 'ACTIVE', 'migration'
FROM `t_product_admission_config` cfg
JOIN `t_bank_product` p ON p.id = cfg.bank_product_id
JOIN `t_bank_channel` c ON c.channel_code = p.bank_channel_code;

-- 回填计划的 strategy_id（1:1 反向关联，需先执行第 2 步 ALTER）
-- UPDATE `t_admission_execution_plan` e
-- JOIN `t_product_strategy` s ON s.execution_plan_id = e.id
-- SET e.strategy_id = s.id;

-- 迁移完成后归档旧表（可选，验证无误后再执行）：
-- RENAME TABLE `t_product_admission_config` TO `t_product_admission_config_archive`;
