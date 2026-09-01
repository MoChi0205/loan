-- ============================================================
-- 业务 ID 统一（Wave 2）：FK 引用列 BIGINT → VARCHAR 业务编码
-- 依据：docs/skills/业务ID规范.md
--       业务唯一 ID = 小写前缀 + 32 位随机；内部自增主键保留但不暴露；
--       FK 引用列改存业务编码，不设物理外键。
-- 执行方式：mysql -uroot -p loan_db --default-character-set=utf8mb4 < db/migrate-bizid-fk.sql
-- 前提：先执行 db/migrate-bizid.sql（扩宽业务编码列）再执行本脚本。
-- 注意：本脚本为一次性迁移，执行前请备份数据库。
-- ============================================================
SET NAMES utf8mb4;
SET @OLD_FOREIGN_KEY_CHECKS = @@FOREIGN_KEY_CHECKS;
SET FOREIGN_KEY_CHECKS = 0;

-- ---------- 1. t_lead：归属/录入人/转正客户 改存业务编码 ----------
ALTER TABLE `t_lead`
  ADD COLUMN `recorder_staff_code` varchar(32) DEFAULT NULL COMMENT '录入人工号(业务编码)' AFTER `recorder_staff_id`,
  ADD COLUMN `owner_staff_code` varchar(32) DEFAULT NULL COMMENT '归属人工号(NULL=公海)' AFTER `owner_staff_id`,
  ADD COLUMN `client_profile_code` varchar(64) DEFAULT NULL COMMENT '转正客户编码(业务ID:client+32位随机)' AFTER `client_profile_id`;

UPDATE `t_lead` l LEFT JOIN `t_staff` s ON s.id = l.recorder_staff_id
   SET l.recorder_staff_code = s.staff_code;
UPDATE `t_lead` l LEFT JOIN `t_staff` s ON s.id = l.owner_staff_id
   SET l.owner_staff_code = s.staff_code;
UPDATE `t_lead` l LEFT JOIN `t_client_profile` c ON c.id = l.client_profile_id
   SET l.client_profile_code = c.client_code;

ALTER TABLE `t_lead`
  DROP INDEX `idx_owner_status_follow`,
  DROP INDEX `idx_public_pool`,
  DROP COLUMN `recorder_staff_id`,
  DROP COLUMN `owner_staff_id`,
  DROP COLUMN `client_profile_id`,
  ADD KEY `idx_owner_status_follow` (`owner_staff_code`, `follow_status`, `last_followed_at`),
  ADD KEY `idx_public_pool` (`lead_type`, `owner_staff_code`, `follow_status`);

-- ---------- 2. t_lead_allocation_record：线索/原归属/新归属 改存业务编码 ----------
ALTER TABLE `t_lead_allocation_record`
  ADD COLUMN `lead_no` varchar(64) NOT NULL COMMENT '线索编号(业务ID:lead+32位随机)' AFTER `lead_id`,
  ADD COLUMN `from_staff_code` varchar(32) DEFAULT NULL COMMENT '原归属人工号' AFTER `from_staff_id`,
  ADD COLUMN `to_staff_code` varchar(32) DEFAULT NULL COMMENT '新归属人工号' AFTER `to_staff_id`;

UPDATE `t_lead_allocation_record` r LEFT JOIN `t_lead` l ON l.id = r.lead_id
   SET r.lead_no = l.lead_no;
UPDATE `t_lead_allocation_record` r LEFT JOIN `t_staff` s ON s.id = r.from_staff_id
   SET r.from_staff_code = s.staff_code;
UPDATE `t_lead_allocation_record` r LEFT JOIN `t_staff` s ON s.id = r.to_staff_id
   SET r.to_staff_code = s.staff_code;

ALTER TABLE `t_lead_allocation_record`
  DROP INDEX `idx_lead`,
  DROP COLUMN `lead_id`,
  DROP COLUMN `from_staff_id`,
  DROP COLUMN `to_staff_id`,
  ADD KEY `idx_lead_no` (`lead_no`);

-- ---------- 3. t_staff：所属部门 改存部门编码 ----------
ALTER TABLE `t_staff`
  ADD COLUMN `dept_code` varchar(32) DEFAULT NULL COMMENT '所属部门编码(业务编码)' AFTER `dept_id`;

