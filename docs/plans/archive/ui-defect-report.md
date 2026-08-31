# loan-main 前端页面交互与展示缺陷审查报告

> 审查范围：`loan-web/src`（Vue 3 + Element Plus + Pinia + Vite）
> 审查日期：2026-08-27
> 审查方法：静态代码走查全部 27 个页面、13 个公共组件、6 个工具/composable、2 个 store、主题与全局样式；对关键交互路径（列表查询、弹窗表单、分页排序、路由刷新、主题切换、表格固定列）逐条核对运行期行为。
> 等级定义：🔴 关键（功能失效 / 崩溃 / 数据丢失 / 安全）｜🟡 重要（展示与交互缺陷 / 主题一致性）｜🔵 建议（规范与体验优化）

---

## 一、问题总览

| 等级 | 数量 | 占比 | 说明 |
| --- | --- | --- | --- |
| 🔴 关键 | 12 | 28% | 排序表头点击报错、401 死循环风险、启动崩溃、Tab 失效、表单空值入库等 |
| 🟡 重要 | 25 | 58% | 样式补丁冲突、主题色硬编码、文案过时、交互断层、校验缺失等 |
| 🔵 建议 | 6 | 14% | 权限未接入、代码重复、命名冲突等 |
| **合计** | **43** | 100% | |

**核心结论**：系统在"功能可达"层面已完成，但存在 **1 个高危共性问题（排序表头点击即报错，波及 9 处页面）**、**1 个登录失效死循环风险**、**2 个应用级崩溃隐患**；全局样式层存在**三套互相矛盾的表格补丁**，主题系统对 Element Plus 主色变量的实现方式（rgba 半透明）会造成明显的按钮/悬停色差；"影子系统"已移除但 **4 处页面文案仍残留影子语义**，与当前"调试执行"定位不符。

---

## 二、🔴 关键问题（功能失效 / 崩溃 / 安全）

### K1. 排序表头点击报错：`handleSortChange` 未从 useTable 解构（9 处）

`useTable` 返回了 `handleSortChange`（`composables/useTable.js:79-98`），但以下页面在模板中使用 `@sort-change="handleSortChange"`，却未在解构语句中取出该函数。`<script setup>` 中模板引用未定义标识符，点击排序表头触发事件时抛 `TypeError`，**排序功能全部失效且控制台报错**：

| 页面 | 模板引用 | 解构语句（缺 handleSortChange） |
| --- | --- | --- |
| `views/audit/AuditCenter.vue` | L28 | L133 |
| `views/order/OrderList.vue` | L23 | L196 |
| `views/reward/RewardList.vue` | L18 | L118 |
| `views/approval/ApprovalCenter.vue` | L24、L31 | L172-177 |
| `views/sms/SmsCenter.vue` | L31 | L164-167 |
| `views/blacklist/BlacklistCenter.vue` | L25 | L98 |
| `views/lead/LeadPool.vue` | L39 | L139 |
| `views/report/ReportCenter.vue` | L59 | L177 |
| `views/template/ReportTemplateList.vue` | L19 | L82 |

**修复**：统一在解构处补上 `handleSortChange`：
```js
const { loading, data, total, query, load, onSearch, onReset, handleSortChange } = useTable(...);
```

---

### K2. 401 跳转未清除 token，与路由守卫构成死循环风险

`utils/request.js:31-35` 响应拦截器对 `code === 2000`（未登录）执行 `window.location.href = '/login'`，**但没有清除 `localStorage.token`**。而路由守卫（`router/index.js`）对"已登录访问 /login"会重定向回 `/workbench`。场景：token 过期 → 任意接口 401 → 整页跳 /login → 守卫发现 token 仍在 → 跳 /workbench → 列表接口再次 401 → 循环跳转，页面白屏闪烁。

**修复**：
```js
if (res.code === 2000) {
  localStorage.removeItem('token');
  localStorage.removeItem('loan_user');
  // 优先走 SPA 路由而非整页刷新
  const { default: router } = await import('@/router');
  router.push('/login');
  return Promise.reject(new Error(res.message));
}
```
（同时避免 `window.location.href` 整页刷新丢失状态。）

