-- =====================================================================
-- 迁移：t_product_strategy 关联字段业务 key 化（规范：无物理外键、业务 key 字符串关联）
-- 依赖：t_bank_product.product_code / t_admission_execution_plan.plan_code 已存在
-- 执行前请确认 t_product_strategy 已完成渠道策略 V2 迁移（migrate-channel-strategy.sql）
-- =====================================================================

-- 1. 新增业务 key 关联列
ALTER TABLE t_product_strategy
    ADD COLUMN bank_product_code   VARCHAR(64) NULL COMMENT '银行产品业务编码（t_bank_product.product_code）',
    ADD COLUMN execution_plan_code VARCHAR(64) NULL COMMENT '执行计划编码（t_admission_execution_plan.plan_code，1:1）';

-- 2. 数据回填：由旧 long id 关联反查业务编码
UPDATE t_product_strategy s
    JOIN t_bank_product p ON s.bank_product_id = p.id
    SET s.bank_product_code = p.product_code
    WHERE s.bank_product_id IS NOT NULL;

UPDATE t_product_strategy s
    JOIN t_admission_execution_plan pl ON s.execution_plan_id = pl.id
    SET s.execution_plan_code = pl.plan_code
    WHERE s.execution_plan_id IS NOT NULL;

-- 3. 辅助索引（业务 key 关联查询）
ALTER TABLE t_product_strategy
    ADD INDEX idx_product_strategy_bank_product_code (bank_product_code),
    ADD INDEX idx_product_strategy_execution_plan_code (execution_plan_code);

-- 4. 校验通过后，删除旧 long id 关联列（破坏性操作，确认回填完整后执行）
-- SELECT id, channel_code, bank_product_id, bank_product_code, execution_plan_id, execution_plan_code FROM t_product_strategy;
ALTER TABLE t_product_strategy
    DROP COLUMN bank_channel_id,
    DROP COLUMN bank_product_id,
    DROP COLUMN execution_plan_id;
