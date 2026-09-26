package com.loan.infrastructure.wechat;

import com.loan.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WxPhoneNumberServiceTest {

    @Test
    void mockModeAcceptsOnlyExplicitPhoneCredential() {
        WxProperties properties = new WxProperties();
        properties.setMockEnabled(true);
        WxPhoneNumberService service = new WxPhoneNumberService(properties);

        assertEquals("13800138000", service.resolvePhone("mock-phone:13800138000"));
        assertThrows(BusinessException.class, () -> service.resolvePhone("13800138000"));
        assertThrows(BusinessException.class, () -> service.resolvePhone("mock-phone:null"));
    }

    @Test
    void realModeRequiresWechatCredentialsBeforeCallingRemoteApi() {
        WxProperties properties = new WxProperties();
        properties.setMockEnabled(false);
        properties.setAppid("");
        properties.setSecret("");
        WxPhoneNumberService service = new WxPhoneNumberService(properties);

        assertThrows(BusinessException.class, () -> service.resolvePhone("dynamic-code"));
    }
}
