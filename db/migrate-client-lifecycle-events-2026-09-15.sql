-- 客户生命周期事件：支撑首次跟进自然小时、公海停留自然小时
CREATE TABLE IF NOT EXISTS `t_client_lifecycle_event` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `client_code` varchar(64) NOT NULL COMMENT '客户业务编码',
  `event_type` varchar(32) NOT NULL COMMENT 'OWNER_ASSIGNED/FIRST_FOLLOW/ENTER_COMPANY_SEA/ENTER_TEAM_SEA/EXIT_SEA',
  `staff_code` varchar(32) DEFAULT NULL COMMENT '归属或跟进员工工号',
  `sea_level` varchar(16) DEFAULT NULL COMMENT 'ENTERPRISE/TEAM',
  `sea_dept_code` varchar(32) DEFAULT NULL,
  `episode_no` int NOT NULL DEFAULT 1 COMMENT '归属/公海周期号',
  `event_at` datetime NOT NULL COMMENT '事件发生时间',
  `operator_staff_code` varchar(32) DEFAULT NULL,
  `reason_code` varchar(64) DEFAULT NULL COMMENT '回收/释放原因',
  PRIMARY KEY (`id`),
  KEY `idx_client_event_time` (`client_code`,`event_type`,`event_at`),
  KEY `idx_event_time` (`event_type`,`event_at`),
  KEY `idx_staff_event_time` (`staff_code`,`event_type`,`event_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户生命周期事件';

-- 同一客户同一生命周期周期内，每类关键事件只允许一条，避免并发重复首跟/出池记录。
ALTER TABLE `t_client_lifecycle_event`
  ADD UNIQUE KEY `uk_client_event_episode` (`client_code`,`event_type`,`episode_no`);

-- 历史客户基线：仅补没有任何生命周期事件的客户，所有时间均为历史估算基线。
-- 已分配客户以 created_at 作为归属起点；当前公海客户以 created_at 作为入池起点。
INSERT INTO `t_client_lifecycle_event`
  (`client_code`,`event_type`,`staff_code`,`sea_level`,`sea_dept_code`,`episode_no`,`event_at`,`reason_code`)
SELECT cp.client_code, 'OWNER_ASSIGNED', cp.owner_staff_code, NULL, NULL, 1,
       COALESCE(cp.created_at, CURRENT_TIMESTAMP), 'HISTORICAL_BASELINE_ESTIMATE'
FROM t_client_profile cp
WHERE cp.owner_staff_code IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM t_client_lifecycle_event e WHERE e.client_code = cp.client_code);

INSERT INTO `t_client_lifecycle_event`
  (`client_code`,`event_type`,`staff_code`,`sea_level`,`sea_dept_code`,`episode_no`,`event_at`,`reason_code`)
SELECT cp.client_code,
       CASE WHEN cp.sea_level = 'TEAM' THEN 'ENTER_TEAM_SEA' ELSE 'ENTER_COMPANY_SEA' END,
       NULL, CASE WHEN cp.sea_level = 'TEAM' THEN 'TEAM' ELSE 'ENTERPRISE' END,
       cp.sea_dept_code, 1, COALESCE(cp.created_at, CURRENT_TIMESTAMP),
       'HISTORICAL_BASELINE_ESTIMATE'
FROM t_client_profile cp
WHERE cp.owner_staff_code IS NULL
  AND cp.sea_level IN ('TEAM','ENTERPRISE')
  AND NOT EXISTS (SELECT 1 FROM t_client_lifecycle_event e WHERE e.client_code = cp.client_code);
