# 16 · 跨 AI 工具规范与 Skills 总览（按项目分类）

> 用途：把各 AI 工具（Cursor / Copilot / Codex / WorkBuddy）在项目和各自配置里落地/维护的 md 文档与 skills **统一定义、按项目归类**，供"先读再改"时一站速查。
> 真源：各 `AGENTS.md` / `CLAUDE.md` / `copilot-instructions.md` / `docs/knowledge-base/*` / 各工具 `skills/*`。本文件是索引与提炼，冲突以真源为准。

## 0. 盘点范围与来源

| 类别 | 位置 | 内容 |
|------|------|------|
| 项目知识库 | `loan-main/docs/knowledge-base/00~15 + README` | 业务域/红线/角色/API/前端/结论沉淀/决策日志 |
| 项目红线 | `loan-main/AGENTS.md` | 5 条基础设施红线 |
| 项目 skills | `loan-main/.workbuddy/skills/`（25 个） | 后端/前端/角色/团队 全栈规范 |
| 全局 skills | `~/.workbuddy/skills`、`~/.cursor/skills`、`~/.copilot/skills`、`~/.codex/skills` | 跨工具通用/专项技能 |
| 工具约定 | `~/.codex/AGENTS.md`、`~/.cursor/skills/local-brain/SKILL.md` | Output style / 知识捕获 / 本地大脑 |
| 其他项目 | `deer-flow-main/*`、`~/IdeaProjects/market/*`、`~/IdeaProjects/mds/*`、`~/IdeaProjects/dev/*` | 仅登记路径，按需深读 |

---

## 1. 项目：loan-main（企融通 · 企业贷款咨询服务系统）

### 1.1 业务域与系统构成
- 四端联动：`loan-service`(Java/Spring Boot) + `loan-gateway`(网关) + `loan-web`(Vue3 管理端) + `loan-mini`(uni-app H5/mp-weixin) + `loan-api`(DTO)。
- 数据库 MySQL `loan_db`。
- 核心业务：智能匹配、经营诊断报告、客户归属流转、公海认领/回收、线索录入、审批中心、OCR 材料回灌、消息中心、邀请引荐、奖励。

### 1.2 业务红线（不可破）

**A. 业务红线（来自 `02-业务红线与编码规范.md`，用户明示）**

| # | 红线 | 后果 |
|---|------|------|
| 1 | ~~助手不碰 `loan-web`（Web 管理端）~~ **【已废止 · 2026-09-22】** | ⚠️ **过时**：该条已被 `02` 文档于 2026-09-22 标注废止。Web 端早已由 AI 协助改造（`docs/design-system/` 令牌化、`UI整改执行记录-Web-2026-09-06.md`、登录 UX 修复 `Login.vue`/`request.js`/`auth.js`）。现行口径：改 Web 端前先读 `docs/knowledge-base/` 与 `docs/design-system/` 真源，遵循 `loan-code-standard` 与 stylelint 门禁 |
| 2 | **定时任务必须用 XXL-Job**，禁止 `@Scheduled` | 避免漏配置导致不执行 |
| 3 | **业务 ID：小写前缀 + 32 位随机** | `BizIdGenerator.generate("xxx")`，不用主键做查询，需业务唯一索引 |
| 4 | **AES 读取解密需在 Service 手动做** | `@TableField typeHandler` 仅写入生效 |
| 5 | **参考项目（tse）只借鉴范式**，禁止把参考项目自身业务动作当本项目需求 | 需求必须独立论证 |
| 6 | **每次沟通先读知识库再改**（2026-08-30 明示） | 沟通纪律，禁止跳过阅读直接开发 |
| 7 | **业务客户端不展示任何物理 ID/业务编码/单号** | 小程序/H5/Web 以客户名、提交人、事项、日期等语义展示；编码只作接口/路由参数和行键 |
| 8 | **MyBatis-Plus `in(col, collection)` 禁止传入空集合** | 空集合须 `isEmpty()` 守卫，否则 `WHERE (col IN ())` → MySQL 异常 500 |

