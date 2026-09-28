-- 开发环境测试数据重置：仅用于当前 loan_db 测试库。
-- 不改表结构；清理账号/角色/菜单/权限及运营业务历史记录，随后由种子脚本重建。
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS=0;

-- 账号、角色、菜单、接口授权
DELETE FROM t_role_permission;
DELETE FROM t_role_api;
DELETE FROM t_api_permission;
DELETE FROM t_staff;
DELETE FROM t_channel_user;
DELETE FROM t_role;
DELETE FROM t_menu;
DELETE FROM t_department;

-- 渠道/产品测试数据（渠道代码与业务产品由后续种子重建）
DELETE FROM t_partner_product;
DELETE FROM t_product_approval;
DELETE FROM t_bank_product_city;
DELETE FROM t_bank_product;
DELETE FROM t_bank_channel;

-- 客户、线索、报告、匹配、材料与经营链路历史数据
DELETE FROM t_attachment_download_log;
DELETE FROM t_attachment_download_approval;
DELETE FROM t_service_attachment;
DELETE FROM t_ocr_record;
DELETE FROM t_material_review;
DELETE FROM t_client_screening;
DELETE FROM t_screening_product;
DELETE FROM t_match_rule_log;
DELETE FROM t_match_trace;
DELETE FROM t_client_submission;
DELETE FROM t_personal_auth;
DELETE FROM t_personal_profile;
DELETE FROM t_client_authorization;
DELETE FROM t_client_business_fact;
DELETE FROM t_client_insight_snapshot;
DELETE FROM t_client_activity_event;
DELETE FROM t_client_follow_record;
DELETE FROM t_staff_outing;
DELETE FROM t_client_appointment;
DELETE FROM t_service_follow;
DELETE FROM t_service_order;
DELETE FROM t_client_allocation_approval;
DELETE FROM t_lead_allocation_record;
DELETE FROM t_lead_archive;
DELETE FROM t_lead_ent_ext;
DELETE FROM t_lead_person_ext;
DELETE FROM t_lead;
DELETE FROM t_client_lifecycle_event;
DELETE FROM t_client_profile;

-- 测试运行痕迹、通知与奖励/邀请历史
DELETE FROM t_operation_log;
DELETE FROM t_notification;
DELETE FROM t_invitation;
DELETE FROM t_reward_record;
DELETE FROM t_withdraw_record;

SET FOREIGN_KEY_CHECKS=1;
