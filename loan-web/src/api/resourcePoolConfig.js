import request from '@/utils/request';

export function listResourcePoolConfigs() {
  return request({ url: '/api/admin/resource-pool-config/list', method: 'get' });
}

export function saveResourcePoolConfig(data) {
  return request({ url: '/api/admin/resource-pool-config/save', method: 'put', data });
}
