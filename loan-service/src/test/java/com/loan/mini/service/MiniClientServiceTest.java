package com.loan.mini.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.loan.client.entity.ClientProfile;
import com.loan.client.entity.ClientLifecycleEvent;
import com.loan.client.mapper.ClientProfileMapper;
import com.loan.client.mapper.ClientLifecycleEventMapper;
import com.loan.client.service.ClientAllocationService;
import com.loan.common.service.BusinessNameService;
import com.loan.context.LoanUser;
import com.loan.lead.mapper.LeadAllocationRecordMapper;
import com.loan.staff.mapper.StaffMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

/** 小程序直接建客户档案仍进入未分配池；线索录入的自动归属由 LeadService 负责。 */
@ExtendWith(MockitoExtension.class)
class MiniClientServiceTest {

    @Mock private ClientProfileMapper clientProfileMapper;
    @Mock private ClientLifecycleEventMapper lifecycleEventMapper;
    @Mock private LeadAllocationRecordMapper allocationRecordMapper;
    @Mock private StaffMapper staffMapper;
    @Mock private ClientAllocationService clientAllocationService;
    @Mock private BusinessNameService businessNameService;

    private MiniClientService service;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "test"), ClientProfile.class);
    }

    @BeforeEach
    void setUp() {
        service = new MiniClientService(clientProfileMapper, lifecycleEventMapper, allocationRecordMapper,
                staffMapper, clientAllocationService, businessNameService);
    }

    @Test
    void createAssignsNewCustomerToCreator() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("entName", "测试企业");
        payload.put("contactPhone", "13800138000");
        payload.put("creditCode", "91330100MA2B2B2B2B");
        LoanUser adviser = new LoanUser();
        adviser.setUserNo("S001");
        adviser.setName("张顾问");
        adviser.setUserType(LoanUser.TYPE_STAFF);

        Map<String, Object> result = service.create(payload, adviser);

        ArgumentCaptor<ClientProfile> captor = ArgumentCaptor.forClass(ClientProfile.class);
        verify(clientProfileMapper).insert(captor.capture());
        assertEquals("S001", captor.getValue().getOwnerStaffCode());
        assertEquals("S001", captor.getValue().getCreatedBy());
        assertEquals("CREATED_ASSIGNED", result.get("action"));
        assertEquals("S001", result.get("ownerStaffCode"));
        ArgumentCaptor<ClientLifecycleEvent> eventCaptor = ArgumentCaptor.forClass(ClientLifecycleEvent.class);
        verify(lifecycleEventMapper).insert(eventCaptor.capture());
        assertEquals("OWNER_ASSIGNED", eventCaptor.getValue().getEventType());
        assertEquals("SELF_CREATE", eventCaptor.getValue().getReasonCode());
    }
}
