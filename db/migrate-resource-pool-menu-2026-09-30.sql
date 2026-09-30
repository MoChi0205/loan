INSERT INTO t_menu (menu_name,path,component,menu_type,permission_code,sort,status,created_by)
SELECT '资源池回收规则','/client/resource-pool-config','views/client/ResourcePoolConfig','MENU','page:client',127,'ACTIVE','system'
WHERE NOT EXISTS (SELECT 1 FROM t_menu WHERE path='/client/resource-pool-config');

UPDATE t_menu child JOIN t_menu parent ON parent.path='/domain/client'
SET child.parent_id=parent.id WHERE child.path='/client/resource-pool-config';

INSERT IGNORE INTO t_role_permission (role_code, menu_id, permission_code, created_by)
SELECT roles.role_code, menu.id, NULL, 'system'
FROM (SELECT 'BOSS' role_code UNION ALL SELECT 'OPERATOR' UNION ALL SELECT 'SUPER_ADMIN' UNION ALL SELECT 'SUPER') roles
JOIN t_menu menu ON menu.path='/client/resource-pool-config';
