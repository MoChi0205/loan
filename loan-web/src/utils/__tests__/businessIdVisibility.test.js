import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

function source(path) {
  return readFileSync(resolve(process.cwd(), path), 'utf8');
}

describe('业务 ID 展示红线', () => {
  it('服务运营页只展示客户、员工和顾问名称', () => {
    const page = source('src/views/service/ServiceOperations.vue');
    expect(page).not.toMatch(/customerName\s*\|\|\s*row\.clientCode/);
    expect(page).not.toMatch(/staffName\s*\|\|\s*row\.staffCode/);
    expect(page).not.toMatch(/hostStaffName\s*\|\|\s*row\.hostStaffCode/);
    expect(page).not.toContain('{{ row.orderNo }}');
    expect(page).not.toContain('{{ selectedClient.clientCode }}');
  });

  it('客户回放将画像枚举转为中文且隐藏内部报告编号', () => {
    const page = source('src/views/service/ServiceOperations.vue');
    expect(page).toContain("OURS: '我司录入'");
    expect(page).toContain("ENTERPRISE: '企业客户'");
    expect(page).toContain("INSIGHT_GENERATED: '生成客户画像'");
    expect(page).toContain("FOLLOW_UP: '客户跟进'");
    expect(page).toContain("'reportNo'");
    expect(page).not.toContain("{{ item.eventType }}");
    expect(page).not.toContain("{{ item.actorType }}");
  });

  it('报告与列表组件不直接渲染业务 ID', () => {
    const report = source('src/components/report/StaffAggregatedReport.vue');
    const list = source('src/views/report/ScreeningReport.vue');
    expect(report).not.toContain('{{ detail.reportNo }}');
    expect(report).not.toContain('{{ profile.clientCode');
    expect(report).not.toContain('{{ profile.ownerStaffCode');
    expect(report).not.toContain('row.productName || row.productCode');
    expect(list).not.toContain('{{ row.clientProfileCode');
  });
});
