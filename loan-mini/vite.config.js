import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'
import path from 'node:path'

// 本项目为 HBuilderX 风格布局（源码在根目录而非 src/），
// 显式声明 uni() 插件以在根目录布局下正确挂载 @vitejs/plugin-vue 等编译链。
//
// ⚠️ 根目录布局关键坑（2026-08-28）：
//   1) uni 插件默认找 src/，须用环境变量 UNI_INPUT_DIR=. 切根目录布局；
//   2) 根目录布局下 easycom 生成的组件 import 是相对 rootDir 的
//      "components/AppXxx.vue"（无 @ 前缀），vite 默认按 importer 目录解析会 404，
//      必须显式配 resolve.alias.components → 项目根 components/ 兜底；
//   3) @ 别名同样显式配到项目根（UNI_INPUT_DIR=. 时 uni 自动配的 @ 可能解析到错误位置）。
//
// 接口代理：api/request.js 的 BASE_URL 为空（相对路径），H5 预览时请求 /api/...，
// 而后端 loan-service 的 context-path 是 /loan，故需重写为 /loan/api/... 并转发到网关 8088。
// 仅影响 H5 开发预览；小程序真机走 api/request.js 里配置的 BASE_URL，不受此处影响。
//
// ⚠️ 关键坑（2026-08-28）：代理 key 不能用裸前缀 '/api'。
//   前端源码目录就叫 api/，dev 模式下模块被解析成 /api/request.js、/api/auth.js、
//   /api/invitation.js 等绝对路径；裸前缀会把它们当成后端接口转发到 8080，
//   后端无 /loan/api/request.js → 404 → 页面组件 import 失败 →
//   uni-h5 的 AsyncError 全屏页「连接服务器超时，点击屏幕重试」（极具误导性）。
//   故用负向前瞻排除带扩展名的请求，只代理真正的后端 API（如 /api/mini/auth/login）。
export default defineConfig({
  plugins: [uni()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname),
      components: path.resolve(__dirname, 'components'),
    },
  },
  server: {
    port: 5174,
    proxy: {
      '^/api/(?!.*\\.(js|mjs|ts|css|json|vue|png|jpe?g|gif|svg|ico|woff2?|ttf)$)': {
        // H5 开发请求也必须经过网关，保持与 Web 管理端一致的 JWT/端类型鉴权链路。
        target: 'http://localhost:8088',
        changeOrigin: true,
        rewrite: (path) => `/loan${path}`,
      },
    },
  },
})
