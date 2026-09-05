# 合规分析报告 UI 优化（三端联动）

## 设计风格

### 小程序端（瑞幸风）
- 主色 `#0B1D3A`（深海军蓝），点缀 `#C8A96E`（暖金）
- **扁平化设计**：结果卡、分数块、进度条、维度条全部用扁平色，去除装饰性渐变
- 合规声明使用 `--warning-bg` + `--warning-line` 突出显示

### Web 管理端（企业蓝）
- 主色 `#3b82f6`（蓝色），辅助色 `#10b981`（绿）/`#f59e0b`（橙）
- 详情抽屉从纯 el-descriptions → 结果横幅 + 分段信息卡 + 合规声明

## 本次优化改动

### 小程序端

**`pages/report/detail.vue`**（报告详情）：
| 元素 | 改前 | 改后 |
|---|---|---|
| 结果卡 rc-pass | `linear-gradient(135deg, success-text, success)` | `var(--success)` 扁平绿 |
| 结果卡 rc-condition | `linear-gradient(135deg, role-adviser, gold)` | `var(--warning)` 扁平橙 |
| 结果卡 rc-reject | `linear-gradient(135deg, text-secondary, text-secondary)` | `var(--text-secondary)` 扁平灰 |
| 分数块 sc-high | `linear-gradient(brand-deep, brand-bright)` | `var(--brand-deep)` 扁平深蓝 |
| 分数块 sc-mid | `linear-gradient(role-adviser, gold)` | `var(--gold)` 扁平暖金 |
| 分数块 sc-low | `linear-gradient(text-secondary, placeholder)` | `var(--text-secondary)` 扁平灰 |
| 进度条 score-fill | `linear-gradient(90deg, brand-bright, gold)` | `var(--brand-deep)` 扁平深蓝 |
| 维度条 dim-fill | `linear-gradient(90deg, brand-bright, gold)` | `var(--brand-deep)` 扁平深蓝 |
| 合规提示 tip-card | `bg-input` 次级底 | `var(--warning-bg)` + `var(--warning-line)` 警告色突出 |

**`pages/report/list.vue`**（报告列表）：
| 元素 | 改前 | 改后 |
|---|---|---|
| 评级块 gb-high | `linear-gradient(brand-deep, brand-bright)` | `var(--brand-deep)` 扁平 |
| 评级块 gb-middle | `linear-gradient(brand-bright, brand-bright)` | `var(--brand-bright)` 扁平 |
| 评级块 gb-low | `linear-gradient(text-secondary, placeholder)` | `var(--text-secondary)` 扁平 |

**`pages/match/match.vue`**（匹配页）：
| 元素 | 改前 | 改后 |
|---|---|---|
| 结果卡 rc-pass | `linear-gradient(brand-deep, brand-bright)` | `var(--success)` 扁平绿 |
| 结果卡 rc-condition | `linear-gradient(role-adviser, gold)` | `var(--warning)` 扁平橙 |
| 结果卡 rc-reject | `linear-gradient(text-secondary, text-secondary)` | `var(--text-secondary)` 扁平灰 |

### Web 管理端

**`views/report/ScreeningReport.vue`**（初筛报告）：
- 详情抽屉从纯 `el-descriptions` → 结构化布局：
  1. **结果横幅**（report-banner）：按档位着色（rb-high=蓝/rb-middle=橙/rb-low=灰），展示命中产品/可进件银行/通过条件拒绝三项指标
  2. **基础信息卡**：2 列 el-descriptions，含报告编号/来源/客户/手机号/档位/状态
  3. **匹配建议**：独立 section + advice-box（代码块风格）
  4. **合规声明**：report-compliance（warning-bg 底色 + 警告文字色）
- 表格中 pass/cond/rej 计数色值从硬编码 → 设计令牌 `var(--loan-success)` / `var(--loan-warning)` / `var(--loan-danger)`

## 编译验证
- `loan-mini` H5 构建 ✅
- `loan-web` Vite 构建 ✅