**B. 基础设施红线（来自 `loan-main/AGENTS.md`，项目级）**

| # | 红线 |
|---|------|
| 1 | 本机只运行应用进程，**不启动/依赖本地 MySQL、Redis、Nacos、OSS/COS 模拟或 Docker** |
| 2 | 启动 `loan-service`/`loan-gateway` 前**必须显式提供 `-Dnacos.server-addr` 与 `-Dnacos.namespace`**；缺失立即失败，**禁止回退到 `127.0.0.1`、默认密码或本地文件存储** |
| 3 | 数据库/Redis/对象存储/JWT/内部令牌/短信/OCR·AI 等环境配置**必须从所选 Nacos 命名空间 `application.properties`(group=`loan`) 读取** |
| 4 | 数据库迁移**必须先从当前启动所选 Nacos 获取实际数据源**再执行；**禁止假定 `127.0.0.1:3306/loan_db` 是当前库** |
| 5 | 任何新增基础设施配置**不得在代码/启动脚本写可工作的本地默认值**；测试只能经 `test`/`l3`/`offline` 专用配置显式隔离 |

> ⚠️ 历史教训（2026-09-21 登录报 `Unknown column 'password'`）：后端误以 Nacos **prd** 命名空间连生产库，本地 dev 才有的 `t_staff.password` 列缺失。根因正是违反 B-2/B-4——**启动未锁定 namespace 或错连**。

### 1.3 编码规范与 API 契约要点
- **命名**：类 PascalCase、接口无 `I` 前缀；方法/字段 camelCase；常量 UPPER_SNAKE；包小写；表 `t_`+snake_case；字段 snake_case。
- **业务唯一 ID**：`BizIdGenerator.generate("report"|"order"|"alloc"|"client"|"product"|"staff"|"bank"|"screening_product")`。
- **敏感字段**：手机号/统一社会信用代码写入前 SHA-256 摘要（`phone_hash`/`credit_code_hash`）；AES 加密字段（phone/bank_contact.phone 等）；精确匹配前对入参同样摘要。
- **API 响应**：统一 `Result<T>`（code=0 成功）+ `PageResult<T>`（page/size/total/records）。
- **字段命名红线**：客户姓名=`contactName`、企业名=`entName`、客户编码=`clientCode`、报告号=`reportNo`、审批单号=`approvalNo`、归属人工号=`ownerStaffCode`/姓名=`ownerStaffName`；匹配结果态 `totalResult` ∈ PASS/CONDITION/REJECT/SKIP_SEGMENT_MISMATCH/ERROR。
- **错误码**：`COMMON_ERROR/PARAM_ERROR/UNAUTHORIZED/FORBIDDEN/DATA_NOT_FOUND`。
- **契约以 `loan-service` 源码为准**；旧文档 `login-bind`/`allocation-approvals/*` 等已废弃（见 `04-后端 API 契约.md` 顶部校正栏）。

### 1.4 业务 Bug / 踩坑记录（沙盒 + 技术坑，来自 `02` 末）

**A. 小程序 wxss/编译坑（编译失败级，必查）**
- `app.wxss` 含 `*::before`/`*::after` → 编译报错（微信不支持全局伪元素）→ App.vue 减动效块只用 `*`。
- 微信 wxss 不支持 `:root` / `:focus` / `:hover` / `@media (hover/prefers-reduced-motion)` / `:not()` → 全部 `#ifdef H5` 隔离。
- `**` 通用选择器 / `+` 相邻兄弟 → `error at token '*'` → 改 `.X view, .X button` 后代+标签选择器。
- `<text>` 嵌套 `<text>` → wxml 编译失败（`((!))` 占位符）→ 拆 sibling 或外层 `<view>`。
- `placeholder` 含 `{` → wxml 报错 → 避免特殊字符。
- `manifest.json` 误加 `appidNote` 注释字段 → 小程序打不开 → 只放微信官方字段。
- `project.config.json` appid 被重置为 `touristappid` → 改**项目根** project.config.json 的 appid。
- `AppIcon` 空 CSS 规则 `.ico-match {}` → minifier 移除导致渲染异常 → 每个 `.ico-*` 至少留一个非空属性。
- 长链 `v-if/v-else-if/v-else` → mp-weixin 偶发丢 children → 改独立 `v-if` 链。

