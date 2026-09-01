-- ============================================================
-- loan_db 初始化数据（阶段一最小闭环）
-- 生成时间: 2026-08-25
-- 依据: 方案评审定稿纪要 + 65 表 DDL
-- 执行: mysql -uroot -p loan_db --default-character-set=utf8mb4 < db/init-data.sql
-- 说明: 手机号等敏感列阶段一先落明文，待 AES TypeHandler 接入后统一加密迁移
-- ============================================================
SET NAMES utf8mb4;

-- ============================================================
-- 一、组织权限域：角色 / 部门 / 员工 / 菜单 / 角色权限
-- ============================================================

-- 三级角色 + 运营/超管/超级/渠道（2026-09-01 补全，见 D19）
INSERT INTO `t_role` (`role_code`, `role_name`, `description`, `created_by`) VALUES
('BOSS', '老板', '跨部门全量数据 + 全部审批', 'system'),
('DEPT_MANAGER', '部门主管', '本部门全量数据 + 本部门审核', 'system'),
('ADVISER', '顾问', '本人数据 + 新增客户 + 公海认领 + 黑名单查询新增', 'system'),
('OPERATOR', '运营', '日常运营：审批/短信/奖励/报表全量', 'system'),
('SUPER_ADMIN', '超管', '系统管理与全部数据', 'system'),
('SUPER', '超级管理员', '最高权限（含调试中心）', 'system'),
('CHANNEL', '渠道合作方', '渠道沙箱：工作台/线索录入/我的产品', 'system');

-- 示例部门（通用部门树）
INSERT INTO `t_department` (`dept_code`, `dept_name`, `parent_id`, `sort`, `created_by`) VALUES
('BOSS_DIRECT', '老板直属', NULL, 1, 'system'),
('CONSULT', '咨询部', NULL, 2, 'system'),
('MARKET', '市场部', NULL, 3, 'system'),
('OPERATION', '运营部', NULL, 4, 'system');

-- 示例员工（crm_user_id 为 SSO 映射键；phone 阶段一明文，后续 AES 加密；2026-09-01 补 4 角色示例）
INSERT INTO `t_staff` (`staff_code`, `crm_user_id`, `staff_name`, `dept_code`, `role_code`, `phone`, `phone_hash`, `created_by`) VALUES
('BOSS001', 'crm-boss-001', '张老板', 'BOSS_DIRECT', 'BOSS', '13800000001', SHA2('13800000001', 256), 'system'),
('ADV001', 'crm-adv-001', '李顾问', 'CONSULT', 'ADVISER', '13800000002', SHA2('13800000002', 256), 'system'),
('DEPT001', 'crm-dept-001', '王经理', 'CONSULT', 'DEPT_MANAGER', '13800000003', SHA2('13800000003', 256), 'system'),
('OP001', 'crm-op-001', '赵运营', 'OPERATION', 'OPERATOR', '13800000004', SHA2('13800000004', 256), 'system'),
('SUP001', 'crm-sup-001', '孙超管', 'BOSS_DIRECT', 'SUPER_ADMIN', '13800000005', SHA2('13800000005', 256), 'system'),
('SUPER001', 'crm-super-001', '钱超级', 'BOSS_DIRECT', 'SUPER', '13800000006', SHA2('13800000006', 256), 'system');

-- 渠道示例账号（T11/D21 渠道 Web 沙箱验收；phone 阶段一明文，待 AES 接入后迁移；登录手机号 13911112222，阶段一模拟串 loan-sim-pwd 旁路 RSA+BCrypt）
INSERT INTO `t_channel_user` (`bank_channel_id`, `phone`, `phone_hash`, `password`, `name`, `job_title`, `created_by`) VALUES
(1, '13911112222', SHA2('13911112222', 256), '$2y$10$kUwWbSsyGWEvjLGpd2lnPeRLxgaXWrhs59uaaEVVOjSyJ8UwmKY5u', '渠道-陈', '渠道经理', 'system');

