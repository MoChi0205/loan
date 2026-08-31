# loan 渠道准入 V2 架构设计（完整规格 · 对齐 mds v2）

> **参考**：`/Users/admin/IdeaProjects/mds/docs/rule-engine/`
> **状态**：定稿（6 项关键决策已确认）

---

## 1. 最终决策（已确认）

| # | 决策 | 结论 |
|---|------|------|
| 1 | 产品挂哪 | **产品挂策略上**（策略 = 渠道×产品的准入策略） |
| 2 | 策略↔计划 | **1:1**（一个策略绑定一个执行计划） |
| 3 | 客群维度 | **放策略层**（`strategy.customer_group`） |
| 4 | 模版/级联删除/跨渠道复制 | **要**（对齐 mds） |
| 5 | 模块/步骤 OR 组 | **要 `join_with_next` 字段**（支持 OR 短路） |
| 6 | 白/黑名单键 | **个人贷=手机号 MD5；企业贷=统一社会信用代码** |

**由此坍缩出的核心模型**：

```
策略 (strategy) = channel × product × customer_group  →  1:1 →  执行计划 (plan)
计划 (plan)     = 规则树：module(模块) → step(步骤) → rule(规则)
```

mds 的五元组（渠道/策略/实验/分组/人群）在 loan 里坍缩为**三元组（渠道/产品/客群）**，实验与实名人群维度移除。

---

## 2. 数据模型（完整 DDL）

### 2.1 渠道（已有，复用）

`t_bank_channel`：`id / channel_code / bank_name / status` ✓

### 2.2 渠道准入策略（复用 `t_product_strategy` 改造为细粒度）

**不新建表**，改造现有 `t_product_strategy`（原为"策略包"，`strategy_code/customer_group/status`，无渠道无产品），加上渠道/产品/计划三个维度：

```sql
ALTER TABLE `t_product_strategy`
  ADD COLUMN `bank_channel_id` bigint DEFAULT NULL COMMENT '渠道ID' AFTER `id`,
  ADD COLUMN `channel_code` varchar(32) DEFAULT NULL COMMENT '渠道编码(冗余)' AFTER `bank_channel_id`,
  ADD COLUMN `bank_product_id` bigint DEFAULT NULL COMMENT '银行产品ID(产品挂策略)' AFTER `channel_code`,
  ADD COLUMN `execution_plan_id` bigint DEFAULT NULL COMMENT '执行计划ID(1:1)' AFTER `description`;
-- 唯一键：全局 uk_strategy_code → 渠道内唯一；加 uk_plan(1:1)
ALTER TABLE `t_product_strategy` DROP KEY `uk_strategy_code`;
ALTER TABLE `t_product_strategy` ADD UNIQUE KEY `uk_channel_strategy` (`channel_code`,`strategy_code`);
ALTER TABLE `t_product_strategy` ADD UNIQUE KEY `uk_plan` (`execution_plan_id`);
ALTER TABLE `t_product_strategy` ADD KEY `idx_channel_product_group` (`bank_channel_id`,`bank_product_id`,`customer_group`);
```

**废弃**：`t_product_strategy_bind`（M:N 策略↔产品）、`t_product_admission_config`（产品↔计划），关系统一由策略表承载（产品直接挂策略、策略 1:1 挂计划）。

### 2.3 执行计划（改造）

```sql
-- 移除 customer_group（上移到策略层），加 strategy_id 反向 1:1
ALTER TABLE `t_admission_execution_plan`
  ADD COLUMN `strategy_id` bigint DEFAULT NULL COMMENT '策略ID(1:1)',
  DROP COLUMN `customer_group`,
  ADD UNIQUE KEY `uk_strategy` (`strategy_id`);
-- plan 保留：plan_code / plan_name / version / status + module/step 规则树
```

### 2.4 模块/步骤/规则（改造）

