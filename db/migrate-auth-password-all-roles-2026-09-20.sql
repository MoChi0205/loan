-- 全角色密码登录与验证码找回密码。
-- 可重复执行前请先确认列不存在；生产执行须经数据库变更审批。
ALTER TABLE `t_staff`
  ADD COLUMN `password` varchar(128) DEFAULT NULL COMMENT '密码(BCrypt，验证码重置后设置)' AFTER `phone_hash`;

ALTER TABLE `t_client_profile`
  ADD COLUMN `password` varchar(128) DEFAULT NULL COMMENT '密码(BCrypt，验证码重置后设置)' AFTER `phone_hash`;

