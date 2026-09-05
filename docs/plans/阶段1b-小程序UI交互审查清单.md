# 阶段 1b · 小程序端（loan-mini）UI 交互功能缺失与优化审查清单

> **审查人**：Alice（产品经理 · UI/交互维度，与架构师的逻辑审查互补）
> **审查日期**：2026-08-30
> **审查范围**：`loan-mini/pages/`（11 页）+ `loan-mini/components/`（10 组件）+ `loan-mini/api/`（请求封装）
> **审查基准**：
> - 知识库 `05-前端工程要点.md`（Token / 组件 / 无障碍）
> - 知识库 `08-小程序角色功能矩阵.md`（7 角色差异）
> - 知识库 `06-业务结论沉淀索引（C1–C19）.md` + 真源 `小程序模块结论沉淀.md`
> - 设计系统规范 `loan-mini-设计系统规范.md` v1.0
> - 交互原型 `loan-mini-交互原型-7角色.html`（7 角色交互基准）
> - 审计基线 `loan-mini-原型UI审计与缺陷清单.md`（22 项缺陷，本轮核对修复情况）
> **审查方法**：逐页静态走查 + 组件 API 交叉核对 + 角色矩阵对照 + 无障碍（对比度 / 触控 / 键盘）实测
> **说明**：本清单仅输出问题与建议，**不直接修改代码**；P0 阻断业务，P1 明显受损，P2 打磨。

---

## 〇、总体结论（摘要）

- 角色化导航、C3/C7 角色二分筛选、C4 命中产品 tab、C10 自动查重、C9 产品状态机等**主干交互已落地**，比原型审计基线（22 项）有明显改善。
- 仍存在 **3 项 P0 交互断裂**（材料上传为 mock、替客匹配 clientCode 被丢弃、编辑产品不回填）、**1 项系统级组件 API 断裂**（AppButton `variant` vs `type`，23 处调用全部失效）、**2 页未 Token 化**（mine 43 处、order/detail 15 处裸 hex，与 C16/C18 记录不符）。
- 交互三态中：**加载态 / 空态基本齐全，错误态系统性缺失**（列表/诊断失败均被渲染成"暂无/生成中"，无法区分网络错误与真实空数据）。

---

## 一、各页面交互审查明细（页面 × 问题 × 依据）

### 1. pages/match/match.vue（智能匹配）

| # | 问题 | 位置 | 依据 | 级别 |
|---|------|------|------|:---:|
| M1 | **材料上传为 mock**：`onUpload/onSupplement` 仅 `uni.showToast('打开文件选择器…')`，未接 `uni.chooseMessageFile/uploadFile`；4 个材料瓦片状态（ok/fail/pending/empty）硬编码静态，上传后不更新 | L395-400 / L386-391 | 原型 renderMatch 步骤2 有真实上传入口；C5 依赖上传材料；设计系统 §五 Loading/交互状态 | **P0** |
| M2 | **替客匹配 clientCode 被丢弃**：页面 `onSubmit` 传 `{clientCode}`，但 `api/match.js runMatch` 仅解构 `{facts, applyCity, clientSubmitId}`，clientCode 未透传后端 → 员工选定归属客户后发起匹配，后端收不到目标客户，C2 闭环断裂 | match.vue L496 / api/match.js L25-31 | C2 替客匹配「仅可在我归属客户上发起」 | **P0** |
| M3 | **步骤 1→2 无表单校验**：`nextStep` 直接 `currentStep+=1`，空表单可进上传步；仅在步骤 3「开始匹配」时 toast 校验并强制跳回步骤 1 → 用户在最后一步才被拦截 | L415-417 / L452-457 | 设计系统 §五 交互状态矩阵；表单校验应有字段级提示 | P1 |
| M4 | **查重失败无错误分支**：`onTargetInput` 中 `await searchClient(v)` 无 try/catch，接口失败时 `dupState` 停留 `'checking'` 无限转圈 | L314-319 | P1-5 错误态；C10 查重 | P1 |
| M5 | **核验清单硬编码含 1 条 fail**（"企业年报核验未通过…请补充材料后再发起匹配"），但「开始匹配」按钮仍可点击 → 失败态下允许继续，语义矛盾 | L403-407 / L238 | C5/C13 交互一致性 | P1 |
| M6 | 成功反馈 icon 弱：`已创建并归属给你` / `已自动归属给你` / `已提交` 均用 `icon:'none'`，应 `icon:'success'` | L340/355/359 | 成功反馈标准 | P2 |
| M7 | `applyCity` ref 无任何 UI 输入，恒为 undefined（死状态） | L290/493 | 死代码 | P2 |
| M8 | 裸色值 9 处：`switch color="#0B1D3A"` ×2、结果卡 `#fff`、`.rc-pass/.rc-condition/.rc-reject` 渐变 | L199/203/674/682-684 | C18 Token 化 | P2 |
| M9 | 创建客户无二次确认且手机号无 11 位格式校验（`type="number"` 仅限数字键盘） | L108/323-344 | 表单校验完整性 | P2 |
| M10 | 未认证引导/渠道禁入空态 ✅；查重三态（checking/hit/miss）✅；替客申请分配后停留在步骤 0 并展示 pending-box ✅（对齐 C2） | — | — | ✅ |

