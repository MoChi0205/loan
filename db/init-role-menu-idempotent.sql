-- loan 角色/菜单幂等初始化（可对云数据库重复执行）
-- 仅维护角色、菜单、角色菜单授权；不删除客户及其他业务数据。
SET NAMES utf8mb4;

INSERT INTO t_role (role_code,role_name,description,status,created_by) VALUES
 ('BOSS','老板','全公司业务数据与审批','ACTIVE','system'),
 ('DEPT_MANAGER','部门经理','本团队数据与团队审批','ACTIVE','system'),
 ('ADVISER','顾问','本人客户、线索与服务事项','ACTIVE','system'),
 ('OPERATOR','运营','运营管理、审批与全公司业务数据','ACTIVE','system'),
 ('SUPER_ADMIN','超管','系统管理与全公司业务数据','ACTIVE','system'),
 ('SUPER','超级管理员','全量系统权限','ACTIVE','system'),
 ('CHANNEL','渠道合作方','渠道本人线索与本人客户只读范围','ACTIVE','system')
ON DUPLICATE KEY UPDATE role_name=VALUES(role_name),description=VALUES(description),status='ACTIVE',updated_by='system';

INSERT INTO t_menu (menu_name,path,component,menu_type,permission_code,sort,status,created_by) VALUES
 ('客户中心','/domain/client',NULL,'MENU','domain:client',100,'ACTIVE','system'),
 ('智能匹配','/domain/matching',NULL,'MENU','domain:matching',200,'ACTIVE','system'),
 ('服务交付','/domain/service',NULL,'MENU','domain:service',300,'ACTIVE','system'),
 ('审批中心','/domain/approval',NULL,'MENU','domain:approval',350,'ACTIVE','system'),
 ('营销激励','/domain/operation',NULL,'MENU','domain:operation',400,'ACTIVE','system'),
 ('经营分析','/domain/report',NULL,'MENU','domain:report',500,'ACTIVE','system'),
 ('产品与规则','/domain/product',NULL,'MENU','domain:product',600,'ACTIVE','system'),
 ('系统管理','/domain/system',NULL,'MENU','domain:system',700,'ACTIVE','system')
ON DUPLICATE KEY UPDATE menu_name=VALUES(menu_name),component=NULL,menu_type='MENU',permission_code=VALUES(permission_code),sort=VALUES(sort),status='ACTIVE';

