-- Web 菜单产品视角重构（幂等）
-- 当前菜单与权限真值：docs/knowledge-base/01-角色权限模型.md 与实际菜单种子数据
-- 仅调整菜单层级/名称/角色页面权限；渠道业务与接口实现不变。
SET NAMES utf8mb4;

-- 1. 九个产品分组。父级只承载导航结构，不直接加载页面。
INSERT INTO t_menu (menu_name, path, component, menu_type, permission_code, sort, status, created_by)
VALUES
 ('客户中心', '/domain/client', NULL, 'MENU', 'domain:client', 100, 'ACTIVE', 'system'),
 ('智能匹配', '/domain/matching', NULL, 'MENU', 'domain:matching', 200, 'ACTIVE', 'system'),
 ('服务交付', '/domain/service', NULL, 'MENU', 'domain:service', 300, 'ACTIVE', 'system'),
 ('审批中心', '/domain/approval', NULL, 'MENU', 'domain:approval', 350, 'ACTIVE', 'system'),
 ('营销激励', '/domain/operation', NULL, 'MENU', 'domain:operation', 400, 'ACTIVE', 'system'),
 ('经营分析', '/domain/report', NULL, 'MENU', 'domain:report', 500, 'ACTIVE', 'system'),
 ('产品与规则', '/domain/product', NULL, 'MENU', 'domain:product', 600, 'ACTIVE', 'system'),
 ('系统管理', '/domain/system', NULL, 'MENU', 'domain:system', 700, 'ACTIVE', 'system')
ON DUPLICATE KEY UPDATE menu_name=VALUES(menu_name), component=NULL, menu_type='MENU',
 permission_code=VALUES(permission_code), sort=VALUES(sort), status='ACTIVE';

-- 2. 补齐独立子页面。组件可复用，但每个业务视图都有独立路由和菜单权限。
INSERT INTO t_menu (menu_name, path, component, menu_type, permission_code, sort, status, created_by)
SELECT '服务台', '/service-operations', 'views/service/ServiceOperations', 'MENU',
       'page:service-operations', 310, 'ACTIVE', 'system'
WHERE NOT EXISTS (SELECT 1 FROM t_menu WHERE path='/service-operations');

INSERT INTO t_menu (parent_id, menu_name, path, component, menu_type, permission_code, sort, status, created_by)
SELECT parent.id, children.menu_name, children.path, children.component, 'MENU', children.permission_code,
       children.sort, 'ACTIVE', 'system'
FROM t_menu parent
JOIN (
 SELECT '今日服务台' menu_name, '/service-operations/daily' path, 'views/service/ServiceOperations' component, 'page:service-daily' permission_code, 311 sort
 UNION ALL SELECT '客户预约','/service-operations/appointments','views/service/ServiceOperations','page:service-appointments',312
 UNION ALL SELECT '员工外出','/service-operations/outings','views/service/ServiceOperations','page:service-outings',313
 UNION ALL SELECT '客户回放','/service-operations/replay','views/service/ServiceOperations','page:service-replay',314
) children
WHERE parent.path='/domain/service'
ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id), menu_name=VALUES(menu_name), component=VALUES(component),
 permission_code=VALUES(permission_code), sort=VALUES(sort), status='ACTIVE';

INSERT INTO t_menu (parent_id, menu_name, path, component, menu_type, permission_code, sort, status, created_by)
SELECT parent.id, children.menu_name, children.path, children.component, 'MENU', children.permission_code,
       children.sort, 'ACTIVE', 'system'
FROM t_menu parent
JOIN (
 SELECT '我的申请' menu_name, '/approval/mine' path, 'views/approval/ApprovalCenter' component, 'page:approval-mine' permission_code, 351 sort
 UNION ALL SELECT '产品审核','/approval/product','views/approval/ApprovalCenter','page:approval-product',352
 UNION ALL SELECT '附件下载审核','/approval/download','views/approval/ApprovalCenter','page:approval-download',353
 UNION ALL SELECT '客户分配审核','/approval/allocation','views/approval/ApprovalCenter','page:approval-allocation',354
 UNION ALL SELECT '外出审批','/approval/outing','views/approval/ApprovalCenter','page:approval-outing',355
 UNION ALL SELECT '手机号查看审批','/approval/sensitive-phone','views/approval/ApprovalCenter','page:approval-sensitive-phone',356
 UNION ALL SELECT '渠道线索审核','/approval/channel-lead','views/approval/ApprovalCenter','page:approval-channel-lead',357
 UNION ALL SELECT '短信模板审核','/approval/sms-template','views/approval/ApprovalCenter','page:approval-sms-template',358
 UNION ALL SELECT '报告模板审核','/approval/report-template','views/approval/ApprovalCenter','page:approval-report-template',359
) children
WHERE parent.path='/domain/approval'
ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id), menu_name=VALUES(menu_name), component=VALUES(component),
 permission_code=VALUES(permission_code), sort=VALUES(sort), status='ACTIVE';

