-- 经营报表入口对所有公司员工开放；页面名称与数据范围由登录角色动态决定。
INSERT INTO `t_role_permission` (`role_code`, `menu_id`, `created_by`)
SELECT r.role_code, m.id, 'system'
FROM (
  SELECT 'BOSS' role_code UNION ALL SELECT 'SUPER_ADMIN' UNION ALL SELECT 'SUPER'
  UNION ALL SELECT 'OPERATOR' UNION ALL SELECT 'DEPT_MANAGER' UNION ALL SELECT 'ADVISER'
) r
JOIN `t_menu` m ON m.path = '/report/center'
WHERE NOT EXISTS (
  SELECT 1 FROM `t_role_permission` rp
  WHERE rp.role_code = r.role_code AND rp.menu_id = m.id
);
