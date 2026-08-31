# loan-web / loan-mini 界面设计规范 v2（浅色专业商务风）

> 整改目标：将原「深色科技感 + 玻璃拟态」重构为**浅色专业商务风**，适配金融信贷业务后台的专业、可信、低干扰办公场景，并补齐交互、响应式与无障碍。

---

## 一、设计语言

| 关键词 | 说明 |
|--------|------|
| 专业可信 | 白卡 + 浅灰底 + 品牌蓝，克制用色，无装饰性特效 |
| 低干扰 | 移除扫描线 / 光斑漂移 / 霓虹辉光等动效，仅保留 transform/opacity 微交互 |
| 可读 | 纯色文本，稳定对比度，长时间办公不疲劳 |
| 可用 | 移动优先、键盘可达、状态不止靠颜色区分 |

---

## 二、色彩令牌（Design Tokens）

### 品牌与表面

| 变量 | 值 | 用途 |
|------|-----|------|
| `--loan-primary` | `#2563eb` | 品牌主色（按钮/激活态/链接） |
| `--loan-primary-hover` | `#1d4ed8` | 主色悬停 |
| `--loan-primary-active` | `#1e40af` | 主色按下 |
| `--loan-primary-soft` | `rgba(37,99,235,.08)` | 浅蓝底（选中/图标底） |
| `--loan-accent` | `#0ea5e9` | 次要强调 / 信息 |
| `--loan-bg` | `#f5f7fa` | 页面背景 |
| `--loan-card-bg` | `#ffffff` | 卡片背景 |
| `--loan-surface` | `#f9fafb` | 次级表面（表头/空态底） |
| `--loan-border` | `#e5e7eb` | 边框 |
| `--loan-border-strong` | `#d1d5db` | 强边框 |

### 文本与语义色

| 变量 | 值 | 用途 |
|------|-----|------|
| `--loan-text` | `#1f2937` | 主文本（对比度 ≥ 12:1） |
| `--loan-text-secondary` | `#4b5563` | 次要文本 |
| `--loan-text-muted` | `#6b7280` | 弱文本（对比度 ≥ 4.5:1） |
| `--loan-success` | `#16a34a` | 成功 / 可进件 |
| `--loan-warning` | `#d97706` | 警告 / 需补料 |
| `--loan-danger` | `#dc2626` | 错误 / 拒绝 |
| `--loan-info` | `#0ea5e9` | 信息 |

### 圆角 / 阴影 / 动效

- 圆角：`sm 6px` / 默认 `8px` / `lg 12px`
- 阴影：`sm`（卡片）/ `lg`（悬浮），均低透明度柔和阴影
- 动效：`0.2s cubic-bezier(0.4,0,0.2,1)`，仅动画 `transform`/`opacity`/`box-shadow`

---

## 三、布局与信息架构

1. **侧栏**：白底 220px，**可折叠**（收起为 64px 图标条）；激活项 = 浅蓝底 + 蓝字 + 左侧 3px 指示条。
2. **顶栏**：折叠按钮 + 面包屑（`企业贷款咨询 / 当前模块`）+ 用户下拉菜单（**退出登录**）。
3. **内容区**：浅灰底 + 白卡片，标题区统一 `loan-page-header` 结构。
4. **断点**：`768px` 以下侧栏自动收窄为图标条；指标卡/快捷入口栅格 `4→2→1` 列降级。

---

## 四、交互与功能补齐

| 页面 | 补齐项 |
|------|--------|
| 登录 | 表单校验、记住账号、回车提交、账号回显 |
| 布局 | 侧栏折叠、面包屑、退出登录 |
| 产品库 | 新增按钮、关键词搜索、分页、查看/编辑操作列 |
| 规则目录 | 分类筛选 + 关键词搜索、重置 |
| 调试中心 | 结果区空态（el-empty）、结果树结构优化 |
| 审计中心 | 由占位改为 traceUuid 时间线展示 |

---

## 五、无障碍（WCAG 2.2 AA）

- **对比度**：正文 ≥ 4.5:1，弱文本 `#6b7280` 满足；标题/数字 ≥ 3:1。
- **状态双重编码**：`DictTag` 增加图标（✓ / ! / × / i / +），色盲用户可凭形状识别，不止靠颜色。
- **焦点可见**：全局 `:focus-visible` 统一 2px 品牌蓝描边；快捷入口补 `focus-visible`。
- **语义化**：导航 `nav[aria-label]`、折叠按钮 `aria-label`、装饰图标 `aria-hidden`。

---

## 六、登录页设计（左右分栏 + 可配置商务风景图）

**布局**：左右分栏，左 60% 品牌区 + 右 40% 白卡登录。

**左侧（品牌区）**：
- 可配置**商务风景图**作为底层（金融区天际线 / 玻璃写字楼 / 商务建筑黄昏剪影）
- 蓝色蒙层 `linear-gradient(155deg, rgba(37,83,212,0.75) → rgba(60,130,230,0.55))` 压在图上，保证白色文字可读
- 品牌头 + 34px 大标题 + 描述段 + 3 条 ✓ 特性 + 合规声明
- 标题加 `text-shadow` 增强在风景图上的对比度

**右侧（白卡登录）**：管理员登录 + 表单 + 演示账号 chip

### 风景图配置（`.env`）

| `VITE_LOGIN_SCENERY` | 效果 |
|----------------------|------|
| `skyline`（默认） | 金融区天际线 |
| `office` | 玻璃写字楼仰视 |
| `twilight` | 商务建筑黄昏剪影 |
| `none` | 纯色渐变（无图） |
| `custom` | 自定义（需配 `VITE_LOGIN_BG_URL`） |

### 新增风景图

把 `.jpg` 放进 `loan-web/src/assets/login-bg/`，在 `index.js` 加一行映射：
```js
export const sceneries = {
  skyline: images['./skyline.jpg']?.default,
  office: images['./office.jpg']?.default,
  twilight: images['./twilight.jpg']?.default,
  // 新增：myscene: images['./myscene.jpg']?.default,
};
```
然后 `VITE_LOGIN_SCENERY=myscene` 即可使用。

### 响应式
< 900px 隐藏品牌区，登录卡占满。

### 遗留资产
`src/components/AnimatedBg.vue`（4 种纯 CSS 动态背景预设）保留为可复用组件，未来欢迎页/404/营销页需要动态效果可直接 `<AnimatedBg type="mesh" />`。

---

## 七、小程序端（loan-mini）

- 首页由骨架占位改为浅色商务风入口：品牌头 + 邀请码绑定 + 申请流程（4 步）+ 主 CTA。
- 导航栏白底黑字；全局浅灰底 `#f5f7fa`、品牌蓝 `#2563eb`，与 Web 端统一。

---

## 八、改动文件清单

**Web 端（loan-web/src）**
- `theme/index.js`、`styles/index.css` — 设计令牌重写
- `.env.development`、`.env.production` — 切换 light + 品牌蓝 + 登录背景配置
- `components/AnimatedBg.vue` — 可配置动态背景（mesh/aurora/particles/image）
- `layout/Layout.vue`、`views/Login.vue`、`views/Workbench.vue`
- `views/product/ProductList.vue`、`views/rule/RuleList.vue`
- `views/debug/DebugCenter.vue`、`views/audit/AuditCenter.vue`
- `components/DictTag.vue`

**小程序端（loan-mini）**
- `pages/index/index.vue`、`App.vue`、`pages.json`
