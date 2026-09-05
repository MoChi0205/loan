package com.loan.channel.controller;

import com.loan.api.dto.PageResult;
import com.loan.channel.service.ChannelDataScopeService;
import com.loan.context.LoanUser;
import com.loan.lead.entity.Lead;
import com.loan.lead.service.LeadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 渠道 Web 新增与本人查询契约测试。 */
class ChannelLeadControllerTest {

    private LeadService leadService;
    private ChannelDataScopeService scopeService;
    private ChannelLeadController controller;
    private LoanUser channel;

    @BeforeEach
    void setUp() {
        leadService = mock(LeadService.class);
        scopeService = mock(ChannelDataScopeService.class);
        controller = new ChannelLeadController(leadService, scopeService);
        channel = new LoanUser();
        channel.setUserType(LoanUser.TYPE_CHANNEL);
        channel.setUserNo("channel-a");
        channel.setName("渠道甲");
        when(scopeService.requireChannel(channel)).thenReturn("channel-a");
    }

    @Test
    void createForcesChannelAndPendingApproval() {
        when(leadService.create(any(Lead.class), eq("channel-a"), eq("渠道甲"))).thenReturn("lead-new");

        controller.createLead(new Lead(), channel);

        ArgumentCaptor<Lead> captor = ArgumentCaptor.forClass(Lead.class);
        verify(leadService).create(captor.capture(), eq("channel-a"), eq("渠道甲"));
        assertEquals("CHANNEL", captor.getValue().getSource());
        assertEquals("PENDING_APPROVAL", captor.getValue().getFollowStatus());
    }

    @Test
    void ownPageDoesNotImplicitlyFilterApprovalStatus() {
        when(leadService.pageByRecorder(eq("channel-a"), any(), any(), any(),
                eq(1), eq(10), any(), any()))
                .thenReturn(PageResult.build(1, 10, 0L, Collections.emptyList()));

        controller.leadPage(null, null, null, 1, 10, null, null, channel);

        verify(leadService).pageByRecorder("channel-a", null, null, null, 1, 10, null, null);
    }
}