-- 3. 命名与父子关系。合并后的旧叶子保留路由/数据，不再作为独立侧栏授权真值。
UPDATE t_menu SET menu_name='客户检索' WHERE path='/client-lookup';
UPDATE t_menu SET menu_name='客户材料' WHERE path='/ocr';
UPDATE t_menu SET menu_name='匹配任务' WHERE path='/screening';
UPDATE t_menu SET menu_name='诊断报告' WHERE path='/report/screening';
UPDATE t_menu SET menu_name='服务台', component='views/service/ServiceOperations', status='ACTIVE' WHERE path='/service-operations';
UPDATE t_menu SET menu_name='审批中心' WHERE path='/approval';
UPDATE t_menu SET menu_name='奖励管理' WHERE path='/reward';
UPDATE t_menu SET menu_name='渠道管理' WHERE path='/channel-config';
UPDATE t_menu SET menu_name='规则模板' WHERE path='/rule-template';
UPDATE t_menu SET menu_name='规则实例' WHERE path='/rule';

UPDATE t_menu m JOIN t_menu p ON p.path='/domain/client' SET m.parent_id=p.id
 WHERE m.path IN ('/lead','/client','/client-lookup','/ocr','/client?scope=TEAM_SEA');
UPDATE t_menu m JOIN t_menu p ON p.path='/domain/matching' SET m.parent_id=p.id
 WHERE m.path IN ('/screening','/report/screening');
UPDATE t_menu m JOIN t_menu p ON p.path='/domain/service' SET m.parent_id=p.id
 WHERE m.path IN ('/service-operations','/order');
UPDATE t_menu m JOIN t_menu p ON p.path='/domain/approval' SET m.parent_id=p.id WHERE m.path='/approval';
UPDATE t_menu m JOIN t_menu p ON p.path='/domain/operation' SET m.parent_id=p.id WHERE m.path IN ('/sms','/reward');
UPDATE t_menu m JOIN t_menu p ON p.path='/domain/report' SET m.parent_id=p.id
 WHERE m.path IN ('/report/center','/report/trend','/report-template');
UPDATE t_menu m JOIN t_menu p ON p.path='/domain/product' SET m.parent_id=p.id
 WHERE m.path IN ('/product','/channel-config','/rule-template','/rule','/plan-edit','/strategy-template');
UPDATE t_menu m JOIN t_menu p ON p.path='/domain/system' SET m.parent_id=p.id
 WHERE m.path IN ('/org','/audit','/blacklist','/config-wizard','/debug');

-- 4. 页面授权按角色矩阵重建；permission_code 按钮授权不在此处改动。
DELETE rp FROM t_role_permission rp
JOIN t_menu m ON m.id=rp.menu_id
WHERE rp.permission_code IS NULL;

