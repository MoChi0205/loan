<template>
  <div class="animated-bg" :class="`bg-${type}`" aria-hidden="true">
    <!-- 渐变光斑网格 mesh -->
    <template v-if="type === 'mesh'">
      <div class="mesh-blob mesh-blob-1" />
      <div class="mesh-blob mesh-blob-2" />
      <div class="mesh-blob mesh-blob-3" />
      <div class="mesh-grid" />
    </template>

    <!-- 极光 aurora -->
    <template v-else-if="type === 'aurora'">
      <div class="aurora-band aurora-band-1" />
      <div class="aurora-band aurora-band-2" />
      <div class="aurora-band aurora-band-3" />
    </template>

    <!-- 粒子 particles -->
    <template v-else-if="type === 'particles'">
      <span
        v-for="p in particles"
        :key="p.id"
        class="particle"
        :style="particleStyle(p)"
      />
    </template>

    <!-- 自定义图片 image -->
    <template v-else-if="type === 'image' && url">
      <div class="image-bg" :style="{ backgroundImage: `url(${url})` }" />
      <div class="image-overlay" />
    </template>

    <!-- 兜底：浅色商务纯色（与 type 不匹配或未配置时） -->
    <template v-else>
      <div class="fallback" />
    </template>
  </div>
</template>

<script setup>
import { computed } from 'vue';

/**
 * 可配置动态登录背景（纯 CSS 动画，无第三方依赖，GPU 加速）。
 *
 * <p>预设（通过 type 切换）：
 *  - mesh      渐变光斑 + 细网格（默认，柔和专业，最贴合金融商务）
 *  - aurora    极光波带（色彩更丰富，更现代）
 *  - particles 漂浮粒子（科技感最强）
 *  - image     自定义图片 + 暗化蒙层 + 缓慢 Ken Burns
 *
 * <p>配置来源：.env 的 VITE_LOGIN_BG_TYPE / VITE_LOGIN_BG_URL。
 * 切换 type 不需改代码，热更新即生效。
 *
 * <p>无障碍：respect prefers-reduced-motion 自动停止动画。
 */
const props = defineProps({
  /** 背景类型：mesh | aurora | particles | image */
  type: { type: String, default: 'mesh' },
  /** 自定义图片 URL（type=image 时使用） */
  url: { type: String, default: '' },
});

/** 粒子配置（生成一次，固定 28 个漂浮点） */
function makeParticles() {
  const arr = [];
  for (let i = 0; i < 28; i++) {
    arr.push({
      id: i,
      x: Math.random() * 100,
      y: Math.random() * 100,
      size: 4 + Math.random() * 6,
      dur: 16 + Math.random() * 16,
      delay: -Math.random() * 20,
      dx: (Math.random() - 0.5) * 60,
      dy: (Math.random() - 0.5) * 60,
      hue: Math.random() > 0.6 ? 'var(--loan-accent)' : 'var(--loan-primary)',
    });
  }
  return arr;
}
const particles = makeParticles();

/** 粒子样式：定位 + 尺寸 + 动画参数（CSS 自定义属性） */
function particleStyle(p) {
  return {
    left: `${p.x}%`,
    top: `${p.y}%`,
    width: `${p.size}px`,
    height: `${p.size}px`,
    background: p.hue,
    '--dur': `${p.dur}s`,
    '--delay': `${p.delay}s`,
    '--dx': `${p.dx}px`,
    '--dy': `${p.dy}px`,
  };
}
</script>

<style scoped>
.animated-bg {
  position: fixed;
  inset: 0;
  z-index: 0;
  overflow: hidden;
  pointer-events: none;
}

/* ============================================================
 * Mesh —— 渐变光斑 + 细网格（默认，柔和专业）
 * ============================================================ */
.bg-mesh {
  background: linear-gradient(135deg, #f5f7fa 0%, #eef2ff 50%, #ecfeff 100%);
}

.mesh-blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(90px);
  opacity: 0.55;
  will-change: transform;
}

.mesh-blob-1 {
  width: 520px;
  height: 520px;
  background: #93c5fd;
  top: -160px;
  left: -120px;
  animation: mesh-drift-a 28s ease-in-out infinite alternate;
}

.mesh-blob-2 {
  width: 460px;
  height: 460px;
  background: #c4b5fd;
  bottom: -140px;
  right: -100px;
  animation: mesh-drift-b 34s ease-in-out infinite alternate;
}

