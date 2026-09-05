-- ============================================================
-- 接口级鉴权（网关）建表脚本
-- 表：t_api_permission（接口权限定义，运行时由 ApiPermissionSyncService 自动同步）
--     t_role_api（角色 × 接口授权，BOSS 全量不落库）
-- 规则下发：loan-service 启动/授权变更时写入 Redis
--   loan:api-perm:rules（全量 JSON） + loan:api-perm:version（版本号）
-- 网关：loan-gateway ApiAuthGlobalFilter 每请求按 角色×接口×端(WEB/MINI_APP) 校验
-- ============================================================

CREATE TABLE IF NOT EXISTS `t_api_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `api_key` varchar(128) NOT NULL COMMENT '接口权限键(模块:方法名,如 order:page)',
  `http_method` varchar(16) NOT NULL DEFAULT 'ALL' COMMENT 'HTTP方法(GET/POST/PUT/DELETE/ALL)',
  `path_pattern` varchar(255) NOT NULL COMMENT '路径模式(Spring pattern,如 /api/admin/order/{orderNo})',
  `module_group` varchar(32) DEFAULT NULL COMMENT '模块分组(客户经营/产品与规则/运营支撑/系统管理/公共)',
  `client_types` varchar(64) NOT NULL DEFAULT 'WEB,MINI_APP' COMMENT '可用端(WEB/MINI_APP,逗号分隔)',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态(ACTIVE/DISABLED)',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注(接口用途)',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_api_key` (`api_key`),
  KEY `idx_path` (`path_pattern`),
  KEY `idx_group` (`module_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='接口权限定义(网关鉴权清单,运行时自动同步)';

CREATE TABLE IF NOT EXISTS `t_role_api` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `role_code` varchar(32) NOT NULL COMMENT '角色编码(BOSS/DEPT_MANAGER/ADVISER/CHANNEL)',
  `api_key` varchar(128) NOT NULL COMMENT '接口权限键',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_api` (`role_code`,`api_key`),
  KEY `idx_role` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色接口授权(角色×接口;BOSS全量不落库)';
