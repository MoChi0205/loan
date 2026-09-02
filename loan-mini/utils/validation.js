/** 跨页面复用的表单校验规则。 */

/** 统一社会信用代码：18 位数字或大写字母（排除 I/O/S/V/Z）。 */
export function isUnifiedSocialCreditCode(value) {
  return /^[0-9A-HJ-NPQRTUWXY]{18}$/.test(String(value || '').trim().toUpperCase());
}

/** 中国居民身份证号的基础格式校验。 */
export function isIdCardNo(value) {
  return /^\d{17}[\dX]$/.test(String(value || '').trim().toUpperCase());
}

/** 中国大陆手机号基础格式校验。 */
export function isMobile(value) {
  return /^1\d{10}$/.test(String(value || '').trim());
}
