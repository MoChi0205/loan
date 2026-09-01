-- ============================================================
-- 菜单 / 角色权限 增量补齐（2026-09-01 · 对应 D19/D20）
-- 目的：修复「动态菜单名存实亡」——原种子（init-data.sql）仅 5 条菜单、
--       且仅 BOSS 有 t_role_permission → 非 BOSS 角色前端兜底看全量、BOSS 反见截断。
-- 本次：补全 26 条菜单 + 7 角色权限（含 CHANNEL 沙箱 3 项）。
-- 幂等：t_menu 按 uk_path upsert；t_role_permission 先清后插；t_role 按 uk_role_code upsert。
-- 适用：已有数据的库（远程 prd / 本地 docker 卷均可重复执行）。
-- 执行：mysql -uroot -p loan_db --default-character-set=utf8mb4 < db/menu-permission-seed-2026-09-01.sql
-- ============================================================
SET NAMES utf8mb4;

-- 1. 补角色（幂等）
INSERT INTO `t_role` (`role_code`, `role_name`, `description`, `created_by`) VALUES
('OPERATOR', '运营', '日常运营：审批/短信/奖励/报表全量', 'system'),
('SUPER_ADMIN', '超管', '系统管理与全部数据', 'system'),
('SUPER', '超级管理员', '最高权限（含调试中心）', 'system'),
('CHANNEL', '渠道合作方', '渠道沙箱：工作台/线索录入/我的产品', 'system')
ON DUPLICATE KEY UPDATE `role_name`=VALUES(`role_name`), `description`=VALUES(`description`);

-- 2. 菜单 upsert（按 path 唯一键 uk_path，幂等）
INSERT INTO `t_menu` (`menu_name`, `path`, `component`, `menu_type`, `sort`, `status`, `created_by`) VALUES
('工作台',      '/workbench',          'views/Workbench',                 'MENU', 1,  'ACTIVE', 'system'),
('线索公海',    '/lead',               'views/lead/LeadPool',             'MENU', 2,  'ACTIVE', 'system'),
('客户档案',    '/client',             'views/client/ClientProfile',      'MENU', 3,  'ACTIVE', 'system'),
('初筛任务',    '/screening',          'views/screening/ScreeningCenter', 'MENU', 4,  'ACTIVE', 'system'),
('服务工单',    '/order',              'views/order/OrderList',           'MENU', 5,  'ACTIVE', 'system'),
('审批中心',    '/approval',           'views/approval/ApprovalCenter',   'MENU', 6,  'ACTIVE', 'system'),
('产品库',      '/product',            'views/product/ProductList',       'MENU', 7,  'ACTIVE', 'system'),
('规则库',      '/rule-template',      'views/rule/RuleTemplateList',     'MENU', 8,  'ACTIVE', 'system'),
('规则集',      '/rule',               'views/rule/RuleList',             'MENU', 9,  'ACTIVE', 'system'),
('策略方案',    '/strategy-template',  'views/plan/StrategyTemplateList', 'MENU', 10, 'ACTIVE', 'system'),
('执行计划',    '/plan-edit',          'views/plan/PlanEdit',             'MENU', 11, 'ACTIVE', 'system'),
('渠道档案',    '/channel-config',     'views/plan/ChannelConfigList',    'MENU', 12, 'ACTIVE', 'system'),
('渠道准入',    '/channel-strategy',   'views/plan/StrategyList',         'MENU', 13, 'ACTIVE', 'system'),
('渠道名单',    '/channel-user-list',  'views/plan/ChannelUserList',      'MENU', 14, 'ACTIVE', 'system'),
('风控名单',    '/blacklist',          'views/blacklist/BlacklistCenter', 'MENU', 15, 'ACTIVE', 'system'),
('报告模板',    '/report-template',    'views/template/ReportTemplateList','MENU', 16, 'ACTIVE', 'system'),
('短信服务',    '/sms',                'views/sms/SmsCenter',             'MENU', 17, 'ACTIVE', 'system'),
('奖励发放',    '/reward',             'views/reward/RewardList',         'MENU', 18, 'ACTIVE', 'system'),
('奖励规则',    '/reward-rule',        'views/reward/RewardRuleConfig',   'MENU', 19, 'ACTIVE', 'system'),
('审计日志',    '/audit',              'views/audit/AuditCenter',         'MENU', 20, 'ACTIVE', 'system'),
('经营概览',    '/report/center',      'views/report/ReportCenter',       'MENU', 21, 'ACTIVE', 'system'),
('趋势分析',    '/report/trend',       'views/report/TrendAnalysis',      'MENU', 22, 'ACTIVE', 'system'),
('初筛报告',    '/report/screening',   'views/report/ScreeningReport',    'MENU', 23, 'ACTIVE', 'system'),
('组织权限',    '/org',                'views/org/OrgCenter',             'MENU', 24, 'ACTIVE', 'system'),
('系统配置',    '/config-wizard',      'views/config/ConfigurationWizard','MENU', 25, 'ACTIVE', 'system'),
('调试中心',    '/debug',              'views/debug/DebugCenter',         'MENU', 26, 'ACTIVE', 'system'),
('材料识别',    '/ocr',                'views/ocr/OcrCenter',              'MENU', 27, 'ACTIVE', 'system')
ON DUPLICATE KEY UPDATE `menu_name`=VALUES(`menu_name`), `component`=VALUES(`component`),
  `sort`=VALUES(`sort`), `status`='ACTIVE';

