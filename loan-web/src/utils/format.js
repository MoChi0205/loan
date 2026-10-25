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
  if (isBlank(value)) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '-';
  const pad = (n) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

export function isBlank(value) {
  return value === null || value === undefined || (typeof value === 'string' && value.trim() === '');
}

/** 空值统一展示，避免页面散落 `|| '-'` 和不一致的破折号。 */
export function displayValue(value, fallback = '-') {
  return isBlank(value) ? fallback : String(value);
}

/** 相对日期展示：今天为 T+0，未来第 N 天为 T+N，过去为 T-N。 */
export function formatTPlus(value, base = new Date()) {
  if (isBlank(value)) return '-';
  const date = new Date(value);
  const anchor = new Date(base);
  if (Number.isNaN(date.getTime()) || Number.isNaN(anchor.getTime())) return '-';
  date.setHours(0, 0, 0, 0);
  anchor.setHours(0, 0, 0, 0);
  const days = Math.round((date.getTime() - anchor.getTime()) / 86400000);
  return `T${days >= 0 ? '+' : ''}${days}`;
}

/**
 * 手机号脱敏：138****5678。
 *
 * @param {string} phone 手机号
 * @returns {string} 脱敏后手机号，空值返回 '-'
 */
export function desensitizePhone(phone) {
  if (!phone || phone.length < 7) return phone || '-';
  // 后端列表接口已经完成脱敏时直接保留，避免二次格式化变成空值。
  if (/^\d{3}\*{4}\d{4}$/.test(String(phone))) return String(phone);
  // 防御：手机号明文才是 11 位纯数字；AES 密文/异常值不解密直接返 "-"，避免显示乱码
  if (!/^\d{11}$/.test(phone)) return '-';
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
 * <p>注意：命名为 statusLabel 而非 statusText，避免与各列表页局部状态字典对象
 * （const statusText = {...}）同名造成阅读混淆。
 *
 * @param {string} code 状态码
 * @param {Object} map 映射表 { code: 中文名 }
 * @returns {string} 中文名，未命中返回原码
 */
export function statusLabel(code, map) {
  return (map && map[code]) || code;
}
