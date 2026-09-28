-- 开发环境角色/菜单/客户链路测试种子。
SET NAMES utf8mb4;

-- 部门（员工数据范围：顾问本人、经理部门、老板/运营/超管全司）
INSERT INTO t_department (dept_code,dept_name,parent_code,sort,status,created_by) VALUES
('BOSS_DIRECT','老板直属',NULL,1,'ACTIVE','test-seed'),
('CONSULT','咨询部',NULL,2,'ACTIVE','test-seed'),
('OPERATION','运营部',NULL,3,'ACTIVE','test-seed')
ON DUPLICATE KEY UPDATE dept_name=VALUES(dept_name),status='ACTIVE';

-- 渠道与内部产品：渠道表仅用于渠道隔离测试，不改变渠道业务代码。
INSERT INTO t_bank_channel (channel_code,bank_name,status,remark,created_by) VALUES
('LOCAL_DEV_CHANNEL','本地开发渠道','ACTIVE','角色与数据范围联调','test-seed')
ON DUPLICATE KEY UPDATE bank_name=VALUES(bank_name),status='ACTIVE';
INSERT INTO t_bank_product (product_code,bank_channel_code,product_name,customer_group,source,amount_min,amount_max,rate_min,rate_max,term_min,term_max,status,created_by) VALUES
('TEST_ENTERPRISE_PRODUCT','LOCAL_DEV_CHANNEL','测试企业经营分析产品','ENTERPRISE','OURS',100000,1000000,0.0400,0.0800,6,36,'APPROVED','test-seed')
ON DUPLICATE KEY UPDATE status='APPROVED',bank_channel_code='LOCAL_DEV_CHANNEL';
INSERT INTO t_partner_product (bank_product_code,cooperate_until,status,created_by)
VALUES ('TEST_ENTERPRISE_PRODUCT','2027-12-31 23:59:59','ACTIVE','test-seed')
ON DUPLICATE KEY UPDATE status='ACTIVE';

-- 两个客户：一个咨询部顾问名下，一个进入企业公海，用于顾问/经理/全司数据范围验证。
INSERT INTO t_client_profile
(client_code,customer_group,enterprise_name,contact_name,phone_hash,owner_staff_code,source,sea_level,sea_dept_code,status,created_by)
VALUES
('client_test_consult_001','ENTERPRISE','测试咨询企业一','测试联系人一',SHA2('13800000901',256),'ADV001','OURS',NULL,NULL,'ACTIVE','test-seed'),
('client_test_sea_001','ENTERPRISE','测试公海企业二','测试联系人二',SHA2('13800000902',256),NULL,'OURS','ENTERPRISE',NULL,'ACTIVE','test-seed')
ON DUPLICATE KEY UPDATE enterprise_name=VALUES(enterprise_name),owner_staff_code=VALUES(owner_staff_code),status='ACTIVE';

INSERT INTO t_client_lifecycle_event
(client_code,event_type,staff_code,sea_level,episode_no,event_at,operator_staff_code,reason_code)
VALUES
('client_test_consult_001','ASSIGNED','ADV001',NULL,1,NOW(),'ADV001','TEST_SEED'),
('client_test_sea_001','ENTER_COMPANY_SEA',NULL,'ENTERPRISE',1,NOW(),'SUP001','TEST_SEED');

INSERT INTO t_lead
(lead_no,lead_type,contact_name,phone_hash,phone,source,recorder_staff_code,owner_staff_code,follow_status,client_profile_code,created_by)
VALUES
('lead_test_001','ENTERPRISE','测试联系人一',SHA2('13800000901',256),'13800000901','ADVISER','ADV001','ADV001','FOLLOWING','client_test_consult_001','ADV001'),
('lead_test_002','ENTERPRISE','测试联系人二',SHA2('13800000902',256),'13800000902','BOSS','BOSS001',NULL,'NEW','client_test_sea_001','BOSS001');

INSERT INTO t_client_appointment
(appointment_no,client_code,host_staff_code,appointment_type,scheduled_start,scheduled_end,location_name,status,customer_confirm_status,created_by_type,created_by_code,source_terminal,created_by)
VALUES
('appt_test_001','client_test_consult_001','ADV001','COMPANY_ON_SITE',DATE_ADD(NOW(),INTERVAL 1 DAY),DATE_ADD(NOW(),INTERVAL 1 DAY)+INTERVAL 1 HOUR,'公司现场','CONFIRMED','CONFIRMED','STAFF','ADV001','WEB','ADV001');

INSERT INTO t_staff_outing
(outing_no,staff_code,client_code,appointment_no,outing_type,planned_start,planned_end,destination,purpose,status,submitted_at,created_by)
VALUES
('outing_test_001','ADV001','client_test_consult_001','appt_test_001','HOME_VISIT',DATE_ADD(NOW(),INTERVAL 2 DAY),DATE_ADD(NOW(),INTERVAL 2 DAY)+INTERVAL 2 HOUR,'测试客户现场','经营资料沟通','PENDING_REVIEW',NOW(),'ADV001');

INSERT INTO t_client_follow_record
(follow_no,client_code,appointment_no,staff_code,channel_type,result_code,content,customer_visible_summary,next_action,next_follow_at,visibility,created_by)
VALUES
('follow_test_001','client_test_consult_001','appt_test_001','ADV001','PHONE','INTERESTED','已完成首次电话咨询，待客户补充经营资料','已完成咨询，后续将协助整理材料','提醒客户补充材料',DATE_ADD(NOW(),INTERVAL 2 DAY),'STAFF_ONLY','ADV001');

INSERT INTO t_client_activity_event
(event_no,client_code,staff_code,event_type,source_type,source_no,happened_at,summary,visibility,actor_type,actor_code)
VALUES
('event_test_001','client_test_consult_001','ADV001','FOLLOW_UP','FOLLOW_RECORD','follow_test_001',NOW(),'顾问完成首次电话咨询','STAFF_ONLY','STAFF','ADV001');

INSERT INTO t_client_screening
(report_no,client_profile_code,template_code,grade,bank_count,product_count,pass_count,condition_count,reject_count,advice_json,status)
VALUES
('report_test_001','client_test_consult_001','ENTERPRISE_ANALYSIS_V1','MIDDLE',0,0,0,0,0,
 JSON_OBJECT('summary','经营资料待补充','nextActions',JSON_ARRAY('补充近12个月经营流水','核对企业基础信息')),'GENERATED');

-- 让每个角色都存在明确的测试账号，密码统一 Test123456（种子脚本写入 BCrypt）。
