#!/usr/bin/env node
/**
 * loan-mini 图标 v5 生成脚本 —— 冷玻璃·线性（Linear Glass）。
 *
 * 设计规范（v5）：
 * - 字形：全部手写 SVG path，24×24 设计网格，stroke 线性（非实心色块），
 *   线宽 1.75（≈10.5px@144），round cap / round join，安全边距 3u。
 * - 磁贴态（name.png）：圆形冷玻璃底（浅蓝渐变 + 顶部高光 + 细白边 +
 *   品牌色柔投影）+ 品牌窄幅渐变字形 #2443C2→#3D63E0（135°）。
 *   用于内容功能入口（认证卡、首页快捷功能、数据卡、菜单行）。
 * - 单色态（name-<key>.png）：同一字形纯色渲染，无底。
 *   key：gray(#5B6678) / brand(#2443C2) / gold(#FFB020) / success(#11A86B)
 *        / info(#0EA8BE) / white(#FFFFFF)。
 *
 * 渲染链路：字形 SVG → resvg-js 栅格化 144×144 透明 PNG → static/icons/。
 * （微信小程序 WXML 不支持内联 SVG，故构建期栅格化；字形源码在本脚本内，
 *   即"可编辑矢量真源"，改形状/配色改本脚本后重跑。）
 *
 * 运行（resvg-js 安装在 WorkBuddy 托管 node workspace）：
 *   NODE_PATH=/Users/admin/.workbuddy/binaries/node/workspace/node_modules \
 *     /Users/admin/.workbuddy/binaries/node/versions/22.22.2/bin/node loan-mini/scripts/gen-icons-v5.mjs
 * 产物：覆盖 loan-mini/static/icons/*.png，并重建 preview.html。
 */
import { fileURLToPath } from 'node:url';
import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const OUT_DIR = path.resolve(__dirname, '..', 'static', 'icons');

// resvg-js 安装于 WorkBuddy 托管 node workspace；也支持项目内 node_modules
const require = createRequire(import.meta.url);
const CANDIDATES = [
  '@resvg/resvg-js',
  '/Users/admin/.workbuddy/binaries/node/workspace/node_modules/@resvg/resvg-js/index.js',
];
let Resvg;
for (const c of CANDIDATES) {
  try { Resvg = require(c).Resvg; break; } catch { /* try next */ }
}
if (!Resvg) throw new Error('未找到 @resvg/resvg-js，请先安装（见脚本头注释）');

/* ---------------- 调色板（与 App.vue 令牌对齐） ---------------- */
const BRAND_DEEP = '#2443C2';
const BRAND_MID = '#3D63E0';
const GRAY = '#5B6678';
const GOLD = '#FFB020';
const SUCCESS = '#11A86B';
const INFO = '#0EA8BE';
const WHITE = '#FFFFFF';

/* ---------------- 字形注册表（24×24 网格，stroke 线性） ----------------
 * 约定：默认继承外层 <g> 的 stroke 设置；fill="none"。
 * 特殊 fill 点（如气泡眼睛、列表圆点）显式标注 fill。
 */
