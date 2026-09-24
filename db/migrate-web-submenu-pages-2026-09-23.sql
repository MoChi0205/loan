-- Web 列表型页内 Tab 拆为独立子菜单（幂等）
-- 页面数据权限仍由原接口 scope/角色校验控制；本迁移只调整导航叶子与页面授权。
SET NAMES utf8mb4;

INSERT INTO t_menu (menu_name, path, component, menu_type, permission_code, sort, status, created_by)
VALUES
 ('我的线索', '/lead/my', 'views/lead/LeadPool', 'MENU', 'page:lead', 111, 'ACTIVE', 'system'),
 ('线索公海', '/lead/pool', 'views/lead/LeadPool', 'MENU', 'page:lead', 112, 'ACTIVE', 'system'),
 ('我的客户', '/client/my', 'views/client/ClientProfile', 'MENU', 'page:client', 121, 'ACTIVE', 'system'),
 ('团队客户', '/client/team', 'views/client/ClientProfile', 'MENU', 'page:client', 122, 'ACTIVE', 'system'),
 ('全司客户', '/client/company', 'views/client/ClientProfile', 'MENU', 'page:client', 123, 'ACTIVE', 'system'),
 ('客户公海', '/client/company-sea', 'views/client/ClientProfile', 'MENU', 'page:client', 124, 'ACTIVE', 'system'),
 ('团队公海', '/client/team-sea', 'views/client/ClientProfile', 'MENU', 'page:client', 125, 'ACTIVE', 'system'),
 ('今日服务台', '/service-operations/daily', 'views/service/ServiceOperations', 'MENU', 'page:service-operations', 311, 'ACTIVE', 'system'),
 ('客户预约', '/service-operations/appointments', 'views/service/ServiceOperations', 'MENU', 'page:service-operations', 312, 'ACTIVE', 'system'),
 ('员工外出', '/service-operations/outings', 'views/service/ServiceOperations', 'MENU', 'page:service-operations', 313, 'ACTIVE', 'system'),
 ('客户回放', '/service-operations/replay', 'views/service/ServiceOperations', 'MENU', 'page:service-operations', 314, 'ACTIVE', 'system'),
 ('我的申请', '/approval/mine', 'views/approval/ApprovalCenter', 'MENU', 'page:approval', 351, 'ACTIVE', 'system'),
 ('产品审核', '/approval/product', 'views/approval/ApprovalCenter', 'MENU', 'page:approval', 352, 'ACTIVE', 'system'),
 ('附件下载审核', '/approval/download', 'views/approval/ApprovalCenter', 'MENU', 'page:approval', 353, 'ACTIVE', 'system'),
 ('客户分配审核', '/approval/allocation', 'views/approval/ApprovalCenter', 'MENU', 'page:approval', 354, 'ACTIVE', 'system'),
 ('外出审批', '/approval/outing', 'views/approval/ApprovalCenter', 'MENU', 'page:approval', 355, 'ACTIVE', 'system'),
 ('渠道线索审核', '/approval/channel-lead', 'views/approval/ApprovalCenter', 'MENU', 'page:approval', 356, 'ACTIVE', 'system'),
 ('短信模板审核', '/approval/sms-template', 'views/approval/ApprovalCenter', 'MENU', 'page:approval', 357, 'ACTIVE', 'system'),
 ('报告模板审核', '/approval/report-template', 'views/approval/ApprovalCenter', 'MENU', 'page:approval', 358, 'ACTIVE', 'system'),
 ('短信模板', '/sms/templates', 'views/sms/SmsCenter', 'MENU', 'page:sms', 411, 'ACTIVE', 'system'),
 ('发送记录', '/sms/records', 'views/sms/SmsCenter', 'MENU', 'page:sms', 412, 'ACTIVE', 'system'),
 ('奖励发放', '/reward/records', 'views/reward/RewardList', 'MENU', 'page:reward', 421, 'ACTIVE', 'system'),
 ('奖励规则', '/reward/rules', 'views/reward/RewardRuleConfig', 'MENU', 'page:reward-rule', 422, 'ACTIVE', 'system'),
 ('全量产品库', '/product/all', 'views/product/ProductList', 'MENU', 'page:product', 611, 'ACTIVE', 'system'),
 ('合作产品库', '/product/cooperate', 'views/product/ProductList', 'MENU', 'page:product', 612, 'ACTIVE', 'system'),
 ('员工管理', '/org/staff', 'views/org/OrgCenter', 'MENU', 'page:org', 711, 'ACTIVE', 'system'),
 ('角色权限', '/org/roles', 'views/org/OrgCenter', 'MENU', 'page:org', 712, 'ACTIVE', 'system'),
 ('接口权限', '/org/api-permissions', 'views/org/OrgCenter', 'MENU', 'page:org', 713, 'ACTIVE', 'system')
