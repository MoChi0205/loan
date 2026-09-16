-- Web 菜单业务域树（开发阶段幂等迁移）
-- 目标：t_menu 父级只做分组，不挂 component；叶子继续使用现有前端路由。
SET NAMES utf8mb4;

INSERT INTO t_menu (menu_name, path, component, menu_type, permission_code, sort, status, created_by)
VALUES
 ('客户经营', '/domain/client', NULL, 'MENU', 'domain:client', 100, 'ACTIVE', 'system'),
 ('匹配与规则', '/domain/matching', NULL, 'MENU', 'domain:matching', 200, 'ACTIVE', 'system'),
 ('服务与审核', '/domain/service', NULL, 'MENU', 'domain:service', 300, 'ACTIVE', 'system'),
 ('运营与激励', '/domain/operation', NULL, 'MENU', 'domain:operation', 400, 'ACTIVE', 'system'),
 ('数据与报表', '/domain/report', NULL, 'MENU', 'domain:report', 500, 'ACTIVE', 'system'),
 ('产品与渠道', '/domain/product', NULL, 'MENU', 'domain:product', 600, 'ACTIVE', 'system'),
 ('系统管理', '/domain/system', NULL, 'MENU', 'domain:system', 700, 'ACTIVE', 'system')
ON DUPLICATE KEY UPDATE menu_name=VALUES(menu_name), component=NULL, menu_type='MENU',
  permission_code=VALUES(permission_code), status='ACTIVE';

UPDATE t_menu m JOIN t_menu p ON p.path='/domain/client' SET m.parent_id=p.id
 WHERE m.path IN ('/lead','/client','/client-lookup','/ocr');
UPDATE t_menu m JOIN t_menu p ON p.path='/domain/matching' SET m.parent_id=p.id
 WHERE m.path IN ('/screening','/rule-template','/rule','/strategy-template','/plan-edit');
UPDATE t_menu m JOIN t_menu p ON p.path='/domain/service' SET m.parent_id=p.id
 WHERE m.path IN ('/order','/approval');
UPDATE t_menu m JOIN t_menu p ON p.path='/domain/operation' SET m.parent_id=p.id
 WHERE m.path IN ('/sms','/reward','/reward-rule','/audit');
UPDATE t_menu m JOIN t_menu p ON p.path='/domain/report' SET m.parent_id=p.id
 WHERE m.path IN ('/report/center','/report/trend','/report/screening','/report-template');
UPDATE t_menu m JOIN t_menu p ON p.path='/domain/product' SET m.parent_id=p.id
 WHERE m.path IN ('/product','/channel-config','/channel-strategy','/channel-user-list','/blacklist');
UPDATE t_menu m JOIN t_menu p ON p.path='/domain/system' SET m.parent_id=p.id
 WHERE m.path IN ('/org','/config-wizard','/debug');

-- 给每个角色补父级菜单；叶子权限保持原有矩阵不变。
INSERT IGNORE INTO t_role_permission (role_code, menu_id, created_by)
SELECT r.role_code, m.id, 'system'
FROM (SELECT 'CHANNEL' role_code UNION ALL SELECT 'ADVISER' UNION ALL SELECT 'DEPT_MANAGER'
      UNION ALL SELECT 'OPERATOR' UNION ALL SELECT 'BOSS' UNION ALL SELECT 'SUPER_ADMIN' UNION ALL SELECT 'SUPER') r
JOIN t_menu m ON m.path IN ('/domain/client','/domain/matching','/domain/service','/domain/operation','/domain/report','/domain/product','/domain/system')
WHERE (r.role_code IN ('CHANNEL','ADVISER','DEPT_MANAGER','OPERATOR','BOSS','SUPER_ADMIN','SUPER'))
  AND ((m.path='/domain/client') OR (m.path='/domain/product'));