---

### K3. 用户信息 JSON.parse 无容错，本地数据损坏即启动崩溃

`store/user.js:13`：`user: JSON.parse(localStorage.getItem(USER_KEY) || 'null')`。若 localStorage 中的 `loan_user` 被写坏（手动编辑、旧版本残留、编码异常），`JSON.parse` 抛错，**应用启动即崩溃白屏**。

**修复**：
```js
let cachedUser = null;
try { cachedUser = JSON.parse(localStorage.getItem(USER_KEY) || 'null'); } catch { /* 忽略损坏数据 */ }
```

---

### K4. 产品库 Tab 切换不生效，两个 Tab 显示相同数据

`views/product/ProductList.vue:158` `onTabChange` 调 `onSearch()`，但 `query` 中没有 `tab` 字段、loader 也未传 `activeTab`——**切换"全量库/合作库"实际不改变查询条件**，两个 Tab 数据一致，核心功能失效。

**修复**：参照 `LeadPool.vue:139-147` 的正确写法——loader 闭包动态拼接 `activeTab` 参数，`onTabChange` 重置页码后 `load()`。

---

### K5. keep-alive 缓存实际失效

`layout/Layout.vue:364` 用 `path.replace(/[/-]/g, '_')` 生成 keep-alive 的 name（如 `_workbench`），但**所有页面组件均未定义 `name` 选项**。Vue 3 `<script setup>` 组件默认按文件名推断 name 需显式声明 `defineOptions({ name })` 才能被 keep-alive 命中——当前缓存名与组件名不匹配，**标签页切换后页面状态全部重建**（表格滚动位置、已展开行、表单输入丢失）。

**修复**：为每个路由页面组件添加 `defineOptions({ name: 'Workbench' })` 等（name 与路由 path 映射规则对齐），或改用 `<KeepAlive :include>` 匹配组件名列表。

---

### K6. 组织权限：接口权限树 node-key 与 setCheckedKeys 的 key 不一致

`views/org/OrgCenter.vue:462-464`：`apiTree` 节点分组 id 为 `'g_' + g`、叶子为 `'a_' + a.apiKey`，而 `el-tree` 的 `node-key="apiKey"`（叶子节点没有 `apiKey` 属性，只有 `key`）；回显 `setCheckedKeys(checked)` 传入的是 `'a_' + k`。**node-key 取值与回显 key 两套体系**，勾选回显失败、半选状态丢失。

**修复**：统一为 `node-key="key"`（每个节点显式带 `key`），回显直接传叶子 key 数组。

---

### K7. 组织权限：编辑员工时手机号被清空

`views/org/OrgCenter.vue:308` `openStaff` 中 `phone: ''` 覆盖了编辑时原有手机号，**编辑任意字段保存后手机号丢失**。

**修复**：编辑分支使用 `row.phone` 回填。

---

### K8. 渠道策略上线绕过"上线校验"

`views/plan/ChannelConfigWizard.vue:297` `onEnable` 直接调启用接口，未先执行 Step3 的"上线校验"（后端 `validateBeforeEnable`）；`strategyActions` 中"上线"同样直接 enable。**不满足准入条件的策略可以被上线**，与"写锁/上线校验"的渠道准入模型（mds v2）割裂。`StrategyList.vue:143-149` 的 `onEnable` 同样绕过校验。

**修复**：上线前先调 `validateStrategy`，校验失败弹窗展示问题明细并中止；仅校验通过才调 enable。

---

### K9. 渠道配置向导：创建计划后反查 planCode 绑定，存在竞态

`views/plan/ChannelConfigWizard.vue:359-361`：`onSavePlan` 创建计划后通过重新拉取计划列表反查 `planCode` 再绑定策略。**依赖列表刷新时序**，若新计划未排在最前/接口延迟，会绑定到错误 planCode 或绑定失败。

**修复**：创建计划接口若返回 `planCode` 直接使用；否则按创建时间/名称精确匹配，并校验唯一性后再绑定。