-- 菜单树（2026-09-01 补全 27 项，与 loan-web BASE_MENU_GROUPS 路径对齐；显式 id 供角色权限引用）
INSERT INTO `t_menu` (`id`, `parent_id`, `menu_name`, `path`, `component`, `menu_type`, `permission_code`, `sort`, `status`, `created_by`) VALUES
(1,  NULL, '工作台',     '/workbench',          'views/Workbench',                 'MENU', NULL,           1,  'ACTIVE', 'system'),
(2,  NULL, '线索公海',   '/lead',               'views/lead/LeadPool',             'MENU', NULL,           2,  'ACTIVE', 'system'),
(3,  NULL, '客户档案',   '/client',             'views/client/ClientProfile',      'MENU', 'page:client',  3,  'ACTIVE', 'system'),
(4,  NULL, '初筛任务',   '/screening',          'views/screening/ScreeningCenter', 'MENU', NULL,           4,  'ACTIVE', 'system'),
(5,  NULL, '服务工单',   '/order',              'views/order/OrderList',           'MENU', NULL,           5,  'ACTIVE', 'system'),
(6,  NULL, '审批中心',   '/approval',           'views/approval/ApprovalCenter',   'MENU', NULL,           6,  'ACTIVE', 'system'),
(7,  NULL, '产品库',     '/product',            'views/product/ProductList',       'MENU', NULL,           7,  'ACTIVE', 'system'),
(8,  NULL, '规则库',     '/rule-template',      'views/rule/RuleTemplateList',     'MENU', NULL,           8,  'ACTIVE', 'system'),
(9,  NULL, '规则集',     '/rule',               'views/rule/RuleList',             'MENU', NULL,           9,  'ACTIVE', 'system'),
(10, NULL, '策略方案',   '/strategy-template',  'views/plan/StrategyTemplateList', 'MENU', NULL,           10, 'ACTIVE', 'system'),
(11, NULL, '执行计划',   '/plan-edit',          'views/plan/PlanEdit',             'MENU', NULL,           11, 'ACTIVE', 'system'),
(12, NULL, '渠道档案',   '/channel-config',     'views/plan/ChannelConfigList',    'MENU', NULL,           12, 'ACTIVE', 'system'),
(13, NULL, '渠道准入',   '/channel-strategy',   'views/plan/StrategyList',         'MENU', NULL,           13, 'ACTIVE', 'system'),
(14, NULL, '渠道名单',   '/channel-user-list',  'views/plan/ChannelUserList',      'MENU', NULL,           14, 'ACTIVE', 'system'),
(15, NULL, '风控名单',   '/blacklist',          'views/blacklist/BlacklistCenter', 'MENU', 'page:blacklist',15, 'ACTIVE', 'system'),
(16, NULL, '报告模板',   '/report-template',    'views/template/ReportTemplateList','MENU', NULL,          16, 'ACTIVE', 'system'),
(17, NULL, '短信服务',   '/sms',                'views/sms/SmsCenter',             'MENU', NULL,           17, 'ACTIVE', 'system'),
(18, NULL, '奖励发放',   '/reward',             'views/reward/RewardList',         'MENU', NULL,           18, 'ACTIVE', 'system'),
(19, NULL, '奖励规则',   '/reward-rule',        'views/reward/RewardRuleConfig',   'MENU', NULL,           19, 'ACTIVE', 'system'),
(20, NULL, '审计日志',   '/audit',              'views/audit/AuditCenter',         'MENU', 'page:audit',  20, 'ACTIVE', 'system'),
(21, NULL, '经营概览',   '/report/center',      'views/report/ReportCenter',       'MENU', NULL,           21, 'ACTIVE', 'system'),
(22, NULL, '趋势分析',   '/report/trend',       'views/report/TrendAnalysis',      'MENU', NULL,           22, 'ACTIVE', 'system'),
(23, NULL, '初筛报告',   '/report/screening',   'views/report/ScreeningReport',    'MENU', NULL,           23, 'ACTIVE', 'system'),
(24, NULL, '组织权限',   '/org',                'views/org/OrgCenter',             'MENU', 'page:org',    24, 'ACTIVE', 'system'),
(25, NULL, '系统配置',   '/config-wizard',      'views/config/ConfigurationWizard','MENU', NULL,          25, 'ACTIVE', 'system'),
(26, NULL, '调试中心',   '/debug',              'views/debug/DebugCenter',         'MENU', NULL,           26, 'ACTIVE', 'system'),
(27, NULL, '材料识别',   '/ocr',                'views/ocr/OcrCenter',              'MENU', NULL,           27, 'ACTIVE', 'system');