**B. 构建/脚本坑**
- `npx uni build` ENOENT（manifest 在项目根）→ 用 `npm run build:h5`/`build:mp-weixin`。
- vite 清 dist 被 safe-delete 拦截（≥50 文件）→ `mv dist/build/{h5,mp-weixin} /tmp/xxx-$(date +%s)`。
- 批量替换 `\b#fff\b` 误伤 `#FFFFFF` 子串 → 先匹配长值再匹配短值 `(?<![0-9A-Fa-f])#FFF\b`。
- `and` 短路把整段清空 → 改前小样本验证 + `cp` 备份 `/tmp`。

**C. 后端坑**
- `in(col, emptyCollection)` → `SQLSyntaxErrorException` 500（红线 8，参照 `ApprovalService` 第 356 行守卫）。
- `PageResult.empty(int,int)` 泛型推断失败 → 用 `PageResult.build(page,size,0L,new ArrayList<>())`。
- `BankProduct` 字段名是 `productCode/amountMin/amountMax/rateMin/rateMax` → 查实体别臆测。

### 1.5 业务结论沉淀速查（C1–C26 + D50，来自 `06`）

| 结论 | 关键点 |
|------|--------|
| **C1** | 智能匹配：客户 + 全部 STAFF（除渠道）可操作；**渠道是唯一不可操作角色** |
| **C2** | 替客匹配 + 归属流转：新客户录入即归属；老客户有归属人自动归属；无归宿需审批 |
| **C3** | 报告查询维度：客户仅日期筛自己；员工四维跨归属；渠道小程序仍隐藏 |
| **C4** | 报告命中产品**仅员工可见**明细；对客脱敏（不返 productName/bankName） |
| **C5** | 经营诊断五块（KPI/建议/风险/历年/多维）；行业均值已表化 `t_industry_benchmark` |
| **C7** | 服务单四维筛选：客户仅状态+时间；员工姓名/手机/状态/时间；渠道隐藏 tab |
| **C17** | **角色化导航**：全端自绘 TabBar；`uni.reLaunch`（禁 `switchTab`）；store.role 修复 |
| **C18** | **Token 化收尾 + H5 hover**：页面禁裸色值；App.vue 全局 button hover |
| **C19** | **B 组数据模型缺口**：明细表 + 诊断算法 + 分配审批 + staffName 接 t_staff |
| **C20** | **渠道线索录入（T4）**：渠道走 Lead 不走 Client；AES+SHA 处理 phone/credit_code |
| **C21** | **OCR 回灌诊断（T2）**：`mergeFromOcr` 仅补空不覆盖；`loan.ocr.provider=mock` 可插拔 |
| **C22** | **审批中心统一（T5）**：无统一审批表，视图层+入口统一；白名单四类均开放 |
| **C23** | **管理端直接指定归属（免审批）**：`POST /api/admin/client/{code}/assign`，覆盖式，前端弹窗二次确认 |
| **C24** | **分配审批团队范围**：`DEPT_MANAGER` 仅审本团队；跨团队 BOSS；`FORBIDDEN` 指引 |
| **C25** | **顾问替客匹配对他人归属客户强制审批（P0-3）**：已归属他人→FORBIDDEN→引导发起审批 |
| **C26** | **客户超期回收 + 预警（P1）**：`t_client_recycle_config` + XXL-Job 调度；回收只更新非加密字段 |
| **D50** | **渠道本人线索/客户/报告只读**：仅终审通过进公海；Web 仅看本人录入客户报告，不可匹配 |

