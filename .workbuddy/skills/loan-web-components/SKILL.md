---
name: loan-web-components
description: >-
  loan-main 前端公共组件库规范。编写 Web 管理端列表页、表单页、弹窗、日期选择、表格操作列、
  分页、查询栏、枚举字典、批量操作、权限控制时使用；涵盖 AppSearchBar / AppPagination / AppDialog /
  AppDateTime / AppTableActions / DictTag / DictSelect / AppEmpty / AppSkeleton 以及
  useTable、appConfirm、v-permission 的用法，禁止重复造轮子。
---

# 前端公共组件库（loan-web-components）

## Step 0 · 前置门禁（强制，不可跳过）

1. **先查历史结论**：`grep -n "组件\|useTable\|表格\|弹窗\|字典" docs/knowledge-base/10-历史结论与决策日志.md#结论台账`，
   从**最新条目往下**读（台账按时间倒序，最新在最上方）；**命中即遵守**；状态为「已被 Dxx 替代」则跳读 Dxx。
   > ⚠️ 取号前必须自己刚跑 `grep -o "^| D[0-9-]*" docs/knowledge-base/10-历史结论与决策日志.md` 取实时最大编号（禁采信转述 / 记忆 / 分配表）。
2. **无结论且不确定 → 停下来问用户**，禁止臆断（新增公共组件影响面大，须先确认）。
3. **再读元技能** `loan-knowledge`（`.workbuddy/skills/loan-knowledge/SKILL.md`），按其 Step 1–5 执行。
4. 回复开头输出：`【结论核对】命中 Dx-x（…）/ 未命中（grep 关键词：…）`。

## 何时使用

- 新增或修改列表页（产品 / 规则 / 客户 / 线索 / 工单…）
- 需要查询栏、分页、弹窗、日期选择、表格操作列、状态标签时 —— **先查本组件库，禁止重复造轮子**
- 编写权限控制 / 二次确认 / 空状态 / 骨架屏逻辑时
- 与 `loan-web-ui`（视觉与布局规范）、`loan-web-dev`（工程结构规范）交叉使用

## 目录

```
src/
├── components/
│   ├── AppSearchBar.vue    # 查询栏（筛选字段 + 查询/重置按钮）
│   ├── AppPagination.vue   # 分页
│   ├── AppDialog.vue       # 弹窗（标题/底部按钮/loading）
│   ├── AppDateTime.vue     # 日期时间选择器（统一格式/快捷选项）
│   ├── AppTableActions.vue # 表格操作列（修复按钮拥挤换行 bug）
│   ├── AppEmpty.vue        # 空状态插画（含操作 slot）
│   ├── AppSkeleton.vue     # 列表骨架屏
│   ├── DictTag.vue         # 枚举标签（图标 + 颜色双编码，无障碍）
│   └── DictSelect.vue      # 枚举下拉（支持 filterable 本地过滤 / remote 远程搜索）
├── composables/
│   └── useTable.js         # 列表页通用 hook（query/loading/data/total/load/search/reset）
├── directives/
│   └── permission.js       # v-permission 指令 + setPermissionChecker
└── utils/
    └── confirm.js          # appConfirm / appAlert 统一确认弹窗
```

## 组件用法

### AppSearchBar（查询栏）

```vue
<AppSearchBar :loading="loading" @search="onSearch" @reset="onReset">
  <el-input v-model="query.name" placeholder="名称" style="width: 200px" clearable />
  <DictSelect v-model="query.status" type="productStatus" placeholder="状态" />
  <template #append>
    <el-button @click="onExport">导出</el-button>
  </template>
</AppSearchBar>
```

- 默认 slot 放筛选字段，`#append` 放额外操作
- 事件：`search` / `reset`；Props：`loading`、`compact`、`showReset`、`showSearch`

### AppPagination（分页）

```vue
<AppPagination v-model:page="query.page" v-model:size="query.size" :total="total" @change="load" />
```

- 事件：`change`（page/size 变化后触发，用于重新拉数据）

### AppDialog（弹窗）

```vue
<AppDialog v-model:visible="visible" title="编辑产品" :loading="saving" @confirm="onSave">
  <el-form>...</el-form>
</AppDialog>
```

- 默认底部「取消 + 确定」，可用 `#footer` 覆盖；`destroy-on-close` 默认开启
- Props：`visible`、`title`、`width`、`loading`；事件：`confirm` / `cancel` / `update:visible`
- ⚠️ 弹窗内 `el-select` 建议 `:placement="'top-start'"`（dropdown 吐舌头问题，见 `loan-web-ui`）

### AppDateTime（日期时间）

```vue
<AppDateTime v-model="form.startTime" type="datetime" placeholder="选择时间" />
<AppDateTime v-model="range" type="daterange" :shortcuts="true" />
```

- `type` 支持 `date` / `datetime` / `daterange` / `datetimerange` / `month` / `year`
- `:shortcuts="true"` 开启默认快捷选项（今日 / 近 7 天 / 近 30 天）

### AppTableActions（表格操作列）

**修复操作列按钮拥挤 / 换行 / 串位的核心组件**，用法：

```vue
<el-table-column label="操作" width="200" fixed="right">
  <template #default="{ row }">
    <AppTableActions :actions="rowActions(row)" />
  </template>
</el-table-column>
```

```js
const rowActions = (row) => [
  { key: 'view', label: '查看', onClick: () => onView(row) },
  { key: 'edit', label: '编辑', onClick: () => onEdit(row) },
  { key: 'toggle', label: '停用', type: 'warning', confirm: '确认停用？', onClick: () => onToggle(row) },
  { key: 'more', label: '更多', children: [
    { key: 'copy', label: '复制编码', onClick: () => onCopy(row) },
    { key: 'del', label: '删除', type: 'danger', confirm: '确认删除？', onClick: () => onDel(row) },
  ]},
];
```

