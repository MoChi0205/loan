-- ============================================================
-- loan_db 贷款咨询产品系统 完整 DDL（63 张表）
-- 生成时间: 2026-08-25（第二十轮工程蓝图定稿）
-- 依据: 方案评审定稿纪要（19 轮 87 项定稿 + 本轮 VIP 域补齐）
-- 规范: 对齐 tse 项目 docs2/数据库/字符集与DDL规范.md
--   全库 utf8mb4 + utf8mb4_0900_ai_ci（禁止 unicode_ci 混用）
--   脚本头 SET NAMES utf8mb4；执行加 --default-character-set=utf8mb4
-- 审计: 全表带 created_by/updated_by(存操作人姓名)/created_at/updated_at
--   由 MyBatis-Plus CustomMetaObjectHandler 自动填充（复用 tse 模式）
-- 加密: 手机号/身份证/税号/执照号等敏感列存 AES 密文，另建 *_hash 列(SHA-256)
-- 单租户: 我司单公司运营，无 enterprise_id 列；数据范围隔离走 dept_id + role_code
-- 执行:
--   mysql -uroot -p loan_db --default-character-set=utf8mb4 < loan-db-schema.sql
-- ============================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;
-- CREATE DATABASE IF NOT EXISTS loan_db DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
-- USE loan_db;

-- ============================================================
-- 一、组织权限域（7 张）—— 复用 tse 角色菜单三表模型升级为部门维度
-- ============================================================

