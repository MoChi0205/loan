<template>
  <view class="search-bar-wrap" :class="{ 'is-fixed': fixed }">
    <view class="search-bar" @click="onTap">
      <view class="search-icon">
        <AppIcon name="search" size="sm" color="var(--text-secondary)" />
      </view>
      <input
        v-if="inputable"
        class="search-input"
        :value="modelValue"
        :placeholder="placeholder"
        placeholder-class="search-ph"
        :confirm-type="confirmType"
        :maxlength="maxlength"
        @input="onInput"
        @confirm="onConfirm"
      />
      <text v-else class="search-ph-text">{{ placeholder }}</text>
      <view class="search-btn" v-if="showButton" @click.stop="onConfirm">
        <text class="btn-text">搜索</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import AppIcon from './AppIcon.vue';

/**
 * 电商风格搜索栏（圆角胶囊 + 放大镜 + 占位文字 + 搜索按钮）。
 *
 * props:
 *   modelValue  绑定值（v-model，可选；不传则仅作入口点击）
 *   placeholder 占位文案
 *   inputable   是否可直接输入（true=输入框模式；false=纯入口点击跳转搜索页）
 *   showButton  是否显示右侧搜索按钮
 *   fixed       是否固定在顶部
 *   confirmType confirm 按钮类型（search/go）
 *   maxlength   最大输入长度
 *
 * 用法：
 *   <!-- 入口模式：点击跳转搜索页 -->
 *   <AppSearchBar placeholder="搜索产品名称 / 银行" @tap="goSearch" />
 *   <!-- 输入模式：实时搜索 -->
 *   <AppSearchBar v-model="keyword" placeholder="搜索产品" inputable show-button @search="onSearch" />
 */
const props = defineProps({
  modelValue: { type: String, default: '' },
  placeholder: { type: String, default: '搜索' },
  inputable: { type: Boolean, default: false },
  showButton: { type: Boolean, default: false },
  fixed: { type: Boolean, default: false },
  confirmType: { type: String, default: 'search' },
  maxlength: { type: Number, default: 50 },
});

const emit = defineEmits(['update:modelValue', 'tap', 'search', 'confirm']);

function onTap() {
  emit('tap');
}

function onInput(e) {
  const val = (e.detail && e.detail.value) || '';
  emit('update:modelValue', val);
}

function onConfirm(e) {
  const val = typeof e === 'string' ? e : ((e && e.detail && e.detail.value) || props.modelValue || '');
  emit('search', val);
  emit('confirm', val);
}
</script>

<style scoped>
.search-bar-wrap {
  padding: 0;
}
.search-bar-wrap.is-fixed {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 99;
}

.search-bar {
  display: flex;
  align-items: center;
  gap: 12rpx;
  height: 72rpx;
  padding: 0 12rpx 0 24rpx;
  background: var(--bg-input);
  border-radius: var(--radius-full);
  transition: background 0.15s;
}

.search-bar:active {
  background: var(--bg-card);
}

.search-icon {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.search-input {
  flex: 1;
  min-width: 0;
  height: 72rpx;
  font-size: var(--fs-md);
  color: var(--text-primary);
  background: transparent;
}

.search-ph {
  color: var(--text-secondary);
  font-size: var(--fs-md);
}

.search-ph-text {
  flex: 1;
  font-size: var(--fs-md);
  color: var(--text-secondary);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.search-btn {
  flex-shrink: 0;
  padding: 0 28rpx;
  height: 56rpx;
  border-radius: var(--radius-full);
  background: var(--brand-deep);
  display: flex;
  align-items: center;
  justify-content: center;
}

.search-btn:active {
  opacity: 0.85;
}

.btn-text {
  font-size: var(--fs-sm);
  font-weight: 600;
  color: var(--text-invert);
  letter-spacing: 1rpx;
}

/* #ifdef H5 */
@media (min-width: 768px) {
  .search-bar-wrap.is-fixed {
    max-width: 600px;
    margin: 0 auto;
    left: 50%;
    transform: translateX(-50%);
    right: auto;
  }
}
/* #endif */
</style>
