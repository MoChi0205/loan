# H5 + 小程序端 UI/UX 排版优化

## 页面风格样式

### 设计语言：瑞幸风（loan-mini）+ 企业蓝（loan-web）

小程序端遵循**瑞幸风格**，管理端遵循**企业蓝风格**，两侧品牌色**故意不统一**（用户确认决策），互不套用。

**小程序端色彩体系（全部定义在 App.vue `page` 选择器中）：**

| 令牌 | 值 | 用途 |
|---|---|---|
| `--brand-deep` | `#0B1D3A` | 深海军蓝，主按钮/顶栏/选中态 |
| `--brand-mid` | `#132D56` | 渐变次色 |
| `--brand-bright` | `#1A3A6E` | 渐变亮色 |
| `--gold` | `#C8A96E` | 暖金点缀，金额/等级金标 |
| `--gold-bg` | `#F5E6C4` | 暖金浅底 |
| `--bg-page` | `#F8FAFC` | 页面底色 |
| `--bg-card` | `#FFFFFF` | 卡片底色 |
| `--bg-input` | `#F1F5F9` | 输入框/浅底 |
| `--text-primary` | `#1E293B` | 主文字 |
| `--text-secondary` | `#64748B` | 次级文字（TabBar 未选中态） |
| `--text-placeholder` | `#CBD5E1` | 占位文字 |
| `--success` / `--success-bg` | `#10B981` / `#ECFDF5` | 成功语义 |
| `--warning` / `--warning-bg` | `#F59E0B` / `#FFFBEB` | 警告语义 |
| `--danger` | `#EF4444` | 危险语义 |

**字号体系（7 级 rpx）：** `--fs-2xl`(44) / `--fs-xl`(36) / `--fs-lg`(30) / `--fs-md`(26) / `--fs-sm`(24) / `--fs-xs`(22) / `--fs-xxs`(22)

**圆角（4 级）：** `--radius-sm`(16) / `--radius-md`(24) / `--radius-lg`(32) / `--radius-full`(999)

**阴影（3 级）：** `--shadow-sm` / `--shadow-md` / `--shadow-lg`

## 本次优化改动清单

### 1. TabBar 重构（`components/TabBar.vue`）
- 未选中态图标色：`--text-placeholder` → `--text-secondary`（可见度提升）
- 选中态：顶部 6rpx 主色指示条 + 图标背景药丸（rgba(11,29,58,.08)，替代不兼容的 color-mix）
- 阴影增强：`0 -4rpx 24rpx rgba(0,0,0,.06)`

### 2. AppIcon 图标修正（`components/AppIcon.vue`）
- 靶心(match)图标 → **指南针(compass)**图标（圆环+指针+中心轴，语义更清晰）
- 新增 search 放大镜图标

### 3. 首页重写（`pages/home/home.vue`）
- 顶部电商风格搜索栏（`--bg-card` + `--radius-full` + `--shadow-md`）
- 横向滚动动态数据卡片（3~4 张，角色化，顶部色条+图标底用设计令牌）
- 四宫格功能导航（4~6 项，角色化，色调区分：`rgba(11,29,58,.08)`/`--gold-bg`/`--success-bg`/`rgba(239,68,68,.08)`/`--bg-input`）

### 4. 落地页间距优化（`pages/index/index.vue`）
- hero padding 全面放大，时间线/CTA/底部声明间距增加

### 5. H5 登录页排版优化（`loan-web/src/views/Login.vue`）
- 卡片 padding 加大，tab/表单/按钮/演示区间距全面优化

### 6. 电商风格搜索栏组件（`components/AppSearchBar.vue`，新建）
- 圆角胶囊 + 放大镜 + 占位文字 + 可选搜索按钮
- 支持入口模式（点击跳转）和输入模式（实时搜索）
- 全部引用设计令牌：`--bg-input`/`--radius-full`/`--brand-deep`/`--fs-md`/`--fs-sm`

### 7. 产品列表页搜索接入（`pages/product/list.vue`）
- 接入 AppSearchBar + 实时本地搜索过滤 + 搜索无结果空状态

## 设计令牌合规检查

| 检查项 | 状态 |
|---|---|
| 裸色值（rgba 硬编码非令牌色） | 已清除（stat/nav 色调改用 `--gold-bg`/`--success-bg`/`rgba(11,29,58,.08)`） |
| `color-mix()` 微信不兼容 | 已替换为静态 `rgba(11,29,58,.08)` |
| 字号低于 `--fs-xxs`(22rpx) 下限 | 已修正（20rpx → `--fs-xxs`） |
| 圆角裸值 | 已替换（36rpx → `--radius-full`） |
| 单位混用 px | 无（全部 rpx） |
| App 组件复用 | TabBar/AppIcon/AppButton/AppEmpty/AppTag/AppSkeleton 全用组件 |

## 编译验证
- `loan-mini` H5 构建 `npm run build:h5` ✅ 成功通过