const GLYPHS = {
  home: `
    <path d="M4 11.2 L12 4.4 L20 11.2"/>
    <path d="M6.4 9.6 V18.4 a1.6 1.6 0 0 0 1.6 1.6 H16 a1.6 1.6 0 0 0 1.6 -1.6 V9.6"/>
    <path d="M10.2 20 V14.6 H13.8 V20"/>`,
  match: `
    <circle cx="9.2" cy="12" r="4.6"/>
    <circle cx="14.8" cy="12" r="4.6"/>`,
  chart: `
    <path d="M4.6 19.8 H19.4"/>
    <path d="M7.4 16.6 V11.4"/>
    <path d="M12 16.6 V6.8"/>
    <path d="M16.6 16.6 V9.6"/>`,
  order: `
    <rect x="5.8" y="3.6" width="12.4" height="16.8" rx="2"/>
    <path d="M9.4 11.8 L11.4 13.8 L14.9 9.9"/>`,
  bank: `
    <path d="M4.2 9.2 L12 4.2 L19.8 9.2"/>
    <path d="M6 9.4 H18"/>
    <path d="M7.2 12.4 V16.6"/>
    <path d="M12 12.4 V16.6"/>
    <path d="M16.8 12.4 V16.6"/>
    <path d="M4.8 19.6 H19.2"/>`,
  users: `
    <circle cx="9.4" cy="8.4" r="3.1"/>
    <path d="M4.4 19.4 c0 -3.2 2.2 -5.2 5 -5.2 s5 2 5 5.2"/>
    <path d="M15.9 5.6 a3.1 3.1 0 0 1 0 5.6"/>
    <path d="M17.4 14.5 c2 .7 3.2 2.4 3.2 4.9"/>`,
  mine: `
    <circle cx="12" cy="8" r="3.6"/>
    <path d="M5.4 20 c0 -3.8 3 -6.2 6.6 -6.2 s6.6 2.4 6.6 6.2"/>`,
  person: `
    <circle cx="12" cy="7.6" r="3.2"/>
    <path d="M5.6 19.8 c0 -3.5 2.9 -5.7 6.4 -5.7 s6.4 2.2 6.4 5.7"/>
    <path d="M9.8 14.9 L11.4 16.4 L14.5 13"/>`,
  check: `
    <path d="M4.8 12.6 L9.8 17.6 L19.2 6.9"/>`,
  search: `
    <circle cx="11" cy="11" r="6.2"/>
    <path d="M15.6 15.6 L19.8 19.8"/>`,
  wechat: `
    <path d="M12 4.8 c4.6 0 8 2.7 8 6.2 s-3.4 6.2 -8 6.2 c-.9 0 -1.8 -.1 -2.6 -.3 L6 19.2 l.7 -3.1 C5.2 15 4 13.6 4 11 c0 -3.5 3.4 -6.2 8 -6.2 Z"/>
    <circle cx="9.4" cy="11" r="1.1" fill="currentColor" stroke="none"/>
    <circle cx="14.6" cy="11" r="1.1" fill="currentColor" stroke="none"/>`,
  bolt: `
    <path d="M13.2 3.6 L6.6 13.4 H11 L10.8 20.4 L17.4 10.6 H13 Z"/>`,
  support: `
    <path d="M4.4 13.2 a7.6 7.6 0 0 1 15.2 0"/>
    <rect x="3.4" y="12.6" width="3.4" height="6" rx="1.7"/>
    <rect x="17.2" y="12.6" width="3.4" height="6" rx="1.7"/>
    <path d="M18.9 18.8 v.6 a2.2 2.2 0 0 1 -2.2 2.2 h-2.4"/>`,
  list: `
    <circle cx="5.2" cy="6.8" r="1.1" fill="currentColor" stroke="none"/>
    <circle cx="5.2" cy="12" r="1.1" fill="currentColor" stroke="none"/>
    <circle cx="5.2" cy="17.2" r="1.1" fill="currentColor" stroke="none"/>
    <path d="M9.4 6.8 H19"/>
    <path d="M9.4 12 H19"/>
    <path d="M9.4 17.2 H19"/>`,
  share: `
    <circle cx="6.2" cy="12" r="2.6"/>
    <circle cx="17.4" cy="5.8" r="2.6"/>
    <circle cx="17.4" cy="18.2" r="2.6"/>
    <path d="M8.5 10.7 L15.1 7.1"/>
    <path d="M8.5 13.3 L15.1 16.9"/>`,
  enterprise: `
    <rect x="4.6" y="4.2" width="10.4" height="15.8" rx="1.4"/>
    <rect x="15" y="9.4" width="4.6" height="10.6" rx="1.2"/>
    <path d="M8 8 H9.2 M11 8 H12.2 M8 11.4 H9.2 M11 11.4 H12.2 M8 14.8 H9.2 M11 14.8 H12.2"/>
    <path d="M17 13 H17.8 M17 16 H17.8"/>
    <path d="M3.4 20 H20.6"/>`,
  refresh: `
    <path d="M19.4 12 a7.4 7.4 0 1 1 -2.16 -5.24"/>
    <path d="M17.6 2.9 V7 H13.5"/>`,
  alert: `
    <path d="M12 4.2 L20.8 19.4 a.9 .9 0 0 1 -.8 1.4 H4 a.9 .9 0 0 1 -.8 -1.4 Z"/>
    <path d="M12 10.2 V14.6"/>
    <circle cx="12" cy="17.4" r="1" fill="currentColor" stroke="none"/>`,
  trend: `
    <path d="M4 17 L9.4 11.4 L13.2 14.8 L20 7"/>
    <path d="M15.8 6.6 H20.4 V11.2"/>`,
  doc: `
    <path d="M14 3.6 H8 a1.8 1.8 0 0 0 -1.8 1.8 v13.2 A1.8 1.8 0 0 0 8 20.4 h8 a1.8 1.8 0 0 0 1.8 -1.8 V7.4 Z"/>
    <path d="M13.8 3.6 V7.6 H17.8"/>
    <path d="M9.4 12.4 H14.6"/>
    <path d="M9.4 15.8 H14.6"/>`,
  file: `
    <rect x="5.4" y="4.6" width="13.2" height="15.8" rx="2"/>
    <rect x="9.4" y="2.9" width="5.2" height="3.4" rx="1.2"/>
    <path d="M9 11.2 H15"/>
    <path d="M9 14.6 H15"/>
    <path d="M9.4 18 L10.8 19.2 L13.8 16.4"/>`,
  photo: `
    <rect x="3.8" y="7" width="16.4" height="12.2" rx="2.4"/>
    <path d="M8.8 7 L10 4.4 H14 L15.2 7"/>
    <circle cx="12" cy="13" r="3.2"/>`,
  lock: `
    <rect x="5.4" y="10.6" width="13.2" height="9.8" rx="2.4"/>
    <path d="M8.4 10.4 V7.6 a3.6 3.6 0 0 1 7.2 0 V10.4"/>
    <circle cx="12" cy="14.8" r="1.4"/>
    <path d="M12 16.2 V17.6"/>`,
  arrow: `
    <path d="M4.4 12 H19"/>
    <path d="M13.6 6.6 L19 12 L13.6 17.4"/>`,
};

