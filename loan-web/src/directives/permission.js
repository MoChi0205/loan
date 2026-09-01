/**
 * 权限检查器（全局可注入；未注入时默认放行，便于开发期）。
 *
 * <p>接入真实权限后，调用 setPermissionChecker 注入：
 *   import { setPermissionChecker } from '@/directives/permission';
 *   setPermissionChecker((code) => userStore.permissions.includes(code));
 */
let checker = () => true;

export function setPermissionChecker(fn) {
  checker = typeof fn === 'function' ? fn : () => true;
}

export function hasPermission(code) {
  if (!code) return true;
  if (Array.isArray(code)) return code.every((c) => checker(c));
  return checker(code);
}

export default {
  name: 'permission',
  mounted(el, binding) {
    if (!hasPermission(binding.value)) {
      el.parentNode?.removeChild(el);
    }
  },
};
