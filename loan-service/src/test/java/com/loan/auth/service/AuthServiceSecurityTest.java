package com.loan.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loan.channel.mapper.ChannelUserMapper;
import com.loan.client.mapper.ClientLifecycleEventMapper;
import com.loan.exception.BusinessException;
import com.loan.infrastructure.security.JwtService;
import com.loan.infrastructure.security.LoginRsaCrypto;
import com.loan.product.mapper.BankChannelMapper;
import com.loan.staff.mapper.StaffMapper;
import com.loan.staff.entity.Staff;
import com.loan.auth.dto.LoginResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;

class AuthServiceSecurityTest {

    @Test
    void h5CustomerSmsLoginSignsMiniAppType() {
        com.loan.client.mapper.ClientProfileMapper clients = mock(com.loan.client.mapper.ClientProfileMapper.class);
        com.loan.client.entity.ClientProfile client = new com.loan.client.entity.ClientProfile();
        client.setId(19L); client.setClientCode("client-test"); client.setContactName("客户");
        when(clients.selectOne(any())).thenReturn(client);
        com.loan.sms.service.SmsService sms = mock(com.loan.sms.service.SmsService.class);
        when(sms.verifyCode("13900000001", "123456")).thenReturn(true);
        JwtService jwt = mock(JwtService.class);
        AuthService service = new AuthService(mock(StaffMapper.class), mock(ChannelUserMapper.class),
                mock(BankChannelMapper.class), jwt, mock(LoginRsaCrypto.class),
                mock(StringRedisTemplate.class, RETURNS_DEEP_STUBS), new ObjectMapper(), clients,
                mock(ClientLifecycleEventMapper.class), sms, mock(com.loan.invitation.service.InvitationService.class));
        service.customerLogin("13900000001", "123456", null);
        verify(jwt).generateToken(eq(19L), eq("CUSTOMER"), eq("client-test"), isNull(), eq("MINI_APP"));
    }

    @Test
    void staffLoginResponseNeverExposesEncryptedOrPlainPhone() {
        StaffMapper staffMapper = mock(StaffMapper.class);
        com.loan.sms.service.SmsService sms = mock(com.loan.sms.service.SmsService.class);
        JwtService jwt = mock(JwtService.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class, RETURNS_DEEP_STUBS);
        Staff staff = new Staff();
        staff.setId(28L);
        staff.setStaffCode("BOSS001");
        staff.setStaffName("张老板");
        staff.setRoleCode("BOSS");
        staff.setStatus("ACTIVE");
        staff.setPhone("encrypted-database-value");
        when(sms.verifyCode("13800000001", "123456")).thenReturn(true);
        when(staffMapper.selectOne(any())).thenReturn(staff);
        when(jwt.generateToken(any(), any(), any(), any())).thenReturn("token");

        AuthService service = new AuthService(
                staffMapper, mock(ChannelUserMapper.class), mock(BankChannelMapper.class), jwt,
                mock(LoginRsaCrypto.class), redis, new ObjectMapper(),
                mock(com.loan.client.mapper.ClientProfileMapper.class),
                mock(ClientLifecycleEventMapper.class), sms,
                mock(com.loan.invitation.service.InvitationService.class));

        LoginResponse response = service.loginByPhoneCode("13800000001", "123456", "STAFF");
        assertEquals("138****0001", response.getUser().getPhone());
        assertEquals("BOSS", response.getUser().getRoleCode());
    }

    @Test
    void externalUserMasksSessionPhone() {
        AuthService service = new AuthService(
                mock(StaffMapper.class), mock(ChannelUserMapper.class), mock(BankChannelMapper.class),
                mock(JwtService.class), mock(LoginRsaCrypto.class),
                mock(StringRedisTemplate.class), new ObjectMapper(),
                mock(com.loan.client.mapper.ClientProfileMapper.class),
                mock(ClientLifecycleEventMapper.class), mock(com.loan.sms.service.SmsService.class),
                mock(com.loan.invitation.service.InvitationService.class));
        com.loan.context.LoanUser source = new com.loan.context.LoanUser();
        source.setPhone("13800000001");
        source.setRoleCode("BOSS");
        assertEquals("138****0001", service.externalUser(source).getPhone());
    }

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
