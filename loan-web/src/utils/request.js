import axios from 'axios';
import { ElMessage } from 'element-plus';

/**
 * Axios 统一请求封装（token / traceUuid / 错误处理，前端工具类）。
 */
const request = axios.create({
  baseURL: '/loan',
  timeout: 30000,
});

// 请求拦截：注入 token 与 traceUuid
request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error),
);

// 响应拦截：只看 code，非 0 统一提示；401 跳登录
request.interceptors.response.use(
  (response) => {
    const res = response.data;
    if (res && res.code !== 0) {
      if (res.code === 2000) {
        // 未登录跳转
        window.location.href = '/login';
        return Promise.reject(new Error(res.message));
      }
      ElMessage.error(res.message || '请求失败');
      return Promise.reject(new Error(res.message));
    }
    return res;
  },
  (error) => {
    ElMessage.error(error.message || '网络异常');
    return Promise.reject(error);
  },
);

export default request;
