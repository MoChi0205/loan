import { createSSRApp } from 'vue';
import { createPinia } from 'pinia';
import App from './App.vue';

/**
 * 小程序入口：挂载 Vue + Pinia（uni-app）。
 */
export function createApp() {
  const app = createSSRApp(App);
  app.use(createPinia());
  return { app };
}