### 2. pages/index/index.vue（登录落地页）

| # | 问题 | 位置 | 依据 | 级别 |
|---|------|------|------|:---:|
| I1 | **DEV 面板无环境门禁**：`showDevPanel = ref(true)` 恒显示，生产包登录页暴露角色切换（可调 `loginByCrm('crm-boss-001')` 拿 STAFF token）→ 越权风险 | L73/132 | 注释声明"仅开发环境显示"但代码未门禁 | **P1** |
| I2 | 邀请码绑定按钮无进度文案（loading 时按钮文字不变） | L40 | 加载反馈 | P2 |
| I3 | 主按钮 `cta-btn`、`bind-btn` 为原生 `<button>` + 手写样式，未收敛到 AppButton 组件（颜色已 Token 化 ✅） | L64/34 | 组件收敛（设计系统 §三） | P2 |
| I4 | 微信登录失败在 H5 有引导文案 ✅；已登录自动跳首页 ✅；合规声明 ✅ | — | — | ✅ |

### 3. pages/auth/auth.vue（身份认证）

| # | 问题 | 位置 | 依据 | 级别 |
|---|------|------|------|:---:|
| A1 | 企业信用代码仅非空校验，**无 18 位格式校验**（个人身份证有 regex）；所有表单错误仅 toast，无字段级红字/红框 | L156-160 / L166-169 | P1-5 Error 态；表单校验完整性 | P1 |
| A2 | `switch color="var(--brand-deep)"` 在微信原生组件上 CSS 变量不解析（小程序原生组件不支持 var()），switch 实际显示默认绿 | L96 | 视觉一致性 | P2 |
| A3 | 个人年龄/城市/企业联系人等无最大长度限制；企业信用代码输入无失焦校验 | L47/84/88 | 表单健壮性 | P2 |
| A4 | 提交 loading/防重复提交 ✅；合规勾选拦截 ✅；认证成功跳首页 ✅ | — | — | ✅ |

### 4. pages/home/home.vue（首页）

| # | 问题 | 位置 | 依据 | 级别 |
|---|------|------|------|:---:|
| H1 | **缺 tabBar 底部安全区 padding**：`.home-page` 无 `padding-bottom: calc(var(--space-16) + env(safe-area-inset-bottom))`，底部合规声明（`.footer-safety` 仅 48rpx）可能被自绘 tabBar 遮挡 | L248-253 / L562-569 | 05-前端工程要点 C17：每个 tab 页底部需预留 | **P1** |
| H2 | 渠道差异化 ✅（隐藏认证卡/顾问卡/合作提示，入口换「我的产品/我的」）；`partnerCount` 加载失败静默为 0（无错误提示，可接受） | L184-198 | C17 | ✅ |
| H3 | `bind-btn` 高度 80rpx（40px）略低于 44px 触控目标 | L496-507 | WCAG 2.5.5 | P2 |
| H4 | 顾问服务卡未绑定态无空态插画（仅一行文字+输入框），对比原型略显单薄 | L57-68 | 体验优化 | P2 |

