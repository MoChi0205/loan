/**
 * 首页统计行接口（我的客户 / 团队成员 / 全司客户 等）。
 *
 * <p>契约：`GET /api/mini/dashboard/stats`。服务端按 `userType/roleCode/deptCode`
 * 收敛数据范围，**只返回当前角色允许的指标键**（避免越权暴露其它范围数据）：
 * - ADVISER：clientCount（我的客户）/ orderCount（待跟进）
 * - DEPT_MANAGER：teamMemberCount（团队成员）/ unassignedCount（待分配）
 * - OPERATOR：clientCount / pendingApprovalCount（待我审批）
 * - BOSS：orgClientCount（全司客户）/ pendingApprovalCount（在途审批）
 * - SUPER_ADMIN / SUPER：orgClientCount / systemErrorCount（系统异常）
 * - CHANNEL：leadCount（我录线索）/ convertedCount（已转化）
 * - CUSTOMER：空对象（无统计行）
 *
 * <p>返回值不含任何业务单号（D68）。标签与展示形态由 `utils/roles.js` 定义。
 */
import { requestGet } from './request';

/**
 * 拉取首页统计行指标。
 *
 * @returns {Promise<Object>} 指标键值（按角色不同）
 */
export function homeStats() {
  // 统计失败不弹错误 toast：统计行可降级为占位，不阻断首页其它内容。
  return requestGet('/api/mini/dashboard/stats', {}, { showError: false });
}
