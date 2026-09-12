/**
 * 站内消息中心接口（2026-09-11 审计 P0-2）。
 *
 * <p>契约对齐后端 {@code MiniNotificationController}（前缀 {@code /api/mini/notification}）：
 * <ul>
 *   <li>{@code GET  /api/mini/notification/mine?page=&size=} —— 我的消息
 *       {@code { records, total, unreadCount }}</li>
 *   <li>{@code GET  /api/mini/notification/mine/unread-count} —— 未读数（红点 / 角标）</li>
 *   <li>{@code POST /api/mini/notification/mine/read-all} —— 全部标记已读（打开列表后清红点）</li>
 * </ul>
 *
 * <p><b>为什么不用 {@code /api/notification/**}</b>：网关按 api_key 前缀放行用户类型，
 * 客户角色只认 {@code mini:}，既有 {@code notification:} 前缀对其不可达，故小程序统一走 mini 前缀。
 *
 * <p><b>合规（D68 / 02-红线 #7）</b>：{@code records} 每条只含
 * {@code { source, matter, time, createdAt, unread }}，不含任何业务单号。
 *
 * <p><b>失败策略</b>：一律 {@code showError:false}——消息中心属次要内容，
 * 拉取失败由弹层自行呈现「重新加载」，不弹全局 toast、不阻断页面。
 */
import { requestGet, requestPost } from './request';

/** 消息列表默认每页条数（弹层一次展示的量）。 */
const PAGE_SIZE = 20;

/**
 * 我的消息分页。
 *
 * @param {number} [page=1] 页码（从 1 开始）
 * @param {number} [size=20] 每页大小
 * @returns {Promise<{records:Array<Object>, total:number, unreadCount:number}>}
 *   records 每项为 {@code { source, matter, time, createdAt, unread }}
 */
export function myMessages(page = 1, size = PAGE_SIZE) {
  return requestGet('/api/mini/notification/mine', { page, size }, { showError: false });
}

/**
 * 未读数（驱动铃铛红点与「我的」角标）。
 *
 * @returns {Promise<number>} 未读条数
 */
export function unreadCount() {
  return requestGet('/api/mini/notification/mine/unread-count', {}, { showError: false });
}

/**
 * 全部标记已读。
 *
 * @returns {Promise<number>} 受影响行数
 */
export function markAllRead() {
  return requestPost('/api/mini/notification/mine/read-all', {}, { showError: false });
}