---

### K10. 布局页"刷新"功能依赖未注册路由

`layout/Layout.vue:441-448` `onRefresh` 通过 `router.replace({ path: '/__reload__' })` 强制重渲染，但 `/__reload__` **未在路由表中定义**——会触发 NotFound 匹配再跳回，URL 闪烁且刷新不可靠。

**修复**：改为 keep-alive 的 `exclude` 动态剔除 + `nextTick` 恢复（标准"刷新当前页"方案），或直接 `router.replace({ path: cur, query: { t: Date.now() } })` 触发组件复用重建（配合 `watch` 路由 query）。

---

### K11. 登录页密码不校验但存在"演示账号密码"死数据，安全语义混乱

`views/Login.vue:113-120`：`password` 的 `required: false`，注释称"SSO 模拟暂不校验"，但页面已走真实 `doLogin`（`store/user.js:27-35`）；`demoAccounts`（含 `password: '123456'`）**从未被使用**——用户以为密码参与校验，实际只填账号即可登录，且代码中残留明文口令。

**修复**：接入真实密码校验（与后端登录接口对齐），或删除 `demoAccounts` 与"暂不校验"注释，避免误导与明文口令残留。

---

### K12. 多业务弹窗无表单校验，空值可直接入库

以下弹窗均**未配置 `el-form` rules**（仅部分手动校验），必填项可空保存：

- `views/plan/ChannelConfigWizard.vue` L191-227：计划 / 模块 / 步骤弹窗全部无 rules，步骤的 `ruleId` 可为 null 提交
- `views/plan/StrategyTemplateList.vue` L47-105：模版 / 模块 / 步骤弹窗无 rules；L284 规则下拉硬编码 `customerGroup: 'ENTERPRISE'`，个人客群策略模版编排选不到个人规则
- `views/rule/RuleTemplateList.vue` L48-120：模版 / 字段弹窗无 rules
- `views/blacklist/BlacklistCenter.vue` L61-80：新增弹窗仅手动校验"命中值非空"，维度/原因分类可空
- `views/reward/RewardList.vue` L65-83、`views/approval/ApprovalCenter.vue` L110-140：审核/申请弹窗仅手动校验
- `views/plan/ChannelConfigWizard.vue`、`views/plan/PlanEdit.vue`：模块/步骤弹窗同缺 rules

**修复**：统一为 `el-form` 配置 `rules` + `formRef.validate()`；步骤的 `ruleId` 加 `required` 校验。

---

## 三、🟡 重要问题

### 3.1 全局样式：表格补丁三套并存、规则互相矛盾

`styles/index.css`：

- **固定列背景写了 3 套**：L478-565（注释"EP 2.6 sticky 结构"）、L1006-1018、L1070-1081 内容高度重复，仅暗色值不同；后期维护极易改一处漏一处。
- **同一属性互相覆盖**：L990-999 定义 `.el-table .el-table__inner-wrapper { width: max-content !important }`，L1021-1026 又定义 `width: 100% !important`（后者生效，但 `display: block !important` 与 EP 内部 flex 布局冲突）。
- **`display: block !important` 强制覆盖**（L992、L1022、L1028-1029）：EP 2.6 表格依赖 flex/内部高度计算，`!important` 强改可能引发表格高度、滚动条、嵌套表格（`ChannelConfigList.vue` 展开行内嵌表格）错乱。
- **固定列背景硬编码**：L1011 `background: var(--el-bg-color, #fff)`、L1017 暗色 `#131e33`——用户切换自定义主色后，固定列背景与表格主色不协调（浅色主题固定列应为卡片白而非透出）。

**修复**：收敛为单一补丁区（约 60 行），统一用 `--el-table-tr-bg-color` / `--el-bg-color` 变量，去掉 `display: block`，改用 EP 官方 `fixed` 属性 + 最小 sticky 兜底。

### 3.2 主题系统：EP 主色浅阶色用 rgba 半透明，产生视觉色差