ON DUPLICATE KEY UPDATE menu_name=VALUES(menu_name), component=VALUES(component), menu_type='MENU',
 permission_code=VALUES(permission_code), sort=VALUES(sort), status='ACTIVE';

UPDATE t_menu m JOIN t_menu p ON p.path='/domain/client' SET m.parent_id=p.id
 WHERE m.path IN ('/lead/my','/lead/pool','/client/my','/client/team','/client/company','/client/company-sea','/client/team-sea');
UPDATE t_menu m JOIN t_menu p ON p.path='/domain/service' SET m.parent_id=p.id
 WHERE m.path IN ('/service-operations/daily','/service-operations/appointments','/service-operations/outings','/service-operations/replay');
UPDATE t_menu m JOIN t_menu p ON p.path='/domain/approval' SET m.parent_id=p.id
 WHERE m.path LIKE '/approval/%';
UPDATE t_menu m JOIN t_menu p ON p.path='/domain/operation' SET m.parent_id=p.id
 WHERE m.path IN ('/sms/templates','/sms/records','/reward/records','/reward/rules');
UPDATE t_menu m JOIN t_menu p ON p.path='/domain/product' SET m.parent_id=p.id
 WHERE m.path IN ('/product/all','/product/cooperate');
UPDATE t_menu m JOIN t_menu p ON p.path='/domain/system' SET m.parent_id=p.id
 WHERE m.path IN ('/org/staff','/org/roles','/org/api-permissions');

DELETE rp FROM t_role_permission rp JOIN t_menu m ON m.id=rp.menu_id
WHERE rp.permission_code IS NULL AND m.path IN (
 '/lead/my','/lead/pool','/client/my','/client/team','/client/company','/client/company-sea','/client/team-sea',
 '/service-operations/daily','/service-operations/appointments','/service-operations/outings','/service-operations/replay',
 '/approval/mine','/approval/product','/approval/download','/approval/allocation','/approval/outing','/approval/channel-lead','/approval/sms-template','/approval/report-template',
 '/sms/templates','/sms/records','/reward/records','/reward/rules','/product/all','/product/cooperate',
 '/channel-config','/channel-strategy','/channel-user-list','/org/staff','/org/roles','/org/api-permissions'
);

INSERT INTO t_role_permission (role_code, menu_id, created_by)
SELECT matrix.role_code, m.id, 'system'
FROM (
 SELECT 'CHANNEL' role_code, '/lead/my' path UNION ALL SELECT 'CHANNEL','/client/my' UNION ALL SELECT 'CHANNEL','/product/all'
 UNION ALL SELECT 'ADVISER','/lead/my' UNION ALL SELECT 'ADVISER','/lead/pool' UNION ALL SELECT 'ADVISER','/client/my' UNION ALL SELECT 'ADVISER','/client/company-sea' UNION ALL SELECT 'ADVISER','/product/all'
 UNION ALL SELECT 'DEPT_MANAGER','/lead/my' UNION ALL SELECT 'DEPT_MANAGER','/lead/pool' UNION ALL SELECT 'DEPT_MANAGER','/client/my' UNION ALL SELECT 'DEPT_MANAGER','/client/team' UNION ALL SELECT 'DEPT_MANAGER','/client/company-sea' UNION ALL SELECT 'DEPT_MANAGER','/client/team-sea' UNION ALL SELECT 'DEPT_MANAGER','/product/all'
) matrix JOIN t_menu m ON m.path=matrix.path;

INSERT INTO t_role_permission (role_code, menu_id, created_by)
SELECT roles.role_code,m.id,'system'
FROM (SELECT 'BOSS' role_code UNION ALL SELECT 'OPERATOR' UNION ALL SELECT 'SUPER_ADMIN' UNION ALL SELECT 'SUPER') roles
JOIN t_menu m ON m.path IN ('/lead/my','/lead/pool','/client/my','/client/company','/client/company-sea','/product/all');

INSERT INTO t_role_permission (role_code, menu_id, created_by)
SELECT roles.role_code,m.id,'system'
FROM (SELECT 'ADVISER' role_code UNION ALL SELECT 'DEPT_MANAGER' UNION ALL SELECT 'BOSS' UNION ALL SELECT 'OPERATOR' UNION ALL SELECT 'SUPER_ADMIN' UNION ALL SELECT 'SUPER') roles
JOIN t_menu m ON m.path IN ('/service-operations/daily','/service-operations/appointments','/service-operations/outings','/service-operations/replay');

