-- 本地/测试环境登录账号种子（2026-09-21）
--
-- 登录入口：Web 管理端 → 账号类型「公司员工」或「渠道合作方」
--   登录账号 = 手机号（不是工号）；密码统一为 Test123456
--   Web 默认登录方式：账号密码 + 随机 4 位验证码（图片验证码）
--
-- | 角色                | 工号      | 姓名   | 部门         | 登录手机号   | 密码       |
-- |-------------------|----------|--------|--------------|--------------|-----------|
-- | BOSS 老板          | BOSS001  | 张老板 | BOSS_DIRECT  | 13800000001  | Test123456 |
-- | ADVISER 顾问       | ADV001   | 李顾问 | CONSULT      | 13800000002  | Test123456 |
-- | ADVISER 顾问(备)   | ADV002   | 周顾问 | CONSULT      | 13800000007  | Test123456 |
-- | DEPT_MANAGER 经理  | DEPT001  | 王经理 | CONSULT      | 13800000003  | Test123456 |
-- | OPERATOR 运营      | OP001    | 赵运营 | OPERATION    | 13800000004  | Test123456 |
-- | SUPER_ADMIN 超管   | SUP001   | 孙超管 | BOSS_DIRECT  | 13800000005  | Test123456 |
-- | SUPER 超级管理员   | SUPER001 | 钱超级 | BOSS_DIRECT  | 13800000006  | Test123456 |
-- | CHANNEL 渠道合作方 | —        | 渠道-陈| 本地开发渠道 | 13911112222  | Test123456 |
--
-- 说明：
--   1. password 列是 Test123456 的 BCrypt 哈希（已用 BCryptPasswordEncoder.matches 验证通过），可直接复用；
--   2. phone 列是 AES/CBC/PKCS5Padding 密文（密钥 = SHA-256(aes.key)，默认 loan-aes-key-2026-dev；随机 IV 前缀 + Base64）；
--      若部署环境覆盖了 aes.key，需按新密钥重新生成 phone 密文，否则登录页可登录但手机号显示为异常；
--   3. phone_hash = SHA-256(手机号明文) 十六进制，登录按此列等值查询，与密文无关；
--   4. 全部 ON DUPLICATE KEY UPDATE，可重复执行。

SET NAMES utf8mb4;

INSERT INTO t_role (role_code, role_name, description, status, created_by) VALUES
('BOSS','老板','公司老板（全量数据）','ACTIVE','local-seed'),
('ADVISER','顾问','一线业务顾问（本人数据）','ACTIVE','local-seed'),
('DEPT_MANAGER','部门经理','本部门数据 + 主管审核','ACTIVE','local-seed'),
('OPERATOR','运营','运营（全量数据）','ACTIVE','local-seed'),
('SUPER_ADMIN','超管','超级管理员','ACTIVE','local-seed'),
('SUPER','超级管理员','超级管理员（全量角色）','ACTIVE','local-seed'),
('CHANNEL','渠道合作方','渠道合作方（仅本人录入）','ACTIVE','local-seed')
ON DUPLICATE KEY UPDATE role_name=VALUES(role_name), status='ACTIVE';

