<template>
  <ElSelect
    ref="innerRef"
    v-bind="$attrs"
    :teleported="true"
    :fit-input-width="true"
    :placement="placement"
    :popper-class="mergedPopperClass"
    :popper-options="popperOptions"
    :popper-style="popperStyle"
    @visible-change="onVisibleChange"
  >
    <!-- 透传全部插槽（默认插槽承载 el-option，prefix/empty 等命名插槽原样转发） -->
    <template v-for="(_, name) in $slots" #[name]="scope">
      <slot :name="name" v-bind="scope || {}" />
    </template>
  </ElSelect>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, ref } from 'vue';
import { ElSelect } from 'element-plus';

/**
 * 统一下拉选择器（弹窗安全版，2026-10-20 第四批 #3）。
 *
 * <p><b>为什么需要</b>：弹窗内裸 `<el-select>` 的 popper 默认以**视口**为边界，
 * 只要弹窗下方视口还有空间，下拉就不会翻转，直接从弹窗底部「穿出去」；
 * 选项多时还会超出可视区。全站 18 个页面 68 处弹窗下拉都存在此问题。
 *
 * <p><b>本组件保证</b>：
 * 1. **不超出所属弹窗**：打开时取最近的 `.el-dialog` 作为 popper 的 `preventOverflow` /
 *    `flip` 边界，超出即翻转或收缩；
 * 2. **数据可上下滚动**：依据上下可用空间动态计算 `--loan-popper-max-h`
 *    （120~280px），列表内部滚动，弹窗本身不滚动；
 * 3. **筛选框不随列表滚动**：filterable 的输入框位于触发器内，与下拉列表分离；
 * 4. **调用方零改动**：`inheritAttrs=false` + `v-bind="$attrs"` + 插槽全透传，
 *    把 `<el-select>` 换成 `<AppSelect>` 即可，原有 `v-model`/`el-option`/事件全部生效。
 *
 * <p>用法：
 * <pre>
 * &lt;AppSelect v-model="form.leadType" style="width:100%"&gt;
 *   &lt;el-option label="企业" value="ENTERPRISE" /&gt;
 * &lt;/AppSelect&gt;
 * </pre>
 */
defineOptions({ name: 'AppSelect', inheritAttrs: false });

const props = defineProps({
  /** 追加的 popper class（与统一 class 合并） */
  popperClass: { type: [String, Array, Object], default: '' },
});

const innerRef = ref();
const dialogEl = ref(null);
const placement = ref('bottom-start');
const maxHeight = ref(280);

const mergedPopperClass = computed(() => ['loan-app-select-popper', props.popperClass].filter(Boolean));

const popperOptions = computed(() => ({
  modifiers: [
    {
      name: 'flip',
      enabled: true,
      options: {
        fallbackPlacements: ['top-start', 'bottom-start'],
        boundary: dialogEl.value || 'clippingParents',
        padding: 8,
      },
    },
    {
      name: 'preventOverflow',
      enabled: true,
      options: {
        boundary: dialogEl.value || 'clippingParents',
        altBoundary: true,
        padding: 8,
      },
    },
  ],
}));

const popperStyle = computed(() => ({ '--loan-popper-max-h': `${maxHeight.value}px` }));

/** 依据弹窗与触发器的相对位置，决定展开方向与列表最大高度。 */
function measure() {
  const root = innerRef.value?.$el;
  if (!root || typeof root.closest !== 'function') return;
  const dialog = root.closest('.el-dialog');
  dialogEl.value = dialog;
  if (!dialog) {
    placement.value = 'bottom-start';
    maxHeight.value = 280;
    return;
  }
  const trigger = root.querySelector('.el-select__wrapper') || root;
  const dialogRect = dialog.getBoundingClientRect();
  const triggerRect = trigger.getBoundingClientRect();
  // 预留 8px 内边距 + 8px 呼吸空间
  const spaceBelow = dialogRect.bottom - triggerRect.bottom - 16;
  const spaceAbove = triggerRect.top - dialogRect.top - 16;
  const openUp = spaceBelow < 160 && spaceAbove > spaceBelow;
  placement.value = openUp ? 'top-start' : 'bottom-start';
  const usable = Math.max(openUp ? spaceAbove : spaceBelow, 120);
  maxHeight.value = Math.max(120, Math.min(280, Math.floor(usable)));
}

let listening = false;
function bind() {
  if (listening) return;
  listening = true;
  window.addEventListener('resize', measure, { passive: true });
  // 捕获阶段监听，覆盖弹窗内部滚动容器
  document.addEventListener('scroll', measure, true);
}
function unbind() {
  if (!listening) return;
  listening = false;
  window.removeEventListener('resize', measure);
  document.removeEventListener('scroll', measure, true);
}

function onVisibleChange(visible) {
  if (!visible) {
    unbind();
    return;
  }
  nextTick(measure);
  bind();
}

onBeforeUnmount(unbind);
</script>
