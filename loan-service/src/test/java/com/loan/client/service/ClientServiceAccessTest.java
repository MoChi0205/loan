package com.loan.client.service;

import com.loan.client.mapper.ClientProfileMapper;
import com.loan.client.model.ClientUpdateRequest;
import com.loan.client.security.ClientAccessGuard;
import com.loan.common.ResultCode;
import com.loan.common.service.BusinessNameService;
import com.loan.context.LoanUser;
import com.loan.exception.BusinessException;
import com.loan.invitation.mapper.InvitationMapper;
import com.loan.personal.mapper.PersonalProfileMapper;
import com.loan.personal.service.PersonalProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ClientServiceAccessTest {

    private ClientProfileMapper clientMapper;
    private PersonalProfileMapper personalMapper;
    private ClientAccessGuard accessGuard;
    private ClientService service;

    @BeforeEach
    void setUp() {
        clientMapper = mock(ClientProfileMapper.class);
        personalMapper = mock(PersonalProfileMapper.class);
        accessGuard = mock(ClientAccessGuard.class);
        service = new ClientService(clientMapper, personalMapper, mock(PersonalProfileService.class),
                mock(InvitationMapper.class), mock(BusinessNameService.class), accessGuard);
    }

    @Test
    @DisplayName("客户编辑在任何写入前执行对象级权限校验")
    void updateStopsBeforeWriteWhenObjectAccessDenied() {
        LoanUser adviser = new LoanUser();
        adviser.setUserType(LoanUser.TYPE_STAFF);
        adviser.setRoleCode("ADVISER");
        adviser.setUserNo("A001");
        ClientUpdateRequest request = new ClientUpdateRequest();
        request.setContactName("越权修改");
        when(accessGuard.requireWritable(adviser, "C001"))
                .thenThrow(new BusinessException(ResultCode.FORBIDDEN, "无权修改该客户资料"));

        BusinessException denied = assertThrows(BusinessException.class,
                () -> service.updateClientDetail("C001", request, adviser));

        assertEquals(ResultCode.FORBIDDEN.getCode(), denied.getCode());
        verify(accessGuard).requireWritable(adviser, "C001");
        verify(clientMapper, never()).updateById(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(personalMapper);
    }
}
