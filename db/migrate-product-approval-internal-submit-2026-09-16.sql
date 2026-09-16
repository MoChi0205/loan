-- 公司员工在全量库提交产品时没有渠道账号，审批记录必须允许 channel_user_id 为空。
ALTER TABLE `t_product_approval`
  MODIFY COLUMN `channel_user_id` bigint NULL COMMENT '提交渠道账号ID；公司内部员工提交时为空';