/* ---------------- 资源矩阵 ----------------
 * 磁贴：全部字形（内容功能入口通用）。
 * 单色：gray + brand 全量（任意 <AppIcon color> 组合不裂图），
 *       语义色按需 + 深底 white 集（与页面实际用法对齐）。
 */
const TILES = Object.keys(GLYPHS);
const PLAIN_KEYS_ALL = ['gray', 'brand'];
const PLAIN_EXTRA = [
  ['gold', ['bank', 'match', 'chart', 'bolt']],
  ['success', ['order', 'users', 'check']],
  ['info', ['mine', 'match', 'home', 'chart', 'order', 'bank', 'users', 'support']],
  ['white', ['lock', 'wechat', 'arrow', 'check', 'search', 'share', 'bolt']],
];

/* ---------------- SVG 组装 ---------------- */

/** 字形标记：24 网格线性 stroke，颜色由调用方传入 */
function glyphMarkup(d, paint) {
  return `<g fill="none" stroke="${paint}" stroke-width="1.75"
    stroke-linecap="round" stroke-linejoin="round"
    color="${paint}">${d}</g>`;
}

/** 磁贴完整 SVG（144 画布）：玻璃圆底 + 渐变字形 */
function tileSvg(d) {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="144" height="144" viewBox="0 0 144 144">
  <defs>
    <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="#F3F6FD"/>
      <stop offset="1" stop-color="#DDE4F4"/>
    </linearGradient>
    <linearGradient id="fg" x1="39.6" y1="39.6" x2="104.4" y2="104.4" gradientUnits="userSpaceOnUse">
      <!-- userSpaceOnUse：objectBoundingBox 对纯水平/垂直线（bbox 零宽高）会退化不渲染 -->
      <stop offset="0" stop-color="${BRAND_DEEP}"/>
      <stop offset="1" stop-color="${BRAND_MID}"/>
    </linearGradient>
    <radialGradient id="hi" cx="0.34" cy="0.26" r="0.9">
      <stop offset="0" stop-color="#FFFFFF" stop-opacity="0.9"/>
      <stop offset="0.55" stop-color="#FFFFFF" stop-opacity="0"/>
    </radialGradient>
    <filter id="ds" x="-20%" y="-20%" width="140%" height="140%">
      <feDropShadow dx="0" dy="2.5" stdDeviation="3.2" flood-color="${BRAND_DEEP}" flood-opacity="0.16"/>
    </filter>
  </defs>
  <circle cx="72" cy="72" r="56" fill="url(#bg)" filter="url(#ds)"/>
  <circle cx="72" cy="72" r="55" fill="url(#hi)"/>
  <circle cx="72" cy="72" r="54.6" fill="none" stroke="#FFFFFF" stroke-opacity="0.75" stroke-width="1.6"/>
  <g transform="translate(39.6 39.6) scale(2.7)">${glyphMarkup(d, 'url(#fg)')}</g>
</svg>`;
}

/** 单色完整 SVG（144 画布，内容区留 6px 边距）：纯色线性字形 */
function plainSvg(d, color) {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="144" height="144" viewBox="0 0 144 144">
  <g transform="translate(7.2 7.2) scale(5.4)">${glyphMarkup(d, color)}</g>
</svg>`;
}

/** 渲染 SVG → PNG buffer */
function render(svg, size = 144) {
  const r = new Resvg(svg, { fitTo: { mode: 'width', value: size } });
  return r.render().asPng();
}

/* ---------------- 主流程 ---------------- */

