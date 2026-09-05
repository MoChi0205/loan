<template>
  <view class="app-steps" :class="`app-steps--${layout}`" :role="layout === 'horizontal' ? 'list' : undefined">
    <view
      v-for="(s, i) in steps"
      :key="i"
      class="app-step"
      :class="{ 'is-done': i < current, 'is-active': i === current, 'is-clickable': i < current }"
      :role="layout === 'horizontal' ? 'listitem' : undefined"
      @click="onStep(i)"
    >
      <view class="app-step__dot">
        <text v-if="i < current" class="app-step__check">✓</text>
        <text v-else class="app-step__num">{{ i + 1 }}</text>
      </view>

      <text v-if="layout === 'horizontal'" class="app-step__name">{{ s }}</text>
      <view v-else class="app-step__body">
        <text class="app-step__name">{{ s }}</text>
        <slot :name="`desc-${i}`" />
      </view>

      <view v-if="i < steps.length - 1" class="app-step__line" />
    </view>
  </view>
</template>

<script setup>
/**
 * 步骤条组件（设计系统 v1.0）。
 *
 * 统一替代原登录页 .flow-step 与匹配页 .steps 两套实现。
 *
 * props:
 *   steps   步骤名数组，如 ['目标企业','经营事实','上传材料','核验匹配']
 *   current 当前步索引（0 基）
 *   layout  horizontal（横向，匹配流程）/ vertical（纵向，认证流程）
 *
 * 交互约束：只允许点击「已完成」的步骤回看，禁止跳过未完成的必填步骤。
 * 无障碍：横向用 role="list"/"listitem"；已完成步骤可点，带 aria 提示。
 *
 * 用法：
 *   <AppStepper :steps="['目标企业','经营事实','上传材料','核验匹配']"
 *               :current="1" @change="i => step = i" />
 */
const props = defineProps({
  steps: { type: Array, required: true },
  current: { type: Number, default: 0 },
  layout: { type: String, default: 'horizontal' }, // horizontal | vertical
});

const emit = defineEmits(['change']);

function onStep(i) {
  // 仅允许回看已完成步骤，防止跳过必填
  if (i < props.current) emit('change', i);
}
</script>

<style scoped>
.app-steps {
  display: flex;
  background: var(--bg-card);
  border-radius: var(--radius-md);
  padding: var(--space-4) var(--space-4) var(--space-3);
  box-shadow: var(--shadow-sm);
}

.app-steps--vertical {
  flex-direction: column;
  padding: var(--space-4);
  background: transparent;
  box-shadow: none;
}

.app-step {
  flex: 1;
  display: flex;
  align-items: center;
  position: relative;
}

.app-steps--horizontal .app-step {
  flex-direction: column;
  gap: var(--space-1);
}

.app-steps--vertical .app-step {
  flex-direction: row;
  gap: var(--space-3);
  padding-bottom: var(--space-4);
  align-items: flex-start;
}

.app-step__dot {
  width: 52rpx;
  height: 52rpx;
  border-radius: 50%;
  background: var(--bg-card);
  border: 3rpx solid var(--line);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--fs-sm);
  font-weight: 700;
  color: var(--text-secondary);
  transition: all var(--transition-base);
  flex-shrink: 0;
}

.app-step.is-done .app-step__dot {
  background: var(--brand-deep);
  border-color: var(--brand-deep);
  color: var(--text-invert);
}

.app-step.is-active .app-step__dot {
  background: var(--gold);
  border-color: var(--gold);
  /* 深色文字保证 WCAG AA：白字 on 暖金仅 2.24:1，改深金棕文字 */
  color: var(--gold-text);
  box-shadow: 0 0 0 8rpx rgba(200, 169, 110, 0.18);
}

.app-step.is-clickable .app-step__dot { cursor: pointer; }

.app-step__check { font-size: var(--fs-sm); line-height: 1; }
.app-step__num { font-size: var(--fs-sm); line-height: 1; }

.app-step__name {
  font-size: var(--fs-sm);
  color: var(--text-secondary);
  text-align: center;
}

.app-steps--vertical .app-step__name { text-align: left; font-size: var(--fs-md); }

.app-step.is-active .app-step__name {
  color: var(--brand-deep);
  font-weight: 600;
}

.app-step.is-done .app-step__name { color: var(--text-primary); }

.app-step__body { flex: 1; min-width: 0; }

.app-step__line { background: var(--line); }

.app-steps--horizontal .app-step__line {
  position: absolute;
  left: 50%;
  right: -50%;
  top: 26rpx;
  height: 3rpx;
  z-index: -1;
}

.app-steps--vertical .app-step__line {
  position: absolute;
  left: 26rpx;
  top: 52rpx;
  bottom: 0;
  width: 4rpx;
}

.app-step.is-done .app-step__line { background: var(--brand-deep); }
</style>
