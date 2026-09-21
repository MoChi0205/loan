package com.loan.infrastructure.wechat;

import com.loan.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class WxCode2SessionServiceSecurityTest {

    @Test
    void missingRealCredentialsNeverFallsBackToMockIdentity() {
        WxProperties properties = new WxProperties();
        properties.setAppid("wx_CHANGE_ME");
        properties.setSecret("");

        WxCode2SessionService service = new WxCode2SessionService(properties);

        assertThrows(BusinessException.class, () -> service.code2Session("any-code"));
    }
}
