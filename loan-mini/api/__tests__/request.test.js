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
  it('相同 GET URL 与参数并发时只发起一次请求', async () => {
    const p1 = requestGet('/api/items', { page: 1, size: 10 });
    const p2 = requestGet('/api/items', { page: 1, size: 10 });
    expect(p1).toBe(p2);
    expect(uni.request).toHaveBeenCalledTimes(1);
    uniRequestOpts.success({
      statusCode: 200,
      data: { code: 0, data: { records: [{ id: 'a' }] } },
    });
    await expect(p1).resolves.toEqual({ records: [{ id: 'a' }] });
    await expect(p2).resolves.toEqual({ records: [{ id: 'a' }] });
  });

  it('GET 请求完成后可再次发起，不同参数不合并', async () => {
    const first = requestGet('/api/items', { page: 1 });
    expect(uni.request).toHaveBeenCalledTimes(1);
    uniRequestOpts.success({ statusCode: 200, data: { code: 0, data: [] } });
    await first;

    const second = requestGet('/api/items', { page: 1 });
    const third = requestGet('/api/items', { page: 2 });
    expect(uni.request).toHaveBeenCalledTimes(3);
    const opts = uni.request.mock.calls[2][0];
    opts.success({ statusCode: 200, data: { code: 0, data: ['page-2'] } });
    const opts2 = uni.request.mock.calls[1][0];
    opts2.success({ statusCode: 200, data: { code: 0, data: ['page-1'] } });
    await expect(second).resolves.toEqual(['page-1']);
    await expect(third).resolves.toEqual(['page-2']);
  });

  it('POST 请求即使参数相同也不合并', async () => {
    const first = requestPost('/api/items', { name: 'x' });
    const second = requestPost('/api/items', { name: 'x' });
    expect(first).not.toBe(second);
    expect(uni.request).toHaveBeenCalledTimes(2);
    uni.request.mock.calls[0][0].success({ statusCode: 200, data: { code: 0, data: 1 } });
    uni.request.mock.calls[1][0].success({ statusCode: 200, data: { code: 0, data: 2 } });
    await expect(first).resolves.toBe(1);
    await expect(second).resolves.toBe(2);
  });

  it('GET 请求失败后清理并发状态，下一次可正常发起', async () => {
    const failed = requestGet('/api/items', { page: 9 });
    uniRequestOpts.fail({ errMsg: 'network down' });
    await expect(failed).rejects.toMatchObject({ code: -1 });
    const retried = requestGet('/api/items', { page: 9 });
    expect(uni.request).toHaveBeenCalledTimes(2);
    uniRequestOpts.success({ statusCode: 200, data: { code: 0, data: 'ok' } });
    await expect(retried).resolves.toBe('ok');
  });

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
