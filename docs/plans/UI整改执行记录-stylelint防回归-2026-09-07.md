# UI 整改执行记录 · stylelint 防回归（2026-09-07）

> 本轮对应待办：工程化防回归（引 stylelint 禁裸值规则）。
> 工作流：严格按 `impeccable` `/normalize` 的「禁裸值」原则，把前 4 轮整改（裸色 / 裸字号 / 裸 rgba / 装饰渐变）的成果用工具永久锁定。

## 一、决策与定位
- 用户二选一（全站 H5 键盘可达 rollout **或** stylelint 防回归），本期选 **stylelint 防回归**。
- 定位：它是前 4 轮整改的「保险栓」——一次配置，永久拦截裸 hex / 裸字号回归，且能客观验收前期整改是否真清零。

## 二、交付物
| 文件 | 作用 |
|---|---|
| `loan-mini/.stylelintrc.json` | 禁裸值规则配置（rpx 白名单 + 禁裸 hex） |
| `loan-mini/package.json` 新增 `lint:style` 脚本 | 一键全站扫描 |
| `loan-mini` 安装 devDeps：`stylelint@^16` + `postcss-html@^2` | 扫描引擎 |
| `loan-mini/App.vue` 令牌块加 `stylelint-disable/enable` 包裹 | 允许设计令牌源写裸 hex |

### 配置要点
```jsonc
{
  "overrides": [{ "files": ["**/*.vue"], "customSyntax": "postcss-html" }],
  "rules": {
    "unit-no-unknown": [true, { "ignoreUnits": ["rpx"] }],   // 小程序 rpx 合法
    "color-no-hex":     [true, { "except": ["custom-properties"] }]
  },
  "ignoreFiles": ["**/node_modules/**","**/dist/**","**/coverage/**","**/uni_modules/**","**/.hbuilderx/**"]
}
```

## 三、执行过程（含踩坑与纠正）
1. **首扫基线**：`color-no-hex` 命中 56 处裸 hex，分两类合理项——
   - 令牌定义源 `App.vue`（如 `--brand-deep: #0B1D3A`）：设计系统源头，必须允许 → 用 `/* stylelint-disable color-no-hex */` 包裹令牌块（83–183 行）。
   - 组件内 `var(--x, #hex)` 回退值（`AppButton/AppIcon/AppTag/AppTopBar/AppSkeleton`）：批量去除（`var(--x, #hex)` → `var(--x)`，令牌已在 `page` 块稳定定义，去回退让设计系统更纯净）。
2. **插件方案被否**：曾尝试 `stylelint-declaration-strict-value`（其价值是可豁免 `var()` 回退裸 hex）。实测 **stylelint 16/17 已移除 `plugins` 配置项**、降级 stylelint@15 又因 peer 冲突装不上，**且该插件规则名（`plugin/` / 包名前缀 / `scale-unlimited/`）均不被识别** → 放弃插件，改用内置 `color-no-hex`。代价：内置规则无法豁免 `var()` 回退，故改为「去除回退值」方案（对稳定设计系统可接受）。
3. **结果**：最终复扫 **0 错误**。

## 四、污染清理（重要）
- 早期一次 `npm install` 漏写 `cd`，误把 `stylelint@15` 装进 **Maven 父目录** `/Users/admin/Downloads/loan-main`，生成了 `package.json` + `package-lock.json` + `node_modules`。
- 已用 npm 原生机制清理：根 `package.json`/`package-lock.json` 已删除（`node_modules` 被 `.gitignore` 忽略、保留无害），`git status` 确认根目录无 npm 残留。
- loan-mini 内因插件方案失败残留的孤儿依赖（`stylelint-config-standard-scss` / `stylelint-declaration-strict-value`）已 `npm prune` 清除。

## 五、验证
- `npm run lint:style`（loan-mini）输出空、**退出码 0** → 全站裸值清零已锁定。
- 引擎版本：`stylelint 16.26.1`（managed node workspace 安装，隔离不污染用户环境）。

## 六、局限与后续建议
1. **CI / pre-commit 未接**：当前 `lint:style` 仅手动触发。要真正「防回归」，需挂到 CI 或 husky pre-commit（建议在 loan-mini 加 husky + lint-staged，提交时自动扫）。
2. **Web 端未覆盖**：本配置在 `loan-mini`。`loan-web` 已做的令牌化整改（Overview 等）尚未被 lint 门禁覆盖，建议为 `loan-web` 单独建 `.stylelintrc.json`（同源规则，覆盖 `src/**/*.{vue,scss,css}`）。
3. **H5 键盘可达全站 rollout 未做**：仍是最大一块待办（其余 ~14 页约 65 处 `view @click` 未补 `<AppClickable>`）。配方已固化，逐页替换 + QA 即可。
4. **`--color-*` 兼容层**：`App.vue` 内旧别名定义（TODO 全量迁移后删除）待确认无引用后删除。

