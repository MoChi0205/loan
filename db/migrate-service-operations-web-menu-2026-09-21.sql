-- P2 客户服务 Web 菜单与角色授权（幂等）
-- 渠道角色完全排除；数据范围仍由后端按顾问本人/经理部门/管理角色全公司收口。
SET NAMES utf8mb4;

-- 兼容早期本地库：当前 Menu 实体会查询 customer_group，缺列会导致整棵菜单加载失败。
SET @has_customer_group := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_menu' AND COLUMN_NAME = 'customer_group'
);
SET @add_customer_group_sql := IF(
  @has_customer_group = 0,
  'ALTER TABLE t_menu ADD COLUMN customer_group varchar(16) DEFAULT ''COMMON'' COMMENT ''客群维度'' AFTER permission_code',
  'SELECT 1'
);
PREPARE add_customer_group_stmt FROM @add_customer_group_sql;
EXECUTE add_customer_group_stmt;
DEALLOCATE PREPARE add_customer_group_stmt;

-- 今日服务台按业务编码读取活跃工单；早期本地库未执行业务编码迁移时补列并回填。
SET @has_order_client_code := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_service_order' AND COLUMN_NAME = 'client_profile_code'
);
SET @add_order_client_code_sql := IF(
  @has_order_client_code = 0,
  'ALTER TABLE t_service_order ADD COLUMN client_profile_code varchar(64) DEFAULT NULL COMMENT ''客户业务编码'' AFTER client_profile_id',
  'SELECT 1'
);
PREPARE add_order_client_code_stmt FROM @add_order_client_code_sql;
EXECUTE add_order_client_code_stmt;
DEALLOCATE PREPARE add_order_client_code_stmt;

SET @has_order_owner_code := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_service_order' AND COLUMN_NAME = 'owner_staff_code'
);
SET @add_order_owner_code_sql := IF(
  @has_order_owner_code = 0,
  'ALTER TABLE t_service_order ADD COLUMN owner_staff_code varchar(32) DEFAULT NULL COMMENT ''顾问员工工号'' AFTER owner_staff_id',
  'SELECT 1'
);
PREPARE add_order_owner_code_stmt FROM @add_order_owner_code_sql;
EXECUTE add_order_owner_code_stmt;
DEALLOCATE PREPARE add_order_owner_code_stmt;

UPDATE t_service_order orders
LEFT JOIN t_client_profile clients ON clients.id = orders.client_profile_id
SET orders.client_profile_code = clients.client_code
WHERE orders.client_profile_code IS NULL;
UPDATE t_service_order orders
LEFT JOIN t_staff staff ON staff.id = orders.owner_staff_id
SET orders.owner_staff_code = staff.staff_code
WHERE orders.owner_staff_code IS NULL;

INSERT INTO t_menu
  (parent_id, menu_name, path, component, menu_type, permission_code, sort, status, created_by)
SELECT (SELECT p.id FROM t_menu p WHERE p.path = '/domain/service' LIMIT 1),
       '客户服务', '/service-operations', 'views/service/ServiceOperations',
       'MENU', 'page:service-operations', 290, 'ACTIVE', 'system'
WHERE NOT EXISTS (SELECT 1 FROM t_menu m WHERE m.path = '/service-operations');

UPDATE t_menu m
JOIN t_menu p ON p.path = '/domain/service'
SET m.parent_id = p.id,
    m.menu_name = '客户服务',
    m.component = 'views/service/ServiceOperations',
    m.permission_code = 'page:service-operations',
    m.sort = 290,
    m.status = 'ACTIVE'
WHERE m.path = '/service-operations';

-- 旧表唯一键含可空 permission_code，MySQL 允许多条 NULL；显式 NOT EXISTS 才能保证幂等。
DELETE duplicate_grant
FROM t_role_permission duplicate_grant
JOIN t_role_permission retained
  ON retained.role_code = duplicate_grant.role_code
 AND retained.menu_id = duplicate_grant.menu_id
 AND retained.id < duplicate_grant.id
JOIN t_menu m ON m.id = duplicate_grant.menu_id
WHERE m.path = '/service-operations'
  AND duplicate_grant.permission_code IS NULL
  AND retained.permission_code IS NULL;

INSERT INTO t_role_permission (role_code, menu_id, created_by)
SELECT roles.role_code, m.id, 'system'
FROM (
  SELECT 'ADVISER' AS role_code UNION ALL
  SELECT 'DEPT_MANAGER' UNION ALL
  SELECT 'BOSS' UNION ALL
  SELECT 'OPERATOR' UNION ALL
  SELECT 'SUPER_ADMIN' UNION ALL
  SELECT 'SUPER'
) roles
JOIN t_menu m ON m.path = '/service-operations'
WHERE NOT EXISTS (
  SELECT 1 FROM t_role_permission existing
  WHERE existing.role_code = roles.role_code
    AND existing.menu_id = m.id
    AND existing.permission_code IS NULL
);

-- 防御性清理：渠道账号不得通过历史误授权看到菜单。
DELETE rp
FROM t_role_permission rp
JOIN t_menu m ON m.id = rp.menu_id
WHERE rp.role_code = 'CHANNEL'
  AND m.path = '/service-operations';

SELECT m.id, m.path, m.parent_id, GROUP_CONCAT(rp.role_code ORDER BY rp.role_code) AS roles
FROM t_menu m
LEFT JOIN t_role_permission rp ON rp.menu_id = m.id
WHERE m.path = '/service-operations'
GROUP BY m.id, m.path, m.parent_id;
