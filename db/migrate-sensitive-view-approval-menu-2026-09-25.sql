-- 手机号查看超额审批菜单及角色权限（幂等）。
SET NAMES utf8mb4;
INSERT INTO t_menu (menu_name, path, component, menu_type, permission_code, sort, status, created_by)
SELECT '手机号查看审批', '/approval/sensitive-phone', 'views/approval/ApprovalCenter', 'MENU', NULL, 45, 'ACTIVE', 'system'
WHERE NOT EXISTS (SELECT 1 FROM t_menu WHERE path='/approval/sensitive-phone');

UPDATE t_menu child JOIN t_menu parent ON parent.path='/approval'
SET child.parent_id=parent.id, child.status='ACTIVE', child.menu_name='手机号查看审批'
WHERE child.path='/approval/sensitive-phone';

INSERT INTO t_role_permission (role_code, menu_id, created_by)
SELECT roles.role_code, m.id, 'system'
FROM (SELECT 'DEPT_MANAGER' role_code UNION ALL SELECT 'BOSS' UNION ALL SELECT 'SUPER_ADMIN' UNION ALL SELECT 'SUPER') roles
JOIN t_menu m ON m.path='/approval/sensitive-phone'
WHERE NOT EXISTS (SELECT 1 FROM t_role_permission rp WHERE rp.role_code=roles.role_code AND rp.menu_id=m.id);

-- 接口权限采用 Controller 模块生成的实际 api_key（SensitiveViewController 归属 lead 模块）。
-- 顾问只可发起客户手机号查看；部门经理只可查看/审批本部门超额申请。
INSERT INTO t_role_api (role_code, api_key, created_by, created_at)
SELECT roles.role_code, 'lead:applyClientView', 'system', NOW()
FROM (SELECT 'ADVISER' role_code UNION ALL SELECT 'OPERATOR' UNION ALL SELECT 'DEPT_MANAGER') roles
WHERE EXISTS (SELECT 1 FROM t_api_permission WHERE api_key='lead:applyClientView' AND status='ACTIVE')
  AND NOT EXISTS (SELECT 1 FROM t_role_api WHERE role_code=roles.role_code AND api_key='lead:applyClientView');

INSERT INTO t_role_api (role_code, api_key, created_by, created_at)
SELECT roles.role_code, apis.api_key, 'system', NOW()
FROM (SELECT 'DEPT_MANAGER' role_code) roles
JOIN (SELECT 'lead:pendingApprovals' api_key UNION ALL SELECT 'lead:auditApproval') apis
JOIN t_api_permission p ON p.api_key=apis.api_key AND p.status='ACTIVE'
WHERE NOT EXISTS (
  SELECT 1 FROM t_role_api ra WHERE ra.role_code=roles.role_code AND ra.api_key=apis.api_key
);
