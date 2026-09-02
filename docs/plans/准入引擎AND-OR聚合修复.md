# 准入引擎 AND/OR 聚合错配修复（对应 mds admission-v2-and-or-aggregation-fix-req）

> 状态（2026-09-02）：后端执行器 + 配置门禁已改，Web 前端校验已改，loan-service 单测 7/7 通过、loan-web 构建通过。loan-mini 无需改动（无准入编排 UI）。

## 1. 问题来源
参考文档：`/Users/admin/IdeaProjects/mds/llm-wiki/raw/requirements/admission-v2-and-or-aggregation-fix-req.md`
- 原实现步骤/模块聚合采用**左结合短路**（遇 PASS+OR 或 FAIL+AND 即短路），导致：
  - 步骤 `A OR B AND C` 中 A=PASS 时**错误地跳过 C**（应为 `(A‖B) && C`，C 必执行）；
  - 模块 `M1(OR) M2(AND) M3(OR) M4` 中 M1=PASS 时**错误地短路跳过 M2/M3/M4**（应为 `(M1‖M2) && (M3‖M4)`，全 4 模块执行）。
- 与需求 §3.1 / §3.2 的分段解析语义（**AND 段 + OR 组**）不一致。

## 2. 根因
`AdmissionPlanExecutor` 的 `executePlan` / `executeModule` 用左结合短路而非「相邻 OR 合并为组、段间 AND」的分段解析。

## 3. 修复内容（后端）
文件：`loan-service/.../engine/execute/AdmissionPlanExecutor.java`
- `buildStepSegments(step, moduleOr)`：连续 `joinWithNext=OR` 合并为步骤 OR 组段；OR 逻辑模块整模块为单一 OR 组；段间 AND。
- `buildModuleSegments(modules)`：相邻 `joinWithNextModule=OR` 合并为模块 OR 组段；段间 AND。
- 段内**全执行**（OR 组不短路），任一 PASS 即段 PASS；段 REJECT 则后续段短路 SKIP（禁止跨段救场）。

文件：`loan-service/.../plan/service/PlanOrchestrationService.java`（FR-03 配置门禁）
- `normalizeJoin()`：连接符 trim+大写，非法值（非 AND/OR）拒绝；保存模块/步骤时强制归一。
- `validatePlanStructure(planId)`：sort / stepSort 唯一、末位模块不可 OR、模块级禁止连续 OR（≥3 模块 OR 组）、末位步骤不可 OR（步骤级连续 OR 允许）。

文件：`loan-service/.../plan/service/ProductStrategyService.java`
- 在 `validateBeforeEnable`（**执行激活**入口）调用 `validatePlanStructure`，将结构问题并入上线拦截。

## 4. 修复内容（前端 Web）
文件：`loan-web/src/views/plan/PlanEdit.vue`
- 新增 `validateModuleJoin` / `validateStepJoin`，与后端 FR-03 对齐（保存即拦截：sort 唯一、末位不可 OR、模块级禁止连续 OR）。
- `onSaveModule` / `onSaveStep` 保存前校验，违规 `ElMessage.warning` 拦截；连接符归一为大写 AND/OR。

## 5. 小程序（loan-mini）
**无需改动**：准入引擎完全在 `loan-service` 后端，配置仅经 `loan-web` 编辑；`loan-mini` 无准入/计划执行或编排 UI（仅 node_modules 命中噪声）。

## 6. 验证
- 单测 `AdmissionPlanExecutorTest`（7 用例）：覆盖 A OR B AND C（A=PASS 仍执行 C）、模块级 M1(OR)…M4 全执行、AND 链断裂短路、OR 模块、空模块、客群不匹配。结果 **7/7 通过**。
- `loan-service` 主代码编译通过（Java 8，`Set.of` 已替换为 `HashSet+Arrays.asList`）。
- `loan-web` `npm run build` 通过（含 PlanEdit chunk）。

## 7. 遗留 / 注意事项
- **预存 broken test（与本次无关）**：`ClientAllocationServiceTest` 因 `ClientAllocationService` 构造器签名漂移（缺 `ClientRecycleConfigMapper`、`NotificationService`）编译失败，属历史存量问题，本次未改动也未修复，验证时临时移出后已还原。
- **存量数据迁移**：已上线/草稿计划中若存在「末位模块 OR」或「≥3 模块连续 OR」，执行激活将被新门禁拦截。建议上线前对 `t_admission_plan_module.join_with_next_module` 做一次数据巡检并修正。
- 红线 #1：本次前端改动沿用用户 2026-09-01 授权（临时放开「不碰 loan-web」）。
