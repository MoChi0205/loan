/**
 * 手机号脱敏（与 Web 端 desensitizePhone 同口径）。
 *
 * 规则：
 *  - 空 / 过短 → 返回原值或 '-'
 *  - 已是「前三后四」脱敏态 → 原样保留（后端列表接口通常已脱敏）
 *  - 11 位纯数字明文 → 脱敏为 `前3****后4`
 *  - 其它（AES 密文 / 异常值）→ 返回 '-'，避免显示乱码
 *
 * @param {string} phone 手机号
 * @returns {string} 脱敏后手机号
 */
export function maskPhone(phone) {
  if (!phone || phone.length < 7) return phone || '-';
  if (/^\d{3}\*{4}\d{4}$/.test(String(phone))) return String(phone);
  if (!/^\d{11}$/.test(phone)) return '-';
  return `${phone.slice(0, 3)}****${phone.slice(-4)}`;
}
