# 设计系统规范

## 设计原则

- 金融场景以高可读、可信和信息层级清晰为先。
- Web 与 H5 / 小程序都支持明暗主题；主题变量的实际值以源码为准。
- 主操作、状态提示和分类数据使用不同语义色，但同一语义不得在页面间跳色。
- 毛玻璃只用于导航、浮层和切换过渡，不降低正文对比度，也不能造成底层内容叠影。

## 现行令牌

| 类型 | Web | H5 / 小程序 |
|---|---|---|
| 背景与表面 | `--loan-bg`、`--loan-card-bg`、`--loan-surface` | `--bg-page`、`--bg-card`、`--bg-input` |
| 主色与强调 | `--loan-primary`、`--loan-accent`、`--loan-gold` | `--brand-deep`、`--brand-mid`、`--gold` |
| 文本 | `--loan-text`、`--loan-text-secondary`、`--loan-text-muted` | `--text-primary`、`--text-body`、`--text-secondary` |
| 状态 | `--loan-success/warning/danger/info` | `--success/warning/danger/info` |
| 边框与阴影 | `--loan-border`、`--loan-shadow-*` | `--line`、`--shadow-*` |

Web 变量由 `loan-web/src/theme/index.js` 注入，默认深色；客户端变量位于 `loan-mini/App.vue`，默认浅色。修改配色时必须同时检查两端的明暗映射和文字对比度。

## 排版与布局

- 间距采用 4px 基数；页面、卡片、字段、行内元素使用语义间距变量。
- Web 桌面端侧栏与主区分层；平板允许侧栏收起；窄屏使用抽屉。
- H5 / 小程序以 375 逻辑像素为设计基准，宽屏内容限制在 600px 内居中。
- 所有浮层必须有独立、不透明度足够的表面背景与遮罩，深色模式下不得透出底层表格文字。

## 动效与可访问性

- 常规过渡 150–300ms，只动画 `transform`、`opacity` 和颜色等低成本属性。
- 折叠和主题切换保持当前上下文与滚动位置，避免闪白和布局跳动。
- 尊重 `prefers-reduced-motion`。
- 文本、状态和操作不能只靠颜色区分；焦点态、禁用态、加载态必须可辨识。

## 图标

- 统一使用各端 `AppIcon` 组件；24×24 视图盒、圆角端点和一致描边。
- Web 图标使用 `currentColor` 或主题变量；小程序在运行时注入真实颜色值。
- 新图标加入对应端图标注册表，不在业务页面散落 SVG 或 emoji。