-- 角色权限（2026-09-01 补全 7 角色；调试中心仅 SUPER/SUPER_ADMIN；DEPT_MANAGER 部门视角；ADVISER 本人视角；CHANNEL 沙箱 3 项）
INSERT INTO `t_role_permission` (`role_code`, `menu_id`, `created_by`) VALUES
-- BOSS：全量 26（不含 /debug）
('BOSS', 1, 'system'), ('BOSS', 2, 'system'), ('BOSS', 3, 'system'), ('BOSS', 4, 'system'),
('BOSS', 5, 'system'), ('BOSS', 6, 'system'), ('BOSS', 7, 'system'), ('BOSS', 8, 'system'),
('BOSS', 9, 'system'), ('BOSS', 10, 'system'), ('BOSS', 11, 'system'), ('BOSS', 12, 'system'),
('BOSS', 13, 'system'), ('BOSS', 14, 'system'), ('BOSS', 15, 'system'), ('BOSS', 16, 'system'),
('BOSS', 17, 'system'), ('BOSS', 18, 'system'), ('BOSS', 19, 'system'), ('BOSS', 20, 'system'),
('BOSS', 21, 'system'), ('BOSS', 22, 'system'), ('BOSS', 23, 'system'), ('BOSS', 24, 'system'),
('BOSS', 25, 'system'), ('BOSS', 27, 'system'),
-- OPERATOR：同 BOSS（26 项）
('OPERATOR', 1, 'system'), ('OPERATOR', 2, 'system'), ('OPERATOR', 3, 'system'), ('OPERATOR', 4, 'system'),
('OPERATOR', 5, 'system'), ('OPERATOR', 6, 'system'), ('OPERATOR', 7, 'system'), ('OPERATOR', 8, 'system'),
('OPERATOR', 9, 'system'), ('OPERATOR', 10, 'system'), ('OPERATOR', 11, 'system'), ('OPERATOR', 12, 'system'),
('OPERATOR', 13, 'system'), ('OPERATOR', 14, 'system'), ('OPERATOR', 15, 'system'), ('OPERATOR', 16, 'system'),
('OPERATOR', 17, 'system'), ('OPERATOR', 18, 'system'), ('OPERATOR', 19, 'system'), ('OPERATOR', 20, 'system'),
('OPERATOR', 21, 'system'), ('OPERATOR', 22, 'system'), ('OPERATOR', 23, 'system'), ('OPERATOR', 24, 'system'),
('OPERATOR', 25, 'system'), ('OPERATOR', 27, 'system'),
-- SUPER_ADMIN：全量 27（含调试中心）
('SUPER_ADMIN', 1, 'system'), ('SUPER_ADMIN', 2, 'system'), ('SUPER_ADMIN', 3, 'system'), ('SUPER_ADMIN', 4, 'system'),
('SUPER_ADMIN', 5, 'system'), ('SUPER_ADMIN', 6, 'system'), ('SUPER_ADMIN', 7, 'system'), ('SUPER_ADMIN', 8, 'system'),
('SUPER_ADMIN', 9, 'system'), ('SUPER_ADMIN', 10, 'system'), ('SUPER_ADMIN', 11, 'system'), ('SUPER_ADMIN', 12, 'system'),
('SUPER_ADMIN', 13, 'system'), ('SUPER_ADMIN', 14, 'system'), ('SUPER_ADMIN', 15, 'system'), ('SUPER_ADMIN', 16, 'system'),
('SUPER_ADMIN', 17, 'system'), ('SUPER_ADMIN', 18, 'system'), ('SUPER_ADMIN', 19, 'system'), ('SUPER_ADMIN', 20, 'system'),
('SUPER_ADMIN', 21, 'system'), ('SUPER_ADMIN', 22, 'system'), ('SUPER_ADMIN', 23, 'system'), ('SUPER_ADMIN', 24, 'system'),
('SUPER_ADMIN', 25, 'system'), ('SUPER_ADMIN', 26, 'system'), ('SUPER_ADMIN', 27, 'system'),
-- SUPER：全量 27
('SUPER', 1, 'system'), ('SUPER', 2, 'system'), ('SUPER', 3, 'system'), ('SUPER', 4, 'system'),
('SUPER', 5, 'system'), ('SUPER', 6, 'system'), ('SUPER', 7, 'system'), ('SUPER', 8, 'system'),
('SUPER', 9, 'system'), ('SUPER', 10, 'system'), ('SUPER', 11, 'system'), ('SUPER', 12, 'system'),
('SUPER', 13, 'system'), ('SUPER', 14, 'system'), ('SUPER', 15, 'system'), ('SUPER', 16, 'system'),
('SUPER', 17, 'system'), ('SUPER', 18, 'system'), ('SUPER', 19, 'system'), ('SUPER', 20, 'system'),
('SUPER', 21, 'system'), ('SUPER', 22, 'system'), ('SUPER', 23, 'system'), ('SUPER', 24, 'system'),
('SUPER', 25, 'system'), ('SUPER', 26, 'system'), ('SUPER', 27, 'system'),
-- DEPT_MANAGER：部门视角 20 项（不含渠道域/风控/系统管理）
('DEPT_MANAGER', 1, 'system'), ('DEPT_MANAGER', 2, 'system'), ('DEPT_MANAGER', 3, 'system'), ('DEPT_MANAGER', 4, 'system'),
('DEPT_MANAGER', 5, 'system'), ('DEPT_MANAGER', 6, 'system'), ('DEPT_MANAGER', 7, 'system'), ('DEPT_MANAGER', 8, 'system'),
('DEPT_MANAGER', 9, 'system'), ('DEPT_MANAGER', 10, 'system'), ('DEPT_MANAGER', 11, 'system'), ('DEPT_MANAGER', 16, 'system'),
('DEPT_MANAGER', 17, 'system'), ('DEPT_MANAGER', 18, 'system'), ('DEPT_MANAGER', 19, 'system'), ('DEPT_MANAGER', 20, 'system'),
('DEPT_MANAGER', 21, 'system'), ('DEPT_MANAGER', 22, 'system'), ('DEPT_MANAGER', 23, 'system'), ('DEPT_MANAGER', 27, 'system'),
-- ADVISER：本人视角 7 项（非审批人；材料识别供顾问传客户材料）
('ADVISER', 1, 'system'), ('ADVISER', 2, 'system'), ('ADVISER', 3, 'system'), ('ADVISER', 4, 'system'),
('ADVISER', 5, 'system'), ('ADVISER', 21, 'system'), ('ADVISER', 27, 'system'),
-- CHANNEL：渠道沙箱 3 项
('CHANNEL', 1, 'system'), ('CHANNEL', 2, 'system'), ('CHANNEL', 7, 'system');

