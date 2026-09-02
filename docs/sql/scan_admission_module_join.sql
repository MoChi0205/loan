-- ============================================================================
-- 扫描 t_admission_plan_module 中违反 FR-03 连接符结构门禁的待修计划
-- 规则（准入引擎 AND/OR 聚合修复，对应 PlanOrchestrationService.validatePlanStructure）：
--   1) 末位模块不可为 OR（末位无"下一模块"，OR 语义无效）
--   2) 模块级禁止「连续 OR >= 3」（步骤级连续 OR 允许，模块级不允许）
-- 本脚本只读（SELECT），不修改任何数据；修改前请先备份并按下方修复指引执行。
-- 适用于 MySQL 8（窗口函数版本为主，末尾附 MySQL 5.7 变量版本回退）。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- A) 末位 OR：每个计划 sort 最大的模块，join_with_next_module = 'OR'
-- ---------------------------------------------------------------------------
SELECT
    m.plan_id,
    m.module_code,
    m.module_name,
    m.sort,
    m.join_with_next_module,
    '末位模块为 OR（无下一模块，语义无效）' AS issue
FROM t_admission_plan_module m
INNER JOIN (
    SELECT plan_id, MAX(sort) AS max_sort
    FROM t_admission_plan_module
    GROUP BY plan_id
) mx ON mx.plan_id = m.plan_id AND mx.max_sort = m.sort
WHERE m.join_with_next_module = 'OR'
ORDER BY m.plan_id, m.sort;

-- ---------------------------------------------------------------------------
-- B) 连续 OR >= 3：按 plan_id 内 sort 升序，存在连续 3 个模块均为 OR
--    （MySQL 8 窗口函数版）
-- ---------------------------------------------------------------------------
WITH ranked AS (
    SELECT
        plan_id, module_code, module_name, sort, join_with_next_module,
        ROW_NUMBER() OVER (PARTITION BY plan_id ORDER BY sort) AS rn
    FROM t_admission_plan_module
),
flags AS (
    SELECT plan_id, rn,
           IF(join_with_next_module = 'OR', 1, 0) AS is_or
    FROM ranked
)
SELECT DISTINCT
    r1.plan_id,
    '存在连续 3 个及以上模块 join_with_next_module=OR（违反模块级禁止连续 OR>=3）' AS issue,
    GROUP_CONCAT(
        CONCAT(r1.sort, ':', r1.module_code, '|',
               r2.sort, ':', r2.module_code, '|',
               r3.sort, ':', r3.module_code)
        ORDER BY r1.sort SEPARATOR ' ; '
    ) AS or_run_sample
FROM flags r1
JOIN flags r2 ON r2.plan_id = r1.plan_id AND r2.rn = r1.rn + 1
JOIN flags r3 ON r3.plan_id = r1.plan_id AND r3.rn = r1.rn + 2
WHERE r1.is_or = 1 AND r2.is_or = 1 AND r3.is_or = 1
ORDER BY r1.plan_id;

-- ---------------------------------------------------------------------------
-- C) 汇总：所有待修计划的 plan_id + 触发的规则（去重）
-- ---------------------------------------------------------------------------
SELECT plan_id,
       MAX(is_tail_or)   AS has_tail_or,
       MAX(is_consec_or) AS has_consecutive_or3
FROM (
    -- 末位 OR 标记
    SELECT m.plan_id, 1 AS is_tail_or, 0 AS is_consec_or
    FROM t_admission_plan_module m
    INNER JOIN (
        SELECT plan_id, MAX(sort) AS max_sort
        FROM t_admission_plan_module GROUP BY plan_id
    ) mx ON mx.plan_id = m.plan_id AND mx.max_sort = m.sort
    WHERE m.join_with_next_module = 'OR'

    UNION ALL

    -- 连续 OR>=3 标记（窗口函数）
    SELECT r1.plan_id, 0 AS is_tail_or, 1 AS is_consec_or
    FROM (
        SELECT plan_id, sort, join_with_next_module,
               ROW_NUMBER() OVER (PARTITION BY plan_id ORDER BY sort) AS rn
        FROM t_admission_plan_module
    ) r1
    JOIN (
        SELECT plan_id, sort, join_with_next_module,
               ROW_NUMBER() OVER (PARTITION BY plan_id ORDER BY sort) AS rn
        FROM t_admission_plan_module
    ) r2 ON r2.plan_id = r1.plan_id AND r2.rn = r1.rn + 1
    JOIN (
        SELECT plan_id, sort, join_with_next_module,
               ROW_NUMBER() OVER (PARTITION BY plan_id ORDER BY sort) AS rn
        FROM t_admission_plan_module
    ) r3 ON r3.plan_id = r1.plan_id AND r3.rn = r1.rn + 2
    WHERE r1.join_with_next_module = 'OR'
      AND r2.join_with_next_module = 'OR'
      AND r3.join_with_next_module = 'OR'
) t
GROUP BY plan_id
ORDER BY plan_id;

-- ============================================================================
-- 修复指引（确认无误后，在事务中执行；先备份 t_admission_plan_module）
-- ============================================================================
-- 1) 末位 OR -> 置为 AND（末位本就无下一模块，AND 为中性正确值）：
--    UPDATE t_admission_plan_module m
--    JOIN (SELECT plan_id, MAX(sort) AS max_sort FROM t_admission_plan_module GROUP BY plan_id) mx
--      ON mx.plan_id = m.plan_id AND mx.max_sort = m.sort
--    SET m.join_with_next_module = 'AND'
--    WHERE m.join_with_next_module = 'OR';
--
-- 2) 连续 OR>=3 -> 在每串 OR 的第 2 个位置插入一个 AND 断点（破坏连续 3 OR）。
--    推荐做法：由业务在「执行激活」前于 Web 端 PlanEdit 手工调整，或按业务语义
--    把连续 OR 串改写为 (A OR B) AND (C OR D)；切勿盲目全量改 AND 以免改变业务语义。
--    例：将某计划内 sort=2 的模块（处于 OR 串中段）改为 AND：
--    UPDATE t_admission_plan_module SET join_with_next_module = 'AND'
--    WHERE plan_id = <plan_id> AND sort = <断点 sort>;
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 回退：MySQL 5.7（无窗口函数）——用用户变量生成行号后再三表自连接
-- ---------------------------------------------------------------------------
-- SET @rn := 0, @pid := '';
-- SELECT DISTINCT r1.plan_id
-- FROM (
--     SELECT plan_id, sort, join_with_next_module,
--            @rn := IF(@pid = plan_id, @rn + 1, 1) AS rn,
--            @pid := plan_id
--     FROM t_admission_plan_module
--     ORDER BY plan_id, sort
-- ) r1
-- JOIN (
--     SELECT plan_id, sort, join_with_next_module,
--            @rn2 := IF(@pid2 = plan_id, @rn2 + 1, 1) AS rn,
--            @pid2 := plan_id
--     FROM t_admission_plan_module
--     ORDER BY plan_id, sort
-- ) r2 ON r2.plan_id = r1.plan_id AND r2.rn = r1.rn + 1
-- JOIN (
--     SELECT plan_id, sort, join_with_next_module,
--            @rn3 := IF(@pid3 = plan_id, @rn3 + 1, 1) AS rn,
--            @pid3 := plan_id
--     FROM t_admission_plan_module
--     ORDER BY plan_id, sort
-- ) r3 ON r3.plan_id = r1.plan_id AND r3.rn = r1.rn + 2
-- WHERE r1.join_with_next_module = 'OR'
--   AND r2.join_with_next_module = 'OR'
--   AND r3.join_with_next_module = 'OR';
