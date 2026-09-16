-- 下载审批绑定唯一客户，并冻结申请时的客户/报告/文件清单，避免审批时无法确认材料归属。
ALTER TABLE `t_attachment_download_approval`
  ADD COLUMN `client_profile_code` varchar(64) NULL COMMENT '本次申请唯一所属客户编码' AFTER `attachment_ids`,
  ADD COLUMN `attachment_summary_json` json NULL COMMENT '申请时冻结的客户/报告/文件明细快照' AFTER `client_profile_code`,
  ADD KEY `idx_download_client` (`client_profile_code`);
