// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import axios from 'axios';
import request from '@/utils/request';
import router from '@/router';

// ---- 外部依赖 mock ----
// element-plus：仅用到 ElMessage 提示
vi.mock('element-plus', () => ({
  ElMessage: { error: vi.fn(), warning: vi.fn() },
}));

// @/utils/storage：token 读取可控
let mockToken = 'tok-123';
vi.mock('@/utils/storage', () => ({
  KEYS: { TOKEN: 'token', USER: 'user' },
  getStorage: vi.fn((k) => (k === 'token' ? mockToken : null)),
  removeStorage: vi.fn(),
}));

// @/router：SPA 跳转（被动态 import，redirectToLogin 调 push）
vi.mock('@/router', () => ({
  default: { push: vi.fn(() => Promise.resolve()) },
}));

// 从 mocked 模块取出引用，便于断言
import { ElMessage } from 'element-plus';
import { KEYS, getStorage, removeStorage } from '@/utils/storage';

const tick = () => new Promise((r) => setTimeout(r, 0));

// 可控 adapter：模拟后端返回/异常，同时让请求拦截注入的 header 可被断言
let adapterImpl;
let lastConfig = null;

beforeEach(() => {
  mockToken = 'tok-123';
  lastConfig = null;
  adapterImpl = (config) => {
    lastConfig = config;
    return Promise.resolve({
      data: { code: 0, message: 'ok', data: { id: 1 } },
      status: 200,
      statusText: 'OK',
      headers: {},
      config,
      request: {},
    });
  };
  request.defaults.adapter = (config) => adapterImpl(config);
  window.__loan_login_redirecting__ = false;
  ElMessage.error.mockClear();
  ElMessage.warning.mockClear();
  removeStorage.mockClear();
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('loan-web request 拦截器', () => {
  it('请求拦截注入 Authorization 与 X-Client-Type', async () => {
    await request.get('/demo');
    expect(lastConfig.headers.Authorization).toBe('Bearer tok-123');
    expect(lastConfig.headers['X-Client-Type']).toBe('WEB');
  });

  it('无 token 时不注入 Authorization，仅注入 X-Client-Type', async () => {
    mockToken = '';
    await request.get('/demo');
    expect(lastConfig.headers.Authorization).toBeUndefined();
    expect(lastConfig.headers['X-Client-Type']).toBe('WEB');
  });

  it('code=0 返回完整 Result 信封（不解包）', async () => {
    const res = await request.get('/demo');
    expect(res.code).toBe(0);
    expect(res.data.id).toBe(1);
  });

  it('业务错误码（非0非2000）拒绝并提示', async () => {
    adapterImpl = () =>
      Promise.resolve({
        data: { code: 5001, message: '参数错误' },
        status: 200,
        statusText: 'OK',
        headers: {},
        config: {},
        request: {},
      });
    await expect(request.get('/demo')).rejects.toThrow('参数错误');
    expect(ElMessage.error).toHaveBeenCalledWith('参数错误');
  });

  it('code=2000 清凭据并跳登录（并发去重只跳一次）', async () => {
    adapterImpl = () =>
      Promise.resolve({
        data: { code: 2000, message: '未登录' },
        status: 200,
        statusText: 'OK',
        headers: {},
        config: {},
        request: {},
      });
    // 拒绝信息回显服务端 message（res.message 存在时优先）
    await expect(request.get('/demo')).rejects.toThrow('未登录');
    // 同步清凭据
    expect(removeStorage).toHaveBeenCalledWith(KEYS.TOKEN);
    expect(removeStorage).toHaveBeenCalledWith(KEYS.USER);
    // 动态 import router 在微任务中 push
    await tick();
    expect(router.push).toHaveBeenCalledWith(
      expect.objectContaining({ path: '/login' }),
    );
  });

  it('HTTP 401 触发登录跳转', async () => {
    adapterImpl = () =>
      Promise.reject({
        response: { status: 401, data: {} },
        config: {},
        request: {},
        isAxiosError: true,
      });
    await expect(request.get('/demo')).rejects.toBeDefined();
    expect(removeStorage).toHaveBeenCalledWith(KEYS.TOKEN);
    await tick();
    expect(router.push).toHaveBeenCalled();
  });

  it('超时（ECONNABORTED）友好提示 warning', async () => {
    adapterImpl = () =>
      Promise.reject({
        code: 'ECONNABORTED',
        message: 'timeout of 30000ms exceeded',
        config: {},
      });
    await expect(request.get('/demo')).rejects.toBeDefined();
    expect(ElMessage.warning).toHaveBeenCalledWith('请求超时，请稍后重试');
  });

  it('主动取消的请求静默（不弹错）', async () => {
    vi.spyOn(axios, 'isCancel').mockReturnValue(true);
    adapterImpl = () => Promise.reject(new axios.Cancel('cancelled'));
    await expect(request.get('/demo')).rejects.toBeDefined();
    expect(ElMessage.error).not.toHaveBeenCalled();
    expect(ElMessage.warning).not.toHaveBeenCalled();
  });

  it('网络异常默认提示', async () => {
    adapterImpl = () =>
      Promise.reject({ message: 'Network Error', config: {} });
    await expect(request.get('/demo')).rejects.toBeDefined();
    expect(ElMessage.error).toHaveBeenCalledWith('网络异常，请稍后重试');
  });
});