INSERT INTO t_staff
(staff_code, crm_user_id, staff_name, dept_code, role_code, phone, phone_hash, password, status, created_by)
VALUES
('BOSS001','crm-boss-001','张老板','BOSS_DIRECT','BOSS','dIG08V+5XqNOHPerQmcvDn//tGrn5wzRqXo2CWh5QdU=','b1c4769e3ad14f68ea1a96b73bbe5a83d90792f044828c5daf9d908b0738b177','$2b$10$YnHnfQgwCSqqn8rqobPHle/tjxRKbUIj4Holxe9Y4Xx9r8qmHytz.','ACTIVE','local-seed'),
('ADV001','crm-adv-001','李顾问','CONSULT','ADVISER','6aR8JVTS4wDS9CrXfVrQkK6g6Ch09yNYUU9OIoCm51Y=','64ac4842ab2fa04b62673f8418af7648def08bb6f2d09b5452b544007f4bf1ed','$2b$10$YnHnfQgwCSqqn8rqobPHle/tjxRKbUIj4Holxe9Y4Xx9r8qmHytz.','ACTIVE','local-seed'),
('ADV002','crm-adv-002','周顾问','CONSULT','ADVISER','LWd5k9xKeRBNsY0HOspaO8IkmKAhweghKO1jAMy1YK4=','4798246a00b7a3c9fbf1fc5bca26bec64ebc7404f98add35439e29fbcad12019','$2b$10$YnHnfQgwCSqqn8rqobPHle/tjxRKbUIj4Holxe9Y4Xx9r8qmHytz.','ACTIVE','local-seed'),
('DEPT001','crm-dept-001','王经理','CONSULT','DEPT_MANAGER','iHT5xSjSaJdldY6LXS2FShzf4WNRJkSExyQHV/DqzeU=','338ecb183e97f2bd1b49116c92c4e4924102d46c1a1f87b5eff8a3841a51bc88','$2b$10$YnHnfQgwCSqqn8rqobPHle/tjxRKbUIj4Holxe9Y4Xx9r8qmHytz.','ACTIVE','local-seed'),
('OP001','crm-op-001','赵运营','OPERATION','OPERATOR','iUdUxKGnpiUPmtOpSonlxzsCrcWPwcf8sVAXcFYZxG0=','59daa874ce11eff1abfcdc63e2042f80e8b84e1c713780ec1ee6a5dd52bcf85d','$2b$10$YnHnfQgwCSqqn8rqobPHle/tjxRKbUIj4Holxe9Y4Xx9r8qmHytz.','ACTIVE','local-seed'),
('SUP001','crm-sup-001','孙超管','BOSS_DIRECT','SUPER_ADMIN','+1jJgnG6/VnO3rtnexh4FnV4DSC5huwUdiu1/16EXbw=','0ef33fa23f167c58ef18f52c1acd0960bcd09c2ef1a98d30f52ebdbf0324c98e','$2b$10$YnHnfQgwCSqqn8rqobPHle/tjxRKbUIj4Holxe9Y4Xx9r8qmHytz.','ACTIVE','local-seed'),
('SUPER001','crm-super-001','钱超级','BOSS_DIRECT','SUPER','zjG1wtTGUGp1xaLSsHGhCEe+yXriPyzZtGD2lbzCMFc=','86775f5019c4ca787eec77ee25bc73a23205218716e9e8c4530532e17d09fea8','$2b$10$YnHnfQgwCSqqn8rqobPHle/tjxRKbUIj4Holxe9Y4Xx9r8qmHytz.','ACTIVE','local-seed')
ON DUPLICATE KEY UPDATE
staff_name=VALUES(staff_name), dept_code=VALUES(dept_code), role_code=VALUES(role_code),
phone=VALUES(phone), phone_hash=VALUES(phone_hash), password=VALUES(password), status='ACTIVE';

INSERT INTO t_bank_channel (channel_code, bank_name, status, remark, created_by)
VALUES ('LOCAL_DEV_CHANNEL','本地开发渠道','ACTIVE','仅本地认证联调使用','local-seed')
ON DUPLICATE KEY UPDATE status='ACTIVE', bank_name=VALUES(bank_name);

INSERT INTO t_channel_user
(bank_channel_id, phone, phone_hash, password, name, job_title, register_type, status, created_by)
SELECT id,'ocIvRUwZdl2bcjNvMbdQbqJkdsOkNB3w1Bf0X4zXHaI=','b79f5a757cfaaaeaf5840212f104a86947d84ef747109767919a539992a46d7d',
'$2b$10$YnHnfQgwCSqqn8rqobPHle/tjxRKbUIj4Holxe9Y4Xx9r8qmHytz.','渠道-陈','渠道经理','LOCAL','ACTIVE','local-seed'
FROM t_bank_channel WHERE channel_code='LOCAL_DEV_CHANNEL'
ON DUPLICATE KEY UPDATE bank_channel_id=VALUES(bank_channel_id), phone=VALUES(phone),
password=VALUES(password), name=VALUES(name), status='ACTIVE';
