-- ============================================================
-- 业务 ID 字段迁移脚本（varchar(32) → varchar(64)）
-- 依据：docs/业务ID规范.md（业务 ID = 小写前缀 + 32 位随机）
--
-- 执行方式：mysql -uroot -p loan_db --default-character-set=utf8mb4 < db/migrate-bizid.sql
-- 说明：仅扩字段宽度，不动数据；存量短码后续按迁移窗口批量替换。
-- ============================================================
SET NAMES utf8mb4;

ALTER TABLE `t_client_profile` MODIFY `client_code` varchar(64) NOT NULL COMMENT '客户编码(业务ID:client+32位随机)';
ALTER TABLE `t_client_submission` MODIFY `submission_no` varchar(64) NOT NULL COMMENT '提交单号(业务ID:submit+32位随机)';
ALTER TABLE `t_client_screening` MODIFY `report_no` varchar(64) NOT NULL COMMENT '报告编号(业务ID:report+32位随机)';
ALTER TABLE `t_product_approval` MODIFY `approval_no` varchar(64) NOT NULL COMMENT '审核工单号(业务ID:prdapr+32位随机)';
ALTER TABLE `t_service_order` MODIFY `order_no` varchar(64) NOT NULL COMMENT '工单号(业务ID:order+32位随机)';
ALTER TABLE `t_attachment_download_approval` MODIFY `approval_no` varchar(64) NOT NULL COMMENT '申请单号(业务ID:dldapr+32位随机)';
ALTER TABLE `t_reward_record` MODIFY `reward_no` varchar(64) NOT NULL COMMENT '奖励单号(业务ID:reward+32位随机)';
ALTER TABLE `t_lead` MODIFY `lead_no` varchar(64) NOT NULL COMMENT '线索编号(业务ID:lead+32位随机)';
ALTER TABLE `t_lead_archive` MODIFY `lead_no` varchar(64) NOT NULL COMMENT '线索编号(业务ID:lead+32位随机)';
ALTER TABLE `t_vip_order` MODIFY `order_no` varchar(64) NOT NULL COMMENT '订单号(业务ID:order+32位随机)';
