---
name: loan-code-standard
description: >-
  loan-main 前后端代码标准化与架构质量门禁。新增、修改、重构或评审 Java 后端、
  Web 管理端、uni-app 客户端、公共组件、接口、缓存、并发、日志、稳定性与数据展示时使用。
---

# loan-main 代码标准化门禁

## 强制入口

1. 先执行 `loan-knowledge` Step 0–2，查历史结论与涉及业务域契约。
2. 在改码前搜索同类实现、公共组件、utils、Service、Mapper 和 DTO；先复用或扩展，再考虑新建。
3. 后端工作必读 [references/backend-standard.md](references/backend-standard.md)；Web / mini 页面、组件、展示必读 [references/frontend-standard.md](references/frontend-standard.md)。
4. 改动接口时同步核对 `loan-biz-id`、`loan-gateway-auth` 和前后端调用方；不允许单边改契约。

## 通用设计原则

- **边界清晰**：按业务域分包，Controller / Service / Repository(Mapper) / DTO / 视图组件 / API / utils 各尽其责；禁止“一个大类包含全流程”。
- **复用与提取**：同一业务规则、查询构造、格式化、校验、异常转换或组件交互出现第二份实现时，必须评估提取公共方法、组合对象、utils 或公共组件。
- **拒绝虚假抽象**：只有语义一致、变化原因相同的逻辑才合并；不为消除数行重复制造难理解的“万能工具类”。
- **扩展优先**：变化频繁的渠道、客群、供应商、规则与状态差异使用策略 / Handler / 注册表 / 配置扩展，公共流程不堆积 `if/else`。
- **性能有证据**：先明确调用量、数据量、一致性、延迟与失败模式，再选批量、索引、缓存、锁、限流、超时、重试或熔断；不无差别叠加中间件。
- **可验证**：每次修复必有最小回归用例；契约、并发、幂等、缓存一致性、限流和熔断改动必须有相应正常/边界/异常用例。

## 交付门禁

- [ ] 是否先查全仓同类类、方法、utils、组件与接口？
- [ ] 重复逻辑是否已复用/提取，或记录了不应合并的语义原因？
- [ ] 功能分层、包边界和依赖方向是否清晰？
- [ ] 对外契约是否使用业务编码，前后端与文档是否同步？
- [ ] 是否完成涉及端的测试、构建和关联回归？
- [ ] 新约束或关键取舍是否回写历史决策台账？

## 与现有 skills 的边界

- 本 skill 是跨端代码质量与稳定性标准的唯一入口。
- `loan-backend`、`loan-web-dev`、`loan-mini-ui` 保留框架和项目实现细节，不复制本 skill 全文。
- `loan-biz-id` 仍是业务 ID 生成、存储、查询契约的唯一真源；`loan-web-ui` 负责具体展示形态。
