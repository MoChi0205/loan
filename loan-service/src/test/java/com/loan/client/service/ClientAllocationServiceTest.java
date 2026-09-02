package com.loan.client.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.loan.approval.entity.ClientAllocationApproval;
import com.loan.approval.mapper.ClientAllocationApprovalMapper;
import com.loan.client.entity.ClientProfile;
import com.loan.client.mapper.ClientProfileMapper;
import com.loan.context.LoanUser;
import com.loan.exception.BusinessException;
import com.loan.lead.entity.LeadAllocationRecord;
import com.loan.lead.mapper.LeadAllocationRecordMapper;
import com.loan.staff.entity.Staff;
import com.loan.staff.mapper.StaffMapper;
import com.loan.client.mapper.ClientRecycleConfigMapper;
import com.loan.notification.service.NotificationService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 客户未分配池与归属审批核心规则单测。 */
@ExtendWith(MockitoExtension.class)
class ClientAllocationServiceTest {

    @Mock private ClientProfileMapper clientMapper;
    @Mock private ClientAllocationApprovalMapper approvalMapper;
    @Mock private StaffMapper staffMapper;
    @Mock private LeadAllocationRecordMapper recordMapper;
    @Mock private ClientRecycleConfigMapper clientRecycleConfigMapper;
    @Mock private NotificationService notificationService;

