-- 角色菜单边界最小增量：BOSS 是业务全量/审批角色，不是系统配置管理员。
-- 幂等：重复删除不存在的角色菜单关系不会报错。
DELETE rp
FROM t_role_permission rp
INNER JOIN t_menu m ON m.id = rp.menu_id
WHERE rp.role_code = 'BOSS'
  AND m.path IN ('/org', '/config-wizard', '/debug');