DROP TABLE IF EXISTS `t_department`;
CREATE TABLE `t_department` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `dept_code` varchar(32) NOT NULL COMMENT '部门编码',
  `dept_name` varchar(64) NOT NULL COMMENT '部门名称',
  `parent_id` bigint DEFAULT NULL COMMENT '上级部门ID(顶级为NULL)',
  `leader_staff_id` bigint DEFAULT NULL COMMENT '负责人员工ID',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态(ACTIVE/DISABLED)',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人姓名',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dept_code` (`dept_code`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='部门(通用部门树,预置老板直属/咨询部/市场部/运营部)';

DROP TABLE IF EXISTS `t_staff`;
CREATE TABLE `t_staff` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `staff_code` varchar(32) NOT NULL COMMENT 'loan内部工号',
  `crm_user_id` varchar(64) NOT NULL COMMENT 'CRM员工ID(SSO映射键)',
  `staff_name` varchar(64) NOT NULL COMMENT '员工姓名',
  `dept_code` varchar(32) DEFAULT NULL COMMENT '所属部门编码(业务编码,与t_department.dept_code关联)',
  `role_code` varchar(32) NOT NULL COMMENT '角色(BOSS老板/DEPT_MANAGER部门主管/ADVISER顾问)',
  `wecom_qr_code` varchar(500) DEFAULT NULL COMMENT '个人企微二维码(报告页与企微引导位展示)',
  `phone` varchar(256) DEFAULT NULL COMMENT '手机号(AES加密)',
  `phone_hash` varchar(64) DEFAULT NULL COMMENT '手机号SHA-256哈希(查重)',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态(ACTIVE在职/LEAVE离职停用,离职联动线索客户转移提醒)',
  `leave_time` datetime DEFAULT NULL COMMENT '离职时间',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人姓名',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_staff_code` (`staff_code`),
  UNIQUE KEY `uk_crm_user_id` (`crm_user_id`),
  KEY `idx_dept_role` (`dept_code`,`role_code`),
  KEY `idx_phone_hash` (`phone_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='员工映射(CRM SSO员工→部门→角色三级绑定,不重复建账号)';

DROP TABLE IF EXISTS `t_role`;
CREATE TABLE `t_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `role_code` varchar(32) NOT NULL COMMENT '角色编码(BOSS/DEPT_MANAGER/ADVISER,大写)',
  `role_name` varchar(64) NOT NULL COMMENT '角色名称',
  `description` varchar(255) DEFAULT NULL COMMENT '角色说明',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态(ACTIVE/DISABLED)',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人姓名',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色字典(三级角色,老板权限最大)';

DROP TABLE IF EXISTS `t_menu`;
CREATE TABLE `t_menu` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `parent_id` bigint DEFAULT NULL COMMENT '上级菜单ID(顶级为NULL)',
  `menu_name` varchar(64) NOT NULL COMMENT '菜单名称',
  `path` varchar(128) NOT NULL COMMENT '前端路由路径(禁止重复path,tse教训M-02)',
  `component` varchar(255) DEFAULT NULL COMMENT '前端组件路径(叶子才挂component,tse教训M-03)',
  `menu_type` varchar(16) NOT NULL DEFAULT 'MENU' COMMENT '类型(MENU菜单/BUTTON操作按钮)',
  `permission_code` varchar(64) DEFAULT NULL COMMENT '操作权限码(如client:add/lead:claim/order:approve)',
  `customer_group` varchar(16) DEFAULT 'COMMON' COMMENT '客群维度(ENTERPRISE企业/PERSONAL个人/COMMON通用,供按客户群分别授权;T10/D28)',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态(ACTIVE/DISABLED)',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人姓名',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_path` (`path`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_permission_code` (`permission_code`),
  KEY `idx_customer_group` (`customer_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='菜单树(loan 20模块与子页面+操作按钮清单)';

DROP TABLE IF EXISTS `t_role_permission`;
CREATE TABLE `t_role_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `role_code` varchar(32) NOT NULL COMMENT '角色编码(大写,tse教训B-01)',
  `menu_id` bigint NOT NULL COMMENT '菜单/按钮ID',
  `permission_code` varchar(64) DEFAULT NULL COMMENT '操作权限码',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_menu_perm` (`role_code`,`menu_id`,`permission_code`),
  KEY `idx_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色权限(角色×菜单×操作按钮逐项勾选;保存仅补祖先expandWithAncestors,tse教训B-10)';

DROP TABLE IF EXISTS `t_dept_approver`;
CREATE TABLE `t_dept_approver` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `dept_id` bigint NOT NULL COMMENT '部门ID',
  `approve_type` varchar(32) NOT NULL COMMENT '审核类型(PRODUCT产品审核/ATTACHMENT_DOWNLOAD无水印下载审批/REWARD_SETTLE奖励结算审核/OFFLINE_SUPPLEMENT线下补录核验)',
  `approver_staff_id` bigint NOT NULL COMMENT '审核人员工ID(可多人可跨部门)',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '生效状态(未配置默认老板兜底)',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人姓名',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dept_type_approver` (`dept_id`,`approve_type`,`approver_staff_id`),
  KEY `idx_approver` (`approver_staff_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='部门审核人配置(部门×审核类型×审核人;未配置默认老板兜底,老板可随时改)';

DROP TABLE IF EXISTS `t_fallback_consultant`;
CREATE TABLE `t_fallback_consultant` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `staff_id` bigint NOT NULL COMMENT '顾问员工ID(与角色无关,普通顾问即可设为兜底)',
  `wecom_qr_code` varchar(500) NOT NULL COMMENT '企微二维码',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '启停',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序',
  `load_count` int NOT NULL DEFAULT '0' COMMENT '当前负载计数(无归属客户按负载最少分配)',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人姓名',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_staff_id` (`staff_id`),
  KEY `idx_enabled_sort` (`enabled`,`sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='兜底顾问池(客户无归属顾问时按负载最少分配并留痕)';

-- ============================================================
-- 二、匹配域（31 张）之 2.1 渠道产品 4 张
-- ============================================================

DROP TABLE IF EXISTS `t_bank_channel`;
CREATE TABLE `t_bank_channel` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `channel_code` varchar(32) NOT NULL COMMENT '渠道编码',
  `bank_name` varchar(128) NOT NULL COMMENT '银行名称',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态(ACTIVE/SUSPENDED)',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人姓名',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel_code` (`channel_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='合作银行渠道';

DROP TABLE IF EXISTS `t_bank_product`;
CREATE TABLE `t_bank_product` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `product_code` varchar(64) NOT NULL COMMENT '产品编码(内部代号化)',
  `bank_channel_code` varchar(64) NOT NULL COMMENT '所属银行渠道编码(业务编码,与t_bank_channel.channel_code对应;D28修正原bank_channel_id漂移)',
  `product_name` varchar(128) NOT NULL COMMENT '产品名称(仅管理端可见,客户端屏蔽)',
  `customer_group` varchar(16) NOT NULL COMMENT '客群(ENTERPRISE企业/PERSONAL个人,上线校验个人产品只能挂个人规则)',
  `source` varchar(16) NOT NULL DEFAULT 'OURS' COMMENT '来源(CHANNEL_SELF渠道自建/OURS我司录入)',
  `channel_user_id` bigint DEFAULT NULL COMMENT '渠道创建人账号ID',
  `amount_min` decimal(14,2) DEFAULT NULL COMMENT '额度下限(元)',
  `amount_max` decimal(14,2) DEFAULT NULL COMMENT '额度上限(元)',
  `rate_min` decimal(6,4) DEFAULT NULL COMMENT '利率下限',
  `rate_max` decimal(6,4) DEFAULT NULL COMMENT '利率上限',
  `term_min` int DEFAULT NULL COMMENT '期限下限(月)',
  `term_max` int DEFAULT NULL COMMENT '期限上限(月)',
  `tax_threshold` decimal(14,2) DEFAULT NULL COMMENT '纳税门槛(元/年)',
  `invoice_require` varchar(255) DEFAULT NULL COMMENT '开票要求',
  `biz_terms_json` json DEFAULT NULL COMMENT '条件资料结构化表单JSON(渠道只填此结构化表单,规则引擎由我司翻译)',
  `status` varchar(16) NOT NULL DEFAULT 'DRAFT' COMMENT '状态(DRAFT草稿/PENDING待审核/APPROVED已入全量库/REJECTED已驳回/OFFLINE已下线)',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人姓名',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_code` (`product_code`),
  KEY `idx_bank_group_status` (`bank_channel_code`,`customer_group`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='银行产品全量库(审核通过入全量库;客户端永不展示产品名)';

DROP TABLE IF EXISTS `t_partner_product`;
CREATE TABLE `t_partner_product` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `bank_product_id` bigint NOT NULL COMMENT '银行产品ID',
  `bank_product_code` varchar(64) NOT NULL COMMENT '银行产品业务编码(红线#3业务唯一ID,PartnerProduct实体查询键;D28补录)',
  `cooperate_until` datetime NOT NULL COMMENT '合作库有效期(到期自动下架,历史匹配报告与审计永久保留)',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态(ACTIVE上架/EXPIRED到期/OFFLINE手动下架)',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人姓名',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_bank_product_id` (`bank_product_id`),
  UNIQUE KEY `uk_bank_product_code` (`bank_product_code`),
  KEY `idx_status_until` (`status`,`cooperate_until`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='合作库上架(对客可见权在我司;T-30/T-7两次到期提醒续签)';

DROP TABLE IF EXISTS `t_product_admission_config`;
CREATE TABLE `t_product_admission_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `bank_product_id` bigint NOT NULL COMMENT '银行产品ID',
  `execution_plan_id` bigint NOT NULL COMMENT '准入执行计划ID',
  `risk_extra_json` json DEFAULT NULL COMMENT '产品级叠加风控模块JSON(全局前置+产品级两层风控)',
  `priority` int NOT NULL DEFAULT '0' COMMENT '优先级',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态(ACTIVE/DISABLED)',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人姓名',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_plan` (`bank_product_id`,`execution_plan_id`),
  KEY `idx_plan` (`execution_plan_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='产品准入配置(产品↔执行计划绑定+产品级风控叠加)';

-- ============================================================
-- 二、匹配域（31 张）之 2.2 规则目录 9 张
-- ============================================================

DROP TABLE IF EXISTS `t_rule_category`;
CREATE TABLE `t_rule_category` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `category_code` varchar(32) NOT NULL COMMENT '分类编码',
  `parent_id` bigint DEFAULT NULL COMMENT '上级分类ID(四分类树形)',
  `category_name` varchar(64) NOT NULL COMMENT '分类名称',
  `customer_group` varchar(16) NOT NULL DEFAULT 'COMMON' COMMENT '客群(PERSONAL/ENTERPRISE/COMMON)',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态(ACTIVE/DISABLED)',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人姓名',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_category_code` (`category_code`),
  KEY `idx_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='规则分类(个人14条+企业16条,四分类树形管理)';

DROP TABLE IF EXISTS `t_rule`;
CREATE TABLE `t_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `rule_code` varchar(64) NOT NULL COMMENT '规则编码',
  `rule_name` varchar(128) NOT NULL COMMENT '规则名称',
  `field_code` varchar(64) NOT NULL COMMENT '字段编码',
  `field_name` varchar(64) DEFAULT NULL COMMENT '字段名称',
  `operator` varchar(16) NOT NULL COMMENT '运算符(==/!=/>/</>=/<=/in/not_in/contains/not_contains/between/is_null/not_null)',
  `value_type` varchar(16) NOT NULL DEFAULT 'STRING' COMMENT '值类型(STRING/NUMBER/DATE/LIST)',
  `value_text` varchar(512) DEFAULT NULL COMMENT '规则值',
  `customer_group` varchar(16) NOT NULL DEFAULT 'COMMON' COMMENT '客群(PERSONAL/ENTERPRISE/COMMON)',
  `current_version` int NOT NULL DEFAULT '1' COMMENT '当前版本号',
  `description` varchar(255) DEFAULT NULL COMMENT '规则说明',
  `status` varchar(16) NOT NULL DEFAULT 'DRAFT' COMMENT '状态(DRAFT/ONLINE/DISABLED)',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人姓名',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rule_code` (`rule_code`),
  KEY `idx_group_status` (`customer_group`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='规则目录(双套规则目录;表达式{field_code}{operator}{value})';

DROP TABLE IF EXISTS `t_rule_version`;
CREATE TABLE `t_rule_version` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `rule_id` bigint NOT NULL COMMENT '规则ID',
  `version_no` int NOT NULL COMMENT '版本号',
  `field_code` varchar(64) NOT NULL COMMENT '字段编码快照',
  `operator` varchar(16) NOT NULL COMMENT '运算符快照',
  `value_text` varchar(512) DEFAULT NULL COMMENT '规则值快照',
  `snapshot_json` json DEFAULT NULL COMMENT '完整快照',
  `status` varchar(16) NOT NULL DEFAULT 'DRAFT' COMMENT '状态(DRAFT/ONLINE,上线写锁)',
  `published_at` datetime DEFAULT NULL COMMENT '发布时间',
  `published_by` varchar(64) DEFAULT NULL COMMENT '发布人姓名',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rule_version` (`rule_id`,`version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='规则版本(validate-before-enable;历史版本永久保留可追溯)';

DROP TABLE IF EXISTS `t_rule_category_rel`;
CREATE TABLE `t_rule_category_rel` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `rule_id` bigint NOT NULL COMMENT '规则ID',
  `category_id` bigint NOT NULL COMMENT '分类ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rule_category` (`rule_id`,`category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='规则-分类关联';

DROP TABLE IF EXISTS `t_product_strategy`;
CREATE TABLE `t_product_strategy` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `strategy_code` varchar(64) NOT NULL COMMENT '策略编码',
  `strategy_name` varchar(128) NOT NULL COMMENT '策略名称',
  `customer_group` varchar(16) NOT NULL DEFAULT 'COMMON' COMMENT '客群',
  `description` varchar(255) DEFAULT NULL COMMENT '策略说明',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态(ACTIVE/DISABLED)',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人姓名',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_strategy_code` (`strategy_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='产品策略(配置向导Step1挂策略)';

DROP TABLE IF EXISTS `t_product_strategy_bind`;
CREATE TABLE `t_product_strategy_bind` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `strategy_id` bigint NOT NULL COMMENT '策略ID',
  `bank_product_id` bigint NOT NULL COMMENT '银行产品ID',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_strategy_product` (`strategy_id`,`bank_product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='策略-产品绑定(配置向导Step0-1)';

DROP TABLE IF EXISTS `t_admission_execution_plan`;
CREATE TABLE `t_admission_execution_plan` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `plan_code` varchar(64) NOT NULL COMMENT '计划编码',
  `plan_name` varchar(128) NOT NULL COMMENT '计划名称',
  `customer_group` varchar(16) NOT NULL DEFAULT 'COMMON' COMMENT '客群',
  `version` int NOT NULL DEFAULT '1' COMMENT '版本',
  `status` varchar(8) NOT NULL DEFAULT '0' COMMENT '生命周期(0草稿/1上线+写锁)',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人姓名',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plan_code` (`plan_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='准入执行计划(计划→模块顺序→步骤;配置向导Step3)';

DROP TABLE IF EXISTS `t_admission_plan_module`;
CREATE TABLE `t_admission_plan_module` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `module_biz_code` varchar(64) NOT NULL COMMENT '模块业务编码',
  `plan_id` bigint NOT NULL COMMENT '计划ID',
  `module_code` varchar(64) NOT NULL COMMENT '模块编码',
  `module_name` varchar(128) NOT NULL COMMENT '模块名称',
  `logic_type` varchar(8) NOT NULL DEFAULT 'AND' COMMENT '模块逻辑(AND遇FAIL短路/OR遇PASS短路)',
  `is_global_pre` tinyint NOT NULL DEFAULT '0' COMMENT '全局前置风控模块(黑名单/失信/欺诈/区域/敏感行业,命中直接REJECT)',
  `sort` int NOT NULL DEFAULT '0' COMMENT '模块顺序',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_plan_sort` (`plan_id`,`sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='计划模块(配置向导Step4编排模块AND/OR)';

DROP TABLE IF EXISTS `t_admission_plan_step`;
CREATE TABLE `t_admission_plan_step` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `module_id` bigint NOT NULL COMMENT '模块ID',
  `rule_id` bigint NOT NULL COMMENT '规则ID',
  `rule_version_id` bigint NOT NULL COMMENT '规则版本ID(步骤=单条规则)',
  `step_sort` int NOT NULL DEFAULT '0' COMMENT '步骤顺序',
  `expression_json` json DEFAULT NULL COMMENT '解析后表达式快照',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_module_sort` (`module_id`,`step_sort`),
  KEY `idx_rule` (`rule_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='计划步骤(步骤=单条规则;配置向导Step4)';
-- ============================================================
-- 二、匹配域（31 张）之 2.3 模板库 7 张（策略模板 4 + 规则模板 3）
-- ============================================================

DROP TABLE IF EXISTS `t_strategy_template`;
CREATE TABLE `t_strategy_template` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `template_code` varchar(64) NOT NULL COMMENT '模板编码',
  `template_name` varchar(128) NOT NULL COMMENT '模板名称',
  `customer_group` varchar(16) NOT NULL DEFAULT 'COMMON' COMMENT '客群',
  `description` varchar(255) DEFAULT NULL COMMENT '模板说明',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态(ACTIVE/DISABLED)',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人姓名',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_template_code` (`template_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='策略模板(模板导入复用)';

DROP TABLE IF EXISTS `t_strategy_template_module`;
CREATE TABLE `t_strategy_template_module` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `module_biz_code` varchar(64) NOT NULL COMMENT '模块业务编码',
  `template_id` bigint NOT NULL COMMENT '策略模板ID',
  `module_code` varchar(64) NOT NULL COMMENT '模块编码',
  `module_name` varchar(128) NOT NULL COMMENT '模块名称',
  `logic_type` varchar(8) NOT NULL DEFAULT 'AND' COMMENT '模块逻辑(AND/OR)',
  `sort` int NOT NULL DEFAULT '0' COMMENT '顺序',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_template_sort` (`template_id`,`sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='策略模板-模块编排';

DROP TABLE IF EXISTS `t_strategy_template_step`;
CREATE TABLE `t_strategy_template_step` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `step_code` varchar(64) NOT NULL COMMENT '步骤业务编码',
  `template_module_id` bigint NOT NULL COMMENT '模板模块ID',
  `rule_template_id` bigint DEFAULT NULL COMMENT '规则模板ID',
  `rule_id` bigint DEFAULT NULL COMMENT '直接引用规则ID',
  `expression_json` json DEFAULT NULL COMMENT '表达式快照',
  `step_sort` int NOT NULL DEFAULT '0' COMMENT '顺序',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_module_sort` (`template_module_id`,`step_sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='策略模板-步骤';

DROP TABLE IF EXISTS `t_strategy_template_version`;
CREATE TABLE `t_strategy_template_version` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `template_id` bigint NOT NULL COMMENT '策略模板ID',
  `version_no` int NOT NULL COMMENT '版本号',
  `snapshot_json` json DEFAULT NULL COMMENT '完整快照(导入时按快照重建)',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
  `published_at` datetime DEFAULT NULL COMMENT '发布时间',
  `published_by` varchar(64) DEFAULT NULL COMMENT '发布人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_template_version` (`template_id`,`version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='策略模板版本';

DROP TABLE IF EXISTS `t_rule_template`;
CREATE TABLE `t_rule_template` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `template_code` varchar(64) NOT NULL COMMENT '模板编码',
  `template_name` varchar(128) NOT NULL COMMENT '模板名称',
  `category_id` bigint DEFAULT NULL COMMENT '所属分类',
  `customer_group` varchar(16) NOT NULL DEFAULT 'COMMON' COMMENT '客群',
  `description` varchar(255) DEFAULT NULL COMMENT '模板说明',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态(ACTIVE/DISABLED)',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人姓名',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_template_code` (`template_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='规则模板(模板导入复用)';

DROP TABLE IF EXISTS `t_rule_template_field`;
CREATE TABLE `t_rule_template_field` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `field_biz_code` varchar(64) NOT NULL COMMENT '字段业务编码',
  `template_id` bigint NOT NULL COMMENT '规则模板ID',
  `field_code` varchar(64) NOT NULL COMMENT '字段编码',
  `field_name` varchar(64) NOT NULL COMMENT '字段名称',
  `field_type` varchar(16) NOT NULL DEFAULT 'STRING' COMMENT '字段类型(STRING/NUMBER/DATE/LIST)',
  `operator` varchar(16) NOT NULL COMMENT '默认运算符',
  `default_value` varchar(512) DEFAULT NULL COMMENT '默认值',
  `required` tinyint NOT NULL DEFAULT '1' COMMENT '是否必填',
  `sort` int NOT NULL DEFAULT '0' COMMENT '顺序',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_template_sort` (`template_id`,`sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='规则模板-字段定义';

DROP TABLE IF EXISTS `t_rule_template_version`;
CREATE TABLE `t_rule_template_version` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `template_id` bigint NOT NULL COMMENT '规则模板ID',
  `version_no` int NOT NULL COMMENT '版本号',
  `snapshot_json` json DEFAULT NULL COMMENT '完整快照',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
  `published_at` datetime DEFAULT NULL COMMENT '发布时间',
  `published_by` varchar(64) DEFAULT NULL COMMENT '发布人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_template_version` (`template_id`,`version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='规则模板版本';

-- ============================================================
-- 二、匹配域（31 张）之 2.4 客户认证 4 张
-- ============================================================

DROP TABLE IF EXISTS `t_client_profile`;
CREATE TABLE `t_client_profile` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `client_code` varchar(64) NOT NULL COMMENT '客户编码(业务ID:client+32位随机)',
  `customer_group` varchar(16) NOT NULL COMMENT '客群(ENTERPRISE/PERSONAL,身份选择后锁定硬分流)',
  `enterprise_name` varchar(128) DEFAULT NULL COMMENT '企业名称(企业客群)',
  `contact_name` varchar(64) DEFAULT NULL COMMENT '联系人姓名',
  `phone` varchar(256) NOT NULL COMMENT '手机号(AES加密)',
  `phone_hash` varchar(64) NOT NULL COMMENT '手机号SHA-256哈希(查重与等值查询)',
  `credit_code` varchar(256) DEFAULT NULL COMMENT '统一社会信用代码(AES加密,企业客群)',
  `credit_code_hash` varchar(64) DEFAULT NULL COMMENT '信用代码SHA-256哈希',
  `owner_staff_code` varchar(64) DEFAULT NULL COMMENT '归属顾问工号(业务编码;为空表示公海未分配)',
  `last_followed_at` datetime DEFAULT NULL COMMENT '最后跟进时间(超期回收判定基准;归属/转移/跟进刷新)',
  `assign_blocked_until` datetime DEFAULT NULL COMMENT '回收冷却到期时间(回收进公海后原归属人不可认领/不可被直接分配)',
  `vip_level` varchar(16) DEFAULT NULL COMMENT 'VIP等级(NULL非会员/VIP)',
  `vip_expire_at` datetime DEFAULT NULL COMMENT 'VIP到期时间(T-7提醒续费,到期自动降级)',
  `invited_flag` tinyint NOT NULL DEFAULT '0' COMMENT '受邀标记(受邀用户免费自动VIP+独享分享推荐奖励)',
  `source` varchar(32) DEFAULT NULL COMMENT '来源(引荐绑定/公海认领/转正)',
  `lead_no` varchar(64) DEFAULT NULL COMMENT '转正来源线索编号(业务ID:lead+32位随机)',
  `wecom_added` tinyint NOT NULL DEFAULT '0' COMMENT '企微添加标记',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态(ACTIVE/DISABLED)',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `ext_json` json DEFAULT NULL COMMENT '扩展预留',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人姓名',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_client_code` (`client_code`),
  UNIQUE KEY `uk_phone_hash_group` (`phone_hash`,`customer_group`),
  KEY `idx_owner` (`owner_staff_code`),
  KEY `idx_lead_no` (`lead_no`),
  KEY `idx_created_at` (`created_at`),
  KEY `idx_credit_code_hash` (`credit_code_hash`),
  KEY `idx_group_status` (`customer_group`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户档案(线索认证通过后转正;数据权限按部门+角色隔离)';

DROP TABLE IF EXISTS `t_client_recycle_config`;
CREATE TABLE `t_client_recycle_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `config_key` varchar(16) NOT NULL DEFAULT 'GLOBAL' COMMENT '配置键(当前仅 GLOBAL 单行)',
  `recycle_enabled` tinyint NOT NULL DEFAULT '1' COMMENT '回收开关(1开/0关)',
  `recycle_days` int NOT NULL DEFAULT '30' COMMENT '回收天数(超过该天数无跟进自动回收进公海)',
  `warn_days` int NOT NULL DEFAULT '3' COMMENT '预警天数(距回收剩余时站内预警归属人)',
  `cooldown_days` int NOT NULL DEFAULT '7' COMMENT '冷却天数(回收后原归属人不可认领)',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户回收规则参数(全参数化不写死;参考 t_lead_recycle_config 与 tse 资源池回收)';

DROP TABLE IF EXISTS `t_personal_profile`;
CREATE TABLE `t_personal_profile` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `client_profile_id` bigint NOT NULL COMMENT '客户档案ID(1:1)',
  `real_name` varchar(64) NOT NULL COMMENT '姓名',
  `id_card_no` varchar(256) DEFAULT NULL COMMENT '身份证号(AES加密)',
  `id_card_hash` varchar(64) DEFAULT NULL COMMENT '身份证SHA-256哈希',
  `age` int DEFAULT NULL COMMENT '年龄',
  `city` varchar(64) DEFAULT NULL COMMENT '城市',
  `house_flag` tinyint DEFAULT NULL COMMENT '房产(0无/1有)',
  `car_flag` tinyint DEFAULT NULL COMMENT '车辆(0无/1有)',
  `social_security_flag` tinyint DEFAULT NULL COMMENT '社保(0无/1有)',
  `fund_flag` tinyint DEFAULT NULL COMMENT '公积金(0无/1有)',
  `ext_json` json DEFAULT NULL COMMENT '扩展预留(个人字段/规则/产品表结构已预留,后续只补产品即可上线)',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人姓名',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_client_profile_id` (`client_profile_id`),
  KEY `idx_id_card_hash` (`id_card_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='个人客户档案(1:1扩展)';

DROP TABLE IF EXISTS `t_personal_auth`;
CREATE TABLE `t_personal_auth` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `client_profile_id` bigint NOT NULL COMMENT '客户档案ID',
  `auth_type` varchar(32) NOT NULL COMMENT '认证类型(ID_CARD_OCR身份证OCR/FACE_LIVENESS人脸活体/PHONE_THREE_ELEMENT手机三要素)',
  `auth_status` varchar(16) NOT NULL COMMENT '认证状态(PENDING/SUCCESS/FAIL)',
  `fail_reason` varchar(255) DEFAULT NULL COMMENT '失败原因',
  `auth_time` datetime DEFAULT NULL COMMENT '认证时间',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_client_type` (`client_profile_id`,`auth_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='个人认证记录(OCR+人脸活体+三要素,全过才认证)';

DROP TABLE IF EXISTS `t_client_submission`;
CREATE TABLE `t_client_submission` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `submission_no` varchar(64) NOT NULL COMMENT '提交单号(业务ID:submit+32位随机)',
  `client_profile_id` bigint NOT NULL COMMENT '客户档案ID',
  `customer_group` varchar(16) NOT NULL COMMENT '客群',
  `data_json` json NOT NULL COMMENT '提交资料结构化JSON',
  `client_submit_id` varchar(64) NOT NULL COMMENT '客户端幂等键(防重复提交)',
  `match_trace_id` bigint DEFAULT NULL COMMENT '关联匹配审计ID',
  `status` varchar(16) NOT NULL DEFAULT 'SUBMITTED' COMMENT '状态(SUBMITTED已提交/MATCHING匹配中/MATCHED已匹配)',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_submission_no` (`submission_no`),
  UNIQUE KEY `uk_client_submit_id` (`client_submit_id`),
  KEY `idx_client_status` (`client_profile_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户资料提交(幂等防重)';

-- ============================================================
-- 二、匹配域（31 张）之 2.5 事实提取 2 张
-- ============================================================

DROP TABLE IF EXISTS `t_extract_field_def`;
CREATE TABLE `t_extract_field_def` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `field_code` varchar(64) NOT NULL COMMENT '字段编码',
  `field_name` varchar(64) NOT NULL COMMENT '字段名称',
  `field_type` varchar(16) NOT NULL DEFAULT 'NUMBER' COMMENT '字段类型(STRING/NUMBER/DATE/BOOL)',
  `customer_group` varchar(16) NOT NULL DEFAULT 'COMMON' COMMENT '客群',
  `extract_rule_json` json DEFAULT NULL COMMENT '提取规则(从提交资料映射到事实字段)',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态(ACTIVE/DISABLED)',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人姓名',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_field_code` (`field_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='提取字段定义';

DROP TABLE IF EXISTS `t_client_business_fact`;
CREATE TABLE `t_client_business_fact` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `client_profile_id` bigint NOT NULL COMMENT '客户档案ID',
  `submission_id` bigint NOT NULL COMMENT '提交单ID',
  `field_code` varchar(64) NOT NULL COMMENT '字段编码',
  `field_value` varchar(512) DEFAULT NULL COMMENT '字段值',
  `field_type` varchar(16) NOT NULL DEFAULT 'STRING' COMMENT '字段类型',
  `extract_time` datetime DEFAULT NULL COMMENT '提取时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_client_field` (`client_profile_id`,`field_code`),
  KEY `idx_submission` (`submission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户经营事实(提取层;匹配引擎数据源)';

-- ============================================================
-- 二、匹配域（31 张）之 2.6 初筛报告 2 张 + 报告模板 1 张
-- ============================================================

DROP TABLE IF EXISTS `t_client_authorization`;
CREATE TABLE `t_client_authorization` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `client_profile_id` bigint NOT NULL COMMENT '客户档案ID',
  `auth_type` varchar(32) NOT NULL COMMENT '授权类型(DATA_ACCESS数据采集/REPORT_GEN报告生成)',
  `auth_content` varchar(500) DEFAULT NULL COMMENT '授权内容摘要',
  `authorized_at` datetime NOT NULL COMMENT '授权时间',
  `expire_at` datetime DEFAULT NULL COMMENT '授权到期',
  `ip` varchar(64) DEFAULT NULL COMMENT '授权IP',
  `status` varchar(16) NOT NULL DEFAULT 'VALID' COMMENT '状态(VALID/EXPIRED/REVOKED)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_client_type` (`client_profile_id`,`auth_type`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户授权留痕(数据来源声明合法渠道+企业授权)';

DROP TABLE IF EXISTS `t_client_screening`;
CREATE TABLE `t_client_screening` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `report_no` varchar(64) NOT NULL COMMENT '报告编号(业务ID:report+32位随机)',
  `client_profile_code` varchar(64) NOT NULL COMMENT '客户编码(业务ID:client+32位随机)',
  `match_trace_no` varchar(64) NOT NULL COMMENT '匹配审计链路UUID(业务编码)',
  `template_code` varchar(64) NOT NULL COMMENT '报告模板编码(版本锁定,改模板不影响历史报告)',
  `grade` varchar(8) DEFAULT NULL COMMENT '档位(HIGH高/MIDDLE中/LOW低,不用百分比)',
  `bank_count` int NOT NULL DEFAULT '0' COMMENT '预计可进件银行数(客户端唯一可见数量口径)',
  `product_count` int NOT NULL DEFAULT '0' COMMENT '命中产品数',
  `pass_count` int NOT NULL DEFAULT '0' COMMENT 'PASS数',
  `condition_count` int NOT NULL DEFAULT '0' COMMENT 'CONDITION需补料数',
  `reject_count` int NOT NULL DEFAULT '0' COMMENT 'REJECT数',
  `advice_json` json DEFAULT NULL COMMENT '多维建议清单(维度分析自动生成,话术避免承诺性表述)',
  `report_file_key` varchar(255) DEFAULT NULL COMMENT '报告文件key(水印版:客户企业名+手机号后四位+生成时间)',
  `vip_flag` tinyint NOT NULL DEFAULT '0' COMMENT 'VIP版标记(VIP附命中维度明细与补强路线图,仍不含产品名)',
  `status` varchar(16) NOT NULL DEFAULT 'GENERATED' COMMENT '状态(GENERATED已生成/VIEWED已查看)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_report_no` (`report_no`),
  KEY `idx_client_created` (`client_profile_code`,`created_at`),
  KEY `idx_trace` (`match_trace_no`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='初筛报告(档位+数量展示;不含任何银行/产品名称)';

DROP TABLE IF EXISTS `t_report_template`;
CREATE TABLE `t_report_template` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `template_code` varchar(64) NOT NULL COMMENT '模板编码',
  `version_no` int NOT NULL COMMENT '版本号(版本锁定,改模板不影响历史报告)',
  `template_name` varchar(128) NOT NULL COMMENT '模板名称',
  `grade_rule_json` json DEFAULT NULL COMMENT '档位映射规则(如命中≥3个产品且核心维度全过=高)',
  `disclaimer_text` text COMMENT '免责声明文案(匹配程度分析与预计概率,不构成银行通过承诺)',
  `advice_rules_json` json DEFAULT NULL COMMENT '多维建议文案库(维度→条件→建议文案映射,后台可配改文案不发版)',
  `wecom_guide_config` json DEFAULT NULL COMMENT '企微咨询引导尾页配置',
  `watermark_config` json DEFAULT NULL COMMENT '报告溯源水印配置',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态(ACTIVE/DISABLED)',
  `published_at` datetime DEFAULT NULL COMMENT '发布时间',
  `published_by` varchar(64) DEFAULT NULL COMMENT '发布人姓名',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人姓名',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code_version` (`template_code`,`version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='报告模板版本(档位映射/免责声明/多维建议文案库随版本锁定)';

-- ============================================================
-- 二、匹配域（31 张）之 2.7 审计 2 张
-- ============================================================

DROP TABLE IF EXISTS `t_match_trace`;
CREATE TABLE `t_match_trace` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `trace_uuid` varchar(64) NOT NULL COMMENT '链路UUID(响应头X-Trace-Id+body双通道)',
  `client_profile_id` bigint NOT NULL COMMENT '客户档案ID',
  `submission_id` bigint NOT NULL COMMENT '提交单ID',
  `customer_group` varchar(16) NOT NULL COMMENT '客群',
  `total_result` varchar(24) NOT NULL COMMENT '总结果(PASS/CONDITION/REJECT/SKIP_SEGMENT_MISMATCH/ERROR)',
  `hit_count` int NOT NULL DEFAULT '0' COMMENT '命中产品数',
  `step_count` int NOT NULL DEFAULT '0' COMMENT '执行步骤数',
  `duration_ms` bigint NOT NULL DEFAULT '0' COMMENT '耗时(毫秒)',
  `mismatch_flag` tinyint NOT NULL DEFAULT '0' COMMENT '双结果不一致标记(step_result与handler_step_result不一致标红)',
  `executed_at` datetime NOT NULL COMMENT '执行时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_trace_uuid` (`trace_uuid`),
  KEY `idx_client` (`client_profile_id`),
  KEY `idx_executed` (`executed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='匹配审计主表(全链路明细含命中产品——仅管理端可见)';

DROP TABLE IF EXISTS `t_match_rule_log`;
CREATE TABLE `t_match_rule_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `trace_id` bigint NOT NULL COMMENT '匹配审计ID',
  `plan_id` bigint NOT NULL COMMENT '执行计划ID',
  `module_id` bigint NOT NULL COMMENT '模块ID',
  `step_id` bigint NOT NULL COMMENT '步骤ID',
  `rule_code` varchar(64) NOT NULL COMMENT '规则编码',
  `expression` varchar(512) DEFAULT NULL COMMENT '执行表达式',
  `step_result` varchar(24) NOT NULL COMMENT '步骤结果(PASS/FAIL/SKIP/SKIP_SEGMENT_MISMATCH/ERROR)',
  `handler_step_result` varchar(24) DEFAULT NULL COMMENT 'Handler结果(双结果审计)',
  `mismatch_flag` tinyint NOT NULL DEFAULT '0' COMMENT '不一致标记',
  `executed_at` datetime NOT NULL COMMENT '执行时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_trace` (`trace_id`),
  KEY `idx_rule` (`rule_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='匹配规则日志(双结果审计)';

DROP TABLE IF EXISTS `t_screening_product`;
CREATE TABLE `t_screening_product` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `report_no` varchar(64) NOT NULL COMMENT '报告编号(业务ID:report+32位随机)',
  `bank_product_id` bigint NOT NULL COMMENT '银行产品ID(t_bank_product)',
  `product_code` varchar(64) NOT NULL COMMENT '产品编码(内部代号化)',
  `hit_result` varchar(24) NOT NULL COMMENT '命中结果(PASS/CONDITION/REJECT)',
  `match_score` int NOT NULL DEFAULT '0' COMMENT '匹配度0-100(落库时按模块命中率计算)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_report_product` (`report_no`,`product_code`),
  KEY `idx_report` (`report_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='报告命中产品明细(员工陪访可见,客户永不展示产品名)';
-- ============================================================
-- 三、渠道体系（4 张）
-- ============================================================

DROP TABLE IF EXISTS `t_channel_user`;
CREATE TABLE `t_channel_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `bank_channel_id` bigint NOT NULL COMMENT '所属银行渠道ID(数据范围硬隔离:仅本行产品与审核状态)',
  `phone` varchar(256) NOT NULL COMMENT '手机号(AES加密)',
  `phone_hash` varchar(64) NOT NULL COMMENT '手机号SHA-256哈希(登录与查重)',
  `password` varchar(128) NOT NULL COMMENT '密码(BCrypt)',
  `name` varchar(64) NOT NULL COMMENT '姓名',
  `job_title` varchar(64) DEFAULT NULL COMMENT '岗位',
  `register_type` varchar(16) NOT NULL DEFAULT 'INVITE_CODE' COMMENT '注册方式(INVITE_CODE邀请码自助注册)',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态(ACTIVE/DISABLED,停用即时踢下线)',
  `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人姓名',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_phone_hash` (`phone_hash`),
  KEY `idx_bank_status` (`bank_channel_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='渠道银行账号(单一角色:录入草稿+查审核进度+收消息;不见任何客户资料匹配结果)';

DROP TABLE IF EXISTS `t_invitation`;
CREATE TABLE `t_invitation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `invitation_code` varchar(64) NOT NULL COMMENT '邀请码/短码(scene携带引荐码扫码自动绑定)',
  `invite_type` varchar(32) NOT NULL COMMENT '邀请类型(CHANNEL渠道/ENTERPRISE服务邀请-企业/PERSONAL服务邀请-个人)',
  `referrer_type` varchar(32) NOT NULL COMMENT '引荐人类型(BOSS老板/ADVISER员工顾问/CHANNEL渠道/VIP客户/CUSTOMER受邀用户推荐)',
  `referrer_id` bigint DEFAULT NULL COMMENT '引荐人ID(员工ID/渠道账号ID/客户档案ID)',
  `referrer_client_code` varchar(64) DEFAULT NULL COMMENT '引荐人客户编码(仅CUSTOMER类型,业务ID)',
  `scene_param` varchar(255) DEFAULT NULL COMMENT '场景参数(企微活码参数同入此表溯源)',
  `expire_at` datetime NOT NULL COMMENT '有效期(短码7天单次;顾问长期固定码expire_at设很远)',
  `used_flag` tinyint NOT NULL DEFAULT '0' COMMENT '使用状态(注册成功即作废)',
  `used_by_client_id` bigint DEFAULT NULL COMMENT '使用者客户档案ID',
  `used_by_client_code` varchar(64) DEFAULT NULL COMMENT '使用者客户编码(业务ID)',
  `used_at` datetime DEFAULT NULL COMMENT '使用时间',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态(ACTIVE/VOID作废,我司可随时作废重发)',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_invitation_code` (`invitation_code`),
  KEY `idx_referrer` (`referrer_type`,`referrer_id`),
  KEY `idx_referrer_client` (`referrer_client_code`),
  KEY `idx_used_client` (`used_by_client_code`,`used_at`),
  KEY `idx_expire` (`expire_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='邀请凭证(仅referrer_type=CUSTOMER受邀用户推荐进入奖励结算;员工/渠道引荐只记归属不产生奖励)';

DROP TABLE IF EXISTS `t_product_approval`;
CREATE TABLE `t_product_approval` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `approval_no` varchar(64) NOT NULL COMMENT '审核工单号(业务ID:prdapr+32位随机)',
  `bank_product_id` bigint NOT NULL COMMENT '银行产品ID',
  `channel_user_id` bigint NOT NULL COMMENT '提交渠道账号ID',
  `apply_type` varchar(16) NOT NULL COMMENT '申请类型(CREATE新建/UPDATE变更,变更生成草稿副本前后快照)',
  `before_snapshot_json` json DEFAULT NULL COMMENT '变更前快照',
  `after_snapshot_json` json DEFAULT NULL COMMENT '变更后快照(全程可追溯)',
  `duplicate_flag` tinyint NOT NULL DEFAULT '0' COMMENT '重复产品标记(同渠道同名/同额度区间自动比对标红)',
  `approve_status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT '审核状态(PENDING待审核/APPROVED通过/REJECTED驳回)',
  `approver_staff_id` bigint DEFAULT NULL COMMENT '审核人(走部门审核人配置t_dept_approver,未配置默认老板)',
  `approve_opinion` varchar(500) DEFAULT NULL COMMENT '审核意见(驳回必填,通过选填)',
  `timeout_at` datetime NOT NULL COMMENT '审核时效(48小时可配,超时仪表盘+消息提醒老板)',
  `approved_at` datetime DEFAULT NULL COMMENT '审核完成时间',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人姓名',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_approval_no` (`approval_no`),
  KEY `idx_status_timeout` (`approve_status`,`timeout_at`),
  KEY `idx_channel` (`channel_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='产品审核工单(新建与变更都走审核;通过入全量库,上架合作库另二次操作)';

DROP TABLE IF EXISTS `t_bank_contact`;
CREATE TABLE `t_bank_contact` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `bank_channel_id` bigint NOT NULL COMMENT '银行渠道ID(每行多位联系人)',
  `contact_name` varchar(64) NOT NULL COMMENT '联系人姓名',
  `job_title` varchar(64) DEFAULT NULL COMMENT '岗位',
  `phone` varchar(256) NOT NULL COMMENT '手机号(AES加密)',
  `phone_hash` varchar(64) DEFAULT NULL COMMENT '手机号SHA-256哈希',
  `wecom_id` varchar(128) DEFAULT NULL COMMENT '企微号(复制企微)',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态(ACTIVE/DISABLED)',
  `created_by` varchar(64) DEFAULT NULL COMMENT '维护人姓名(老板维护/主管本部门相关/顾问只读)',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_bank_status` (`bank_channel_id`,`status`),
  KEY `idx_phone_hash` (`phone_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='银行渠道联系人库(管理端客户银行匹配明细展示数据源;一键拨号/复制企微)';

-- ============================================================
-- 四、服务工单域（5 张）—— t_deal_record 升级为业务订单主表
-- ============================================================

DROP TABLE IF EXISTS `t_service_order`;
CREATE TABLE `t_service_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_no` varchar(64) NOT NULL COMMENT '工单号(业务ID:order+32位随机)',
  `client_profile_id` bigint NOT NULL COMMENT '客户档案ID',
  `customer_group` varchar(16) NOT NULL COMMENT '客群',
  `bank_product_id` bigint DEFAULT NULL COMMENT '关联银行产品ID',
  `owner_staff_id` bigint DEFAULT NULL COMMENT '顾问员工ID',
  `deal_amount` decimal(14,2) DEFAULT NULL COMMENT '成交金额(DEAL计入营收并触发奖励结算基数)',
  `deal_time` datetime DEFAULT NULL COMMENT '成交时间',
  `customer_remark` varchar(500) DEFAULT NULL COMMENT '客户可见备注(小程序我的服务单展示)',
  `internal_remark` varchar(500) DEFAULT NULL COMMENT '内部备注(仅管理端可见)',
  `pay_type` varchar(32) DEFAULT NULL COMMENT '支付方式',
  `voucher_attachment_id` bigint DEFAULT NULL COMMENT '凭证附件ID(线下补录必填)',
  `status` varchar(16) NOT NULL DEFAULT 'NEW' COMMENT '状态机(NEW新建/IN_SERVICE服务中/DEAL已成交/CANCEL已取消/REFUND已退款冲减)',
  `source` varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '来源(CRM_WRITEBACK合同回写自动建单/OFFLINE_SUPPLEMENT线下补录/MANUAL手工建单)',
  `source_order_no` varchar(64) DEFAULT NULL COMMENT '来源单号(CRM合同号/线下成交单号)',
  `reward_settled_flag` tinyint NOT NULL DEFAULT '0' COMMENT '奖励结算标记(DEAL触发结算,REFUND联动奖励单作废/冲正)',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人姓名(操作人留痕)',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_client_status` (`client_profile_id`,`status`),
  KEY `idx_owner_status` (`owner_staff_id`,`status`),
  KEY `idx_status_dealtime` (`status`,`deal_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='服务工单(业务订单主表,支撑客户/推荐人/公司三视角;REFUND自动冲减营收与奖励)';

DROP TABLE IF EXISTS `t_service_attachment`;
CREATE TABLE `t_service_attachment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_id` bigint NOT NULL COMMENT '工单ID(上传动作绑工单留痕)',
  `client_profile_id` bigint NOT NULL COMMENT '客户ID(同客户跨工单一键引用复用)',
  `attachment_type` varchar(32) NOT NULL COMMENT '资料类型(ID_CARD身份证/BUSINESS_LICENSE营业执照/FINANCIAL_STATEMENT财报/CONTRACT合同/DUE_DILIGENCE尽调材料/OTHER其他)',
  `file_key` varchar(255) NOT NULL COMMENT '文件key(阿里云OSS,复用tse OssStorageService;本地磁盘为开发模式)',
  `file_name` varchar(255) NOT NULL COMMENT '原始文件名',
  `file_size` bigint DEFAULT NULL COMMENT '文件大小(字节)',
  `sensitive_flag` tinyint NOT NULL DEFAULT '0' COMMENT '敏感标记(证件/合同,文件级AES加密,解密走服务端受控接口)',
  `encrypted_flag` tinyint NOT NULL DEFAULT '1' COMMENT '已加密标记(落库前加密)',
  `referenced_from_id` bigint DEFAULT NULL COMMENT '跨单引用来源附件ID(引用复用留痕)',
  `upload_staff_id` bigint DEFAULT NULL COMMENT '上传人',
  `upload_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_order` (`order_id`),
  KEY `idx_client_type` (`client_profile_id`,`attachment_type`),
  KEY `idx_upload_staff` (`upload_staff_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='服务资料库(查看/下载统一动态水印,原文件永不出库)';

DROP TABLE IF EXISTS `t_service_follow`;
CREATE TABLE `t_service_follow` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_id` bigint NOT NULL COMMENT '工单ID',
  `content` text NOT NULL COMMENT '沟通内容',
  `attachment_ids` varchar(500) DEFAULT NULL COMMENT '附件引用(JSON数组ID)',
  `channel_type` varchar(16) NOT NULL DEFAULT 'PHONE' COMMENT '沟通渠道(VISIT上门/PHONE电话/WECOM企微/MEETING面谈)',
  `next_follow_time` datetime DEFAULT NULL COMMENT '下次跟进时间(驱动工单提醒与回收预警)',
  `follower_staff_id` bigint NOT NULL COMMENT '跟进人',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人姓名(到人留痕)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_time` (`order_id`,`created_at`),
  KEY `idx_follower` (`follower_staff_id`),
  KEY `idx_next_follow` (`next_follow_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='服务沟通记录(时间轴倒序沉淀内部可见,客户不可见;参考tse CustomerFollowPo含next_follow_time)';

DROP TABLE IF EXISTS `t_attachment_download_approval`;
CREATE TABLE `t_attachment_download_approval` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `approval_no` varchar(64) NOT NULL COMMENT '申请单号(业务ID:dldapr+32位随机)',
  `applicant_staff_id` bigint NOT NULL COMMENT '申请人员工ID(仅公司内部员工,客户无此通道)',
  `attachment_ids` varchar(500) NOT NULL COMMENT '资料清单(JSON数组附件ID)',
  `purpose` varchar(500) NOT NULL COMMENT '用途说明(必填,如报送银行/纸质留存)',
  `expect_days` int DEFAULT NULL COMMENT '期望使用期限',
  `approver_staff_id` bigint DEFAULT NULL COMMENT '审批人(走部门审核人配置,未配置默认老板;驳回必填原因)',
  `approve_status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT '审批状态(PENDING/APPROVED/REJECTED)',
  `approve_opinion` varchar(500) DEFAULT NULL COMMENT '审批意见',
  `link_token` varchar(64) DEFAULT NULL COMMENT '24h限时下载链接token(通过后生成)',
  `link_expire_at` datetime DEFAULT NULL COMMENT '链接过期时间(超时作废需重新申请)',
  `void_flag` tinyint NOT NULL DEFAULT '0' COMMENT '作废标记(可手动作废)',
  `approved_at` datetime DEFAULT NULL COMMENT '审批时间',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人姓名',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_approval_no` (`approval_no`),
  UNIQUE KEY `uk_link_token` (`link_token`),
  KEY `idx_applicant_status` (`applicant_staff_id`,`approve_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='无水印下载审批单(通过生成24h限时链接,超时作废重新申请;下载动作全量留痕)';

DROP TABLE IF EXISTS `t_attachment_download_log`;
CREATE TABLE `t_attachment_download_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `downloader_type` varchar(16) NOT NULL COMMENT '下载人类型(CUSTOMER客户/STAFF员工)',
  `downloader_id` bigint NOT NULL COMMENT '下载人ID(客户档案ID/员工ID)',
  `downloader_name` varchar(64) NOT NULL COMMENT '下载人姓名(水印内容=姓名+手机尾号+操作时间)',
  `attachment_id` bigint NOT NULL COMMENT '附件ID',
  `watermark_type` varchar(16) NOT NULL COMMENT '水印类型(DYNAMIC动态水印副本/NONE无水印审批下载)',
  `approval_id` bigint DEFAULT NULL COMMENT '关联审批单号(无水印下载时)',
  `ip` varchar(64) DEFAULT NULL COMMENT 'IP',
  `device` varchar(255) DEFAULT NULL COMMENT '设备信息',
  `download_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下载时间(保留≥3年可审计)',
  PRIMARY KEY (`id`),
  KEY `idx_downloader` (`downloader_type`,`downloader_id`),
  KEY `idx_attachment` (`attachment_id`),
  KEY `idx_time` (`download_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资料下载日志(谁/哪些文件/水印类型/关联审批单/IP/设备/时间,保留≥3年)';

-- ============================================================
-- 五、奖励域（3 张）—— 仅受邀用户参与，渠道/员工不产生奖励
-- ============================================================

DROP TABLE IF EXISTS `t_reward_rule`;
CREATE TABLE `t_reward_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `rule_version` varchar(64) NOT NULL COMMENT '规则版本(业务唯一编码,冻结快照,后续改比例不影响已生成奖励单)',
  `direct_rate` decimal(6,4) NOT NULL COMMENT '直推比例(第1层X%)',
  `indirect_rate` decimal(6,4) DEFAULT NULL COMMENT '间推比例(第2层Y%,后台预留开关默认关)',
  `indirect_enabled` tinyint NOT NULL DEFAULT '0' COMMENT '间推开关(默认关闭,发1层存2层)',
  `base_caliber` varchar(64) NOT NULL DEFAULT 'SERVICE_ORDER_DEAL' COMMENT '基数口径(服务工单DEAL:CRM合同回写+线下补录;线上VIP支付不计入)',
  `product_code` varchar(64) DEFAULT NULL COMMENT '适用产品编码(NULL=全局默认);按产品×客群分层配置,无默认必须显式配置',
  `customer_group` varchar(16) DEFAULT NULL COMMENT '适用客群(ENTERPRISE企业/PERSONAL个人,NULL=全局默认);与product_code组合精确匹配',
  `valid_from` datetime NOT NULL COMMENT '生效时间',
  `valid_until` datetime DEFAULT NULL COMMENT '失效时间',
  `min_amount` decimal(12,2) DEFAULT NULL COMMENT '奖励下限(元)',
  `max_amount` decimal(12,2) DEFAULT NULL COMMENT '奖励上限(元)',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态(ACTIVE/DISABLED)',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人姓名',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rule_version` (`rule_version`),
  UNIQUE KEY `uk_product_group` (`product_code`, `customer_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='推荐奖励规则(按产品×客群分层,全部后台可改不写死;无全局默认须显式配置)';

DROP TABLE IF EXISTS `t_reward_record`;
CREATE TABLE `t_reward_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `reward_no` varchar(64) NOT NULL COMMENT '奖励单号(业务ID:reward+32位随机)',
  `referrer_client_id` bigint NOT NULL COMMENT '推荐人客户档案ID(仅受邀用户)',
  `referee_client_id` bigint NOT NULL COMMENT '被推荐人客户档案ID',
  `level` tinyint NOT NULL DEFAULT '1' COMMENT '层级(1直推发奖励/2间推仅记录,层级封顶2层,第3层自动截断)',
  `service_order_id` bigint NOT NULL COMMENT '关联服务工单ID(成交基数来源)',
  `base_amount` decimal(14,2) NOT NULL COMMENT '基数金额快照(成交金额)',
  `rate_snapshot` decimal(6,4) NOT NULL COMMENT '比例快照',
  `rule_version` varchar(32) NOT NULL COMMENT '规则版本快照',
  `calc_process` varchar(500) DEFAULT NULL COMMENT '计算过程留痕',
  `reward_amount` decimal(12,2) NOT NULL COMMENT '奖励金额',
  `status` varchar(16) NOT NULL DEFAULT 'PENDING_AUDIT' COMMENT '状态机(PENDING_AUDIT待审核/GRANTED已发放/REJECTED已驳回/VOID已作废)',
  `reject_reason` varchar(500) DEFAULT NULL COMMENT '驳回原因(必填)',
  `manual_adjust_flag` tinyint NOT NULL DEFAULT '0' COMMENT '人工调整标记(后台手动改写金额必填原因+留痕)',
  `manual_adjust_reason` varchar(500) DEFAULT NULL COMMENT '人工调整原因',
  `settle_staff_id` bigint DEFAULT NULL COMMENT '结算人',
  `settle_time` datetime DEFAULT NULL COMMENT '结算时间',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人姓名',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reward_no` (`reward_no`),
  UNIQUE KEY `uk_order_referrer_level` (`service_order_id`,`referrer_client_id`,`level`),
  KEY `idx_referrer_status` (`referrer_client_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='奖励流水(成交落库自动计算入单;可追溯可查账;推荐人看板数据源)';

DROP TABLE IF EXISTS `t_withdraw_record`;
CREATE TABLE `t_withdraw_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `withdraw_no` varchar(32) NOT NULL COMMENT '提现单号',
  `reward_id` bigint NOT NULL COMMENT '奖励单ID',
  `transfer_no` varchar(64) DEFAULT NULL COMMENT '微信转账单号(商家转账到零钱)',
  `amount` decimal(12,2) NOT NULL COMMENT '金额',
  `status` varchar(16) NOT NULL DEFAULT 'WAITING' COMMENT '状态(WAITING待转账/TRANSFERRING转账中/SUCCESS成功/FAILED失败)',
  `fail_reason` varchar(500) DEFAULT NULL COMMENT '失败原因',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT '重试次数(失败自动重试3次后转人工)',
  `arrive_time` datetime DEFAULT NULL COMMENT '到账时间',
  `operator` varchar(64) DEFAULT NULL COMMENT '操作人',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人姓名',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_withdraw_no` (`withdraw_no`),
  KEY `idx_reward` (`reward_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='提现流水(审核通过自动触发微信商家转账到零钱,无需客户操作)';

-- ============================================================
-- 六、短信域（3 张）—— 对齐 tse SmsFacade 三表骨架
-- ============================================================

DROP TABLE IF EXISTS `t_sms_template`;
CREATE TABLE `t_sms_template` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `template_code` varchar(64) NOT NULL COMMENT '模板编码',
  `template_name` varchar(128) NOT NULL COMMENT '模板名称',
  `content` varchar(1000) NOT NULL COMMENT '模板内容(变量占位符如${code})',
  `params_json` json DEFAULT NULL COMMENT '变量占位符定义',
  `sign_name` varchar(64) NOT NULL COMMENT '短信签名(腾讯云报备)',
  `sms_type` varchar(32) NOT NULL COMMENT '短信类型(LOGIN_VERIFY登录验证/NOTIFICATION通知-邀请成功/MARKETING业务营销)',
  `provider_template_id` varchar(64) DEFAULT NULL COMMENT '腾讯云模板ID',
  `freq_strategy` varchar(128) DEFAULT NULL COMMENT '频控策略(验证码60s间隔/单号日上限/营销按模板限频)',
  `unsubscribe_required` tinyint NOT NULL DEFAULT '0' COMMENT '营销短信强制回T退订标记',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '启停开关(后台可配改文案不发版)',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人姓名',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_template_code` (`template_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='短信模板(三类场景;复用tse SmsFacade+SmsTypeEnum骨架)';

DROP TABLE IF EXISTS `t_sms_record`;
CREATE TABLE `t_sms_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `phone` varchar(256) NOT NULL COMMENT '手机号(AES加密,发送时解密)',
  `phone_hash` varchar(64) DEFAULT NULL COMMENT '手机号SHA-256哈希(按手机号查询)',
  `sms_type` varchar(32) NOT NULL COMMENT '短信类型',
  `template_id` bigint NOT NULL COMMENT '模板ID',
  `params_snapshot` json DEFAULT NULL COMMENT '变量快照',
  `content` varchar(1000) DEFAULT NULL COMMENT '发送内容快照',
  `channel_code` varchar(32) DEFAULT NULL COMMENT '发送通道(腾讯云,预留多通道)',
  `status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT '状态(PENDING待发送/SENT已发送/SUCCESS成功/FAIL失败)',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT '重试次数',
  `operator` varchar(64) DEFAULT NULL COMMENT '操作人(营销群发留痕)',
  `send_time` datetime DEFAULT NULL COMMENT '发送时间',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_phone_hash_time` (`phone_hash`,`created_at`),
  KEY `idx_type_status` (`sms_type`,`status`),
  KEY `idx_send_time` (`send_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='短信发送记录(全量落库;营销仅发已留资名单)';

DROP TABLE IF EXISTS `t_sms_receipt`;
CREATE TABLE `t_sms_receipt` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `record_id` bigint NOT NULL COMMENT '发送记录ID',
  `deliver_status` varchar(16) NOT NULL COMMENT '送达状态(DELIVERED送达/FAILED失败)',
  `fail_reason` varchar(255) DEFAULT NULL COMMENT '失败原因',
  `reply_content` varchar(255) DEFAULT NULL COMMENT '回复内容(含TD退订,回TD自动进退订黑名单永久不扰)',
  `unsubscribe_flag` tinyint NOT NULL DEFAULT '0' COMMENT '退订标记',
  `callback_time` datetime NOT NULL COMMENT '回调时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_record` (`record_id`),
  KEY `idx_unsubscribe` (`unsubscribe_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='短信回执(送达回执/失败原因/重试记录/回复内容)';
-- ============================================================
-- 七、线索域（6 张）—— 主表+双客群扩展；千万级性能四板斧
-- ============================================================

DROP TABLE IF EXISTS `t_lead`;
CREATE TABLE `t_lead` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `lead_no` varchar(64) NOT NULL COMMENT '线索编号(业务ID:lead+32位随机)',
  `lead_type` varchar(16) NOT NULL COMMENT '客群(ENTERPRISE企业/PERSONAL个人,可扩展新客群)',
  `contact_name` varchar(64) NOT NULL COMMENT '联系人',
  `phone` varchar(256) NOT NULL COMMENT '手机号(AES加密)',
  `phone_hash` varchar(64) NOT NULL COMMENT '手机号SHA-256哈希(查重与等值查询,加密不影响索引)',
  `source` varchar(16) NOT NULL COMMENT '来源(BOSS老板/ADVISER员工顾问/CHANNEL渠道/VIP客户;CHANNEL待终审通过后进公海,VIP直接进公海)',
  `recorder_staff_code` varchar(64) DEFAULT NULL COMMENT '录入主体业务编码(员工工号或渠道稳定userNo)',
  `owner_staff_code` varchar(32) DEFAULT NULL COMMENT '归属人工号(NULL=公海)',
  `follow_status` varchar(32) NOT NULL DEFAULT 'NEW' COMMENT '状态(PENDING_APPROVAL待渠道终审/NEW新线索/REJECTED驳回/INTENTION有意向/POTENTIAL潜在/VISITED已拜访/NO_ANSWER未接通/NO_NEED无意向)',
  `last_followed_at` datetime DEFAULT NULL COMMENT '最后跟进时间(回收扫描依据)',
  `assign_blocked_until` datetime DEFAULT NULL COMMENT '认领冷却截止(回收后冷却期内原归属人不能认领/被指派)',
  `client_profile_code` varchar(64) DEFAULT NULL COMMENT '转正客户编码(业务ID:client+32位随机,CONVERT流转)',
  `ext_json` json DEFAULT NULL COMMENT '扩展预留(未沟通字段先落JSON不改表)',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人姓名',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lead_no` (`lead_no`),
  UNIQUE KEY `uk_phone_hash_type` (`phone_hash`,`lead_type`),
  KEY `idx_owner_status_follow` (`owner_staff_code`,`follow_status`,`last_followed_at`),
  KEY `idx_public_pool` (`lead_type`,`owner_staff_code`,`follow_status`),
  KEY `idx_recorder_source_status` (`recorder_staff_code`,`source`,`follow_status`,`created_at`),
  KEY `idx_client_profile_code` (`client_profile_code`),
  KEY `idx_created_at` (`created_at`),
  KEY `idx_updated_at` (`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='线索主表(唯一入口;归属/回收/公海/流转/定时任务只作用主表,逻辑只写一份)';

DROP TABLE IF EXISTS `t_lead_ent_ext`;
CREATE TABLE `t_lead_ent_ext` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `lead_id` bigint NOT NULL COMMENT '线索ID(1:1)',
  `company_name` varchar(128) DEFAULT NULL COMMENT '企业名称',
  `credit_code` varchar(256) DEFAULT NULL COMMENT '统一社会信用代码(AES加密)',
  `credit_code_hash` varchar(64) DEFAULT NULL COMMENT '信用代码SHA-256哈希(查重)',
  `industry` varchar(64) DEFAULT NULL COMMENT '行业',
  `found_years` int DEFAULT NULL COMMENT '成立年限',
  `tax_level` varchar(16) DEFAULT NULL COMMENT '纳税等级(A/B/C/D/M)',
  `annual_tax_amount` decimal(14,2) DEFAULT NULL COMMENT '年纳税额(元)',
  `annual_invoice_amount` decimal(14,2) DEFAULT NULL COMMENT '年开票额(元)',
  `ext_json` json DEFAULT NULL COMMENT '扩展预留',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人姓名',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lead_id` (`lead_id`),
  KEY `idx_credit_code_hash` (`credit_code_hash`),
  KEY `idx_company_name` (`company_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='企业线索扩展(1:1;当前主链路)';

DROP TABLE IF EXISTS `t_lead_person_ext`;
CREATE TABLE `t_lead_person_ext` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `lead_id` bigint NOT NULL COMMENT '线索ID(1:1)',
  `name` varchar(64) DEFAULT NULL COMMENT '姓名',
  `age` int DEFAULT NULL COMMENT '年龄',
  `city` varchar(64) DEFAULT NULL COMMENT '城市',
  `house_flag` tinyint DEFAULT NULL COMMENT '房产(0/1)',
  `car_flag` tinyint DEFAULT NULL COMMENT '车辆(0/1)',
  `social_security_flag` tinyint DEFAULT NULL COMMENT '社保(0/1)',
  `fund_flag` tinyint DEFAULT NULL COMMENT '公积金(0/1)',
  `demand_amount` decimal(14,2) DEFAULT NULL COMMENT '需求额度(元)',
  `fund_purpose` varchar(128) DEFAULT NULL COMMENT '资金用途',
  `credit_overview` varchar(255) DEFAULT NULL COMMENT '征信概况',
  `ext_json` json DEFAULT NULL COMMENT '扩展预留(个人产品上线后直接放开无需改表)',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人姓名',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lead_id` (`lead_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='个人线索扩展(1:1;占位,认证/匹配入口预留置灰)';

DROP TABLE IF EXISTS `t_lead_recycle_config`;
CREATE TABLE `t_lead_recycle_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `follow_status` varchar(16) NOT NULL COMMENT '跟进状态(逐行配置,新增状态行即生效)',
  `recycle_enabled` tinyint NOT NULL DEFAULT '1' COMMENT '回收开关',
  `recycle_days` int NOT NULL COMMENT '回收天数(超过无跟进自动回收)',
  `warn_days` int NOT NULL DEFAULT '3' COMMENT '预警天数(距回收剩余时站内+订阅消息警告归属人)',
  `cooldown_days` int NOT NULL DEFAULT '7' COMMENT '冷却天数(回收后原归属人不可认领)',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_follow_status` (`follow_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='线索回收规则参数(全参数化不写死;参考tse migration124按状态配置回收天数,升级为独立配置表)';

DROP TABLE IF EXISTS `t_lead_allocation_record`;
CREATE TABLE `t_lead_allocation_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `lead_no` varchar(64) NOT NULL COMMENT '线索或客户业务编码',
  `action_type` varchar(16) NOT NULL COMMENT '流转类型(MANUAL手动指派/AUTO自动/CLAIM认领/RECYCLE回收/TRANSFER离职转移/CONVERT转正客户)',
  `from_staff_code` varchar(32) DEFAULT NULL COMMENT '原归属人员工工号',
  `to_staff_code` varchar(32) DEFAULT NULL COMMENT '新归属人员工工号',
  `operator` varchar(64) NOT NULL COMMENT '操作人姓名',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_lead_no` (`lead_no`),
  KEY `idx_action_time` (`action_type`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='线索流转记录(谁录入/谁认领/谁回收全程可追溯)';

DROP TABLE IF EXISTS `t_client_allocation_approval`;
CREATE TABLE `t_client_allocation_approval` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `approval_no` varchar(64) NOT NULL COMMENT '审批单号(业务ID:alloc+32位随机)',
  `client_code` varchar(64) NOT NULL COMMENT '客户编码(业务ID:client+32位随机)',
  `applicant_staff_code` varchar(32) NOT NULL COMMENT '申请人(员工工号)',
  `from_owner_staff_code` varchar(32) DEFAULT NULL COMMENT '申请时原归属顾问工号(转移审批并发校验)',
  `pending_key` varchar(64) DEFAULT NULL COMMENT '待审唯一键(PENDING时=client_code,完成后NULL)',
  `apply_source` varchar(32) NOT NULL DEFAULT 'ADVISER_CLAIM' COMMENT '申请来源(ADVISER_CLAIM顾问认领/MANAGER_ASSIGN管理分配)',
  `apply_operator_code` varchar(32) DEFAULT NULL COMMENT '发起操作人员工工号',
  `approve_status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT '状态(PENDING待审/APPROVED通过/REJECTED驳回)',
  `approver_staff_code` varchar(32) DEFAULT NULL COMMENT '审批人(运营/超管)',
  `approve_opinion` varchar(500) DEFAULT NULL COMMENT '审批意见(驳回必填)',
  `approved_at` datetime DEFAULT NULL COMMENT '审批完成时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_approval_no` (`approval_no`),
  UNIQUE KEY `uk_pending_key` (`pending_key`),
  KEY `idx_client_status` (`client_code`,`approve_status`),
  KEY `idx_status` (`approve_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户归属分配审批单(无归宿客户申请分配,运营/超管审批后归属流转)';

DROP TABLE IF EXISTS `t_lead_archive`;
CREATE TABLE `t_lead_archive` (
  `id` bigint NOT NULL COMMENT '主键ID(保留原ID)',
  `lead_no` varchar(64) NOT NULL COMMENT '线索编号(业务ID:lead+32位随机)',
  `lead_type` varchar(16) NOT NULL COMMENT '客群',
  `contact_name` varchar(64) NOT NULL COMMENT '联系人',
  `phone` varchar(256) NOT NULL COMMENT '手机号(AES加密)',
  `phone_hash` varchar(64) NOT NULL COMMENT '手机号SHA-256哈希',
  `source` varchar(16) NOT NULL COMMENT '来源',
  `recorder_staff_id` bigint DEFAULT NULL COMMENT '录入人',
  `owner_staff_id` bigint DEFAULT NULL COMMENT '最后归属人',
  `follow_status` varchar(16) NOT NULL COMMENT '最终跟进状态',
  `last_followed_at` datetime DEFAULT NULL COMMENT '最后跟进时间',
  `client_profile_id` bigint DEFAULT NULL COMMENT '转正客户档案ID',
  `ext_json` json DEFAULT NULL COMMENT '扩展数据快照',
  `archive_reason` varchar(16) NOT NULL COMMENT '归档原因(CONVERTED转正/CLOSED关闭超期)',
  `archive_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '归档时间',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lead_no` (`lead_no`),
  KEY `idx_phone_hash` (`phone_hash`),
  KEY `idx_archive_time` (`archive_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='线索归档表(冷存储,结构与主表一致;主表只留活跃线索,归档数据可查询回溯)';

-- ============================================================
-- 八、VIP 域（2 张）—— 第二十轮 DDL 编制时补齐（原纪要 61 表缺 VIP 落表）
-- ============================================================

DROP TABLE IF EXISTS `t_vip_plan`;
CREATE TABLE `t_vip_plan` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `plan_code` varchar(32) NOT NULL COMMENT '档位编码',
  `plan_name` varchar(64) NOT NULL COMMENT '档位名称(月卡/季卡/年卡)',
  `duration_days` int NOT NULL COMMENT '时长(天)',
  `price` decimal(10,2) NOT NULL COMMENT '价格(元,后台改不硬编码,阶段一先上一档试水)',
  `benefits_json` json DEFAULT NULL COMMENT '权益说明(不限次匹配/命中维度明细/企微优先服务)',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态(ACTIVE/DISABLED)',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人姓名',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plan_code` (`plan_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='VIP档位价格配置(月卡/季卡/年卡,后台多档可配)';

DROP TABLE IF EXISTS `t_vip_order`;
CREATE TABLE `t_vip_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_no` varchar(64) NOT NULL COMMENT '订单号(业务ID:order+32位随机)',
  `client_profile_id` bigint NOT NULL COMMENT '客户档案ID',
  `plan_id` bigint NOT NULL COMMENT '档位ID',
  `amount` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '金额(元;受邀免费发放与手动发放为0)',
  `pay_type` varchar(32) NOT NULL COMMENT '获得方式(WECHAT_PAY在线付费/INVITED_FREE受邀免费自动/MANUAL_GRANT后台手动发放,VIP权益一致)',
  `wx_pay_no` varchar(64) DEFAULT NULL COMMENT '微信支付单号(商户号对公结算,sandbox先行)',
  `wx_refund_no` varchar(64) DEFAULT NULL COMMENT '微信退款单号',
  `status` varchar(16) NOT NULL DEFAULT 'PENDING_PAID' COMMENT '状态(PENDING_PAID待支付/PAID已支付/REFUNDED已退款/CLOSED已关闭)',
  `vip_start` datetime DEFAULT NULL COMMENT 'VIP开始时间',
  `vip_end` datetime NOT NULL COMMENT 'VIP到期时间(到期自动降级,T-7提醒续费)',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注(手动发放原因)',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人姓名',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_client_status` (`client_profile_id`,`status`),
  KEY `idx_wx_pay_no` (`wx_pay_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='VIP订单/发放记录(受邀免费+在线付费+手动发放统一入口;线上VIP支付不计入奖励基数)';

-- ============================================================
-- 九、风控与日志（2 张）
-- ============================================================

DROP TABLE IF EXISTS `t_blacklist`;
CREATE TABLE `t_blacklist` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `dimension` varchar(16) NOT NULL COMMENT '命中维度(PHONE手机号/ID_CARD身份证/CREDIT_CODE企业统一信用代码/LEGAL_PERSON法人)',
  `value` varchar(256) NOT NULL COMMENT '命中值(AES加密)',
  `value_hash` varchar(64) NOT NULL COMMENT '命中值SHA-256哈希(匹配用)',
  `reason_type` varchar(16) NOT NULL COMMENT '原因分类(FRAUD欺诈/DISHONEST失信/SENSITIVE敏感/OTHER其他)',
  `reason_remark` varchar(500) DEFAULT NULL COMMENT '原因说明',
  `status` varchar(16) NOT NULL DEFAULT 'EFFECTIVE' COMMENT '状态(EFFECTIVE生效/RELEASED已解禁)',
  `release_staff_id` bigint DEFAULT NULL COMMENT '解禁人(删除/解禁仅老板可操作)',
  `release_time` datetime DEFAULT NULL COMMENT '解禁时间',
  `created_by` varchar(64) DEFAULT NULL COMMENT '录入人姓名(顾问可查可增,提交即全局生效留痕到人)',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dimension_hash` (`dimension`,`value_hash`),
  KEY `idx_status` (`status`),
  KEY `idx_reason_type` (`reason_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='风控黑名单(全局前置风控命中直接REJECT;多维命中查询)';

DROP TABLE IF EXISTS `t_operation_log`;
CREATE TABLE `t_operation_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `biz_type` varchar(32) NOT NULL COMMENT '业务类型(产品/规则/计划/配置变更/渠道操作)',
  `biz_id` varchar(64) DEFAULT NULL COMMENT '业务对象ID',
  `action` varchar(64) NOT NULL COMMENT '操作动作(CREATE/UPDATE/DELETE/APPROVE/ENABLE等)',
  `detail_json` json DEFAULT NULL COMMENT '变更明细快照',
  `operator` varchar(64) NOT NULL COMMENT '操作人姓名',
  `operator_role` varchar(32) DEFAULT NULL COMMENT '操作人角色',
  `ip` varchar(64) DEFAULT NULL COMMENT 'IP',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间(保留≥3年)',
  PRIMARY KEY (`id`),
  KEY `idx_biz` (`biz_type`,`biz_id`),
  KEY `idx_operator` (`operator`),
  KEY `idx_time` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='操作日志(产品/规则/计划配置变更+渠道用户操作全留痕,保留≥3年)';

-- ============================================================
-- 九、识别记录（1 张）—— 第二十一轮定稿：图片识别 + AI 信息提取
-- ============================================================

DROP TABLE IF EXISTS `t_ocr_record`;
CREATE TABLE `t_ocr_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `biz_scene` varchar(32) NOT NULL COMMENT '场景(CLIENT_AUTH客户认证/CLIENT_SUBMIT资料提交/CHANNEL_PRODUCT渠道产品)',
  `biz_id` bigint DEFAULT NULL COMMENT '关联业务对象ID(客户档案ID/提交单ID/银行产品ID)',
  `file_key` varchar(255) NOT NULL COMMENT '原始文件key(阿里云OSS,复用tse OssStorageService)',
  `ocr_type` varchar(32) NOT NULL COMMENT '识别类型(provider名:mock默认未激活/vlm大模型视觉抽取)',
  `tencent_action` varchar(64) DEFAULT NULL COMMENT '腾讯云OCR接口名(BizLicenseOCR/VatInvoiceOCR等)',
  `extract_json` json DEFAULT NULL COMMENT '提取结果JSON(结构化字段值+各字段置信度)',
  `confidence_avg` decimal(4,2) DEFAULT NULL COMMENT '平均置信度',
  `manual_fix_json` json DEFAULT NULL COMMENT '人工修正JSON(识别仅辅助录入,人工可改)',
  `operator_type` varchar(16) NOT NULL COMMENT '操作端(CUSTOMER客户/CHANNEL渠道/STAFF员工)',
  `operator_id` bigint DEFAULT NULL COMMENT '操作人ID',
  `created_by` varchar(64) DEFAULT NULL COMMENT '操作人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '识别时间',
  `updated_at` datetime DEFAULT NULL COMMENT '更新时间(识别结果二次回填/人工修正)',
  `review_status` varchar(32) DEFAULT NULL COMMENT '复核状态(PENDING_REVIEW待复核/APPROVED已通过/REJECTED已驳回)',
  `visible_flag` tinyint(1) DEFAULT 0 COMMENT '客户可见标志(0不可见/1可见，审批通过后置1)',
  PRIMARY KEY (`id`),
  KEY `idx_scene_biz` (`biz_scene`,`biz_id`),
  KEY `idx_file_key` (`file_key`),
  KEY `idx_operator` (`operator_type`,`operator_id`),
  KEY `idx_ocr_review_status` (`review_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='图片识别记录(原图+提取JSON+置信度+人工修正,AI提取轨迹可追溯)';

DROP TABLE IF EXISTS `t_material_review`;
CREATE TABLE `t_material_review` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `review_no` varchar(64) NOT NULL COMMENT '复核单号(业务唯一ID: matrev + 32位随机)',
  `ocr_record_id` bigint DEFAULT NULL COMMENT '关联OCR记录主键(t_ocr_record.id)',
  `biz_type` varchar(32) DEFAULT NULL COMMENT '资料类型(ID_CARD/BUSINESS_LICENSE/FINANCIAL_STATEMENT/CONTRACT/DUE_DILIGENCE/OTHER)',
  `client_profile_code` varchar(64) DEFAULT NULL COMMENT '客户编码(业务ID)',
  `report_no` varchar(64) DEFAULT NULL COMMENT '关联报告编号(诊断材料回灌用)',
  `pending_facts_json` json DEFAULT NULL COMMENT '待复核事实JSON(VLM抽取后经t_extract_field_def映射的规范facts)',
  `review_status` varchar(32) NOT NULL DEFAULT 'PENDING_REVIEW' COMMENT '复核状态(PENDING_REVIEW/APPROVED/REJECTED)',
  `reviewer_staff_code` varchar(64) DEFAULT NULL COMMENT '审批人工号(业务编码)',
  `review_opinion` varchar(512) DEFAULT NULL COMMENT '审批意见(驳回必填)',
  `review_time` datetime DEFAULT NULL COMMENT '复核完成时间',
  `submission_no` varchar(64) DEFAULT NULL COMMENT '回灌后的提交单号',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人(上传操作人)',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_review_no` (`review_no`),
  KEY `idx_review_status` (`review_status`),
  KEY `idx_review_client` (`client_profile_code`),
  KEY `idx_review_report` (`report_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='材料复核审批单(上传识别结果门控，审批通过才回灌客数据)';

-- ============================================================
-- 九、系统配置（1 张）—— 第二十二轮定稿：第三方密钥配置项
-- ============================================================

DROP TABLE IF EXISTS `t_config`;
CREATE TABLE `t_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `config_group` varchar(32) NOT NULL COMMENT '配置组(TENCENT_OCR/QWEN_VL/SMS/OSS/WECHAT_PAY/BIZ_VERIFY)',
  `config_key` varchar(64) NOT NULL COMMENT '配置键(如 secretId/secretKey/region/model/endpoint/enabled)',
  `config_value` varchar(512) DEFAULT NULL COMMENT '配置值(密钥AES加密存储,后台仅显示脱敏掩码****)',
  `is_secret` tinyint NOT NULL DEFAULT '0' COMMENT '密钥标记(1=Secret敏感,后台脱敏展示;0=普通参数)',
  `description` varchar(255) DEFAULT NULL COMMENT '说明',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '启停',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人姓名',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_key` (`config_group`,`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='第三方服务配置项(后台可视化编辑+@RefreshScope动态刷新,密钥Secret仅脱敏展示)';

-- ============================================================
-- 九之补充 · 行业基准均值（#4a 经营诊断多维统计对比基线）
-- 完整 DDL + 种子数据见 db/migrate-industry-benchmark.sql，此处保留表结构以维护全量 DDL。
-- ============================================================
DROP TABLE IF EXISTS `t_industry_benchmark`;
CREATE TABLE `t_industry_benchmark` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `benchmark_no` varchar(64) NOT NULL COMMENT '基准编号(业务ID:benchmark+32位随机)',
  `industry_code` varchar(32) NOT NULL COMMENT '行业编码(GB/T4754门类,如C=制造业;DEFAULT=全行业兜底)',
  `industry_name` varchar(64) NOT NULL COMMENT '行业名称(用于自由文本归一化匹配)',
  `customer_group` varchar(16) NOT NULL DEFAULT 'ENTERPRISE' COMMENT '客群(ENTERPRISE企业/PERSONAL个人)',
  `dimension_code` varchar(32) NOT NULL COMMENT '维度编码(TAX_INTENSITY纳税强度/INVOICE_SCALE开票规模/OPERATE_YEARS经营时长/FINANCIAL_HEALTH财务健康/MATCH_OVERALL综合匹配)',
  `dimension_name` varchar(32) NOT NULL COMMENT '维度名称(与前端展示文案一致)',
  `avg_score` int NOT NULL COMMENT '行业均值(0-100归一化,诊断多维统计对比基线)',
  `p25_score` int DEFAULT NULL COMMENT '25分位(预留分层展示)',
  `p75_score` int DEFAULT NULL COMMENT '75分位(预留分层展示)',
  `sample_size` int DEFAULT NULL COMMENT '样本量(数据可信度提示)',
  `stat_period` varchar(16) NOT NULL COMMENT '统计周期(如2026H1/2025Y,同维度可多版本并存)',
  `data_source` varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '数据来源(MANUAL人工维护/INTERNAL内部样本统计/EXTERNAL第三方)',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态(ACTIVE生效/DISABLED停用)',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人姓名',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_benchmark_no` (`benchmark_no`),
  UNIQUE KEY `uk_ind_group_dim_period` (`industry_code`,`customer_group`,`dimension_code`,`stat_period`),
  KEY `idx_industry_status` (`industry_code`,`status`),
  KEY `idx_dim_status` (`dimension_code`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='行业基准均值(经营诊断多维统计对比基线;DEFAULT行兜底,替代MiniMatchService硬编码常量)';

-- ============================================================
-- t_api_permission（2026-09-01 从远程库补录，D26：schema 真源缺表）
-- ============================================================
CREATE TABLE `t_api_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `api_key` varchar(128) NOT NULL COMMENT '接口权限键(模块:方法名,如 order:page)',
  `http_method` varchar(16) NOT NULL DEFAULT 'ALL' COMMENT 'HTTP方法(GET/POST/PUT/DELETE/ALL)',
  `path_pattern` varchar(255) NOT NULL COMMENT '路径模式(Spring pattern,如 /api/admin/order/{orderNo})',
  `module_group` varchar(32) DEFAULT NULL COMMENT '模块分组(客户经营/产品与规则/运营支撑/系统管理/公共)',
  `client_types` varchar(64) NOT NULL DEFAULT 'WEB,MINI_APP' COMMENT '可用端(WEB/MINI_APP,逗号分隔)',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态(ACTIVE/DISABLED)',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注(接口用途)',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_api_key` (`api_key`),
  KEY `idx_path` (`path_pattern`),
  KEY `idx_group` (`module_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='接口权限定义(网关鉴权清单,运行时自动同步)';

-- ============================================================
-- t_role_api（2026-09-01 从远程库补录，D26：schema 真源缺表）
-- ============================================================
CREATE TABLE `t_role_api` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `role_code` varchar(32) NOT NULL COMMENT '角色编码(BOSS/DEPT_MANAGER/ADVISER/CHANNEL)',
  `api_key` varchar(128) NOT NULL COMMENT '接口权限键',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_api` (`role_code`,`api_key`),
  KEY `idx_role` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色接口授权(角色×接口;BOSS全量不落库)';

-- ============================================================
-- t_sensitive_view_grant（2026-09-01 从远程库补录，D26：schema 真源缺表）
-- ============================================================
CREATE TABLE `t_sensitive_view_grant` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_no` varchar(64) NOT NULL COMMENT '申请人工号(staff_code)',
  `lead_no` varchar(64) NOT NULL COMMENT '线索业务ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '授权时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_lead` (`user_no`,`lead_no`),
  KEY `idx_lead` (`lead_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='敏感数据查看授权(受限角色申请后授权,防并发重复)';

-- ============================================================
-- t_sensitive_view_log（2026-09-01 从远程库补录，D26：schema 真源缺表）
-- ============================================================
CREATE TABLE `t_sensitive_view_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_no` varchar(64) NOT NULL COMMENT '查看人工号',
  `lead_no` varchar(64) NOT NULL COMMENT '线索业务ID',
  `view_date` date NOT NULL COMMENT '查看日期(日限额统计)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '查看时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_date` (`user_no`,`view_date`),
  KEY `idx_lead` (`lead_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='敏感数据查看留痕(日限额30/天)';

-- ============================================================
-- t_notification（2026-09-01 从远程库补录，D26：schema 真源缺表）
-- ============================================================
CREATE TABLE `t_notification` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `notification_id` varchar(64) NOT NULL COMMENT '通知业务ID(noti+32位随机)',
  `user_no` varchar(64) NOT NULL COMMENT '接收人(员工工号staff_code/渠道账号/客户编号)',
  `type` varchar(32) NOT NULL COMMENT '通知类型(SYSTEM_NOTICE/LEAD_RECYCLE_WARN/PRODUCT_APPROVAL/SERVICE_ORDER)',
  `title` varchar(128) NOT NULL COMMENT '通知标题',
  `content` varchar(1000) NOT NULL COMMENT '通知内容',
  `related_id` varchar(64) DEFAULT NULL COMMENT '关联业务ID',
  `read_status` tinyint NOT NULL DEFAULT '0' COMMENT '0未读/1已读',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `read_at` datetime DEFAULT NULL COMMENT '阅读时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notification_id` (`notification_id`),
  KEY `idx_user_read` (`user_no`,`read_status`),
  KEY `idx_type_related` (`type`,`related_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='站内消息通知';

-- ============================================================
-- t_channel_user_list（2026-09-01 从远程库补录，D26：schema 真源缺表）
-- ============================================================
CREATE TABLE `t_channel_user_list` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `list_code` varchar(16) NOT NULL COMMENT '名单记录业务编码(culist+小写字母数字,总长16)',
  `channel_code` varchar(32) NOT NULL COMMENT '渠道编码',
  `customer_group` varchar(16) NOT NULL COMMENT '客群',
  `list_type` varchar(16) NOT NULL COMMENT 'LOCAL_WHITE/LOCAL_BLACK',
  `list_key` varchar(64) NOT NULL COMMENT '名单键',
  `created_by` varchar(64) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_list_code` (`list_code`),
  UNIQUE KEY `uk_channel_cg_type_key` (`channel_code`,`customer_group`,`list_type`,`list_key`),
  KEY `idx_channel_group_type` (`channel_code`,`customer_group`,`list_type`),
  KEY `idx_key` (`list_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='渠道本地白/黑名单';

-- ============================================================
-- t_bank_product_city（2026-09-01 从远程库补录，D26：schema 真源缺表）
-- ============================================================
CREATE TABLE `t_bank_product_city` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理主键',
  `product_city_code` varchar(16) NOT NULL COMMENT '产品城市关系业务编码(pcity+小写字母数字,总长16)',
  `product_code` varchar(64) NOT NULL COMMENT '产品业务唯一键',
  `province` varchar(64) NOT NULL COMMENT '省',
  `city` varchar(64) NOT NULL COMMENT '市(市一级)',
  `created_by` varchar(64) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_city_code` (`product_city_code`),
  UNIQUE KEY `uk_product_city` (`product_code`,`province`,`city`),
  KEY `idx_city` (`city`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='银行产品-服务城市';

-- ============================================================
-- 十、初始化种子数据（阶段一建库时执行）
-- ============================================================

-- 1. 三级角色
INSERT INTO `t_role` (`role_code`,`role_name`,`description`) VALUES
('BOSS','老板','跨部门全量数据+全部审批+全部配置,权限最大'),
('DEPT_MANAGER','部门主管','本部门全量数据+本部门审核+本部门配置'),
('ADVISER','顾问','本人数据+新增客户+公海认领+黑名单查询新增');

-- 2. 示例部门（老板直属/咨询部/市场部/运营部，后续自行增改）
INSERT INTO `t_department` (`dept_code`,`dept_name`,`parent_id`,`sort`) VALUES
('BOSS_DIRECT','老板直属',NULL,1),
('CONSULT','咨询部',NULL,2),
('MARKET','市场部',NULL,3),
('OPERATION','运营部',NULL,4);

-- 3. 线索回收规则参数（按跟进状态逐行配置，后台可改，此处为初始化示例值）
INSERT INTO `t_lead_recycle_config` (`follow_status`,`recycle_enabled`,`recycle_days`,`warn_days`,`cooldown_days`) VALUES
('NEW',1,7,3,7),
('INTENTION',1,15,3,7),
('POTENTIAL',1,30,5,7),
('VISITED',1,15,3,7),
('NO_ANSWER',1,7,3,7),
('NO_NEED',0,30,0,0);

-- 4. 短信模板初始化示例（LOGIN_VERIFY 登录验证码，模板/签名后台可配）
INSERT INTO `t_sms_template` (`template_code`,`template_name`,`content`,`sign_name`,`sms_type`,`freq_strategy`,`unsubscribe_required`,`enabled`) VALUES
('LOGIN_VERIFY','登录验证码','您的验证码为${code}，${minute}分钟内有效，请勿泄露。','贷款咨询','LOGIN_VERIFY','60s间隔/单号日上限10',0,1);

-- ============================================================
-- 说明：本文件共 66 张表（组织权限域7 + 匹配域31 + 渠道体系4 + 服务工单域5
--       + 奖励域3 + 短信域3 + 线索域6 + VIP域2 + 风控日志2 + 识别记录1 + 系统配置1
--       + 行业基准域1(t_industry_benchmark)）。
--       全部满足 utf8mb4_0900_ai_ci、审计四字段、加密+hash 规范。
-- ============================================================