> 待办遗留（06 末）：行业均值已表化（T1✅）、OCR 回灌（T2✅）、审批统一（T5✅）；`t_service_follow` 后端尚无实体，`lastFollowedAt` 以归属/审批通过为刷新基线（跟进接入后须挂钩 `touchAssignment`，否则回收过早）；上线真实 AppID/urlCheck/HTTPS 待用户给值。

### 1.6 角色权限模型要点（来自 `01`）
- **7 角色**：客户 CUSTOMER / 渠道 CHANNEL / 顾问 ADVISER / 部门经理 DEPT_MANAGER / 老板 BOSS / 运营 OPERATOR / 超级管理员 SUPER_ADMIN·SUPER。
- 后端 `LoanUser`（`@ControllerUser` 注入）：userType + roleCode（仅 STAFF）+ deptCode。
- 前端 `store/user.js`：role 字符串（customer/channel/adviser/deptmgr/boss/operator/super）+ getters（isStaff/isChannel/isAuthed）；`roleCode` 未识别→adviser 兜底。
- 角色化导航（`components/TabBar.vue` + `utils/roles.js`）：客户 5 tab / 渠道 4 tab（唯一不可匹配）/ 顾问·经理 4 tab / 运营 5 tab / 老板·超管 5 tab；合并页「线索录入」「我的客户」；切换 `uni.reLaunch`。
- 常见错误：客户视角看全功能（isStaff getter 误判）、渠道被允许匹配（缺 `requireChannelGuard`）、切换用户菜单不变（未 `store.setUser` 触发 role 更新）、渠道显员工 tab（getter 未拿最新值）。

### 1.7 前端工程要点（来自 `05`）
- 构建：`npm run build:h5` / `build:mp-weixin`（禁 `npx uni build`）。
- 自绘 TabBar（禁原生）：`pages.json` 移除 tabBar 配置；切换 `uni.reLaunch` 禁 `switchTab`。
- easycom `^App(.*)` 自动；非 App 前缀显式 import；`@` = 项目根。
- **Token 体系全在 `App.vue` 注入**：品牌色 `--brand-deep` 等；**禁页面写裸色值**（C18）。视觉 D70：墨蓝 `#111E36`+皇家蓝 `#2C52C9`+香槟金 `#C7A15A`。
- 响应式：**字号下限 22rpx**（`--fs-xxs`）；宽屏治理走 **JS 驱动** `store.isTablet` + `u-shell`（wxss 不支持 media query，必须 `#ifdef H5` 隔离）。
- 无障碍 WCAG AA：对比度 ≥4.5:1、触控 ≥88rpx、`:focus-visible` 金色 outline。

### 1.8 项目级 Skills 清单（`.workbuddy/skills/`，25 个）
| 分组 | 技能 |
|------|------|
| 后端/数据 | `loan-backend`、`loan-biz-id`、`loan-code-standard`（**统一标准真源**）、`loan-database`、`loan-gateway-auth`、`loan-service-ops`、`loan-data-visualization` |
| 小程序 | `loan-mini-ui`、`loan-mini-avatar` |
| Web 管理端 | `loan-web-dev`、`loan-web-ui`、`loan-web-components` |
| 知识 | `loan-knowledge` |
| 角色（7） | `role-customer` `role-channel` `role-adviser` `role-deptmgr` `role-boss` `role-operator` `role-super` |
| 团队（4） | `team-architect` `team-engineer` `team-pm` `team-qa` |

> 代码标准化（复用/查询/缓存/并发幂等/异常/限流/traceId/名称主显）**唯一真源 = `loan-code-standard`**；`docs/skills/` 已废弃，不再维护重复副本。

### 1.9 沟通纪律（项目最高优先级）
> 每次小程序/Web/后端沟通与编码修改前，**先读本知识库相关模块文档 + 对应结论沉淀，再执行。禁止跳过阅读直接开发。**（README 明示）

---

## 2. 跨工具全局 Skills 总览（按工具）

