import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import AutoImport from 'unplugin-auto-import/vite';
import Components from 'unplugin-vue-components/vite';
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers';
import { createHash } from 'node:crypto';
import { fileURLToPath, URL } from 'node:url';

/**
 * 生产构建专用 CSP 插件（XSS 加固）。
 * - 仅 apply: 'build' 生效，开发模式（vite / dev）完全不触发，不影响 HMR。
 * - 在 index.html 注入 <meta http-equiv="Content-Security-Policy">。
 * - 对最终 HTML 里的所有内联 <script> 计算 sha256 哈希并加入白名单，
 *   从而放行 Vite 自身的 modulepreload polyfill 等内联脚本，同时阻断任何
 *   非构建产物的注入脚本（如 XSS 注入的内联脚本，因哈希不匹配而被拒）。
 * - 不设置 default-src，避免误伤后端 API（connect-src）与图片/字体等；
 *   仅锁定最危险的脚本执行面 + base-uri / object-src。
 * 注：meta CSP 不支持 frame-ancestors，如需该指令请在部署服务器（nginx 等）
 * 以 HTTP 响应头形式下发。
 */
function buildCspPlugin() {
  return {
    name: 'build-csp',
    apply: 'build',
    enforce: 'post',
    transformIndexHtml(html) {
      const hashes = new Set();
      const re = /<script(\s[^>]*)?>([\s\S]*?)<\/script>/gi;
      let m;
      while ((m = re.exec(html))) {
        const body = m[2] || '';
        if (body.trim()) {
          const hash = 'sha256-' + createHash('sha256').update(body).digest('base64');
          hashes.add(hash);
        }
      }
      const scriptSrc =
        hashes.size > 0
          ? `script-src 'self' ${[...hashes].join(' ')}`
          : `script-src 'self'`;
      const csp = [
        scriptSrc,
        `style-src 'self' 'unsafe-inline'`,
        `base-uri 'self'`,
        `object-src 'none'`,
      ].join('; ');
      return {
        html,
        tags: [
          {
            tag: 'meta',
            attrs: { 'http-equiv': 'Content-Security-Policy', content: csp },
            injectTo: 'head-prepend',
          },
        ],
      };
    },
  };
}

/**
 * Vite 配置（参考 tse-frontend）。
 * 本地联调：VITE_API_PROXY=http://localhost:8088 代理到网关（网关统一鉴权后转发后端）
 * 直连后端调试：VITE_API_PROXY=http://localhost:8080
 */
export default defineConfig({
  plugins: [
    vue(),
    // Element Plus 按需引入：仅打包用到的组件/指令/样式，消除全量引入导致的超大 vendor 包。
    // locale 通过 App.vue 的 <el-config-provider :locale="zhCn"> 注入（见 components resolver 不处理 locale）。
    AutoImport({
      resolvers: [ElementPlusResolver()],
      dts: false,
    }),
    Components({
      resolvers: [ElementPlusResolver()],
      dts: false,
    }),
    buildCspPlugin(),
  ],
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
          // element-plus 按需引入后按组件族拆分，避免单一 vendor 块超 500KB（按功能拆包）。
          if (id.includes('/element-plus/')) {
            if (id.includes('/table') || id.includes('/table-column') || id.includes('/table-v2') || id.includes('/grid')) {
              return 'vendor-element-table';
            }
            if (id.includes('/form') || id.includes('/select') || id.includes('/cascader') || id.includes('/tree-select') || id.includes('/date-picker')) {
              return 'vendor-element-form';
            }
            if (id.includes('/dialog') || id.includes('/drawer') || id.includes('/dropdown') || id.includes('/popover') || id.includes('/tooltip') || id.includes('/overlay')) {
              return 'vendor-element-overlay';
            }
            return 'vendor-element';
          }
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