`t_admission_plan_module`（`plan_id / module_code / module_name / logic_type / is_global_pre / sort`）、
`t_admission_plan_step`（`module_id / rule_id / step_sort / join_with_next`）、
`t_rule` 已存在，复用。

**补 `join_with_next` 字段支持 OR 组短路**（确认要）：

```sql
-- 步骤级 OR（步骤间 join_with_next 已有，确认语义：AND/OR）
-- 模块级 OR（模块间连接，mds 的 join_with_next_module）
ALTER TABLE `t_admission_plan_module`
  ADD COLUMN `join_with_next_module` varchar(8) NOT NULL DEFAULT 'AND' COMMENT '与下一模块连接(AND/OR)';
ALTER TABLE `t_admission_plan_step`
  ADD COLUMN `join_with_next` varchar(8) NOT NULL DEFAULT 'AND' COMMENT '与下一步骤连接(AND/OR)',
  ADD COLUMN `is_dry_run` tinyint NOT NULL DEFAULT '0' COMMENT '步骤级空跑';
```

### 2.5 渠道名单（新增，对齐 mds `c_channel_user_list`）

```sql
CREATE TABLE `t_channel_user_list` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `channel_code` varchar(32) NOT NULL,
  `customer_group` varchar(16) NOT NULL COMMENT '客群(决定 list_key 语义)',
  `list_type` varchar(16) NOT NULL COMMENT 'LOCAL_WHITE/LOCAL_BLACK',
  `list_key` varchar(64) NOT NULL COMMENT '名单键：个人=PERSONAL 手机号MD5(32hex)；企业=ENTERPRISE 统一社会信用代码(18位)',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_channel_type` (`channel_code`, `customer_group`, `list_type`)
) COMMENT='渠道本地白/黑名单';
```

### 2.6 模版（复用已有表，不新建）

现有 `t_strategy_template`（+ module/step/version）与 `t_rule_template`（+ field/version）已存在，直接复用。阶段一仅需在策略模版 step 表补 `join_with_next` / `is_dry_run` 字段（与计划 step 对齐），用于模版导入时保留 OR 组与空跑语义：

```sql
ALTER TABLE `t_strategy_template_step`
  ADD COLUMN `join_with_next` varchar(8) NOT NULL DEFAULT 'AND' COMMENT '与下一步骤连接(AND/OR)' AFTER `step_sort`,
  ADD COLUMN `is_dry_run` tinyint NOT NULL DEFAULT '0' COMMENT '步骤级空跑' AFTER `join_with_next`;
```

---

## 3. 路由与执行流程

### 3.1 路由（二元组 → 策略列表）

```
输入：channel_code + customer_group + fieldValues
1. 渠道存在且 ACTIVE？否 → routeFailed
2. 查策略：channel_code 匹配 AND
   (strategy.customer_group == 'COMMON' OR strategy.customer_group == 请求客群)
   AND strategy.status == '1'(上线)  // 调试可放宽
