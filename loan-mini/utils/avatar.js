/**
 * 微信头像能力封装（小程序端「头像昵称填写能力」）。
 *
 * <p>微信自 2022-10 起回收 wx.getUserProfile / wx.getUserInfo 直接取头像昵称，
 * 改为「用户主动选择」：通过 <button open-type="chooseAvatar" bind:chooseavatar>
 * 拉起微信头像选择，回调 e.detail.avatarUrl 返回本地临时文件。
 *
 * <p>本模块职责：
 * - uploadAvatar(tempFilePath)：把临时文件上传到后端 /api/mini/avatar，
 *   成功返回服务端 URL（供 home.vue 写回 store）；失败返回 null（不阻断本地预览）。
 * - 后端接口未就绪时，home.vue 仍会以本地临时路径预览，刷新后由 /api/mini/me 覆盖。
 */
import { uploadImage } from './wx';

/** 头像上传接口（后端就绪后启用；404/失败时静默降级为本地预览） */
const AVATAR_UPLOAD_URL = '/api/mini/avatar';

/**
 * 上传微信头像临时文件到后端。
 *
 * @param {string} tempFilePath chooseAvatar 回调的本地临时路径
 * @returns {Promise<string|null>} 服务端 URL 或 null
 */
export async function uploadAvatar(tempFilePath) {
  if (!tempFilePath) return null;
  try {
    const url = await uploadImage({ filePath: tempFilePath, url: AVATAR_UPLOAD_URL });
    return url || null;
  } catch (e) {
    // 接口未就绪/网络失败：不阻断，home.vue 仍用本地临时路径预览
    return null;
  }
}
