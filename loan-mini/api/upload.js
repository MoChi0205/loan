/**
 * 小程序材料上传接口（G3：匹配/认证/诊断材料真实数据流）。
 *
 * 契约对齐后端 MiniUploadController：
 * - POST /api/mini/upload  上传（字段名 file，返回 {fileKey, fileName, fileSize, url}）
 * - GET  /api/mini/upload/{fileKey}  回传文件（inline 预览）
 *
 * 底层走 utils/wx.js 的 uploadImage（uni.uploadFile 封装，自动带鉴权头、解包 Result.data）。
 */
import { BASE_URL } from './request';
import { uploadImage } from '../utils/wx';

/** 上传接口完整地址（H5 走相对路径经 vite 代理；小程序走绝对 BASE_URL） */
const UPLOAD_URL = `${BASE_URL}/api/mini/upload`;

/**
 * 上传一份材料。
 *
 * 响应（uploadImage 已解包 Result.data）含 fileKey/fileName/fileSize/url，
 * 以及 OCR 回灌相关字段：ocrApplied(boolean) / extractedFields(数组) / mergedCount(int) / ocrRecordId(long)。
 * 调用方应据此做「OCR 是否已识别」双分支回显，禁止在 ocrApplied=false 时承诺「已识别 N 项」。
 *
 * @param {string} filePath 本地临时文件路径（uni.chooseImage / chooseMessageFile 返回）
 * @param {Object} [opts]
 * @param {string} [opts.bizType] 资料类型：
 *        ID_CARD / BUSINESS_LICENSE / FINANCIAL_STATEMENT / CONTRACT / DUE_DILIGENCE / OTHER
 * @param {string} [opts.clientCode] 客户编码（不传则用登录态）
 * @param {string} [opts.reportNo] 关联报告编号（诊断材料回灌用，T2）
 * @returns {Promise<{fileKey:string, fileName:string, fileSize:number, url:string,
 *          ocrApplied:boolean, extractedFields:Array, mergedCount:number, ocrRecordId:number}>}
 */
export function uploadMaterial(filePath, { bizType, clientCode, reportNo } = {}) {
  const formData = {};
  if (bizType) formData.bizType = bizType;
  if (clientCode) formData.clientCode = clientCode;
  if (reportNo) formData.reportNo = reportNo;
  return uploadImage({ filePath, url: UPLOAD_URL, name: 'file', formData });
}

/**
 * 材料访问地址（用于 uni.previewImage / 诊断页刷新后引用）。
 *
 * @param {string} fileKey 上传返回的文件标识
 * @returns {string} 完整/相对 URL（同 BASE_URL 规则）
 */
export function materialUrl(fileKey) {
  return `${BASE_URL}/api/mini/upload/${fileKey}`;
}