### 5. pages/report/list.vue（报告列表）

| # | 问题 | 位置 | 依据 | 级别 |
|---|------|------|------|:---:|
| R1 | **加载失败与空态混淆**：`fetchList` catch 后列表为空 → 渲染「暂无匹配报告/无匹配的报告」，网络错误被误读为无数据 | L185 | P1-5 错误态 | P1 |
| R2 | 「点击加载更多」为裸 `<text>`（约 24rpx 高），无按钮语义、无 role/tabindex、触控目标不足；「下拉刷新」无刷新图标 | L96-100 | WCAG 2.5.5 / 2.4.7 | P1 |
| R3 | 页头裸色值 `#1A1A2E`/`#6B7280`（应 `--text-primary`/`--text-secondary`）；遗留整块 `.report-item` 死 CSS（迁移 AppListItem 后未清理，含 16 处裸 hex） | L426-439 / L444-522 | C18 Token 化 | P2 |
| R4 | `filters.owner` 默认表达式恒为 `'all'`（`cond ? 'all' : 'all'` 死逻辑） | L150 | 死代码 | P2 |
| R5 | 空态双语义（从未有/筛选无结果）✅；骨架屏 ✅；客户仅日期筛选、员工四维 ✅ | — | C3/C11 | ✅ |

### 6. pages/report/detail.vue（报告详情）

| # | 问题 | 位置 | 依据 | 级别 |
|---|------|------|------|:---:|
| D1 | **诊断接口失败与"生成中"混淆**：`loadDiagnosis` catch 置空对象 → 渲染「诊断内容生成中」，后端错误被误读为"算法未落地" | L304-312 / L185-187 | P1-5 错误态 | P1 |
| D2 | **「上传最新材料」为 mock**：`onUploadMaterial` 仅 toast，无真实上传 → C5「材料非最新→上传最新材料重生成」闭环缺失 | L316-318 | C5 | P1 |
| D3 | 结果卡 `rc-pass` 渐变 `#15803D→#22C55E` + 白字，渐变末端对比度 ≈2.1:1 不达 AA；且绿色系非品牌 token | L393-395 | WCAG 1.4.3 / C18 | P1 |
| D4 | KPI `k.color` 由后端内联 hex 注入（`:style`），绕过 Token 体系，后端色值对比度不可控 | L128 | Token 优先（设计系统 §二） | P1 |
| D5 | 裸色值 12 处（结果卡/评级块渐变、`score-track` 圆角 6rpx 裸值等） | L393-441 | C18 | P2 |
| D6 | C4 命中产品 tab（员工可见/客户脱敏）✅；产品明细懒加载 ✅；报告不存在空态 ✅；规则命中说明 ✅ | — | C4/C5/C6 | ✅ |

### 7. pages/order/list.vue（服务单列表）

| # | 问题 | 位置 | 依据 | 级别 |
|---|------|------|------|:---:|
| O1 | 加载失败与空态混淆（同 R1） | L198 | P1-5 错误态 | P1 |
| O2 | 「点击加载更多」裸 text，触控/语义不足（同 R2） | L98-102 | WCAG | P2 |
| O3 | 四维筛选 ✅（客户仅状态+时间、员工姓名+手机+状态+时间）；6 项状态分段 3 列折行且 min-height 88rpx ✅；日期时间戳由后端计算 ✅（修复 P0-5） | — | C7 | ✅ |

### 8. pages/order/detail.vue（服务单详情）

