// Vitest 配置（小程序调用层 T0 测试）。
// request.js 通过全局 uni 对象发起请求，无业务依赖，环境用 node 即可。
// 注：本文件不 import 'vitest/config'，以便在本仓库未安装 vitest 时也能被工作区 vitest 加载。
export default {
  test: {
    environment: 'node',
    include: ['api/**/*.test.js'],
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
};