UPDATE `t_staff` s LEFT JOIN `t_department` d ON d.id = s.dept_id
   SET s.dept_code = d.dept_code;

ALTER TABLE `t_staff`
  DROP INDEX `idx_dept_role`,
  DROP COLUMN `dept_id`,
  ADD KEY `idx_dept_role` (`dept_code`, `role_code`);

-- ---------- 4. t_department：上级部门/负责人 改存业务编码 ----------
ALTER TABLE `t_department`
  ADD COLUMN `parent_code` varchar(32) DEFAULT NULL COMMENT '上级部门编码(顶级为NULL)' AFTER `parent_id`,
  ADD COLUMN `leader_staff_code` varchar(32) DEFAULT NULL COMMENT '负责人员工工号' AFTER `leader_staff_id`;

UPDATE `t_department` d LEFT JOIN `t_department` p ON p.id = d.parent_id
   SET d.parent_code = p.dept_code;
UPDATE `t_department` d LEFT JOIN `t_staff` s ON s.id = d.leader_staff_id
   SET d.leader_staff_code = s.staff_code;

ALTER TABLE `t_department`
  DROP INDEX `idx_parent_id`,
  DROP COLUMN `parent_id`,
  DROP COLUMN `leader_staff_id`,
  ADD KEY `idx_parent_code` (`parent_code`);

-- ---------- 5. t_service_attachment：工单/客户 改存业务编码 ----------
ALTER TABLE `t_service_attachment`
  ADD COLUMN `order_no` varchar(64) NOT NULL COMMENT '工单号(业务ID:order+32位随机)' AFTER `order_id`,
  ADD COLUMN `client_profile_code` varchar(64) NOT NULL COMMENT '客户编码(业务ID:client+32位随机)' AFTER `client_profile_id`;

UPDATE `t_service_attachment` a LEFT JOIN `t_service_order` o ON o.id = a.order_id
   SET a.order_no = o.order_no;
UPDATE `t_service_attachment` a LEFT JOIN `t_client_profile` c ON c.id = a.client_profile_id
   SET a.client_profile_code = c.client_code;

ALTER TABLE `t_service_attachment`
  DROP INDEX `idx_order`,
  DROP INDEX `idx_client_type`,
  DROP COLUMN `order_id`,
  DROP COLUMN `client_profile_id`,
  ADD KEY `idx_order_no` (`order_no`),
  ADD KEY `idx_client_type` (`client_profile_code`, `attachment_type`);

-- ---------- 6. t_bank_product：所属银行渠道 改存渠道编码 ----------
ALTER TABLE `t_bank_product`
  ADD COLUMN `bank_channel_code` varchar(32) NOT NULL COMMENT '所属银行渠道编码' AFTER `bank_channel_id`;

UPDATE `t_bank_product` p LEFT JOIN `t_bank_channel` c ON c.id = p.bank_channel_id
   SET p.bank_channel_code = c.channel_code;

ALTER TABLE `t_bank_product`
  DROP INDEX `idx_bank_group_status`,
  DROP COLUMN `bank_channel_id`,
  ADD KEY `idx_bank_group_status` (`bank_channel_code`, `customer_group`, `status`);

-- ---------- 7. t_service_order（补充，订单模块主表）：客户/归属顾问/产品 改存业务编码 ----------
ALTER TABLE `t_service_order`
  ADD COLUMN `client_profile_code` varchar(64) NOT NULL COMMENT '客户编码(业务ID:client+32位随机)' AFTER `client_profile_id`,
  ADD COLUMN `owner_staff_code` varchar(32) DEFAULT NULL COMMENT '顾问员工工号(业务编码)' AFTER `owner_staff_id`,
  ADD COLUMN `bank_product_code` varchar(64) DEFAULT NULL COMMENT '关联银行产品编码(业务编码)' AFTER `bank_product_id`;

UPDATE `t_service_order` o LEFT JOIN `t_client_profile` c ON c.id = o.client_profile_id
   SET o.client_profile_code = c.client_code;
UPDATE `t_service_order` o LEFT JOIN `t_staff` s ON s.id = o.owner_staff_id
   SET o.owner_staff_code = s.staff_code;
