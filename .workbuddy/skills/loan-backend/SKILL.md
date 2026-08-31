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
- **审批权限真值（D0-4）**：`ALLOCATION` 审批仅 `OPERATOR` / `SUPER_ADMIN` / `SUPER` / `BOSS`
  （**不含 DEPT_MANAGER**）；`PRODUCT` / `DOWNLOAD` 才含 `DEPT_MANAGER`。
  依据 `loan-service/src/main/java/com/loan/mini/service/MiniRoleGuard.java` 的 `APPROVER_ROLES` vs `APPROVAL_ROLES`
- **OCR 回灌真名（D0-3）**：`SubmissionFactsMerger.mergeFromOcr(reportNo, ocrFacts, operator)`，**不是** `mergeToOcr`
- **分工红线（D0-1）**：前端（loan-web / 小程序）由用户自己负责，助手只做后端 `loan-service`

## 自检清单（改完必过）

- [ ] Step 0 结论核对是否已输出？
- [ ] 公共 Service 是否新增渠道 / 客群硬编码分支？→ 应下沉 Handler / 策略子类
- [ ] 是否在通用 DTO 上增加「仅某渠道 / 客群使用」字段？→ 改为 Handler 内局部变量或私有方法
- [ ] 是否出现循环内逐条 RPC / 查库？→ 改批量 `handleBatch`
- [ ] 是否出现魔法值 / 硬编码文案阈值？→ 走 Nacos / `t_config` / 枚举
- [ ] 所有类 / 方法是否有 Javadoc？DTO 字段是否逐字段注释？
- [ ] 不经常变更的数据是否走了 Caffeine + Redis 二级缓存？写后是否 evict？
- [ ] 是否复用 tse / mds 骨架，未重复造轮子？
- [ ] 是否跑过 `mvn compile` 自检（**JDK 8 约束：禁 `var` / `List.of` / 文本块**）？
- [ ] 业务标识是否走业务 ID（禁 `@PathVariable Long id` / `selectById` 作业务查询）？→ 见 `loan-biz-id`
- [ ] 新增定时任务是否用 XXL-Job（禁 `@Scheduled`）？
- [ ] 涉及审批权限是否按 D0-4 真值校验（ALLOCATION 不含 DEPT_MANAGER）？

## 相关文档

- `docs/knowledge-base/10-历史结论与决策日志.md#结论台账`（**Step 0 必查**，D0-1 / D0-3 / D0-4 为后端高频红线）
- `docs/knowledge-base/02-业务红线与编码规范.md#编码规范`
- `docs/knowledge-base/02-业务红线与编码规范.md#用户明示红线（不可破）`
- `docs/knowledge-base/00-项目结构与代码地图.md#模块地图（阶段四规划）`
- `docs/knowledge-base/03-数据模型（DB schema 索引）.md#关键业务表`
- `docs/knowledge-base/04-后端 API 契约.md#字段命名红线`
- `docs/knowledge-base/06-业务结论沉淀索引（C1-C19）.md#结论速查`
- `docs/plans/archive/方案评审定稿纪要.html`（契约基线：第 15 章规则引擎语义、第 24 章代码开发规范；表数以 `db/loan-db-schema.sql` 为准）
- 生成入口：`loan-service/src/main/java/com/loan/common/util/BizIdGenerator.java`
- 参考 tse：`/Users/admin/Documents/crm/tse`（骨架）
- 参考 mds：`/Users/admin/IdeaProjects/mds/mds-service/.../com/xr/dam/mds/v2`（规则引擎）
