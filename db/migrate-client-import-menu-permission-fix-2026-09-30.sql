-- 将历史导入进度菜单补齐到当前 t_role_permission 菜单真值表。
INSERT IGNORE INTO t_role_permission(role_code, menu_id, permission_code, created_by)
SELECT roles.role_code,m.id,NULL,'system'
FROM (SELECT 'BOSS' role_code UNION ALL SELECT 'SUPER_ADMIN' UNION ALL SELECT 'SUPER') roles
JOIN t_menu m ON m.path='/client/import-tasks' AND m.status='ACTIVE';
UPDATE t_menu child JOIN t_menu parent ON parent.path='/domain/client'
SET child.parent_id=parent.id WHERE child.path='/client/import-tasks';
