---
name: frontend-components
description: >-
  loan-platform 前端公共组件库规范。编写 Web 管理端列表页、表单页、弹窗、日期选择、
  操作列、分页、查询栏、权限控制时使用；涵盖 AppSearchBar / AppPagination / AppDialog /
  AppDateTime / AppTableActions / DictTag / DictSelect 以及 useTable、appConfirm、v-permission。
---

# 前端公共组件库

## 何时使用

- 新增或修改列表页（产品 / 规则 / 客户 / 线索 / 工单…）
- 需要查询栏、分页、弹窗、日期选择、表格操作列、状态标签时——**先查本组件库，禁止重复造轮子**
- 编写权限控制 / 二次确认逻辑时

## 目录

```
src/
├── components/
│   ├── AppSearchBar.vue    # 查询栏（筛选字段 + 查询/重置按钮）
│   ├── AppPagination.vue   # 分页
│   ├── AppDialog.vue       # 弹窗（标题/底部按钮/loading）
│   ├── AppDateTime.vue     # 日期时间选择器（统一格式/快捷选项）
│   ├── AppTableActions.vue # 表格操作列（修复按钮拥挤换行 bug）
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

- 默认 slot 放筛选字段，#append 放额外操作
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

### AppDateTime（日期时间）

```vue
<AppDateTime v-model="form.startTime" type="datetime" placeholder="选择时间" />
<AppDateTime v-model="range" type="daterange" :shortcuts="true" />
```

- type 支持 date/datetime/daterange/datetimerange/month/year
- `:shortcuts="true"` 开启默认快捷选项（今日/近 7 天/近 30 天）

### AppTableActions（表格操作列）

**修复操作列按钮拥挤/换行/串位的核心组件**，用法：

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

- action 字段：`key`（唯一）、`label`、`type`（primary/success/warning/danger）、`onClick`、`disabled`、`confirm`（二次确认文案）、`children`（二级菜单）
- 超过 `maxInline`（默认 3）自动收进「更多」下拉
- 列宽建议 **180~200px**（不要再用 130px）

### useTable（列表页 hook）

```js
const { loading, data, total, query, load, onSearch, onReset, onPageChange, onSizeChange } =
  useTable(pageProducts, { productName: '', bankName: '', status: '' });

onMounted(load);
```

- `loader(query)` 返回 `{ records, total }`（或 `{ data: { records, total } }`）
- `query` 已内置 `page` / `size`，`onSearch` 回到第一页，`onReset` 恢复初始条件

### DictSelect（枚举下拉）

```vue
<DictSelect v-model="form.group" type="customerGroup" />
<DictSelect v-model="form.group" type="customerGroup" filterable />              <!-- 本地过滤 -->
<DictSelect v-model="form.group" remote :remote-method="searchDict" />           <!-- 远程搜索 -->
```

- `filterable` 本地过滤（字典选项较多时）；`remote` + `remote-method` 远程搜索（字典体量巨大时）
- `remoteMethod: (keyword) => Promise<Array<{label, value, colorType}>>`

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

### v-permission

```vue
<el-button v-permission="'product:add'">新增</el-button>
```

```js
import { setPermissionChecker } from '@/directives/permission';
setPermissionChecker((code) => userStore.permissions.includes(code)); // 接入真实权限后注入
```

## 自检清单

- [ ] 列表页是否用了 `useTable` 而非手写 query/loading/data/total？
- [ ] 查询栏是否用 `AppSearchBar` 而非裸 `.loan-filter-bar`？
- [ ] 分页是否用 `AppPagination`？
- [ ] 操作列是否用 `AppTableActions`、列宽 ≥ 180px？
- [ ] 弹窗是否用 `AppDialog`？二次确认是否用 `appConfirm`？
- [ ] 状态标签是否用 `DictTag`（图标 + 颜色）？枚举是否走后端字典？

## 相关文档

- `../frontend-development/SKILL.md`（整体前端规范）
- `../../ui-design-spec.md`（设计令牌与视觉规范）
