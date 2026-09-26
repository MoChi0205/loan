<template>
  <el-popover v-model:visible="visible" placement="bottom-end" :width="360" trigger="click" @show="loadMessages">
    <template #reference>
      <button class="notification-bell" type="button" aria-label="消息通知">
        <AppIcon name="bell" :size="18" />
        <span v-if="unread > 0" class="notification-badge">{{ unread > 99 ? '99+' : unread }}</span>
      </button>
    </template>
    <div class="notification-panel">
      <div class="notification-panel__header"><strong>消息通知</strong><el-button link type="primary" :disabled="!unread" @click="markAll">全部已读</el-button></div>
      <div v-loading="loading" class="notification-list">
        <button v-for="item in messages" :key="item.notificationId" type="button" class="notification-item" :class="{ unread: item.readStatus === 0 }" @click="read(item)">
          <span class="notification-item__dot"></span><span class="notification-item__body"><span class="notification-item__title">{{ item.title }}</span><span class="notification-item__content">{{ item.content }}</span><span class="notification-item__time">{{ item.createdAt || '' }}</span></span>
        </button>
        <el-empty v-if="!loading && !messages.length" description="暂无消息" :image-size="52" />
      </div>
    </div>
  </el-popover>
</template>
<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import AppIcon from '@/components/AppIcon.vue';
import { markAllNotificationsRead, markNotificationRead, myNotifications, unreadCount } from '@/api/notification';
const visible = ref(false); const loading = ref(false); const unread = ref(0); const messages = ref([]); let timer;
async function refreshUnread() { try { unread.value = Number((await unreadCount()).data || 0); } catch { /* 静默轮询 */ } }
async function loadMessages() { loading.value = true; try { const res = await myNotifications({ page: 1, size: 20 }); messages.value = res.data?.records || []; unread.value = Number(res.data?.unreadCount || 0); } catch { messages.value = []; } finally { loading.value = false; } }
async function read(item) { if (item.readStatus !== 0) return; await markNotificationRead(item.notificationId); item.readStatus = 1; unread.value = Math.max(0, unread.value - 1); }
async function markAll() { if (!unread.value) return; await markAllNotificationsRead(); messages.value.forEach((item) => { item.readStatus = 1; }); unread.value = 0; ElMessage.success('已全部标记为已读'); }
onMounted(() => { refreshUnread(); timer = window.setInterval(refreshUnread, 60000); }); onBeforeUnmount(() => { if (timer) window.clearInterval(timer); });
</script>
<style scoped>
.notification-bell { position: relative; display: inline-flex; align-items: center; justify-content: center; width: 34px; height: 34px; border: 0; border-radius: var(--loan-radius-sm); color: var(--loan-text-secondary); background: transparent; cursor: pointer; }
.notification-bell:hover { color: var(--loan-primary); background: var(--loan-surface); }
.notification-badge { position: absolute; top: -2px; right: -3px; min-width: 16px; height: 16px; padding: 0 4px; border-radius: 999px; color: #fff; background: var(--loan-danger, #ef4444); font-size: 10px; line-height: 16px; text-align: center; }
.notification-panel__header { display: flex; justify-content: space-between; align-items: center; padding-bottom: 8px; border-bottom: 1px solid var(--loan-border); color: var(--loan-text); }
.notification-list { max-height: 360px; overflow: auto; }
.notification-item { display: flex; width: 100%; gap: 8px; padding: 11px 4px; border: 0; border-bottom: 1px solid var(--loan-border); background: transparent; text-align: left; cursor: pointer; }
.notification-item:hover { background: var(--loan-surface); }
.notification-item__dot { width: 6px; height: 6px; margin-top: 6px; border-radius: 50%; background: transparent; flex-shrink: 0; }
.notification-item.unread .notification-item__dot { background: var(--loan-primary); }
.notification-item__body { display: flex; min-width: 0; flex-direction: column; gap: 3px; }
.notification-item__title { color: var(--loan-text); font-size: 13px; font-weight: 600; }
.notification-item__content { overflow: hidden; color: var(--loan-text-secondary); font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.notification-item__time { color: var(--loan-text-muted); font-size: 11px; }
</style>
