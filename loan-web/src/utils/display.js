import { desensitizePhone } from '@/utils/format';

/** 面向业务人员的客户标签：姓名主显，不暴露内部客户编码。 */
export function clientDisplayLabel(client) {
  if (!client) return '—';
  const name = client.enterpriseName || client.clientName || client.contactName || client.name || '未命名客户';
  const group = client.customerGroup === 'ENTERPRISE' ? '企业' : '个人';
  const phone = client.phone ? desensitizePhone(client.phone) : '';
  return [name, group, phone].filter(Boolean).join(' · ');
}

/** 人员选择标签：姓名与部门属于业务信息，内部工号仅作为 option value。 */
export function staffDisplayLabel(staff) {
  if (!staff) return '—';
  return [staff.staffName || '姓名待补充', staff.deptName].filter(Boolean).join(' · ');
}

/** 产品选择标签：名称主显，使用机构/客群帮助区分重名产品。 */
export function productDisplayLabel(product) {
  if (!product) return '—';
  const group = product.customerGroup === 'ENTERPRISE' ? '企业' : product.customerGroup === 'PERSONAL' ? '个人' : '';
  return [product.productName || '未命名产品', product.bankName || product.institutionName, group]
    .filter(Boolean).join(' · ');
}

/** 报告标题：面向业务人员仅显示客户与生成日期，业务编码只留作内部定位。 */
export function reportDisplayTitle(report) {
  if (!report) return '报告';
  const client = report.clientName || report.enterpriseName || report.entName || report.contactName || '客户待补充';
  const parts = String(report.createdAt || report.generatedAt || '').slice(0, 10).split('-');
  const date = parts.length === 3 ? `${parts[0]}年${parts[1]}月${parts[2]}日` : '日期待补充';
  return `【${client}】【${date}】`;
}

/** 审批事项：不以审批号作为可见标题。 */
export function approvalMatter(row, kind) {
  if (!row) return '业务审批';
  if (kind === 'product') return `产品${row.applyType === 'DELETE' ? '删除' : '录入'}：${row.bankProductName || '产品名称待补充'}`;
  if (kind === 'download') return `资料下载：${row.purpose || '用途待补充'}`;
  if (kind === 'allocation') return `客户归属：${row.entName || row.contactName || '客户姓名待补充'}`;
  if (kind === 'channelLead') return `渠道线索：${row.contactName || row.entName || '客户姓名待补充'}`;
  return '业务审批';
}
