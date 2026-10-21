-- ============================================================
-- loan_db 生产库基线（2026-10-21 由 SHOW CREATE TABLE 导出）
-- 来源: Nacos prd/application.properties(group=loan) -> 110.42.219.5:9306/loan_db
-- 用途: 文档基线曾与线上结构性偏差（76 处差异/7 张表缺失），导致
--       insert 漏设 NOT NULL 无默认列而报 "doesn't have a default value"。
--       本文件以生产库为准重新生成；表数 91。
-- 再同步: 执行 python3 scripts/schema-diff-against-live.py
-- ============================================================

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
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='准入执行计划(计划→模块顺序→步骤;配置向导Step3)';

CREATE TABLE `t_admission_plan_module` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `module_biz_code` varchar(64) NOT NULL,
  `plan_id` bigint NOT NULL COMMENT '计划ID',
  `module_code` varchar(64) NOT NULL COMMENT '模块编码',
  `module_name` varchar(128) NOT NULL COMMENT '模块名称',
  `logic_type` varchar(8) NOT NULL DEFAULT 'AND' COMMENT '模块逻辑(AND遇FAIL短路/OR遇PASS短路)',
  `is_global_pre` tinyint NOT NULL DEFAULT '0' COMMENT '全局前置风控模块(黑名单/失信/欺诈/区域/敏感行业,命中直接REJECT)',
  `sort` int NOT NULL DEFAULT '0' COMMENT '模块顺序',
  `join_with_next_module` varchar(8) NOT NULL DEFAULT 'AND' COMMENT '与下一模块连接(AND/OR)',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plan_module_biz_code` (`module_biz_code`),
  UNIQUE KEY `uk_plan_module_code` (`plan_id`,`module_code`),
  KEY `idx_plan_sort` (`plan_id`,`sort`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='计划模块(配置向导Step4编排模块AND/OR)';

CREATE TABLE `t_admission_plan_step` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `step_code` varchar(64) NOT NULL,
  `module_id` bigint NOT NULL COMMENT '模块ID',
  `rule_id` bigint NOT NULL COMMENT '规则ID',
  `rule_version_id` bigint NOT NULL COMMENT '规则版本ID(步骤=单条规则)',
  `step_sort` int NOT NULL DEFAULT '0' COMMENT '步骤顺序',
  `join_with_next` varchar(8) NOT NULL DEFAULT 'AND' COMMENT '与下一步骤连接(AND/OR)',
  `is_dry_run` tinyint NOT NULL DEFAULT '0' COMMENT '步骤级空跑(1=命中REJECT改写PASS)',
  `step_config_json` text COMMENT '步骤参数配置(schema驱动动态表单)',
  `condition_field` varchar(64) DEFAULT NULL COMMENT '步骤前置条件-字段(fact字段码)',
  `condition_operator` varchar(16) DEFAULT NULL COMMENT '步骤前置条件-运算符(EQ/NE/IN/NOT_IN/IS_BLANK/IS_NOT_BLANK)',
  `condition_value` varchar(512) DEFAULT NULL COMMENT '步骤前置条件-值',
  `expression_json` json DEFAULT NULL COMMENT '解析后表达式快照',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plan_step_code` (`module_id`,`step_code`),
  KEY `idx_module_sort` (`module_id`,`step_sort`),
  KEY `idx_rule` (`rule_id`)
) ENGINE=InnoDB AUTO_INCREMENT=51 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='计划步骤(步骤=单条规则;配置向导Step4)';

CREATE TABLE `t_allocation_quota_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `scope` varchar(16) NOT NULL COMMENT '适用范围(LEAD线索认领/CLIENT客户认领)',
  `daily_claim_limit` int NOT NULL DEFAULT '30' COMMENT '每员工每日公海认领上限(0=不限);参照tse daily_assign_limit_per_user',
  `max_holding` int NOT NULL DEFAULT '0' COMMENT '每员工持有上限(0=不限);参照tse max_new_customers_per_user',
  `protect_days` int DEFAULT NULL COMMENT '数据保护期天数：进入公海后N天内不可被认领（0/NULL=不启用）',
  `daily_cooldown_limit` int DEFAULT NULL COMMENT '每日认领冷却条数：与每日认领上限取小生效（0/NULL=不启用）',
  `return_target` varchar(20) DEFAULT NULL COMMENT '退回/回收回纳目标：PUBLIC=公司公海(默认)/ORIGINAL=原部门团队池',
  `remark` varchar(255) DEFAULT NULL COMMENT '说明',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scope` (`scope`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='认领配额参数(全参数化不写死;参考tse daily_assign_limit_per_user/max_new_customers_per_user)';

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
) ENGINE=InnoDB AUTO_INCREMENT=722 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='接口权限定义(网关鉴权清单,运行时自动同步)';

