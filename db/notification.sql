-- ============================================================
-- 站内消息通知表（参考 tse t_notification，业务 ID 遵循 docs/业务ID规范.md）
-- ============================================================
CREATE TABLE IF NOT EXISTS `t_notification` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `notification_id` varchar(64) NOT NULL COMMENT '通知业务ID(noti+32位随机)',
  `user_no` varchar(64) NOT NULL COMMENT '接收人(员工工号staff_code/渠道账号/客户编号)',
  `type` varchar(32) NOT NULL COMMENT '通知类型(SYSTEM_NOTICE系统通知/LEAD_RECYCLE_WARN线索回收预警/PRODUCT_APPROVAL产品审核/SERVICE_ORDER工单)',
  `title` varchar(128) NOT NULL COMMENT '通知标题',
  `content` varchar(1000) NOT NULL COMMENT '通知内容',
  `related_id` varchar(64) DEFAULT NULL COMMENT '关联业务ID(如线索lead_no/审核approval_no)',
  `read_status` tinyint NOT NULL DEFAULT '0' COMMENT '0未读/1已读',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `read_at` datetime DEFAULT NULL COMMENT '阅读时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notification_id` (`notification_id`),
  KEY `idx_user_read` (`user_no`,`read_status`),
  KEY `idx_type_related` (`type`,`related_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='站内消息通知(弹窗提醒;未读数缓存;参考tse NotificationPo)';
