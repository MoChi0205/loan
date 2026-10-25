export const validators = Object.freeze({
  required: (label = '该字段') => ({ required: true, message: `${label}不能为空`, trigger: ['blur', 'change'] }),
  maxLength: (max, label = '该字段') => ({
    validator: (_rule, value, callback) => String(value ?? '').length > max
      ? callback(new Error(`${label}不能超过 ${max} 个字符`)) : callback(),
    trigger: ['blur', 'change'],
  }),
  pattern: (pattern, message = '格式不正确') => ({ pattern, message, trigger: ['blur', 'change'] }),
});

export function normalizeText(value, max) {
  const text = String(value ?? '').trim();
  return max ? text.slice(0, max) : text;
}
