-- 预约到店时间索引（09-21 方案 §6.2 建议索引）。
-- 幂等：information_schema 守卫，已存在则不重复创建。
SET @db := DATABASE();

SET @sql := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `t_client_appointment` ADD KEY `idx_appointment_arrived` (`actual_arrived_at`)',
  'SELECT ''idx_appointment_arrived exists''')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_client_appointment' AND INDEX_NAME = 'idx_appointment_arrived');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
