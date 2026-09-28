-- 2026-09-28：内部角色业务入口补齐。
-- 仅补齐菜单权限，不授予渠道角色；菜单 path 使用业务路由，不暴露数据库自增 ID。
-- 幂等执行，可在当前 Nacos 指向的数据源上执行。
INSERT INTO t_menu (menu_name, path, component, menu_type, permission_code, sort, status, created_by)
SELECT '客户检索', '/client-lookup', 'views/client/ClientLookup', 'MENU', 'page:client', 230, 'ACTIVE', 'system'
WHERE NOT EXISTS (SELECT 1 FROM t_menu WHERE path = '/client-lookup');

INSERT IGNORE INTO t_role_permission (role_code, menu_id, permission_code, created_by)
SELECT roles.role_code, m.id, NULL, 'system'
FROM (
  SELECT 'ADVISER' role_code UNION ALL SELECT 'DEPT_MANAGER' UNION ALL
  SELECT 'BOSS' UNION ALL SELECT 'OPERATOR' UNION ALL
  SELECT 'SUPER_ADMIN' UNION ALL SELECT 'SUPER'
) roles
JOIN t_menu m ON m.path IN (
  '/lead/my', '/lead/pool', '/client-lookup', '/client/company-sea',
  '/ocr', '/product/all'
) AND m.status = 'ACTIVE';

-- 团队公海仅部门经理可见；全司公海由各内部角色按数据范围进入。
INSERT IGNORE INTO t_role_permission (role_code, menu_id, permission_code, created_by)
SELECT 'DEPT_MANAGER', m.id, NULL, 'system'
FROM t_menu m
WHERE m.path = '/client/team-sea' AND m.status = 'ACTIVE';
