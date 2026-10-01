-- loan 测试组织/账号数据（幂等）
-- 只维护测试部门、员工、渠道账号，不删除客户、线索、预约、工单等业务数据。
SET NAMES utf8mb4;

INSERT INTO t_department (dept_code,dept_name,parent_code,sort,status,created_by) VALUES
 ('BOSS_DIRECT','老板直属',NULL,1,'ACTIVE','system'),('CONSULT','咨询部',NULL,2,'ACTIVE','system'),
 ('MARKET','市场部',NULL,3,'ACTIVE','system'),('OPERATION','运营部',NULL,4,'ACTIVE','system')
ON DUPLICATE KEY UPDATE dept_name=VALUES(dept_name),sort=VALUES(sort),status='ACTIVE',updated_by='system';

INSERT INTO t_staff (staff_code,crm_user_id,staff_name,dept_code,role_code,phone,phone_hash,status,created_by) VALUES
 ('BOSS001','crm-boss-001','张老板','BOSS_DIRECT','BOSS','13800000001',SHA2('13800000001',256),'ACTIVE','system'),
 ('ADV001','crm-adv-001','李顾问','CONSULT','ADVISER','13800000002',SHA2('13800000002',256),'ACTIVE','system'),
 ('DEPT001','crm-dept-001','王经理','CONSULT','DEPT_MANAGER','13800000003',SHA2('13800000003',256),'ACTIVE','system'),
 ('OP001','crm-op-001','赵运营','OPERATION','OPERATOR','13800000004',SHA2('13800000004',256),'ACTIVE','system'),
 ('SUP001','crm-sup-001','孙超管','BOSS_DIRECT','SUPER_ADMIN','13800000005',SHA2('13800000005',256),'ACTIVE','system'),
 ('SUPER001','crm-super-001','钱超级','BOSS_DIRECT','SUPER','13800000006',SHA2('13800000006',256),'ACTIVE','system')
ON DUPLICATE KEY UPDATE crm_user_id=VALUES(crm_user_id),staff_name=VALUES(staff_name),dept_code=VALUES(dept_code),role_code=VALUES(role_code),phone=VALUES(phone),phone_hash=VALUES(phone_hash),status='ACTIVE',updated_by='system';

INSERT INTO t_bank_channel (channel_code,bank_name,status,created_by) VALUES
 ('WH_BANK','武汉某商业银行','ACTIVE','system'),('HB_BANK','湖北某农商行','ACTIVE','system')
ON DUPLICATE KEY UPDATE bank_name=VALUES(bank_name),status='ACTIVE',updated_by='system';

INSERT INTO t_channel_user (bank_channel_id,phone,phone_hash,password,name,job_title,status,created_by)
SELECT id,'13911112222',SHA2('13911112222',256),'$2y$10$kUwWbSsyGWEvjLGpd2lnPeRLxgaXWrhs59uaaEVVOjSyJ8UwmKY5u','渠道-陈','渠道经理','ACTIVE','system'
 FROM t_bank_channel WHERE channel_code='WH_BANK'
 AND NOT EXISTS (SELECT 1 FROM t_channel_user WHERE phone_hash=SHA2('13911112222',256));
