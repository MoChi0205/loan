-- 员工普通外出：可不关联客户/预约；仍需主管审批，出发和返回均须照片+单点位置。
-- 幂等迁移；执行前必须从当前启动环境的 Nacos 获取实际数据源。
SET NAMES utf8mb4;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='t_staff_outing' AND COLUMN_NAME='client_code') > 0,
  'ALTER TABLE t_staff_outing MODIFY COLUMN client_code varchar(64) NULL COMMENT ''可选关联客户业务编号''',
  'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='t_staff_outing' AND COLUMN_NAME='appointment_no') > 0,
  'ALTER TABLE t_staff_outing MODIFY COLUMN appointment_no varchar(64) NULL COMMENT ''可选关联上门预约号''',
  'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='t_staff_outing' AND CONSTRAINT_NAME='chk_outing_type') > 0,
  'ALTER TABLE t_staff_outing DROP CHECK chk_outing_type', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
ALTER TABLE t_staff_outing ADD CONSTRAINT chk_outing_type CHECK (outing_type IN ('HOME_VISIT','GENERAL'));

ALTER TABLE t_staff_outing MODIFY COLUMN outing_type varchar(32) NOT NULL DEFAULT 'GENERAL'
  COMMENT 'HOME_VISIT上门拜访/GENERAL普通外出';
ALTER TABLE t_staff_outing COMMENT='员工外出：可关联上门预约或普通外出，主管审核后双打卡(图片+单点定位)，无连续轨迹';
