-- ============================================================
-- 员工外勤（t_staff_outing）：新增「审核状态机 + 审核人字段」与「打卡照片」
-- 日期：2026-09-21 ｜ 幂等：可重复执行
--
-- 背景（用户 2026-09-21 确认的四条口径）：
--   ① t_staff_outing 加审核状态机 + 审核人字段与端点
--   ② 关联预约时不允许别人代录 —— 只能「预约的主服务顾问本人」提交；普通外出由本人提交
--   ③ 打卡必须上传「图片 + 定位数据」（定位原有，本次新增照片）
--   ④ 跟进记录统一用 t_client_follow_record（t_service_follow 保持废弃）
--
-- 状态机变更（新增两态，原五态保留）：
--   PENDING_REVIEW（本人提交申请，待审核，新建默认态）
--     ├─ 审核通过 → READY（待出发/待打卡）
--     └─ 审核驳回 → REJECTED（可修改后重新提交 → PENDING_REVIEW）
--   READY → IN_PROGRESS（出发打卡）→ COMPLETED（返回打卡）
--   任意非终态 → CANCELLED
--
-- 存量数据说明：本次不写任何伪造的审核记录。历史 status='READY' 的行
--   在迁移后语义变为「审核通过·待出发」，但其 reviewer_* 为空 —— 属历史一次性
--   放行，不补审核人（保留真实留痕，宁可空也不造假）。
--
-- 全量 DDL 同步：db/loan-db-schema.sql 的 t_staff_outing 已含本次全部字段。
-- ============================================================
SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. 幂等补列（MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，用 information_schema 守卫）
-- ------------------------------------------------------------

-- 1.1 提交审核时间
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_staff_outing' AND COLUMN_NAME = 'submitted_at');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `t_staff_outing` ADD COLUMN `submitted_at` datetime DEFAULT NULL COMMENT ''提交审核时间(本人提交申请，不接受他人代录)'' AFTER `planned_end`',
  'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1.2 审核人工号
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_staff_outing' AND COLUMN_NAME = 'reviewer_staff_code');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `t_staff_outing` ADD COLUMN `reviewer_staff_code` varchar(32) DEFAULT NULL COMMENT ''审核人工号(禁止自审)'' AFTER `status`',
  'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1.3 审核人姓名（到人留痕，与项目其它表 created_by/updated_by 存姓名一致）
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_staff_outing' AND COLUMN_NAME = 'reviewer_name');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `t_staff_outing` ADD COLUMN `reviewer_name` varchar(64) DEFAULT NULL COMMENT ''审核人姓名(到人留痕)'' AFTER `reviewer_staff_code`',
  'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1.4 审核时间
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_staff_outing' AND COLUMN_NAME = 'reviewed_at');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `t_staff_outing` ADD COLUMN `reviewed_at` datetime DEFAULT NULL COMMENT ''审核时间'' AFTER `reviewer_name`',
  'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1.5 审核意见（驳回必填原因）
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_staff_outing' AND COLUMN_NAME = 'review_remark');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `t_staff_outing` ADD COLUMN `review_remark` varchar(500) DEFAULT NULL COMMENT ''审核意见(驳回必填原因)'' AFTER `reviewed_at`',
  'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1.6 出发打卡照片
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_staff_outing' AND COLUMN_NAME = 'departed_photo_key');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `t_staff_outing` ADD COLUMN `departed_photo_key` varchar(255) DEFAULT NULL COMMENT ''出发打卡照片fileKey(必填，现场凭证)'' AFTER `departed_location_ciphertext`',
  'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1.7 返回打卡照片
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_staff_outing' AND COLUMN_NAME = 'returned_photo_key');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `t_staff_outing` ADD COLUMN `returned_photo_key` varchar(255) DEFAULT NULL COMMENT ''返回打卡照片fileKey(必填，现场凭证)'' AFTER `returned_location_ciphertext`',
  'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 2. 新建默认态改为 PENDING_REVIEW（本人提交即待审核）
-- ------------------------------------------------------------
ALTER TABLE `t_staff_outing`
  MODIFY COLUMN `status` varchar(16) NOT NULL DEFAULT 'PENDING_REVIEW'
  COMMENT 'PENDING_REVIEW待审核/READY审核通过待出发/REJECTED已驳回/IN_PROGRESS外出中/COMPLETED已完成/CANCELLED已取消/DRAFT草稿';

-- ------------------------------------------------------------
-- 3. 重建状态 CHECK 约束（纳入 PENDING_REVIEW / REJECTED）
--    MySQL 8.0.16+ 支持 DROP CHECK；先删后加，整体幂等。
-- ------------------------------------------------------------
SET @chk_exists := (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 't_staff_outing' AND CONSTRAINT_NAME = 'chk_outing_status');
SET @ddl := IF(@chk_exists > 0, 'ALTER TABLE `t_staff_outing` DROP CHECK `chk_outing_status`', 'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

ALTER TABLE `t_staff_outing`
  ADD CONSTRAINT `chk_outing_status`
  CHECK (`status` IN ('DRAFT','PENDING_REVIEW','REJECTED','READY','IN_PROGRESS','COMPLETED','CANCELLED'));

-- ------------------------------------------------------------
-- 4. 待审核列表索引（主管按「状态 + 计划时间」筛本部门待审）
-- ------------------------------------------------------------
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_staff_outing' AND INDEX_NAME = 'idx_outing_status_time');
SET @ddl := IF(@idx_exists = 0,
  'ALTER TABLE `t_staff_outing` ADD INDEX `idx_outing_status_time` (`status`,`planned_start`)',
  'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 5. 表注释更新（说明新的履约链路）
-- ------------------------------------------------------------
ALTER TABLE `t_staff_outing`
  COMMENT = '员工上门拜访外出：本人提交申请→主管审核→出发/返回双打卡(图片+单点定位)，无连续轨迹';
