/**
 * 微信 JS-SDK（H5）初始化工具。
 *
 * 流程：动态加载 jweixin → 按当前页 URL 向后端申请签名 → wx.config 注入。
 * 仅 H5（微信浏览器）生效；微信小程序宿主无 window.wx，调用自动跳过。
 *
 * 后端签名接口：GET /api/mini/wechat/jssdk/signature?url=当前页URL
 * （新增于 loan-service WxJsSdkController / WxJsSdkService，需配置 wechat.oaAppid/oaSecret）
 *
 * 关键坑（见决策台账 D17）：JS-SDK 的 jsapi_ticket 由【公众号】凭据换取，
 * 与小程序 appid 是两套凭证，必须配公众号 appid，否则 invalid signature。
 */

import { requestGet } from '../api/request';

const JWEIXIN_CDN = 'https://res.wx.qq.com/open/js/jweixin-1.6.0.js';
let jweixinPromise = null;
let lastUrl = '';

/** 动态加载 jweixin SDK（H5 仅一次） */
function loadJWeixin() {
  // #ifndef H5
  return Promise.resolve(null);
  // #endif
  // #ifdef H5
  if (typeof window !== 'undefined' && window.wx) return Promise.resolve(window.wx);
  if (jweixinPromise) return jweixinPromise;
  jweixinPromise = new Promise((resolve, reject) => {
    const s = document.createElement('script');
    s.src = JWEIXIN_CDN;
    s.onload = () => resolve(window.wx);
    s.onerror = () => reject(new Error('微信 JS-SDK 加载失败'));
    document.head.appendChild(s);
  });
  return jweixinPromise;
  // #endif
}

/**
 * 初始化当前页 JS-SDK（签名 + wx.config）。
 *
 * @param {string[]} [jsApiList] 需要的接口，默认分享类
 * @param {Object}   [shareData] 分享内容 { title, desc, link, imgUrl }
 * @returns {Promise<void>}
 */
export async function initWxJsSdk(
  jsApiList = ['updateAppMessageShareData', 'updateTimelineShareData'],
  shareData = null,
) {
  // #ifndef H5
  return;
  // #endif
  // #ifdef H5
  const wx = await loadJWeixin().catch(() => null);
  if (!wx) return;
  const url = (typeof window !== 'undefined' && window.location ? window.location.href : '').split('#')[0];
  if (!url) return;
  // 同页已配置且本次未带分享内容 → 跳过重复请求
  if (url === lastUrl && !shareData) return;
  lastUrl = url;
  try {
    const cfg = await requestGet('/api/mini/wechat/jssdk/signature', { url });
    wx.config({
      debug: false,
      appId: cfg.appId,
      timestamp: Number(cfg.timestamp),
      nonceStr: cfg.nonceStr,
      signature: cfg.signature,
      jsApiList,
    });
    wx.ready(() => {
      if (shareData) applyShare(wx, shareData);
    });
    wx.error((err) => console.warn('[wx-jssdk] config error', err));
  } catch (e) {
    console.warn('[wx-jssdk] 签名获取失败', e);
  }
  // #endif
}

/** 设置分享内容（需在 wx.ready 后调用） */
function applyShare(wx, shareData) {
  if (typeof wx.updateAppMessageShareData === 'function') {
    wx.updateAppMessageShareData(shareData);
  }
  if (typeof wx.updateTimelineShareData === 'function') {
    wx.updateTimelineShareData(shareData);
  }
}

/**
 * 单独设置分享内容（页面已有 wx.config 时直接调用）。
 * @param {Object} shareData { title, desc, link, imgUrl }
 */
export function setWxShare(shareData) {
  // #ifndef H5
  return;
  // #endif
  // #ifdef H5
  loadJWeixin().then((wx) => {
    if (wx) wx.ready(() => applyShare(wx, shareData));
  }).catch(() => {});
  // #endif
}

export default initWxJsSdk;