| # | 问题 | 位置 | 依据 | 级别 |
|---|------|------|------|:---:|
| OD1 | **全页未 Token 化**（15 处裸 hex）：背景 `#f5f7fa`、状态卡蓝色渐变 `#1e3a8a→#2563eb→#0ea5e9`（非品牌深海军蓝）、卡片 `#ffffff/#e5e7eb/#1f2937/#6b7280` 等 | L137-245 | C18 Token 化 / 视觉一致性 | **P1** |
| OD2 | Loading 用文字「加载中…」而非 AppSkeleton，与 report/detail 不一致 | L4-6 | 加载态统一 | P2 |
| OD3 | 空态 `AppButton type="primary"` 使用了正确 prop ✅（唯一用 `type` 的地方），但页面整体未用 AppCard/AppListItem 收敛，手写 .card/.info-row | L64-66 / L192-232 | 组件收敛 | P2 |
| OD4 | 状态卡白字 on 蓝色渐变 `#0ea5e9` 区域对比度 ≈3.2:1（白字 ≥3:1 大字勉强，小字不达 4.5:1） | L156 | WCAG 1.4.3 | P2 |

### 9. pages/product/list.vue（我的产品）

| # | 问题 | 位置 | 依据 | 级别 |
|---|------|------|------|:---:|
| P1 | **加载失败与空态混淆**：`load` catch 置 `[]` → 渲染「暂无产品」，网络错误被误读（同 R1） | L194-196 | P1-5 错误态 | P1 |
| P2 | 状态图例（legend）面向渠道语境（草稿/待审批/上架/驳回/待删除），**员工角色**（可录入银行产品）进入同页仍显示渠道图例，角色语境偏差 | L98-104 / mine.vue goProduct | C9 角色差异 | P2 |
| P3 | **员工从 mine navigateTo 进入本页时渲染 TabBar current="product"**，但员工 tab 列表无 product → 无高亮项，tabBar 与导航语义不符 | L70 / TabBar.vue L47-62 | C17 角色化导航 | P2 |
| P4 | 「申请删除」破坏性操作有 showModal 二次确认 ✅（修复 P0-3 审批流）；按钮级 loading ✅；删除审批流后端已打通 PENDING_DELETE ✅ | — | C9 | ✅ |

### 10. pages/product/edit.vue（录入/编辑产品）

| # | 问题 | 位置 | 依据 | 级别 |
|---|------|------|------|:---:|
| PE1 | **编辑模式不回填**：`onLoad` 仅存 `code`，无详情拉取（代码 TODO），点击「编辑/编辑重提」打开**空表单**，且银行产品编码 disabled 为空 → 用户看不到原数据，保存即覆盖/校验失败 | L92-98 | C9 状态机（DRAFT/REJECTED 可编辑重提） | **P0** |
| PE2 | 无 `amountMin ≤ amountMax` 校验；`cooperateUntil` 无日期格式校验（自由文本） | L17-29 / L132-138 | 表单校验完整性 | P1 |
| PE3 | JSON 解析字段级错误/成功提示 ✅（P1-5 Error 态落地）；保存防重复提交 ✅ | L41-44 | — | ✅ |

### 11. pages/mine/mine.vue（我的）

| # | 问题 | 位置 | 依据 | 级别 |
|---|------|------|------|:---:|
| MI1 | **全页 43 处裸 hex**（`#FFFFFF/#1A1A2E/#9CA3AF/#10B981/#FDE68A/#92400E/#EF4444` 等）——与 C16「新改 5 页 100% 用 Token」记录不符，实际未 Token 化 | L310-522 | C18 Token 化 | **P1** |
| MI2 | 奖励汇总卡未按角色门禁（功能矩阵：**奖励汇总仅客户 ✅，其余角色 —**），员工/渠道若接口有返回也会展示 | L103-120 | 08-角色功能矩阵 | P2 |
| MI3 | `customerGroupLabel` 计算属性未使用（死代码）；`chip-ok` 文字 `#10B981` on 浅绿底 ≈2.5:1 不达 AA | L220-224 / L360 | 死代码 / WCAG | P2 |
| MI4 | 触控目标：`copy-btn` 72rpx（36px）、`go-bind-btn` 64rpx（32px）均 < 44px | L436-446 / L487-497 | WCAG 2.5.5 | P1 |
| MI5 | C8 角色化账户字段 ✅（客户实名/手机/性别/注册/邀请人；渠道银行信息；员工工号部门，老板/超管隐藏工号部门） | L181-218 | C8 | ✅ |