CREATE TABLE `t_attachment_download_approval` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `approval_no` varchar(64) NOT NULL COMMENT '申请单号(业务ID:dldapr+32位随机)',
  `applicant_staff_code` varchar(32) NOT NULL COMMENT '申请人工号(业务编码)',
  `attachment_ids` varchar(500) NOT NULL COMMENT '资料清单(JSON数组附件ID)',
  `client_profile_code` varchar(64) DEFAULT NULL COMMENT '本次申请唯一所属客户编码',
  `attachment_summary_json` json DEFAULT NULL COMMENT '申请时冻结的客户/报告/文件明细快照',
  `purpose` varchar(500) NOT NULL COMMENT '用途说明(必填,如报送银行/纸质留存)',
  `expect_days` int DEFAULT NULL COMMENT '期望使用期限',
  `approver_staff_code` varchar(32) DEFAULT NULL COMMENT '审批人工号(业务编码)',
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
  KEY `idx_applicant_status` (`applicant_staff_code`,`approve_status`),
  KEY `idx_created_at` (`created_at`),
  KEY `idx_download_client` (`client_profile_code`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='无水印下载审批单(通过生成24h限时链接,超时作废重新申请;下载动作全量留痕)';

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
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='合作银行渠道';

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
  UNIQUE KEY `uk_channel_phone` (`bank_channel_id`,`phone_hash`),
  KEY `idx_bank_status` (`bank_channel_id`,`status`),
  KEY `idx_phone_hash` (`phone_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='银行渠道联系人库(管理端客户银行匹配明细展示数据源;一键拨号/复制企微)';

CREATE TABLE `t_bank_product` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `product_code` varchar(64) NOT NULL COMMENT '产品编码(内部代号化)',
  `bank_channel_code` varchar(32) NOT NULL COMMENT '所属银行渠道编码',
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
  `created_staff_no` varchar(32) DEFAULT NULL COMMENT '创建人工号(员工数据范围隔离)',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_code` (`product_code`),
  KEY `idx_bank_group_status` (`bank_channel_code`,`customer_group`,`status`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='银行产品全量库(审核通过入全量库;客户端永不展示产品名)';

CREATE TABLE `t_bank_product_city` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理主键',
  `product_city_code` varchar(16) NOT NULL COMMENT '产品城市关系业务编码(pcity+小写字母数字,总长16)',
  `product_code` varchar(64) NOT NULL COMMENT '产品业务唯一键',
  `province` varchar(64) NOT NULL COMMENT '省',
  `city` varchar(64) NOT NULL COMMENT '市(市一级)',
  `created_by` varchar(64) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_city` (`product_code`,`province`,`city`),
  UNIQUE KEY `uk_product_city_code` (`product_city_code`),
  KEY `idx_city` (`city`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='银行产品-服务城市';

CREATE TABLE `t_batch_import_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_no` varchar(64) NOT NULL,
  `import_type` varchar(20) NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `total_count` int NOT NULL DEFAULT '0',
  `success_count` int NOT NULL DEFAULT '0',
  `failed_count` int NOT NULL DEFAULT '0',
  `duplicate_count` int NOT NULL DEFAULT '0' COMMENT '重复数据条数（文件内重复）',
  `existing_count` int NOT NULL DEFAULT '0' COMMENT '系统已有数据条数',
  `pool_code` varchar(64) DEFAULT NULL COMMENT '导入目标资源池编码',
  `progress_percent` int NOT NULL DEFAULT '0',
  `applicant_staff_code` varchar(64) NOT NULL,
  `error_message` varchar(500) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_batch_task_no` (`task_no`),
  KEY `idx_batch_task_staff` (`status`,`applicant_staff_code`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `t_batch_import_task_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_no` varchar(64) NOT NULL,
  `row_no` int NOT NULL,
  `status` varchar(20) NOT NULL,
  `error_message` varchar(500) DEFAULT NULL,
  `result_json` text,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_batch_task_row` (`task_no`,`row_no`),
  KEY `idx_batch_task_detail` (`task_no`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `t_blacklist` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `dimension` varchar(16) NOT NULL COMMENT '命中维度(PHONE手机号/ID_CARD身份证/CREDIT_CODE企业统一信用代码/LEGAL_PERSON法人)',
  `value` varchar(256) NOT NULL COMMENT '命中值(AES加密)',
  `value_hash` varchar(64) NOT NULL COMMENT '命中值SHA-256哈希(匹配用)',
  `reason_type` varchar(16) NOT NULL COMMENT '原因分类(FRAUD欺诈/DISHONEST失信/SENSITIVE敏感/OTHER其他)',
  `reason_remark` varchar(500) DEFAULT NULL COMMENT '原因说明',
  `status` varchar(16) NOT NULL DEFAULT 'EFFECTIVE' COMMENT '状态(EFFECTIVE生效/RELEASED已解禁)',
  `release_staff_code` varchar(32) DEFAULT NULL COMMENT '解禁人工号(业务编码)',
  `release_time` datetime DEFAULT NULL COMMENT '解禁时间',
  `created_by` varchar(64) DEFAULT NULL COMMENT '录入人姓名(顾问可查可增,提交即全局生效留痕到人)',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dimension_hash` (`dimension`,`value_hash`),
  KEY `idx_status` (`status`),
  KEY `idx_reason_type` (`reason_type`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='风控黑名单(全局前置风控命中直接REJECT;多维命中查询)';

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
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='渠道银行账号(单一角色:录入草稿+查审核进度+收消息;不见任何客户资料匹配结果)';

CREATE TABLE `t_channel_user_list` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `list_code` varchar(16) NOT NULL COMMENT '名单记录业务编码(culist+小写字母数字,总长16)',
  `channel_code` varchar(32) NOT NULL COMMENT '渠道编码',
  `customer_group` varchar(16) NOT NULL COMMENT '客群',
  `list_type` varchar(16) NOT NULL COMMENT 'LOCAL_WHITE/LOCAL_BLACK',
  `list_key` varchar(64) NOT NULL COMMENT '名单键',
  `raw_key` varchar(64) DEFAULT NULL COMMENT '名单键明文（个人=手机号原文；企业=统一社会信用代码原文）',
  `created_by` varchar(64) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel_cg_type_key` (`channel_code`,`customer_group`,`list_type`,`list_key`),
  UNIQUE KEY `uk_list_code` (`list_code`),
  KEY `idx_channel_group_type` (`channel_code`,`customer_group`,`list_type`),
  KEY `idx_key` (`list_key`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='渠道本地白/黑名单';

CREATE TABLE `t_client_activity_event` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理主键，不对外暴露',
  `event_no` varchar(64) NOT NULL COMMENT '活动事件业务编号',
  `client_code` varchar(64) NOT NULL COMMENT '客户业务编号',
  `staff_code` varchar(32) DEFAULT NULL COMMENT '关联员工工号',
  `event_type` varchar(32) NOT NULL COMMENT '画像/材料/报告/跟进/预约/到店/外出/工单事件',
  `source_type` varchar(32) NOT NULL COMMENT '原始业务对象类型',
  `source_no` varchar(64) NOT NULL COMMENT '原始业务对象编号',
  `happened_at` datetime NOT NULL COMMENT '业务发生时间',
  `summary` varchar(500) NOT NULL COMMENT '事件摘要',
  `visibility` varchar(16) NOT NULL DEFAULT 'STAFF_ONLY' COMMENT 'STAFF_ONLY/CUSTOMER',
  `actor_type` varchar(16) NOT NULL COMMENT 'CUSTOMER/STAFF/SYSTEM',
  `actor_code` varchar(64) NOT NULL COMMENT '操作者业务编号',
  `metadata_json` json DEFAULT NULL COMMENT '裁剪后的扩展数据；禁止写银行准入或内部匹配结果到客户事件',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_activity_event_no` (`event_no`),
  KEY `idx_activity_client_time` (`client_code`,`happened_at`),
  KEY `idx_activity_source` (`source_type`,`source_no`),
  KEY `idx_activity_staff_time` (`staff_code`,`happened_at`),
  CONSTRAINT `chk_activity_actor_type` CHECK ((`actor_type` in (_utf8mb4'CUSTOMER',_utf8mb4'STAFF',_utf8mb4'SYSTEM'))),
  CONSTRAINT `chk_activity_visibility` CHECK ((`visibility` in (_utf8mb4'STAFF_ONLY',_utf8mb4'CUSTOMER')))
) ENGINE=InnoDB AUTO_INCREMENT=26 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户服务统一活动回放读模型（追加写）';

CREATE TABLE `t_client_allocation_approval` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `approval_no` varchar(64) NOT NULL COMMENT '审批单号(业务ID:alloc+32位随机)',
  `client_code` varchar(64) NOT NULL COMMENT '客户编码(业务ID:client+32位随机)',
  `applicant_staff_code` varchar(32) NOT NULL COMMENT '申请人(员工工号)',
  `from_owner_staff_code` varchar(32) DEFAULT NULL COMMENT '申请时原归属顾问工号(转移审批并发校验)',
  `pending_key` varchar(64) DEFAULT NULL COMMENT '待审唯一键(PENDING时=client_code,完成后NULL)',
  `apply_source` varchar(32) NOT NULL DEFAULT 'ADVISER_CLAIM' COMMENT '申请来源(ADVISER_CLAIM顾问认领/MANAGER_ASSIGN管理分配/ADVISER_TRANSFER顾问转移)',
  `apply_operator_code` varchar(32) DEFAULT NULL COMMENT '发起操作人员工工号',
  `approve_status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT '状态(PENDING待审/APPROVED通过/REJECTED驳回)',
  `approval_stage` varchar(20) NOT NULL DEFAULT 'TEAM_REVIEW' COMMENT 'TEAM_REVIEW/BOSS_REVIEW',
  `team_approver_staff_code` varchar(32) DEFAULT NULL,
  `team_approved_at` datetime DEFAULT NULL,
  `approver_staff_code` varchar(32) DEFAULT NULL COMMENT '审批人(运营/超管)',
  `approve_opinion` varchar(500) DEFAULT NULL COMMENT '审批意见(驳回必填)',
  `approved_at` datetime DEFAULT NULL COMMENT '审批完成时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_approval_no` (`approval_no`),
  UNIQUE KEY `uk_pending_key` (`pending_key`),
  KEY `idx_client_status` (`client_code`,`approve_status`),
  KEY `idx_status` (`approve_status`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户归属分配审批单(无归宿客户申请分配,运营/超管审批后归属流转)';

CREATE TABLE `t_client_appointment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理主键，不对外暴露',
  `appointment_no` varchar(64) NOT NULL COMMENT '预约业务编号',
  `client_code` varchar(64) NOT NULL COMMENT '客户业务编号',
  `order_no` varchar(64) DEFAULT NULL COMMENT '关联服务工单号',
  `host_staff_code` varchar(32) NOT NULL COMMENT '服务顾问工号',
  `appointment_type` varchar(32) NOT NULL COMMENT 'COMPANY_ON_SITE/HOME_VISIT/VIDEO_MEETING/PHONE_CONSULT',
  `scheduled_start` datetime NOT NULL COMMENT '预约开始时间',
  `scheduled_end` datetime NOT NULL COMMENT '预约结束时间',
  `location_name` varchar(128) DEFAULT NULL COMMENT '客户可见地点名称',
  `location_detail` varchar(255) DEFAULT NULL COMMENT '客户可见地点详情或会议说明',
  `status` varchar(16) NOT NULL DEFAULT 'REQUESTED' COMMENT 'REQUESTED/CONFIRMED/ARRIVED/SERVING/COMPLETED/CANCELLED/NO_SHOW/RESCHEDULED',
  `customer_confirm_status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/CONFIRMED；员工代建同样保留客户确认状态',
  `actual_arrived_at` datetime DEFAULT NULL COMMENT '公司现场实际到店时间',
  `actual_left_at` datetime DEFAULT NULL COMMENT '实际结束或离店时间',
  `cancel_reason` varchar(500) DEFAULT NULL COMMENT '取消或异常调整原因',
  `customer_visible_note` varchar(500) DEFAULT NULL COMMENT '客户可见说明及下一步安排',
  `internal_note` varchar(1000) DEFAULT NULL COMMENT '仅员工可见备注',
  `created_by_type` varchar(16) NOT NULL COMMENT 'CUSTOMER/STAFF',
  `created_by_code` varchar(64) NOT NULL COMMENT '创建人业务编号',
  `source_terminal` varchar(16) NOT NULL COMMENT 'WEB/MINI_APP/H5',
  `rescheduled_from_no` varchar(64) DEFAULT NULL COMMENT '改期来源预约号；原记录保留为RESCHEDULED',
  `created_by` varchar(64) DEFAULT NULL,
  `updated_by` varchar(64) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_appointment_no` (`appointment_no`),
  KEY `idx_appointment_client_time` (`client_code`,`scheduled_start`),
  KEY `idx_appointment_staff_status_time` (`host_staff_code`,`status`,`scheduled_start`),
  KEY `idx_appointment_day_type_status` (`scheduled_start`,`appointment_type`,`status`),
  KEY `idx_appointment_rescheduled_from` (`rescheduled_from_no`),
  CONSTRAINT `chk_appointment_creator_type` CHECK ((`created_by_type` in (_utf8mb4'CUSTOMER',_utf8mb4'STAFF'))),
  CONSTRAINT `chk_appointment_status` CHECK ((`status` in (_utf8mb4'REQUESTED',_utf8mb4'CONFIRMED',_utf8mb4'ARRIVED',_utf8mb4'SERVING',_utf8mb4'COMPLETED',_utf8mb4'CANCELLED',_utf8mb4'NO_SHOW',_utf8mb4'RESCHEDULED'))),
  CONSTRAINT `chk_appointment_terminal` CHECK ((`source_terminal` in (_utf8mb4'WEB',_utf8mb4'MINI_APP',_utf8mb4'H5'))),
  CONSTRAINT `chk_appointment_time` CHECK ((`scheduled_end` > `scheduled_start`)),
  CONSTRAINT `chk_appointment_type` CHECK ((`appointment_type` in (_utf8mb4'COMPANY_ON_SITE',_utf8mb4'HOME_VISIT',_utf8mb4'VIDEO_MEETING',_utf8mb4'PHONE_CONSULT')))
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户预约与四类服务履约记录';

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

CREATE TABLE `t_client_business_fact` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `client_profile_code` varchar(64) NOT NULL COMMENT '客户编码(业务ID)',
  `submission_no` varchar(64) NOT NULL COMMENT '提交单号(业务ID)',
  `field_code` varchar(64) NOT NULL COMMENT '字段编码',
  `field_value` varchar(512) DEFAULT NULL COMMENT '字段值',
  `field_type` varchar(16) NOT NULL DEFAULT 'STRING' COMMENT '字段类型',
  `extract_time` datetime DEFAULT NULL COMMENT '提取时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_client_field` (`field_code`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户经营事实(提取层;匹配引擎数据源)';

CREATE TABLE `t_client_follow_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理主键，不对外暴露',
  `follow_no` varchar(64) NOT NULL COMMENT '跟进业务编号',
  `client_code` varchar(64) NOT NULL COMMENT '客户业务编号',
  `order_no` varchar(64) DEFAULT NULL COMMENT '可选关联服务工单',
  `appointment_no` varchar(64) DEFAULT NULL COMMENT '可选关联预约',
  `staff_code` varchar(32) NOT NULL COMMENT '跟进员工工号',
  `channel_type` varchar(24) NOT NULL COMMENT 'PHONE/COMPANY_ON_SITE/HOME_VISIT/VIDEO_MEETING/WECOM/OTHER',
  `result_code` varchar(32) NOT NULL COMMENT '已联系/待补材料/已预约/已到店/暂缓/无意向等字典编码',
  `content` text NOT NULL COMMENT '内部跟进内容',
  `customer_visible_summary` varchar(500) DEFAULT NULL COMMENT '经裁剪的客户可见摘要',
  `next_action` varchar(500) DEFAULT NULL COMMENT '下一步动作',
  `next_follow_at` datetime DEFAULT NULL COMMENT '下次跟进时间',
  `visibility` varchar(16) NOT NULL DEFAULT 'STAFF_ONLY' COMMENT 'STAFF_ONLY/CUSTOMER',
  `created_by` varchar(64) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_follow_no` (`follow_no`),
  KEY `idx_follow_client_time` (`client_code`,`created_at`),
  KEY `idx_follow_staff_next` (`staff_code`,`next_follow_at`),
  CONSTRAINT `chk_follow_channel` CHECK ((`channel_type` in (_utf8mb4'PHONE',_utf8mb4'COMPANY_ON_SITE',_utf8mb4'HOME_VISIT',_utf8mb4'VIDEO_MEETING',_utf8mb4'WECOM',_utf8mb4'OTHER'))),
  CONSTRAINT `chk_follow_visibility` CHECK ((`visibility` in (_utf8mb4'STAFF_ONLY',_utf8mb4'CUSTOMER')))
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户级跟进记录；不依赖工单存在';

CREATE TABLE `t_client_insight_snapshot` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理主键，不对外暴露',
  `snapshot_no` varchar(64) NOT NULL COMMENT '画像快照业务编号',
  `client_code` varchar(64) NOT NULL COMMENT '客户业务编号',
  `report_no` varchar(64) DEFAULT NULL COMMENT '关联客户报告号，可为空',
  `snapshot_version` int NOT NULL COMMENT '同一客户递增版本',
  `dimension_json` json DEFAULT NULL COMMENT '经营规模/现金流/负债/回款/客户集中度/资料完整度等维度',
  `risk_flags_json` json DEFAULT NULL COMMENT '风险提示（不得写成审批结论）',
  `advice_json` json DEFAULT NULL COMMENT '经营改善建议（仅员工视角）',
  `source_summary_json` json DEFAULT NULL COMMENT '来源材料/授权时间/数据期间/可信度/核验状态',
  `customer_summary` varchar(500) DEFAULT NULL COMMENT '客户端可读的裁剪摘要（非承诺性措辞）',
  `generated_at` datetime NOT NULL COMMENT '生成时间',
  `generated_by` varchar(16) NOT NULL DEFAULT 'RULE' COMMENT 'AI/RULE/STAFF',
  `status` varchar(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/REVIEWED/CURRENT/ARCHIVED',
  `reviewed_by` varchar(64) DEFAULT NULL COMMENT '复核人姓名（到人留痕）',
  `reviewed_at` datetime DEFAULT NULL COMMENT '复核时间',
  `review_remark` varchar(500) DEFAULT NULL COMMENT '复核意见',
  `current_flag` tinyint GENERATED ALWAYS AS ((case when (`status` = _utf8mb4'CURRENT') then 1 else NULL end)) STORED COMMENT '唯一当前版本约束用生成列（仅 CURRENT 置 1）',
  `created_by` varchar(64) DEFAULT NULL,
  `updated_by` varchar(64) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_insight_snapshot_no` (`snapshot_no`),
  UNIQUE KEY `uk_insight_client_version` (`client_code`,`snapshot_version`),
  UNIQUE KEY `uk_insight_client_current` (`client_code`,`current_flag`),
  KEY `idx_insight_client_status` (`client_code`,`status`),
  KEY `idx_insight_client_generated` (`client_code`,`generated_at`),
  KEY `idx_insight_report_no` (`report_no`),
  CONSTRAINT `chk_insight_generated_by` CHECK ((`generated_by` in (_utf8mb4'AI',_utf8mb4'RULE',_utf8mb4'STAFF'))),
  CONSTRAINT `chk_insight_status` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'REVIEWED',_utf8mb4'CURRENT',_utf8mb4'ARCHIVED')))
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户画像版本快照（版本链非覆盖，同一客户仅一个 CURRENT）';

CREATE TABLE `t_client_lifecycle_event` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `client_code` varchar(64) NOT NULL COMMENT '客户业务编码',
  `event_type` varchar(32) NOT NULL COMMENT 'OWNER_ASSIGNED/FIRST_FOLLOW/ENTER_COMPANY_SEA/ENTER_TEAM_SEA/EXIT_SEA',
  `staff_code` varchar(32) DEFAULT NULL COMMENT '归属或跟进员工工号',
  `sea_level` varchar(16) DEFAULT NULL COMMENT 'ENTERPRISE/TEAM',
  `sea_dept_code` varchar(32) DEFAULT NULL,
  `episode_no` int NOT NULL DEFAULT '1' COMMENT '归属/公海周期号',
  `event_at` datetime NOT NULL COMMENT '事件发生时间',
  `operator_staff_code` varchar(32) DEFAULT NULL,
  `reason_code` varchar(64) DEFAULT NULL COMMENT '回收/释放原因',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_client_event_episode` (`client_code`,`event_type`,`episode_no`),
  KEY `idx_client_event_time` (`client_code`,`event_type`,`event_at`),
  KEY `idx_event_time` (`event_type`,`event_at`),
  KEY `idx_staff_event_time` (`staff_code`,`event_type`,`event_at`)
) ENGINE=InnoDB AUTO_INCREMENT=114 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户生命周期事件';

CREATE TABLE `t_client_profile` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `client_code` varchar(64) NOT NULL COMMENT '客户编码(业务ID:client+32位随机)',
  `customer_group` varchar(16) NOT NULL COMMENT '客群(ENTERPRISE/PERSONAL,身份选择后锁定硬分流)',
  `enterprise_name` varchar(128) DEFAULT NULL COMMENT '企业名称(企业客群)',
  `contact_name` varchar(64) DEFAULT NULL COMMENT '联系人姓名',
  `phone` varchar(256) DEFAULT NULL COMMENT '手机号(AES加密)',
  `phone_hash` varchar(64) DEFAULT NULL COMMENT '手机号SHA-256哈希(查重与等值查询)',
  `phone_plain` varchar(32) DEFAULT NULL COMMENT '手机号内部原值，禁止对外返回',
  `password` varchar(128) DEFAULT NULL COMMENT '密码(BCrypt，验证码重置后设置)',
  `wx_openid` varchar(64) DEFAULT NULL COMMENT '微信小程序openid',
  `wx_openid_hash` varchar(64) DEFAULT NULL COMMENT 'openid SHA-256哈希',
  `credit_code` varchar(256) DEFAULT NULL COMMENT '统一社会信用代码(AES加密,企业客群)',
  `credit_code_hash` varchar(64) DEFAULT NULL COMMENT '信用代码SHA-256哈希',
  `owner_staff_code` varchar(32) DEFAULT NULL COMMENT '归属顾问工号(业务编码)',
  `vip_level` varchar(16) DEFAULT NULL COMMENT 'VIP等级(NULL非会员/VIP)',
  `vip_expire_at` datetime DEFAULT NULL COMMENT 'VIP到期时间(T-7提醒续费,到期自动降级)',
  `invited_flag` tinyint NOT NULL DEFAULT '0' COMMENT '受邀标记(受邀用户免费自动VIP+独享分享推荐奖励)',
  `source` varchar(32) DEFAULT NULL COMMENT '来源(引荐绑定/公海认领/转正)',
  `pool_code` varchar(64) DEFAULT NULL COMMENT '所属资源池编码',
  `customer_tag` varchar(32) DEFAULT NULL COMMENT '客户经营标签(NO_NEED无需求/INTENTION意向/POTENTIAL潜在/DEAL成交/VISITED已来访/NO_ANSWER无人接听)',
  `lead_no` varchar(64) DEFAULT NULL COMMENT '来源线索编号(业务ID:lead+32位随机)',
  `wecom_added` tinyint NOT NULL DEFAULT '0' COMMENT '企微添加标记',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态(ACTIVE/DISABLED)',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `ext_json` json DEFAULT NULL COMMENT '扩展预留',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人姓名',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `last_followed_at` datetime DEFAULT NULL COMMENT '最后跟进时间(超期回收判定基准;归属/转移/跟进刷新)',
  `assign_blocked_until` datetime DEFAULT NULL COMMENT '回收冷却到期时间(回收进公海后原归属人不可认领/不可被直接分配)',
  `sea_level` varchar(16) DEFAULT NULL COMMENT '公海层级: ENTERPRISE/TEAM; 已分配为空',
  `sea_dept_code` varchar(16) DEFAULT NULL COMMENT '团队公海所属部门编码',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_client_code` (`client_code`),
  UNIQUE KEY `uk_phone_hash_group` (`phone_hash`,`customer_group`),
  UNIQUE KEY `uk_wx_openid` (`wx_openid`),
  KEY `idx_credit_code_hash` (`credit_code_hash`),
  KEY `idx_group_status` (`customer_group`,`status`),
  KEY `idx_created_at` (`created_at`),
  KEY `idx_updated_at` (`updated_at`),
  KEY `idx_wx_openid_hash` (`wx_openid_hash`),
  KEY `idx_client_sea_scope` (`sea_level`,`sea_dept_code`,`owner_staff_code`,`status`),
  KEY `idx_customer_tag` (`customer_tag`)
) ENGINE=InnoDB AUTO_INCREMENT=231 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户档案(线索认证通过后转正;数据权限按部门+角色隔离)';

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
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户回收规则参数(全参数化不写死;参考 t_lead_recycle_config 与 tse 资源池回收)';

CREATE TABLE `t_client_screening` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `report_no` varchar(64) NOT NULL COMMENT '报告编号(业务ID:report+32位随机)',
  `client_profile_code` varchar(64) NOT NULL COMMENT '客户编码(业务ID)',
  `match_trace_uuid` varchar(64) DEFAULT NULL COMMENT '匹配审计UUID',
  `template_code` varchar(64) DEFAULT NULL COMMENT '报告模板编码',
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
  `customer_snapshot_key` varchar(255) DEFAULT NULL COMMENT '客户视角快照对象键',
  `staff_snapshot_key` varchar(255) DEFAULT NULL COMMENT '员工视角快照对象键',
  `customer_pdf_key` varchar(255) DEFAULT NULL COMMENT '客户视角PDF对象键',
  `staff_pdf_key` varchar(255) DEFAULT NULL COMMENT '员工视角PDF对象键',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_report_no` (`report_no`),
  KEY `idx_client` (`client_profile_code`),
  KEY `idx_trace` (`match_trace_uuid`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='初筛报告(档位+数量展示;不含任何银行/产品名称)';

CREATE TABLE `t_client_submission` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `submission_no` varchar(64) NOT NULL COMMENT '提交单号(业务ID:submit+32位随机)',
  `client_profile_code` varchar(64) NOT NULL COMMENT '客户编码(业务ID)',
  `customer_group` varchar(16) NOT NULL COMMENT '客群',
  `data_json` json NOT NULL COMMENT '提交资料结构化JSON',
  `client_submit_id` varchar(64) NOT NULL COMMENT '客户端幂等键(防重复提交)',
  `status` varchar(16) NOT NULL DEFAULT 'SUBMITTED' COMMENT '状态(SUBMITTED已提交/MATCHING匹配中/MATCHED已匹配)',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `match_trace_no` varchar(64) DEFAULT NULL COMMENT '匹配链路业务编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_submission_no` (`submission_no`),
  UNIQUE KEY `uk_client_submit_id` (`client_submit_id`),
  KEY `idx_client_status` (`client_profile_code`,`status`),
  KEY `idx_client_code_status` (`client_profile_code`,`status`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户资料提交(幂等防重)';

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
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='第三方服务配置项(后台可视化编辑+@RefreshScope动态刷新,密钥Secret仅脱敏展示)';

CREATE TABLE `t_content_approval` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `approval_no` varchar(64) NOT NULL,
  `approval_type` varchar(32) NOT NULL COMMENT 'SMS_TEMPLATE/REPORT_TEMPLATE',
  `target_code` varchar(64) NOT NULL COMMENT '模板业务编码',
  `target_version` int DEFAULT NULL,
  `applicant_staff_code` varchar(64) NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `opinion` varchar(500) DEFAULT NULL,
  `approver_staff_code` varchar(64) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `approved_at` datetime DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_content_approval_no` (`approval_no`),
  KEY `idx_content_approval_pending` (`approval_type`,`status`,`created_at`),
  KEY `idx_content_approval_target` (`target_code`,`target_version`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='运营内容审批记录';

CREATE TABLE `t_department` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `dept_code` varchar(32) NOT NULL COMMENT '部门编码',
  `dept_name` varchar(64) NOT NULL COMMENT '部门名称',
  `parent_code` varchar(32) DEFAULT NULL COMMENT '上级部门编码(顶级为NULL)',
  `leader_staff_code` varchar(32) DEFAULT NULL COMMENT '负责人员工工号',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态(ACTIVE/DISABLED)',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人姓名',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dept_code` (`dept_code`),
  KEY `idx_parent_code` (`parent_code`)
) ENGINE=InnoDB AUTO_INCREMENT=29 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='部门(通用部门树,预置老板直属/咨询部/市场部/运营部)';

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
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='提取字段定义';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `t_invitation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `invitation_code` varchar(64) NOT NULL COMMENT '邀请码/短码(scene携带引荐码扫码自动绑定)',
  `invite_type` varchar(32) NOT NULL COMMENT '邀请类型(CHANNEL渠道/ENTERPRISE服务邀请-企业/PERSONAL服务邀请-个人)',
  `referrer_type` varchar(32) NOT NULL COMMENT '引荐人类型(BOSS老板/ADVISER员工顾问/CHANNEL渠道/VIP客户/CUSTOMER受邀用户推荐)',
  `referrer_id` bigint DEFAULT NULL COMMENT '引荐人ID(员工ID/渠道账号ID/客户档案ID)',
  `referrer_client_code` varchar(64) DEFAULT NULL COMMENT '引荐人客户编码(仅referrer_type=CUSTOMER)',
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
  KEY `idx_expire` (`expire_at`)
) ENGINE=InnoDB AUTO_INCREMENT=36 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='邀请凭证(仅referrer_type=CUSTOMER受邀用户推荐进入奖励结算;员工/渠道引荐只记归属不产生奖励)';

CREATE TABLE `t_lead` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `lead_no` varchar(64) NOT NULL COMMENT '线索编号(业务ID:lead+32位随机)',
  `lead_type` varchar(16) NOT NULL COMMENT '客群(ENTERPRISE企业/PERSONAL个人,可扩展新客群)',
  `pool_code` varchar(64) DEFAULT NULL COMMENT '所属资源池编码（导入/认领按池隔离）',
  `sea_level` varchar(16) DEFAULT NULL COMMENT '公海层级（COMPANY 公司 / TEAM 团队），NULL 视为公司级（兼容存量）',
  `sea_dept_code` varchar(64) DEFAULT NULL COMMENT '团队公海所属部门编码',
  `contact_name` varchar(64) NOT NULL COMMENT '联系人',
  `phone` varchar(256) NOT NULL COMMENT '手机号(AES加密)',
  `phone_hash` varchar(64) NOT NULL COMMENT '手机号SHA-256哈希(查重与等值查询,加密不影响索引)',
  `source` varchar(16) NOT NULL COMMENT '来源(BOSS老板/ADVISER员工顾问/CHANNEL渠道/VIP客户;渠道VIP录入不归属直接进公海)',
  `recorder_staff_code` varchar(64) DEFAULT NULL COMMENT '录入主体业务编码(员工工号或渠道稳定userNo)',
  `owner_staff_code` varchar(32) DEFAULT NULL COMMENT '归属人工号(NULL=公海)',
  `follow_status` varchar(16) NOT NULL DEFAULT 'NEW' COMMENT '跟进状态(NEW新线索/INTENTION有意向/POTENTIAL潜在/VISITED已拜访/NO_ANSWER未接通/NO_NEED无意向,对齐tse)',
  `last_followed_at` datetime DEFAULT NULL COMMENT '最后跟进时间(回收扫描依据)',
  `assign_blocked_until` datetime DEFAULT NULL COMMENT '认领冷却截止(回收后冷却期内原归属人不能认领/被指派)',
  `client_profile_code` varchar(64) DEFAULT NULL COMMENT '转正客户编码(业务ID:client+32位随机)',
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
  KEY `idx_created_at` (`created_at`),
  KEY `idx_updated_at` (`updated_at`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='线索主表(唯一入口;归属/回收/公海/流转/定时任务只作用主表,逻辑只写一份)';

CREATE TABLE `t_lead_allocation_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `lead_no` varchar(64) NOT NULL COMMENT '线索编号(业务ID:lead+32位随机)',
  `action_type` varchar(16) NOT NULL COMMENT '流转类型(MANUAL手动指派/AUTO自动/CLAIM认领/RECYCLE回收/TRANSFER离职转移/CONVERT转正客户)',
  `from_staff_code` varchar(32) DEFAULT NULL COMMENT '原归属人工号',
  `to_staff_code` varchar(32) DEFAULT NULL COMMENT '新归属人工号',
  `operator` varchar(64) NOT NULL COMMENT '操作人姓名',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_action_time` (`action_type`,`created_at`),
  KEY `idx_lead_no` (`lead_no`)
) ENGINE=InnoDB AUTO_INCREMENT=64 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='线索流转记录(谁录入/谁认领/谁回收全程可追溯)';

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
  KEY `idx_archive_time` (`archive_time`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='线索归档表(冷存储,结构与主表一致;主表只留活跃线索,归档数据可查询回溯)';

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
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='线索回收规则参数(全参数化不写死;参考tse migration124按状态配置回收天数,升级为独立配置表)';

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
) ENGINE=InnoDB AUTO_INCREMENT=159 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='匹配规则日志(双结果审计)';

CREATE TABLE `t_match_trace` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `trace_uuid` varchar(64) NOT NULL COMMENT '链路UUID(响应头X-Trace-Id+body双通道)',
  `client_profile_id` bigint DEFAULT NULL,
  `submission_id` bigint DEFAULT NULL,
  `customer_group` varchar(16) NOT NULL COMMENT '客群',
  `total_result` varchar(24) NOT NULL COMMENT '总结果(PASS/CONDITION/REJECT/SKIP_SEGMENT_MISMATCH/ERROR)',
  `hit_count` int NOT NULL DEFAULT '0' COMMENT '命中产品数',
  `step_count` int NOT NULL DEFAULT '0' COMMENT '执行步骤数',
  `duration_ms` bigint NOT NULL DEFAULT '0' COMMENT '耗时(毫秒)',
  `mismatch_flag` tinyint NOT NULL DEFAULT '0' COMMENT '双结果不一致标记(step_result与handler_step_result不一致标红)',
  `executed_at` datetime NOT NULL COMMENT '执行时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `client_profile_code` varchar(64) DEFAULT NULL COMMENT '客户业务编码',
  `submission_no` varchar(64) DEFAULT NULL COMMENT '提交单业务编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_trace_uuid` (`trace_uuid`),
  KEY `idx_client` (`client_profile_id`),
  KEY `idx_executed` (`executed_at`),
  KEY `idx_created_at` (`created_at`),
  KEY `idx_client_code` (`client_profile_code`),
  KEY `idx_submission_no` (`submission_no`)
) ENGINE=InnoDB AUTO_INCREMENT=21 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='匹配审计主表(全链路明细含命中产品——仅管理端可见)';

CREATE TABLE `t_material_review` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `review_no` varchar(64) NOT NULL,
  `ocr_file_key` varchar(255) DEFAULT NULL COMMENT 'OCR 文件业务键',
  `biz_type` varchar(32) DEFAULT NULL,
  `client_profile_code` varchar(64) DEFAULT NULL,
  `report_no` varchar(64) DEFAULT NULL,
  `pending_facts_json` json DEFAULT NULL,
  `review_status` varchar(32) NOT NULL DEFAULT 'PENDING_REVIEW',
  `reviewer_staff_code` varchar(64) DEFAULT NULL,
  `review_opinion` varchar(512) DEFAULT NULL,
  `review_time` datetime DEFAULT NULL,
  `submission_no` varchar(64) DEFAULT NULL,
  `created_by` varchar(64) DEFAULT NULL,
  `updated_by` varchar(64) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_review_no` (`review_no`),
  KEY `idx_review_status` (`review_status`),
  KEY `idx_review_ocr_file` (`ocr_file_key`),
  KEY `idx_review_client` (`client_profile_code`),
  KEY `idx_review_report` (`report_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='材料复核审批单';

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
) ENGINE=InnoDB AUTO_INCREMENT=641 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='菜单树(loan 20模块与子页面+操作按钮清单)';

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
) ENGINE=InnoDB AUTO_INCREMENT=66 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='站内消息通知';

CREATE TABLE `t_ocr_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `biz_scene` varchar(32) NOT NULL COMMENT '场景(CLIENT_AUTH客户认证/CLIENT_SUBMIT资料提交/CHANNEL_PRODUCT渠道产品)',
  `biz_id` bigint DEFAULT NULL COMMENT '关联业务对象ID(客户档案ID/提交单ID/银行产品ID)',
  `biz_code` varchar(64) DEFAULT NULL COMMENT '关联业务编码(reportNo/clientCode/productCode,红线#3业务ID查询)',
  `file_key` varchar(255) NOT NULL COMMENT '原始文件key(阿里云OSS,复用tse OssStorageService)',
  `ocr_type` varchar(32) NOT NULL COMMENT '识别类型(TENCENT_OCR腾讯云专项/QWEN_VL通义多模态)',
  `tencent_action` varchar(64) DEFAULT NULL COMMENT '腾讯云OCR接口名(BizLicenseOCR/VatInvoiceOCR等)',
  `extract_json` json DEFAULT NULL COMMENT '提取结果JSON(结构化字段值+各字段置信度)',
  `confidence_avg` decimal(4,2) DEFAULT NULL COMMENT '平均置信度',
  `manual_fix_json` json DEFAULT NULL COMMENT '人工修正JSON(识别仅辅助录入,人工可改)',
  `operator_type` varchar(16) NOT NULL COMMENT '操作端(CUSTOMER客户/CHANNEL渠道/STAFF员工)',
  `operator_id` bigint DEFAULT NULL COMMENT '操作人ID',
  `created_by` varchar(64) DEFAULT NULL COMMENT '操作人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '识别时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `review_status` varchar(32) DEFAULT NULL,
  `visible_flag` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_scene_biz` (`biz_scene`,`biz_id`),
  KEY `idx_file_key` (`file_key`),
  KEY `idx_operator` (`operator_type`,`operator_id`),
  KEY `idx_ocr_review_status` (`review_status`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='图片识别记录(原图+提取JSON+置信度+人工修正,AI提取轨迹可追溯)';

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
) ENGINE=InnoDB AUTO_INCREMENT=434 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='操作日志(产品/规则/计划配置变更+渠道用户操作全留痕,保留≥3年)';

CREATE TABLE `t_partner_product` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `bank_product_code` varchar(64) DEFAULT NULL COMMENT '关联银行产品编码(业务编码)',
  `cooperate_until` datetime NOT NULL COMMENT '合作库有效期(到期自动下架,历史匹配报告与审计永久保留)',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态(ACTIVE上架/EXPIRED到期/OFFLINE手动下架)',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人姓名',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_bank_product_code` (`bank_product_code`),
  KEY `idx_status_until` (`status`,`cooperate_until`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='合作库上架(对客可见权在我司;T-30/T-7两次到期提醒续签)';

CREATE TABLE `t_personal_auth` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `client_profile_code` varchar(64) DEFAULT NULL COMMENT '客户编码(业务ID:client+32位随机)',
  `auth_type` varchar(32) NOT NULL COMMENT '认证类型(ID_CARD_OCR身份证OCR/FACE_LIVENESS人脸活体/PHONE_THREE_ELEMENT手机三要素)',
  `auth_status` varchar(16) NOT NULL COMMENT '认证状态(PENDING/SUCCESS/FAIL)',
  `fail_reason` varchar(255) DEFAULT NULL COMMENT '失败原因',
  `auth_time` datetime DEFAULT NULL COMMENT '认证时间',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_client_type` (`client_profile_code`,`auth_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='个人认证记录(OCR+人脸活体+三要素,全过才认证)';

CREATE TABLE `t_personal_profile` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `client_profile_code` varchar(64) DEFAULT NULL COMMENT '客户编码(业务ID:client+32位随机)',
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
  UNIQUE KEY `uk_client_profile_code` (`client_profile_code`),
  KEY `idx_id_card_hash` (`id_card_hash`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='个人客户档案(1:1扩展)';

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
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='产品准入配置(产品↔执行计划绑定+产品级风控叠加)';

CREATE TABLE `t_product_approval` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `approval_no` varchar(64) NOT NULL COMMENT '审核工单号(业务ID:prdapr+32位随机)',
  `bank_product_code` varchar(64) NOT NULL COMMENT '银行产品编码(业务编码)',
  `channel_user_id` bigint DEFAULT NULL COMMENT '提交渠道账号ID；公司内部员工提交时为空',
  `apply_type` varchar(16) NOT NULL COMMENT '申请类型(CREATE新建/UPDATE变更,变更生成草稿副本前后快照)',
  `apply_reason` varchar(255) DEFAULT NULL COMMENT '申请理由(下线删除必填;变更填变更说明)',
  `before_snapshot_json` json DEFAULT NULL COMMENT '变更前快照',
  `after_snapshot_json` json DEFAULT NULL COMMENT '变更后快照(全程可追溯)',
  `duplicate_flag` tinyint NOT NULL DEFAULT '0' COMMENT '重复产品标记(同渠道同名/同额度区间自动比对标红)',
  `approve_status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT '审核状态(PENDING待审核/APPROVED通过/REJECTED驳回)',
  `approver_staff_code` varchar(32) DEFAULT NULL COMMENT '审核人工号(业务编码)',
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
  KEY `idx_channel` (`channel_user_id`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='产品审核工单(新建与变更都走审核;通过入全量库,上架合作库另二次操作)';

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
  `channel_code` varchar(32) DEFAULT NULL COMMENT '渠道编码',
  `bank_product_code` varchar(64) DEFAULT NULL COMMENT '银行产品业务编码(t_bank_product.product_code)',
  `execution_plan_code` varchar(64) DEFAULT NULL COMMENT '执行计划编码(t_admission_execution_plan.plan_code,1:1)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_strategy_code` (`strategy_code`),
  KEY `idx_ps_bank_product_code` (`bank_product_code`),
  KEY `idx_ps_execution_plan_code` (`execution_plan_code`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='产品策略(配置向导Step1挂策略)';

CREATE TABLE `t_product_strategy_bind` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `strategy_id` bigint NOT NULL COMMENT '策略ID',
  `bank_product_id` bigint NOT NULL COMMENT '银行产品ID',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_strategy_product` (`strategy_id`,`bank_product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='策略-产品绑定(配置向导Step0-1)';

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
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='报告模板版本(档位映射/免责声明/多维建议文案库随版本锁定)';

CREATE TABLE `t_resource_pool` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `pool_code` varchar(64) NOT NULL COMMENT '资源池编码（业务唯一）',
  `pool_name` varchar(128) NOT NULL COMMENT '资源池名称',
  `pool_type` varchar(32) NOT NULL DEFAULT 'COMPANY' COMMENT '池类型（COMPANY 公司 / TEAM 团队）',
  `dept_code` varchar(64) DEFAULT NULL COMMENT '团队池关联部门编码（COMPANY 池为空）',
  `dept_name` varchar(128) DEFAULT NULL COMMENT '团队池关联部门名称',
  `description` varchar(255) DEFAULT NULL COMMENT '池说明',
  `status` varchar(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态（ACTIVE/INACTIVE）',
  `recycle_enabled` tinyint NOT NULL DEFAULT '1' COMMENT '自动回收开关(1=开启;参考tse auto_recycle_enabled)',
  `recycle_days` int DEFAULT '30' COMMENT '默认回收天数',
  `warn_days` int DEFAULT '15' COMMENT '提前预警天数',
  `cooldown_days` int DEFAULT '7' COMMENT '回收冷却天数',
  `status_days_json` varchar(2000) DEFAULT NULL COMMENT '按跟进状态的无活跃回收天数JSON',
  `created_by` varchar(64) DEFAULT NULL,
  `updated_by` varchar(64) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pool_code` (`pool_code`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资源池定义（导入与公海分配的目标池）';

CREATE TABLE `t_resource_pool_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `pool_code` varchar(64) NOT NULL,
  `pool_name` varchar(128) NOT NULL,
  `scope_type` varchar(16) NOT NULL COMMENT 'COMPANY/TEAM/STAFF',
  `scope_key` varchar(64) DEFAULT NULL COMMENT '团队编码或员工工号',
  `enabled` tinyint NOT NULL DEFAULT '1',
  `recycle_enabled` tinyint NOT NULL DEFAULT '1',
  `recycle_days` int DEFAULT '30',
  `warn_days` int DEFAULT '15',
  `cooldown_days` int DEFAULT '7',
  `status_days_json` varchar(2000) DEFAULT NULL,
  `updated_by` varchar(64) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_resource_pool_scope` (`pool_code`),
  KEY `idx_resource_pool_scope` (`scope_type`,`scope_key`,`enabled`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资源池回收规则';

CREATE TABLE `t_reward_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `reward_no` varchar(64) NOT NULL COMMENT '奖励单号(业务ID:reward+32位随机)',
  `referrer_client_code` varchar(64) DEFAULT NULL COMMENT '推荐人客户编码(业务ID)',
  `referee_client_code` varchar(64) NOT NULL COMMENT '被推荐人客户编码(业务ID)',
  `level` tinyint NOT NULL DEFAULT '1' COMMENT '层级(1直推发奖励/2间推仅记录,层级封顶2层,第3层自动截断)',
  `service_order_no` varchar(64) NOT NULL COMMENT '关联工单号(业务ID)',
  `base_amount` decimal(14,2) NOT NULL COMMENT '基数金额快照(成交金额)',
  `rate_snapshot` decimal(6,4) NOT NULL COMMENT '比例快照',
  `rule_version` varchar(32) NOT NULL COMMENT '规则版本快照',
  `calc_process` varchar(500) DEFAULT NULL COMMENT '计算过程留痕',
  `reward_amount` decimal(12,2) NOT NULL COMMENT '奖励金额',
  `status` varchar(16) NOT NULL DEFAULT 'PENDING_AUDIT' COMMENT '状态机(PENDING_AUDIT待审核/GRANTED已发放/REJECTED已驳回/VOID已作废)',
  `reject_reason` varchar(500) DEFAULT NULL COMMENT '驳回原因(必填)',
  `manual_adjust_flag` tinyint NOT NULL DEFAULT '0' COMMENT '人工调整标记(后台手动改写金额必填原因+留痕)',
  `manual_adjust_reason` varchar(500) DEFAULT NULL COMMENT '人工调整原因',
  `settle_staff_code` varchar(32) DEFAULT NULL COMMENT '结算人(工号)',
  `settle_time` datetime DEFAULT NULL COMMENT '结算时间',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人姓名',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reward_no` (`reward_no`),
  UNIQUE KEY `uk_order_referrer_level` (`service_order_no`,`referrer_client_code`,`level`),
  KEY `idx_referrer_status` (`referrer_client_code`,`status`),
  KEY `idx_created_at` (`created_at`),
  KEY `idx_settle_time` (`settle_time`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='奖励流水(成交落库自动计算入单;可追溯可查账;推荐人看板数据源)';

CREATE TABLE `t_reward_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `rule_version` varchar(64) NOT NULL COMMENT '规则版本(业务唯一编码)',
  `direct_rate` decimal(6,4) NOT NULL COMMENT '直推比例(第1层X%)',
  `indirect_rate` decimal(6,4) DEFAULT NULL COMMENT '间推比例(第2层Y%,后台预留开关默认关)',
  `indirect_enabled` tinyint NOT NULL DEFAULT '0' COMMENT '间推开关(默认关闭,发1层存2层)',
  `base_caliber` varchar(64) NOT NULL DEFAULT 'SERVICE_ORDER_DEAL' COMMENT '基数口径(服务工单DEAL:CRM合同回写+线下补录;线上VIP支付不计入)',
  `product_code` varchar(64) DEFAULT NULL COMMENT '适用产品编码(精确匹配;NULL=历史未分配)',
  `customer_group` varchar(16) DEFAULT NULL COMMENT '适用客群(ENTERPRISE企业/PERSONAL个人;与product_code组合精确匹配)',
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
  UNIQUE KEY `uk_product_group` (`product_code`,`customer_group`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='推荐奖励规则(全部后台可改不写死)';

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
) ENGINE=InnoDB AUTO_INCREMENT=92 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色字典(三级角色,老板权限最大)';

CREATE TABLE `t_role_api` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `role_code` varchar(32) NOT NULL COMMENT '角色编码(BOSS/DEPT_MANAGER/ADVISER/CHANNEL)',
  `api_key` varchar(128) NOT NULL COMMENT '接口权限键',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_api` (`role_code`,`api_key`),
  KEY `idx_role` (`role_code`)
) ENGINE=InnoDB AUTO_INCREMENT=1150 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色接口授权(角色×接口;BOSS全量不落库)';

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
) ENGINE=InnoDB AUTO_INCREMENT=5074 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色权限(角色×菜单×操作按钮逐项勾选;保存仅补祖先expandWithAncestors,tse教训B-10)';

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
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='规则目录(双套规则目录;表达式{field_code}{operator}{value})';

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
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='规则分类(个人14条+企业16条,四分类树形管理)';

CREATE TABLE `t_rule_category_rel` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `rule_id` bigint NOT NULL COMMENT '规则ID',
  `category_id` bigint NOT NULL COMMENT '分类ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rule_category` (`rule_id`,`category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='规则-分类关联';

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

CREATE TABLE `t_rule_template_field` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `field_biz_code` varchar(64) NOT NULL,
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
  UNIQUE KEY `uk_field_biz_code` (`field_biz_code`),
  UNIQUE KEY `uk_template_field_code` (`template_id`,`field_code`),
  KEY `idx_template_sort` (`template_id`,`sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='规则模板-字段定义';

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
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='规则版本(validate-before-enable;历史版本永久保留可追溯)';

CREATE TABLE `t_screening_product` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `report_no` varchar(64) NOT NULL COMMENT '报告编号(业务ID:report+32位随机)',
  `bank_product_id` bigint DEFAULT NULL,
  `product_code` varchar(64) NOT NULL COMMENT '产品编码(内部代号化)',
  `hit_result` varchar(24) NOT NULL COMMENT '命中结果(PASS/CONDITION/REJECT)',
  `match_score` int NOT NULL DEFAULT '0' COMMENT '匹配度0-100(落库时按模块命中率计算)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_report_product` (`report_no`,`product_code`),
  KEY `idx_report` (`report_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='报告命中产品明细(员工陪访可见,客户永不展示产品名)';

CREATE TABLE `t_sensitive_view_approval` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `approval_no` varchar(64) NOT NULL COMMENT '敏感数据查看审批业务ID',
  `client_code` varchar(64) NOT NULL COMMENT '客户业务编码',
  `applicant_staff_code` varchar(64) NOT NULL COMMENT '申请员工工号',
  `applicant_role_code` varchar(32) NOT NULL,
  `applicant_dept_code` varchar(64) DEFAULT NULL,
  `view_date` date NOT NULL COMMENT '触发额度日期',
  `approver_staff_code` varchar(64) DEFAULT NULL,
  `approval_stage` varchar(20) NOT NULL COMMENT 'MANAGER_REVIEW/BOSS_REVIEW',
  `approve_status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `approve_opinion` varchar(500) DEFAULT NULL,
  `approved_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `pending_unique_key` varchar(255) GENERATED ALWAYS AS ((case when (`approve_status` = _utf8mb4'PENDING') then concat(`client_code`,_utf8mb4'#',`applicant_staff_code`,_utf8mb4'#',`view_date`) else NULL end)) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sensitive_view_approval_no` (`approval_no`),
  UNIQUE KEY `uk_sensitive_view_pending_only` (`pending_unique_key`),
  KEY `idx_sensitive_view_stage_status` (`approval_stage`,`approve_status`),
  KEY `idx_sensitive_view_applicant` (`applicant_staff_code`,`view_date`)
) ENGINE=InnoDB AUTO_INCREMENT=38 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='超出手机号查看额度审批单';

CREATE TABLE `t_sensitive_view_grant` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_no` varchar(64) NOT NULL COMMENT '申请人工号(staff_code)',
  `lead_no` varchar(64) DEFAULT NULL COMMENT '线索业务编码，与客户编码二选一',
  `client_code` varchar(64) DEFAULT NULL COMMENT '客户业务编码',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '授权时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_lead` (`user_no`,`lead_no`),
  UNIQUE KEY `uk_user_client` (`user_no`,`client_code`),
  KEY `idx_lead` (`lead_no`)
) ENGINE=InnoDB AUTO_INCREMENT=118 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='敏感数据查看授权(受限角色申请后授权,防并发重复)';

CREATE TABLE `t_sensitive_view_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_no` varchar(64) NOT NULL COMMENT '查看人工号',
  `lead_no` varchar(64) DEFAULT NULL COMMENT '线索业务编码，与客户编码二选一',
  `client_code` varchar(64) DEFAULT NULL COMMENT '客户业务编码',
  `view_date` date NOT NULL COMMENT '查看日期(日限额统计)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '查看时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_date` (`user_no`,`view_date`),
  KEY `idx_lead` (`lead_no`),
  KEY `idx_user_date_client` (`user_no`,`view_date`,`client_code`)
) ENGINE=InnoDB AUTO_INCREMENT=125 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='敏感数据查看留痕(日限额30/天)';

CREATE TABLE `t_service_attachment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_no` varchar(64) NOT NULL COMMENT '工单号(业务ID:order+32位随机)',
  `client_profile_code` varchar(64) NOT NULL COMMENT '客户编码(业务ID:client+32位随机)',
  `report_no` varchar(64) DEFAULT NULL COMMENT '关联初筛报告编号(业务ID:report+32位随机;诊断补充材料场景)',
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
  UNIQUE KEY `uk_service_attachment_file_key` (`file_key`),
  KEY `idx_upload_staff` (`upload_staff_id`),
  KEY `idx_order_no` (`order_no`),
  KEY `idx_client_type` (`client_profile_code`,`attachment_type`),
  KEY `idx_report_no` (`report_no`),
  KEY `idx_service_attachment_report_no` (`report_no`),
  KEY `idx_service_attachment_client_code` (`client_profile_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='服务资料库(查看/下载统一动态水印,原文件永不出库)';

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

CREATE TABLE `t_service_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_no` varchar(64) NOT NULL COMMENT '工单号(业务ID:order+32位随机)',
  `client_profile_code` varchar(64) NOT NULL COMMENT '客户编码(业务ID:client+32位随机)',
  `customer_group` varchar(16) NOT NULL COMMENT '客群',
  `bank_product_code` varchar(64) DEFAULT NULL COMMENT '关联银行产品编码(业务编码)',
  `owner_staff_code` varchar(32) DEFAULT NULL COMMENT '顾问员工工号(业务编码)',
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
  KEY `idx_status_dealtime` (`status`,`deal_time`),
  KEY `idx_client_status` (`client_profile_code`,`status`),
  KEY `idx_owner_status` (`owner_staff_code`,`status`),
  KEY `idx_created_at` (`created_at`),
  KEY `idx_updated_at` (`updated_at`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='服务工单(业务订单主表,支撑客户/推荐人/公司三视角;REFUND自动冲减营收与奖励)';

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

CREATE TABLE `t_sms_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `phone` varchar(256) NOT NULL COMMENT '手机号(AES加密,发送时解密)',
  `phone_hash` varchar(64) DEFAULT NULL COMMENT '手机号SHA-256哈希(按手机号查询)',
  `sms_type` varchar(32) NOT NULL COMMENT '短信类型',
  `template_code` varchar(64) DEFAULT NULL COMMENT '模板编码(业务编码)',
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
  KEY `idx_send_time` (`send_time`),
  KEY `idx_template_code` (`template_code`)
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='短信发送记录(全量落库;营销仅发已留资名单)';

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
  UNIQUE KEY `uk_template_code` (`template_code`),
  UNIQUE KEY `uk_sms_template_code` (`template_code`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='短信模板(三类场景;复用tse SmsFacade+SmsTypeEnum骨架)';

CREATE TABLE `t_staff` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `staff_code` varchar(32) NOT NULL COMMENT 'loan内部工号',
  `crm_user_id` varchar(64) NOT NULL COMMENT 'CRM员工ID(SSO映射键)',
  `staff_name` varchar(64) NOT NULL COMMENT '员工姓名',
  `dept_code` varchar(32) DEFAULT NULL COMMENT '所属部门编码(业务编码)',
  `role_code` varchar(32) NOT NULL COMMENT '角色(BOSS老板/DEPT_MANAGER部门主管/ADVISER顾问)',
  `wecom_qr_code` varchar(500) DEFAULT NULL COMMENT '个人企微二维码(报告页与企微引导位展示)',
  `phone` varchar(256) DEFAULT NULL COMMENT '手机号(AES加密)',
  `phone_hash` varchar(64) DEFAULT NULL COMMENT '手机号SHA-256哈希(查重)',
  `password` varchar(128) DEFAULT NULL COMMENT '密码(BCrypt，验证码重置后设置)',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态(ACTIVE在职/LEAVE离职停用,离职联动线索客户转移提醒)',
  `leave_time` datetime DEFAULT NULL COMMENT '离职时间',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人姓名',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '更新人姓名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_staff_code` (`staff_code`),
  UNIQUE KEY `uk_crm_user_id` (`crm_user_id`),
  KEY `idx_phone_hash` (`phone_hash`),
  KEY `idx_dept_role` (`dept_code`,`role_code`)
) ENGINE=InnoDB AUTO_INCREMENT=48 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='员工映射(CRM SSO员工→部门→角色三级绑定,不重复建账号)';

CREATE TABLE `t_staff_data_transfer_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `transfer_no` varchar(64) NOT NULL,
  `from_staff_code` varchar(64) NOT NULL,
  `to_staff_code` varchar(64) DEFAULT NULL,
  `business_type` varchar(32) NOT NULL,
  `business_code` varchar(128) NOT NULL,
  `reason` varchar(255) DEFAULT NULL,
  `operator` varchar(64) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_staff_transfer_no` (`transfer_no`),
  KEY `idx_staff_transfer_from` (`from_staff_code`,`created_at`),
  KEY `idx_staff_transfer_business` (`business_type`,`business_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='离职员工数据回收与继承审计';

CREATE TABLE `t_staff_outing` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理主键，不对外暴露',
  `outing_no` varchar(64) NOT NULL COMMENT '外出业务编号',
  `staff_code` varchar(32) NOT NULL COMMENT '外出员工工号',
  `client_code` varchar(64) DEFAULT NULL COMMENT '可选关联客户业务编号',
  `appointment_no` varchar(64) DEFAULT NULL COMMENT '可选关联上门预约号',
  `order_no` varchar(64) DEFAULT NULL COMMENT '关联服务工单号',
  `outing_type` varchar(32) NOT NULL DEFAULT 'GENERAL' COMMENT 'HOME_VISIT上门拜访/GENERAL普通外出',
  `planned_start` datetime NOT NULL,
  `planned_end` datetime NOT NULL,
  `submitted_at` datetime DEFAULT NULL COMMENT '提交审核时间(本人提交申请，不接受他人代录)',
  `actual_departed_at` datetime DEFAULT NULL COMMENT '出发打卡时间',
  `departed_location_ciphertext` varchar(1024) DEFAULT NULL COMMENT '出发单点定位加密载荷（经纬度/精度/位置文本/采集时间）',
  `departed_photo_key` varchar(255) DEFAULT NULL COMMENT '出发打卡照片fileKey(必填，现场凭证)',
  `actual_returned_at` datetime DEFAULT NULL COMMENT '返回打卡时间',
  `returned_location_ciphertext` varchar(1024) DEFAULT NULL COMMENT '返回单点定位加密载荷（经纬度/精度/位置文本/采集时间）',
  `returned_photo_key` varchar(255) DEFAULT NULL COMMENT '返回打卡照片fileKey(必填，现场凭证)',
  `destination` varchar(255) NOT NULL COMMENT '拜访目的地',
  `purpose` varchar(500) NOT NULL COMMENT '拜访目的',
  `status` varchar(16) NOT NULL DEFAULT 'PENDING_REVIEW' COMMENT 'PENDING_REVIEW待审核/READY审核通过待出发/REJECTED已驳回/IN_PROGRESS外出中/COMPLETED已完成/CANCELLED已取消/DRAFT草稿',
  `reviewer_staff_code` varchar(32) DEFAULT NULL COMMENT '审核人工号(禁止自审)',
  `reviewer_name` varchar(64) DEFAULT NULL COMMENT '审核人姓名(到人留痕)',
  `reviewed_at` datetime DEFAULT NULL COMMENT '审核时间',
  `review_remark` varchar(500) DEFAULT NULL COMMENT '审核意见(驳回必填原因)',
  `internal_note` varchar(1000) DEFAULT NULL,
  `created_by` varchar(64) DEFAULT NULL,
  `updated_by` varchar(64) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_outing_no` (`outing_no`),
  UNIQUE KEY `uk_outing_appointment` (`appointment_no`),
  KEY `idx_outing_staff_status_time` (`staff_code`,`status`,`planned_start`),
  KEY `idx_outing_client_time` (`client_code`,`planned_start`),
  KEY `idx_outing_status_time` (`status`,`planned_start`),
  CONSTRAINT `chk_outing_status` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'PENDING_REVIEW',_utf8mb4'REJECTED',_utf8mb4'READY',_utf8mb4'IN_PROGRESS',_utf8mb4'COMPLETED',_utf8mb4'CANCELLED'))),
  CONSTRAINT `chk_outing_time` CHECK ((`planned_end` > `planned_start`)),
  CONSTRAINT `chk_outing_type` CHECK ((`outing_type` in (_utf8mb4'HOME_VISIT',_utf8mb4'GENERAL')))
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='员工外出：可关联上门预约或普通外出，主管审核后双打卡(图片+单点定位)，无连续轨迹';

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
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='策略模板(模板导入复用)';

CREATE TABLE `t_strategy_template_module` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `module_biz_code` varchar(64) NOT NULL,
  `template_id` bigint NOT NULL COMMENT '策略模板ID',
  `module_code` varchar(64) NOT NULL COMMENT '模块编码',
  `module_name` varchar(128) NOT NULL COMMENT '模块名称',
  `logic_type` varchar(8) NOT NULL DEFAULT 'AND' COMMENT '模块逻辑(AND/OR)',
  `join_with_next_module` varchar(8) NOT NULL DEFAULT 'AND' COMMENT '与下一模块连接(AND/OR)',
  `sort` int NOT NULL DEFAULT '0' COMMENT '顺序',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_template_module_biz_code` (`module_biz_code`),
  UNIQUE KEY `uk_template_module_code` (`template_id`,`module_code`),
  KEY `idx_template_sort` (`template_id`,`sort`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='策略模板-模块编排';

CREATE TABLE `t_strategy_template_step` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `step_code` varchar(64) NOT NULL,
  `template_module_id` bigint NOT NULL COMMENT '模板模块ID',
  `rule_template_id` bigint DEFAULT NULL COMMENT '规则模板ID',
  `rule_id` bigint DEFAULT NULL COMMENT '直接引用规则ID',
  `expression_json` json DEFAULT NULL COMMENT '表达式快照',
  `step_sort` int NOT NULL DEFAULT '0' COMMENT '顺序',
  `join_with_next` varchar(8) NOT NULL DEFAULT 'AND' COMMENT '与下一步骤连接(AND/OR)',
  `is_dry_run` tinyint NOT NULL DEFAULT '0' COMMENT '步骤级空跑(1=命中REJECT改写PASS)',
  `step_config_json` text COMMENT '步骤参数配置(schema驱动动态表单)',
  `condition_field` varchar(64) DEFAULT NULL COMMENT '步骤前置条件-字段(fact字段码)',
  `condition_operator` varchar(16) DEFAULT NULL COMMENT '步骤前置条件-运算符(EQ/NE/IN/NOT_IN/IS_BLANK/IS_NOT_BLANK)',
  `condition_value` varchar(512) DEFAULT NULL COMMENT '步骤前置条件-值',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_template_step_code` (`template_module_id`,`step_code`),
  KEY `idx_module_sort` (`template_module_id`,`step_sort`)
) ENGINE=InnoDB AUTO_INCREMENT=33 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='策略模板-步骤';

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

CREATE TABLE `t_withdraw_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `withdraw_no` varchar(32) NOT NULL COMMENT '提现单号',
  `reward_no` varchar(64) DEFAULT NULL COMMENT '奖励单号(业务ID)',
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
  KEY `idx_status` (`status`),
  KEY `idx_reward_no` (`reward_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='提现流水(审核通过自动触发微信商家转账到零钱,无需客户操作)';
