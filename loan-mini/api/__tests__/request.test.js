// @vitest-environment node
import { describe, it, expect, vi, beforeEach } from 'vitest';
import request, {
  requestGet,
  requestPost,
  getToken,
  clearToken,
  BASE_URL,
  TOKEN_KEY,
} from '../request';

// 落地页路径需与源文件保持一致
const LANDING_PAGE = '/pages/index/index';

// ---- uni 全局 mock ----
let uniRequestOpts = null;
const uni = {
  getStorageSync: vi.fn(() => 'mini-tok'),
  removeStorageSync: vi.fn(),
  showToast: vi.fn(),
  reLaunch: vi.fn((opts) => opts && opts.complete && opts.complete()),
  request: vi.fn((opts) => {
    uniRequestOpts = opts;
  }),
};

beforeEach(() => {
  uni.getStorageSync.mockImplementation(() => 'mini-tok');
  uni.removeStorageSync.mockClear();
  uni.showToast.mockClear();
  uni.reLaunch.mockClear();
  uni.request.mockClear();
  uniRequestOpts = null;
  globalThis.uni = uni;
});

describe('loan-mini request 调用层', () => {
  it('请求头注入 X-Client-Type 与 Bearer token', () => {
    requestPost('/api/x', {});
    expect(uniRequestOpts.header['X-Client-Type']).toBe('MINI_APP');
    expect(uniRequestOpts.header.Authorization).toBe('Bearer mini-tok');
    expect(uniRequestOpts.header['Content-Type']).toBe('application/json');
  });

  it('无 token 时不注入 Authorization', () => {
    uni.getStorageSync.mockImplementation(() => '');
    requestGet('/api/x');
    expect(uniRequestOpts.header.Authorization).toBeUndefined();
    expect(uniRequestOpts.header['X-Client-Type']).toBe('MINI_APP');
  });

  it('成功解包 data（statusCode=200 && code=0）', async () => {
    const p = requestPost('/api/x', {});
    uniRequestOpts.success({
      statusCode: 200,
      data: { code: 0, message: 'ok', data: { id: 2 }, traceUuid: 't' },
    });
    await expect(p).resolves.toEqual({ id: 2 });
  });

  it('业务错误码（非0非2000）拒绝并 toast', async () => {
    const p = requestPost('/api/x', {});
    uniRequestOpts.success({
      statusCode: 200,
      data: { code: 5001, message: '参数错误' },
    });
    await expect(p).rejects.toMatchObject({ code: 5001, message: '参数错误' });
    expect(uni.showToast).toHaveBeenCalledWith({
      title: '参数错误',
      icon: 'none',
      duration: 2500,
    });
  });

  it('code=2000 清 token 并跳落地页（不 toast）', async () => {
    const p = requestPost('/api/x', {});
    uniRequestOpts.success({
      statusCode: 200,
      data: { code: 2000, message: '未登录' },
    });
    await expect(p).rejects.toMatchObject({ code: 2000 });
    expect(uni.removeStorageSync).toHaveBeenCalledWith(TOKEN_KEY);
    expect(uni.reLaunch).toHaveBeenCalledWith(
      expect.objectContaining({ url: LANDING_PAGE }),
    );
    expect(uni.showToast).not.toHaveBeenCalled();
  });

  it('HTTP 401 清 token 并跳落地页', async () => {
    const p = requestPost('/api/x', {});
    uniRequestOpts.success({ statusCode: 401, data: {} });
    await expect(p).rejects.toMatchObject({
      code: 2000,
      message: '登录已过期，请重新登录',
    });
    expect(uni.removeStorageSync).toHaveBeenCalledWith(TOKEN_KEY);
    expect(uni.reLaunch).toHaveBeenCalled();
  });

  it('网络失败 fail 回调 -> e.code=-1 并 toast（errMsg 缺失时回退默认文案）', async () => {
    const p = requestPost('/api/x', {});
    // errMsg 为空时回退到默认网络异常文案
    uniRequestOpts.fail({ errMsg: '' });
    await expect(p).rejects.toMatchObject({
      code: -1,
      message: '网络异常，请稍后重试',
    });
    expect(uni.showToast).toHaveBeenCalledWith({
      title: '网络异常，请稍后重试',
      icon: 'none',
      duration: 2500,
    });
  });

  it('网络失败带回 errMsg 时消息透传 errMsg', async () => {
    const p = requestPost('/api/x', {});
    uniRequestOpts.fail({ errMsg: 'request:fail timeout' });
    await expect(p).rejects.toMatchObject({
      code: -1,
      message: 'request:fail timeout',
    });
    expect(uni.showToast).toHaveBeenCalledWith({
      title: 'request:fail timeout',
      icon: 'none',
      duration: 2500,
    });
  });

  it('showError=false 时业务错误不弹 toast', async () => {
    const p = requestPost('/api/x', {}, { showError: false });
    uniRequestOpts.success({
      statusCode: 200,
      data: { code: 5001, message: '参数错误' },
    });
    await expect(p).rejects.toMatchObject({ code: 5001 });
    expect(uni.showToast).not.toHaveBeenCalled();
  });
});

// 纯函数导出校验
describe('loan-mini request 工具导出', () => {
  it('getToken 读取 storage', () => {
    expect(getToken()).toBe('mini-tok');
    expect(uni.getStorageSync).toHaveBeenCalledWith(TOKEN_KEY);
  });
  it('clearToken 清除 storage', () => {
    clearToken();
    expect(uni.removeStorageSync).toHaveBeenCalledWith(TOKEN_KEY);
  });
  it('BASE_URL 在非 H5 环境指向本地网关', () => {
    expect(BASE_URL).toBe('http://localhost:8088/loan');
  });
});
