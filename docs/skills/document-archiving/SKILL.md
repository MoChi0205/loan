---
name: document-archiving
description: >-
  loan-platform 文档归档规范。生成项目文档（需求 / 计划 / 设计 / 会议纪要 / 结论文档）时使用；
  按 raw/ 规则分类归档，简体中文，唯一真源，禁止文档散落到 /tmp / notes 等任意位置。
---

# 文档归档规范

## 何时使用

- 生成需求、PRD、功能规格、实施计划、技术设计、架构说明
- 记录会议纪要、决策记录、研究总结、方案分析
- 想把结论文档「先放桌面上 / 临时目录」时——**先读本文**

## 核心原则：唯一真源 + 分类归档 + 简体中文

### 禁止

- ❌ 把项目文档散落到 `/tmp`、`/notes`、桌面、仓库根目录等任意位置
- ❌ 在多个位置创建重复 canonical 副本
- ❌ 只把重要项目决策留在聊天记录中，不落库
- ❌ 说明性文本用英文（除非原始材料为英文需总结为中文）

### 应该

- ✅ **唯一真源**：项目规划 / 需求 / 设计 / 决策的 canonical source 是知识库，不是临时聊天输出
- ✅ **分类归档**：按文档类型放入对应子目录
- ✅ **简体中文**：知识库说明性文本、摘要、分析、决策、日志一律简体中文；代码符号 / 类名 / 方法名 / 表名 / 枚举值 / 命令可保留原文
- ✅ **持久记录**：重要新知识写回知识库，不随会话消失

## 扩展点模式：归档分类

| 文档类型 | 位置 |
|---------|------|
| 需求 / PRD / 规格 / 用户故事 | `requirements/` |
| 计划 / 设计 / 实施 / 架构 | `plans/` |
| 研究 / 调研 / 对比 | `research/` |
| 会议纪要 / 决策记录 | `meetings/` |
| 外部文章 / 指南 / 参考 | `articles/` |
| 图片 / 图表 / 附件 | `assets/` |

loan-platform 落地目录：

```
docs/skills/           # 开发规范 Skill（本目录）
docs/                  # 交付物（纪要 / 评审结论）——契约基线（如 方案评审定稿纪要.html）
db/                    # DDL 与迁移脚本（如 loan-db-schema.sql）
.workbuddy/memory/     # 项目长期记忆（MEMORY.md）+ 当日日志（YYYY-MM-DD.md）
```

### 命名规范

- kebab-case 描述性文件名：`<topic>-<doc-type>.md`、`YYYY-MM-DD-<topic>-<note-type>.md`

## 自检清单（改完必过）

- [ ] 文档是否放到了正确分类目录，而非 `/tmp` / 桌面 / 仓库根目录？
- [ ] 是否存在重复 canonical 副本？→ 只保留唯一真源
- [ ] 说明性文本是否为简体中文？代码符号 / 表名 / 枚举值是否保留原文？
- [ ] 重要决策是否已落库，而非只留在聊天记录？

## 相关文档

- `.workbuddy/memory/MEMORY.md`（项目长期记忆）
- `docs/方案评审定稿纪要.html`（唯一有效契约基线）
- 参考 mds：`/Users/admin/IdeaProjects/mds/AGENTS.md`、`/Users/admin/IdeaProjects/mds/llm-wiki/AGENTS.md`
