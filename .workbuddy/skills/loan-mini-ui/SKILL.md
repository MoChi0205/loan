---
name: loan-mini-ui
description: >-
  loan-main 小程序（loan-mini）UI 与交互规范。新增或修改 uni-app 小程序页面布局、
  设计令牌（瑞幸风深蓝+暖金）、公共组件复用、AppIcon 图标、空状态、骨架屏、列表分页、
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
- 管理端页面请看 `loan-web-ui` —— **两侧风格独立，互不套用**

## 一、设计令牌（瑞幸风，全局唯一真源在 App.vue）

| 令牌 | 值 | 用途 |
|---|---|---|
| `--color-primary` | `#0B1D3A` 深海军蓝 | 主按钮 / 顶栏 / 强调 |
| `--color-primary-light` | `#1A3A6E` | 渐变次色 / 选中态 |
| `--color-accent` | `#C8A96E` 暖金 | 点缀 / 金额 / 等级金标 |
| `--color-bg` | `#F5F6F8` | 页面底 |
| `--color-card` | `#FFFFFF` | 卡片底 |
| `--color-text` / `-secondary` / `-hint` | `#1A1A2E` / `#6B7280` / `#9CA3AF` | 三级文字 |
| `--radius-card` / `-btn` / `-input` | `24rpx` / `20rpx` / `16rpx` | 圆角体系 |
| `--shadow-card` | `0 4rpx 20rpx rgba(0,0,0,.05)` | 卡片阴影 |

**规则**：
- 新样式**必须引用 CSS 变量**，禁止裸色值（与 Web 端 `loan-web-ui` 同规则）
- 字号体系：标题 34rpx / 正文 28rpx / 次要 25rpx / 提示 24rpx
- 单位统一 **rpx**（750 设计稿），**禁止混用 px**

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
| `AppIcon` | 单色图标（view/CSS 绘制，零依赖） | `name`(match/chart/bolt/lock/list/wechat/arrow) / `size`(sm/md/lg) |

**规则**：
- 新增组件命名 **App 前缀**（自动注册），放 `loan-mini/components/`
- 卡片 / 按钮 / 空态 / 骨架屏 / 图标**一律用组件**，**禁止页面内复制样式**

## 三、图标规范

- **统一用 `<AppIcon name="..." />`**（view/CSS 绘制，跨端渲染一致）
- **禁止 emoji 图标**（🎯📊⚡🔒 等在不同平台渲染不一致）
- **禁止 iconfont 字符**（`&#xe900;` 等）—— `loan-mini/static/` **无字体文件**，微信端渲染为豆腐块（已踩坑修复）
- 单纯色符号字符（`✓` `›` `!`）可保留，各平台渲染一致

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

## 七、交互与可访问性

- 可点元素（卡片 / 按钮）用 `hover-class` 提供按压反馈
- 重要操作（提交 / 支付）**必须** `:loading` 防重复提交
- 删除 / 不可逆操作需二次确认（`uni.showModal`）
- 文字对比度：正文对底 ≥ 4.5:1，禁用浅灰 `#C0C4CC` 做正文
- H5 端 hover 态（C18）：H5 与小程序 hover 表现需分别处理

## 八、风格红线

- 小程序是**客户端风格**（瑞幸风深蓝 + 暖金），Web 管理端是**企业风格**（蓝 `#3b82f6`）
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
- [ ] 是否与 Web 管理端风格混用（品牌色 / 组件）？
- [ ] 是否跑过 `npm run build:mp-weixin`（注意 manifest 在根目录，必须用 `npm run` 而非 `npx uni build`）？

## 相关文档

- `docs/knowledge-base/10-历史结论与决策日志.md#结论台账`（**Step 0 必查**）
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
