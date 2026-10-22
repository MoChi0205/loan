---
name: loan-backend
description: >-
  loan-main 后端开发规范。编写或修改 Java / Spring Boot 业务代码（Controller / Service / Mapper /
  规则引擎 Handler / 缓存 / 审计 / 定时任务）时使用；涵盖分层边界、渠道差异下沉、批量优先、
  Javadoc 注释、Caffeine+Redis 二级缓存、禁止硬编码，以及 JDK 8 编译约束与 XXL-Job 定时任务红线。
---

# 后端开发规范（loan-backend）

## Step 0 · 前置门禁（强制，不可跳过）

1. **先查历史结论**：`grep -n "<本次需求关键词>" docs/knowledge-base/10-历史结论与决策日志.md#结论台账`，
   从**最新条目往下**读（台账按时间倒序，最新在最上方）；**命中即遵守**，与旧文档冲突时以 D 条目为准；状态为「已被 Dxx 替代」则跳读 Dxx。
   > ⚠️ 取号前必须自己刚跑 `grep -o "^| D[0-9-]*" docs/knowledge-base/10-历史结论与决策日志.md` 取实时最大编号（禁采信转述 / 记忆 / 分配表）。
2. **无结论且不确定 → 停下来问用户**，禁止臆断。
3. **再读元技能** `loan-knowledge`（`.workbuddy/skills/loan-knowledge/SKILL.md`），按其 Step 1–5 执行。
4. 回复开头输出：`【结论核对】命中 Dx-x（…）/ 未命中（grep 关键词：…）`。

## 何时使用

- 新增或修改 loan-service 后端业务代码（`com.loan.*`）
- 编写 Controller / Service(Impl) / Mapper / 规则 Handler / 缓存 / 审计 / 定时任务
- 在公共 Service 写「某渠道 / 某客群专用」逻辑前 —— **先读本文件**
- **每次代码改动前必读本规范**，并按场景交叉可读：
  - 接口设计、代码复用、并发、幂等、缓存、异常、限流、熔断、traceId、日志 → **必读** `loan-code-standard` 的 `references/backend-standard.md`
  - 接口入参 / 实体字段 / FK 列 / 业务 ID → **先读** `loan-biz-id`
  - 改表 / 索引 / 报表统计 / 分页排序 → **先读** `loan-database`
  - 新增/修改 Controller 接口 / 角色接口授权 / Web 与小程序端可访问性 → **先读** `loan-gateway-auth`
  - 新增或修改接口同步前端 → 同步核对 `loan-web-ui`（业务 ID 展示 / 时间排序参数）

## 核心原则：分层 + 下沉 + 批量 + 可配

### 禁止

- ❌ 在公共 Service 写死渠道 / 客群 / 产品分支（`if (channelCode == ...)` 业务判断）
- ❌ 在通用 DTO 上挂「仅某渠道 / 某客群使用」的字段
- ❌ 硬编码配置项 / 文案 / 阈值 / 比例 / 状态机（魔法值）
- ❌ 循环内逐条 RPC / 逐条查库（`for { rpc() }`）
- ❌ 无 Javadoc 的类/方法、无字段注释的入参出参 DTO
- ❌ 大方法 / 深嵌套 / 重复代码（同类业务各写一份）
- ❌ 用 `@Scheduled` 写定时任务 → **必须用 XXL-Job**（D0-1 长期红线）

### 应该

- ✅ **分层边界**：`controller / service(impl) / repository / dao / entity / execution / rules(handler) / support / enums / config`，
  跨层不越级调用；Controller 用 JavaDoc 说明，**不引 Swagger**
- ✅ **差异下沉 Handler / 策略子类**：渠道 / 客群差异写独立 Handler，公共 Service 只做通用编排
- ✅ **批量优先**：取数 / 比较 / 回写一律批量（`handleBatch → Map` 结果），能批必须批；单号也走批量 API `list size=1`
- ✅ **注释规范**：所有类/方法 Javadoc；DTO 每字段注释（含义 / 是否必填 / 取值范围）；枚举值注释语义
- ✅ **二级缓存**：不经常变更数据用 Caffeine（一级本地，短 TTL）+ Redis（二级分布式，长 TTL）
- ✅ **不硬编码**：配置 / 文案 / 阈值走 Nacos 配置中心或 `t_config` + 常量类 / 枚举

## 扩展点模式

### 规则引擎（参考 mds V2）

```
AdmissionExecutionPlan（计划） → Module（模块，AND/OR 短路） → Step（单条规则）
        ↓
AdmissionRuleRegistry（ruleCode → Handler 注册表）
        ↓
RuleConditionEvaluator（conditionField + conditionOperator + conditionValue 条件表达式）
```

