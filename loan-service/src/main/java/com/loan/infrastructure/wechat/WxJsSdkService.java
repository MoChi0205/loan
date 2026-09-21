package com.loan.infrastructure.wechat;

import com.loan.common.ResultCode;
import com.loan.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 微信 JS-SDK 签名服务（H5 在微信浏览器内调用 {@code wx.config} 所需）。
 *
 * <p>流程：{@code access_token}（公众号凭据）→ {@code jsapi_ticket} → 按页面 URL 生成
 * {@code signature}。签名参数 {@code nonceStr / timestamp / signature} 由后端下发，
 * 前端配合 {@code appId} 注入 {@code wx.config}。</p>
 *
 * <p>缓存：access_token / jsapi_ticket 各 7200s，采用<strong>单机内存缓存</strong>
 * （到期前复用，提前 200s 刷新）。多实例部署需改为 Redis 共享，避免各实例重复获取
 * 触发微信频次限制。</p>
 *
 * @author loan-platform
 */
@Slf4j
@Service
public class WxJsSdkService {

    private static final String TOKEN_URL =
            "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid={appid}&secret={secret}";
    private static final String TICKET_URL =
            "https://api.weixin.qq.com/cgi-bin/ticket/getticket?access_token={token}&type=jsapi";

    private final WxProperties wxProperties;
    private final RestTemplate restTemplate = new RestTemplate();

    /** access_token 缓存（值 + 过期时间戳 ms） */
    private final AtomicReference<String> tokenCache = new AtomicReference<>();
    private final AtomicLong tokenExpireAt = new AtomicLong(0);

    /** jsapi_ticket 缓存（值 + 过期时间戳 ms） */
    private final AtomicReference<String> ticketCache = new AtomicReference<>();
    private final AtomicLong ticketExpireAt = new AtomicLong(0);

    public WxJsSdkService(WxProperties wxProperties) {
        this.wxProperties = wxProperties;
    }

    /**
     * 生成 JS-SDK 签名参数。
     *
     * @param url 当前页面 URL（<b>不含</b> {@code #} 及其后部分；前端需自行截断）
     * @return { appId, timestamp, nonceStr, signature }
     */
    public Map<String, String> signature(String url) {
        if (!StringUtils.hasText(url)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "签名 url 必填");
        }
        if (!StringUtils.hasText(wxProperties.getOaAppid())
                || "wx_CHANGE_ME".equals(wxProperties.getOaAppid())
                || !StringUtils.hasText(wxProperties.getOaSecret())) {
            throw new BusinessException(ResultCode.RULE_CONFIG_ERROR, "未配置真实公众号 appid/secret");
        }
        String ticket = getJsapiTicket();
        String nonceStr = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        long timestamp = System.currentTimeMillis() / 1000;
        String raw = "jsapi_ticket=" + ticket
                + "&noncestr=" + nonceStr
                + "&timestamp=" + timestamp
                + "&url=" + url;
        String sig = sha1(raw);

        Map<String, String> map = new HashMap<>(4);
        map.put("appId", wxProperties.getOaAppid());
        map.put("timestamp", String.valueOf(timestamp));
        map.put("nonceStr", nonceStr);
        map.put("signature", sig);
        return map;
    }

    private String getJsapiTicket() {
        long now = System.currentTimeMillis();
        String t = ticketCache.get();
        if (t != null && now < ticketExpireAt.get()) {
            return t;
        }
        synchronized (this) {
            long now2 = System.currentTimeMillis();
            t = ticketCache.get();
            if (t != null && now2 < ticketExpireAt.get()) {
                return t;
            }
            String token = getAccessToken();
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.getForObject(TICKET_URL, Map.class, token);
            if (resp == null || !"0".equals(String.valueOf(resp.get("errcode")))) {
                log.warn("getticket 返回异常: {}", resp);
                throw new BusinessException(ResultCode.THIRD_PARTY_ERROR, "微信 JS-SDK ticket 获取失败");
            }
            String ticket = String.valueOf(resp.get("ticket"));
            int expires = toInt(resp.get("expires_in"), 7200);
            ticketCache.set(ticket);
            ticketExpireAt.set(System.currentTimeMillis() + (expires - 200L) * 1000L);
            return ticket;
        }
    }

    private String getAccessToken() {
        long now = System.currentTimeMillis();
        String t = tokenCache.get();
        if (t != null && now < tokenExpireAt.get()) {
            return t;
        }
        synchronized (this) {
            long now2 = System.currentTimeMillis();
            t = tokenCache.get();
            if (t != null && now2 < tokenExpireAt.get()) {
                return t;
            }
            Map<String, String> vars = new HashMap<>(2);
            vars.put("appid", wxProperties.getOaAppid());
            vars.put("secret", wxProperties.getOaSecret());
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.getForObject(TOKEN_URL, Map.class, vars);
            if (resp == null || !StringUtils.hasText(String.valueOf(resp.get("access_token")))) {
                log.warn("token 返回异常: {}", resp);
                throw new BusinessException(ResultCode.THIRD_PARTY_ERROR, "微信 access_token 获取失败");
            }
            String token = String.valueOf(resp.get("access_token"));
            int expires = toInt(resp.get("expires_in"), 7200);
            tokenCache.set(token);
            tokenExpireAt.set(System.currentTimeMillis() + (expires - 200L) * 1000L);
            return token;
        }
    }

    private static int toInt(Object o, int def) {
        if (o == null) {
            return def;
        }
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static String sha1(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 not supported", e);
        }
    }

}
