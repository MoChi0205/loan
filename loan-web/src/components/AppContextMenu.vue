<template>
  <Teleport to="body">
    <Transition name="ctx-menu">
      <div
        v-if="state.open"
        class="ctx-menu"
        :style="{ left: state.x + 'px', top: state.y + 'px' }"
        role="menu"
        @contextmenu.prevent
      >
        <template v-for="(item, i) in state.items" :key="i">
          <div v-if="item.divider" class="ctx-sep" />
          <button
            v-else
            class="ctx-item"
            :class="{ 'ctx-item--danger': item.danger }"
            type="button"
            :disabled="item.disabled"
            role="menuitem"
            @click="onClick(item)"
          >
            <AppIcon v-if="item.icon" class="ctx-icon" :name="item.icon" :size="16" />
            <span>{{ item.label }}</span>
          </button>
        </template>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { onMounted, onBeforeUnmount } from 'vue';
import { contextMenuState, closeContextMenu } from '@/utils/contextMenu';
import AppIcon from '@/components/AppIcon.vue';

/** 模板别名：state = contextMenuState */
const state = contextMenuState;

/** 点击菜单项：先关闭再执行回调 */
function onClick(item) {
  closeContextMenu();
  if (item.onClick) item.onClick();
}

/** 点击外部关闭 */
function onDocClick(ev) {
  if (!contextMenuState.open) return;
  const el = ev.target;
  if (el && el.closest && el.closest('.ctx-menu')) return;
  closeContextMenu();
}
function onKeyDown(ev) {
  if (ev.key === 'Escape') closeContextMenu();
}
onMounted(() => {
  document.addEventListener('click', onDocClick);
  window.addEventListener('blur', closeContextMenu);
  window.addEventListener('keydown', onKeyDown);
});
onBeforeUnmount(() => {
  document.removeEventListener('click', onDocClick);
  window.removeEventListener('blur', closeContextMenu);
  window.removeEventListener('keydown', onKeyDown);
});
</script>

<style scoped>
/* ============================================================
 * 全局右键菜单（暗色卡片 + 圆角 + 阴影 + 进入动画）
 * ============================================================ */
.ctx-menu {
  position: fixed;
  z-index: 3000;
  min-width: 176px;
  max-width: 260px;
  padding: 6px;
  background: var(--loan-card-bg);
  border: 1px solid var(--loan-border);
  border-radius: 10px;
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.35), 0 2px 8px rgba(0, 0, 0, 0.18);
  font-size: 13.5px;
  color: var(--loan-text, #1f2937);
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.ctx-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  background: transparent;
  border: none;
  border-radius: 6px;
  text-align: left;
  cursor: pointer;
  color: inherit;
  font-size: inherit;
  transition: background 0.12s, color 0.12s;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.ctx-item:hover:not(:disabled) {
  background: var(--loan-primary-soft, rgba(59, 130, 246, 0.18));
  color: var(--loan-text, #1f2937);
}
.ctx-item:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.ctx-icon {
  display: inline-flex;
  flex-shrink: 0;
  opacity: 0.85;
}
.ctx-item:hover:not(:disabled) .ctx-icon { opacity: 1; }
.ctx-item--danger:not(:disabled) { color: var(--loan-danger); }
.ctx-item--danger:hover:not(:disabled) {
  background: var(--loan-primary-soft, rgba(239, 68, 68, 0.18));
  color: var(--loan-danger);
}
.ctx-sep {
  height: 1px;
  margin: 4px 8px;
  background: var(--loan-border);
}
/* 进入/退出动画 */
.ctx-menu-enter-active,
.ctx-menu-leave-active {
  transition: opacity 0.14s ease, transform 0.14s ease;
}
.ctx-menu-enter-from,
.ctx-menu-leave-to {
  opacity: 0;
  transform: translateY(-4px) scale(0.98);
}
</style>
