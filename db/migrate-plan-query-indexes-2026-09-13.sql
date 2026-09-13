-- 计划编排查询优化：业务编码、客群过滤、模块/步骤关联索引。
-- 可重复执行；线上执行前请先备份并核对索引是否已存在。
ALTER TABLE `t_admission_execution_plan`
  ADD INDEX `idx_plan_customer_group_updated` (`customer_group`, `updated_at`);
ALTER TABLE `t_admission_plan_module`
  ADD INDEX `idx_module_plan_id` (`plan_id`);
ALTER TABLE `t_admission_plan_step`
  ADD INDEX `idx_step_module_id` (`module_id`);
