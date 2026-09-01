-- =====================================================================
-- 迁移：执行计划/策略模版 步骤级 AND/OR + 空跑 + 模块间 OR 组 + 步骤参数
-- 规格来源：docs/channel-admission-v2-design.md §2.4 / §2.6
--   - 模块级 join_with_next_module：与下一模块连接(AND/OR)，支持 OR 组短路
--   - 步骤级 join_with_next：与下一步骤连接(AND/OR)
--   - 步骤级 is_dry_run：空跑（Handler REJECT 时链上 step_result 改写 PASS，保留 handler_step_result）
--   - 步骤级 step_config_json：步骤参数配置（schema 驱动动态表单，对齐 mds）
-- 注意：生产库执行前请确认目标列不存在（重复执行会报 Duplicate column）
-- =====================================================================

-- 1. 执行计划：模块表补「与下一模块连接」
ALTER TABLE t_admission_plan_module
    ADD COLUMN join_with_next_module varchar(8) NOT NULL DEFAULT 'AND'
        COMMENT '与下一模块连接(AND/OR)' AFTER sort;

-- 2. 执行计划：步骤表补「与下一步骤连接 / 空跑 / 步骤参数」
ALTER TABLE t_admission_plan_step
    ADD COLUMN join_with_next varchar(8) NOT NULL DEFAULT 'AND'
        COMMENT '与下一步骤连接(AND/OR)' AFTER step_sort,
    ADD COLUMN is_dry_run tinyint NOT NULL DEFAULT '0'
        COMMENT '步骤级空跑(1=命中REJECT改写PASS)' AFTER join_with_next,
    ADD COLUMN step_config_json text NULL
        COMMENT '步骤参数配置(schema驱动动态表单)' AFTER is_dry_run;

-- 3. 策略模版：模块表补「与下一模块连接」（模版导入保留模块 OR 组语义）
ALTER TABLE t_strategy_template_module
    ADD COLUMN join_with_next_module varchar(8) NOT NULL DEFAULT 'AND'
        COMMENT '与下一模块连接(AND/OR)' AFTER logic_type;

-- 4. 策略模版：步骤表补「与下一步骤连接 / 空跑 / 步骤参数」
ALTER TABLE t_strategy_template_step
    ADD COLUMN join_with_next varchar(8) NOT NULL DEFAULT 'AND'
        COMMENT '与下一步骤连接(AND/OR)' AFTER step_sort,
    ADD COLUMN is_dry_run tinyint NOT NULL DEFAULT '0'
        COMMENT '步骤级空跑(1=命中REJECT改写PASS)' AFTER join_with_next,
    ADD COLUMN step_config_json text NULL
        COMMENT '步骤参数配置(schema驱动动态表单)' AFTER is_dry_run;
