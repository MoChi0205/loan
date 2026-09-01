-- ============================================================
-- #4b OCR 回灌诊断（打通假闭环）
-- 运行方式：在 loan-service 连接的 MySQL 中执行本文件
-- ① t_ocr_record 补 biz_code（业务编码，红线#3：不用主键做查询）+ updated_at
-- ② t_service_attachment 补 report_no（诊断补充材料按报告回溯）
-- ============================================================

-- (1) t_ocr_record 补业务编码列（红线#3：不用主键做查询）
ALTER TABLE `t_ocr_record`
  ADD COLUMN `biz_code` varchar(64) DEFAULT NULL
      COMMENT '关联业务编码(业务ID:reportNo/clientCode/productCode;替代biz_id物理主键查询)' AFTER `biz_id`,
  ADD COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
      COMMENT '更新时间(识别结果二次回填/人工修正)' AFTER `created_at`,
  ADD KEY `idx_scene_biz_code` (`biz_scene`,`biz_code`);

-- (2) t_service_attachment 补报告关联（诊断补充材料需按报告回溯）
ALTER TABLE `t_service_attachment`
  ADD COLUMN `report_no` varchar(64) DEFAULT NULL
      COMMENT '关联初筛报告编号(业务ID:report+32位随机;诊断补充材料场景)' AFTER `client_profile_code`,
  ADD KEY `idx_report_no` (`report_no`);
