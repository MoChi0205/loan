-- 修复客户子菜单与 page:client 接口权限不一致。
-- 旧 /client 菜单已停用，但历史角色授权仍停留在旧菜单，导致预约客户搜索、客户回放等接口 403。
-- 幂等执行；只补充各角色应有的客户子菜单，不扩大渠道权限。

INSERT IGNORE INTO t_role_permission (role_code, menu_id, permission_code, created_by)
SELECT roles.role_code, m.id, NULL, 'system'
FROM (
  SELECT 'ADVISER' role_code, '/client/my' path
  UNION ALL SELECT 'DEPT_MANAGER', '/client/my'
  UNION ALL SELECT 'DEPT_MANAGER', '/client/team'
  UNION ALL SELECT 'BOSS', '/client/my'
  UNION ALL SELECT 'BOSS', '/client/team'
  UNION ALL SELECT 'BOSS', '/client/company'
  UNION ALL SELECT 'OPERATOR', '/client/my'
  UNION ALL SELECT 'OPERATOR', '/client/team'
  UNION ALL SELECT 'OPERATOR', '/client/company'
  UNION ALL SELECT 'SUPER_ADMIN', '/client/my'
  UNION ALL SELECT 'SUPER_ADMIN', '/client/team'
  UNION ALL SELECT 'SUPER_ADMIN', '/client/company'
  UNION ALL SELECT 'SUPER', '/client/my'
  UNION ALL SELECT 'SUPER', '/client/team'
  UNION ALL SELECT 'SUPER', '/client/company'
) roles
JOIN t_menu m ON m.path = roles.path AND m.status = 'ACTIVE';

INSERT IGNORE INTO t_role_permission (role_code, menu_id, permission_code, created_by)
SELECT roles.role_code, m.id, NULL, 'system'
FROM (
  SELECT 'ADVISER' role_code
  UNION ALL SELECT 'DEPT_MANAGER'
  UNION ALL SELECT 'BOSS'
  UNION ALL SELECT 'OPERATOR'
  UNION ALL SELECT 'SUPER_ADMIN'
  UNION ALL SELECT 'SUPER'
) roles
JOIN t_menu m ON m.path = '/client/company-sea' AND m.status = 'ACTIVE';

INSERT IGNORE INTO t_role_permission (role_code, menu_id, permission_code, created_by)
SELECT 'DEPT_MANAGER', m.id, NULL, 'system'
FROM t_menu m
WHERE m.path = '/client/team-sea' AND m.status = 'ACTIVE';

