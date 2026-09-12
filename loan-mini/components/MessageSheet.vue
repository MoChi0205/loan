<template>
  <view v-if="visible">
    <view class="sheet-mask" @click="close" />
    <view class="sheet" :data-theme="themeMode">
      <view class="grab" />
      <text class="sheet-title">消息中心</text>
      <text class="sheet-sub">仅显示 提交人 + 事项 + 时间，不含任何单号</text>

      <!-- 首次加载 -->
      <AppSkeleton v-if="loading && !items.length" :rows="3" />

      <!-- 列表 -->
      <view v-else-if="items.length" class="msg-list">
        <view v-for="(m, i) in items" :key="i" class="msg-row">
          <view class="msg-ava" :class="{ 'ava-new': m.unread }">{{ (m.source || '系')[0] }}</view>
          <view class="msg-main">
            <text class="msg-t1">{{ m.matter }}</text>
            <text class="msg-t2">{{ m.source }} · {{ m.time }}</text>
          </view>
          <text v-if="m.unread" class="msg-new">新</text>
        </view>
      </view>

      <!-- 失败：允许重试 -->
      <view v-else-if="failed" class="msg-fallback">
        <AppEmpty title="消息加载失败" desc="请检查网络后重试" />
        <AppButton variant="secondary" size="md" block :loading="loading" @click="load">重新加载</AppButton>
      </view>

      <!-- 空态 -->
      <AppEmpty v-else title="暂无消息" desc="审核结果与分配通知会在这里提醒你" />

      <AppButton class="sheet-close" variant="secondary" size="md" block @click="close">关闭</AppButton>
    </view>
  </view>
</template>

<script setup>
/**
 * 消息中心弹层（首页铃铛与「我的」页共用）。
 *
 * <p>唯一数据来源 = {@code /api/mini/notification/**}，**不再包含任何示例数据**：
 * 打开时实时拉取，未读数与红点由真实数据驱动（2026-09-11 审计 P0-2）。
 *
 * <p>三种状态齐备：加载中骨架屏 / 空态 / 失败可重试。
 *
 * <p>合规：每条只渲染「来源 + 事项 + 时间」，接口不返回业务单号
 * （D68 / 02-红线 #7）。
 *
 * 用法：
 *   <MessageSheet v-model:visible="msgOpen" @read="onMessageRead" />
 */
import { ref, watch } from 'vue';
import AppButton from './AppButton.vue';
import AppSkeleton from './AppSkeleton.vue';
import AppEmpty from './AppEmpty.vue';
import { myMessages, markAllRead } from '../api/notification';
import { useThemeMode } from '../theme';

const props = defineProps({
  /** 是否展示（配合 v-model:visible 使用）。 */
  visible: { type: Boolean, default: false },
  /** 主动读取条数上限（弹层一次展示）。 */
  size: { type: Number, default: 20 },
});
const emit = defineEmits(['update:visible', 'close', 'read']);

const themeMode = useThemeMode();

const items = ref([]);
const loading = ref(false);
const failed = ref(false);

watch(() => props.visible, (open) => {
  if (open) openSheet();
});

/**
 * 打开弹层：拉取消息 → 有未读则标记已读并通知父级刷新红点。
 */
async function openSheet() {
  await load();
  if (failed.value) return;
  if (items.value.some((m) => m.unread)) {
    try {
      await markAllRead();
    } catch (e) {
      // 标记失败不阻断浏览；红点由父级下次轮询自然修正
    }
    items.value = items.value.map((m) => ({ ...m, unread: false }));
  }
  emit('read');
}

/**
 * 拉取消息列表（失败置 failed，由弹层展示重试）。
 */
async function load() {
  loading.value = true;
  failed.value = false;
  try {
    const data = await myMessages(1, props.size);
    items.value = (data && data.records) || [];
  } catch (e) {
    items.value = [];
    failed.value = true;
  } finally {
    loading.value = false;
  }
}

function close() {
  emit('update:visible', false);
  emit('close');
}
</script>

<style scoped>
.sheet-mask { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(17, 30, 54, 0.42); z-index: 200; }
.sheet {
  position: fixed; left: 0; right: 0; bottom: 0; z-index: 201;
  background: var(--bg-card);
  border-radius: 36rpx 36rpx 0 0;
  padding: 20rpx 32rpx calc(40rpx + env(safe-area-inset-bottom));
  max-height: 80%;
  overflow-y: auto;
}
/* #ifdef H5 */
@media (min-width: 768px) {
  .sheet { left: 0; right: 0; max-width: 600px; margin: 0 auto; }
}
/* #endif */
.grab { width: 72rpx; height: 8rpx; border-radius: 6rpx; background: var(--line); margin: 6rpx auto 20rpx; }
.sheet-title { font-size: 34rpx; font-weight: 800; color: var(--text-primary); }
.sheet-sub { display: block; margin-top: 8rpx; margin-bottom: 12rpx; font-size: 22rpx; color: var(--text-secondary); }

.msg-list { margin-top: 8rpx; }
.msg-row { display: flex; align-items: center; gap: 20rpx; padding: 18rpx 0; border-top: 1rpx solid var(--line); }
.msg-row:first-child { border-top: none; }
.msg-ava {
  width: 76rpx; height: 76rpx; border-radius: 22rpx; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  background: var(--bg-input); color: var(--brand-deep); font-size: 28rpx; font-weight: 800;
}
.msg-ava.ava-new { background: var(--avatar-gold-bg); color: var(--text-invert); }
.msg-main { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.msg-t1 { font-size: 27rpx; font-weight: 700; color: var(--text-primary); }
.msg-t2 { margin-top: 6rpx; font-size: 22rpx; color: var(--text-secondary); }
.msg-new {
  font-size: 22rpx; font-weight: 700; flex-shrink: 0;
  color: var(--warning-text); background: var(--warning-bg);
  padding: 6rpx 16rpx; border-radius: 999rpx;
}

.msg-fallback { padding-top: 8rpx; }
.sheet-close { margin-top: 20rpx; }
</style>
