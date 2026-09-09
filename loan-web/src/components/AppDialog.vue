<template>
  <el-dialog
    :model-value="visible"
    :title="title"
    :width="width"
    :top="top"
    :close-on-click-modal="closeOnClickModal"
    :close-on-press-escape="closeOnPressEscape"
    :destroy-on-close="destroyOnClose"
    :append-to-body="true"
    :modal-class="modalClass"
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
 * <p>定位策略（用户 2026-09-09 反馈「打开弹窗要向上翻才看到」）：
 * - 默认 anchor 在视口顶部 `10vh`，而非 Element Plus 默认的 `50% 居中`：
 *   长表格页面滚动到下方时点「分配顾问」/「指派」/「编辑」，
 *   居中策略会让弹窗落在视口中央偏下、与底部操作栏距离过近；
 *   anchor-top 10vh 既稳定可见、又能给 el-select 下拉（placement=top-start）留出向上展开空间
 * - 可通过 `top` prop 覆盖（如详情页大弹窗可用 5vh）；
 * - modal-class 默认带 `loan-app-dialog`，用于全局样式精细调优。
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
  /** 弹窗距视口顶部的距离，避免视口中部居中导致长表页面看不到顶部内容（用户 2026-09-09 反馈） */
  top: { type: String, default: '10vh' },
  loading: { type: Boolean, default: false },
  closeOnClickModal: { type: Boolean, default: false },
  closeOnPressEscape: { type: Boolean, default: false },
  destroyOnClose: { type: Boolean, default: true },
  /** 透传给 el-dialog 的 modal-class，用于覆盖 Element Plus 内置类样式 */
  modalClass: { type: String, default: 'loan-app-dialog' },
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

/* 注意：以下暴露给 slots 使用的 props 列表，遵循 <script setup> 编译器的隐式 return 规则 */
defineExpose({});
</script>
