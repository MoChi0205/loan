import request from '@/utils/request';

/**
 * 认证接口（对接 loan-service /api/auth）。
 */
export function getPublicKey() {
  return request({
    url: '/api/auth/public-key',
    method: 'get',
  });
}

export function login(data) {
  return request({
    url: '/api/auth/login',
    method: 'post',
    data,
  });
}

/**
 * 渠道账号登录（阶段一联调：密码传约定模拟串 loan-sim-pwd，后端旁路 BCrypt，T11/D21）。
 * 正式接入 RSA 加密后改为：password = JSEncrypt 加密 Base64。
 */
export function channelLogin(data) {
  return request({
    url: '/api/auth/channel-login',
    method: 'post',
    data,
  });
}

export function logout() {
  return request({
    url: '/api/auth/logout',
    method: 'post',
  });
}

export function getMe() {
  return request({
    url: '/api/auth/me',
    method: 'get',
  });
}