---

## 二、角色视角偏差（对照 08-角色功能矩阵 + 交互原型）

| # | 偏差 | 现状 | 依据 | 级别 |
|---|------|------|------|:---:|
| R1 | **运营/超管无审批中心/工作台入口**：TabBar 对所有非渠道角色渲染同一 5 tab（首页/匹配/报告/服务单/我的）；原型与设计系统 §六要求运营/超管为「工作台/智能匹配/全部报告/审批中心/我的」，C19 的 3 个分配审批接口已就绪但小程序无入口 | TabBar.vue L47-62 | 08 矩阵「审批中心 🟡 无入口」；C19 | **P1** |
| R2 | TabBar label 文案：员工/运营场景原型为「全部报告」，实现恒为「我的报告」（列表页头已正确显示「全部报告」，仅 tab label 偏差） | TabBar.vue L58 | 原型 tabsFor | P2 |
| R3 | 员工从 mine 进入 product/list 渲染无高亮 tabBar（见 P3） | product/list.vue L70 | C17 | P2 |
| R4 | product/list 状态图例按渠道语境渲染，员工角色语境不符（见 P2） | product/list.vue L98 | C9 | P2 |
| R5 | 渠道 4 tab（首页/我的产品/录入客户/我的）隐藏匹配/报告/服务单 ✅；「录入客户」tab 已于阶段三落地（**纠错证据**：原文记「实现 3 tab」有误，实测 `TabBar.vue` 为 4 tab）✅ | TabBar.vue L48-53 | C1/C3/C7/C17 | ✅ |
| R6 | 客户仅日期筛选、员工四维、渠道不可见 ✅；报告详情客户脱敏/员工全量 ✅；match 渠道禁入 ✅；mine C8 差异化 ✅ | 各页 | C1/C3/C4/C7/C8 | ✅ |

---

## 三、视觉 / Token 一致性

| # | 问题 | 位置 | 依据 | 级别 |
|---|------|------|------|:---:|
| T1 | **AppButton API 系统性断裂**：组件 props 为 `type`（primary/ghost/text/gold），页面 23 处调用用 `variant`（13 primary + 10 secondary）→ 全部回落到默认 primary；**所有次操作按钮（上一步/重置/重新匹配/解析预览/编辑/上传最新材料）渲染成主按钮深蓝**，主次层级失效 | AppButton.vue L33-40 vs match/report/order/product 各页 | 设计系统 §3.1（spec 写 variant，实现为 type，二者不一致） | **P1（全站）** |
| T2 | **AppButton `block` 默认 true**（设计系统规范默认 false）→ 非通栏按钮（报告详情「上传最新材料」、产品卡操作组、诊断 header 按钮）实际通栏，布局异常 | AppButton.vue L36 | 设计系统 §3.1 | P1 |
| T3 | mine.vue 43 处裸 hex（见 MI1） | mine.vue | C18 | **P1** |
| T4 | order/detail.vue 15 处裸 hex + 蓝色渐变非品牌（见 OD1） | order/detail.vue | C18 | **P1** |
| T5 | report/list.vue 23 处裸 hex（页头 + 死代码块） | report/list.vue | C18 | P2 |
| T6 | report/detail.vue 12 处裸 hex；match.vue 9 处裸 hex（switch color 字面量等） | 两页 | C18 | P2 |
| T7 | TabBar 组件裸色值 `#FFFFFF/#F0F1F3/#9CA3AF/#0B1D3A`，tab-label 21rpx 小于 `--fs-xs`；未用 `role="tablist"/aria-selected` | TabBar.vue | C18 / 无障碍 | P2 |
| T8 | index/home/auth 三页仍用原生 `<button>`+手写样式而非 AppButton（颜色 Token 化 ✅，组件未收敛） | 三页 | 组件收敛 | P2 |
| T9 | AppButton/AppEmpty/AppSkeleton/AppListItem 仍依赖 `--color-*` 兼容别名（如 `--color-primary`）而非新 token（`--brand-deep`）→ 迁移未完成 | 各组件 | C18 迁移说明 | P2 |
| T10 | AppTag 实现用 `type`、设计系统文档写 `tone`，文档与实现命名不一致（页面按实现用 type 工作正常） | AppTag.vue | 文档同步 | P3 |

