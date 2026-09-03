import { describe, expect, it } from 'vitest';
import {
  ACTION_PERMISSION,
  approvalActionState,
  availableOrderTransitions,
  defaultPermissionsForRole,
  hasActionPermission,
  isChannelUser,
} from '@/utils/access';

describe('Web 操作级权限矩阵', () => {
  it('部门经理可审批本团队分配并直接分配客户', () => {
    const permissions = defaultPermissionsForRole('DEPT_MANAGER');
    expect(hasActionPermission(permissions, ACTION_PERMISSION.ALLOCATION_AUDIT)).toBe(true);
    expect(hasActionPermission(permissions, ACTION_PERMISSION.CLIENT_ASSIGN)).toBe(true);
  });

  it('顾问可申请认领客户，但不能直接分配或审批', () => {
    const permissions = defaultPermissionsForRole('ADVISER');
    expect(hasActionPermission(permissions, ACTION_PERMISSION.CLIENT_CLAIM)).toBe(true);
    expect(hasActionPermission(permissions, ACTION_PERMISSION.CLIENT_ASSIGN)).toBe(false);
    expect(hasActionPermission(permissions, ACTION_PERMISSION.ALLOCATION_AUDIT)).toBe(false);
  });

  it('渠道可只读查看本人录入客户及报告，但不能进入公海或修改归属', () => {
    const permissions = defaultPermissionsForRole('CHANNEL');
    expect(hasActionPermission(permissions, ACTION_PERMISSION.LEAD_CREATE)).toBe(true);
    expect(hasActionPermission(permissions, ACTION_PERMISSION.CLIENT_OWN_VIEW)).toBe(true);
    expect(hasActionPermission(permissions, ACTION_PERMISSION.REPORT_OWN_VIEW)).toBe(true);
    expect(hasActionPermission(permissions, ACTION_PERMISSION.LEAD_CLAIM)).toBe(false);
    expect(hasActionPermission(permissions, ACTION_PERMISSION.CLIENT_POOL_VIEW)).toBe(false);
    expect(hasActionPermission(permissions, ACTION_PERMISSION.CLIENT_ASSIGN)).toBe(false);
    expect(hasActionPermission(permissions, ACTION_PERMISSION.CLIENT_UPDATE)).toBe(false);
    expect(hasActionPermission(permissions, ACTION_PERMISSION.CLIENT_SCREENING)).toBe(false);
    expect(isChannelUser({ userType: 'CHANNEL' })).toBe(true);
    expect(isChannelUser({ userType: 'STAFF' })).toBe(false);
  });
});

describe('审批与工单状态收口', () => {
  const all = ['*'];

  it('下载审批仅待审可审批、仅已通过且有效可作废', () => {
    expect(approvalActionState('download', { approveStatus: 'PENDING', voidFlag: 0 }, all))
      .toEqual({ canAudit: true, canVoid: false });
    expect(approvalActionState('download', { approveStatus: 'APPROVED', voidFlag: 0 }, all))
      .toEqual({ canAudit: false, canVoid: true });
    expect(approvalActionState('download', { approveStatus: 'APPROVED', voidFlag: 1 }, all))
      .toEqual({ canAudit: false, canVoid: false });
  });

  it('工单状态只暴露后端允许的迁移，终态无操作', () => {
    expect(availableOrderTransitions('NEW', all)).toEqual(['IN_SERVICE', 'CANCEL']);
    expect(availableOrderTransitions('IN_SERVICE', all)).toEqual(['DEAL', 'CANCEL']);
    expect(availableOrderTransitions('DEAL', all)).toEqual(['REFUND']);
    expect(availableOrderTransitions('REFUND', all)).toEqual([]);
    expect(availableOrderTransitions('NEW', [])).toEqual([]);
  });
});