`theme/index.js:135-139`：`--el-color-primary-light-3/5/7/8/9` 与 `dark-2` 用 `rgba(primaryRgb, x)` 半透明实现。Element Plus 内部期望的是**与白/黑混合后的不透明色**（如 `#79bbff`），半透明叠加在不同底色上产生明显色差（按钮 hover、浅色标签、选中态背景均受影响）。同时 `--loan-success` 浅色取 `#16a34a`（L117），在 `--loan-bg: #f5f7fa` 上的对比度约为 3.9:1，**低于 WCAG 2.1 AA 正文 4.5:1**。

**修复**：按 `mix(#ffffff, primary, 30%)` 方式计算不透明浅阶色；`--loan-success` 加深至 `#15803d` 一档或仅用于大字/图标。

### 3.3 过时文案：影子系统语义残留 4 处

系统已按需求移除影子系统、仅保留调试能力，但文案未同步：

- `views/Workbench.vue:102`："阶段一聚焦「配置产品 → **影子匹配** → 档位 → 审计」"
- `views/Workbench.vue:207`：待办 desc "模拟客户**影子执行**，验证规则引擎"
- `views/debug/DebugCenter.vue:6`：副标题 "**影子执行**：模拟客户空跑规则引擎，不落线上（dryRun）"
- `views/debug/DebugCenter.vue:10`、`:51`：按钮与空态文案 "点击「**影子执行**」"

**修复**：统一替换为"调试执行 / 试运行"；按钮文案与 `DebugCenter` 页标题（"调试中心"）对齐。

### 3.4 公共组件缺陷

| 组件 | 问题 |
| --- | --- |
| `AppTableActions.vue:90-103` | `maxInline` 注释"内联上限不含更多"与实现矛盾：`slice(0, maxInline-1)` 实际内联 `maxInline-1` 个 + 更多按钮共 `maxInline` 个按钮位，按注释意图应内联 `maxInline` 个 |
| `AppTableActions.vue:127` | 撤销链接 `style="color:#3b82f6"` 硬编码主色，不随主题变量 |
| `AppTableActions.vue:16` | `aria-expanded="'false'"` 写死不随展开状态更新（ARIA 误导，违反 WCAG 4.1.2） |
| `AppTableActions.vue:110` | `a.type === 'danger' ? 'warning' : 'warning'` 两个分支相同，冗余 |
| `AppDialog.vue` | `onConfirm` 不自动关闭而 `onCancel` 自动关闭，行为不对称；默认 `closeOnPressEscape: true`，含长表单弹窗误触 ESC 丢失输入 |
| `AppSearchBar.vue` | 组件不处理回车提交，各页需手动 `@keyup.enter`，约 10 处重复代码 |
| `AppPagination.vue:10-13` | `update:page` 与 `current-change` 双事件绑定本身不重复请求，但 **size-change 触发 `emit('change')` 时未重置页码**——第 5 页切每页条数后仍在第 5 页请求，可能返回空列表；`useTable.onSizeChange` 存在但未被使用 |
| `AppEmpty.vue` | 使用 `var(--empty-bg)` 未在 `:root` 定义；`rgba(59,130,246,.07)` 硬编码主色，换主题不变 |
| `ThemeSwitch.vue` | `aria-pressed` 绑定的 `isDark` 在 `onMounted` 才初始化，首帧闪烁；金色主色（`#f59e0b`）上的白色对勾对比度不足 |
| `AppContextMenu.vue:22` | `v-html="item.icon"` 渲染调用方传入内容（内部接口可接受，但公共组件存在注入面）；L103/L116 hover 色硬编码，浅色主题下 `--el-bg-color` fallback `#131e33` 偏深 |

### 3.5 业务页交互缺陷

