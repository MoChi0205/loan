/**
 * 公共格式化工具类（前端 utils，统一提取避免重复）。
 */

/**
 * 格式化日期时间：yyyy-MM-dd HH:mm:ss。
 *
 * @param {string|number|Date} value 时间值
 * @returns {string} 格式化后字符串，空值返回 '-'
 */
export function formatDateTime(value) {
  if (!value) return '-';
  const date = new Date(value);
  const pad = (n) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

/**
 * 手机号脱敏：138****5678。
 *
 * @param {string} phone 手机号
 * @returns {string} 脱敏后手机号，空值返回 '-'
 */
export function desensitizePhone(phone) {
  if (!phone || phone.length < 7) return phone || '-';
  return `${phone.slice(0, 3)}****${phone.slice(-4)}`;
}

/**
 * 金额格式化：千分位 + 保留两位小数（¥）。
 *
 * @param {number|string} amount 金额
 * @returns {string} 格式化后金额
 */
export function formatMoney(amount) {
  if (amount === null || amount === undefined || amount === '') return '-';
  const num = Number(amount);
  return `¥${num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

/**
 * 状态字典转换：按映射表取中文名。
 *
 * @param {string} code 状态码
 * @param {Object} map 映射表 { code: 中文名 }
 * @returns {string} 中文名，未命中返回原码
 */
export function statusText(code, map) {
  return (map && map[code]) || code;
}
