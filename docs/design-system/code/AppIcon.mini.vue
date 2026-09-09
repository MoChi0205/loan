<template>
  <!-- 墨金 Ink & Gold · Mini(uni-app) 端统一图标组件 -->
  <!-- 关键点：小程序 SVG 的 stroke 不解析 var()，故运行时注入真实色值生成 data-URI -->
  <view class="app-icon" :style="boxStyle" :aria-label="label || name"></view>
</template>

<script>
import { ICON_PATHS } from './icons.js';

export default {
  name: 'AppIcon',
  props: {
    name: { type: String, required: true },
    size: { type: [Number, String], default: 24 },
    /** 必须传真实色值(#RRGGBB / rgba)，不可传 var()；由调用方按主题注入 */
    color: { type: String, default: '#1A2336' },
    strokeWidth: { type: [Number, String], default: 1.75 },
    label: { type: String, default: '' }
  },
  computed: {
    uri() {
      const p = ICON_PATHS[this.name] || '';
      const svg =
        `<svg xmlns='http://www.w3.org/2000/svg' width='${this.size}' height='${this.size}' ` +
        `viewBox='0 0 24 24' fill='none' stroke='${this.color}' ` +
        `stroke-width='${this.strokeWidth}' stroke-linecap='round' stroke-linejoin='round'>${p}</svg>`;
      return 'data:image/svg+xml,' + encodeURIComponent(svg);
    },
    boxStyle() {
      const s = `${this.size}px`;
      return {
        width: s,
        height: s,
        backgroundImage: `url("${this.uri}")`,
        backgroundSize: 'contain',
        backgroundRepeat: 'no-repeat',
        backgroundPosition: 'center'
      };
    }
  }
};
</script>

<style scoped>
.app-icon {
  display: inline-block;
  flex-shrink: 0;
}
</style>
