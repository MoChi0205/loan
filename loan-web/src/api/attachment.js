import request from '@/utils/request';

/** 服务资料分页，用于审批资料选择与档案复用。 */
export function pageAttachments(params) {
  return request({ url: '/api/admin/attachment/page', method: 'get', params });
}
