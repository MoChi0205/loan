package com.loan.common.service;

import com.loan.client.entity.ClientProfile;
import com.loan.client.mapper.ClientProfileMapper;
import com.loan.invitation.entity.Invitation;
import com.loan.product.entity.BankProduct;
import com.loan.product.mapper.BankProductMapper;
import com.loan.staff.entity.Staff;
import com.loan.staff.mapper.StaffMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 业务名称批量装配测试：去空去重、名称优先级及邀请批量查询。 */
@ExtendWith(MockitoExtension.class)
class BusinessNameServiceTest {

    @Mock private StaffMapper staffMapper;
    @Mock private ClientProfileMapper clientProfileMapper;
    @Mock private BankProductMapper bankProductMapper;
    private BusinessNameService service;

    @BeforeEach
    void setUp() {
        service = new BusinessNameService(staffMapper, clientProfileMapper, bankProductMapper);
    }

    @Test
    void emptyCodesDoNotQueryDatabase() {
        assertEquals(Collections.emptyMap(), service.staffNames(Arrays.asList(null, " ")));
        assertEquals(Collections.emptyMap(), service.clientNames(Collections.emptyList()));
        assertEquals(Collections.emptyMap(), service.productNames(null));
        verifyNoInteractions(staffMapper, clientProfileMapper, bankProductMapper);
    }

    @Test
    void clientNameUsesEnterpriseNameThenContactName() {
        ClientProfile enterprise = client("C1", "甲公司", "张三");
        ClientProfile personal = client("C2", null, "李四");
        when(clientProfileMapper.selectList(any())).thenReturn(Arrays.asList(enterprise, personal));

        Map<String, String> result = service.clientNames(Arrays.asList(" C1 ", "C1", "C2"));

        assertEquals("甲公司", result.get("C1"));
        assertEquals("李四", result.get("C2"));
        verify(clientProfileMapper).selectList(any());
    }

    @Test
    void referrerNamesBatchCustomersAndHistoricalStaffIds() {
        Invitation customer = invitation("INV1", "CUSTOMER", "C1", null);
        Invitation adviser = invitation("INV2", "ADVISER", null, 8L);
        when(clientProfileMapper.selectList(any())).thenReturn(
                Collections.singletonList(client("C1", null, "客户甲")));
        Staff staff = new Staff();
        staff.setId(8L);
        staff.setStaffName("顾问乙");
        when(staffMapper.selectBatchIds(any())).thenReturn(Collections.singletonList(staff));

        Map<String, String> result = service.referrerNames(Arrays.asList(customer, adviser));

        assertEquals("客户甲", result.get("INV1"));
        assertEquals("顾问乙", result.get("INV2"));
        assertFalse(result.containsKey("8"));
        verify(clientProfileMapper).selectList(any());
        verify(staffMapper).selectBatchIds(any());
    }

    private ClientProfile client(String code, String enterpriseName, String contactName) {
        ClientProfile client = new ClientProfile();
        client.setClientCode(code);
        client.setEnterpriseName(enterpriseName);
        client.setContactName(contactName);
        return client;
    }

    private Invitation invitation(String code, String type, String clientCode, Long staffId) {
        Invitation invitation = new Invitation();
        invitation.setInvitationCode(code);
        invitation.setReferrerType(type);
        invitation.setReferrerClientCode(clientCode);
        invitation.setReferrerId(staffId);
        return invitation;
    }
}
