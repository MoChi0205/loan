import request from '@/utils/request';

/** 工作台待办 */
export function dashboardTodo() {
  return request({ url: '/api/admin/dashboard/todo', method: 'get' });
}

/** 配置完成度 */
export function configStatus() {
  return request({ url: '/api/admin/config/status', method: 'get' });
}

/** 初筛执行 */
export function runScreening(data) {
  return request({ url: '/api/admin/screening/run', method: 'post', data });
}
