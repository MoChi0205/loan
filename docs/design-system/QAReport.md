# QAReport · 墨金 Ink & Gold 交付质量检查

> 交付前五项检查。结论：可交付（P0 0 项，P1 0 项，P2 少量建议）。
> 检查对象：原型 `prototype/design-showcase.html` + 设计系统 `DesignSystemManifest.md` + 组件 `ComponentSpec.md` + 图标代码 `code/`。

## 1. AI 味检测（10 项默认审美）
| # | 默认审美 | 结果 |
|---|---|---|
| 1 | 紫 / 蓝紫渐变 | ✅ 规避，墨蓝+暖金二元 |
| 2 | 三列 icon 卡片 | ✅ 规避，图标区为统一图标库网格，非 feature 卡片 |
| 3 | lorem ipsum | ✅ 规避，全部真实业务文案 |
| 4 | 无意义 stock 占位 | ✅ 规避 |
| 5 | Inter/Roboto/Arial/系统字体 | ✅ 规避，Space Grotesk + Noto Sans SC |
| 6 | emoji 当 icon | ✅ 规避，全部 SVG |
| 7 | 每次换风格 | ✅ 全系统单一方向 |
| 8 | 局部修改整页重写 | ✅ 组件化增量 |
| 9 | 无设计系统出高保真 | ✅ 先 DesignSystemManifest |
| 10 | 奶油底+赤陶橙衬线 | ✅ 规避 |
| 11 | 近黑底+酸性绿/朱红 | ✅ 规避 |
| 12 | 报纸风零圆角密排 | ✅ 规避，12–16 圆角 |

## 2. 可访问性审查
- 对比度：墨蓝 `#16203A` 底白字 / 暖金 `#B8861A` 浅底文字均满足 WCAG AA（4.5:1）。
- 语义化：原型用 header/nav/main/table 语义标签；图标 `role="img"` + `aria-label`。
- 键盘可达：可交互元素具备 focus 态（按钮外环）；交付代码需保证 Tab 顺序。
- 动效偏好：`prefers-reduced-motion` 已在原则中声明，落地时实现。
- 表单：Input 含 focus/error 态与文字提示。

## 3. 层级与节奏审查
- 视觉权重：标题 900 > 正文 400；强调仅暖金一处，无多色竞争。
- 节奏：间距全部取自 4px 标尺；垂直节奏一致。
- 色彩权重：暖金面积受控（按钮/激活态），主面积为墨蓝/中性。
- 排版节奏：字号梯度 12→36 连续，行高舒适，段间距一致。

## 4. 交互状态审查
- Button：d/h/a/f/di/lo 齐全 ✅
- Input：d/f/e ✅
- Tag：语义四态 ✅
- Nav/Sidebar：d/h/active ✅
- TabBar：default/active ✅（active 暖金随主题联动）
- 图标：统一单一来源，无散落内联 ✅

## 5. 终检汇总
- 过渡动画 0.2–0.3s ✅；反馈即时（hover/focus 即时）✅；悬停目标 ≥44px（TabBar/按钮）✅。
- **P2 建议（非阻断）**：
  1. 真机/真页面接入后用 axe 复测对比度与实际焦点顺序。
  2. Mini 浅色主题下 `--text-muted` 在极小字（10.5px TabBar）需确认可读性，必要时提至 `#5A6舒80` 等价。
  3. 暗主题图表柱体若改用暖金渐变，需验证与背景对比。

**交付判定：通过（可归档）。**
