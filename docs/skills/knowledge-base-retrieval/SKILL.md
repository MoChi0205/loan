---
name: knowledge-base-retrieval
description: >-
  loan-platform 知识库检索规范。涉及需求、设计、历史决策或任何改码任务前使用；强制走
  「先查知识库 → 找对应子规范 → 按规范写 → 过自检清单」流程，禁止跳过知识库凭假设写代码。
---

# 知识库检索规范

## 何时使用

- 任何涉及 loan-platform 的需求分析、设计、编码、评审任务**开始前**
- 需要业务背景、历史决策、字段口径、复用映射时
- 想凭「我记得 / 我猜」直接写代码前——**先读本文**

## 核心原则：先查库，再动手

### 禁止

- ❌ 跳过知识库，凭假设 / 记忆直接写代码
- ❌ 忽略已有需求、计划、决策记录，重复发明
- ❌ 不查历史就向用户索要本可查到的项目背景

### 应该

- ✅ **强制流程**：`先查知识库 → 找对应子规范 → 按规范写 → 过自检清单`
- ✅ **复用已有知识**：已有定稿结论直接引用，不重开讨论
- ✅ **信息缺失才问**：仅当知识库信息缺失、冲突或明显不完整时才询问用户

## 扩展点模式：检索顺序

```
1. 结论台账（修改前必读）        docs/knowledge-base/10-历史结论与决策日志.md
2. 契约基线（评审定稿）          docs/方案评审定稿纪要.html（表数勿引此文件，见下方数据真源）
3. 数据真源                      db/loan-db-schema.sql（表数以此为准）+ db/migrate-*.sql
4. 实现真源                      loan-service/src/main/java/com/loan/
5. 业务知识库                    docs/knowledge-base/（00-10 各模块索引）
6. 参考项目骨架                  /Users/admin/Documents/crm/tse（骨架）
                                 /Users/admin/IdeaProjects/mds（规则引擎）
7. 子规范 Skill                  docs/skills/backend-development
                                 docs/skills/frontend-development
                                 docs/skills/document-archiving
```

### 契约真源（三件套）

契约的最终解释权归以下三项，**按此顺序裁决**：

1. `db/loan-db-schema.sql` —— 数据契约。**表数唯一真源**，取值命令：`grep -c "CREATE TABLE" db/loan-db-schema.sql`（禁止在任何文档中写死该数值）
2. `loan-service` 实际代码 —— 实现契约（代码与文档冲突时，以代码为准并回写文档）
3. `docs/knowledge-base/` —— 业务契约（结论沉淀、角色矩阵、流程图谱）

### ⚠️ 已确认失效，禁止再引用（2026-08-31 全仓搜索核实）

| 失效引用 | 核实结论 |
|----------|----------|
| `output/` 目录 | **已不存在**，原文件已归档进 `docs/` |
| `前端交互逻辑蓝图.html` | **全仓不存在**，从未落盘，勿再引用 |
| `后端/前端逻辑蓝图.html` | **全仓不存在**，从未落盘，勿再引用 |
| `.workbuddy/memory/MEMORY.md` | 本项目下 `.workbuddy/` 目录**不存在**；长期红线暂由 `docs/knowledge-base/10-历史结论与决策日志.md` 承担 |

> 上表内容如后续被创建，须由用户确认后再从本表移除，不得凭推测恢复引用。

### 检索命中后

- 命中「定稿结论」→ 直接按结论执行，不再询问
- 命中「复用映射」→ 直接复用 tse / mds 对应代码，不重写
- 命中「子规范」→ 按该规范的「禁止 / 应该 + 自检清单」约束产出

## 自检清单（改完必过）

- [ ] 动手前是否查了 `方案评审定稿纪要.html` 对应章节？
- [ ] 是否查了 `docs/knowledge-base/10-历史结论与决策日志.md` 的复用映射与红线？
- [ ] 是否确认该结论是「已定稿」而非「历史讨论稿」？
- [ ] 是否找到对应的子规范 Skill 并按其「禁止 / 应该」约束？
- [ ] 是否存在「凭假设写代码」的隐患？→ 先查库，缺失才问

## 相关文档

- `docs/knowledge-base/10-历史结论与决策日志.md`（**修改前必查**：D 系列结论台账）
- `docs/方案评审定稿纪要.html`（唯一有效契约基线；表数以 `db/loan-db-schema.sql` 为准）
- 参考 mds：`/Users/admin/IdeaProjects/mds/AGENTS.md`（知识检索优先原则）