## 七、本轮结论
小程序端「裸值防回归」已用 stylelint 落地并验证清零；误装污染已清理；设计系统因去 `var()` 回退更纯净。下一步建议优先接 CI 门禁 + 推进 H5 键盘可达全站 rollout。

---

## 八、续作：门禁落地 + loan-web 同款配置 + H5 键盘可达全站 rollout（2026-09-07）

> 旧「局限 1/2/3」三项已全部完成；仅剩「局限 4」（`--color-*` 兼容层）待办。

### 8.1 CI / husky 门禁（完成 · 旧局限 1）
- 根仓库 `package.json` 新增 `husky@^9` + `lint-staged@^15` devDeps，`"prepare": "husky"`。
- `npx husky init` 已设 `core.hooksPath=.husky/_`；`.husky/pre-commit` 改为跑 `npx lint-staged`。
- 根 `.lintstagedrc.json`：`*.{vue,scss,css}` → `bash scripts/lint-style-staged.sh`。
- `scripts/lint-style-staged.sh`：按路径前缀 `loan-mini/*` / `loan-web/*` `cd` 进子包并去前缀，调用该子包自带 `./node_modules/.bin/stylelint --fix`。**仅扫暂存文件**，裸 hex 等不可自修项非零退出即阻断提交。
- 实测：构造含裸 hex 的临时文件跑该脚本 → stylelint 退出 2，门禁生效（已删除临时文件）。
- `.github/workflows/stylelint.yml`：push/PR 对 `loan-mini/**`、`loan-web/**` 各跑 `npm install && npm run lint:style`，全量门禁。

### 8.2 loan-web 同款配置 + 扫清 234 违规（完成 · 旧局限 2）
- 新增 `loan-web/.stylelintrc.json`（同源规则：rpx 白名单 + `color-no-hex` + `postcss-html` 处理 `.vue`）。
- `loan-web/package.json` 加 `lint:style` 脚本 + `stylelint@^16.26.1` + `postcss-html@^2` devDeps（managed node 隔离安装）。
- 首扫基线 **234 处裸 hex（跨 24 文件）**。关键发现：loan-web 令牌系统**暗色优先**（`--loan-text:#f1f5f9`），违规组件却大量硬编码**浅色报表/白底预览**浅色板；直接映射暗色令牌会视觉回归 → 与用户确认采用「主题令牌映射」方案。
- 处理：
  - 令牌源 `src/styles/index.css` 的 `:root` 块用 `/* stylelint-disable color-no-hex -- 原因 */` 包裹（自定义属性声明 + Element Plus 变量覆盖允许裸 hex）。
  - 新增 `--loan-paper:#ffffff`（白底/纸面刻意保留白）与 `--loan-black:#000000`（color-mix 压暗 / 遮罩，非主题）。
  - 用 stylelint JSON 报告的精确 `line/column/endColumn`，逐处把裸 hex → `var(--loan-*)` 主题令牌（白底→`--loan-paper`、黑→`--loan-black`、状态色→语义令牌、浅色中性→对应主题令牌），仅行内替换不跨行、不碰 `<script>` JS 颜色。
  - 2 处歧义/装饰（AnimatedBg 渐变 `#0f172a/#1e293b`、`Layout.vue` 的 `var(--loan-warning-line,#e6d4a8)` 回退）用 `/* stylelint-disable-line color-no-hex */` 精确豁免。
  - 回退值被映射成的 66 处 `var(--x, var(--x))` 自引用冗余已脚本折叠为 `var(--x)`。
  - 复扫 **0 错误（234 → 0）**。

### 8.3 H5 键盘可达全站 rollout（完成 · 旧局限 3）
- `AppClickable` 组件封装 `view @click` 的 `tabindex`/`role`/`keydown`：H5 可 Tab 聚焦、Enter/Space 触发；小程序端无键盘安全忽略。
- 脚本化将 **9 个页面文件**的 `view ...@click` 转为 `<AppClickable ...>`，并去除源 `view` 上冗余 `tabindex/role/@keydown`（避免双击）。
- 修复转换器早期属性间距缺陷（`<AppClickable v-for=" o in ..."` 值内多余空格已清除）；复验 **25 处开/闭标签配对**、**0 处 `view @click` 残留**、**0 处冗余 a11y 属性**。

### 8.4 验证（2026-09-07）
- `loan-mini` 与 `loan-web` 两包 `npm run lint:style` 均 **退出码 0、0 问题**。
- husky pre-commit + lint-staged 路由脚本实测可用；构造违规文件验证门禁阻断。
- **遗留（仍待办）**：`App.vue` `--color-*` 旧别名兼容层（旧局限 4），待确认无引用后删除；双端编译验证。