INSERT INTO t_role_permission (role_code, menu_id, created_by)
SELECT matrix.role_code, m.id, 'system'
FROM (
 SELECT 'CHANNEL' role_code, '/workbench' path UNION ALL SELECT 'CHANNEL','/lead' UNION ALL SELECT 'CHANNEL','/client' UNION ALL SELECT 'CHANNEL','/product' UNION ALL SELECT 'CHANNEL','/report/screening'
 UNION ALL SELECT 'ADVISER','/workbench' UNION ALL SELECT 'ADVISER','/lead' UNION ALL SELECT 'ADVISER','/client' UNION ALL SELECT 'ADVISER','/client-lookup' UNION ALL SELECT 'ADVISER','/ocr' UNION ALL SELECT 'ADVISER','/screening' UNION ALL SELECT 'ADVISER','/report/screening' UNION ALL SELECT 'ADVISER','/service-operations' UNION ALL SELECT 'ADVISER','/service-operations/daily' UNION ALL SELECT 'ADVISER','/service-operations/appointments' UNION ALL SELECT 'ADVISER','/service-operations/outings' UNION ALL SELECT 'ADVISER','/service-operations/replay' UNION ALL SELECT 'ADVISER','/order' UNION ALL SELECT 'ADVISER','/approval' UNION ALL SELECT 'ADVISER','/approval/mine' UNION ALL SELECT 'ADVISER','/report/center' UNION ALL SELECT 'ADVISER','/product'
 UNION ALL SELECT 'DEPT_MANAGER','/workbench' UNION ALL SELECT 'DEPT_MANAGER','/lead' UNION ALL SELECT 'DEPT_MANAGER','/client' UNION ALL SELECT 'DEPT_MANAGER','/client-lookup' UNION ALL SELECT 'DEPT_MANAGER','/client?scope=TEAM_SEA' UNION ALL SELECT 'DEPT_MANAGER','/ocr' UNION ALL SELECT 'DEPT_MANAGER','/screening' UNION ALL SELECT 'DEPT_MANAGER','/report/screening' UNION ALL SELECT 'DEPT_MANAGER','/service-operations' UNION ALL SELECT 'DEPT_MANAGER','/order' UNION ALL SELECT 'DEPT_MANAGER','/approval' UNION ALL SELECT 'DEPT_MANAGER','/report/center' UNION ALL SELECT 'DEPT_MANAGER','/product'
) matrix JOIN t_menu m ON m.path=matrix.path;

-- 所有内部角色都能查看本人申请；渠道完全排除。
INSERT INTO t_role_permission (role_code, menu_id, created_by)
SELECT roles.role_code, m.id, 'system'
FROM (SELECT 'ADVISER' role_code UNION ALL SELECT 'DEPT_MANAGER' UNION ALL SELECT 'BOSS' UNION ALL
      SELECT 'OPERATOR' UNION ALL SELECT 'SUPER_ADMIN' UNION ALL SELECT 'SUPER') roles
JOIN t_menu m ON m.path='/approval/mine'
LEFT JOIN t_role_permission existing ON existing.role_code=roles.role_code AND existing.menu_id=m.id AND existing.permission_code IS NULL
WHERE existing.id IS NULL;

-- 服务交付四个子页面对所有内部角色开放，数据范围仍由后端收口；渠道完全排除。
INSERT INTO t_role_permission (role_code, menu_id, created_by)
SELECT roles.role_code, m.id, 'system'
FROM (SELECT 'ADVISER' role_code UNION ALL SELECT 'DEPT_MANAGER' UNION ALL SELECT 'BOSS' UNION ALL
      SELECT 'OPERATOR' UNION ALL SELECT 'SUPER_ADMIN' UNION ALL SELECT 'SUPER') roles
JOIN t_menu m ON m.path IN ('/service-operations/daily','/service-operations/appointments',
                            '/service-operations/outings','/service-operations/replay')
LEFT JOIN t_role_permission existing ON existing.role_code=roles.role_code AND existing.menu_id=m.id AND existing.permission_code IS NULL
WHERE existing.id IS NULL;