- 数值 / 枚举类准入条件（纳税额 / 开票额 / 负债率 / 成立年限 / 行业）→ 通用 Handler + 表达式后台可配
- 行为类（黑名单 / 失信 / 欺诈核验查外部）→ 专用 Handler
- 结果五态：`PASS / FAIL / SKIP / SKIP_SEGMENT_MISMATCH / ERROR`

### 策略工厂（多供应商可插拔）

```
SmsFacade → SmsSendStrategy → SmsStrategyFactory → SmsChannelStrategy（短信）
OcrStrategyFactory → TENCENT_OCR / QWEN_VL（识别）
OssStorageService → local / aliyun（@ConditionalOnProperty 切换）
```

新增供应商只加策略实现，不改调用方。

### 缓存四防

| 问题 | 手段 |
|---|---|
| 缓存穿透 | 空值缓存 / 布隆过滤器 |
| 缓存击穿 | 热点互斥锁 / 热点不过期 |
| 缓存雪崩 | TTL 加随机抖动 |
| 缓存一致性 | 写后失效 / 延迟双删（写后主动 `evict` 对应 key） |

## 契约与红线速查

- **契约真源**：`db/loan-db-schema.sql`（**表数以 `grep -c "CREATE TABLE" db/loan-db-schema.sql` 为准，不写死数字**，规则 9）→ `loan-service` 代码 → `docs/knowledge-base/`
- **禁止引用** `前端交互逻辑蓝图.html` / `后端逻辑蓝图.html` / `output/`（均不存在或已删除，见 `loan-knowledge`）
- **审批权限真值（D60 覆盖 D59 的 PRODUCT 部分）**：`ALLOCATION` 审批包含 `DEPT_MANAGER`，但部门经理仅可审批本人团队；渠道 `PRODUCT` 录入/删除终审仅 `BOSS/SUPER_ADMIN/SUPER`；`DOWNLOAD/MATERIAL_REVIEW` 可包含 `DEPT_MANAGER/OPERATOR`。
  依据 `loan-service/src/main/java/com/loan/mini/service/MiniRoleGuard.java` 的 `APPROVER_ROLES` vs `APPROVAL_ROLES`
- **OCR 回灌真名（D0-3）**：`SubmissionFactsMerger.mergeFromOcr(reportNo, ocrFacts, operator)`，**不是** `mergeToOcr`
- **分工红线（D83 已更新，取代 D0-1）**：loan-web UI 缺陷修复已授权助手；改前端仍须先过 Step 0 门禁
- **schema 文档不是真理（D83-六批）**：`db/loan-db-schema.sql` 曾与生产库偏差 76 处 / 缺 7 张表，
  导致 insert 漏设 NOT NULL 无默认列时报 `Field 'xxx' doesn't have a default value`。
  **契约真源顺序：生产库（Nacos 数据源）→ 重新生成的 schema 基线 → 代码**。
  新增表 / 加列 / 改可空性后必须重新生成基线并提交。

## 自检清单（改完必过）

- [ ] Step 0 结论核对是否已输出？
- [ ] 公共 Service 是否新增渠道 / 客群硬编码分支？→ 应下沉 Handler / 策略子类
- [ ] 是否在通用 DTO 上增加「仅某渠道 / 客群使用」字段？→ 改为 Handler 内局部变量或私有方法
- [ ] 是否出现循环内逐条 RPC / 查库？→ 改批量 `handleBatch`
- [ ] 同一资源是否支持单条、批量、组合条件分页并复用统一 Query 模型，而非按条件增加方法？
- [ ] 是否出现魔法值 / 硬编码文案阈值？→ 走 Nacos / `t_config` / 枚举
- [ ] 所有类 / 方法是否有 Javadoc？DTO 字段是否逐字段注释？
- [ ] 不经常变更的数据是否走了 Caffeine + Redis 二级缓存？写后是否 evict？
- [ ] 是否复用 tse / mds 骨架，未重复造轮子？
- [ ] 是否跑过 `mvn compile` 自检（**JDK 8 约束：禁 `var` / `List.of` / 文本块**）？
- [ ] 业务标识是否走业务 ID（禁 `@PathVariable Long id` / `selectById` 作业务查询）？→ 见 `loan-biz-id`
- [ ] 新增定时任务是否用 XXL-Job（禁 `@Scheduled`）？
- [ ] 涉及审批权限是否按 D39 真值校验（DEPT_MANAGER 仅本团队 ALLOCATION，跨团队上收）？
- [ ] 重要写接口的幂等、状态 CAS、锁与线程安全是否有明确设计和并发验证？
- [ ] 限流/熔断/traceId/日志分流与脱敏是否符合 `loan-code-standard` 后端验收清单？

## 实战坑位清单（2026-10-20 沉淀，改代码前必扫）

