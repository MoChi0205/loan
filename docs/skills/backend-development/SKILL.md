---
name: backend-development
description: >-
  loan-platform 后端开发规范。编写或修改 Java / Spring Boot 业务代码（Controller / Service / Mapper /
  规则引擎 / 缓存 / 审计）时使用；涵盖分层边界、渠道差异下沉、批量优先、注释、二级缓存与不硬编码。
---

# 后端开发规范

## 何时使用

- 新增或修改 loan-platform 后端业务代码（`com.loan.*`）
- 编写 Controller / Service / Mapper / 规则 Handler / 缓存 / 定时任务
- 在公共 Service 写「某渠道 / 某客群专用」逻辑前——**先读本文**

## 核心原则：分层 + 下沉 + 批量 + 可配

### 禁止

- ❌ 在公共 Service 写死渠道 / 客群 / 产品分支（`if (channelCode == ...)` 业务判断）
- ❌ 在通用 DTO 上挂「仅某渠道 / 某客群使用」的字段
- ❌ 硬编码配置项 / 文案 / 阈值 / 比例 / 状态机（魔法值）
- ❌ 循环内逐条 RPC / 逐条查库（`for { rpc() }`）
- ❌ 无 Javadoc 的类/方法、无字段注释的入参出参 DTO
- ❌ 大方法 / 深嵌套 / 重复代码（同类业务各写一份）

### 应该

- ✅ **分层边界**：`controller / service(impl) / repository / dao / entity / execution / rules(handler) / support / enums / config`，跨层不越级调用；Controller 用 JavaDoc 无 Swagger
- ✅ **差异下沉 Handler / 策略子类**：渠道 / 客群差异写独立 Handler，公共 Service 只做通用编排
- ✅ **批量优先**：取数 / 比较 / 回写一律批量（`handleBatch → Map` 结果），能批必须批，单号走批量 API `list size=1`
- ✅ **注释规范**：所有类/方法 Javadoc；DTO 每字段注释说明（含义 / 是否必填 / 取值范围）；枚举值注释语义
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
- 结果五态：PASS / FAIL / SKIP / SKIP_SEGMENT_MISMATCH / ERROR

### 策略工厂（多供应商可插拔）

```
SmsFacade → SmsSendStrategy → SmsStrategyFactory → SmsChannelStrategy（短信）
OcrStrategyFactory → TENCENT_OCR / QWEN_VL（识别）
OssStorageService → local / aliyun（@ConditionalOnProperty 切换）
```

新增供应商只加策略实现，不改调用方。

### 缓存四防

| 问题 | 手段 |
|------|------|
| 缓存穿透 | 空值缓存 / 布隆过滤器 |
| 缓存击穿 | 热点互斥锁 / 热点不过期 |
| 缓存雪崩 | TTL 加随机抖动 |
| 缓存一致性 | 写后失效 / 延迟双删（写后主动 `evict` 对应 key） |

## 自检清单（改完必过）

- [ ] 公共 Service 是否新增渠道 / 客群硬编码分支？→ 应下沉 Handler / 策略子类
- [ ] 是否在通用 DTO 上增加「仅某渠道 / 客群使用」字段？→ 改为 Handler 内局部变量或私有方法
- [ ] 是否出现循环内逐条 RPC / 查库？→ 改批量 `handleBatch`
- [ ] 是否出现魔法值 / 硬编码文案阈值？→ 走 Nacos / `t_config` / 枚举
- [ ] 所有类 / 方法是否有 Javadoc？DTO 字段是否逐字段注释？
- [ ] 不经常变更的数据是否走了 Caffeine + Redis 二级缓存？写后是否 evict？
- [ ] 是否复用 tse / mds 骨架，未重复造轮子？
- [ ] 是否跑过 `mvn compile` 自检（JDK 8 约束：禁 var / List.of / 文本块）？

## 相关文档

- `../../output/方案评审定稿纪要.html` 第 15 章规则引擎语义、第 24 章代码开发规范
- 参考 tse：`/Users/admin/Documents/crm/tse`（骨架）
- 参考 mds：`/Users/admin/IdeaProjects/mds/mds-service/.../com/xr/dam/mds/v2`（规则引擎）
