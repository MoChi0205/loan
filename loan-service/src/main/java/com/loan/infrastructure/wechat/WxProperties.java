package com.loan.infrastructure.wechat;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 微信开放平台配置（小程序端登录/后续支付预留）。
 *
 * <p>配置项（application.properties 或 Nacos 覆盖）：
 * <pre>
 * wechat.appid=wx_xxx           小程序 appid
 * wechat.secret=xxx             appsecret
 * wechat.mock=true              true 走 Mock code2session（本地/联调免真实凭证）
 * </pre>
 *
 * <p>P2-1 微信支付预留商户号/APIv3 密钥字段，本阶段不读取。
 *
 * @author loan-platform
 */
@Data
@Component
@ConfigurationProperties(prefix = "wechat")
public class WxProperties {

    /** 小程序 appid */
    private String appid = "wx_CHANGE_ME";

    /** 小程序 appsecret */
    private String secret = "";

    /** Mock 开关：true 时 jscode2session 不调微信，按 code 哈希伪造 openid */
    private boolean mock = true;

    /** 商户号（P2-1 微信支付预留） */
    private String payMchId;

    /** 商户 APIv3 密钥（P2-1 微信支付预留） */
    private String payApiV3Key;

    /** 商户证书序列号（P2-1 微信支付预留） */
    private String paySerialNo;

    /**
     * 公众号（服务号）appid —— JS-SDK 分享 / 支付 / 企微活码签名使用。
     *
     * <p><b>注意</b>：JS-SDK 的 {@code jsapi_ticket} 由<strong>公众号</strong>凭据换取，
     * 与小程序的 {@code appid/secret} 是<strong>两套不同凭证</strong>。H5 在微信浏览器内运行时
     * 调用 {@code wx.config} 必须用公众号 appid，错用小程序 appid 会报
     * {@code invalid signature}。</p>
     */
    private String oaAppid;

    /** 公众号 appsecret */
    private String oaSecret;
}
