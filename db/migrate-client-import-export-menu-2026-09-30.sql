INSERT INTO t_menu (menu_name,path,component,menu_type,permission_code,sort,status,created_by)
SELECT '客户导入与导出','/client/import-export','views/client/ClientImportExport','MENU','page:client',128,'ACTIVE','system'
WHERE NOT EXISTS (SELECT 1 FROM t_menu WHERE path='/client/import-export');
UPDATE t_menu child JOIN t_menu parent ON parent.path='/domain/client' SET child.parent_id=parent.id WHERE child.path='/client/import-export';
INSERT IGNORE INTO t_role_permission(role_code,menu_id,permission_code,created_by)
SELECT roles.role_code,m.id,NULL,'system' FROM (SELECT 'BOSS' role_code UNION ALL SELECT 'SUPER_ADMIN' UNION ALL SELECT 'SUPER') roles JOIN t_menu m ON m.path='/client/import-export';