-- ============================================================
-- 二、渠道产品域：银行渠道 / 产品 / 合作库上架
-- ============================================================

INSERT INTO `t_bank_channel` (`channel_code`, `bank_name`, `created_by`) VALUES
('WH_BANK', '武汉某商业银行', 'system'),
('HB_BANK', '湖北某农商行', 'system');

-- 产品（客户群 ENTERPRISE，来源 OURS 我司录入；产品名仅管理端可见）
INSERT INTO `t_bank_product`
(`product_code`, `bank_channel_id`, `product_name`, `customer_group`, `source`,
 `amount_min`, `amount_max`, `rate_min`, `rate_max`, `term_min`, `term_max`,
 `tax_threshold`, `invoice_require`, `status`, `created_by`) VALUES
('WH_TAX_LOAN_A', 1, '武汉某行·企业税贷 A', 'ENTERPRISE', 'OURS',
 500000.00, 3000000.00, 0.0350, 0.0650, 12, 36,
 30000.00, '近 12 个月连续开票', 'APPROVED', 'system'),
('HB_TAX_LOAN_B', 2, '湖北某行·企业税贷 B', 'ENTERPRISE', 'OURS',
 300000.00, 1000000.00, 0.0400, 0.0700, 6, 24,
 20000.00, '近 6 个月连续开票', 'APPROVED', 'system');

-- 合作库上架（对客可见权在我司，设有效期）
INSERT INTO `t_partner_product` (`bank_product_id`, `cooperate_until`, `created_by`) VALUES
(1, '2027-08-25 00:00:00', 'system'),
(2, '2027-08-25 00:00:00', 'system');

-- ============================================================
-- 三、规则目录域：四分类 / 企业规则 / 规则版本
-- ============================================================

INSERT INTO `t_rule_category` (`category_code`, `parent_id`, `category_name`, `customer_group`, `sort`, `created_by`) VALUES
('RISK', NULL, '基础风控', 'COMMON', 1, 'system'),
('OPERATION', NULL, '经营能力', 'COMMON', 2, 'system'),
('QUALIFICATION', NULL, '资质准入', 'COMMON', 3, 'system'),
('PERSONAL', NULL, '个人基础', 'COMMON', 4, 'system');

