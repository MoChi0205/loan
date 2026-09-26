package com.loan.infrastructure.wechat;

import com.loan.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WxCode2SessionServiceSecurityTest {

    @Test
    void missingRealCredentialsNeverFallsBackToMockIdentity() {
        WxProperties properties = new WxProperties();
        properties.setAppid("wx_CHANGE_ME");
        properties.setSecret("");

        WxCode2SessionService service = new WxCode2SessionService(properties);

        assertThrows(BusinessException.class, () -> service.code2Session("any-code"));
    }

    @Test
    void nullOpenidFromWechatIsRejected() throws Exception {
        WxProperties properties = new WxProperties();
        properties.setAppid("wx-real-appid");
        properties.setSecret("real-secret");
        WxCode2SessionService service = new WxCode2SessionService(properties);
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.getForObject(any(String.class), eq(java.util.Map.class), anyMap()))
                .thenReturn(Collections.singletonMap("errmsg", "invalid code"));

        Field field = WxCode2SessionService.class.getDeclaredField("restTemplate");
        field.setAccessible(true);
        field.set(service, restTemplate);

        assertThrows(BusinessException.class, () -> service.code2Session("expired-code"));
    }
}
