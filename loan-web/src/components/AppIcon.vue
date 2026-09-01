<template>
  <span
    class="app-icon"
    :style="{ width: size + 'px', height: size + 'px', color }"
    role="img"
    :aria-label="name"
  >
    <svg
      v-if="ICONS[name]"
      viewBox="0 0 24 24"
      width="100%"
      height="100%"
      fill="none"
      stroke="currentColor"
      stroke-width="1.7"
      stroke-linecap="round"
      stroke-linejoin="round"
    >
      <template v-for="(p, i) in ICONS[name]" :key="i">
        <path v-if="p.k === 'path'" :d="p.d" />
        <circle v-else-if="p.k === 'circle'" :cx="p.cx" :cy="p.cy" :r="p.r" />
        <rect v-else-if="p.k === 'rect'" :x="p.x" :y="p.y" :width="p.w" :height="p.h" :rx="p.rx" />
        <line v-else-if="p.k === 'line'" :x1="p.x1" :y1="p.y1" :x2="p.x2" :y2="p.y2" />
      </template>
    </svg>
  </span>
</template>

<script setup>
/**
 * 图标组件（Web 端集中式 SVG registry，T13/D28）。
 *
 * <p>统一 24×24 viewBox + 线性描边风格（stroke=currentColor），消除 Layout.vue 内联 SVG
 * 散落、重复与 scoped CSS 尺寸失效问题；语义与小程序 AppIcon（match/chart/bank/order/...）对齐。
 *
 * 用法：
 *   <AppIcon name="workbench" :size="18" />
 *   <AppIcon name="lead" :size="16" color="var(--loan-primary)" />
 */
defineProps({
  /** 图标名（见 ICONS 键） */
  name: { type: String, required: true },
  /** 尺寸 px（默认 18，与侧栏子项一致） */
  size: { type: Number, default: 18 },
  /** 颜色，默认继承 currentColor */
  color: { type: String, default: 'currentColor' },
});

