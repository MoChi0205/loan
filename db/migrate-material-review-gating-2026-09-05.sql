-- ============================================================
-- 材料复核审批门控 + VLM 识别字段映射种子
-- 适用：上传文件经大模型(VLM)识别 → 提交材料复核 → 我司审批通过后才回灌客数据
-- 配套后端：VlmOcrExtractor / MiniMaterialService(ingest) / MaterialReviewService / ApprovalService(MATERIAL_REVIEW)
-- ============================================================

-- 1) t_ocr_record 增加复核状态与客户可见标志（门控）
ALTER TABLE `t_ocr_record`
    ADD COLUMN `review_status` varchar(32) DEFAULT NULL COMMENT '复核状态(PENDING_REVIEW待复核/APPROVED已通过/REJECTED已驳回)' AFTER `updated_at`,
    ADD COLUMN `visible_flag` tinyint(1) DEFAULT 0 COMMENT '客户可见标志(0不可见/1可见，审批通过后置1)' AFTER `review_status`;
CREATE INDEX `idx_ocr_review_status` ON `t_ocr_record` (`review_status`);

-- 2) 新建材料复核审批单表
DROP TABLE IF EXISTS `t_material_review`;
CREATE TABLE `t_material_review` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `review_no` varchar(64) NOT NULL COMMENT '复核单号(业务唯一ID: matrev + 32位随机)',
  `ocr_record_id` bigint DEFAULT NULL COMMENT '关联OCR记录主键(t_ocr_record.id)',
  `biz_type` varchar(32) DEFAULT NULL COMMENT '资料类型(ID_CARD/ BUSINESS_LICENSE/FINANCIAL_STATEMENT/CONTRACT/DUE_DILIGENCE/OTHER)',
  `client_profile_code` varchar(64) DEFAULT NULL COMMENT '客户编码(业务ID)',
  `report_no` varchar(64) DEFAULT NULL COMMENT '关联报告编号(诊断材料回灌用)',
  `pending_facts_json` json DEFAULT NULL COMMENT '待复核事实JSON(VLM抽取后经t_extract_field_def映射的规范facts)',
  `review_status` varchar(32) NOT NULL DEFAULT 'PENDING_REVIEW' COMMENT '复核状态(PENDING_REVIEW/APPROVED/REJECTED)',
  `reviewer_staff_code` varchar(64) DEFAULT NULL COMMENT '审批人工号(业务编码)',
  `review_opinion` varchar(512) DEFAULT NULL COMMENT '审批意见(驳回必填)',
  `review_time` datetime DEFAULT NULL COMMENT '复核完成时间',
  `submission_no` varchar(64) DEFAULT NULL COMMENT '回灌后的提交单号',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人(上传操作人)',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_review_no` (`review_no`),
  KEY `idx_review_status` (`review_status`),
  KEY `idx_review_client` (`client_profile_code`),
  KEY `idx_review_report` (`report_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='材料复核审批单(上传识别结果门控，审批通过才回灌客数据)';

-- 3) t_extract_field_def 映射种子（VLM 抽取的原始字段 → 规范 facts）
--    OcrService.mapToFacts 按 extract_rule_json 将模型返回的原始字段映射到 targetFactKey。
--    下列种子覆盖企业/个人常见财报与证照字段；COMMON 对所有客群生效。
--    注意：sourceKeys 取「模型可能返回的任一别名」，命中第一个即写入；transform=YUAN 尝试数值化。

INSERT INTO `t_extract_field_def` (`field_code`, `field_name`, `field_type`, `customer_group`, `extract_rule_json`, `status`, `created_by`, `created_at`)
VALUES
  ('entName',       '企业名称',     'STRING', 'COMMON',    '{"sourceKeys":["entName","企业名称","公司名称","单位名称"],"targetFactKey":"entName"}',                 'ACTIVE', 'system', NOW()),
  ('creditCode',    '统一社会信用代码', 'STRING', 'COMMON', '{"sourceKeys":["creditCode","统一社会信用代码","税号","社会信用代码"],"targetFactKey":"creditCode"}',      'ACTIVE', 'system', NOW()),
  ('industry',      '所属行业',     'STRING', 'COMMON',    '{"sourceKeys":["industry","所属行业","行业","经营范围"],"targetFactKey":"industry"}',                   'ACTIVE', 'system', NOW()),
  ('foundYears',    '成立年限',     'NUMBER', 'COMMON',    '{"sourceKeys":["foundYears","成立年限","经营年限","存续年限"],"transform":"YUAN","targetFactKey":"foundYears"}', 'ACTIVE', 'system', NOW()),
  ('annualTaxAmount',   '年纳税额',  'NUMBER', 'ENTERPRISE', '{"sourceKeys":["annualTaxAmount","年纳税额","年度纳税额","纳税总额","实缴税额"],"transform":"YUAN","targetFactKey":"annualTaxAmount"}', 'ACTIVE', 'system', NOW()),
  ('annualInvoiceAmount','年开票额', 'NUMBER', 'ENTERPRISE', '{"sourceKeys":["annualInvoiceAmount","年开票额","年度开票额","开票总额","销售开票金额"],"transform":"YUAN","targetFactKey":"annualInvoiceAmount"}', 'ACTIVE', 'system', NOW()),
  ('annualRevenue', '年营收',       'NUMBER', 'ENTERPRISE', '{"sourceKeys":["annualRevenue","年营收","年度营收","营业收入","营业额"],"transform":"YUAN","targetFactKey":"annualRevenue"}', 'ACTIVE', 'system', NOW()),
  ('employeeCount', '从业人数',     'NUMBER', 'ENTERPRISE', '{"sourceKeys":["employeeCount","从业人数","员工人数","在职人数","参保人数"],"transform":"YUAN","targetFactKey":"employeeCount"}', 'ACTIVE', 'system', NOW()),
  ('registeredCapital','注册资本',  'NUMBER', 'ENTERPRISE', '{"sourceKeys":["registeredCapital","注册资本","注册资金","认缴资本"],"transform":"YUAN","targetFactKey":"registeredCapital"}', 'ACTIVE', 'system', NOW());
