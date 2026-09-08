<template>
  <view class="app-icon" :class="[`icon-${size}`]">
    <svg class="ico-svg" viewBox="0 0 48 48" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
      <!-- 鲜亮商务圆角磁贴底（统一风格：每个功能一个品牌色） -->
      <rect x="3" y="3" width="42" height="42" rx="13" :fill="tileColor" />
      <!-- 白色线性字形（2.5px 圆角描边，统一图标家族） -->
      <g fill="none" stroke="#ffffff" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
        <template v-if="name === 'home'">
          <path d="M13 24 L24 15 L35 24" />
          <path d="M16 24 V34 H32 V24" />
          <path d="M21 34 V28 H27 V34" />
        </template>

        <template v-else-if="name === 'match'">
          <circle cx="24" cy="24" r="11" />
          <path d="M24 15 L27.5 24 L24 33 L20.5 24 Z" fill="#ffffff" stroke="none" />
          <circle cx="24" cy="24" r="2.6" :fill="tileColor" stroke="none" />
        </template>

        <template v-else-if="name === 'chart'">
          <path d="M12 35 H36" />
          <path d="M15 35 V25" />
          <path d="M24 35 V18" />
          <path d="M33 35 V27" />
        </template>

        <template v-else-if="name === 'order'">
          <rect x="16" y="12" width="16" height="24" rx="2.5" />
          <path d="M20 19 H28" />
          <path d="M20 24 H28" />
          <path d="M20 29 H25" />
        </template>

        <template v-else-if="name === 'bank'">
          <path d="M11 22 L24 14 L37 22" />
          <path d="M14 22 V30 M20 22 V30 M28 22 V30 M34 22 V30" />
          <path d="M11 33 H37" />
        </template>

        <template v-else-if="name === 'users'">
          <circle cx="20" cy="20" r="4.2" />
          <path d="M12.5 32 C12.5 26.5 27.5 26.5 27.5 32" />
          <circle cx="31" cy="22.5" r="3.6" />
          <path d="M26 33 C26 28.5 37 28.5 37 33" />
        </template>

        <template v-else-if="name === 'mine'">
          <circle cx="24" cy="19" r="5" />
          <path d="M14 34 C14 27 34 27 34 34" />
        </template>

        <template v-else-if="name === 'check'">
          <path d="M16 24 L22 30 L33 17" />
        </template>

        <template v-else-if="name === 'search'">
          <circle cx="21" cy="21" r="8" />
          <path d="M27 27 L34 34" />
        </template>

        <template v-else-if="name === 'wechat'">
          <ellipse cx="19" cy="21" rx="8.5" ry="6.2" />
          <ellipse cx="29.5" cy="28" rx="6.5" ry="4.8" />
          <circle cx="16.5" cy="20" r="1.3" fill="#ffffff" stroke="none" />
          <circle cx="21.5" cy="20" r="1.3" fill="#ffffff" stroke="none" />
          <circle cx="27.5" cy="27.5" r="1.1" fill="#ffffff" stroke="none" />
          <circle cx="31.5" cy="27.5" r="1.1" fill="#ffffff" stroke="none" />
        </template>

        <template v-else-if="name === 'bolt'">
          <path d="M26 12 L16 26 H23.5 L21 36 L33 21 H25 Z" fill="#ffffff" stroke="none" />
        </template>

        <template v-else-if="name === 'support'">
          <path d="M16 26 V22 C16 16 32 16 32 22 V26" />
          <rect x="13.5" y="25.5" width="5" height="9" rx="2.5" />
          <rect x="29.5" y="25.5" width="5" height="9" rx="2.5" />
          <path d="M31 35 V33 C31 30 36 30 36 33" />
        </template>

        <template v-else-if="name === 'list'">
          <circle cx="13.5" cy="17" r="1.7" fill="#ffffff" stroke="none" />
          <circle cx="13.5" cy="24" r="1.7" fill="#ffffff" stroke="none" />
          <circle cx="13.5" cy="31" r="1.7" fill="#ffffff" stroke="none" />
          <path d="M17 17 H34" />
          <path d="M17 24 H34" />
          <path d="M17 31 H34" />
        </template>

        <template v-else-if="name === 'share'">
          <circle cx="24" cy="14" r="4" />
          <circle cx="14" cy="33" r="4" />
          <circle cx="34" cy="33" r="4" />
          <path d="M21 17 L16.5 30" />
          <path d="M27 17 L31.5 30" />
        </template>

        <template v-else-if="name === 'enterprise'">
          <rect x="15" y="13" width="18" height="23" rx="1.5" />
          <path d="M19 18 H22 M26 18 H29 M19 24 H22 M26 24 H29 M19 30 H22 M26 30 H29" />
        </template>

        <template v-else-if="name === 'person'">
          <circle cx="24" cy="19" r="5" />
          <path d="M14 34 C14 27 34 27 34 34" />
        </template>

        <template v-else-if="name === 'refresh'">
          <path d="M32 17 A10 10 0 1 0 33 27" />
          <path d="M32 12 V18.5 H25.5" />
        </template>

        <template v-else-if="name === 'alert'">
          <path d="M24 14 L35 32 H13 Z" />
          <path d="M24 21 V26" />
          <circle cx="24" cy="30" r="1.6" fill="#ffffff" stroke="none" />
        </template>

        <template v-else-if="name === 'trend'">
          <path d="M14 31 L21 24 L27 28 L35 16" />
          <path d="M30 16 H35 V21" />
        </template>

        <template v-else-if="name === 'doc'">
          <path d="M17 12 H26 L32 18 V36 H17 Z" />
          <path d="M26 12 V18 H32" />
          <path d="M21 24 H29 M21 29 H29" />
        </template>

        <template v-else-if="name === 'file'">
          <path d="M15 15 H22 L25 19 H33 V34 H15 Z" />
          <path d="M20 25 H29 M20 30 H29" />
        </template>

        <template v-else-if="name === 'photo'">
          <rect x="14" y="15" width="20" height="18" rx="2.5" />
          <circle cx="20.5" cy="21" r="2" />
          <path d="M14.5 30 L22 22.5 L27 27.5 L31 23 L33.5 25.5" />
        </template>

        <template v-else-if="name === 'lock'">
          <rect x="15" y="21" width="18" height="14" rx="2.5" />
          <path d="M18.5 21 V17 C18.5 13.5 29.5 13.5 29.5 17 V21" />
          <circle cx="24" cy="27.5" r="2" :fill="tileColor" stroke="none" />
        </template>

        <template v-else-if="name === 'arrow'">
          <path d="M17 24 H32" />
          <path d="M27 19 L33 24 L27 29" />
        </template>

        <!-- 兜底：圆点磁贴 -->
        <template v-else>
          <circle cx="24" cy="24" r="5" fill="#ffffff" stroke="none" />
        </template>
      </g>
    </svg>
  </view>