- **筛选参数必须后端显式 `@RequestParam` 接收**：前端 `useTable` 会把 query 整体透传，但后端不接收就是"假筛选"（前端加了下拉/日期却筛不动）。补筛选 = 前端控件 + 后端参数 + `LambdaQueryWrapper` 条件三件套一起做，禁只改前端。
- **分页链路改动要逐层打通**：`Controller → (MiniClientService) → XxxService → Mapper` 多层委派时，加参数要每层签名一起改，别只改到中间层漏了调用点（编译能过但逻辑丢参）。
- **统计口径与列表口径必须一致**：chip 计数 / 角标数量 与对应列表的 where 条件若口径分裂（如计数把 `NULL/空` 归入某类，列表用精确 `eq`），会出现"计数 1、列表 0"。改计数 SQL 时同步核对列表过滤。
- **业务异常返回明确 code，禁止前端靠文案匹配分支**：新增专属语义码（如 `CLIENT_CROSS_TEAM=1005`）到 `ResultCode`，`BusinessException(ResultCode.XXX, msg)` 抛出；全局兜底（`Exception.class`）不得吞掉业务 message。前端按 `e.code` 分支。
- **`LoanUser.userNo` 对员工就是工号（staff_code）**：申请认领/指派类接口传目标人时，`user.getUserNo()` 即工号，不要另寻 `staffCode` 字段（字段注释已写明）；跨团队转移申请的目标人经 `body.targetStaffCode` 传入、缺省回落申请人本人。
- **哨兵值要有明确语义**：`remaining = -1` 表示"豁免角色不限次"，返回给前端时要能区分（配套 `unlimited=true` 之类标志），别让前端裸显 -1。
- **本地编译必须带 `-pl loan-service -am`**：`loan-service` 依赖 `loan-api` 模块，直接在 loan-service 目录 `mvn compile` 会用本地仓库旧 jar（报 `找不到符号 setXxx`）。正确姿势：`mvn -pl loan-service -am compile -DskipTests`，且 `JAVA_HOME` 指向 JDK8（否则报 `No compiler is provided...`）。
- **NOT NULL 无默认列是运行时的坑，编译期发现不了**：生产库有 306 个 / 90 张表这类列。MyBatis-Plus 默认**跳过 null 字段**（不写进 INSERT），漏设 = SQL 缺列 = 报 `Field 'xxx' doesn't have a default value`。**往任何表 insert 前先跑 `python3 scripts/schema-diff-against-live.py`**，看目标表的 NOT NULL 无默认列清单，实体必须逐个显式赋值（语义可空的写 `""` 占位并注释「勿删」）。已踩：`t_client_business_fact.submission_no`、`t_service_attachment.order_no`（两个上传路径）。
- **insert 与查询必须同时改**：给某列写空串占位后，所有 `isNull(该列)` 的查询都要改成 `isNull OR = ''`。只改 insert 不改查询 → 空串行查不到，每次保存都插新行（重复数据）；只改查询不改 insert → 照样缺列报错。
- **审批列表不要硬编码「只返回待审」**：审批/待办类分页接口若 `eq(status, PENDING)` 写死，审批通过后数据从列表"消失"、无处可查。必须提供 `status` 可选参数（空=待审保持原语义，非空=按状态查全部），前端配合状态下拉。已踩：手机号/分配/渠道线索/外出/短信模板/报告模板六个审批 tab 全部硬编码（D83-九批统一补参数）。
- **审批用 CAS 条件更新做幂等**（`eq(status, PENDING)` 的 update 返回 0 行即抛「不存在或已处理」），这是正确的并发防线；「审批单不存在或已处理」批量出现在日志里是**前端无防重导致连点重复提交**，不是后端 bug。后端保持 CAS，防重交给前端（按钮 loading / auditing 标志）。
- **配置项短码 `varchar(32)` 必须用 `BizIdGenerator.generate(prefix, 32)`**：`generate(prefix)` 默认 = 前缀 + 32 位 hex（总长 = 前缀长 + 32）。当目标列是 `varchar(32)`（如 `t_bank_channel.channel_code`、`t_reward_rule.rule_version`）且前缀非空时，会生成 34 字符直接 `Data too long` 截断报错。凡是写 `channel_code` / `rule_version` / 其他定长 32 短码，一律 `generate(prefix, 32)` 固定总长；记录级业务 ID（`varchar(64)`）才用 `generate(prefix)`。改完 grep 全仓确认无 `generate("ch")` / `generate("rule")` 裸调用。
- **编码长度三层防线：前端限长提示 + 后端显式校验 + 全链路禁截断**（用户明确规范）：
  ① 前端编码输入框必须 `maxlength` + `show-word-limit` + placeholder 写明上限（如「不超过32个字符」）；**原生 maxlength 不拦截 JS 赋值/回填**，表单 rules 必须再加 `{ max: N, message: 'XX不能超过N个字符', trigger: 'blur' }` 兜底；
  ② 后端保存入口必须显式校验，用 `FieldLengthGuard.requireMaxLength("字段名", value, max)`（`com.loan.common.util`），上限与 `db/loan-db-schema.sql` 列宽一致，超长抛 `BusinessException(PARAM_ERROR)` 给出「哪个字段、上限多少、当前多长」；
  ③ **任何情况下禁止 substring/abbreviate 式静默截断**——MySQL 严格模式会抛 `Data too long for column` 原始 SQL 错误（用户无法定位字段），非严格模式则悄悄切掉数据造脏数据。已接入点：`OrgWriteService.saveDepartment`（dept_code **32**）/ `ProductService.validate` / `RuleService.validate` / `SmsAdminService.saveTemplate` / `ReportTemplateService.save`。
