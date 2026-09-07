package com.loan.lead.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.loan.common.ResultCode;
import com.loan.exception.BusinessException;
import com.loan.lead.entity.Lead;
import com.loan.lead.mapper.LeadAllocationRecordMapper;
import com.loan.lead.mapper.LeadMapper;
import com.loan.notification.service.NotificationService;
import com.loan.sensitive.service.SensitiveViewService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import org.mockito.ArgumentCaptor;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.Arrays;

/** 渠道线索终审的幂等与并发冲突测试。 */
class LeadServiceChannelApprovalTest {

    private LeadMapper leadMapper;
    private LeadAllocationRecordMapper recordMapper;
    private LeadService service;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        TableInfoHelper.initTableInfo(assistant, Lead.class);
    }

    @BeforeEach
    void setUp() {
        leadMapper = mock(LeadMapper.class);
        recordMapper = mock(LeadAllocationRecordMapper.class);
        service = new LeadService(leadMapper, recordMapper,
                mock(NotificationService.class), mock(SensitiveViewService.class));
    }

    @Test
    void sameApprovalResultIsIdempotent() {
        when(leadMapper.selectOne(any())).thenReturn(channelLead("NEW"));

        service.auditChannelLead("lead-own", true, null, "BOSS001");

        verify(leadMapper, never()).auditChannelLead(any(), any(), any());
        verify(recordMapper, never()).insert(any());
    }

    @Test
    void oppositeApprovalResultCannotOverwriteCompletedDecision() {
        when(leadMapper.selectOne(any())).thenReturn(channelLead("REJECTED"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.auditChannelLead("lead-own", true, null, "BOSS001"));

        assertEquals(ResultCode.PARAM_ERROR.getCode(), error.getCode());
        verify(leadMapper, never()).auditChannelLead(any(), any(), any());
    }

    @Test
    void concurrentSameDecisionIsIdempotentAfterCasMiss() {
        when(leadMapper.selectOne(any()))
                .thenReturn(channelLead("PENDING_APPROVAL"))
                .thenReturn(channelLead("NEW"));
        when(leadMapper.auditChannelLead("lead-own", "NEW", "BOSS001")).thenReturn(0);

        service.auditChannelLead("lead-own", true, null, "BOSS001");

        verify(recordMapper, never()).insert(any());
    }

    @Test
    void concurrentOppositeDecisionReturnsConflict() {
        when(leadMapper.selectOne(any()))
                .thenReturn(channelLead("PENDING_APPROVAL"))
                .thenReturn(channelLead("REJECTED"));
        when(leadMapper.auditChannelLead("lead-own", "NEW", "BOSS001")).thenReturn(0);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.auditChannelLead("lead-own", true, null, "BOSS001"));

        assertEquals(ResultCode.PARAM_ERROR.getCode(), error.getCode());
        verify(recordMapper, never()).insert(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void publicPoolOnlyIncludesApprovedChannelLeads() {
        when(leadMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenAnswer(invocation -> {
            Page<Lead> page = invocation.getArgument(0);
            page.setRecords(java.util.Collections.emptyList());
            page.setTotal(0);
            return page;
        });

        service.page(null, null, null, null, 1, 10, "ADVISER", "ADV001", null, null);

        ArgumentCaptor<LambdaQueryWrapper<Lead>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(leadMapper).selectPage(any(Page.class), captor.capture());
        String sql = captor.getValue().getCustomSqlSegment();
        assertEquals(true, sql.contains("owner_staff_code IS NULL"));
        assertEquals(true, sql.contains("source <>"));
        assertEquals(true, sql.contains("follow_status ="));
    }

    @Test
    void batchDeleteLoadsOnceAndDeletesOnce() {
        Lead first = channelLead("NEW");
        first.setLeadNo("lead-1");
        Lead second = channelLead("NEW");
        second.setLeadNo("lead-2");
        when(leadMapper.selectList(any())).thenReturn(Arrays.asList(first, second));
        when(leadMapper.deleteByLeadNos(any())).thenReturn(2);

        assertEquals(2, service.batchDelete(Arrays.asList("lead-1", "lead-2", "lead-1"), "boss"));

        verify(leadMapper, times(1)).selectList(any());
        verify(leadMapper, times(1)).deleteByLeadNos(any());
        verify(leadMapper, never()).selectOne(any());
    }

    @Test
    void concurrentClaimCannotOverwriteOtherAdviser() {
        Lead lead = channelLead("NEW");
        lead.setLeadNo("lead-1");
        when(leadMapper.selectOne(any())).thenReturn(lead);
        when(leadMapper.claimIfUnowned("lead-1", "ADV001", "顾问甲")).thenReturn(0);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.claim("lead-1", "ADV001", "顾问甲"));

        assertEquals(ResultCode.PARAM_ERROR.getCode(), error.getCode());
        verify(recordMapper, never()).insert(any());
    }

    private Lead channelLead(String status) {
        Lead lead = new Lead();
        lead.setLeadNo("lead-own");
        lead.setSource("CHANNEL");
        lead.setFollowStatus(status);
        return lead;
    }
}