function main() {
  fs.mkdirSync(OUT_DIR, { recursive: true });
  // 清理旧 PNG（保留 preview.html，最后重建）
  for (const f of fs.readdirSync(OUT_DIR)) {
    if (f.endsWith('.png')) fs.unlinkSync(path.join(OUT_DIR, f));
  }

  const made = [];
  for (const name of TILES) {
    const file = path.join(OUT_DIR, `${name}.png`);
    fs.writeFileSync(file, render(tileSvg(GLYPHS[name])));
    made.push(name);
  }
  for (const name of TILES) {
    for (const key of PLAIN_KEYS_ALL) {
      const color = { gray: GRAY, brand: BRAND_DEEP }[key];
      fs.writeFileSync(
        path.join(OUT_DIR, `${name}-${key}.png`),
        render(plainSvg(GLYPHS[name], color)),
      );
      made.push(`${name}-${key}`);
    }
  }
  for (const [key, names] of PLAIN_EXTRA) {
    const color = { gold: GOLD, success: SUCCESS, info: INFO, white: WHITE }[key];
    for (const name of names) {
      fs.writeFileSync(
        path.join(OUT_DIR, `${name}-${key}.png`),
        render(plainSvg(GLYPHS[name], color)),
      );
      made.push(`${name}-${key}`);
    }
  }

  // preview.html：白/浅灰双底对照 + 磁贴与单色全量
  const tilesHtml = TILES.map((n) => chip(n, `${n}.png`)).join('');
  const plainsHtml = made
    .filter((m) => m.includes('-'))
    .map((n) => chip(n, `${n}.png`)).join('');
  const html = `<!DOCTYPE html>
<html lang="zh-CN"><head><meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>loan-mini 图标 v5 · 冷玻璃线性</title>
<style>
  body{font-family:-apple-system,sans-serif;background:#F8FAFC;margin:0;padding:24px;color:#1A2336}
  h1{font-size:18px} h2{font-size:14px;margin:28px 0 10px;color:#5B6678}
  .row{display:flex;flex-wrap:wrap;gap:10px}
  .cell{width:96px;text-align:center}
  .cell img{width:64px;height:64px;display:block;margin:0 auto}
  .cell span{font-size:10px;color:#5B6678;word-break:break-all}
  .dark{background:#17216B;padding:14px;border-radius:12px;margin-top:6px}
  .dark .cell span{color:#B9C4E2}
  .phone{width:375px;border-radius:24px;background:#FFF;border:1px solid #E5EAF3;padding:16px;margin-top:8px}
  .menu{display:flex;align-items:center;gap:12px;padding:10px 0}
  .menu b{font-size:14px}.menu i{font-size:11px;color:#9AA4B4;font-style:normal}
</style></head><body>
<h1>loan-mini 图标 v5 · 冷玻璃·线性（144px @24grid / stroke 1.75 round）</h1>
<h2>磁贴态（功能入口）</h2><div class="row">${tilesHtml}</div>
<h2>单色态（全量）</h2><div class="row">${plainsHtml}</div>
<h2>深底对照（white 档）</h2><div class="dark row">
  ${['lock', 'wechat', 'arrow', 'check', 'search', 'share'].map((n) => chip(n, `${n}-white.png`)).join('')}
</div>
<h2>页面场景模拟（72rpx 菜单行 / 88rpx TabBar）</h2>
<div class="phone">
  ${[['order', '我的服务单', '查看服务进度与跟进摘要'], ['bank', '我的产品', '录入 / 撤销提交 / 申请删除'], ['check', '审核中心', '无归宿客户分配申请']].map(([n, t, s]) => `
  <div class="menu"><img src="${n}.png" width="36" height="36" style="flex-shrink:0"><div><b>${t}</b><br><i>${s}</i></div></div>`).join('')}
  <div style="display:flex;border-top:1px solid #EEF1F7;padding-top:10px;margin-top:6px">
    ${[['home', 'brand'], ['match', 'gray'], ['chart', 'gray'], ['order', 'gray'], ['mine', 'gray']].map(([n, k]) => `
    <div style="flex:1;text-align:center"><img src="${n}-${k}.png" width="24" height="24"><div style="font-size:10px;color:${k === 'brand' ? '#2443C2' : '#5B6678'}">${n}</div></div>`).join('')}
  </div>
</div>
</body></html>`;
  fs.writeFileSync(path.join(OUT_DIR, 'preview.html'), html);

  console.log(`v5 done: ${made.length} PNG + preview.html -> ${OUT_DIR}`);
}

function chip(label, file) {
  return `<div class="cell"><img src="${file}"><span>${label}</span></div>`;
}

main();