    private ClientAllocationService service;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        TableInfoHelper.initTableInfo(assistant, ClientProfile.class);
        TableInfoHelper.initTableInfo(assistant, ClientAllocationApproval.class);
        TableInfoHelper.initTableInfo(assistant, Staff.class);
    }

    @BeforeEach
    void setUp() {
        service = new ClientAllocationService(clientMapper, approvalMapper, staffMapper,
                recordMapper, clientRecycleConfigMapper, notificationService);
    }

    @Test
    void unassignedPageBatchesPendingAdviserNames() {
        ClientProfile first = client("client1", null);
        first.setContactName("客户甲");
        ClientProfile second = client("client2", null);
        second.setContactName("客户乙");
        when(clientMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<ClientProfile> page = invocation.getArgument(0);
            page.setRecords(Arrays.asList(first, second));
            page.setTotal(2);
            return page;
        });
        ClientAllocationApproval pending = pending("alloc1", "client1", "S001");
        when(approvalMapper.selectList(any())).thenReturn(Collections.singletonList(pending));
        when(staffMapper.selectList(any())).thenReturn(Collections.singletonList(adviser("S001", "张顾问")));

        com.loan.api.dto.PageResult<Map<String, Object>> result = service.pageUnassigned(null, 1, 10);

        assertEquals(2, result.getTotal());
        assertEquals("张顾问", result.getRecords().get(0).get("applicantName"));
        assertEquals(false, result.getRecords().get(1).get("allocationPending"));
        verify(staffMapper).selectList(any());
    }

    @Test
    void samePendingApplicationIsIdempotent() {
        when(clientMapper.selectOne(any())).thenReturn(client("client1", null));
        when(staffMapper.selectOne(any())).thenReturn(adviser("S001", "张顾问"));
        when(approvalMapper.selectOne(any())).thenReturn(pending("alloc1", "client1", "S001"));

        Map<String, Object> result = service.apply("client1", "S001", operator(), "ADVISER_CLAIM");

        assertEquals("alloc1", result.get("approvalNo"));
        assertEquals(true, result.get("reused"));
        verify(approvalMapper, never()).insert(any());
    }

    @Test
    void differentAdviserCannotReplacePendingApplication() {
        when(clientMapper.selectOne(any())).thenReturn(client("client1", null));
        when(staffMapper.selectOne(any())).thenReturn(adviser("S002", "李顾问"));
        when(approvalMapper.selectOne(any())).thenReturn(pending("alloc1", "client1", "S001"));

        assertThrows(BusinessException.class,
                () -> service.apply("client1", "S002", operator(), "MANAGER_ASSIGN"));
        verify(approvalMapper, never()).insert(any());
    }

    @Test
    void inactiveOrNonAdviserCannotBeAssigned() {
        when(clientMapper.selectOne(any())).thenReturn(client("client1", null));
        Staff manager = adviser("S003", "部门经理");
        manager.setRoleCode("DEPT_MANAGER");
        when(staffMapper.selectOne(any())).thenReturn(manager);

        assertThrows(BusinessException.class,
                () -> service.apply("client1", "S003", operator(), "MANAGER_ASSIGN"));
    }

    @Test
    void approveUsesCasForApprovalAndClientOwner() {
        ClientAllocationApproval approval = pending("alloc1", "client1", "S001");
        when(approvalMapper.selectOne(any())).thenReturn(approval);
        when(approvalMapper.update(any(), any())).thenReturn(1);
        when(clientMapper.selectOne(any())).thenReturn(client("client1", null));
        when(clientMapper.assignOwnerIfUnassigned(any(), any(), any(), any())).thenReturn(1);
        when(recordMapper.insert(any(LeadAllocationRecord.class))).thenReturn(1);

        Map<String, Object> result = service.approve("alloc1", operator());

        assertEquals("APPROVED", result.get("status"));
        verify(clientMapper).assignOwnerIfUnassigned(any(), any(), any(), any());
        verify(recordMapper).insert(any(LeadAllocationRecord.class));
    }

    @Test
    void approveDoesNotOverwriteConcurrentOwner() {
        when(approvalMapper.selectOne(any())).thenReturn(pending("alloc1", "client1", "S001"));
        when(approvalMapper.update(any(), any())).thenReturn(1);
        when(clientMapper.selectOne(any())).thenReturn(client("client1", null));
        when(clientMapper.assignOwnerIfUnassigned(any(), any(), any(), any())).thenReturn(0);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.approve("alloc1", operator()));

        assertTrue(error.getMessage().contains("其他流程"));
        verify(recordMapper, never()).insert(any());
    }

    @Test
    void operationScopeAllowsCustomerSelfAndCurrentOwnerOnly() {
        LoanUser customer = new LoanUser();
        customer.setUserNo("client-self");
        customer.setUserType(LoanUser.TYPE_CUSTOMER);
        assertEquals("client-self", service.requireOperationClientCode(customer, "client-other"));

        LoanUser adviser = new LoanUser();
        adviser.setUserNo("S001");
        adviser.setUserType(LoanUser.TYPE_STAFF);
        when(clientMapper.selectOne(any())).thenReturn(client("client1", "S001"));
        assertEquals("client1", service.requireOperationClientCode(adviser, "client1"));
    }

    @Test
    void operationScopeRejectsUnassignedOrOtherOwner() {
        LoanUser adviser = new LoanUser();
        adviser.setUserNo("S001");
        adviser.setUserType(LoanUser.TYPE_STAFF);
        when(clientMapper.selectOne(any())).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> service.requireOperationClientCode(adviser, "client1"));
    }

    private ClientProfile client(String code, String owner) {
        ClientProfile client = new ClientProfile();
        client.setClientCode(code);
        client.setOwnerStaffCode(owner);
        return client;
    }

    private Staff adviser(String code, String name) {
        Staff staff = new Staff();
        staff.setStaffCode(code);
        staff.setStaffName(name);
        staff.setRoleCode("ADVISER");
        staff.setStatus("ACTIVE");
        return staff;
    }

    private ClientAllocationApproval pending(String approvalNo, String clientCode, String staffCode) {
        ClientAllocationApproval approval = new ClientAllocationApproval();
        approval.setApprovalNo(approvalNo);
        approval.setClientCode(clientCode);
        approval.setApplicantStaffCode(staffCode);
        approval.setApproveStatus("PENDING");
        approval.setPendingKey(clientCode);
        return approval;
    }

    private LoanUser operator() {
        LoanUser user = new LoanUser();
        user.setUserNo("OP001");
        user.setName("操作员");
        user.setUserType(LoanUser.TYPE_STAFF);
        return user;
    }
}