.mesh-blob-3 {
  width: 380px;
  height: 380px;
  background: #67e8f9;
  top: 40%;
  left: 50%;
  transform: translate(-50%, -50%);
  animation: mesh-drift-c 40s ease-in-out infinite alternate;
}

.mesh-grid {
  position: absolute;
  inset: 0;
  background-image: radial-gradient(
    circle,
    rgba(37, 99, 235, 0.08) 1px,
    transparent 1px
  );
  background-size: 28px 28px;
  -webkit-mask-image: radial-gradient(ellipse at center, #000 30%, transparent 75%);
  mask-image: radial-gradient(ellipse at center, #000 30%, transparent 75%);
}

@keyframes mesh-drift-a {
  0% {
    transform: translate(0, 0) scale(1);
  }
  100% {
    transform: translate(160px, 100px) scale(1.15);
  }
}

@keyframes mesh-drift-b {
  0% {
    transform: translate(0, 0) scale(1.1);
  }
  100% {
    transform: translate(-140px, -100px) scale(0.9);
  }
}

@keyframes mesh-drift-c {
  0% {
    transform: translate(-50%, -50%) scale(1);
  }
  100% {
    transform: translate(-30%, -60%) scale(1.2);
  }
}

/* ============================================================
 * Aurora —— 极光波带
 * ============================================================ */
.bg-aurora {
  background: linear-gradient(160deg, #eef2ff 0%, #f5f7fa 50%, #ecfeff 100%);
}

.aurora-band {
  position: absolute;
  width: 140%;
  height: 320px;
  left: -20%;
  filter: blur(80px);
  opacity: 0.55;
  border-radius: 50%;
  will-change: transform;
}

.aurora-band-1 {
  background: linear-gradient(90deg, transparent, #93c5fd, transparent);
  top: 20%;
  animation: aurora-sway-a 18s ease-in-out infinite alternate;
}

.aurora-band-2 {
  background: linear-gradient(90deg, transparent, #c4b5fd, transparent);
  top: 50%;
  animation: aurora-sway-b 22s ease-in-out infinite alternate;
}

.aurora-band-3 {
  background: linear-gradient(90deg, transparent, #67e8f9, transparent);
  top: 75%;
  animation: aurora-sway-c 26s ease-in-out infinite alternate;
}

@keyframes aurora-sway-a {
  0% {
    transform: translate(-10%, 0) rotate(-3deg);
  }
  100% {
    transform: translate(10%, -20px) rotate(3deg);
  }
}

@keyframes aurora-sway-b {
  0% {
    transform: translate(10%, 0) rotate(2deg);
  }
  100% {
    transform: translate(-12%, 20px) rotate(-2deg);
  }
}

@keyframes aurora-sway-c {
  0% {
    transform: translate(-5%, 0) rotate(-1deg);
  }
  100% {
    transform: translate(8%, -15px) rotate(4deg);
  }
}

/* ============================================================
 * Particles —— 漂浮粒子
 * ============================================================ */
.bg-particles {
  background: linear-gradient(135deg, #0f172a 0%, #1e293b 100%);
}

.particle {
  position: absolute;
  border-radius: 50%;
  opacity: 0.5;
  will-change: transform;
  animation: particle-float var(--dur) ease-in-out infinite;
  animation-delay: var(--delay);
}

@keyframes particle-float {
  0%,
  100% {
    transform: translate(0, 0);
  }
  50% {
    transform: translate(var(--dx), var(--dy));
  }
}

/* ============================================================
 * Image —— 自定义图片
 * ============================================================ */
.image-bg {
  position: absolute;
  inset: 0;
  background-size: cover;
  background-position: center;
  animation: image-zoom 30s ease-in-out infinite alternate;
  will-change: transform;
}

.image-overlay {
  position: absolute;
  inset: 0;
  background: linear-gradient(
    180deg,
    rgba(15, 23, 42, 0.35),
    rgba(15, 23, 42, 0.6)
  );
}

@keyframes image-zoom {
  0% {
    transform: scale(1);
  }
  100% {
    transform: scale(1.08);
  }
}

/* 兜底：纯色渐变 */
.fallback {
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, #f5f7fa 0%, #eef2ff 100%);
}

/* 无障碍：尊重用户的减少动画偏好 */
@media (prefers-reduced-motion: reduce) {
  .mesh-blob,
  .aurora-band,
  .particle,
  .image-bg {
    animation: none !important;
  }
}
</style>
