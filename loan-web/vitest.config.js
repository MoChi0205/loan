import { defineConfig } from 'vitest/config';
import { fileURLToPath, URL } from 'node:url';

// Vitest 配置（调用层 T0 测试）。
// 复用 vite 的 @ -> src 别名；环境用 jsdom 以支撑 window / location / 动态 import router。
export default defineConfig({
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    environment: 'jsdom',
    include: ['src/**/*.{test,spec}.{js,ts}'],
    globals: false,
    coverage: {
      provider: 'v8',
      reporter: ['text', 'lcov'],
      // 只统计 T0 实际加载执行的调用层文件，避免未导入的 0% 文件拖垮分母
      all: false,
      // T0（调用层）覆盖率门槛：PR 不达标阻断合并
      thresholds: {
        lines: 70,
        statements: 70,
        functions: 70,
        branches: 60,
      },
    },
  },
});