INSERT INTO t_menu (menu_name,path,component,menu_type,permission_code,sort,status,created_by) VALUES
 ('工作台','/workbench','views/Workbench','MENU',NULL,1,'ACTIVE','system'),
 ('我的线索','/lead/my','views/lead/LeadPool','MENU','page:lead',111,'ACTIVE','system'),
 ('线索公海','/lead/pool','views/lead/LeadPool','MENU','page:lead',112,'ACTIVE','system'),
 ('我的客户','/client/my','views/client/ClientProfile','MENU','page:client',121,'ACTIVE','system'),
 ('团队客户','/client/team','views/client/ClientProfile','MENU','page:client',122,'ACTIVE','system'),
 ('全司客户','/client/company','views/client/ClientProfile','MENU','page:client',123,'ACTIVE','system'),
 ('客户公海','/client/company-sea','views/client/ClientProfile','MENU','page:client',124,'ACTIVE','system'),
 ('团队公海','/client/team-sea','views/client/ClientProfile','MENU','page:client',125,'ACTIVE','system'),
 ('客户导入进度','/client/import-tasks','views/client/ImportTasks','MENU','page:client',126,'ACTIVE','system'),
 ('资源池回收规则','/client/resource-pool-config','views/client/ResourcePoolConfig','MENU','page:client',127,'ACTIVE','system'),
 ('客户导入与导出','/client/import-export','views/client/ClientImportExport','MENU','page:client',128,'ACTIVE','system'),
 ('客户检索','/client-lookup','views/client/ClientLookup','MENU','page:client',129,'ACTIVE','system'),
 ('客户材料','/ocr','views/ocr/OcrCenter','MENU',NULL,130,'ACTIVE','system'),
 ('匹配任务','/screening','views/screening/ScreeningCenter','MENU',NULL,211,'ACTIVE','system'),
 ('诊断报告','/report/screening','views/report/ScreeningReport','MENU',NULL,212,'ACTIVE','system'),
 ('服务总览','/service-operations/daily','views/service/ServiceOperations','MENU','page:service-daily',311,'ACTIVE','system'),
 ('客户预约','/service-operations/appointments','views/service/ServiceOperations','MENU','page:service-appointments',312,'ACTIVE','system'),
 ('员工外出','/service-operations/outings','views/service/ServiceOperations','MENU','page:service-outings',313,'ACTIVE','system'),
 ('客户服务记录','/service-operations/replay','views/service/ServiceOperations','MENU','page:service-replay',314,'ACTIVE','system'),
 ('服务工单','/order','views/order/OrderList','MENU',NULL,320,'ACTIVE','system'),
 ('我的申请','/approval/mine','views/approval/ApprovalCenter','MENU','page:approval-mine',351,'ACTIVE','system'),
 ('产品审核','/approval/product','views/approval/ApprovalCenter','MENU','page:approval-product',352,'ACTIVE','system'),
 ('附件下载审核','/approval/download','views/approval/ApprovalCenter','MENU','page:approval-download',353,'ACTIVE','system'),
 ('客户分配审核','/approval/allocation','views/approval/ApprovalCenter','MENU','page:approval-allocation',354,'ACTIVE','system'),
 ('外出审批','/approval/outing','views/approval/ApprovalCenter','MENU','page:approval-outing',355,'ACTIVE','system'),
 ('手机号查看审批','/approval/sensitive-phone','views/approval/ApprovalCenter','MENU','page:approval-sensitive-phone',356,'ACTIVE','system'),
 ('渠道线索审核','/approval/channel-lead','views/approval/ApprovalCenter','MENU','page:approval-channel-lead',357,'ACTIVE','system'),
 ('短信模板审核','/approval/sms-template','views/approval/ApprovalCenter','MENU','page:approval-sms-template',358,'ACTIVE','system'),
 ('报告模板审核','/approval/report-template','views/approval/ApprovalCenter','MENU','page:approval-report-template',359,'ACTIVE','system'),
 ('短信模板','/sms/templates','views/sms/SmsCenter','MENU','page:sms',411,'ACTIVE','system'),
 ('发送记录','/sms/records','views/sms/SmsCenter','MENU','page:sms',412,'ACTIVE','system'),
 ('奖励发放','/reward/records','views/reward/RewardList','MENU','page:reward',421,'ACTIVE','system'),
 ('奖励规则','/reward/rules','views/reward/RewardRuleConfig','MENU','page:reward-rule',422,'ACTIVE','system'),
 ('经营看板','/report/center','views/report/ReportCenter','MENU',NULL,511,'ACTIVE','system'),
 ('趋势分析','/report/trend','views/report/TrendAnalysis','MENU',NULL,512,'ACTIVE','system'),
 ('报告模板','/report-template','views/template/ReportTemplateList','MENU',NULL,513,'ACTIVE','system'),
 ('全量产品库','/product/all','views/product/ProductList','MENU','page:product',611,'ACTIVE','system'),
 ('合作产品库','/product/cooperate','views/product/ProductList','MENU','page:product',612,'ACTIVE','system'),
 ('渠道档案','/channel-config','views/plan/ChannelConfigList','MENU',NULL,621,'ACTIVE','system'),
 ('渠道准入','/channel-strategy','views/plan/StrategyList','MENU',NULL,622,'ACTIVE','system'),
 ('渠道名单','/channel-user-list','views/plan/ChannelUserList','MENU',NULL,623,'ACTIVE','system'),
 ('规则模板','/rule-template','views/rule/RuleTemplateList','MENU',NULL,631,'ACTIVE','system'),
 ('规则实例','/rule','views/rule/RuleList','MENU',NULL,632,'ACTIVE','system'),
 ('策略方案','/strategy-template','views/plan/StrategyTemplateList','MENU',NULL,633,'ACTIVE','system'),
 ('执行计划','/plan-edit','views/plan/PlanEdit','MENU',NULL,634,'ACTIVE','system'),
 ('风控名单','/blacklist','views/blacklist/BlacklistCenter','MENU','page:blacklist',711,'ACTIVE','system'),
 ('审计日志','/audit','views/audit/AuditCenter','MENU','page:audit',712,'ACTIVE','system'),
 ('员工管理','/org/staff','views/org/OrgCenter','MENU','page:org',721,'ACTIVE','system'),
 ('角色权限','/org/roles','views/org/OrgCenter','MENU','page:org',722,'ACTIVE','system'),
 ('接口权限','/org/api-permissions','views/org/OrgCenter','MENU','page:org',723,'ACTIVE','system'),
 ('系统配置','/config-wizard','views/config/ConfigurationWizard','MENU',NULL,724,'ACTIVE','system'),
 ('调试中心','/debug','views/debug/DebugCenter','MENU',NULL,725,'ACTIVE','system')
