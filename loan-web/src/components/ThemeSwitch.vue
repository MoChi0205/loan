<template>
  <div class="theme-switch">
    <!-- 明/暗切换 -->
    <button
      class="theme-btn"
      type="button"
      :title="isDark ? '切换为浅色' : '切换为深色'"
      :aria-label="isDark ? '切换为浅色' : '切换为深色'"
      :aria-pressed="isDark ? 'true' : 'false'"
      @click="toggleMode"
    >
      <svg v-if="isDark" viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" stroke-width="1.8">
        <circle cx="12" cy="12" r="4" />
        <path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
      </svg>
      <svg v-else viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" stroke-width="1.8">
        <path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z" />
      </svg>
    </button>

    <!-- 主色选择 -->
    <el-popover placement="bottom" trigger="click" width="216">
      <template #reference>
        <button class="theme-btn color-btn" type="button" title="主题色" aria-label="主题色">
          <span class="color-dot" :style="{ background: primary }"></span>
        </button>
      </template>
      <div class="palette">
        <div class="palette-title">品牌主色</div>
        <div class="palette-grid">
          <button
            v-for="c in palette"
            :key="c.value"
            class="palette-item"
            :class="{ active: c.value === primary }"
            type="button"
            :title="c.name"
            @click="setPrimary(c.value)"
          >
            <span class="palette-swatch" :style="{ background: c.value }"></span>
            <svg v-if="c.value === primary" class="palette-check" viewBox="0 0 24 24" width="12" height="12" fill="none" :stroke="checkColor(c.value)" stroke-width="3">
              <path d="M5 13l4 4L19 7" />
            </svg>
          </button>
        </div>
      </div>
    </el-popover>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import { getTheme, setThemeMode, setThemePrimary } from '@/theme';

/** setup 同步读取当前主题（applyTheme 已在 main.js 挂载前执行），首帧即正确，避免闪烁 */
const initial = getTheme();
const isDark = ref(initial.mode === 'dark');
const primary = ref(initial.primary);

/** 可选主色（金融/商务友好） */
const palette = [
  { name: '电光蓝', value: '#3b82f6' },
  { name: '靛蓝', value: '#6366f1' },
  { name: '青', value: '#06b6d4' },
  { name: '翠绿', value: '#10b981' },
  { name: '紫罗兰', value: '#8b5cf6' },
  { name: '金', value: '#f59e0b' },
];

function toggleMode() {
  setThemeMode(isDark.value ? 'light' : 'dark');
  isDark.value = !isDark.value;
}

function setPrimary(color) {
  setThemePrimary(color);
  primary.value = color;
}

/** 判断颜色明暗：亮色底上用深色对勾，深色底上用白色对勾（保证 WCAG 对比度） */
function checkColor(hex) {
  const h = String(hex || '').replace('#', '');
  if (!/^[0-9a-fA-F]{6}$/.test(h)) return '#fff';
  const r = parseInt(h.slice(0, 2), 16);
  const g = parseInt(h.slice(2, 4), 16);
  const b = parseInt(h.slice(4, 6), 16);
  const luma = 0.299 * r + 0.587 * g + 0.114 * b;
  return luma > 150 ? '#1f2937' : '#ffffff';
}
</script>

<style scoped>
.theme-switch {
  display: flex;
  align-items: center;
  gap: 4px;
}

.theme-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: none;
  border-radius: var(--loan-radius-sm);
  background: transparent;
  color: var(--loan-text-secondary);
  cursor: pointer;
  transition: background var(--loan-transition), color var(--loan-transition);
}

.theme-btn:hover {
  background: var(--loan-surface);
  color: var(--loan-text);
}

.color-dot {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  border: 2px solid var(--loan-card-bg);
  box-shadow: 0 0 0 1px var(--loan-border);
}

/* 主色面板 */
.palette-title {
  font-size: 12px;
  color: var(--loan-text-secondary);
  margin-bottom: 10px;
}

.palette-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 8px;
}

.palette-item {
  position: relative;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  border: none;
  cursor: pointer;
  padding: 0;
  transition: transform var(--loan-transition);
}

.palette-item:hover {
  transform: scale(1.12);
}

.palette-item.active {
  outline: 2px solid var(--loan-primary);
  outline-offset: 2px;
}

.palette-swatch {
  display: block;
  width: 100%;
  height: 100%;
  border-radius: 8px;
}

.palette-check {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
}
</style>
