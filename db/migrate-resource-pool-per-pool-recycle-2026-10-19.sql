-- ============================================================
-- 资源池按池回收规则（参考 tse t_resource_pool 内嵌回收字段）+「运营管理」菜单域
-- 日期：2026-10-19
-- 背景：
--   1) 回收规则此前按 COMPANY/TEAM/STAFF 范围全局解析，一条配置控制所有池；
--      对齐 tse 后改为规则内嵌在每个资源池上（一个池一条规则）。
--   2) 资源池创建/维护角色收紧为 部门经理/老板/超级管理员（tse 为 ADMIN/MANAGER）。
--   3) 「客户导入进度 / 资源池回收规则 / 认领设置」从「客户中心」移入独立「运营管理」域。
-- 幂等：可重复执行。
-- ============================================================
SET NAMES utf8mb4;

-- ---------- 1. t_resource_pool 增加回收规则列（MySQL 8 无 ADD COLUMN IF NOT EXISTS，用 information_schema 守卫） ----------
SET @add_cols := (
  SELECT CONCAT(
    IF(@c1 := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
               AND TABLE_NAME = 't_resource_pool' AND COLUMN_NAME = 'recycle_enabled') = 0,
       ', ADD COLUMN recycle_enabled TINYINT NOT NULL DEFAULT 1 COMMENT ''自动回收开关(1=开启;参考tse auto_recycle_enabled)'' AFTER status', ''),
    IF(@c2 := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
               AND TABLE_NAME = 't_resource_pool' AND COLUMN_NAME = 'recycle_days') = 0,
       ', ADD COLUMN recycle_days INT DEFAULT 30 COMMENT ''默认回收天数'' AFTER recycle_enabled', ''),
    IF(@c3 := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
               AND TABLE_NAME = 't_resource_pool' AND COLUMN_NAME = 'warn_days') = 0,
       ', ADD COLUMN warn_days INT DEFAULT 15 COMMENT ''提前预警天数'' AFTER recycle_days', ''),
    IF(@c4 := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
               AND TABLE_NAME = 't_resource_pool' AND COLUMN_NAME = 'cooldown_days') = 0,
       ', ADD COLUMN cooldown_days INT DEFAULT 7 COMMENT ''回收冷却天数'' AFTER warn_days', ''),
    IF(@c5 := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
               AND TABLE_NAME = 't_resource_pool' AND COLUMN_NAME = 'status_days_json') = 0,
       ', ADD COLUMN status_days_json VARCHAR(2000) DEFAULT NULL COMMENT ''按跟进状态的无活跃回收天数JSON'' AFTER cooldown_days', '')
  ));
SET @ddl := IF(CHAR_LENGTH(@add_cols) = 0, 'DO 0',
  CONCAT('ALTER TABLE t_resource_pool', SUBSTRING(@add_cols, 2)));
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 2. 存量池种子：从旧 t_resource_pool_config 迁移规则值（仅更新仍为默认值的行，可重复执行） ----------
UPDATE t_resource_pool p
JOIN t_resource_pool_config c ON c.pool_code IN ('COMPANY', 'POOL_COMPANY')
SET p.recycle_enabled = c.recycle_enabled,
    p.recycle_days    = COALESCE(c.recycle_days, 30),
    p.warn_days       = COALESCE(c.warn_days, 15),
    p.cooldown_days   = COALESCE(c.cooldown_days, 7),
    p.status_days_json = c.status_days_json,
    p.updated_at = NOW()
WHERE p.pool_code = 'POOL_COMPANY'
  AND p.recycle_days = 30 AND p.warn_days = 15 AND p.cooldown_days = 7 AND p.status_days_json IS NULL;

-- 团队池沿用同一套默认状态天数（NEW 30 / NO_ANSWER 15 / POTENTIAL 30 / INTENTION 30 / VISITED 45）
UPDATE t_resource_pool
SET status_days_json = '{"NEW":30,"NO_ANSWER":15,"POTENTIAL":30,"INTENTION":30,"VISITED":45}',
    updated_at = NOW()
WHERE pool_code = 'POOL_TEAM' AND status_days_json IS NULL;

-- ---------- 3. 「运营管理」独立菜单域，三个配置页从「客户中心」迁入 ----------
INSERT INTO t_menu (menu_name, path, component, menu_type, permission_code, sort, status, created_by)
SELECT '运营管理', '/domain/ops', NULL, 'MENU', 'domain:ops', 450, 'ACTIVE', 'system'
WHERE NOT EXISTS (SELECT 1 FROM t_menu WHERE path = '/domain/ops');

UPDATE t_menu m JOIN t_menu p ON p.path = '/domain/ops' SET m.parent_id = p.id, m.sort = m.sort
 WHERE m.path IN ('/client/import-tasks', '/client/resource-pool-config', '/allocation-quota');

-- 「运营管理」父级授权：老板/运营/超管/超级管理员 + 部门经理（经理可维护资源池）
INSERT IGNORE INTO t_role_permission (role_code, menu_id, permission_code, created_by)
SELECT roles.role_code, menu.id, NULL, 'system'
FROM (SELECT 'BOSS' role_code UNION ALL SELECT 'OPERATOR' UNION ALL SELECT 'SUPER_ADMIN'
      UNION ALL SELECT 'SUPER' UNION ALL SELECT 'DEPT_MANAGER') roles
JOIN t_menu menu ON menu.path = '/domain/ops';

-- 资源池回收规则页：补授部门经理（经理可按池维护回收规则）
INSERT IGNORE INTO t_role_permission (role_code, menu_id, permission_code, created_by)
SELECT 'DEPT_MANAGER', menu.id, NULL, 'system'
FROM t_menu menu WHERE menu.path = '/client/resource-pool-config';

-- ---------- 4. 接口权限说明（无需 SQL） ----------
-- 资源池接口 api_key（resource-pool:page/create/toggle/updateRecycle）由启动期
-- ApiPermissionSyncService 自动登记；DEPT_MANAGER 默认矩阵已补上述键，
-- 重启 loan-service 后 backfillRoleApis 自动补齐授权，网关重载规则即生效。
-- 旧 t_resource_pool_config 表保留作迁移痕迹，不再被代码读写。
