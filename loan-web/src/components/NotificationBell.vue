<template>
  <el-popover v-model:visible="visible" placement="bottom-end" :width="360" trigger="click" @show="loadMessages">
    <template #reference>
      <button class="notification-bell" type="button" aria-label="消息通知">
        <AppIcon name="bell" :size="18" />
        <span v-if="unread > 0" class="notification-badge">{{ unread > 99 ? '99+' : unread }}</span>
      </button>
    </template>
    <div class="notification-panel">
      <div class="notification-panel__header">
        <strong>消息通知</strong>
        <span class="notification-panel__actions">
          <el-button link type="primary" :disabled="!unread" @click="markAll">全部已读</el-button>
          <el-button link type="danger" :disabled="!messages.length" @click="clearAll">清空</el-button>
        </span>
      </div>
      <div v-loading="loading" class="notification-list">
        <div v-for="item in messages" :key="item.notificationId" class="notification-item" :class="{ unread: item.readStatus === 0 }" role="button" tabindex="0" @click="read(item)" @keyup.enter="read(item)">
          <span class="notification-item__dot"></span>
          <span class="notification-item__body"><span class="notification-item__title">{{ item.title }}</span><span class="notification-item__content">{{ item.content }}</span><span class="notification-item__time">{{ item.createdAt || '' }}</span></span>
          <el-button class="notification-item__del" link type="danger" size="small" @click.stop="removeOne(item)">删除</el-button>
        </div>
        <el-empty v-if="!loading && !messages.length" description="暂无消息" :image-size="52" />
      </div>
    </div>
  </el-popover>
</template>
<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useRouter } from 'vue-router';
import AppIcon from '@/components/AppIcon.vue';
import { deleteAllNotifications, deleteNotifications, markAllNotificationsRead, markNotificationRead, myNotifications, unreadCount } from '@/api/notification';
const router = useRouter();
const visible = ref(false); const loading = ref(false); const unread = ref(0); const messages = ref([]); let timer;
async function refreshUnread() { try { unread.value = Number((await unreadCount()).data || 0); } catch { /* 静默轮询 */ } }
async function loadMessages() { loading.value = true; try { const res = await myNotifications({ page: 1, size: 20 }); messages.value = res.data?.records || []; unread.value = Number(res.data?.unreadCount || 0); } catch { messages.value = []; } finally { loading.value = false; } }
async function read(item) {
  // D83-八批：点击消息=标记已读 + 查看详情 + 跳转对应数据（原来只标记已读，既不弹详情也不跳转）
  if (item.readStatus === 0) {
    try { await markNotificationRead(item.notificationId); } catch { /* 已读失败不阻塞查看 */ }
    item.readStatus = 1; unread.value = Math.max(0, unread.value - 1);
  }
  const target = resolveTarget(item);
  if (target) {
    visible.value = false;
    router.push(target);
  } else {
    showDetail(item);
  }
}

/**
 * 通知类型 → 跳转目标（relatedId 为业务单号/客户编码/线索编号）。
 *
 * <p>无映射的类型（如系统公告）走详情弹窗，不静默丢失。</p>
 */
const NOTIFY_TARGETS = {
  LEAD_RECYCLE_WARN: (id) => ({ path: '/lead/my', query: id ? { keyword: id } : undefined }),
  LEAD_RECYCLE_MANAGER_WARN: (id) => ({ path: '/lead/my', query: id ? { keyword: id } : undefined }),
  CLIENT_RECYCLE_WARN: (id) => ({ path: '/client/my', query: id ? { keyword: id } : undefined }),
  CLIENT_RECYCLE_MANAGER_WARN: (id) => ({ path: '/client/my', query: id ? { keyword: id } : undefined }),
  PRODUCT_APPROVAL: () => ({ path: '/approval/product' }),
  CONTENT_APPROVAL: () => ({ path: '/approval/mine' }),
  SERVICE_ORDER: (id) => ({ path: '/service-operations/daily', query: id ? { keyword: id } : undefined }),
  SYSTEM_NOTICE: () => ({ path: '/approval/sensitive-phone' }),
};
function resolveTarget(item) {
  const build = NOTIFY_TARGETS[item.type];
  return build ? build(item.relatedId) : null;
}
/** 无跳转映射时展示完整内容（列表只显示摘要，长内容被截断）。 */
function showDetail(item) {
  ElMessageBox.alert(
    `${item.content || '（无内容）'}\n\n时间：${item.createdAt || '—'}`,
    item.title || '消息详情',
    { confirmButtonText: '知道了', customClass: 'notification-detail-dialog' },
  ).catch(() => {});
}
async function markAll() { if (!unread.value) return; await markAllNotificationsRead(); messages.value.forEach((item) => { item.readStatus = 1; }); unread.value = 0; ElMessage.success('已全部标记为已读'); }
/** 单条删除：复用批量删除接口传单个 ID，未读消息删除后同步扣减角标。 */
async function removeOne(item) {
  try {
    await deleteNotifications([item.notificationId]);
    if (item.readStatus === 0) unread.value = Math.max(0, unread.value - 1);
    messages.value = messages.value.filter((m) => m.notificationId !== item.notificationId);
    ElMessage.success('已删除');
  } catch { /* 拦截器已提示 */ }
}
/** 清空全部消息（二次确认，防误触）。 */
async function clearAll() {
  try { await ElMessageBox.confirm('确定清空全部消息吗？清空后不可恢复。', '清空消息', { type: 'warning', confirmButtonText: '清空', cancelButtonText: '取消' }); }
  catch { return; }
  try {
    await deleteAllNotifications();
    messages.value = [];
    unread.value = 0;
    ElMessage.success('已清空全部消息');
  } catch { /* 拦截器已提示 */ }
}
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
