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
