-- 报告双视角不可变快照与 PDF 产物（开发阶段直接执行）。
ALTER TABLE t_client_screening
  ADD COLUMN customer_snapshot_key varchar(255) DEFAULT NULL COMMENT '客户视角快照对象键',
  ADD COLUMN staff_snapshot_key varchar(255) DEFAULT NULL COMMENT '员工视角快照对象键',
  ADD COLUMN customer_pdf_key varchar(255) DEFAULT NULL COMMENT '客户视角PDF对象键',
  ADD COLUMN staff_pdf_key varchar(255) DEFAULT NULL COMMENT '员工视角PDF对象键';

-- 严格禁止历史手机号明文继续存在。执行前应由受控迁移程序使用当前 aes.key 重加密；
-- 本脚本只负责发现残留并中止，不在数据库侧猜测密钥。
SET @legacy_phone_count := (
  SELECT COUNT(*) FROM t_client_profile WHERE phone REGEXP '^1[0-9]{10}$'
);
SET @legacy_phone_count_lead := (
  SELECT COUNT(*) FROM t_lead WHERE phone REGEXP '^1[0-9]{10}$'
);
SET @legacy_phone_count_staff := (
  SELECT COUNT(*) FROM t_staff WHERE phone REGEXP '^1[0-9]{10}$'
);
SET @legacy_phone_count_sms := (
  SELECT COUNT(*) FROM t_sms_record WHERE phone REGEXP '^1[0-9]{10}$'
);
SET @legacy_phone_total := @legacy_phone_count + @legacy_phone_count_lead + @legacy_phone_count_staff + @legacy_phone_count_sms;
SET @legacy_sql := IF(@legacy_phone_total = 0, 'SELECT ''AES legacy plaintext check passed'' AS result',
  'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''发现手机号明文，先使用当前 aes.key 完成受控重加密''');
PREPARE legacy_stmt FROM @legacy_sql;
EXECUTE legacy_stmt;
DEALLOCATE PREPARE legacy_stmt;
