---
name: loan-mini-ui
description: >-
  loan-main 小程序（loan-mini）UI 与交互规范。新增或修改 uni-app 小程序页面布局、
  设计令牌（冷玻璃风格：冷调低饱和蓝 + 磨砂油画/毛玻璃质感）、公共组件复用、AppIcon 图标、空状态、骨架屏、列表分页、
  自绘 TabBar 时使用；违反将出现 emoji 渲染不一致 / 各页样式各写各的 / 空态插画不一致 /
  iconfont 豆腐块（static/ 无字体文件）/ 原生 TabBar 与角色化冲突等问题（2026-08-28 汇总）。
---

# 小程序 UI 与交互规范（loan-mini-ui）

## Step 0 · 前置门禁（强制，不可跳过）

1. **先查历史结论**：`grep -n "小程序\|mini\|TabBar\|图标\|令牌" docs/knowledge-base/10-历史结论与决策日志.md#结论台账`，
   从**最新条目往下**读（台账按时间倒序，最新在最上方）；**命中即遵守**（尤其 **C17 自绘 TabBar**、**C18 H5 hover 态**）；
   状态为「已被 Dxx 替代」则跳读 Dxx。
   > ⚠️ 取号前必须自己刚跑 `grep -o "^| D[0-9-]*" docs/knowledge-base/10-历史结论与决策日志.md` 取实时最大编号（禁采信转述 / 记忆 / 分配表）。
2. **无结论且不确定 → 停下来问用户**，禁止臆断。
   > ⚠️ **分工红线（D0-1）**：小程序由**用户自己**负责，助手默认只做后端 `loan-service`。
3. **再读元技能** `loan-knowledge`（`.workbuddy/skills/loan-knowledge/SKILL.md`），按其 Step 1–5 执行。
4. 回复开头输出：`【结论核对】命中 Dx-x / Cx（…）/ 未命中（grep 关键词：…）`。

## 何时使用

- 新增 / 修改小程序页面（`loan-mini/pages/**`）
- 用视觉元素（图标 / 空态 / 卡片 / 按钮 / 标签 / TabBar）前必读
- 写列表页 / 详情页 / 表单页前必读
- **每次代码改动前先读本规范 + `docs/knowledge-base/05-前端工程要点.md` 的 TabBar / AppIcon / Token 章节**
- 页面分层复用、接口数据与名称/编码展示同时必读 `loan-code-standard` 的 `references/frontend-standard.md`
- 管理端页面请看 `loan-web-ui` —— **两侧风格独立，互不套用**

## 一、设计令牌（瑞幸风，全局唯一真源在 App.vue）

| 令牌 | 值 | 用途 |
|---|---|---|
| `--brand-deep` | `#2443C2` | 唯一品牌强调色（选中态 / 按钮 / 图标字形） |
| `--brand-mid` / `--brand-bright` | `#3D63E0` / `#5B7CFF` | 渐变次色 / 小面积点缀 |
| `--gold` | `#FFB020` | 仅语义点缀（金额 / 等级金标），**不用于页面主色** |
| `--glass-bg` | `#E8ECF5` | header 玻璃主底 |
| `--glass-bg-deep` | `#D6DDEB` | 默认头像底 / 暗部 |
| `--glass-hi` | `rgba(255,255,255,0.45)` | 高光斑 |
| `--glass-edge` | `rgba(255,255,255,0.6)` | 细白边 |
| `--glass-tint` | `#EAF0FF` | 功能图标浅底（统一品牌蓝字形容器） |
| `--glass-gradient` | 多层 radial+linear 渐变 | header 油画光斑底 |
| `--hero-gradient` | `linear-gradient(160deg,#17216B,#2443C2 40%,#2C5BFF)` | Hero 通栏 / 主按钮同源渐变 |
| `--btn-primary-bg` | `linear-gradient(135deg,#2443C2,#2C5BFF 55%,#3D63E0)` | **主按钮唯一底色**（与 hero 同源） |
| `--btn-primary-shadow` | `0 12rpx 28rpx rgba(36,67,194,.28)` | 主按钮投影 |
| `--btn-gold-bg` / `--btn-gold-shadow` | 暖金渐变 / 投影 | 强调 CTA（配 `--gold-text` 深棕字保证对比度） |
| `--btn-danger-bg` / `--btn-danger-shadow` | 红渐变 / 投影 | 危险操作 |
| `--btn-glass-bg` / `--btn-glass-border` | `rgba(255,255,255,.72)` / `rgba(36,67,194,.28)` | 玻璃次按钮 / 状态胶囊 |
| `--btn-disabled-bg` / `--btn-disabled-text` | `#DDE3EC` / `#9AA4B4` | 禁用态（**不用 opacity 压渐变**） |
| `--bg-page` / `--bg-card` | `#F8FAFC` / `#FFFFFF` | 页面底 / 卡片底 |
| `--text-primary` / `-secondary` / `-placeholder` | `#1A2336` / `#5B6678` / `#B6C0CE` | 三级文字 |
| `--radius-md` / `-lg` / `-full` | `24rpx` / `32rpx` / `999rpx` | 圆角体系 |
| `--shadow-md` | `0 8rpx 24rpx rgba(15,23,42,0.06)` | 卡片阴影 |

