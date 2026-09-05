<template>
  <span class="loan-tag" :class="colorClass" role="img" :aria-label="`状态：${text}`">
    <svg
      viewBox="0 0 24 24"
      width="12"
      height="12"
      fill="none"
      stroke="currentColor"
      stroke-width="2.2"
      aria-hidden="true"
    >
      <path :d="iconPath" />
    </svg>
    <span>{{ text }}</span>
  </span>
</template>

<script setup>
import { computed } from 'vue';
import { dictLabel, dictColor } from '@/utils/dict';

/**
 * 枚举标签：按后端字典解析 code → 中文语义 + 语义色 + 状态图标展示。
 *
 * <p>无障碍：状态不止靠颜色区分，同时附带图标（✓ / ! / × / i / ·），
 * 色盲用户也能通过形状识别状态。
 *
 * 用法：<DictTag type="totalResult" :value="row.totalResult" />
 */
const props = defineProps({
  /** 字典类型 */
  type: { type: String, required: true },
  /** 枚举 code */
  value: { type: [String, Number], default: '' },
});

const text = computed(() => dictLabel(props.type, props.value));

const colorClass = computed(() => {
  const c = dictColor(props.type, props.value);
  const map = {
    success: 'loan-tag-success',
    warning: 'loan-tag-warning',
    danger: 'loan-tag-danger',
    info: 'loan-tag-info',
    primary: 'loan-tag-primary',
    muted: 'loan-tag-muted',
  };
  return map[c] || 'loan-tag-muted';
});

/** 状态图标路径（按语义色类型映射，配合颜色双重编码） */
const iconPath = computed(() => {
  const c = dictColor(props.type, props.value);
  switch (c) {
    case 'success':
      return 'M5 13l4 4L19 7'; // 对勾
    case 'warning':
      return 'M12 4v9M12 17h.01'; // 感叹号
    case 'danger':
      return 'M6 6l12 12M18 6L6 18'; // 叉
    case 'info':
      return 'M12 11v5M12 8h.01'; // 信息
    case 'primary':
      return 'M5 12h14M12 5v14'; // 加号
    default:
      return 'M12 12h.01'; // 圆点
  }
});
</script>