-- 企业 16 条规则（API 型为专用 Handler；通用型为表达式后台可配）
INSERT INTO `t_rule`
(`rule_code`, `rule_name`, `field_code`, `field_name`, `operator`, `value_type`, `value_text`, `customer_group`, `description`, `status`, `created_by`) VALUES
('blacklist_reject', '黑名单·拒绝', 'blacklist', '黑名单', '==', 'API', NULL, 'ENTERPRISE', '命中本地黑名单直接拒绝', 'ONLINE', 'system'),
('dishonest_reject', '失信名单·拒绝', 'dishonest', '失信名单', '==', 'API', NULL, 'ENTERPRISE', '命中失信被执行人名单拒绝', 'ONLINE', 'system'),
('fraud_reject', '欺诈核验·拒绝', 'fraud', '欺诈核验', '==', 'API', NULL, 'ENTERPRISE', '欺诈风险核验命中拒绝', 'ONLINE', 'system'),
('region_allow', '区域限制', 'region', '区域', 'in', 'LIST', '武汉,湖北', 'ENTERPRISE', '企业注册地须在允许区域', 'ONLINE', 'system'),
('industry_forbid', '敏感行业禁入', 'industry', '行业', 'not_in', 'LIST', '房地产,娱乐,两高一剩', 'ENTERPRISE', '行业不在敏感禁入列表', 'ONLINE', 'system'),
('lawsuit_reject', '司法诉讼·拒绝', 'lawsuit', '司法诉讼', '==', 'API', NULL, 'ENTERPRISE', '存在未结诉讼/被执行拒绝', 'ONLINE', 'system'),
('establish_years_min', '成立年限', 'establish_years', '成立年限', '>=', 'NUMBER', '2', 'ENTERPRISE', '成立年限下限 2 年', 'ONLINE', 'system'),
('annual_tax_min', '年纳税额', 'annual_tax', '年纳税额', '>=', 'NUMBER', '30000', 'ENTERPRISE', '年纳税额下限 3 万', 'ONLINE', 'system'),
('annual_invoice_min', '年开票额', 'annual_invoice', '年开票额', '>=', 'NUMBER', '500000', 'ENTERPRISE', '年开票额下限 50 万', 'ONLINE', 'system'),
('tax_grade_in', '纳税等级', 'tax_grade', '纳税等级', 'in', 'LIST', 'A,B', 'ENTERPRISE', '纳税信用等级 A/B', 'ONLINE', 'system'),
('debt_ratio_max', '资产负债率', 'debt_ratio', '资产负债率', '<=', 'NUMBER', '70', 'ENTERPRISE', '资产负债率上限 70%', 'ONLINE', 'system'),
('invoice_continuity_min', '开票连续性', 'invoice_continuity', '开票连续性', '>=', 'NUMBER', '6', 'ENTERPRISE', '连续开票 6 个月', 'ONLINE', 'system'),
('tax_continuity_min', '纳税连续性', 'tax_continuity', '纳税连续性', '>=', 'NUMBER', '6', 'ENTERPRISE', '连续纳税 6 个月', 'ONLINE', 'system'),
('biz_status_in', '经营状态', 'biz_status', '经营状态', 'in', 'LIST', '存续,在营', 'ENTERPRISE', '经营状态存续/在营', 'ONLINE', 'system'),
('registered_capital_min', '注册资本', 'registered_capital', '注册资本', '>=', 'NUMBER', '500000', 'ENTERPRISE', '注册资本下限 50 万', 'ONLINE', 'system'),
('social_count_min', '社保人数', 'social_security_count', '社保人数', '>=', 'NUMBER', '5', 'ENTERPRISE', '社保人数下限 5 人', 'ONLINE', 'system');

-- 规则版本（与规则 1:1，version_no=1，ONLINE）
INSERT INTO `t_rule_version` (`rule_id`, `version_no`, `field_code`, `operator`, `value_text`, `status`, `published_at`, `published_by`, `created_by`)
SELECT `id`, 1, `field_code`, `operator`, `value_text`, 'ONLINE', NOW(), 'system', 'system' FROM `t_rule`;

-- ============================================================
-- 四、执行计划域：计划 / 模块 / 步骤 / 产品准入配置
-- ============================================================

-- 计划（企业税贷通用准入计划，status=1 上线）
INSERT INTO `t_admission_execution_plan` (`plan_code`, `plan_name`, `customer_group`, `version`, `status`, `created_by`) VALUES
('ENT_TAX_PLAN_V1', '企业税贷通用准入计划', 'ENTERPRISE', 1, '1', 'system');