| 技能 | WorkBuddy | Cursor | Copilot | Codex | 说明 |
|------|:---:|:---:|:---:|:---:|------|
| `i-have-adhd` | ✅ | ✅ | ✅ | ✅(AGENTS.md 规则) | 输出风格：行动优先/步骤编号/结论可见/列表≤5 |
| `knowledge-capture` | ✅ | ✅ | ✅ | ✅(AGENTS.md 段落) | 对话结束提取知识点写入 `~/.local/share/knowledge.json` |
| `local-brain` | — | ✅ | — | — | 本地大脑：知识图检索/联动（Cursor 专用） |
| `前端开发` | ✅ | — | — | — | 前端开发规范（已注册为 `spec-fe-frontend`） |
| `品牌设计风格专家` | ✅ | — | — | — | DESIGN.md 合集（Vercel/Stripe/Linear 等） |
| `薪资成本与人效分析` | ✅ | — | — | — | 工资表/人效 HTML 报告 |
| `银行流水信贷审查报告` | ✅ | — | — | — | 流水→信贷风险审查 HTML |
| `change-safety-check` | ✅ | — | — | — | 变更安全检查 |
| `doc-hygiene` | ✅ | — | — | — | 文档卫生规范 |
| `session-handoff` | ✅ | — | — | — | 会话/任务交接 |
| `figma` 系列（7） | — | — | — | ✅ | figma / create-design-system-rules / create-new-file / generate-design / generate-library / implement-design / use |

> 注：本地大脑的 8 类规范骨架（`hub-spec` + `spec-*`）已注册进 `knowledge.json`，其中仅 `spec-fe-frontend` 落了真实 skill 路径，其余 7 类（后端/UI/UX/架构/数据库/测试/运维）待写。

---

## 3. 其他已发现项目注册表（待按需深读）

| 项目 | AI 文档位置 | 备注 |
|------|------------|------|
| `deer-flow-main` | `frontend/AGENTS.md`、`frontend/CLAUDE.md`、`backend/AGENTS.md`、`backend/CLAUDE.md`、`.github/copilot-instructions.md` | 前端+后端双栈，已配 Copilot 指令 |
| `IdeaProjects/market/*` | `market-web/AGENTS.md`、`ma-executor/`、`ma-channel/`、`ma-base/`、`cdp-*`(base/rule/gateway/realtime-flink/compute/matcher) 各 `AGENTS.md` | CDP/营销自动化系列多模块 |
| `IdeaProjects/mds/*` | `mds/AGENTS.md`、`llm-wiki/AGENTS.md`、`deepseek-harness/`（多 AGENTS/CLAUDE） | LLM/知识库相关 |
| `IdeaProjects/dev/*` | `abs/.cursorrules`、`lps/AGENTS.md`、`lps/llm-wiki/AGENTS.md` | 开发工具类 |

> 以上仅登记路径。如需并入本总览，逐个深读其 `AGENTS/CLAUDE/copilot-instructions` 后补充「业务域/红线/bug/skills」四栏。

---

## 4. 与本知识库/本地大脑的关系
- 本文件是"按项目分类"的**索引与提炼**；红线/结论以 `docs/knowledge-base/02`、`06` 及 `AGENTS.md` 为真源。
- 跨工具的 `knowledge.json` 已承载 `spec-*` 规范骨架与 `hub-by-*` 多维导航；本文件第 2 节的 skills 清单可反向沉淀为 `spec`/工具节点。
- 修改任何结论/红线后，**反向更新本知识库**（追加结论沉淀、追加红线）。

---

## 跳到
- 红线真源：`02-业务红线与编码规范.md` + `../AGENTS.md`
- 业务结论：`06-业务结论沉淀索引（C1-C26）.md`
- 角色：`01-角色权限模型.md` · 前端：`05-前端工程要点.md` · API：`04-后端 API 契约.md`
- 跨工具知识库：`~/.local/share/knowledge.json` + `knowledge-graph-viewer.html`