- `Workbench.vue:395-403`：指标卡 `font-size: 28px` 但 `overflow: hidden` 截断大金额数字；L76 `recentMatches` 无点击跳转只有 hover 反馈，交互断层。
- `ProductList.vue:173-176`：删除后提示"可撤销"但 `undo` 仅提示"演示:产品删除不可逆"，**无真实撤销能力却展示撤销入口**，误导用户（防误删机制应改为后端软删除 + 恢复接口）。
- `ProductList.vue`：导出 CSV `size: 1000` 硬编码上限，超 1000 条漏导；城市弹窗 `city`/`province` 无省市级联校验（后端"市一级精确匹配"）。
- `DebugCenter.vue:549-551`：`step-expr` 用 `ellipsis` 截断表达式，关键规则不可见需 hover；结果树展开后无折叠，产品/模块多时页面过长；`applyCity` 输入无格式提示，随意输入 0 命中无引导。
- `LeadPool.vue:218`：指派员工只拉 `roleCode: 'ADVISER'`，与提示"仅顾问/主管可被指派"不符（主管角色未拉取）。
- `ChannelConfigList.vue:90-99`：展开行每次展开重新拉策略，无缓存，频繁展开/收起重复请求。
- `ChannelConfigWizard.vue`：`changeChannel` 只重置 `channelCode`，`strategies/modules` 旧数据残留；Step2 中无 `executionPlanCode` 的策略不触发"新建计划并绑定"引导。
- `PlanEdit.vue:127`：用 `planDialog.title === '编辑计划'` 判断编辑态（反模式），应显式传 `editing` 标志；无"该计划已被策略引用"的删除保护提示。
- `OrgCenter.vue:243`：员工/部门/角色权限的写接口从 `@/api/blacklist` 导入（`api/org.js` 只有查询类）——**命名与业务语义严重不符**，应拆分 `api/org.js` 补齐写接口后修正导入。
- `OrgCenter.vue`：员工"离职"确认弹窗文案未明确"离职"后果（账号停用、线索回收）。
- `ApprovalCenter.vue:131`：附件清单用文本框输入 JSON 字符串（`如 [1,2,3]`），无格式校验与友好交互，错误 JSON 到后端才报错；L82 `attachmentIds` 列直接显示原始数组。
- `ApprovalCenter.vue:145-152`：`onCopy` 函数定义在 `import` 语句之前（依赖 ESM 提升，风格混乱）；L16/64 用 `v-show` 双表常驻 DOM 且 `onMounted` 同时发起两个列表请求。
- `ScreeningCenter.vue:118-135`：`onRun` 先写入占位假数据（`grade: '—'`、全 0），随后串行调 `screeningDetail`；若详情接口失败，**占位假数据仍渲染为"初筛完成"**，误导用户；动态 `import('@/api/report')` 应改为顶部静态导入。
- `OrderList.vue:58`：列 label "创建时间" 绑定 `updatedAt` 字段，语义不符；L20 `el-checkbox label="仅我的工单"` 在 EP 2.6 中 `label` 已被赋值为 value 语义，显示文本应放默认插槽（新版可能不显示文字）。
- `RuleList.vue:180-225`：`listRules({})` 全量加载后前端过滤分页，数据量大时首屏慢且内存占用高；`query` 用 `ref` 与其他页面 `useTable` 风格不统一。
- 各页 `navigator.clipboard`（复制单号/表达式等约 8 处）：**非 HTTPS/localhost 环境 `navigator.clipboard` 不可用**，生产 http 部署下复制功能全部失败（虽有 catch 提示"复制失败"）。建议统一封装 `utils/clipboard.js` 用 `execCommand` 降级。

### 3.6 主题一致性：颜色硬编码残留

- `views/report/TrendAnalysis.vue:17,36`：图表颜色 `#3b82f6` / `#38bdf8` 硬编码，不随主题主色。
- `views/error/Forbidden.vue`、`NotFound.vue`：背景/卡片 fallback 值 `#0f172a` / `#131e33` / `#243148` 为深色系，若变量未注入会显示深色卡片。
- `views/report/ReportCenter.vue:259-261`：`.pass-cnt/.cond-cnt/.rej-cnt` 硬编码 `#34d399/#fbbf24/#f87171`。
- `AppTrendChart.vue` 组件默认色 `var(--loan-primary)` ✓（正面），但调用方传入硬编码色覆盖。

### 3.7 空态与加载反馈缺失

