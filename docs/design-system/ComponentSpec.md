# ComponentSpec · 墨金 Ink & Gold

> 可复用组件清单与状态规范。所有组件遵循 `DesignSystemManifest.md` 的令牌与图标规范。状态缩写：default(d) / hover(h) / active(a) / focus(f) / disabled(di) / loading(lo) / error(e)。

## 1. Button 按钮
- 变体：`primary`（暖金填充，墨蓝文字）/ `ghost`（透明 + 墨蓝描边）/ `text`（纯文字链接）。
- 尺寸：sm(28h) / md(36h) / lg(44h)。圆角 `--r-sm`。
- 状态：
  - primary：d `#D9A441` → h 提亮 `#E0AE4E` → a 压暗 `#B8861A` → f 外环 `0 0 0 3px rgba(217,164,65,.35)` → di 降透明度 40% 禁点击 → lo 前置 spinner。
  - ghost：d 描边 `--border`，文字 `--ink-700` → h 底 `--surface-2`。

## 2. Card 卡片
- d：底 `--surface`(亮)/`--ink-800`(暗)，描边 `--border`，圆角 `--r-md`，阴影 `--sh-sm`。
- h（可点击卡）：描边变 `--gold-300`，阴影 `--sh-md`，`translateY(-3px)`。
- 内部间距统一 `--sp-4`~`--sp-6`。

## 3. Table 表格
- 行高 44–52；表头 `--text-muted` 12px；分隔线 `--border`。
- 状态列用 `Tag` 呈现，状态与颜色双编码（色 + 图标/文字）。
- 行 h：底 `--surface-2`；a：底暖金 8% 透明。

## 4. Input / Search 输入
- d：底 `--surface`，描边 `--border`，圆角 `--r-sm`；f：描边 `--gold-500` + 外环。
- 错误 e：描边 `--danger` + 下方 `--danger` 提示文字。
- 搜索框前置 `AppIcon name="search"`，右置清除 `close`。

## 5. Tag / Pill 状态标签
- 语义映射：success(`--success` 绿底绿字) / warning(`--gold` 金底金字) / danger(`--danger` 红底红字) / info(`--info` 蓝底蓝字) / neutral(灰底灰字)。
- 圆角 `--r-pill`，内联小图标（如 check / warning）与文字并存，状态不只靠颜色。

## 6. Nav / Sidebar 侧栏（Web）
- 项高 40，图标 `AppIcon` 18px（墨蓝-次），文字 `--text-muted`。
- active：底暖金 14% 透明，文字/图标 `--gold-300`(暗) 或 `--gold-600`(亮)，左侧 3px 暖金指示条。
- h：底 `--ink-700`(暗)/`--surface-2`(亮)。

## 7. TopBar 顶栏（Web）
- 含页面标题、搜索、通知 `bell`、用户 `user` 头像入口；底描边 `--border`。

## 8. TabBar 标签栏（Mini）
- 5 主 Tab：home / match / report / order / user；图标 `AppIcon` 22px。
- active：图标与文字 `--gold-500`，**颜色由主题对象注入真实值**（不再写死 `#2443C2`），见 DeveloperGuide。

## 9. Empty / 空态
- 居中 `AppIcon`（如 `document`/`search`）+ 文案 + 可选操作按钮。

## 10. Dialog / Toast
- Dialog：遮罩 `rgba(14,22,38,.5)`，卡片 `--surface` 圆角 `--r-lg` 阴影 `--sh-lg`；Toast 底部 2.5s 自动消失，暖金勾 `check` 表示成功。

## 11. Icon 图标原子
- 唯一入口 `AppIcon`（Web: `code/AppIcon.web.vue`；Mini: `code/AppIcon.mini.vue`）。
- 禁止：内联 `<svg>` 散落、Element Plus 内置图标、`emoji`。
- 36 枚见 `icons.js`；新增须同几何规范并入 `ICON_PATHS`。
