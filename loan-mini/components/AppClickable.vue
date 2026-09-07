<template>
  <!--
    AppClickable —— 可键盘操作的点击容器（H5 无障碍 P1-#5 统一方案）。
    封装 view @click 的 tabindex / role / keydown，使 H5 端可用 Tab 聚焦、Enter/Space 触发。
    小程序端无键盘，role/tabindex 被安全忽略，不影响触摸交互。
    用法：<AppClickable class="x" :class="{active}" @click="fn">…</AppClickable>
         等价于 <view @click="fn">，但额外获得键盘可达。
  -->
  <view
    class="app-clickable"
    :role="role"
    :tabindex="disabled ? -1 : tabindex"
    :aria-disabled="disabled || undefined"
    @click="onClick"
    @keydown="onKeydown"
  >
    <slot />
  </view>
</template>

<script setup>
const props = defineProps({
  role: { type: String, default: 'button' },
  disabled: { type: Boolean, default: false },
  tabindex: { type: [Number, String], default: 0 },
});
const emit = defineEmits(['click', 'activate']);

function onClick(e) {
  if (props.disabled) return;
  emit('click', e);
  emit('activate', e);
}

function onKeydown(e) {
  if (props.disabled) return;
  // H5：Enter / Space 触发点击（小程序无键盘事件，安全忽略）
  if (e.key === 'Enter' || e.key === ' ' || e.key === 'Spacebar') {
    e.preventDefault();
    emit('click', e);
    emit('activate', e);
  }
}
</script>

<style scoped>
.app-clickable {
  outline: none;
}
/* 键盘焦点可见（WCAG 2.4.7）：仅 H5 生效，小程序 wxss 不支持 :focus-visible */
/* #ifdef H5 */
.app-clickable:focus-visible {
  outline: 4rpx solid var(--gold);
  outline-offset: 4rpx;
  border-radius: var(--radius-sm);
}
/* #endif */
</style>