- `AuditCenter.vue`、`StrategyList.vue`、`StrategyTemplateList.vue`、`RuleTemplateList.vue`、`OrderList.vue`（有）、`RewardList.vue`、`BlacklistCenter.vue`、`SmsCenter.vue`、`ReportCenter.vue` 等多数列表页**无 `AppEmpty` 空态引导**（仅 LeadPool/OrderList 有），数据为空时白板一片。
- `ApprovalCenter.vue` 用 `v-show` 双表常驻，切换 Tab 不触发加载态。

---

## 四、🔵 建议改进

1. **权限体系未接线**：`directives/permission.js:8` 默认 `checker = () => true`（全放行）且全工程从未调用 `setPermissionChecker`；路由守卫（`router/index.js`）仅校验 token，无角色/菜单权限校验——角色权限 API 已就绪但前端未消费。建议在登录后拉取用户权限集注入 checker，并在路由守卫增加 meta.roles 校验（403 页已存在）。
2. **`utils/format.js:50` `statusText()` 与多页面本地常量 `statusText` 重名**（如 OrderList L172、RewardList L98、SmsCenter L161），误导入时静默覆盖，建议改名 `dictText`。
3. **错误页重复**：`Forbidden.vue` / `NotFound.vue` 除 code/文案/渐变外完全一致，可合并为 `ErrorPage.vue` 传参。
4. **`store/user.js:12` 与 `store/dict.js` 模块顶层读 localStorage**：无 SSR 场景可接受，但建议统一封装 `utils/storage.js` 便于测试与容错。
5. **`AppSkeleton.vue`** 无问题（含 `prefers-reduced-motion` 降级，正面示例）；`styles/index.css:1126-1132` 已全局降级动效（正面），但 `.loan-card-hover:hover` 等 transform 动效应确认是否受控。
6. **`ConfigurationWizard.vue:64`** 渠道步骤跳转 `/product`，与现有"渠道配置列表/向导"（`/plan/channel-config*`）入口割裂，建议核对路由后跳转渠道配置页。

---

## 五、修复优先级路线图

| 优先级 | 内容 | 预估工作量 |
| --- | --- | --- |
| P0（阻断） | K1 排序报错 9 处补解构；K2 401 清 token 防死循环；K3 启动崩溃容错；K4 产品库 Tab 修复 | 0.5 天 |
| P1（高） | K5 keep-alive name；K6/K7 组织权限两处；K8 上线校验；K9 planCode 竞态；K12 弹窗 rules 补齐（6 页） | 1 天 |
| P2（中） | 3.1 表格样式补丁收敛；3.2 主题浅阶色改混色；3.3 影子文案 4 处；3.4 公共组件（AppPagination 页码重置、AppTableActions maxInline/ARIA）；3.5 业务页交互（撤销误导、Clipboard 封装、城市校验、空态补齐） | 1.5 天 |
| P3（低） | 3.6 颜色硬编码替换为 CSS 变量；3.7 空态；🔵 建议项 | 0.5 天 |

**总计约 3-3.5 人日**，可显著消除当前"页面可用但交互处处露怯"的观感。

---

## 六、总结

- 全局最大风险是**排序表头点击报错**（9 处同因）与 **401 死循环**，属上线即踩的阻断级缺陷，建议本周内修复。
- 样式层"补丁叠补丁"是维护性负债：表格 3 套补丁、`display:block !important`、硬编码背景色，建议在 P2 一次性收敛并回归固定列、嵌套表格（渠道配置展开行）与双主题。
- 业务语义一致性需专项清理：影子文案、`blacklist.js` 承载组织写接口、`ConfigurationWizard` 渠道入口跳转——这些不影响运行但直接影响使用者的心智模型与后续维护。
- 表单校验缺失集中在"配置/编排"类页面（向导、模板、计划），与渠道准入的高合规要求不匹配，应优先补齐。

---

## 七、落地记录（2026-08-27）

> P0/P1/P2 已按路线图分批实施；本节记录 P3（低）+ 🔵 建议项 + 「列表查询条件补齐」的实际落地。

