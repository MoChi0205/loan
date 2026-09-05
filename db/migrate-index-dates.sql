-- ============================================================
-- 日期字段索引（报表统计 / 列表查询避免全表扫描）
-- 执行方式：mysql -uroot -p loan_db --default-character-set=utf8mb4 < db/migrate-index-dates.sql
-- 幂等说明：MySQL 8 不支持 ADD INDEX IF NOT EXISTS，重复执行会报错；执行前用 information_schema 检查
-- ============================================================
SET NAMES utf8mb4;

-- 服务工单：按创建/更新时间排序
ALTER TABLE `t_service_order` ADD KEY `idx_created_at` (`created_at`);
ALTER TABLE `t_service_order` ADD KEY `idx_updated_at` (`updated_at`);

-- 奖励流水：报表按月统计 / 结算时间查询
ALTER TABLE `t_reward_record` ADD KEY `idx_created_at` (`created_at`);
ALTER TABLE `t_reward_record` ADD KEY `idx_settle_time` (`settle_time`);

-- 客户档案
ALTER TABLE `t_client_profile` ADD KEY `idx_created_at` (`created_at`);
ALTER TABLE `t_client_profile` ADD KEY `idx_updated_at` (`updated_at`);

-- 初筛报告
ALTER TABLE `t_client_screening` ADD KEY `idx_created_at` (`created_at`);

-- 审批单（产品审核 / 附件下载审批）
ALTER TABLE `t_attachment_download_approval` ADD KEY `idx_created_at` (`created_at`);
ALTER TABLE `t_product_approval` ADD KEY `idx_created_at` (`created_at`);

-- 黑名单
ALTER TABLE `t_blacklist` ADD KEY `idx_created_at` (`created_at`);

-- 线索主表
ALTER TABLE `t_lead` ADD KEY `idx_created_at` (`created_at`);
ALTER TABLE `t_lead` ADD KEY `idx_updated_at` (`updated_at`);
ALTER TABLE `t_lead_archive` ADD KEY `idx_created_at` (`created_at`);

-- 匹配审计
ALTER TABLE `t_match_trace` ADD KEY `idx_created_at` (`created_at`);