**规则**：
- 完整令牌见 `App.vue` 的 `page` 块与 `docs/knowledge-base/小程序首页设计规范.md`（冷玻璃风格唯一真源）。
- 新样式**必须引用 CSS 变量**，禁止裸色值（与 Web 端 `loan-web-ui` 同规则）。
- 字号体系：标题 30rpx / 正文 26rpx / 次要 22~24rpx / 提示 22rpx。
- 单位统一 **rpx**（750 设计稿），**禁止混用 px**。

## 二、公共组件（components/，easycom 自动注册）

`pages.json` 已配 `easycom.custom: {"^App(.*)": "@/components/App$1.vue"}`，页面**无需 import 直接用**：

| 组件 | 用途 | 关键 props |
|---|---|---|
| `AppCard` | 卡片容器（圆角 24rpx + 阴影） | `padding` / `radius` |
| `AppButton` | 按钮 | `type`(primary/ghost/text/gold) / `size`(sm/md/lg) / `block` / `loading` / `disabled` |
| `AppTag` | 标签 | `tone`(success/warning/danger/info/gold) |
| `AppTopBar` | 页面顶栏 | `title` / `showBack` |
| `AppEmpty` | 空状态插画（CSS 绘制文件卡 + 放大镜） | `title` / `desc` / slot 放操作按钮 |
| `AppSkeleton` | 列表骨架屏 | `rows` |
| `AppIcon` | 图标（PNG 资源版 v5 冷玻璃线性；磁贴/单色两态） | `name`(home/match/chart/order/bank/users/mine/check/search/wechat/bolt/support/list/share/enterprise/person/refresh/alert/trend/doc/file/photo/lock/arrow，共 24 枚) / `size`(sm32/md48/lg64/**xl88** rpx) / `color`(单色态传设计令牌；传了走 `-gray/-brand/-gold/-success/-info/-white` 单色资源，不传走磁贴) |

**规则**：
- 新增组件命名 **App 前缀**（自动注册），放 `loan-mini/components/`
- 卡片 / 按钮 / 空态 / 骨架屏 / 图标**一律用组件**，**禁止页面内复制样式**

## 三、图标规范（v5 冷玻璃·线性）

- **统一用 `<AppIcon name="..." />`**（PNG 资源渲染，跨端一致；`static/icons/` 下 144×144 高清图）
- **字形设计语言**：手写线性 SVG 字形，24×24 网格、stroke 1.75、round cap/join、安全边距 3u；
  语义细节齐全（doc 折角+行、order 单据+勾、bank 柱廊、enterprise 双楼+窗）。
  **禁止退回实心色块字形**。
- **两态**：
  - **磁贴态（不传 `color`）**：PNG 自带「圆形冷玻璃底（#F3F6FD→#DDE4F4 渐变 + 顶部高光 +
    细白边 + 品牌柔投影）+ 品牌窄幅渐变字形 `#2443C2→#3D63E0`」。
    用于首页数据卡 / 快捷功能 / 认证引导卡 / 菜单行等**内容功能入口**。
    ⚠️ **容器禁止再叠浅底方块**（会形成双层底）—— `.stat-icon-wrap` / `.nav-icon-wrap` /
    `.menu-icon-wrap` / `.advisor-empty-icon` 等一律 `background:transparent`。
  - **单色态（传 `color`）**：同一字形纯色渲染无底，用于 TabBar、搜索栏、深底按钮（`-white`）。
- **禁止按功能/角色跳色**：磁贴态字形统一品牌渐变；单色态只用 `brand / gray / gold / success / info / white` 六档。
- **图标资源由脚本生成**：`loan-mini/scripts/gen-icons-v5.mjs`（resvg-js 栅格化，运行命令见脚本头注释；
  resvg-js 装在 WorkBuddy 托管 node workspace：`/Users/admin/.workbuddy/binaries/node/workspace/node_modules`）。
  字形 SVG path 真源内嵌在脚本 `GLYPHS` 中，改图标形状/配色**改脚本后重跑**，勿手改 PNG。
  脚本会**全量重建**：24 磁贴 + 全量 gray/brand 单色 + 按需语义色（PLAIN_EXTRA），保证任意
  `<AppIcon color="X">` 组合不裂图（v4 曾因资源不全导致 TabBar 选中态裂图，已修复，勿回退）。
  新增 color 组合：先在 `PLAIN_EXTRA` 登记再重跑。
- **禁止 emoji 图标**（🎯📊⚡🔒 等在不同平台渲染不一致）
- **禁止 iconfont 字符**（`&#xe900;` 等）—— `loan-mini/static/` **无字体文件**，微信端渲染为豆腐块（已踩坑修复）
- **禁止内联 SVG 标签**（微信 WXML 不支持，v2 已踩坑）
- 单纯色符号字符（`✓` `›` `!`）可保留，各平台渲染一致

## 三·补、按钮规范

- **所有按钮走 `AppButton`**，variant：`primary`（油画渐变+白字+投影）/ `gold`（暖金渐变+深棕字）/ `danger` /
  `secondary`（玻璃半透白底+品牌细边）/ `ghost`（透明+实心边）/ `text`。
- **主按钮必须与登录页 hero 同源渐变**（`--btn-primary-bg`），禁止裸 `--brand-deep` 平色。
- **禁用/加载态用 `--btn-disabled-bg` 灰底**，禁止仅靠 `opacity` 压渐变（会发灰发脏）。
- 类按钮控件（首页「完成认证」胶囊等）**同样引用 `--btn-*` 令牌**：
  行动类 → `--btn-primary-bg`；状态类（已认证）→ 柔和语义色 + `--btn-glass-border` 弱化，不抢主视觉。

## 四、空状态与骨架屏规范

**空态**（数据为空时）：

```html
<AppEmpty v-if="!loading && !list.length" title="暂无报告" desc="完成匹配后在此查看">
  <AppButton type="primary" size="md" @click="goMatch">去匹配</AppButton>
</AppEmpty>
```

- 页面级"无数据"**必须**用 `AppEmpty`，禁止手写插画 / 字符图标
- 操作按钮放 slot，用 `AppButton`，禁止原生 `<button class="empty-btn">`
- 行内小提示（如"暂无跟进备注"）不属页面空态，保留普通 text

**骨架屏**（首次加载时）：

```html
<AppSkeleton v-if="loading && !list.length" :rows="4" />
<view v-else>...真实列表...</view>
```

- 列表页**必须**配骨架屏，禁止空白等待
- 空态与骨架屏互斥：`v-if="!loading && !list.length"`

## 五、列表页规范

- 分页：`onReachBottom` 触发加载更多，`finished` 后显示"已加载全部"
- 底部统一三态：`加载中… / 已加载全部 / 点击加载更多`
- 空态、加载中、有数据三态互斥，用 `loading` / `finished` / 数据长度推导

## 六、TabBar：必须自绘，禁用原生（C17）

- **禁用原生 TabBar**（`pages.json` 的 `tabBar` 配置），改用**自绘 `components/TabBar.vue`**
- 原因：7 业务角色 tabBar **结构不同**（客户 5 tab / 渠道 4 tab / 员工侧不同），原生 tabBar 无法角色化
- **tabBar 结构以代码 `components/TabBar.vue` 为准**，文档（如 `01-角色权限模型.md`）与代码冲突时**以代码为准并回写文档**
- 角色 tabBar 边界详见对应 `role-<角色>` 技能（T02 产出）

### fixed 元素限宽居中铁律（D64，用户 2026-09-07 报告叠影后固化）

- **禁止 `left:50% + translateX(-50%)` 给 fixed 元素做"限宽居中"**：
  - 若 `right` 保持 `0` → left/right over-constrained + max-width，宽度计算错乱（实测 381px）；
  - 若 `right:auto` 且无显式 `width` → fixed 元素 **shrink-to-fit 收缩成内容宽**，5 个 tab 挤压、
    标签换行把 TabBar 撑高，盖住页面底部内容形成「叠影」。
- **唯一正确写法**：`left:0; right:0; max-width:600px; margin:0 auto;`（定宽居中，不依赖 transform）。
  涉及两处：`TabBar.vue` scoped 的 H5 `@media (min-width:768px)` 与 `App.vue` 全局 `.tab-bar.is-tablet`（JS 驱动、!important，双端通用），**两处必须保持同一写法**。
- `.tab-label` 必须 `white-space: nowrap`，防止窄项下 4 字标签折行。
- uni-app H5 宽屏 rpx 按 `rpxCalcMaxDeviceWidth`(960) 放大 1.28 倍：TabBar 实高约 123px，
  tab 页根容器 `padding-bottom` 预留（≥128rpx）不可删，删了滚动到底内容必被盖。

## 七、交互与可访问性

- 可点元素（卡片 / 按钮）用 `hover-class` 提供按压反馈
- 重要操作（提交 / 支付）**必须** `:loading` 防重复提交
- 删除 / 不可逆操作需二次确认（`uni.showModal`）
- 文字对比度：正文对底 ≥ 4.5:1，禁用浅灰 `#C0C4CC` 做正文
- H5 端 hover 态（C18）：H5 与小程序 hover 表现需分别处理

## 八、风格红线

- 小程序是**客户端风格（冷玻璃体系：冷调低饱和蓝 + 磨砂油画/毛玻璃质感，唯一品牌色 `--brand-deep` #2443C2）**，Web 管理端是**企业风格**
  —— **两侧品牌色故意不统一**（用户确认），**互不套用**
- 不引入 AI 模板感：避免纯白卡片堆叠、霓虹渐变、过多 emoji
- 页面结构统一：`page-head`（标题 + 副标题）→ 内容卡 → 底部安全区

## 契约红线速查

- **契约真源**：`db/loan-db-schema.sql`（表数以该文件为准）→ `loan-service` 代码 → `docs/knowledge-base/`
- **禁止引用** `前端交互逻辑蓝图.html` / `output/` 等已失效路径（见 `loan-knowledge`）
- 小程序接口全部走网关，请求头 `X-Client-Type: MINI_APP`（见 `loan-gateway-auth`）

## 自检清单

- [ ] Step 0 结论核对是否已输出？是否命中 C17 / C18？
- [ ] 新样式是否全部引用 CSS 变量（无裸色值）？单位是否统一 rpx？
- [ ] 卡片 / 按钮 / 空态 / 骨架屏 / 图标是否全部用 App 组件？有无页面内复制样式？
- [ ] 是否用了 emoji 或 iconfont 字符做图标？→ 改 `AppIcon`
- [ ] 列表页是否配了 `AppSkeleton` + `AppEmpty` 且两者互斥？
- [ ] TabBar 是否自绘（`components/TabBar.vue`）？有无回退到原生 tabBar？
- [ ] 重要操作是否 `:loading` 防重复？不可逆操作是否 `uni.showModal` 二次确认？
- [ ] 人员、企业、产品、部门等是否名称主显？物理 ID / 业务编码 / 审批号 / 报告号 / 线索号 / 工单号是否仅作请求参数和行键、未出现在可见文案与 aria-label？
- [ ] 审批是否按“提交人 + 事项 + 状态/意见 + 时间”展示？报告是否按“【客户】【年月日】”展示？名称缺失是否由后端批量补齐而非回退显示编码？
- [ ] 是否与 Web 管理端风格混用（品牌色 / 组件）？
- [ ] 是否跑过 `npm run build:mp-weixin`（注意 manifest 在根目录，必须用 `npm run` 而非 `npx uni build`）？

## 相关文档

- `docs/knowledge-base/10-历史结论与决策日志.md#结论台账`（**Step 0 必查**）
- `.workbuddy/skills/loan-code-standard/references/frontend-standard.md`（跨端复用、契约与展示唯一标准）
- `docs/knowledge-base/小程序首页设计规范.md`（首页冷玻璃风格唯一真源）
- `.workbuddy/skills/loan-mini-avatar/SKILL.md`（微信头像昵称填写能力、默认头像兜底、上传后端）
- `docs/knowledge-base/05-前端工程要点.md#Token 体系（设计系统 v1.0，全在 App.vue 注入）`
- `docs/knowledge-base/05-前端工程要点.md#AppIcon（已扩 16 个）`
- `docs/knowledge-base/05-前端工程要点.md#自绘 TabBar（C17，禁用原生）`
- `docs/knowledge-base/05-前端工程要点.md#H5 hover 态（C18）`
- `docs/knowledge-base/05-前端工程要点.md#easycom（autoscan=false）`
- `docs/knowledge-base/05-前端工程要点.md#无障碍（WCAG）`
- `docs/knowledge-base/05-前端工程要点.md#响应式基线（T3 · #1 320px 小屏 / 平板走查）`
- `docs/knowledge-base/05-前端工程要点.md#store/user.js 关键状态`
- `docs/knowledge-base/01-角色权限模型.md#角色化导航（C17）`
- `docs/knowledge-base/06-业务结论沉淀索引（C1-C19）.md#结论速查`
- `docs/knowledge-base/08-小程序角色功能矩阵.md#结论`