INSERT INTO t_role_permission (role_code, menu_id, created_by)
SELECT roles.role_code,m.id,'system'
FROM (SELECT 'DEPT_MANAGER' role_code UNION ALL SELECT 'BOSS' UNION ALL SELECT 'OPERATOR' UNION ALL SELECT 'SUPER_ADMIN' UNION ALL SELECT 'SUPER') roles
JOIN t_menu m ON m.path IN ('/approval/download','/approval/allocation','/approval/outing');

-- “我的申请”只展示本人发起记录，不授予审批权；所有内部员工均应可见。
INSERT INTO t_role_permission (role_code, menu_id, created_by)
SELECT roles.role_code,m.id,'system'
FROM (SELECT 'ADVISER' role_code UNION ALL SELECT 'DEPT_MANAGER' UNION ALL SELECT 'BOSS'
      UNION ALL SELECT 'OPERATOR' UNION ALL SELECT 'SUPER_ADMIN' UNION ALL SELECT 'SUPER') roles
JOIN t_menu m ON m.path='/approval/mine';

INSERT INTO t_role_permission (role_code, menu_id, created_by)
SELECT roles.role_code,m.id,'system'
FROM (SELECT 'BOSS' role_code UNION ALL SELECT 'SUPER_ADMIN' UNION ALL SELECT 'SUPER') roles
JOIN t_menu m ON m.path IN ('/approval/channel-lead','/product/cooperate');

INSERT INTO t_role_permission (role_code, menu_id, created_by)
SELECT roles.role_code,m.id,'system'
FROM (SELECT 'OPERATOR' role_code UNION ALL SELECT 'BOSS' UNION ALL SELECT 'SUPER_ADMIN' UNION ALL SELECT 'SUPER') roles
JOIN t_menu m ON m.path IN ('/approval/product','/approval/sms-template','/approval/report-template');

INSERT INTO t_role_permission (role_code, menu_id, created_by)
SELECT roles.role_code,m.id,'system'
FROM (SELECT 'BOSS' role_code UNION ALL SELECT 'OPERATOR' UNION ALL SELECT 'SUPER_ADMIN' UNION ALL SELECT 'SUPER') roles
JOIN t_menu m ON m.path IN ('/sms/templates','/sms/records','/reward/records','/reward/rules');

INSERT INTO t_role_permission (role_code, menu_id, created_by)
SELECT roles.role_code,m.id,'system'
FROM (SELECT 'BOSS' role_code UNION ALL SELECT 'OPERATOR' UNION ALL SELECT 'SUPER_ADMIN' UNION ALL SELECT 'SUPER') roles
JOIN t_menu m ON m.path IN ('/channel-config','/channel-strategy','/channel-user-list');

INSERT INTO t_role_permission (role_code, menu_id, created_by)
SELECT roles.role_code,m.id,'system'
FROM (SELECT 'OPERATOR' role_code UNION ALL SELECT 'SUPER_ADMIN' UNION ALL SELECT 'SUPER') roles
JOIN t_menu m ON m.path IN ('/org/staff','/org/roles','/org/api-permissions');

-- 新叶子就绪后隐藏被拆分的旧聚合入口；渠道三个页面本身已是独立页，保持启用。
UPDATE t_menu SET status='DISABLED' WHERE path IN ('/lead','/client','/client?scope=TEAM_SEA','/service-operations','/approval','/sms','/reward','/reward-rule','/product','/org');
UPDATE t_menu SET status='ACTIVE' WHERE path IN ('/channel-config','/channel-strategy','/channel-user-list');

-- 父分组授权（兼容旧服务实例）。
INSERT INTO t_role_permission (role_code, menu_id, created_by)
SELECT DISTINCT leaf.role_code,parent.id,'system'
FROM t_role_permission leaf
JOIN t_menu child ON child.id=leaf.menu_id
JOIN t_menu parent ON parent.id=child.parent_id
LEFT JOIN t_role_permission existing ON existing.role_code=leaf.role_code AND existing.menu_id=parent.id AND existing.permission_code IS NULL
WHERE leaf.permission_code IS NULL AND child.status='ACTIVE' AND existing.id IS NULL;

SELECT m.path,m.menu_name,p.path parent_path,GROUP_CONCAT(rp.role_code ORDER BY rp.role_code) roles
FROM t_menu m LEFT JOIN t_menu p ON p.id=m.parent_id LEFT JOIN t_role_permission rp ON rp.menu_id=m.id AND rp.permission_code IS NULL
WHERE m.status='ACTIVE' GROUP BY m.id,m.path,m.menu_name,p.path ORDER BY m.sort,m.id;
