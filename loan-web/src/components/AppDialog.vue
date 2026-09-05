<template>
  <el-dialog
    :model-value="visible"
    :title="title"
    :width="width"
    :close-on-click-modal="closeOnClickModal"
    :close-on-press-escape="closeOnPressEscape"
    :destroy-on-close="destroyOnClose"
    :append-to-body="true"
    @update:model-value="(v) => $emit('update:visible', v)"
  >
    <slot />
    <template #footer>
      <slot name="footer">
        <el-button @click="onCancel">取消</el-button>
        <el-button type="primary" :loading="loading" @click="onConfirm">确定</el-button>
      </slot>
    </template>
  </el-dialog>
</template>

<script setup>
/**
 * 弹窗（公共组件）。
 *
 * <p>统一弹窗行为：
 * - 取消：即时关闭（emit cancel + update:visible false）
 * - 确认：不自动关闭，由父组件在异步保存成功后自行关闭（:loading 期间禁用重复点击）
 *   —— 取消是即时动作、确认是异步动作，两者按各自语义关闭，避免保存中途丢弹窗；
 * - 遮罩点击 / ESC 默认不关闭（长表单防误触丢失输入），需要时通过 prop 开启。
 *
 * 用法：
 *   <AppDialog v-model:visible="dialogVisible" title="编辑产品" :loading="saving" @confirm="onSave">
 *     <el-form>...</el-form>
 *   </AppDialog>
 */
const props = defineProps({
  visible: { type: Boolean, default: false },
  title: { type: String, default: '' },
  width: { type: [String, Number], default: '560px' },
  loading: { type: Boolean, default: false },
  closeOnClickModal: { type: Boolean, default: false },
  closeOnPressEscape: { type: Boolean, default: false },
  destroyOnClose: { type: Boolean, default: true },
});

const emit = defineEmits(['update:visible', 'confirm', 'cancel']);

function onCancel() {
  emit('cancel');
  emit('update:visible', false);
}
function onConfirm() {
  if (props.loading) return; // 提交中防重复触发
  emit('confirm');
}
</script>
