import { describe, expect, it } from 'vitest';
import { normalizeApprovalItem } from '../approval';

describe('统一审批记录适配', () => {
  it('材料复核记录将 reviewNo 适配为统一审核动作编号', () => {
    const item = normalizeApprovalItem({
      type: 'MATERIAL_REVIEW',
      reviewNo: 'matrev-001',
      reportNo: 'report-001',
    });

    expect(item).toMatchObject({
      type: 'MATERIAL_REVIEW',
      reviewNo: 'matrev-001',
      approvalNo: 'matrev-001',
    });
  });

  it('保留普通审批记录已有的 approvalNo', () => {
    expect(normalizeApprovalItem({ type: 'ALLOCATION', approvalNo: 'alloc-001' }))
      .toMatchObject({ approvalNo: 'alloc-001' });
  });

  it('保留产品和下载审批的可理解展示字段', () => {
    expect(normalizeApprovalItem({
      type: 'PRODUCT', approvalNo: 'product-001', bankProductName: '经营贷',
    })).toMatchObject({ approvalNo: 'product-001', bankProductName: '经营贷' });
    expect(normalizeApprovalItem({
      type: 'DOWNLOAD', approvalNo: 'download-001', applicantStaffName: '张三', purpose: '尽调',
    })).toMatchObject({ approvalNo: 'download-001', applicantStaffName: '张三', purpose: '尽调' });
  });
});