-- 审核子页面按角色业务边界授权；“我的申请”已在上方单独向全部内部角色开放。
INSERT INTO t_role_permission (role_code, menu_id, created_by)
SELECT matrix.role_code, m.id, 'system'
FROM (
 SELECT 'OPERATOR' role_code, '/approval/product' path UNION ALL SELECT 'BOSS','/approval/product' UNION ALL SELECT 'SUPER_ADMIN','/approval/product' UNION ALL SELECT 'SUPER','/approval/product'
 UNION ALL SELECT 'DEPT_MANAGER','/approval/download' UNION ALL SELECT 'BOSS','/approval/download' UNION ALL SELECT 'OPERATOR','/approval/download' UNION ALL SELECT 'SUPER_ADMIN','/approval/download' UNION ALL SELECT 'SUPER','/approval/download'
 UNION ALL SELECT 'DEPT_MANAGER','/approval/allocation' UNION ALL SELECT 'BOSS','/approval/allocation' UNION ALL SELECT 'OPERATOR','/approval/allocation' UNION ALL SELECT 'SUPER_ADMIN','/approval/allocation' UNION ALL SELECT 'SUPER','/approval/allocation'
 UNION ALL SELECT 'DEPT_MANAGER','/approval/outing' UNION ALL SELECT 'BOSS','/approval/outing' UNION ALL SELECT 'OPERATOR','/approval/outing' UNION ALL SELECT 'SUPER_ADMIN','/approval/outing' UNION ALL SELECT 'SUPER','/approval/outing'
 UNION ALL SELECT 'DEPT_MANAGER','/approval/sensitive-phone' UNION ALL SELECT 'BOSS','/approval/sensitive-phone' UNION ALL SELECT 'SUPER_ADMIN','/approval/sensitive-phone' UNION ALL SELECT 'SUPER','/approval/sensitive-phone'
 UNION ALL SELECT 'BOSS','/approval/channel-lead' UNION ALL SELECT 'SUPER_ADMIN','/approval/channel-lead' UNION ALL SELECT 'SUPER','/approval/channel-lead'
 UNION ALL SELECT 'OPERATOR','/approval/sms-template' UNION ALL SELECT 'BOSS','/approval/sms-template' UNION ALL SELECT 'SUPER_ADMIN','/approval/sms-template' UNION ALL SELECT 'SUPER','/approval/sms-template'
 UNION ALL SELECT 'OPERATOR','/approval/report-template' UNION ALL SELECT 'BOSS','/approval/report-template' UNION ALL SELECT 'SUPER_ADMIN','/approval/report-template' UNION ALL SELECT 'SUPER','/approval/report-template'
) matrix JOIN t_menu m ON m.path=matrix.path
LEFT JOIN t_role_permission existing ON existing.role_code=matrix.role_code AND existing.menu_id=m.id AND existing.permission_code IS NULL
WHERE existing.id IS NULL;

INSERT INTO t_role_permission (role_code, menu_id, created_by)
SELECT roles.role_code, m.id, 'system'
FROM (SELECT 'BOSS' role_code UNION ALL SELECT 'OPERATOR' UNION ALL SELECT 'SUPER_ADMIN' UNION ALL SELECT 'SUPER') roles
JOIN t_menu m ON m.path IN ('/workbench','/lead','/client','/client-lookup','/client?scope=TEAM_SEA','/ocr','/screening','/report/screening','/service-operations','/order','/approval','/sms','/reward','/report/center','/report/trend','/report-template','/product','/channel-config','/rule-template','/rule','/plan-edit','/strategy-template','/audit','/blacklist');

INSERT INTO t_role_permission (role_code, menu_id, created_by)
SELECT roles.role_code, m.id, 'system'
FROM (SELECT 'OPERATOR' role_code UNION ALL SELECT 'SUPER_ADMIN' UNION ALL SELECT 'SUPER') roles
JOIN t_menu m ON m.path IN ('/org','/config-wizard');

INSERT INTO t_role_permission (role_code, menu_id, created_by)
SELECT roles.role_code, m.id, 'system'
FROM (SELECT 'SUPER_ADMIN' role_code UNION ALL SELECT 'SUPER') roles
JOIN t_menu m ON m.path='/debug';

-- 父分组授权显式写入，兼容尚未升级 OrgService 自动补祖先的服务实例。
INSERT INTO t_role_permission (role_code, menu_id, created_by)
SELECT DISTINCT leaf.role_code, parent.id, 'system'
FROM t_role_permission leaf
JOIN t_menu child ON child.id=leaf.menu_id
JOIN t_menu parent ON parent.id=child.parent_id
LEFT JOIN t_role_permission existing ON existing.role_code=leaf.role_code AND existing.menu_id=parent.id AND existing.permission_code IS NULL
WHERE leaf.permission_code IS NULL AND existing.id IS NULL;

-- 顾问保留审批中心父分组与“我的申请”，不授予任何审核子页面。

-- 合并菜单的旧路径不再独立出现在权限树，但路由由前端映射到主菜单后仍可访问。
UPDATE t_menu SET status='DISABLED' WHERE path IN ('/reward-rule','/channel-strategy','/channel-user-list');

SELECT m.path,m.menu_name,p.path parent_path,GROUP_CONCAT(rp.role_code ORDER BY rp.role_code) roles
FROM t_menu m LEFT JOIN t_menu p ON p.id=m.parent_id LEFT JOIN t_role_permission rp ON rp.menu_id=m.id
WHERE m.status='ACTIVE' GROUP BY m.id,m.path,m.menu_name,p.path ORDER BY m.sort,m.id;