ON DUPLICATE KEY UPDATE menu_name=VALUES(menu_name),component=VALUES(component),menu_type='MENU',permission_code=VALUES(permission_code),sort=VALUES(sort),status='ACTIVE';

UPDATE t_menu m JOIN t_menu p ON p.path='/domain/client' SET m.parent_id=p.id
 WHERE m.path IN ('/lead/my','/lead/pool','/client/my','/client/team','/client/company','/client/company-sea','/client/team-sea','/client/import-tasks','/client/resource-pool-config','/client/import-export','/client-lookup','/ocr');
UPDATE t_menu m JOIN t_menu p ON p.path='/domain/matching' SET m.parent_id=p.id WHERE m.path IN ('/screening','/report/screening');
UPDATE t_menu m JOIN t_menu p ON p.path='/domain/service' SET m.parent_id=p.id WHERE m.path IN ('/service-operations/daily','/service-operations/appointments','/service-operations/outings','/service-operations/replay','/order');
UPDATE t_menu m JOIN t_menu p ON p.path='/domain/approval' SET m.parent_id=p.id WHERE m.path LIKE '/approval/%';
UPDATE t_menu m JOIN t_menu p ON p.path='/domain/operation' SET m.parent_id=p.id WHERE m.path IN ('/sms/templates','/sms/records','/reward/records','/reward/rules');
UPDATE t_menu m JOIN t_menu p ON p.path='/domain/report' SET m.parent_id=p.id WHERE m.path IN ('/report/center','/report/trend','/report-template');
UPDATE t_menu m JOIN t_menu p ON p.path='/domain/product' SET m.parent_id=p.id WHERE m.path IN ('/product/all','/product/cooperate','/channel-config','/channel-strategy','/channel-user-list','/rule-template','/rule','/strategy-template','/plan-edit');
UPDATE t_menu m JOIN t_menu p ON p.path='/domain/system' SET m.parent_id=p.id WHERE m.path IN ('/blacklist','/audit','/org/staff','/org/roles','/org/api-permissions','/config-wizard','/debug');
UPDATE t_menu SET status='DISABLED' WHERE path IN ('/lead','/client','/client?scope=TEAM_SEA','/service-operations','/approval','/sms','/reward','/reward-rule','/product','/org');

-- 历史表的 NULL permission_code 不受唯一索引去重约束，清理重复叶子/父级授权。
DELETE a FROM t_role_permission a JOIN t_role_permission b
 ON a.role_code=b.role_code AND a.menu_id=b.menu_id
 AND a.permission_code IS NULL AND b.permission_code IS NULL AND a.id>b.id;
DELETE rp FROM t_role_permission rp JOIN t_menu m ON m.id=rp.menu_id
 WHERE rp.permission_code IS NULL AND m.status='DISABLED';

-- 先重建本脚本负责的页面授权，保留按钮级 permission_code 授权。
DELETE rp FROM t_role_permission rp JOIN t_menu m ON m.id=rp.menu_id
 WHERE rp.permission_code IS NULL AND m.path IN ('/workbench','/lead/my','/lead/pool','/client/my','/client/team','/client/company','/client/company-sea','/client/team-sea','/client/import-tasks','/client/resource-pool-config','/client/import-export','/client-lookup','/ocr','/screening','/report/screening','/service-operations/daily','/service-operations/appointments','/service-operations/outings','/service-operations/replay','/order','/approval/mine','/approval/product','/approval/download','/approval/allocation','/approval/outing','/approval/sensitive-phone','/approval/channel-lead','/approval/sms-template','/approval/report-template','/sms/templates','/sms/records','/reward/records','/reward/rules','/report/center','/report/trend','/report-template','/product/all','/product/cooperate','/channel-config','/channel-strategy','/channel-user-list','/rule-template','/rule','/strategy-template','/plan-edit','/blacklist','/audit','/org/staff','/org/roles','/org/api-permissions','/config-wizard','/debug');

