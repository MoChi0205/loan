/**
 * OCR 接口（对接 loan-service /api/admin/ocr，T16 Web 材料识别）。
 */
import request from '@/utils/request';

/** 提取字段定义列表（按客群） */
export function ocrFieldDefs(customerGroup) {
  return request({
    url: '/api/admin/ocr/field-defs',
    method: 'get',
    params: { customerGroup },
  });
}

/** 材料识别（multipart：file + bizType + customerGroup） */
export function ocrRecognize(formData) {
  return request({
    url: '/api/admin/ocr/recognize',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000,
  });
}
