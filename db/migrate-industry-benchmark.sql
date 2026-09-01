-- ============================================================
-- #4a 行业均值数据源表化（行业基准均值 t_industry_benchmark）
-- 运行方式：在 loan-service 连接的 MySQL 中执行本文件（建表 + 35 行种子）
-- 说明：benchmark_no 种子期用 SQL 侧 CONCAT('benchmark', UUID 32位) 近似；
--       运行时 CRUD 一律走 BizIdGenerator.generate("benchmark")（红线 #3）。
-- ============================================================

DROP TABLE IF EXISTS `t_industry_benchmark`;
CREATE TABLE `t_industry_benchmark` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `benchmark_no` varchar(64) NOT NULL COMMENT '基准编号(业务ID:benchmark+32位随机)',
  `industry_code` varchar(32) NOT NULL COMMENT '行业编码(GB/T4754门类,如C=制造业;DEFAULT=全行业兜底)',
  `industry_name` varchar(64) NOT NULL COMMENT '行业名称(用于自由文本归一化匹配)',
  `customer_group` varchar(16) NOT NULL DEFAULT 'ENTERPRISE' COMMENT '客群(ENTERPRISE企业/PERSONAL个人)',
  `dimension_code` varchar(32) NOT NULL COMMENT '维度编码(TAX_INTENSITY纳税强度/INVOICE_SCALE开票规模/OPERATE_YEARS经营时长/FINANCIAL_HEALTH财务健康/MATCH_OVERALL综合匹配)',
  `dimension_name` varchar(32) NOT NULL COMMENT '维度名称(与前端展示文案一致)',
  `avg_score` int NOT NULL COMMENT '行业均值(0-100归一化,诊断多维统计对比基线)',
  `p25_score` int DEFAULT NULL COMMENT '25分位(预留分层展示)',
  `p75_score` int DEFAULT NULL COMMENT '75分位(预留分层展示)',
  `sample_size` int DEFAULT NULL COMMENT '样本量(数据可信度提示)',
  `stat_period` varchar(16) NOT NULL COMMENT '统计周期(如2026H1/2025Y,同维度可多版本并存)',
  `data_source` varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '数据来源(MANUAL人工维护/INTERNAL内部样本统计/EXTERNAL第三方)',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态(ACTIVE生效/DISABLED停用)',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人姓名',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_benchmark_no` (`benchmark_no`),
  UNIQUE KEY `uk_ind_group_dim_period` (`industry_code`,`customer_group`,`dimension_code`,`stat_period`),
  KEY `idx_industry_status` (`industry_code`,`status`),
  KEY `idx_dim_status` (`dimension_code`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='行业基准均值(经营诊断多维统计对比基线;DEFAULT行兜底,替代MiniMatchService硬编码常量)';

-- ============================================================
-- 种子数据：DEFAULT×5（avg 取 45/50/55/60/55 等价现状）+ 6 主行业×5 维
-- 说明：本轮全部行业使用与 DEFAULT 一致的常量（零回归）；后续以真实样本统计值填充，
--       通过 stat_period 多版本并存，不影响历史诊断。
-- ============================================================

INSERT INTO `t_industry_benchmark`
  (`benchmark_no`,`industry_code`,`industry_name`,`customer_group`,
   `dimension_code`,`dimension_name`,`avg_score`,`stat_period`,`data_source`,`status`,`created_at`,`updated_at`)
VALUES
  -- ---------- DEFAULT / 全行业（兜底，等价于改造前硬编码） ----------
  (CONCAT('benchmark', LEFT(REPLACE(UUID(),'-',''), 32)),'DEFAULT','全行业','ENTERPRISE','TAX_INTENSITY','纳税强度',45,'2026H1','MANUAL','ACTIVE',NOW(),NOW()),
  (CONCAT('benchmark', LEFT(REPLACE(UUID(),'-',''), 32)),'DEFAULT','全行业','ENTERPRISE','INVOICE_SCALE','开票规模',50,'2026H1','MANUAL','ACTIVE',NOW(),NOW()),
  (CONCAT('benchmark', LEFT(REPLACE(UUID(),'-',''), 32)),'DEFAULT','全行业','ENTERPRISE','OPERATE_YEARS','经营时长',55,'2026H1','MANUAL','ACTIVE',NOW(),NOW()),
  (CONCAT('benchmark', LEFT(REPLACE(UUID(),'-',''), 32)),'DEFAULT','全行业','ENTERPRISE','FINANCIAL_HEALTH','财务健康',60,'2026H1','MANUAL','ACTIVE',NOW(),NOW()),
  (CONCAT('benchmark', LEFT(REPLACE(UUID(),'-',''), 32)),'DEFAULT','全行业','ENTERPRISE','MATCH_OVERALL','综合匹配',55,'2026H1','MANUAL','ACTIVE',NOW(),NOW()),

  -- ---------- C / 制造业 ----------
  (CONCAT('benchmark', LEFT(REPLACE(UUID(),'-',''), 32)),'C','制造业','ENTERPRISE','TAX_INTENSITY','纳税强度',45,'2026H1','MANUAL','ACTIVE',NOW(),NOW()),
  (CONCAT('benchmark', LEFT(REPLACE(UUID(),'-',''), 32)),'C','制造业','ENTERPRISE','INVOICE_SCALE','开票规模',50,'2026H1','MANUAL','ACTIVE',NOW(),NOW()),
  (CONCAT('benchmark', LEFT(REPLACE(UUID(),'-',''), 32)),'C','制造业','ENTERPRISE','OPERATE_YEARS','经营时长',55,'2026H1','MANUAL','ACTIVE',NOW(),NOW()),
  (CONCAT('benchmark', LEFT(REPLACE(UUID(),'-',''), 32)),'C','制造业','ENTERPRISE','FINANCIAL_HEALTH','财务健康',60,'2026H1','MANUAL','ACTIVE',NOW(),NOW()),
  (CONCAT('benchmark', LEFT(REPLACE(UUID(),'-',''), 32)),'C','制造业','ENTERPRISE','MATCH_OVERALL','综合匹配',55,'2026H1','MANUAL','ACTIVE',NOW(),NOW()),

  -- ---------- F / 批发和零售业 ----------
  (CONCAT('benchmark', LEFT(REPLACE(UUID(),'-',''), 32)),'F','批发和零售业','ENTERPRISE','TAX_INTENSITY','纳税强度',45,'2026H1','MANUAL','ACTIVE',NOW(),NOW()),
  (CONCAT('benchmark', LEFT(REPLACE(UUID(),'-',''), 32)),'F','批发和零售业','ENTERPRISE','INVOICE_SCALE','开票规模',50,'2026H1','MANUAL','ACTIVE',NOW(),NOW()),
  (CONCAT('benchmark', LEFT(REPLACE(UUID(),'-',''), 32)),'F','批发和零售业','ENTERPRISE','OPERATE_YEARS','经营时长',55,'2026H1','MANUAL','ACTIVE',NOW(),NOW()),
  (CONCAT('benchmark', LEFT(REPLACE(UUID(),'-',''), 32)),'F','批发和零售业','ENTERPRISE','FINANCIAL_HEALTH','财务健康',60,'2026H1','MANUAL','ACTIVE',NOW(),NOW()),
  (CONCAT('benchmark', LEFT(REPLACE(UUID(),'-',''), 32)),'F','批发和零售业','ENTERPRISE','MATCH_OVERALL','综合匹配',55,'2026H1','MANUAL','ACTIVE',NOW(),NOW()),

  -- ---------- E / 建筑业 ----------
  (CONCAT('benchmark', LEFT(REPLACE(UUID(),'-',''), 32)),'E','建筑业','ENTERPRISE','TAX_INTENSITY','纳税强度',45,'2026H1','MANUAL','ACTIVE',NOW(),NOW()),
  (CONCAT('benchmark', LEFT(REPLACE(UUID(),'-',''), 32)),'E','建筑业','ENTERPRISE','INVOICE_SCALE','开票规模',50,'2026H1','MANUAL','ACTIVE',NOW(),NOW()),
  (CONCAT('benchmark', LEFT(REPLACE(UUID(),'-',''), 32)),'E','建筑业','ENTERPRISE','OPERATE_YEARS','经营时长',55,'2026H1','MANUAL','ACTIVE',NOW(),NOW()),
  (CONCAT('benchmark', LEFT(REPLACE(UUID(),'-',''), 32)),'E','建筑业','ENTERPRISE','FINANCIAL_HEALTH','财务健康',60,'2026H1','MANUAL','ACTIVE',NOW(),NOW()),
  (CONCAT('benchmark', LEFT(REPLACE(UUID(),'-',''), 32)),'E','建筑业','ENTERPRISE','MATCH_OVERALL','综合匹配',55,'2026H1','MANUAL','ACTIVE',NOW(),NOW()),

  -- ---------- G / 交通运输仓储和邮政业 ----------
  (CONCAT('benchmark', LEFT(REPLACE(UUID(),'-',''), 32)),'G','交通运输仓储和邮政业','ENTERPRISE','TAX_INTENSITY','纳税强度',45,'2026H1','MANUAL','ACTIVE',NOW(),NOW()),
  (CONCAT('benchmark', LEFT(REPLACE(UUID(),'-',''), 32)),'G','交通运输仓储和邮政业','ENTERPRISE','INVOICE_SCALE','开票规模',50,'2026H1','MANUAL','ACTIVE',NOW(),NOW()),
  (CONCAT('benchmark', LEFT(REPLACE(UUID(),'-',''), 32)),'G','交通运输仓储和邮政业','ENTERPRISE','OPERATE_YEARS','经营时长',55,'2026H1','MANUAL','ACTIVE',NOW(),NOW()),
  (CONCAT('benchmark', LEFT(REPLACE(UUID(),'-',''), 32)),'G','交通运输仓储和邮政业','ENTERPRISE','FINANCIAL_HEALTH','财务健康',60,'2026H1','MANUAL','ACTIVE',NOW(),NOW()),
  (CONCAT('benchmark', LEFT(REPLACE(UUID(),'-',''), 32)),'G','交通运输仓储和邮政业','ENTERPRISE','MATCH_OVERALL','综合匹配',55,'2026H1','MANUAL','ACTIVE',NOW(),NOW()),

  -- ---------- I / 信息传输软件和信息技术服务业 ----------
  (CONCAT('benchmark', LEFT(REPLACE(UUID(),'-',''), 32)),'I','信息传输软件和信息技术服务业','ENTERPRISE','TAX_INTENSITY','纳税强度',45,'2026H1','MANUAL','ACTIVE',NOW(),NOW()),
  (CONCAT('benchmark', LEFT(REPLACE(UUID(),'-',''), 32)),'I','信息传输软件和信息技术服务业','ENTERPRISE','INVOICE_SCALE','开票规模',50,'2026H1','MANUAL','ACTIVE',NOW(),NOW()),
  (CONCAT('benchmark', LEFT(REPLACE(UUID(),'-',''), 32)),'I','信息传输软件和信息技术服务业','ENTERPRISE','OPERATE_YEARS','经营时长',55,'2026H1','MANUAL','ACTIVE',NOW(),NOW()),
  (CONCAT('benchmark', LEFT(REPLACE(UUID(),'-',''), 32)),'I','信息传输软件和信息技术服务业','ENTERPRISE','FINANCIAL_HEALTH','财务健康',60,'2026H1','MANUAL','ACTIVE',NOW(),NOW()),
  (CONCAT('benchmark', LEFT(REPLACE(UUID(),'-',''), 32)),'I','信息传输软件和信息技术服务业','ENTERPRISE','MATCH_OVERALL','综合匹配',55,'2026H1','MANUAL','ACTIVE',NOW(),NOW()),

  -- ---------- H / 住宿和餐饮业 ----------
  (CONCAT('benchmark', LEFT(REPLACE(UUID(),'-',''), 32)),'H','住宿和餐饮业','ENTERPRISE','TAX_INTENSITY','纳税强度',45,'2026H1','MANUAL','ACTIVE',NOW(),NOW()),
  (CONCAT('benchmark', LEFT(REPLACE(UUID(),'-',''), 32)),'H','住宿和餐饮业','ENTERPRISE','INVOICE_SCALE','开票规模',50,'2026H1','MANUAL','ACTIVE',NOW(),NOW()),
  (CONCAT('benchmark', LEFT(REPLACE(UUID(),'-',''), 32)),'H','住宿和餐饮业','ENTERPRISE','OPERATE_YEARS','经营时长',55,'2026H1','MANUAL','ACTIVE',NOW(),NOW()),
  (CONCAT('benchmark', LEFT(REPLACE(UUID(),'-',''), 32)),'H','住宿和餐饮业','ENTERPRISE','FINANCIAL_HEALTH','财务健康',60,'2026H1','MANUAL','ACTIVE',NOW(),NOW()),
  (CONCAT('benchmark', LEFT(REPLACE(UUID(),'-',''), 32)),'H','住宿和餐饮业','ENTERPRISE','MATCH_OVERALL','综合匹配',55,'2026H1','MANUAL','ACTIVE',NOW(),NOW());
