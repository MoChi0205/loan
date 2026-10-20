<template>
  <el-select
    :model-value="modelValue"
    :multiple="multiple"
    :clearable="clearable"
    :disabled="disabled"
    :placeholder="placeholder"
    :loading="loading"
    :filterable="true"
    :remote="true"
    :remote-method="onRemote"
    :fit-input-width="true"
    :teleported="true"
    :popper-class="popperClass"
    :popper-options="POPPER_OPTIONS"
    :reserve-keyword="false"
    class="dialog-select"
    @update:model-value="(v) => emit('update:modelValue', v)"
    @visible-change="onVisibleChange"
  >
    <el-option
      v-for="opt in options"
      :key="opt.value"
      :label="opt.label"
      :value="opt.value"
      :disabled="opt.disabled"
    >
      <span v-if="opt.sub" class="dialog-select__item">
        <span class="dialog-select__label">{{ opt.label }}</span>
        <span class="dialog-select__sub">{{ opt.sub }}</span>
      </span>
      <span v-else>{{ opt.label }}</span>
    </el-option>
  </el-select>
</template>

<script setup>
import { ref, watch } from 'vue';

/**
 * 弹窗内下拉选择器（D83 根因 A，2026-10-20）。
 *
 * <p><b>为什么要有这个组件</b>：此前弹窗内的下拉存在两套互相矛盾的实现——
 * 有的写死 `:teleported="false"`（popper 渲染进 `.el-dialog__body`，被其 overflow 裁掉，
 * 且 body 滚动时 popper 位置不重算，表现为"下拉被隐藏 / 随滚动折叠"）；
 * 有的什么都不限制（选项一多就溢出弹窗、列表不可滚动）。同一控件两套行为 = 问题反复复发。
 *
 * <p><b>本组件固定的规范（对齐 Element Plus / Ant Design 主流行为）</b>：
 * - `teleported=true`：浮层挂到 body，不受弹窗 overflow 裁剪；
 * - `fit-input-width`：下拉宽度 = 触发框宽度，不撑破弹窗；
 * - 列表 `max-height: 240px` + 内部滚动 —— <b>滚的是列表，输入框和弹窗都不动</b>；
 * - `popper-options` 启用 flip + preventOverflow：空间不足自动上翻、不溢出视口；
 * - 远程搜索内置 250ms 防抖 + 请求序号保护（避免慢响应覆盖新结果）；
 * - 面板展开时若选项为空，自动以空关键字拉一次。
 *
 * <p><b>用法</b>：
 * <pre>
 * &lt;DialogSelect v-model="targetStaffCode" :fetcher="searchStaff" placeholder="搜索姓名或工号" /&gt;
 * </pre>
 * `fetcher(keyword)` 需返回 `[{ value, label, sub?, disabled? }]` 数组（或 Promise）。
 */
const props = defineProps({
  modelValue: { type: [String, Number, Array], default: '' },
  /** 远程取数函数：(keyword) => Array | Promise&lt;Array&gt;，元素形如 { value, label, sub, disabled } */
  fetcher: { type: Function, required: true },
  placeholder: { type: String, default: '请输入关键字搜索' },
  multiple: { type: Boolean, default: false },
  clearable: { type: Boolean, default: true },
  disabled: { type: Boolean, default: false },
  /** 防抖毫秒数 */
  debounce: { type: Number, default: 250 },
  /** 附加给 popper 的 class（如需个别定制） */
  popperClass: { type: String, default: 'loan-dialog-select-popper' },
});

const emit = defineEmits(['update:modelValue']);

/** flip：空间不足自动翻转方向；preventOverflow：保证不溢出视口。 */
const POPPER_OPTIONS = Object.freeze({
  modifiers: [
    { name: 'flip', enabled: true, options: { fallbackPlacements: ['top-start', 'bottom-start'] } },
    { name: 'preventOverflow', enabled: true, options: { padding: 8 } },
  ],
});

const options = ref([]);
const loading = ref(false);
let timer;
let seq = 0;
let opened = false;

async function run(keyword) {
  const my = ++seq;
  loading.value = true;
  try {
    const list = await props.fetcher(String(keyword || '').trim());
    if (my !== seq) return; // 慢响应丢弃，避免覆盖新结果
    options.value = Array.isArray(list) ? list : [];
  } catch {
    if (my === seq) options.value = [];
  } finally {
    if (my === seq) loading.value = false;
  }
}

function onRemote(keyword) {
  clearTimeout(timer);
  timer = setTimeout(() => run(keyword), props.debounce);
}

function onVisibleChange(visible) {
  if (!visible) {
    opened = false;
    return;
  }
  opened = true;
  if (!options.value.length) run('');
}

/** 关键字被清空后（如 clearable 点击）重新拉一次全量。 */
watch(() => props.modelValue, (v) => {
  if (opened && (v === '' || v == null)) run('');
});
</script>

<style scoped>
.dialog-select {
  width: 100%;
}
.dialog-select__item {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}
.dialog-select__label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dialog-select__sub {
  flex: 0 0 auto;
  color: var(--loan-text-muted, #94a3b8);
  font-size: 12px;
}
</style>

<style>
/* popper 挂到 body，故不能用 scoped */
.loan-dialog-select-popper .el-select-dropdown__wrap {
  max-height: 240px;
}
.loan-dialog-select-popper .el-select-dropdown__item {
  height: auto;
  min-height: 34px;
  line-height: 1.5;
  padding-top: 5px;
  padding-bottom: 5px;
}
</style>
