<template>
  <view
    class="app-li"
    :class="{ 'is-tappable': tappable }"
    :hover-class="tappable ? 'app-li--hover' : ''"
    :hover-stay-time="80"
    :role="tappable ? 'button' : undefined"
    :tabindex="tappable ? 0 : undefined"
    :aria-label="ariaLabel || title"
    @click="onClick"
    @keydown.enter="onClick"
  >
    <view v-if="$slots.leading" class="app-li__leading">
      <slot name="leading" />
    </view>

    <view class="app-li__main">
      <text v-if="id" class="app-li__id">{{ id }}</text>
      <text class="app-li__title">{{ title }}</text>
      <view v-if="$slots.meta || desc" class="app-li__meta">
        <slot name="meta">
          <text v-if="desc" class="app-li__desc">{{ desc }}</text>
        </slot>
      </view>
      <slot />
    </view>

    <view v-if="$slots.trailing" class="app-li__trailing">
      <slot name="trailing" />
    </view>
  </view>
</template>

<script setup>
/**
 * 列表项组件（设计系统 v1.0）。
 *
 * 统一替代原 .list-item / .report-card / .prod-card 三套并行实现。
 * 差异全部通过 slot 承载：
 *   #leading   左侧固定区（图标 / 评级块 / 头像）
 *   #meta      主内容下方元信息行（归属 / 手机 / 时间 / 命中数）
 *   默认 slot  主内容下方的自由区（进度条 / 标签组 / 操作按钮）
 *   #trailing  右侧固定区（箭头 / 状态标签）
 *
 * 无障碍：可点击时自动补 role="button" / tabindex / aria-label（WCAG 2.1.1、2.4.7）。
 * 触控：min-height 88rpx = 44px（WCAG 2.5.5）。
 *
 * 用法：
 *   <AppListItem title="王*明 · 上海*明科技" id="rep-001" tappable @click="go">
 *     <template #leading><view class="grade">B+</view></template>
 *     <template #meta><text>归属 陈顾问 · 命中 3 款</text></template>
 *     <template #trailing><AppTag type="success">已上架</AppTag></template>
 *   </AppListItem>
 */
const props = defineProps({
  /** 顶部小字（报告号 / 工单号 / 产品编码，等宽字体） */
  id: { type: String, default: '' },
  /** 主标题（单行省略） */
  title: { type: String, default: '' },
  /** 描述文字（meta 未传时生效） */
  desc: { type: String, default: '' },
  /** 是否可点击（决定 hover / role / tabindex / click 事件） */
  tappable: { type: Boolean, default: false },
  /** 读屏标签，缺省用 title */
  ariaLabel: { type: String, default: '' },
});

const emit = defineEmits(['click']);

function onClick(e) {
  if (props.tappable) emit('click', e);
}
</script>

<style scoped>
.app-li {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  background: var(--bg-card);
  border-radius: var(--radius-md);
  padding: var(--space-3) var(--space-4);
  margin-bottom: var(--space-2);
  box-shadow: var(--shadow-sm);
  /* 触控目标 ≥44px（WCAG 2.5.5） */
  min-height: 88rpx;
  transition: background var(--transition-fast);
}

.app-li--hover { background: var(--bg-page); }

.app-li__leading { flex-shrink: 0; display: flex; align-items: center; }
.app-li__main { flex: 1; min-width: 0; }
.app-li__trailing { flex-shrink: 0; display: flex; align-items: center; }

.app-li__id {
  font-size: var(--fs-xs);
  color: var(--text-secondary);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  letter-spacing: 0.4rpx;
}

.app-li__title {
  display: block;
  font-size: var(--fs-md);
  font-weight: 600;
  color: var(--text-primary);
  line-height: var(--lh-tight);
  margin: 4rpx 0 8rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-li__meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  align-items: center;
}

.app-li__desc {
  font-size: var(--fs-sm);
  color: var(--text-secondary);
}
</style>
