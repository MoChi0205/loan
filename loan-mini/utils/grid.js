/**
 * 快捷宫格列数计算（App 端唯一实现，与设计真源原型同源）。
 *
 * <p><b>为什么需要</b>：原实现把磁贴宽度写死成「4 列的 1/4」
 * （`calc((100% - 54rpx) / 4)`），于是 6 个磁贴排成 4+2、3 个磁贴右侧空一格，栅格参差。
 * 设计真源原型 `docs/prototypes/redesign-all-roles-v1.html` 改为「按磁贴数量在 2–4 列中取
 * 末行空位最少的方案」，本模块是该规则在 App 端的落地，保证两端同源。
 *
 * <p><b>取值</b>：1→1、2→2、3→3、4→4、5→3（3+2）、6→3（3+3）、7→4（4+3）、8→4（4+4）。
 *
 * <p><b>为什么不用 CSS Grid</b>：微信小程序端对 `display:grid` 支持不一致，
 * 沿用现有 flex 换行 + 计算宽度更稳，同时不产生横向滚动。
 *
 * @module utils/grid
 */

/** 允许的最小列数。 */
const MIN_COLS = 2;
/** 允许的最大列数（超过则磁贴过窄，点击目标不达标）。 */
const MAX_COLS = 4;

/**
 * 按磁贴数量取列数：在 2–4 列中选「末行空位最少」的方案，并列时取列数更多者。
 *
 * @param {number} count 磁贴数量
 * @returns {number} 列数；数量 ≤1 时返回 1
 */
export function gridCols(count) {
  const n = Number(count) || 0;
  if (n <= 1) return 1;
  let best = MAX_COLS;
  let bestDeficit = Number.MAX_SAFE_INTEGER;
  for (let c = MIN_COLS; c <= MAX_COLS; c += 1) {
    // 末行空位数：整除时为 0
    const deficit = (c - (n % c)) % c;
    if (deficit < bestDeficit || (deficit === bestDeficit && c > best)) {
      bestDeficit = deficit;
      best = c;
    }
  }
  return Math.min(best, n);
}

/**
 * 磁贴宽度（flex 换行布局下按列数等分并扣除列间距）。
 *
 * @param {number} count 磁贴数量
 * @param {number} [gapRpx=28] 列间距，须与容器 `gap` 一致（默认 28rpx，对齐设计真源原型 14px）
 * @returns {string} 可直接用于 `:style="{ width }"` 的宽度值
 */
export function gridItemWidth(count, gapRpx = 28) {
  const cols = gridCols(count);
  return `calc((100% - ${(cols - 1) * gapRpx}rpx) / ${cols})`;
}
