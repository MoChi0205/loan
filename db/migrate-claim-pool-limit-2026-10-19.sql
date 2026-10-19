-- 认领设置新增公海限制三维度（2026-10-19）
-- 背景：认领设置此前只有「每日认领上限 + 持有上限」，公海保护期 / 冷却收紧 / 回纳目标无处配置。
-- 语义：
--   protect_days          数据保护期天数：客户/线索进入公海后 N 天内所有人不可认领（0/NULL = 不启用）
--   daily_cooldown_limit  每日认领冷却条数：与「每日认领上限」取较小值生效（0/NULL = 不启用，仅按每日上限）
--   return_target         退回/回收回纳目标：PUBLIC=公司公海（默认，现状） / ORIGINAL=归属人原部门团队池（仅自动回收生效）
ALTER TABLE `t_allocation_quota_config`
  ADD COLUMN `protect_days` INT NULL COMMENT '数据保护期天数：进入公海后N天内不可被认领（0/NULL=不启用）' AFTER `max_holding`,
  ADD COLUMN `daily_cooldown_limit` INT NULL COMMENT '每日认领冷却条数：与每日认领上限取小生效（0/NULL=不启用）' AFTER `protect_days`,
  ADD COLUMN `return_target` VARCHAR(20) NULL COMMENT '退回/回收回纳目标：PUBLIC=公司公海(默认)/ORIGINAL=原部门团队池' AFTER `daily_cooldown_limit`;

-- 幂等校验辅助（重复执行前请先检查列是否存在）
-- SELECT COUNT(*) FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_allocation_quota_config' AND COLUMN_NAME = 'protect_days';
