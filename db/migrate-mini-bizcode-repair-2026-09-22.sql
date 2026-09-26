-- 业务编码迁移收尾（针对已部分执行的测试库，幂等）
-- 来源数据源必须由当前 Nacos namespace 提供；不在本文件写连接信息。
SET NAMES utf8mb4;

-- t_partner_product：先确认业务编码已回填，再移除旧 bigint 关联。
SET @n := (SELECT COUNT(*) FROM t_partner_product WHERE bank_product_code IS NULL);
SET @sql := IF(@n = 0, 'DO 0', 'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''t_partner_product.bank_product_code 存在空值，停止迁移''');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @n := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='t_partner_product' AND index_name='uk_bank_product_id');
SET @sql := IF(@n > 0, 'ALTER TABLE t_partner_product DROP INDEX uk_bank_product_id', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @n := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='t_partner_product' AND column_name='bank_product_id');
SET @sql := IF(@n > 0, 'ALTER TABLE t_partner_product DROP COLUMN bank_product_id', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @n := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='t_partner_product' AND index_name='uk_bank_product_code');
SET @sql := IF(@n = 0, 'ALTER TABLE t_partner_product ADD UNIQUE KEY uk_bank_product_code (bank_product_code)', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- t_personal_profile：业务编码已回填后移除旧 client_profile_id。
SET @n := (SELECT COUNT(*) FROM t_personal_profile WHERE client_profile_code IS NULL);
SET @sql := IF(@n = 0, 'DO 0', 'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''t_personal_profile.client_profile_code 存在空值，停止迁移''');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @n := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='t_personal_profile' AND index_name='uk_client_profile_id');
SET @sql := IF(@n > 0, 'ALTER TABLE t_personal_profile DROP INDEX uk_client_profile_id', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @n := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='t_personal_profile' AND column_name='client_profile_id');
SET @sql := IF(@n > 0, 'ALTER TABLE t_personal_profile DROP COLUMN client_profile_id', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @n := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='t_personal_profile' AND index_name='uk_client_profile_code');
SET @sql := IF(@n = 0, 'ALTER TABLE t_personal_profile ADD UNIQUE KEY uk_client_profile_code (client_profile_code)', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- t_personal_auth：业务编码已回填后移除旧 client_profile_id。
SET @n := (SELECT COUNT(*) FROM t_personal_auth WHERE client_profile_code IS NULL);
SET @sql := IF(@n = 0, 'DO 0', 'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''t_personal_auth.client_profile_code 存在空值，停止迁移''');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @n := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='t_personal_auth' AND index_name='idx_client_type');
SET @sql := IF(@n > 0, 'ALTER TABLE t_personal_auth DROP INDEX idx_client_type', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @n := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='t_personal_auth' AND column_name='client_profile_id');
SET @sql := IF(@n > 0, 'ALTER TABLE t_personal_auth DROP COLUMN client_profile_id', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @n := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='t_personal_auth' AND index_name='idx_client_type');
SET @sql := IF(@n = 0, 'ALTER TABLE t_personal_auth ADD KEY idx_client_type (client_profile_code, auth_type)', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- t_client_submission：回填后移除旧 match_trace_id。
SET @n := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='t_client_submission' AND column_name='match_trace_id');
SET @sql := IF(@n > 0, 'UPDATE t_client_submission s LEFT JOIN t_match_trace t ON t.id=s.match_trace_id SET s.match_trace_no=t.trace_uuid WHERE s.match_trace_no IS NULL', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @n := (SELECT COUNT(*) FROM t_client_submission WHERE match_trace_no IS NULL AND status IN ('MATCHING','MATCHED'));
SET @sql := IF(@n = 0, 'DO 0', 'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''已匹配提交单存在空 match_trace_no，停止迁移''');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @n := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='t_client_submission' AND column_name='match_trace_id');
SET @sql := IF(@n > 0, 'ALTER TABLE t_client_submission DROP COLUMN match_trace_id', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- t_client_screening 当前结构已经使用 match_trace_uuid；不重命名，不删除已存在业务列。
SET @n := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='t_client_screening' AND column_name='match_trace_uuid');
SET @sql := IF(@n = 1, 'DO 0', 'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''t_client_screening 缺少 match_trace_uuid，停止迁移''');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- t_match_trace 业务编码列和索引已存在时跳过；历史孤立记录保留审计，不伪造编码。