UPDATE `t_service_order` o LEFT JOIN `t_bank_product` p ON p.id = o.bank_product_id
   SET o.bank_product_code = p.product_code;

ALTER TABLE `t_service_order`
  DROP INDEX `idx_client_status`,
  DROP INDEX `idx_owner_status`,
  DROP COLUMN `client_profile_id`,
  DROP COLUMN `owner_staff_id`,
  DROP COLUMN `bank_product_id`,
  ADD KEY `idx_client_status` (`client_profile_code`, `status`),
  ADD KEY `idx_owner_status` (`owner_staff_code`, `status`);

-- ---------- 8. t_client_profile（补充，客户档案主表）：归属顾问/来源线索 改存业务编码 ----------
ALTER TABLE `t_client_profile`
  ADD COLUMN `owner_staff_code` varchar(32) DEFAULT NULL COMMENT '归属顾问工号(业务编码)' AFTER `owner_staff_id`,
  ADD COLUMN `lead_no` varchar(64) DEFAULT NULL COMMENT '来源线索编号(业务ID:lead+32位随机)' AFTER `lead_id`;

UPDATE `t_client_profile` p LEFT JOIN `t_staff` s ON s.id = p.owner_staff_id
   SET p.owner_staff_code = s.staff_code;
UPDATE `t_client_profile` p LEFT JOIN `t_lead` l ON l.id = p.lead_id
   SET p.lead_no = l.lead_no;

ALTER TABLE `t_client_profile`
  DROP COLUMN `owner_staff_id`,
  DROP COLUMN `lead_id`;

-- ---------- 9. t_reward_record（补充，奖励流水）：推荐人/被推荐人/工单/结算人 改存业务编码 ----------
ALTER TABLE `t_reward_record`
  ADD COLUMN `referrer_client_code` varchar(64) DEFAULT NULL COMMENT '推荐人客户编码(业务ID:client+32位随机)' AFTER `referrer_client_id`,
  ADD COLUMN `referee_client_code` varchar(64) NOT NULL COMMENT '被推荐人客户编码(业务ID:client+32位随机)' AFTER `referee_client_id`,
  ADD COLUMN `service_order_no` varchar(64) NOT NULL COMMENT '关联工单号(业务ID:order+32位随机)' AFTER `service_order_id`,
  ADD COLUMN `settle_staff_code` varchar(32) DEFAULT NULL COMMENT '结算人(员工工号)' AFTER `settle_staff_id`;

UPDATE `t_reward_record` r LEFT JOIN `t_client_profile` c ON c.id = r.referrer_client_id
   SET r.referrer_client_code = c.client_code;
UPDATE `t_reward_record` r LEFT JOIN `t_client_profile` c ON c.id = r.referee_client_id
   SET r.referee_client_code = c.client_code;
UPDATE `t_reward_record` r LEFT JOIN `t_service_order` o ON o.id = r.service_order_id
   SET r.service_order_no = o.order_no;
UPDATE `t_reward_record` r LEFT JOIN `t_staff` s ON s.id = r.settle_staff_id
   SET r.settle_staff_code = s.staff_code;

ALTER TABLE `t_reward_record`
  DROP INDEX `uk_order_referrer_level`,
  DROP INDEX `idx_referrer_status`,
  DROP COLUMN `referrer_client_id`,
  DROP COLUMN `referee_client_id`,
  DROP COLUMN `service_order_id`,
  DROP COLUMN `settle_staff_id`,
  ADD UNIQUE KEY `uk_order_referrer_level` (`service_order_no`, `referrer_client_code`, `level`),
  ADD KEY `idx_referrer_status` (`referrer_client_code`, `status`);

-- ---------- 10. t_withdraw_record（补充，提现记录）：奖励单 改存业务编码 ----------
ALTER TABLE `t_withdraw_record`
  ADD COLUMN `reward_no` varchar(64) DEFAULT NULL COMMENT '奖励单号(业务ID:reward+32位随机)' AFTER `reward_id`;

UPDATE `t_withdraw_record` w LEFT JOIN `t_reward_record` r ON r.id = w.reward_id
   SET w.reward_no = r.reward_no;

