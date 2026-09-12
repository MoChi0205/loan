import { describe, expect, it } from 'vitest';
import { gridCols, gridItemWidth } from '../grid';

describe('快捷宫格列数（与设计真源原型同源）', () => {
  it('数量 ≤4 时按实际数量分列，右侧不留空', () => {
    expect(gridCols(1)).toBe(1);
    expect(gridCols(2)).toBe(2);
    expect(gridCols(3)).toBe(3);
    expect(gridCols(4)).toBe(4);
  });

  it('数量 >4 时取「末行空位最少」的列数', () => {
    // 5 个：3 列 → 3+2（空 1），优于 2 列 3 行 / 4 列 4+1（空 3）
    expect(gridCols(5)).toBe(3);
    // 6 个：3 列 → 3+3（整除），优于 4 列 4+2
    expect(gridCols(6)).toBe(3);
    // 7 个：4 列 → 4+3（空 1）
    expect(gridCols(7)).toBe(4);
    // 8 个：4 列 → 4+4（整除）
    expect(gridCols(8)).toBe(4);
  });

  it('非法/空数量兜底为 1 列，不抛错', () => {
    expect(gridCols(0)).toBe(1);
    expect(gridCols(undefined)).toBe(1);
    expect(gridCols(null)).toBe(1);
    expect(gridCols('abc')).toBe(1);
  });

  it('数量较大时仍在 2–4 列内收敛，且优先整除方案', () => {
    // 9 个：3 列 → 3+3+3（整除），优于 4 列 4+4+1
    expect(gridCols(9)).toBe(3);
    // 12 个：4 列 → 4+4+4（整除；并列时取更多列）
    expect(gridCols(12)).toBe(4);
    // 列数永不超过 4
    expect(gridCols(20)).toBeLessThanOrEqual(4);
    expect(gridCols(20)).toBeGreaterThanOrEqual(2);
  });

  it('宽度按列数等分并扣除列间距（rpx）', () => {
    expect(gridItemWidth(6, 18)).toBe('calc((100% - 36rpx) / 3)');
    expect(gridItemWidth(3, 18)).toBe('calc((100% - 36rpx) / 3)');
    expect(gridItemWidth(4, 18)).toBe('calc((100% - 54rpx) / 4)');
    expect(gridItemWidth(1, 18)).toBe('calc((100% - 0rpx) / 1)');
  });

  it('默认列间距与首页 .m-grid 的 gap 一致（28rpx）', () => {
    expect(gridItemWidth(3)).toBe('calc((100% - 56rpx) / 3)');
    expect(gridItemWidth(6)).toBe('calc((100% - 56rpx) / 3)');
    expect(gridItemWidth(4)).toBe('calc((100% - 84rpx) / 4)');
  });
});