---

## 四、无障碍问题（WCAG 2.1 AA）

| # | 问题 | 位置 | 实测/依据 | 级别 |
|---|------|------|------|:---:|
| A11 | **AppStepper 当前步白字 on 暖金**（`--gold`）：白字 #FFFFFF on #C8A96E ≈ **2.24:1** < 4.5:1（设计系统审计 P1-2 已修复角色徽章，但步骤条遗漏） | AppStepper.vue L116-121 | WCAG 1.4.3 | **P1** |
| A12 | match 结果卡 `rc-condition` 渐变 `#8A6D3A→#C8A96E` + 白字「需补料」末端 ≈2.24:1；report/detail `rc-pass` 渐变 `#15803D→#22C55E` + 白字末端 ≈2.1:1 | match.vue L683 / report/detail.vue L393 | WCAG 1.4.3 | P1 |
| A13 | mine `chip-ok` 文字 `#10B981` on rgba(16,185,129,.2) 白底 ≈**2.5:1**；TabBar 未激活文字 `#9CA3AF` on 白 ≈**3.1:1** | mine.vue L360 / TabBar.vue L111 | WCAG 1.4.3 | P1/P2 |
| A14 | **触控目标不足**：「点击加载更多」裸 text（report/order 列表）；mine `copy-btn` 72rpx、`go-bind-btn` 64rpx；home `bind-btn` 80rpx（均 < 88rpx/44px） | 各页 | WCAG 2.5.5 | P1 |
| A15 | 可点击 `<view>`（date-chip / owner-item / status-item / tab-item / type-item / upload-tile / role-item / load-more）均无 `role="button"` + `tabindex`，H5 键盘不可达（仅 AppListItem 自带） | report/order/match/auth/product 页 | WCAG 2.1.1 / 2.4.7 | P2 |
| A16 | TabBar 无 `role="tablist"/role="tab"/aria-selected`；AppTag 状态标签无 `aria-label`（设计系统要求「状态：待审批」读屏前缀） | TabBar.vue / AppTag 使用处 | 设计系统 §四 | P2 |
| A17 | 全局 `:focus-visible` + `prefers-reduced-motion` 已在 App.vue（H5 端）✅ | App.vue | — | ✅ |
| A18 | AppListItem 自带 role/tabindex/aria-label、筛选项 min-height 88rpx、骨架屏/双空态 ✅ | 各列表页 | — | ✅ |

---

## 五、体验优化建议

