# 本地大脑 · 蒸馏知识快照

本目录是「本地大脑」知识库（`~/.local/share/knowledge.json`）经自动蒸馏后导出的**版本控制快照**。

## 来源与流程
- **源数据**：`~/.local/share/knowledge.json`（由各 AI 工具在对话后自动捕获知识点写入，节点约 3790+）。
- **蒸馏脚本**：`~/.local/share/kb_distill.py`（清理噪音 → 补全 domain/kind 语义维度 → 按 group 分主题蒸馏为 Markdown）。
- **触发**：知识写入后防抖自动蒸馏 + 每日 03:00 LaunchAgent（`com.user.knowledge-distill.plist`）+ 手动 `python3 ~/.local/share/kb_distill.py`。
- **本地大脑服务**：`~/.local/share/kg_server.py`（http://localhost:8777），提供 `/query`、`/semantic`、`/linked`、`/ml/*` 等接口与 AI 实验室（带护栏的向量化/线性回归/神经网络）。

## 文件说明
- `00-总索引.md`：清理与蒸馏说明 + 主题文档清单 + 维度分布。
- `01-app.md` ~ `07-risk.md`：按 group 分主题的核心知识（排除 tool/document 原始资料节点）。

## 同步方式
`kb_distill.py` 在渲染 `kb-docs/` 后会一并同步到本目录（best-effort、失败静默），
因此每次蒸馏后本目录即最新快照。可用 `git commit` 纳入版本控制以追踪知识演进。

> 本目录与 `docs/knowledge-base/`（loan-main 项目自身文档）相互独立，请勿混淆。