3. 每个策略 → 1:1 取 execution_plan_id → 加载计划(模块→步骤→规则)
4. 返回 List<ProductPlan>（product + plan）
```

### 3.2 执行（模块 AND/OR 短路 + 空跑，对齐 mds §4）

1. 计划按 `module.sort` 升序遍历模块，默认模块间 AND 链
2. `join_with_next_module=OR` 时当前模块与下一模块组成 OR 组
3. 任一模块链上未通过 → 后续模块写 `SKIP_SHORT_CIRCUIT`，不再执行
4. 模块内按 `step_sort`，`join_with_next` 控制步骤 AND/OR
5. **空跑**：步骤 `is_dry_run=1` 且 Handler 返回 REJECT → 链上 `step_result` 改写 PASS，保留 `handler_step_result=REJECT`

### 3.3 审计

- 调试/正式执行写 `t_match_trace`（路由快照 channel/strategy/customer_group）+ `t_match_rule_log`（`step_result` vs `handler_step_result`）

---

## 4. 生命周期（写锁 + 上线校验，对齐 mds §7/§8）

- **写锁**：策略 `status=1` 时，该策略及其计划/模块/步骤只读；允许策略自身下线
- **上线校验** `validate-before-enable`：计划结构完整（模块有步骤、步骤有规则、规则有 Handler）方可上线，否则列 issues 阻止

---

## 5. 影子移除 + 调试保留（对齐 mds §7/§11）

- **移除**：mds `v2Enabled` 旁路影子观察（生产链旁异步对比）、`DebugController` 中"影子执行不落线上"二元语义
- **保留**：调试中心复用生产同一引擎（路由→执行→审计），入参加 `channel_code`，可选 `dryRun`，返回步骤级结果树 + traceUuid

---

## 6. Controller 接口清单

| 模块 | 端点 | 说明 |
|------|------|------|
| 策略管理 | `GET /api/admin/channel-strategy/page` | 分页（渠道/产品/客群/状态筛选） |
| | `POST /api/admin/channel-strategy` | 新建（status=0） |
| | `PUT /api/admin/channel-strategy/{id}` | 编辑（写锁校验） |
| | `DELETE /api/admin/channel-strategy/{id}` | 删除（级联删计划树） |
| | `POST /api/admin/channel-strategy/{id}/enable` | 上线（先 validate） |
| | `POST /api/admin/channel-strategy/{id}/disable` | 下线 |
| | `POST /api/admin/channel-strategy/{id}/validate-before-enable` | 上线校验 |
| 跨渠道复制 | `POST /api/admin/channel-strategy/import-from-channel` | 源渠道复制策略+计划到目标 |
| 模版导入 | `POST /api/admin/channel-strategy/import-from-template` | 已上线模版实例化为渠道策略+计划 |
| 计划编排 | `GET/POST/PUT/DELETE /api/admin/execution-plan/**` | 计划+模块+步骤 CRUD（含模块 join_with_next_module、步骤 join_with_next/is_dry_run/条件三元组） |
| 计划复制 | `POST /api/admin/execution-plan/copy` | 复制计划为草稿（含计划树） |
| 另存为模版 | `POST /api/admin/execution-plan/save-as-template` | 计划 → 策略模版草稿 |
| 模版快照 | `POST /api/admin/strategy-template/snapshot-from-channel` | 渠道策略+计划 → 模版草稿 |
| 模版 | `GET/POST/PUT/DELETE /api/admin/strategy-template/**` | 模版 CRUD + 模块/步骤 + 上线/下线 |
| 名单 | `GET/POST/DELETE /api/admin/channel-user-list/**` | 白/黑名单 |
| 调试 | `POST /api/debug/match` | 入参加 channel_code（复用现有） |
| | `GET /api/admin/audit/{traceUuid}` | trace 查询（复用） |

---

## 7. 前端页面规格（实际落地，第一批 + 第二批）

| 页面 | 内容 |
|------|------|
| 渠道准入策略列表（总览） | 按渠道汇总：策略数/已上线/计划数/最近更新；展开行查看策略明细；行内操作：编排(跳向导断点续做)/校验/上线/下线 |
| 渠道配置向导（4 步） | Step0 选定渠道 → Step1 渠道策略（新增/从其他渠道复制/从模版导入）→ Step2 规则编排（模块/步骤树）→ Step3 上线校验；URL 携带 channelCode + strategyId 断点续做 |
| 计划编排（独立页） | 计划选择 + 模块树；模块（AND/OR + join_with_next_module 且/或 + 全局风控）；步骤（顺序/与下一步 AND-OR/空跑/前置条件三元组/编辑）；复制计划、另存为模版 |
| 策略模版 | 列表（搜索/分页/上线下线/导入到策略/编排）；头部「从渠道快照」入口（渠道策略+计划 → 模版草稿）；模块/步骤编排同计划页能力 |
| 步骤前置条件 | 三元组 conditionField/conditionOperator/conditionValue；运算符 EQ/NE/IN/NOT_IN/IS_BLANK/IS_NOT_BLANK；IS_BLANK 类禁用 value；运算符为空自动清空字段/值；条件不满足 → SKIP 不阻断 |
| 调试中心（改造） | 加渠道选择下拉，其余复用 |

> mds 的六步向导（Step0 选渠道 → Step5 上线）在 loan 阶段一可简化为"列表 + 编辑 + 编排"三页，后续按需增强为向导。

---

## 8. 跨渠道复制 + 级联删除（对齐 mds §12/§13）

- **复制**：`import-from-channel(sourceChannel → targetChannel)`，深拷贝策略+计划(模块/步骤)，目标渠道写锁校验
- **级联删除**：删策略时按序清理 步骤→模块→计划→编排，避免残留

---

## 9. 实施步骤

| 阶段 | 内容 |
|------|------|
| P1 | DDL + 迁移：新增策略表/模版表/名单表；计划表加 strategy_id 去 customer_group；迁移 `t_product_admission_config` 数据到策略层 |
| P2 | 后端：`AdmissionContext` 加 channelCode；`PlanLoaderService.loadProductPlans(channelCode, customerGroup)`；策略 CRUD + 写锁 + 上线校验 + 复制 + 级联删除 |
| P3 | 调试改造：DebugController 加 channel 入参，移除影子语义 |
| P4 | 前端：调试中心加渠道选择；新增策略列表/编辑/编排/模版页 |
| P5 | 迁移脚本 + 测试 + 验收 |

---

## 10. 实施顺序建议

按依赖关系，建议按 **P1 → P2 → P3 → P4 → P5** 顺序实施，P1（DDL+迁移）与 P2（后端核心）是本次改造的关键路径。数据迁移须在策略表上线前完成，确保 `t_product_admission_config` 的存量绑定不丢失。

---

## 11. V2 增强落地记录（第一批 + 第二批，已完成并验证）

> 状态：后端编译通过 + 生产连接重启验证通过；前端 vite build 通过。

### 11.1 后端

- **DDL 迁移**（幂等，`docs/plans/` 内脚本）：`t_admission_plan_module` 加 `join_with_next_module`；`t_admission_plan_step` 加 `join_with_next` / `is_dry_run` / `step_config_json` / `condition_field` / `condition_operator` / `condition_value`；策略模版 step 表对齐。
- **引擎执行**：模块 OR 组（`join_with_next_module=OR` / `logic_type=OR`）、步骤 AND/OR（`join_with_next`）、空跑（`is_dry_run=1` 时 Handler REJECT 改写链上 PASS 且不写结果缓存）；条件不满足记 SKIP 不阻断，条件字段/运算符未知抛异常（保存拒绝、运行跳过）。
- **新接口**：`execution-plan/save-as-template`、`execution-plan/copy`、`execution-plan/apply-template`、`strategy-template/snapshot-from-channel`、`channel-strategy/import-from-template`（既有 import-from-channel 保留）。
- **缺陷修复**：`apply-template` / `createStep` 的 `rule_version_id` 缺失（5000）——新增 `RuleMapper.selectCurrentVersionId`（`t_rule.current_version → t_rule_version.id` 精确解析）+ `resolveRuleVersionId` 兜底（版本记录缺失回退 ruleId 兼容单版本形态）。
- **校验白名单**：`conditionOperator` 仅接受 EQ/NE/IN/NOT_IN/IS_BLANK/IS_NOT_BLANK，非法返回业务错误。

### 11.2 前端（loan-web）

- **api 层**：`plan.js` 加 applyTemplate/saveAsTemplate/copyPlan；`strategyTemplate.js` 加 snapshotFromChannel；`channelStrategy.js` 复用 importFromChannel/importFromTemplate。
- **PlanEdit.vue**：模块弹窗加 joinWithNextModule；步骤弹窗重构为 form 对象（顺序/与下一步/空跑/条件三元组/编辑）；计划栏加复制计划、另存为模版；步骤行展示 或/且、空跑、条件摘要。
- **StrategyTemplateList.vue**：编排弹窗同 PlanEdit 能力；头部加「从渠道快照」（选渠道 → 选策略 → 模版编码/名称）。
- **ChannelConfigWizard.vue**：4 步向导编排能力对齐 PlanEdit；Step1 加「从其他渠道复制」（源渠道 → 源策略 → 目标策略编码）与「从模版导入」；Step2 加「另存为模版」；onMounted 断点续做（channelCode + strategyId 直达规则编排）。
- **ChannelConfigList.vue**：列表总览；展开行策略明细加 编排/校验/上线/下线（上线前置校验，失败弹明细）。

### 11.3 验证

- 后端冒烟：save-as-template / apply-template / copy / snapshot-from-channel 路由非 404；copy(id=1)→新计划、save-as-template→模版、snapshot-from-channel(WH_BANK)→模版均成功；apply-template 修复后新计划 id=4 且 rule_version_id 完整；createStep 修复后 id=50；updateStep 非法运算符 GT 正确拒绝（1001）。
- 前端：vite build 连续通过（PlanEdit / StrategyTemplateList / ChannelConfigWizard / ChannelConfigList 均正常打包）。

---

## 12. 与 mds V2 参考实现的三处对齐（已落地，编译通过）

> 背景：原实现与参考项目 `mds`（docs/rule-engine/渠道准入V2-技术设计说明书.md）存在三处不一致，已按「对齐 mds、低风险、不迁库」原则修复。

### 12.1 执行计划配置参数（liteflow）死配置已移除

- **结论**：`expressionJson` 列被 Service 读写但执行器 `AdmissionPlanExecutor` 从不消费，属死配置；且参考项目 mds 自身已 `liteflow.enable=false`（运行态为自定义执行器 `AdmissionStrategyExecutor`/`AdmissionRuleStepExecutor`）。故采用「移除死配置」而非「真接入 liteflow」。
- **改动**：
  - 实体 `AdmissionPlanStep`、`StrategyTemplateStep` 删除 `expressionJson` 字段。
  - Service 层 7 处残留引用（`PlanOrchestrationService` / `StrategyTemplateService` / `ProductStrategyService` 的 `svo.put` 与 `setExpressionJson`）全部清除。
  - 计划/步骤驱动改为 `ruleCode + stepConfigJson + 步骤前置条件(conditionField/Operator/Value)` 同构 mds，保留自研执行器。

### 12.2 数据表设计局部语义对齐（不改名、不迁库）

- 策略表已为「渠道×产品×客群」三元组 + `executionPlanCode` 1:1 关联，符合 mds 语义；审计字段统一（createdBy/createdAt）。
- 运行时 `PlanLoaderService.buildStep` 已用 `rule.getRuleCode()` 填充 `RuleStepConfig.ruleCode`（稳定目录键），与 mds 步骤表用 `ruleCode` 同构。
- 仅做语义对齐，未改表名、未新增 DB 列（云生产库 schema 未知，保持向后兼容）。

### 12.3 规则值解析模式对齐（事实字段白名单）

- `StepConditionEvaluator` 新增与 mds `RuleConditionEvaluator#FIELDS` 同构的 `FACT_FIELDS` 白名单：`channelCode/customerGroup/strategyCode/submissionId/clientProfileId/blacklist/dishonest/fraud/lawsuit`。
- 新增 `isKnownField(field)` / `needsValue(operator)` / `knownFieldText()`，与 mds 方法签名对齐。
- 保存期校验（`PlanOrchestrationService.validateStepCondition`）：运算符白名单之后追加 **字段白名单校验** + **needsValue 必填校验**（IS_BLANK/IS_NOT_BLANK 免填 conditionValue）。
- 运行期保持宽容：未知字段取不到即视为空白（与 mds「未知字段抛错」不同，因 loan-main 事实来自 DB 开放式 `t_client_business_fact`）；保存期才用白名单拦截（「配置错误、保存拒绝」）。
