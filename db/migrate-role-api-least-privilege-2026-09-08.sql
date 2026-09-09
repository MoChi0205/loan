-- D69：ADVISER / DEPT_MANAGER 接口权限最小化迁移。
-- 仅删除历史由 system 自动播种且已被现行角色矩阵明确禁止的权限；不覆盖人工配置的其他权限。
-- 可重复执行：DELETE 对不存在的授权无副作用。

START TRANSACTION;

DELETE FROM t_role_api
WHERE role_code = 'ADVISER'
  AND created_by = 'system'
  AND api_key IN (
    'client:assign', 'client:claim', 'client:recycle', 'client:unassignedPage',
    'lead:assign', 'lead:batchAssign', 'lead:batchDelete', 'lead:recycle', 'lead:warnRecycle',
    'notification:send',
    'report:page', 'report:save', 'report:toggle',
    'sms:recordPage', 'sms:saveTemplate', 'sms:send', 'sms:templateList',
    'sms:templatePage', 'sms:toggleTemplate'
  );

DELETE FROM t_role_api
WHERE role_code = 'DEPT_MANAGER'
  AND created_by = 'system'
  AND (
    api_key IN (
      'approval:channelLeadAudit', 'approval:channelLeadPage',
      'approval:productAudit', 'approval:productDetail', 'approval:productPage',
      'blacklist:add', 'blacklist:page', 'blacklist:release',
      'org:disableDepartment', 'org:disableStaff', 'org:menuTree',
      'org:saveDepartment', 'org:saveRolePermission', 'org:saveStaff'
    )
    OR api_key LIKE 'channel:%'
    OR api_key LIKE 'channel-strategy:%'
    OR api_key LIKE 'partner-product:%'
  );

COMMIT;

-- 回读建议：结果应为空。
SELECT role_code, api_key
FROM t_role_api
WHERE (role_code = 'ADVISER' AND api_key IN (
         'client:assign', 'client:recycle', 'lead:assign', 'lead:batchAssign',
         'lead:batchDelete', 'notification:send', 'report:save', 'sms:send'
       ))
   OR (role_code = 'DEPT_MANAGER' AND (
         api_key IN ('approval:productAudit', 'approval:productDetail', 'approval:productPage',
                     'approval:channelLeadAudit', 'approval:channelLeadPage',
                     'org:saveRolePermission', 'blacklist:add')
         OR api_key LIKE 'channel:%'
         OR api_key LIKE 'channel-strategy:%'
         OR api_key LIKE 'partner-product:%'
       ));
