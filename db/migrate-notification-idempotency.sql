-- 站内业务通知幂等约束：同一接收人、同一类型、同一关联业务只允许一条。
-- 执行前先处理历史重复数据；本脚本不自动删除任何通知。
SELECT `user_no`, `type`, `related_id`, COUNT(*) AS duplicate_count
FROM `t_notification`
WHERE `related_id` IS NOT NULL
GROUP BY `user_no`, `type`, `related_id`
HAVING COUNT(*) > 1;

-- 确认上方查询无结果后执行：
ALTER TABLE `t_notification`
  ADD UNIQUE KEY `uk_notify_biz_once` (`user_no`,`type`,`related_id`);