-- 3. 角色权限重建（先清后插；以 path 取 menu_id，避免自增 id 漂移）
DELETE FROM `t_role_permission`
WHERE `role_code` IN ('BOSS','DEPT_MANAGER','ADVISER','OPERATOR','SUPER_ADMIN','SUPER','CHANNEL');

-- BOSS：全量 26 项（不含 /debug——调试中心仅 SUPER/SUPER_ADMIN，D19）
INSERT INTO `t_role_permission` (`role_code`, `menu_id`, `created_by`)
SELECT 'BOSS', m.`id`, 'system' FROM `t_menu` m WHERE m.`path` IN
('/workbench','/lead','/client','/ocr','/screening','/order','/approval','/product','/rule-template','/rule','/strategy-template','/plan-edit','/channel-config','/channel-strategy','/channel-user-list','/blacklist','/report-template','/sms','/reward','/reward-rule','/audit','/report/center','/report/trend','/report/screening','/org','/config-wizard');

-- OPERATOR：同 BOSS（运营为管理视角全量，不含调试中心）
INSERT INTO `t_role_permission` (`role_code`, `menu_id`, `created_by`)
SELECT 'OPERATOR', m.`id`, 'system' FROM `t_menu` m WHERE m.`path` IN
('/workbench','/lead','/client','/ocr','/screening','/order','/approval','/product','/rule-template','/rule','/strategy-template','/plan-edit','/channel-config','/channel-strategy','/channel-user-list','/blacklist','/report-template','/sms','/reward','/reward-rule','/audit','/report/center','/report/trend','/report/screening','/org','/config-wizard');

-- SUPER_ADMIN / SUPER：全量 27 项（含调试中心）
INSERT INTO `t_role_permission` (`role_code`, `menu_id`, `created_by`)
SELECT 'SUPER_ADMIN', m.`id`, 'system' FROM `t_menu` m WHERE m.`path` IN
('/workbench','/lead','/client','/ocr','/screening','/order','/approval','/product','/rule-template','/rule','/strategy-template','/plan-edit','/channel-config','/channel-strategy','/channel-user-list','/blacklist','/report-template','/sms','/reward','/reward-rule','/audit','/report/center','/report/trend','/report/screening','/org','/config-wizard','/debug');

INSERT INTO `t_role_permission` (`role_code`, `menu_id`, `created_by`)
SELECT 'SUPER', m.`id`, 'system' FROM `t_menu` m WHERE m.`path` IN
('/workbench','/lead','/client','/ocr','/screening','/order','/approval','/product','/rule-template','/rule','/strategy-template','/plan-edit','/channel-config','/channel-strategy','/channel-user-list','/blacklist','/report-template','/sms','/reward','/reward-rule','/audit','/report/center','/report/trend','/report/screening','/org','/config-wizard','/debug');