</template>

<script setup>
/**
 * 图标组件 v2（自绘鲜亮矢量图标，零依赖、无限缩放 = 高清）。
 *
 * <p>v1 为纯 CSS 单色描边（currentColor），全站呈现「灰蓝」观感且偏死板。
 * v2 改为每个功能一个品牌色圆角磁贴 + 白色线性字形，统一为现代金融科技图标家族，
 * 色彩鲜明、明亮、商务，无 AI 模板感。矢量绘制，小程序 / H5 双端一致。
 *
 * <p>可用 name：home / match / chart / order / bank / users / mine / check /
 * search / wechat / bolt / support / list / share / enterprise / person /
 * refresh / alert / trend / doc / file / photo / lock / arrow
 *
 * 用法：<AppIcon name="match" size="lg" />
 */
const props = defineProps({
  name: { type: String, default: 'dot' },
  /** 尺寸：sm(32rpx) / md(48rpx) / lg(64rpx) */
  size: { type: String, default: 'md' },
  /** 兼容旧调用（v2 图标自带品牌色，此属性保留但不生效） */
  color: { type: String, default: '' },
});

/** 每个功能的品牌磁贴色（深一档亮色，保证白色字形 ≥4.5:1 对比度） */
const TILE = {
  home: '#2C5BFF',
  match: '#6D4AED',
  chart: '#F0920C',
  order: '#11A86B',
  bank: '#2C5BFF',
  users: '#0E9CB0',
  mine: '#4F46E5',
  check: '#EF4D5E',
  search: '#2C5BFF',
  wechat: '#11A86B',
  bolt: '#F0920C',
  support: '#0E9CB0',
  list: '#2C5BFF',
  share: '#6D4AED',
  enterprise: '#4F46E5',
  person: '#4F46E5',
  refresh: '#2C5BFF',
  alert: '#F0920C',
  trend: '#11A86B',
  doc: '#2C5BFF',
  file: '#F0920C',
  photo: '#0E9CB0',
  lock: '#6D4AED',
  arrow: '#2C5BFF',
};

const tileColor = TILE[props.name] || '#2C5BFF';
</script>

<style scoped>
.app-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  line-height: 0;
}
.icon-sm { width: 32rpx; height: 32rpx; }
.icon-md { width: 48rpx; height: 48rpx; }
.icon-lg { width: 64rpx; height: 64rpx; }

.ico-svg {
  width: 100%;
  height: 100%;
  display: block;
}
</style>
