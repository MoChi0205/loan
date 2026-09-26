package com.loan.infrastructure.wechat;

import com.loan.common.ResultCode;
import com.loan.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 微信小程序 getPhoneNumber 动态 code 换手机号。
 * 测试环境仅接受显式 mock-phone:{手机号}，不会把任意字符串当作手机号。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WxPhoneNumberService {

    private static final String ACCESS_TOKEN_URL =
            "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid={appid}&secret={secret}";
    private static final String PHONE_URL =
            "https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token={accessToken}";

    private final WxProperties wxProperties;
    private final RestTemplate restTemplate = new RestTemplate();

    public String resolvePhone(String phoneCode) {
        if (!StringUtils.hasText(phoneCode)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "微信手机号 code 必填");
        }
        if (wxProperties.isMockEnabled()) {
            if (!phoneCode.startsWith("mock-phone:")) {
                throw new BusinessException(ResultCode.PARAM_ERROR,
                        "测试环境请使用 mock-phone:{手机号} 手机号凭证");
            }
            String phone = phoneCode.substring("mock-phone:".length()).trim();
            if (!phone.matches("1\\d{10}")) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "测试手机号格式不正确");
            }
            return phone;
        }
        if (!StringUtils.hasText(wxProperties.getAppid())
                || "wx_CHANGE_ME".equals(wxProperties.getAppid())
                || !StringUtils.hasText(wxProperties.getSecret())) {
            throw new BusinessException(ResultCode.RULE_CONFIG_ERROR, "未配置真实微信 appid/secret");
        }
        try {
            Map<?, ?> tokenResp = restTemplate.getForObject(ACCESS_TOKEN_URL, Map.class,
                    wxProperties.getAppid(), wxProperties.getSecret());
            Object accessToken = tokenResp == null ? null : tokenResp.get("access_token");
            if (accessToken == null || !StringUtils.hasText(String.valueOf(accessToken))) {
                throw new BusinessException(ResultCode.THIRD_PARTY_ERROR, "微信 access_token 获取失败");
            }
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, String> body = new LinkedHashMap<>();
            body.put("code", phoneCode);
            Map<?, ?> phoneResp = restTemplate.postForObject(PHONE_URL,
                    new HttpEntity<>(body, headers), Map.class, accessToken);
            Map<?, ?> phoneInfo = phoneResp == null ? Collections.emptyMap() :
                    asMap(phoneResp.get("phone_info"));
            Object phone = phoneInfo.get("purePhoneNumber");
            if (phone == null || !StringUtils.hasText(String.valueOf(phone))) {
                Object msg = phoneResp == null ? null : phoneResp.get("errmsg");
                throw new BusinessException(ResultCode.THIRD_PARTY_ERROR,
                        "微信手机号获取失败：" + (msg == null ? "动态 code 已失效" : msg));
            }
            return String.valueOf(phone);
        } catch (RestClientException e) {
            log.error("调用微信手机号接口失败", e);
            throw new BusinessException(ResultCode.THIRD_PARTY_ERROR, "微信手机号服务暂不可用");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<?, ?> asMap(Object value) {
        return value instanceof Map ? (Map<?, ?>) value : Collections.emptyMap();
    }
}
