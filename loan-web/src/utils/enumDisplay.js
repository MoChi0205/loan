/** 业务展示兜底必须是中文，内部编码只用于查询和路由。 */
export function enumLabel(mapping, code, fallback = '待确认') {
  if (code === null || code === undefined || code === '') return '—';
  if (mapping?.[code]) return mapping[code];
  return /[\u4e00-\u9fff]/.test(String(code)) ? String(code) : fallback;
}