-- 模块（全局前置风控 + 经营能力 + 资质准入，AND）
INSERT INTO `t_admission_plan_module` (`plan_id`, `module_code`, `module_name`, `logic_type`, `is_global_pre`, `sort`, `created_by`) VALUES
(1, 'GLOBAL_RISK', '全局前置风控', 'AND', 1, 1, 'system'),
(1, 'OPERATION', '经营能力', 'AND', 0, 2, 'system'),
(1, 'QUALIFICATION', '资质准入', 'AND', 0, 3, 'system');

-- 步骤（步骤 = 单条规则；rule_id/rule_version_id 关联规则）
-- 模块 1 全局前置风控：黑名单/失信/欺诈/区域/行业/诉讼
INSERT INTO `t_admission_plan_step` (`module_id`, `rule_id`, `rule_version_id`, `step_sort`, `created_by`)
SELECT 1, r.`id`, rv.`id`, s.`sort`, 'system'
FROM (SELECT 'blacklist_reject' AS rc, 1 AS `sort` UNION ALL SELECT 'dishonest_reject', 2 UNION ALL
      SELECT 'fraud_reject', 3 UNION ALL SELECT 'region_allow', 4 UNION ALL
      SELECT 'industry_forbid', 5 UNION ALL SELECT 'lawsuit_reject', 6) s
JOIN `t_rule` r ON r.`rule_code` = s.rc
JOIN `t_rule_version` rv ON rv.`rule_id` = r.`id` AND rv.`version_no` = 1;

-- 模块 2 经营能力
INSERT INTO `t_admission_plan_step` (`module_id`, `rule_id`, `rule_version_id`, `step_sort`, `created_by`)
SELECT 2, r.`id`, rv.`id`, s.`sort`, 'system'
FROM (SELECT 'establish_years_min' AS rc, 1 AS `sort` UNION ALL SELECT 'annual_tax_min', 2 UNION ALL
      SELECT 'annual_invoice_min', 3 UNION ALL SELECT 'tax_grade_in', 4 UNION ALL
      SELECT 'debt_ratio_max', 5 UNION ALL SELECT 'invoice_continuity_min', 6 UNION ALL
      SELECT 'tax_continuity_min', 7) s
JOIN `t_rule` r ON r.`rule_code` = s.rc
JOIN `t_rule_version` rv ON rv.`rule_id` = r.`id` AND rv.`version_no` = 1;

-- 模块 3 资质准入
INSERT INTO `t_admission_plan_step` (`module_id`, `rule_id`, `rule_version_id`, `step_sort`, `created_by`)
SELECT 3, r.`id`, rv.`id`, s.`sort`, 'system'
FROM (SELECT 'biz_status_in' AS rc, 1 AS `sort` UNION ALL SELECT 'registered_capital_min', 2 UNION ALL
      SELECT 'social_count_min', 3) s
JOIN `t_rule` r ON r.`rule_code` = s.rc
JOIN `t_rule_version` rv ON rv.`rule_id` = r.`id` AND rv.`version_no` = 1;

-- 产品准入配置（产品 ↔ 计划绑定）
INSERT INTO `t_product_admission_config` (`bank_product_id`, `execution_plan_id`, `priority`, `created_by`) VALUES
(1, 1, 1, 'system'),
(2, 1, 1, 'system');

-- ============================================================
-- 五、报告模板（档位映射 / 免责声明 / 多维建议文案库）
-- ============================================================

INSERT INTO `t_report_template`
(`template_code`, `version_no`, `template_name`, `grade_rule_json`, `disclaimer_text`, `status`, `published_at`, `published_by`, `created_by`) VALUES
('ENT_REPORT', 1, '企业咨询报告模板 v1',
 '{"high":3,"mid":1}',
 '本报告为企业经营状况与银行产品准入条件的匹配程度分析及预计概率，不构成任何银行通过承诺，具体以银行实际审批为准。',
 'ACTIVE', NOW(), 'system', 'system');

-- ============================================================
-- 六、黑名单（示例数据：演示全局前置风控命中 REJECT）
-- ============================================================

INSERT INTO `t_blacklist` (`dimension`, `value`, `value_hash`, `reason_type`, `reason_remark`, `created_by`, `status`) VALUES
('PHONE', '13900000000', SHA2('13900000000', 256), 'FRAUD', '示例欺诈号码', 'system', 'EFFECTIVE');