- action 字段：`key`（唯一）、`label`、`type`（primary/success/warning/danger）、`onClick`、`disabled`、
  `confirm`（二次确认文案）、`children`（二级菜单）、`undo` + `undoMessage`（删除撤销，5s 内可恢复）、`success`（轻操作成功提示）
- 超过 `maxInline`（默认 3；`loan-web-ui` 要求管理端默认 **2**）自动收进「更多」下拉
- 列宽建议 **180~200px**（不要再用 130px；3+ 操作统一 180）

### AppEmpty（空状态）

```vue
<el-table :data="data">
  <template #empty>
    <AppEmpty title="暂无XX" desc="说明 + 操作引导">
      <el-button type="primary" size="small" @click="create">新增</el-button>
    </AppEmpty>
  </template>
</el-table>
```

### AppSkeleton（骨架屏）

```vue
<AppSkeleton v-if="loading && !data.length" :rows="6" :cols="8" />
<el-table v-else ...>
```

- 与空态互斥：`v-if="!loading && !list.length"` 才是空态

### useTable（列表页 hook）

```js
const { loading, data, total, query, load, onSearch, onReset, onPageChange, onSizeChange } =
  useTable(pageProducts, { productName: '', bankName: '', status: '' });

onMounted(load);
```

- `loader(query)` 返回 `{ records, total }`（或 `{ data: { records, total } }`）
- `query` 已内置 `page` / `size`，`onSearch` 回到第一页，`onReset` 恢复初始条件
- ⚠️ **useTable 不自动加载**，必须 `onMounted(load)`；提供 `handleSortChange` 供 el-table `@sort-change` 绑定

### DictSelect（枚举下拉）

```vue
<DictSelect v-model="form.group" type="customerGroup" />
<DictSelect v-model="form.group" type="customerGroup" filterable />              <!-- 本地过滤 -->
<DictSelect v-model="form.group" remote :remote-method="searchDict" />           <!-- 远程搜索 -->
```

- `filterable` 本地过滤（字典选项较多时）；`remote` + `remote-method` 远程搜索（字典体量巨大时）
- `remoteMethod: (keyword) => Promise<Array<{label, value, colorType}>>`

### DictTag（枚举标签）

- 图标 + 颜色双编码（不依赖颜色传达语义，无障碍）
- 外层 span 加 `role="img" :aria-label="状态：${text}"`（WCAG 要求）

### 表格批量操作

```vue
<el-table ref="tableRef" @selection-change="onSelectionChange" row-key="id">
  <el-table-column type="selection" width="44" fixed="left" />
  ...
</el-table>
<div v-if="selectedRows.length" class="batch-bar">
  已选 {{ selectedRows.length }} 项
  <el-button size="small" @click="onBatch('ENABLED')">批量启用</el-button>
</div>
```

```js
const selectedRows = ref([]);
const tableRef = ref();
function onSelectionChange(rows) { selectedRows.value = rows; }
async function onBatch(status) {
  try { await appConfirm(`确认操作 ${selectedRows.value.length} 条？`); } catch { return; }
  // 调接口...
  tableRef.value?.clearSelection();
}
```

### appConfirm / appAlert

```js
import { appConfirm } from '@/utils/confirm';
try { await appConfirm('确认删除？'); /* 确定 */ } catch { /* 取消 */ }
```

> **禁止**使用原生 `window.confirm` / `window.alert` / `window.prompt`（见 `loan-web-ui` 第十章）。

### v-permission

```vue
<el-button v-permission="'product:add'">新增</el-button>
```

```js
import { setPermissionChecker } from '@/directives/permission';
setPermissionChecker((code) => userStore.permissions.includes(code)); // 接入真实权限后注入
```

## 契约红线速查

- **契约真源**：`db/loan-db-schema.sql`（表数以该文件为准）→ `loan-service` 代码 → `docs/knowledge-base/`
- **禁止引用**已失效的 `output/` 与「逻辑蓝图.html」路径（见 `loan-knowledge`）
- **业务 ID 展示**：组件内展示编号列必须走 `loan-biz-id` 的宽度/次要化规则

## 自检清单

- [ ] Step 0 结论核对是否已输出？
- [ ] 列表页是否用了 `useTable` 而非手写 query/loading/data/total？
- [ ] 是否 `onMounted(load)`（useTable 不自动加载）？
- [ ] 查询栏是否用 `AppSearchBar` 而非裸 `.loan-filter-bar`？
- [ ] 分页是否用 `AppPagination`？
- [ ] 操作列是否用 `AppTableActions`、列宽 ≥ 180px？
- [ ] 弹窗是否用 `AppDialog`？二次确认是否用 `appConfirm`（非原生 confirm）？
- [ ] 状态标签是否用 `DictTag`（图标 + 颜色 + aria-label）？枚举是否走后端字典？
- [ ] 空态是否用 `AppEmpty`？首次加载是否用 `AppSkeleton`？
- [ ] 新增公共组件是否先与用户确认（影响面大）？

## 相关文档

- `docs/knowledge-base/10-历史结论与决策日志.md#结论台账`（**Step 0 必查**）
- `docs/knowledge-base/05-前端工程要点.md#常用组件 API`
- `docs/knowledge-base/04-后端 API 契约.md#通用约定`（接口返回结构：`{ records, total }`）
- `docs/knowledge-base/02-业务红线与编码规范.md#编码规范`
- `docs/knowledge-base/06-业务结论沉淀索引（C1-C19）.md#结论速查`
- `docs/plans/archive/ui-design-spec.md`（设计令牌与视觉规范）
- 交叉技能：`loan-web-dev`、`loan-web-ui`、`loan-biz-id`