-- 角色菜单规则：基础集合 + 管理角色集合。
INSERT IGNORE INTO t_role_permission(role_code,menu_id,permission_code,created_by)
SELECT r.role_code,m.id,NULL,'system'
FROM (SELECT 'CHANNEL' role_code UNION ALL SELECT 'ADVISER' UNION ALL SELECT 'DEPT_MANAGER' UNION ALL SELECT 'OPERATOR' UNION ALL SELECT 'BOSS' UNION ALL SELECT 'SUPER_ADMIN' UNION ALL SELECT 'SUPER') r
JOIN t_menu m ON m.path='/workbench' AND m.status='ACTIVE';
INSERT IGNORE INTO t_role_permission(role_code,menu_id,permission_code,created_by)
SELECT r.role_code,m.id,NULL,'system' FROM (SELECT 'CHANNEL' role_code UNION ALL SELECT 'ADVISER' UNION ALL SELECT 'DEPT_MANAGER' UNION ALL SELECT 'OPERATOR' UNION ALL SELECT 'BOSS' UNION ALL SELECT 'SUPER_ADMIN' UNION ALL SELECT 'SUPER') r JOIN t_menu m ON m.path IN ('/lead/my','/client/my') AND m.status='ACTIVE';
INSERT IGNORE INTO t_role_permission(role_code,menu_id,permission_code,created_by)
SELECT 'CHANNEL',m.id,NULL,'system' FROM t_menu m WHERE m.path IN ('/product/all','/report/screening') AND m.status='ACTIVE';
INSERT IGNORE INTO t_role_permission(role_code,menu_id,permission_code,created_by)
SELECT r.role_code,m.id,NULL,'system' FROM (SELECT 'ADVISER' role_code UNION ALL SELECT 'DEPT_MANAGER' UNION ALL SELECT 'OPERATOR' UNION ALL SELECT 'BOSS' UNION ALL SELECT 'SUPER_ADMIN' UNION ALL SELECT 'SUPER') r JOIN t_menu m ON m.path IN ('/lead/pool','/client/company-sea','/client-lookup','/ocr','/screening','/report/screening','/service-operations/daily','/service-operations/appointments','/service-operations/outings','/service-operations/replay','/order','/report/center','/product/all') AND m.status='ACTIVE';
INSERT IGNORE INTO t_role_permission(role_code,menu_id,permission_code,created_by)
SELECT 'DEPT_MANAGER',m.id,NULL,'system' FROM t_menu m WHERE m.path IN ('/client/team','/client/team-sea','/approval/mine','/approval/download','/approval/allocation','/approval/outing','/approval/sensitive-phone') AND m.status='ACTIVE';
INSERT IGNORE INTO t_role_permission(role_code,menu_id,permission_code,created_by)
SELECT r.role_code,m.id,NULL,'system' FROM (SELECT 'OPERATOR' role_code UNION ALL SELECT 'BOSS' UNION ALL SELECT 'SUPER_ADMIN' UNION ALL SELECT 'SUPER') r JOIN t_menu m ON m.path IN ('/client/company','/client/resource-pool-config','/approval/mine','/approval/product','/approval/download','/approval/allocation','/approval/outing','/approval/sensitive-phone','/approval/channel-lead','/approval/sms-template','/approval/report-template','/sms/templates','/sms/records','/reward/records','/reward/rules','/report/trend','/report-template','/channel-config','/channel-strategy','/channel-user-list','/rule-template','/rule','/strategy-template','/plan-edit','/blacklist','/audit') AND m.status='ACTIVE';
INSERT IGNORE INTO t_role_permission(role_code,menu_id,permission_code,created_by)
SELECT r.role_code,m.id,NULL,'system' FROM (SELECT 'BOSS' role_code UNION ALL SELECT 'SUPER_ADMIN' UNION ALL SELECT 'SUPER') r JOIN t_menu m ON m.path IN ('/client/import-tasks','/client/import-export') AND m.status='ACTIVE';
INSERT IGNORE INTO t_role_permission(role_code,menu_id,permission_code,created_by)
SELECT r.role_code,m.id,NULL,'system' FROM (SELECT 'SUPER_ADMIN' role_code UNION ALL SELECT 'SUPER') r JOIN t_menu m ON m.path IN ('/org/staff','/org/roles','/org/api-permissions','/config-wizard','/debug') AND m.status='ACTIVE';

-- 叶子授权自动补齐有效父级，父级没有子项时不会返回。
INSERT IGNORE INTO t_role_permission(role_code,menu_id,permission_code,created_by)
SELECT DISTINCT rp.role_code,p.id,NULL,'system' FROM t_role_permission rp JOIN t_menu c ON c.id=rp.menu_id JOIN t_menu p ON p.id=c.parent_id
 WHERE rp.permission_code IS NULL AND c.status='ACTIVE' AND p.status='ACTIVE';

-- 最终去重：MySQL 唯一索引对 NULL 不互斥，必须显式保留每个角色/菜单一条记录。
DELETE a FROM t_role_permission a JOIN t_role_permission b
 ON a.role_code=b.role_code AND a.menu_id=b.menu_id
 AND a.permission_code IS NULL AND b.permission_code IS NULL AND a.id>b.id;
