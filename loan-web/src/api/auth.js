import request from '@/utils/request';

/**
 * 认证接口（对接 loan-service /api/auth）。
 */
export function getPublicKey() {
  return silentRequest({
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
/**
 * 登录类接口统一标记 `__loanSilent`：由登录页自己弹一次明确提示，
 * 避免拦截器与页面各弹一次造成重复或无提示。
 */
function silentRequest(config) {
  return request({ ...config, __loanSilent: true });
}

export function channelLogin(data) {
  return silentRequest({
    url: '/api/auth/channel-login',
    method: 'post',
    data,
  });
}
export function passwordLogin(data) {
  return silentRequest({ url: '/api/auth/password-login', method: 'post', data });
}
export function sendLoginCode(phone, captchaId, captchaCode, scene = 'LOGIN') {
  return silentRequest({ url: '/api/sms/send-code', method: 'post', data: { phone, captchaId, captchaCode, scene } });
}

export function codeLogin(data) {
  return silentRequest({ url: '/api/auth/code-login', method: 'post', data });
}

export function resetPassword(data) {
  return silentRequest({ url: '/api/auth/reset-password', method: 'post', data });
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
