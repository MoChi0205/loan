package com.loan.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loan.channel.mapper.ChannelUserMapper;
import com.loan.client.mapper.ClientLifecycleEventMapper;
import com.loan.exception.BusinessException;
import com.loan.infrastructure.security.JwtService;
import com.loan.infrastructure.security.LoginRsaCrypto;
import com.loan.product.mapper.BankChannelMapper;
import com.loan.staff.mapper.StaffMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuthServiceSecurityTest {

    @Test
    void fixedChannelBypassPasswordIsRejectedBeforeAccountLookup() {
        ChannelUserMapper channelUsers = mock(ChannelUserMapper.class);
        LoginRsaCrypto rsa = mock(LoginRsaCrypto.class);
        when(rsa.decryptBase64("loan-sim-pwd")).thenReturn(null);

        AuthService service = new AuthService(
                mock(StaffMapper.class), channelUsers, mock(BankChannelMapper.class),
                mock(JwtService.class), rsa, mock(StringRedisTemplate.class), new ObjectMapper(),
                mock(com.loan.client.mapper.ClientProfileMapper.class),
                mock(ClientLifecycleEventMapper.class), mock(com.loan.sms.service.SmsService.class),
                mock(com.loan.invitation.service.InvitationService.class));

        assertThrows(BusinessException.class,
                () -> service.passwordLogin("13911112222", "loan-sim-pwd", "CHANNEL"));
        verifyNoInteractions(channelUsers);
    }
}