### 7.1 P3 · 🔵 建议项（#51）已实施

- **🔵 建议 4（storage 统一封装）**：新增 `utils/storage.js`（`getStorageJSON/setStorageJSON` + KEYS 常量 + try/catch 容错），`store/user.js` / `store/dict.js` 改用封装读写。
- **🔵 建议 1（权限体系接线）**：`store/user.js` 接入 `setPermissionChecker`；`utils/request.js` 401（code 2000）清 token 并跳登录（K2 同修）；`router/index.js` 增加权限守卫（meta.roles 校验 + 403 拦截）；`main.js` 登录后注入权限 checker。
- **🔵 建议 3（错误页合并）**：`Forbidden.vue` / `NotFound.vue` 合并为 `ErrorPage.vue`（code/文案/渐变传参），路由统一引用。
- **🔵 建议 5（动效收敛）**：`Layout.vue` 过渡统一 `transform/opacity 0.2s`，受控卡片 hover。
- **3.3 影子文案**：`Workbench.vue` / `DebugCenter.vue` 4 处"影子系统"语义清除（需求仅保留调试）。
- **3.6/3.7 样式与空态**：`theme/index.js` 浅阶色由 rgba 半透明改为混色；`DictTag` 状态图标 + 空态文案补齐（`AppEmpty` 统一）。

### 7.2 列表查询条件补齐（手机号 / 姓名等）

> 背景：多个列表页仅支持单号/编码检索，缺失业务人员高频使用的手机号、客户姓名等条件。

**后端（loan-service，编译通过 + 生产连接重启验证）**：

| 接口 | 新增能力 | 实现 |
| --- | --- | --- |
| 线索池 `lead/page` | **手机号检索** | keyword 为 11 位数字时按 `phoneHash`（SHA-256）精确匹配（phone 密文不可 LIKE），否则联系人/线索编号模糊 |
| 初筛报告 `screening/page` | **客户姓名检索** | keyword 命中 `client_profile.contact_name/enterprise_name` → 收集 client_code `IN` 追加匹配 |
| 工单 `order/page` | **客户姓名检索** | 同上，追加 `client_profile_code IN` |
| 奖励记录 `reward/page` | **客户姓名检索** | 同上，推荐人/被推荐人双 client_code `IN` |
| 名单管理 `channel-user-list/page` | **明文手机号检索** | keyword 为 11 位数字时按 MD5 键匹配（个人名单键为手机号 MD5），否则名单键 LIKE |
| 审计中心 `audit/page` | **traceUuid 模糊 + 客群/结果/异常/时间** | `like(traceUuid)` + `eq(customerGroup/totalResult/mismatchFlag)` + `ge/le(executedAt)`，按时间倒序 |

**前端（loan-web，vite build 通过）**：

- 6 处查询 placeholder 更新：`OrderList`（工单号 / 客户编码 / 客户姓名）、`LeadPool`（联系人 / 手机号 / 线索编号）、`RewardList`（奖励单号 / 工单号 / 客户编码 / 客户姓名）、`ChannelUserList`（名单键 / 手机号）、`ScreeningReport` / `ReportCenter`（报告编号 / 客户编码 / 客户姓名）。
- `AuditCenter.vue` 查询区重构：traceUuid 由"精确跳详情"改为**列表模糊过滤**；新增客群下拉、总结果下拉、异常标记下拉（有异常=1/无异常=0）、执行时间范围（datetimerange）；表格补"异常"列（不一致/正常标签）。请求前清洗空值参数，避免 Integer 空串 400；保留路由 `?trace=` 跳详情能力。

### 7.3 验证

- 后端 `mvn compile` EXIT=0；前端 `vite build` 3.18s 通过。
- 生产连接重启后接口实测：`audit/page` 基础分页 total=10；`traceUuid=0de` 模糊命中 2 条；`mismatchFlag=0` 返回全 0；`customerGroup=PERSONAL` 仅返回 PERSONAL；空 `mismatchFlag=` 不报 400（Spring 空串转 null 忽略）。
