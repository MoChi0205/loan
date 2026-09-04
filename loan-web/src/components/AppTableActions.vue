<template>
  <div class="app-table-actions">
    <template v-for="(a, i) in renderedActions" :key="a.key">
      <span v-if="i > 0" class="app-table-actions__sep" aria-hidden="true">·</span>
      <el-dropdown
        v-if="a.children"
        trigger="click"
        @command="(c) => onCommand(c)"
        @visible-change="(v) => onDropdownChange(a.key, v)"
      >
        <span
          class="app-table-actions__link"
          :class="['app-table-actions__' + (a.type || 'primary')]"
          role="button"
          tabindex="0"
          :aria-haspopup="'true'"
          :aria-expanded="String(expanded.has(a.key))"
        >
          {{ a.label }}
          <svg viewBox="0 0 24 24" width="10" height="10" fill="none" stroke="currentColor" stroke-width="2" style="margin-left: 1px; vertical-align: -1px"><path d="M6 9l6 6 6-6" /></svg>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item
              v-for="c in a.children"
              :key="c.key"
              :command="c"
              :disabled="c.disabled"
            >
              <span :class="['app-table-actions__item', c.type && ('app-table-actions__' + c.type)]">
                {{ c.label }}
              </span>
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <span
        v-else
        class="app-table-actions__link"
        :class="[
          'app-table-actions__' + (a.type || 'primary'),
          { 'is-disabled': a.disabled },
        ]"
        role="button"
        tabindex="0"
        @click="onAction(a)"
        @keyup.enter="onAction(a)"
      >
        {{ a.label }}
      </span>
    </template>
  </div>
</template>

<script setup>
import { computed, h, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { appConfirm } from '@/utils/confirm';

/** 已展开的更多下拉集合（key → 展开态），用于 aria-expanded 实时同步 */
const expanded = ref(new Set());
function onDropdownChange(key, visible) {
  const s = new Set(expanded.value);
  if (visible) s.add(key);
  else s.delete(key);
  expanded.value = s;
}

/**
 * 表格操作列（公共组件，修复按钮拥挤/换行 bug）。
 *
 * <p>统一操作列渲染：等宽间距、垂直居中、不再换行；超过 maxInline 个的操作收进"更多"。
 * 支持二级操作（children）与确认弹窗（confirm）。
 *
 * 用法：
 *   const actions = (row) => [
 *     { key: 'view', label: '查看', onClick: () => onView(row) },
 *     { key: 'edit', label: '编辑', onClick: () => onEdit(row) },
 *     { key: 'toggle', label: '停用', type: 'warning', confirm: '确认停用？', onClick: () => onToggle(row) },
 *     { key: 'more', label: '更多', children: [
 *       { key: 'copy', label: '复制编码', onClick: () => onCopy(row) },
 *       { key: 'del', label: '删除', type: 'danger', confirm: '确认删除？', onClick: () => onDel(row) },
 *     ]},
 *   ];
 *
 *   <el-table-column label="操作" width="180" fixed="right">
 *     <template #default="{ row }">
 *       <AppTableActions :actions="actions(row)" />
 *     </template>
 *   </el-table-column>
 */
const props = defineProps({
  /** 操作列表：{ key, label, type?, onClick, disabled?, confirm?, children? } */
  actions: { type: Array, default: () => [] },
  /** 内联展示上限（不含"更多"），超出收进更多下拉；默认 2 */
  maxInline: { type: Number, default: 2 },
});

/** 计算要渲染的操作：内联前 maxInline 个 + 剩余收进更多 */
const renderedActions = computed(() => {
  const list = props.actions.filter((a) => !a.hidden);
  if (list.length <= props.maxInline) return list;
  const inline = list.slice(0, props.maxInline);
  const rest = list.slice(props.maxInline);
  return [
    ...inline,
    {
      key: '__more__',
      label: '更多',
      children: rest,
    },
  ];
});

/** 执行操作（含 confirm 二次确认 + disabled 拦截） */
async function onAction(a) {
  if (a.disabled) return;
  if (a.confirm) {
    try {
      await appConfirm(a.confirm, '操作确认', { type: 'warning' });
    } catch {
      return; // 取消
    }
  }
  try {
    await a.onClick?.();
    // 轻操作成功反馈:action 定义 success 消息时提示(如复制成功)
    if (a.success) ElMessage.success(a.success);
    // 删除撤销:action 定义 undo 回调时,成功弹 5s 可撤销 toast(防误删)
    if (typeof a.undo === 'function') {
      ElMessage({
        message: h('div', { style: 'display:flex;align-items:center;gap:10px' }, [
          h('span', null, a.undoMessage || '删除成功'),
          h(
            'a',
            {
              style: 'color:var(--loan-primary);cursor:pointer;font-weight:600',
              onClick: () => {
                a.undo();
              },
            },
            '撤销'
          ),
        ]),
        type: 'success',
        duration: 5000,
        showClose: true,
      });
    }
  } catch (e) {
    // 业务异常一般由 axios 拦截器统一处理，这里兜底
    if (e?.message) ElMessage.error(e.message);
  }
}

function onCommand(c) {
  onAction(c);
}
</script>

<style scoped>
/* 关键：操作列容器用 inline-flex + 固定 gap + 强制 nowrap，杜绝按钮换行/截断；
   与状态列等行高时由父 td vertical-align:middle 居中，不再视觉错位。 */
:deep(.el-table) .app-table-actions,
.app-table-actions {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
  font-size: 12px;
  line-height: 1.4;
  /* 允许长中文和下拉触发器完整展示；表格固定列负责布局，不在组件内二次裁切 */
  max-width: none;
  overflow: visible;
}

.app-table-actions__sep {
  color: var(--loan-border-strong, #d8dee8);
  font-size: 11px;
  user-select: none;
  margin: 0 1px;
}

.app-table-actions__link {
  font-size: 12px;
  cursor: pointer;
  padding: 0 2px;
  transition: opacity var(--loan-transition, 0.2s);
  outline: none;
  white-space: nowrap;
}

.app-table-actions__primary {
  color: var(--loan-primary);
}

.app-table-actions__success {
  color: var(--loan-success);
}

.app-table-actions__warning {
  color: var(--loan-warning);
}

.app-table-actions__danger {
  color: var(--loan-danger);
}

.app-table-actions__link:hover {
  opacity: 0.75;
}

.app-table-actions__link:focus-visible {
  outline: 2px solid var(--loan-primary);
  outline-offset: 2px;
  border-radius: 2px;
}

.app-table-actions__link.is-disabled {
  color: var(--loan-text-muted);
  cursor: not-allowed;
}

.app-table-actions__link.is-disabled:hover {
  opacity: 1;
}

.app-table-actions__item {
  font-size: 13px;
}
</style>
