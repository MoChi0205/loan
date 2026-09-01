/**
 * 登录页商务风景图预设（可配置）。
 *
 * <p>使用方式：通过 .env 的 VITE_LOGIN_SCENERY 切换：
 *  - skyline   金融区天际线（默认）
 *  - office    玻璃写字楼仰视
 *  - twilight  商务建筑黄昏剪影
 *  - none      纯色渐变（无图）
 *  - custom    自定义（需同时配置 VITE_LOGIN_BG_URL）
 *
 * <p>新增风景图：把 .jpg 放进本目录，在 sceneries 映射里加一行即可。
 */
const images = import.meta.glob('./*.jpg', { eager: true });

export const sceneries = {
  skyline: images['./skyline.jpg']?.default,
  office: images['./office.jpg']?.default,
  twilight: images['./twilight.jpg']?.default,
};

export const sceneryKeys = Object.keys(sceneries);
