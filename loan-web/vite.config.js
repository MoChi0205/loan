import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import { fileURLToPath, URL } from 'node:url';

/**
 * Vite 配置（参考 tse-frontend）。
 * 本地联调：VITE_API_PROXY=http://localhost:8088 代理到网关（网关统一鉴权后转发后端）
 * 直连后端调试：VITE_API_PROXY=http://localhost:8080
 */
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  build: {
    // 不清空 dist 目录，避免触发删除确认（本地验证用；生产发布可改回 true）
    emptyOutDir: false,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) return;
          if (id.includes('/echarts/')) return 'vendor-echarts';
          if (id.includes('/element-plus/')) return 'vendor-element';
          if (id.includes('/vue') || id.includes('/vue-router') || id.includes('/pinia')) return 'vendor-vue';
          if (id.includes('/axios/') || id.includes('/jsencrypt/')) return 'vendor-utils';
          return 'vendor';
        },
      },
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/loan': {
        target: process.env.VITE_API_PROXY || 'http://localhost:8088',
        changeOrigin: true,
      },
    },
  },
});
