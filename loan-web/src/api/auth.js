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

export function getCaptcha() {
  return request({ url: '/api/auth/captcha', method: 'get', params: { _: Date.now() } });
}

/**
 * 渠道账号登录。password 必须使用登录公钥进行 RSA PKCS#1 加密。
 */
export function channelLogin(data) {
  return request({
    url: '/api/auth/channel-login',
    method: 'post',
    data,
  });
}
export function passwordLogin(data) {
  return request({ url: '/api/auth/password-login', method: 'post', data });
}
export function sendLoginCode(phone, captchaId, captchaCode, scene = 'LOGIN') {
  return request({ url: '/api/sms/send-code', method: 'post', data: { phone, captchaId, captchaCode, scene } });
}

export function codeLogin(data) {
  return request({ url: '/api/auth/code-login', method: 'post', data });
}

export function resetPassword(data) {
  return request({ url: '/api/auth/reset-password', method: 'post', data });
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
