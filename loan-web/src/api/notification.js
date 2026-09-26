import request from '@/utils/request';
export function myNotifications(params = { page: 1, size: 20 }) { return request({ url: '/api/notification/mine', method: 'get', params, __loanSilent: true }); }
export function unreadCount() { return request({ url: '/api/notification/mine/unread-count', method: 'get', __loanSilent: true }); }
export function markNotificationRead(notificationId) { return request({ url: `/api/notification/${notificationId}/read`, method: 'post', __loanSilent: true }); }
export function markAllNotificationsRead() { return request({ url: '/api/notification/mine/read-all', method: 'post', __loanSilent: true }); }
