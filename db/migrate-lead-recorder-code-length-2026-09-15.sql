-- 录入主体既可能是员工工号，也可能是渠道稳定业务编号，统一与 schema 的 varchar(64) 对齐。
ALTER TABLE `t_lead`
  MODIFY COLUMN `recorder_staff_code` varchar(64) DEFAULT NULL COMMENT '录入主体业务编码(员工工号或渠道稳定userNo)';