INSERT IGNORE INTO t_role_permission (role_code, menu_id, created_by)
SELECT r.role_code, m.id, 'system'
FROM (SELECT 'ADVISER' role_code UNION ALL SELECT 'DEPT_MANAGER' UNION ALL SELECT 'OPERATOR'
      UNION ALL SELECT 'BOSS' UNION ALL SELECT 'SUPER_ADMIN' UNION ALL SELECT 'SUPER') r
JOIN t_menu m ON m.path IN ('/domain/matching','/domain/service','/domain/report');
INSERT IGNORE INTO t_role_permission (role_code, menu_id, created_by)
SELECT r.role_code, m.id, 'system'
FROM (SELECT 'OPERATOR' role_code UNION ALL SELECT 'BOSS' UNION ALL SELECT 'SUPER_ADMIN' UNION ALL SELECT 'SUPER') r
JOIN t_menu m ON m.path='/domain/operation';
INSERT IGNORE INTO t_role_permission (role_code, menu_id, created_by)
SELECT r.role_code, m.id, 'system'
FROM (SELECT 'OPERATOR' role_code UNION ALL SELECT 'SUPER_ADMIN' UNION ALL SELECT 'SUPER') r
JOIN t_menu m ON m.path='/domain/system';

-- 公司员工均可查询产品库；顾问原始种子缺少该叶子菜单，此处补齐。
INSERT IGNORE INTO t_role_permission (role_code, menu_id, created_by)
SELECT 'ADVISER', id, 'system' FROM t_menu WHERE path='/product';

-- 公司员工用户查询入口。
INSERT INTO t_menu (menu_name, path, component, menu_type, permission_code, sort, status, created_by)
SELECT '用户查询', '/client-lookup', 'client/ClientLookup', 'MENU', 'page:client', 230, 'ACTIVE', 'system'
WHERE NOT EXISTS (SELECT 1 FROM t_menu WHERE path='/client-lookup');
UPDATE t_menu m JOIN t_menu p ON p.path='/domain/client' SET m.parent_id=p.id WHERE m.path='/client-lookup';
INSERT IGNORE INTO t_role_permission (role_code, menu_id, created_by)
SELECT r.role_code, m.id, 'system'
FROM (SELECT 'ADVISER' role_code UNION ALL SELECT 'DEPT_MANAGER' UNION ALL SELECT 'OPERATOR'
      UNION ALL SELECT 'BOSS' UNION ALL SELECT 'SUPER_ADMIN' UNION ALL SELECT 'SUPER') r
JOIN t_menu m ON m.path='/client-lookup';

SELECT m.path, m.parent_id, p.path parent_path FROM t_menu m
LEFT JOIN t_menu p ON p.id=m.parent_id
WHERE m.path LIKE '/domain/%' OR m.path IN ('/lead','/client','/ocr','/approval','/product');

-- 审批模块对全部公司员工开放；页面内仅本人申请可全员查看，审核动作仍按角色控制。
UPDATE t_menu SET menu_name='我的审批' WHERE path='/approval';
INSERT IGNORE INTO t_role_permission (role_code, menu_id, created_by)
SELECT r.role_code,m.id,'system'
FROM (SELECT 'ADVISER' role_code UNION ALL SELECT 'DEPT_MANAGER' UNION ALL SELECT 'OPERATOR'
      UNION ALL SELECT 'BOSS' UNION ALL SELECT 'SUPER_ADMIN' UNION ALL SELECT 'SUPER') r
JOIN t_menu m ON m.path='/approval';

-- 初筛报告作为数据与报表独立菜单，所有公司员工按各自数据范围查看。
INSERT IGNORE INTO t_role_permission (role_code, menu_id, created_by)
SELECT r.role_code,m.id,'system'
FROM (SELECT 'ADVISER' role_code UNION ALL SELECT 'DEPT_MANAGER' UNION ALL SELECT 'OPERATOR'
      UNION ALL SELECT 'BOSS' UNION ALL SELECT 'SUPER_ADMIN' UNION ALL SELECT 'SUPER') r
JOIN t_menu m ON m.path='/report/screening';
