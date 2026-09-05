import { ElMessageBox } from 'element-plus';

/**
 * 统一确认弹窗工具（替代散落的 ElMessageBox.confirm 调用）。
 *
 * 用法：
 *   try {
 *     await appConfirm('确认删除该产品？');
 *     // 用户点了确定
 *   } catch {
 *     // 用户点了取消
 *   }
 *
 *   // 自定义按钮文案与类型
 *   await appConfirm('确认提交审核？', '提交确认', { type: 'warning', confirmButtonText: '提交' });
 */
export function appConfirm(message, title = '操作确认', options = {}) {
  return ElMessageBox.confirm(message, title, {
    type: 'warning',
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    ...options,
  });
}

/**
 * 统一告警/提示弹窗。
 */
export function appAlert(message, title = '提示', options = {}) {
  return ElMessageBox.alert(message, title, {
    confirmButtonText: '我知道了',
    ...options,
  });
}
