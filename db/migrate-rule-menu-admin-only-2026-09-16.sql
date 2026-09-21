-- 规则配置属于平台经营配置，仅运营、老板和超级管理员可见可用。
DELETE rp FROM `t_role_permission` rp
JOIN `t_menu` m ON m.id = rp.menu_id
WHERE rp.role_code IN ('ADVISER', 'DEPT_MANAGER', 'CHANNEL')
  AND m.path IN ('/rule-template', '/plan-edit', '/strategy-template', '/rule');

DELETE FROM `t_role_api`
WHERE role_code IN ('ADVISER', 'DEPT_MANAGER', 'CHANNEL')
  AND (api_key LIKE 'rule:%'
    OR api_key LIKE 'rule-template:%'
    OR api_key LIKE 'strategy-template:%'
    OR api_key LIKE 'execution-plan:%');
