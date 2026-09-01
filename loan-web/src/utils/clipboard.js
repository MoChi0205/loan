/**
 * 剪贴板工具：统一复制能力，兼容非 HTTPS / localhost 环境。
 *
 * <p>背景：`navigator.clipboard` 仅在安全上下文（HTTPS 或 localhost）可用，
 * 生产 http 部署下直接调用会抛错，导致各页复制功能全部失败。
 * 本工具优先 Clipboard API，失败时降级 `document.execCommand('copy')`（textarea 方案）。
 *
 * 用法：
 *   import { copyText } from '@/utils/clipboard';
 *   try { await copyText(text); ElMessage.success('已复制'); }
 *   catch { ElMessage.warning('复制失败'); }
 */
export async function copyText(text) {
  const value = typeof text === 'string' ? text : String(text ?? '');
  // 1) Clipboard API（仅安全上下文可用）
  if (navigator.clipboard && window.isSecureContext) {
    try {
      await navigator.clipboard.writeText(value);
      return;
    } catch (e) {
      // 权限被拒等 → 走降级
    }
  }
  // 2) execCommand 降级（http 生产部署 / 权限受限场景）
  const ta = document.createElement('textarea');
  ta.value = value;
  ta.setAttribute('readonly', '');
  ta.style.position = 'fixed';
  ta.style.left = '-9999px';
  ta.style.top = '0';
  ta.style.opacity = '0';
  document.body.appendChild(ta);
  ta.select();
  ta.setSelectionRange(0, ta.value.length);
  let ok = false;
  try {
    ok = document.execCommand('copy');
  } catch (e) {
    ok = false;
  } finally {
    document.body.removeChild(ta);
  }
  if (!ok) throw new Error('copy failed');
}