/** 集中式图标 registry：元素语法 {k: path|circle|rect|line, ...} */
const ICONS = {
  /** 工作台：田字格（原"我的工作台"同款） */
  workbench: [
    { k: 'rect', x: 3, y: 3, w: 7, h: 7, rx: 1.5 },
    { k: 'rect', x: 14, y: 3, w: 7, h: 7, rx: 1.5 },
    { k: 'rect', x: 3, y: 14, w: 7, h: 7, rx: 1.5 },
    { k: 'rect', x: 14, y: 14, w: 7, h: 7, rx: 1.5 },
  ],
  /** 线索：漏斗/发射 */
  lead: [{ k: 'path', d: 'M4 20V10' }, { k: 'path', d: 'M10 20V4' }, { k: 'path', d: 'M16 20v-7' }, { k: 'path', d: 'M22 20H2' }],
  /** 客户：单人+档案角 */
  client: [
    { k: 'path', d: 'M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2' },
    { k: 'circle', cx: 12, cy: 7, r: 4 },
    { k: 'path', d: 'M16 3h4v4' },
  ],
  /** 初筛：播放/漏斗 */
  screening: [{ k: 'path', d: 'M5 3l14 9-14 9V3z' }],
  /** 规则库：文档+行 */
  ruleTemplate: [{ k: 'rect', x: 4, y: 3, w: 16, h: 18, rx: 2 }, { k: 'path', d: 'M8 8h8' }, { k: 'path', d: 'M8 12h8' }, { k: 'path', d: 'M8 16h5' }],
  /** 执行计划：列表+对勾 */
  plan: [{ k: 'path', d: 'M4 6h16' }, { k: 'path', d: 'M4 12h16' }, { k: 'path', d: 'M4 18h10' }, { k: 'path', d: 'M18 15l2 2 4-4' }],
  /** 策略方案：盾牌+对勾 */
  strategy: [{ k: 'path', d: 'M12 2l8 4v6c0 5-3.4 8.6-8 10-4.6-1.4-8-5-8-10V6l8-4z' }, { k: 'path', d: 'M9 12l2 2 4-4' }],
  /** 规则集：三线 */
  rule: [{ k: 'path', d: 'M8 6h13' }, { k: 'path', d: 'M8 12h13' }, { k: 'path', d: 'M8 18h13' }, { k: 'path', d: 'M3 6h.01' }, { k: 'path', d: 'M3 12h.01' }, { k: 'path', d: 'M3 18h.01' }],
  /** 工单：单据（与小程序 order 语义对齐） */
  order: [
    { k: 'path', d: 'M9 3h6v4H9z' },
    { k: 'path', d: 'M7 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V5a2 2 0 0 0-2-2h-2' },
    { k: 'path', d: 'M9 12h6' },
    { k: 'path', d: 'M9 16h4' },
  ],
  /** 审批：勾选+框 */
  approval: [{ k: 'path', d: 'M9 11l3 3 8-8' }, { k: 'path', d: 'M20 12v6a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h9' }],
  /** 短信：气泡 */
  sms: [{ k: 'path', d: 'M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z' }, { k: 'path', d: 'M8 9h8' }, { k: 'path', d: 'M8 13h5' }],
  /** 奖励：星形 */
  reward: [{ k: 'path', d: 'M12 2l2.9 6.3 6.9.8-5.1 4.7 1.4 6.8L12 17.3 5.9 20.6l1.4-6.8L2.2 9.1l6.9-.8L12 2z' }],
  /** 审计：放大镜 */
  audit: [{ k: 'circle', cx: 11, cy: 11, r: 7 }, { k: 'path', d: 'M21 21l-4.3-4.3' }],
  /** 报表/概览：柱状（与小程序 chart 语义对齐） */
  report: [{ k: 'path', d: 'M4 20h16' }, { k: 'path', d: 'M6 16V10' }, { k: 'path', d: 'M12 16V4' }, { k: 'path', d: 'M18 16v-6' }],
  /** 趋势：折线 */
  trend: [{ k: 'path', d: 'M4 19l5-5 4 3 6-7' }, { k: 'path', d: 'M4 20h16' }],
  /** 报告：纸张 */
  reportDoc: [{ k: 'path', d: 'M6 2h9l5 5v15H6z' }, { k: 'path', d: 'M14 2v6h6' }, { k: 'path', d: 'M9 13h7' }, { k: 'path', d: 'M9 17h5' }],
  /** 产品：立方体（与小程序 bank 语义对齐：银行/产品） */
  product: [{ k: 'path', d: 'M4 7l8-4 8 4-8 4-8-4z' }, { k: 'path', d: 'M4 7v10l8 4 8-4V7' }, { k: 'path', d: 'M12 11v10' }],
  /** 渠道：窗口 */
  channel: [{ k: 'rect', x: 4, y: 5, w: 16, h: 14, rx: 2 }, { k: 'path', d: 'M4 9h16' }, { k: 'path', d: 'M9 9v10' }],
  /** 名单：禁止圆 */
  ban: [{ k: 'circle', cx: 12, cy: 12, r: 9 }, { k: 'path', d: 'M5 5l14 14' }],
  /** 组织：双人 */
  org: [
    { k: 'circle', cx: 9, cy: 7, r: 3 },
    { k: 'circle', cx: 17, cy: 7, r: 2 },
    { k: 'path', d: 'M3 20c0-3 2.7-5 6-5s6 2 6 5' },
    { k: 'path', d: 'M15 20c0-2.5 1.8-4.3 4-4.8' },
  ],
  /** 配置：齿轮 */
  config: [
    { k: 'circle', cx: 12, cy: 12, r: 3 },
    { k: 'path', d: 'M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09a1.65 1.65 0 0 0-1-1.51 1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09a1.65 1.65 0 0 0 1.51-1 1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33h.09a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82v.09a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z' },
  ],
  /** 调试：扳手 */
  debug: [{ k: 'path', d: 'M14.7 6.3a4 4 0 0 0-5.4 5.4L4 17l3 3 5.3-5.3a4 4 0 0 0 5.4-5.4l-2.5 2.5-2-2 2.5-2.5z' }],
  /** OCR：扫描/照片（T16 Web OCR 页） */
  ocr: [{ k: 'path', d: 'M3 7V5a2 2 0 0 1 2-2h2' }, { k: 'path', d: 'M17 3h2a2 2 0 0 1 2 2v2' }, { k: 'path', d: 'M21 17v2a2 2 0 0 1-2 2h-2' }, { k: 'path', d: 'M7 21H5a2 2 0 0 1-2-2v-2' }, { k: 'path', d: 'M8 12h8' }, { k: 'path', d: 'M12 8v8' }],
};
</script>

<style scoped>
.app-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  vertical-align: middle;
}
.app-icon svg {
  display: block;
}
</style>
