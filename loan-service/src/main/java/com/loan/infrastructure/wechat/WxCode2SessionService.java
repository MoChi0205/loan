package com.loan.infrastructure.wechat;

import com.loan.common.ResultCode;
import com.loan.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 微信小程序 code2session 客户端（wx.login → code → openid）。
 *
 * <p>接口：{@code GET https://api.weixin.qq.com/sns/jscode2session?appid=&secret=&js_code=&grant_type=authorization_code}
 * 返回 {@code openid/session_key/unionid} 或 {@code errcode/errmsg}。
 *
 * <p>Mock 开关（{@code wechat.mock=true}）：不调微信，按 code 哈希生成稳定 openid（前缀 mock_），
 * 供本地/联调无真实凭证时打通登录链路。
 *
 * @author loan-platform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WxCode2SessionService {

    /** 微信 jscode2session 接口地址 */
    private static final String CODE2SESSION_URL =
            "https://api.weixin.qq.com/sns/jscode2session?appid={appid}&secret={secret}&js_code={js_code}&grant_type=authorization_code";

    private final WxProperties wxProperties;

    /** 轻量 HTTP 客户端（1 个接口，无需引入微信 SDK） */
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 用 wx.login 的 code 换 openid（等值查询用 hash，防明文直存）。
     *
     * @param code wx.login 返回的临时凭证
     * @return openid（明文仅内存使用，落库只存 hash）
     */
    public String code2Session(String code) {
        if (!StringUtils.hasText(code)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "wx.login code 必填");
        }
        if (wxProperties.isMock()) {
            return mockOpenid(code);
        }
        if (!StringUtils.hasText(wxProperties.getAppid())
                || "wx_CHANGE_ME".equals(wxProperties.getAppid())) {
            throw new BusinessException(ResultCode.RULE_CONFIG_ERROR, "未配置 wechat.appid");
        }
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("appid", wxProperties.getAppid());
        vars.put("secret", wxProperties.getSecret());
        vars.put("js_code", code);
        try {
            Map<?, ?> resp = restTemplate.getForObject(CODE2SESSION_URL, Map.class, vars);
            if (resp == null) {
                throw new BusinessException(ResultCode.THIRD_PARTY_ERROR, "微信登录失败：无响应");
            }
            Object openid = resp.get("openid");
            if (!StringUtils.hasText(String.valueOf(openid))) {
                log.warn("jscode2session 返回错误: {}", resp);
                Object err = resp.get("errmsg");
                String errMsg = err != null ? String.valueOf(err) : "code 已失效";
                throw new BusinessException(ResultCode.THIRD_PARTY_ERROR, "微信登录失败：" + errMsg);
            }
            return String.valueOf(openid);
        } catch (RestClientException e) {
            log.error("调用微信 jscode2session 失败", e);
            throw new BusinessException(ResultCode.THIRD_PARTY_ERROR, "微信登录服务暂不可用");
        }
    }

    /**
     * Mock 模式：按 code 哈希伪造稳定 openid（mock_ + 32 位），同 code 命中同 openid。
     *
     * @param code 临时凭证
     * @return Mock openid
     */
    private String mockOpenid(String code) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256")
                    .digest(code.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder("mock_");
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.substring(0, 40);
        } catch (Exception e) {
            throw new IllegalStateException("Mock openid 生成失败", e);
        }
    }
}
