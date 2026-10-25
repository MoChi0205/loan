import { describe, expect, it } from 'vitest';
import { displayValue, formatTPlus } from '@/utils/format';

describe('公共展示格式', () => {
  it('统一空值与 T+N 日期', () => {
    expect(displayValue(null)).toBe('-');
    expect(displayValue('  ')).toBe('-');
    expect(formatTPlus('2026-11-02', new Date('2026-10-25T12:00:00'))).toBe('T+8');
    expect(formatTPlus('bad')).toBe('-');
  });
});
import { enumLabel } from '../enumDisplay';

describe('业务枚举展示', () => {
  it('显示已定义的中文释义', () => {
    expect(enumLabel({ NEW: '新客户' }, 'NEW')).toBe('新客户');
  });
  it('未知编码不泄露给业务页面', () => {
    expect(enumLabel({}, 'INTERNAL_UNKNOWN')).toBe('待确认');
    expect(enumLabel({}, 'INTERNAL_UNKNOWN', '状态待补充')).toBe('状态待补充');
  });
  it('保留中文业务值并用短横线表示空值', () => {
    expect(enumLabel({}, '已来访')).toBe('已来访');
    expect(enumLabel({}, null)).toBe('—');
  });
});