1. **匹配流程（最高优先）**：步骤 2 接入 `uni.chooseMessageFile` + `uploadFile`，上传中显示进度、成功后实时更新瓦片状态（已核验/待核验）；核验清单不再硬编码 fail 项，改为按实际上传状态驱动，fail 时禁用「开始匹配」并引导补料。
2. **错误态与空态分离**：report/order/product 列表与 report/detail 诊断增加 `hasError` 状态，失败渲染「加载失败 + 重试」而非「暂无数据」。
3. **列表加载更多改为按钮**：使用 AppButton（size=sm, variant=text）或至少补 min-height:88rpx + role/tabindex。
4. **员工进入 product/list 不渲染 TabBar**（或改 reLaunch 导航，与 tab 语义一致）；员工角色下 legend 文案切换为「产品审批状态」语境。
5. **编辑产品补详情回填**：新增 `/api/mini/product/{code}` 详情接口或列表页传完整对象，编辑态回填表单。
6. **运营/超管补轻量审批入口**：按 KB 08 建议在 mine 页给 OPERATOR/SUPER_ADMIN/BOSS 加「分配审批」入口（复用 C19 pending/approve/reject 接口）。
7. **成功反馈统一**：创建客户/提交审批/保存草稿/撤销审批等成功 toast 改用 `icon:'success'`。
8. **KPI 颜色去后端 hex**：诊断 KPI 只返回语义键（high/mid/low），前端映射 `--success-text/--warning-text/--danger-text`，避免对比度失控。
9. **底部安全区统一**：home 页补 `padding-bottom: calc(var(--space-16) + env(safe-area-inset-bottom))`；全站检查 tabBar 遮挡。
10. **DEV 面板门禁**：`showDevPanel` 以 `import.meta.env.DEV`（H5）/ 后端 dev 开关控制，生产构建强制隐藏。
11. **邀请码「7 天有效」可加倒计时**（到期换新提示）；报告列表「下拉刷新」加刷新图标。
12. **AppButton 修复后全站回归**：`variant` → `type` 统一（或组件兼容 variant），重点核对「上一步/重置/重新匹配」等次操作的描边样式与 `block` 默认值。

---

## 六、优先级汇总

### 🔴 P0（阻塞业务流程，建议工程师本轮修复）
| # | 问题 | 页面 |
|---|------|------|
| P0-1 | 材料上传为 mock（onUpload/onSupplement/onUploadMaterial 仅 toast） | match / report-detail |
| P0-2 | 替客匹配 `clientCode` 在 api 层被丢弃，C2 闭环断裂 | match + api/match.js |
| P0-3 | 编辑产品不回填（空表单编辑，保存覆盖风险） | product/edit |

### 🟠 P1（明显受损 / 系统级一致性 / 无障碍）
| # | 问题 | 归属 |
|---|------|------|
| P1-1 | AppButton `variant`/`type` API 断裂（23 处调用失效，次操作全渲染主按钮）+ `block` 默认值不符 | 全站组件 |
| P1-2 | 运营/超管无审批中心/工作台入口 | TabBar + mine |
| P1-3 | mine.vue / order/detail.vue 未 Token 化（43/15 处裸 hex） | 两页 |
| P1-4 | 列表/诊断 加载失败与空态混淆（错误态缺失） | report/order/product/detail |
| P1-5 | 匹配步骤 1→2 无校验、查重失败无限 loading、核验 fail 仍可提交 | match |
| P1-6 | DEV 面板无环境门禁泄漏到生产 | index |
| P1-7 | 无障碍：AppStepper 白字金底 2.24:1、结果卡渐变白字 <4.5:1、mine chip-ok 2.5:1、触控 3 类 <44px | 组件/页面 |
| P1-8 | 企业信用代码格式校验缺失；home 页 tabBar 安全区遮挡 | auth / home |
| P1-9 | KPI 颜色走后端 hex 绕过 Token；成功 toast 用 icon:'none' | report-detail / 多处 |

### 🟡 P2（打磨项）
- 各页裸 hex 残留（report/list 23、report/detail 12、match 9）；TabBar 裸值 + tab 语义；死代码清理（.report-item、customerGroupLabel、filters.owner 死表达式、applyCity）。
- 加载更多可点性、员工 product 页 tabBar 无高亮、reward 卡角色门禁、product legend 角色语境、TabBar label「全部报告」、邀请码倒计时、auth switch color token、AppButton 别名 token 迁移、order/detail 骨架屏统一、OD 状态卡对比度、文档（AppTag tone/type）同步。

---

**审查角色**：Alice（产品经理 · UI/交互）
**状态**：清单已输出，待 team-lead 汇总后转工程师（阶段 1c）修复；本清单不包含代码修改。