-- DEPT_MANAGER：部门视角 20 项（不含渠道域/风控/系统管理；审批含 PRODUCT/DOWNLOAD，D0-4）
INSERT INTO `t_role_permission` (`role_code`, `menu_id`, `created_by`)
SELECT 'DEPT_MANAGER', m.`id`, 'system' FROM `t_menu` m WHERE m.`path` IN
('/workbench','/lead','/client','/ocr','/screening','/order','/approval','/product','/rule-template','/rule','/strategy-template','/plan-edit','/report-template','/sms','/reward','/reward-rule','/audit','/report/center','/report/trend','/report/screening');

-- ADVISER：本人视角 7 项（顾问非审批人，D0-4 不含 ADVISER；材料识别供顾问传客户材料）
INSERT INTO `t_role_permission` (`role_code`, `menu_id`, `created_by`)
SELECT 'ADVISER', m.`id`, 'system' FROM `t_menu` m WHERE m.`path` IN
('/workbench','/lead','/client','/ocr','/screening','/order','/report/center');

-- CHANNEL：渠道沙箱 3 项（工作台 / 线索录入 / 我的产品，D19）
INSERT INTO `t_role_permission` (`role_code`, `menu_id`, `created_by`)
SELECT 'CHANNEL', m.`id`, 'system' FROM `t_menu` m WHERE m.`path` IN
('/workbench','/lead','/product');

-- ============================================================
-- 4. 补充示例员工（覆盖 7 角色中 5 个管理角色，便于逐角色验收菜单）
--    dept_code 为业务编码（与 t_department.dept_code 关联：BOSS_DIRECT 老板直属 / CONSULT 咨询部 / OPERATION 运营部）。
--    2026-09-01 修正：用户实际库 t_staff 为 dept_code 列（旧 schema.sql 曾写 dept_id，已对齐，见 D24）。
--    ON DUPLICATE 同时更新 role_code/dept_code，保证重复执行可补全部门。
-- ============================================================
INSERT INTO `t_staff` (`staff_code`, `crm_user_id`, `staff_name`, `dept_code`, `role_code`, `phone`, `phone_hash`, `created_by`) VALUES
('DEPT001', 'crm-dept-001', '王经理', 'CONSULT', 'DEPT_MANAGER', '13800000003', SHA2('13800000003', 256), 'system'),
('OP001', 'crm-op-001', '赵运营', 'OPERATION', 'OPERATOR', '13800000004', SHA2('13800000004', 256), 'system'),
('SUP001', 'crm-sup-001', '孙超管', 'BOSS_DIRECT', 'SUPER_ADMIN', '13800000005', SHA2('13800000005', 256), 'system'),
('SUPER001', 'crm-super-001', '钱超级', 'BOSS_DIRECT', 'SUPER', '13800000006', SHA2('13800000006', 256), 'system')
ON DUPLICATE KEY UPDATE `staff_name`=VALUES(`staff_name`), `role_code`=VALUES(`role_code`), `dept_code`=VALUES(`dept_code`);

-- ============================================================
-- 5. 渠道示例账号（T11/D21 渠道 Web 沙箱验收用）
--    - phone 阶段一明文（与 t_staff 一致，AesUtils.decrypt 失败返回 null，登录/显示用 name，无碍）；
--      待 AES 密钥统一接入后迁移为密文。
--    - password 为 BCrypt（登录走 /api/auth/channel-login，阶段一模拟串 loan-sim-pwd 旁路 RSA+BCrypt）。
--    - 登录手机号：13911112222；bank_channel_id=1（武汉某商业银行）。
--    ⚠️ 不写 job_title 列（可空、版本敏感，部分库无此列，避免 1054）。
-- ============================================================
INSERT INTO `t_channel_user` (`bank_channel_id`, `phone`, `phone_hash`, `password`, `name`, `created_by`) VALUES
(1, '13911112222', SHA2('13911112222', 256), '$2y$10$kUwWbSsyGWEvjLGpd2lnPeRLxgaXWrhs59uaaEVVOjSyJ8UwmKY5u', '渠道-陈', 'system')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`);