ALTER TABLE `t_withdraw_record`
  DROP INDEX `idx_reward`,
  DROP COLUMN `reward_id`,
  ADD KEY `idx_reward_no` (`reward_no`);

-- ---------- 11. t_product_approval（补充，产品审核工单）：产品/审核人 改存业务编码 ----------
ALTER TABLE `t_product_approval`
  ADD COLUMN `bank_product_code` varchar(64) NOT NULL COMMENT '银行产品编码(业务编码)' AFTER `bank_product_id`,
  ADD COLUMN `approver_staff_code` varchar(32) DEFAULT NULL COMMENT '审核人工号(业务编码)' AFTER `approver_staff_id`;

UPDATE `t_product_approval` a LEFT JOIN `t_bank_product` p ON p.id = a.bank_product_id
   SET a.bank_product_code = p.product_code;
UPDATE `t_product_approval` a LEFT JOIN `t_staff` s ON s.id = a.approver_staff_id
   SET a.approver_staff_code = s.staff_code;

ALTER TABLE `t_product_approval`
  DROP COLUMN `bank_product_id`,
  DROP COLUMN `approver_staff_id`;

-- ---------- 12. t_attachment_download_approval（补充，附件下载审批单）：申请人/审批人 改存业务编码 ----------
ALTER TABLE `t_attachment_download_approval`
  ADD COLUMN `applicant_staff_code` varchar(32) NOT NULL COMMENT '申请人工号(业务编码)' AFTER `applicant_staff_id`,
  ADD COLUMN `approver_staff_code` varchar(32) DEFAULT NULL COMMENT '审批人工号(业务编码)' AFTER `approver_staff_id`;

UPDATE `t_attachment_download_approval` a LEFT JOIN `t_staff` s ON s.id = a.applicant_staff_id
   SET a.applicant_staff_code = s.staff_code;
UPDATE `t_attachment_download_approval` a LEFT JOIN `t_staff` s ON s.id = a.approver_staff_id
   SET a.approver_staff_code = s.staff_code;

ALTER TABLE `t_attachment_download_approval`
  DROP INDEX `idx_applicant_status`,
  DROP COLUMN `applicant_staff_id`,
  DROP COLUMN `approver_staff_id`,
  ADD KEY `idx_applicant_status` (`applicant_staff_code`, `approve_status`);

SET FOREIGN_KEY_CHECKS = @OLD_FOREIGN_KEY_CHECKS;

-- ============================================================
-- 迁移后校验（应全部返回空结果）：
--   SELECT * FROM t_lead WHERE owner_staff_code IS NULL AND owner_staff_id 已删（无法再查）
--   建议抽查：SELECT COUNT(*) FROM t_lead_allocation_record WHERE lead_no IS NULL;
--   计数为 0 即迁移成功。
-- ============================================================

-- ---------- 14. t_client_submission / t_client_business_fact：FK 引用列编码化（阶段三小程序） ----------
ALTER TABLE `t_client_submission`
  ADD COLUMN `client_profile_code` varchar(64) NOT NULL COMMENT '客户编码(业务ID)' AFTER `client_profile_id`;

UPDATE `t_client_submission` s LEFT JOIN `t_client_profile` c ON c.id = s.client_profile_id
   SET s.client_profile_code = c.client_code;

ALTER TABLE `t_client_submission`
  DROP INDEX `idx_client_status`,
  DROP COLUMN `client_profile_id`,
  ADD KEY `idx_client_status` (`client_profile_code`, `status`);

ALTER TABLE `t_client_business_fact`
  ADD COLUMN `client_profile_code` varchar(64) NOT NULL COMMENT '客户编码(业务ID)' AFTER `client_profile_id`,
  ADD COLUMN `submission_no` varchar(64) NOT NULL COMMENT '提交单号(业务ID)' AFTER `submission_id`;

UPDATE `t_client_business_fact` f LEFT JOIN `t_client_profile` c ON c.id = f.client_profile_id
   SET f.client_profile_code = c.client_code;
UPDATE `t_client_business_fact` f LEFT JOIN `t_client_submission` s ON s.id = f.submission_id
   SET f.submission_no = s.submission_no;

ALTER TABLE `t_client_business_fact`
  DROP COLUMN `client_profile_id`,
  DROP COLUMN `submission_id`;
