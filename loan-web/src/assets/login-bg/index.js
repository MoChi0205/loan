/**
 * 登录页商务风景图预设（可配置）。
 *
 * <p>图片以静态资源方式存放于 public/login-bg/，构建时直接拷贝到产物根目录，
 * 不进入 JS 打包图（避免 5MB+ 背景图被哈希打包进首屏产物，且可独立缓存 / CDN 加速）。
 *
 * <p>使用方式：通过 .env 的 VITE_LOGIN_SCENERY 切换：
 *  - skyline   金融区天际线（默认）
 *  - office    玻璃写字楼仰视
 *  - twilight  商务建筑黄昏剪影
 *  - none      纯色渐变（无图）
 *  - custom    自定义（需同时配置 VITE_LOGIN_BG_URL）
 */
const BASE = import.meta.env.BASE_URL || '/';

export const sceneries = {
  skyline: `${BASE}login-bg/skyline.jpg`,
  office: `${BASE}login-bg/office.jpg`,
  twilight: `${BASE}login-bg/twilight.jpg`,
  inkDark: `${BASE}login-bg/login-bg-dark.png`,
  inkLight: `${BASE}login-bg/login-bg-light.png`,
};

export const sceneryKeys = Object.keys(sceneries);
