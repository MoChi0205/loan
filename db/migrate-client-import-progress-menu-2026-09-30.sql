-- 客户中心新增「客户导入进度」独立子菜单，仅老板/超级管理员可见。
INSERT INTO t_menu (menu_name, path, component, menu_type, permission, sort_no, status, created_by)
SELECT '客户导入进度', '/client/import-tasks', 'views/client/ImportTasks', 'MENU', 'page:client', 126, 'ACTIVE', 'system'
WHERE NOT EXISTS (SELECT 1 FROM t_menu WHERE path = '/client/import-tasks');

UPDATE t_menu child
JOIN t_menu parent ON parent.path = '/domain/client'
SET child.parent_id = parent.id
WHERE child.path = '/client/import-tasks';

INSERT INTO t_role_menu (role_code, menu_id)
SELECT role_code, menu.id
FROM (SELECT 'BOSS' role_code UNION ALL SELECT 'SUPER_ADMIN' UNION ALL SELECT 'SUPER') roles
JOIN t_menu menu ON menu.path = '/client/import-tasks'
WHERE NOT EXISTS (
  SELECT 1 FROM t_role_menu existing
  WHERE existing.role_code = roles.role_code AND existing.menu_id = menu.id
);
