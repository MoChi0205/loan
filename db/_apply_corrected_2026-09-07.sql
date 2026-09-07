-- 业务编码对齐迁移（2026-09-07，安全幂等版）
-- 本文件不会自动执行，也不能证明已落到 prd；执行前须 DBA 只读核对、备份并审批。
-- 只新增业务编码列并回填，旧 bigint 列暂不删除；新代码仅读取 *_code/*_no/file_key。
SET NAMES utf8mb4;

-- OCR 记录业务关联
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='t_ocr_record' AND column_name='biz_code')=0,
 'ALTER TABLE t_ocr_record ADD COLUMN biz_code varchar(64) DEFAULT NULL COMMENT ''关联业务编码'', ADD KEY idx_scene_biz_code (biz_scene,biz_code)','SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='t_ocr_record' AND column_name='updated_at')=0,
 'ALTER TABLE t_ocr_record ADD COLUMN updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER created_at','SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='t_ocr_record' AND column_name='review_status')=0,
 'ALTER TABLE t_ocr_record ADD COLUMN review_status varchar(32) DEFAULT NULL AFTER updated_at, ADD COLUMN visible_flag tinyint(1) DEFAULT 0 AFTER review_status, ADD KEY idx_ocr_review_status (review_status)','SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 附件的工单、客户、报告均用业务编码关联
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='t_service_attachment' AND column_name='order_no')=0,
 'ALTER TABLE t_service_attachment ADD COLUMN order_no varchar(64) DEFAULT NULL COMMENT ''工单业务编号''','SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='t_service_attachment' AND column_name='client_profile_code')=0,
 'ALTER TABLE t_service_attachment ADD COLUMN client_profile_code varchar(64) DEFAULT NULL COMMENT ''客户业务编码''','SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='t_service_attachment' AND column_name='report_no')=0,
 'ALTER TABLE t_service_attachment ADD COLUMN report_no varchar(64) DEFAULT NULL COMMENT ''报告业务编号''','SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='t_service_attachment' AND column_name='order_id')>0,
 'UPDATE t_service_attachment a LEFT JOIN t_service_order o ON o.id=a.order_id SET a.order_no=o.order_no WHERE a.order_no IS NULL','SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='t_service_attachment' AND column_name='client_profile_id')>0,
 'UPDATE t_service_attachment a LEFT JOIN t_client_profile c ON c.id=a.client_profile_id SET a.client_profile_code=c.client_code WHERE a.client_profile_code IS NULL','SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='t_service_attachment' AND index_name='idx_report_no')=0,
 'ALTER TABLE t_service_attachment ADD KEY idx_report_no(report_no)','SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 客户提交与匹配链路业务编码
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='t_client_submission' AND column_name='client_profile_code')=0,
 'ALTER TABLE t_client_submission ADD COLUMN client_profile_code varchar(64) DEFAULT NULL COMMENT ''客户业务编码''','SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='t_client_submission' AND column_name='match_trace_no')=0,
 'ALTER TABLE t_client_submission ADD COLUMN match_trace_no varchar(64) DEFAULT NULL COMMENT ''匹配链路业务编号''','SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='t_client_submission' AND column_name='client_profile_id')>0,
 'UPDATE t_client_submission s LEFT JOIN t_client_profile c ON c.id=s.client_profile_id SET s.client_profile_code=c.client_code WHERE s.client_profile_code IS NULL','SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='t_client_submission' AND column_name='match_trace_id')>0,
 'UPDATE t_client_submission s LEFT JOIN t_match_trace t ON t.id=s.match_trace_id SET s.match_trace_no=t.trace_uuid WHERE s.match_trace_no IS NULL','SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='t_client_submission' AND index_name='idx_client_code_status')=0,
 'ALTER TABLE t_client_submission ADD KEY idx_client_code_status(client_profile_code,status)','SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 匹配审计业务编码（旧物理列保留为可空，仅用于历史回溯）
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='t_match_trace' AND column_name='client_profile_code')=0,
 'ALTER TABLE t_match_trace ADD COLUMN client_profile_code varchar(64) DEFAULT NULL COMMENT ''客户业务编码'', ADD COLUMN submission_no varchar(64) DEFAULT NULL COMMENT ''提交单业务编号'', ADD KEY idx_client_code(client_profile_code), ADD KEY idx_submission_no(submission_no)','SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='t_match_trace' AND column_name='client_profile_id')>0,
 'ALTER TABLE t_match_trace MODIFY COLUMN client_profile_id bigint NULL, MODIFY COLUMN submission_id bigint NULL','SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 报告命中明细以产品编码为关联；旧物理产品列迁移期允许为空
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='t_screening_product' AND column_name='product_code')=0,
 'ALTER TABLE t_screening_product ADD COLUMN product_code varchar(64) DEFAULT NULL COMMENT ''产品业务编码'' AFTER bank_product_id','SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='t_screening_product' AND column_name='bank_product_id')>0,
 'ALTER TABLE t_screening_product MODIFY COLUMN bank_product_id bigint NULL','SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 材料复核只保存 OCR 文件业务键，不保存物理 OCR ID
CREATE TABLE IF NOT EXISTS t_material_review (
 id bigint NOT NULL AUTO_INCREMENT, review_no varchar(64) NOT NULL,
 ocr_file_key varchar(255) DEFAULT NULL COMMENT 'OCR 文件业务键', biz_type varchar(32) DEFAULT NULL,
 client_profile_code varchar(64) DEFAULT NULL, report_no varchar(64) DEFAULT NULL,
 pending_facts_json json DEFAULT NULL, review_status varchar(32) NOT NULL DEFAULT 'PENDING_REVIEW',
 reviewer_staff_code varchar(64) DEFAULT NULL, review_opinion varchar(512) DEFAULT NULL,
 review_time datetime DEFAULT NULL, submission_no varchar(64) DEFAULT NULL,
 created_by varchar(64) DEFAULT NULL, updated_by varchar(64) DEFAULT NULL,
 created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at datetime DEFAULT NULL,
 PRIMARY KEY(id), UNIQUE KEY uk_review_no(review_no), KEY idx_review_status(review_status),
 KEY idx_review_ocr_file(ocr_file_key), KEY idx_review_client(client_profile_code), KEY idx_review_report(report_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='材料复核审批单';
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='t_material_review' AND column_name='ocr_file_key')=0,
 'ALTER TABLE t_material_review ADD COLUMN ocr_file_key varchar(255) DEFAULT NULL COMMENT ''OCR 文件业务键'' AFTER review_no, ADD KEY idx_review_ocr_file(ocr_file_key)','SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='t_material_review' AND column_name='ocr_record_id')>0,
 'UPDATE t_material_review r LEFT JOIN t_ocr_record o ON o.id=r.ocr_record_id SET r.ocr_file_key=o.file_key WHERE r.ocr_file_key IS NULL','SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 只读验收：
-- SELECT COUNT(*) FROM t_client_submission WHERE client_profile_code IS NULL;
-- SELECT COUNT(*) FROM t_service_attachment WHERE client_profile_code IS NULL;
-- SELECT COUNT(*) FROM t_material_review WHERE ocr_file_key IS NULL;
