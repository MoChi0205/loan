-- ============================================================
-- 小程序微信登录（P0-1）：t_client_profile 增加 wx_openid / wx_openid_hash
-- 依据：design-three-terminal.md §3.1.6 / Q3 方案 A
--       微信小程序 openid 为方案 A 客户账号主键，落明文 + UNIQUE；
--       wx_openid_hash 为 SHA-256 等值查询列（防 openid 直存检索，取数查 hash）。
-- 执行方式：mysql -uroot -p loan_db --default-character-set=utf8mb4 < db/migrate-mini-wechat.sql
-- 幂等说明：MySQL 8 的 ADD COLUMN / ADD KEY 不支持 IF NOT EXISTS，
--           重复执行会报 Duplicate column/key name；可先执行下方检查语句确认后再执行。
--   SELECT COLUMN_NAME FROM information_schema.COLUMNS
--    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='t_client_profile'
--      AND COLUMN_NAME IN ('wx_openid','wx_openid_hash');
-- ============================================================
SET NAMES utf8mb4;

ALTER TABLE `t_client_profile`
  ADD COLUMN `wx_openid` varchar(64) DEFAULT NULL COMMENT '微信小程序openid' AFTER `phone_hash`,
  ADD COLUMN `wx_openid_hash` varchar(64) DEFAULT NULL COMMENT 'openid SHA-256哈希' AFTER `wx_openid`;

ALTER TABLE `t_client_profile`
  ADD UNIQUE KEY `uk_wx_openid` (`wx_openid`),
  ADD KEY `idx_wx_openid_hash` (`wx_openid_hash`);

-- 迁移后校验：
--   SELECT COUNT(*) FROM information_schema.COLUMNS
--    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='t_client_profile'
--      AND COLUMN_NAME IN ('wx_openid','wx_openid_hash');  -- 应为 2
--   SELECT COUNT(*) FROM information_schema.STATISTICS
--    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='t_client_profile'
--      AND INDEX_NAME IN ('uk_wx_openid','idx_wx_openid_hash');  -- 应为 2