- **认领/分配角色白名单必须四处对齐**：前端 `canForceAssign`、后端 `ClientController.assign/directAssign` 的 `Arrays.asList(...)`、`MiniRoleGuard` 的 `APPROVER_ROLES/APPROVAL_ROLES`、菜单 SQL 授权行——任一漏角色（如漏 `OPERATOR`）即导致该角色 403 或进页面却永远只读。改角色权限四处一起改并 grep 复核，能力标志（`canManage`/`canForceAssign`）由后端单一真源计算，前端禁止本地硬编码角色判断。
- **企业名称/联系人必须后端 DTO 显式补齐 + 合并查询**：服务域 DTO（`StaffAppointmentDTO`/`StaffOutingDTO`/`DailyFollowDTO`/`DailyOrderDTO`）与订单出参必须显式加 `enterpriseName`/`contactName` 字段并在组装处 set；列表接口合并**一次**客户查询补齐（防 N+1），前端客户列主行企业名 + 副行联系人（个人客群仅联系人）。新增任何含「客户/企业」信息的列表，默认补这两个字段，不得只返业务编码让前端逐行查名称。
- **回收/释放/转移必须联动关联对象（手动与自动同一套）**：线索与客户档案存在 `clientProfileCode` 关联，任何「线索释放/回收」都要同步处理关联客户，否则线索回公海、客户仍挂原顾问「我的客户」→ 两个列表口径分裂。手动路径（`LeadService.release`）与自动路径（`recycleOverdue` 定时任务）**必须复用同一个 `releaseLinkedClient`**，禁止只改一个入口。联动调用 `selfRelease` 时 operator 不能为 null（其归属校验 `operator.userNo == ownerStaffCode`），系统场景用归属人工号构造 system LoanUser。
- **长度三层防线不只针对编码，名称/标题/地址/备注等所有用户可输入文本同样适用**：前端 maxlength + rules max、后端 `FieldLengthGuard.requireMaxLength`、DB 列宽三处必须同值。已接入名称校验的入口：`StrategyTemplateService` / `ProductStrategyService` / `RuleTemplateService` / `PlanOrchestrationService` 的 create/update（上限 128 / 64）。注意 DB 列宽 name 类多为 128，仅 `dept_name`/`staff_name`/`field_name`/`sign_name` 为 64。

## 相关文档

- `docs/knowledge-base/10-历史结论与决策日志.md#结论台账`（**Step 0 必查**，D0-1 / D0-3 / D0-4 为后端高频红线）
- `.workbuddy/skills/loan-code-standard/references/backend-standard.md`（接口、并发、缓存、稳定性与日志唯一标准）
- `docs/knowledge-base/02-业务红线与编码规范.md#编码规范`
- `docs/knowledge-base/02-业务红线与编码规范.md#用户明示红线（不可破）`
- `docs/knowledge-base/00-项目结构与代码地图.md#模块地图（阶段四规划）`
- `docs/knowledge-base/03-数据模型（DB schema 索引）.md#关键业务表`
- `docs/knowledge-base/04-后端 API 契约.md#字段命名红线`
- `docs/knowledge-base/06-业务结论沉淀索引（C1-C19）.md#结论速查`
- `docs/plans/archive/方案评审定稿纪要.html`（契约基线：第 15 章规则引擎语义、第 24 章代码开发规范；表数以 `db/loan-db-schema.sql` 为准）
- 生成入口：`loan-service/src/main/java/com/loan/common/util/BizIdGenerator.java`
- 长度守卫（编码超长显式报错、禁截断）：`loan-service/src/main/java/com/loan/common/util/FieldLengthGuard.java`
- 参考 tse：`/Users/admin/Documents/crm/tse`（骨架）
- 参考 mds：`/Users/admin/IdeaProjects/mds/mds-service/.../com/xr/dam/mds/v2`（规则引擎）
