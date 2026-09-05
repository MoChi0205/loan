<template>
  <el-select
    :model-value="modelValue"
    :placeholder="placeholder"
    :clearable="clearable"
    :disabled="disabled"
    :filterable="filterable || remote"
    :remote="remote"
    :remote-method="onRemoteMethod"
    :loading="remoteLoading"
    :style="style"
    @update:model-value="onChange"
  >
    <el-option
      v-for="opt in options"
      :key="opt.value"
      :label="opt.label"
      :value="opt.value"
    >
      <span class="dict-option">
        <i class="dict-dot" :class="dotClass(opt.colorType)"></i>
        <span>{{ opt.label }}</span>
      </span>
    </el-option>
  </el-select>
</template>

<script setup>
import { computed, ref } from 'vue';
import { dictOptions } from '@/utils/dict';

/**
 * 枚举下拉：选项展示中文 label + 语义色圆点，值为 code（存储/传输用 code）。
 *
 * <p>支持三种模式：
 *  1. 普通：从 Pinia dict store 取全部选项
 *  2. filterable：本地过滤（字典选项较多时）
 *  3. remote + remoteMethod：远程搜索（字典体量巨大时）
 *
 * 用法：
 *   <DictSelect v-model="form.customerGroup" type="customerGroup" />
 *   <DictSelect v-model="form.group" type="customerGroup" filterable />
 *   <DictSelect v-model="form.group" remote :remote-method="searchDict" />
 */
const props = defineProps({
  /** 字典类型 */
  type: { type: String, required: true },
  modelValue: { type: [String, Number], default: '' },
  placeholder: { type: String, default: '请选择' },
  clearable: { type: Boolean, default: true },
  disabled: { type: Boolean, default: false },
  /** 本地过滤（选项较多时） */
  filterable: { type: Boolean, default: false },
  /** 远程搜索 */
  remote: { type: Boolean, default: false },
  /** 远程搜索方法：(keyword) => Promise<Array<{label, value, colorType}>> */
  remoteMethod: { type: Function, default: undefined },
  /** 远程加载态 */
  remoteLoading: { type: Boolean, default: false },
  /** 自定义宽度 */
  style: { type: [String, Object], default: undefined },
});

const emit = defineEmits(['update:modelValue']);

/** 本地选项（普通/可过滤模式） */
const localOptions = computed(() => dictOptions(props.type));

/** 远程搜索返回的选项 */
const remoteOptions = ref([]);

/** 最终展示的选项 */
const options = computed(() => (props.remote ? remoteOptions.value : localOptions.value));

function onChange(val) {
  emit('update:modelValue', val);
}

/** 远程搜索 */
async function onRemoteMethod(keyword) {
  if (typeof props.remoteMethod !== 'function') return;
  remoteOptions.value = await props.remoteMethod(keyword);
}

function dotClass(colorType) {
  const map = {
    success: 'dot-success',
    warning: 'dot-warning',
    danger: 'dot-danger',
    info: 'dot-info',
    primary: 'dot-primary',
    muted: 'dot-muted',
  };
  return map[colorType] || 'dot-muted';
}
</script>

<style scoped>
.dict-option {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.dict-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.dot-success {
  background: var(--loan-success);
}
.dot-warning {
  background: var(--loan-warning);
}
.dot-danger {
  background: var(--loan-danger);
}
.dot-info {
  background: var(--loan-info);
}
.dot-primary {
  background: var(--loan-primary);
}
.dot-muted {
  background: var(--loan-text-muted);
}
</style>
