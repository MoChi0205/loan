<template>
  <view class="consent" @click="toggle">
    <view class="box" :class="{ checked: modelValue }"><text v-if="modelValue">✓</text></view>
    <text>已阅读并同意</text>
    <text class="link" @click.stop="$emit('open', '用户协议')">《用户协议》</text>
    <text>和</text>
    <text class="link" @click.stop="$emit('open', '隐私政策')">《隐私政策》</text>
  </view>
</template>
<script setup>
const props = defineProps({ modelValue: { type: Boolean, default: true } });
const emit = defineEmits(['update:modelValue', 'open']);
function toggle() { emit('update:modelValue', !props.modelValue); }
</script>
<style scoped>
.consent{display:flex;align-items:center;justify-content:center;min-height:76rpx;white-space:nowrap;color:var(--text-secondary);font-size:22rpx}
.box{width:34rpx;height:34rpx;flex:0 0 34rpx;margin-right:12rpx;border:3rpx solid var(--control-border);border-radius:8rpx;display:flex;align-items:center;justify-content:center;box-sizing:border-box;background:var(--bg-card);color:var(--text-invert);font-size:22rpx;font-weight:800}
.box.checked{background:var(--brand-deep);border-color:var(--brand-deep);box-shadow:0 0 0 3rpx rgba(47,91,211,.14)}
.link{color:var(--brand-deep);font-weight:600}
</style>
