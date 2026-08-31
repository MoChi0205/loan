---
name: mini-program-UI
description: >-
  loan-platform 小程序（loan-mini）UI 与交互规范。新增/修改小程序页面布局、设计令牌、组件复用、
  图标、空状态、骨架屏、列表分页时使用；违反将出现 emoji 渲染不一致 / 各页样式各写各的 /
  空态插画不一致 / 图标豆腐块（static/ 无字体文件）等问题（2026-08-28 汇总）。
---

# 小程序 UI 与交互规范（loan-mini）

## 何时使用

- 新增 / 修改小程序页面（`loan-mini/pages/**`）
- 用视觉元素（图标 / 空态 / 卡片 / 按钮 / 标签）前必读
- 写列表页 / 详情页 / 表单页前必读
- **每次代码改动前先读本文 + `frontend-ui`（管理端）两份规范**，两侧风格独立不互相套用

## 一、设计令牌（瑞幸风，全局唯一真源在 App.vue）

| 令牌 | 值 | 用途 |
|---|---|---|
| `--color-primary` | `#0B1D3A` 深海军蓝 | 主按钮 / 顶栏 / 强调 |
| `--color-primary-light` | `#1A3A6E` | 渐变次色 / 选中态 |
| `--color-accent` | `#C8A96E` 暖金 | 点缀 / 金额 / 等级金标 |
| `--color-bg` | `#F5F6F8` | 页面底 |
| `--color-card` | `#FFFFFF` | 卡片底 |
| `--color-text` / `-secondary` / `-hint` | `#1A1A2E` / `#6B7280` / `#9CA3AF` | 三级文字 |
| `--radius-card / -btn / -input` | `24rpx / 20rpx / 16rpx` | 圆角体系 |
| `--shadow-card` | `0 4rpx 20rpx rgba(0,0,0,.05)` | 卡片阴影 |

**规则**：
- 新样式**必须引用 CSS 变量**，禁止裸色值（与 Web 端 `frontend-ui` 同规则）
- 字号体系：标题 34rpx / 正文 28rpx / 次要 25rpx / 提示 24rpx
- 单位统一 **rpx**（750 设计稿），禁止混用 px

## 二、公共组件（components/，easycom 自动注册）

`pages.json` 已配 `easycom.custom: {"^App(.*)": "@/components/App$1.vue"}`，页面**无需 import 直接用**：

| 组件 | 用途 | 关键 props |
|---|---|---|
| `AppCard` | 卡片容器（圆角 24rpx + 阴影） | `padding` / `radius` |
| `AppButton` | 按钮 | `type`(primary/ghost/text/gold) / `size`(sm/md/lg) / `block` / `loading` / `disabled` |
| `AppTag` | 标签 | `tone`(success/warning/danger/info/gold) |
| `AppTopBar` | 页面顶栏 | `title` / `showBack` |
| `AppEmpty` | 空状态插画（CSS 绘制文件卡+放大镜） | `title` / `desc` / slot 放操作按钮 |
| `AppSkeleton` | 列表骨架屏 | `rows` |
| `AppIcon` | 单色图标（view/CSS 绘制，零依赖） | `name`(match/chart/bolt/lock/list/wechat/arrow) / `size`(sm/md/lg) |

**规则**：
- 新增组件命名 **App 前缀**（自动注册），放 `loan-mini/components/`
- 卡片 / 按钮 / 空态 / 骨架屏 / 图标**一律用组件**，禁止页面内复制样式

## 三、图标规范

- **统一用 `<AppIcon name="..." />`**（view/CSS 绘制，跨端渲染一致）
- **禁止 emoji 图标**（🎯📊⚡🔒 等在不同平台渲染不一致）
- **禁止 iconfont 字符**（`&#xe900;` 等）—— `loan-mini/static/` 无字体文件，微信端渲染为豆腐块（已踩坑修复）
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

## 六、交互与可访问性

- 可点元素（卡片 / 按钮）用 `hover-class` 提供按压反馈
- 重要操作（提交 / 支付）**必须** `:loading` 防重复提交
- 删除 / 不可逆操作需二次确认（`uni.showModal`）
- 文字对比度：正文对底 ≥ 4.5:1，禁用浅灰 `#C0C4CC` 做正文

## 七、风格红线

- 小程序是**客户端风格**（瑞幸风深蓝+暖金），Web 管理端是**企业风格**（蓝 `#3b82f6`）——**两侧品牌色故意不统一**（用户确认），互不套用
- 不引入 AI 模板感：避免纯白卡片堆叠、霓虹渐变、过多 emoji
- 页面结构统一：`page-head`(标题+副标题) → 内容卡 → 底部安全区
