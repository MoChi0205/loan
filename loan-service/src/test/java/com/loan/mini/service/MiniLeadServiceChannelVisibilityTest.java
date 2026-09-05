package com.loan.mini.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.loan.api.dto.PageResult;
import com.loan.context.LoanUser;
import com.loan.lead.entity.Lead;
import com.loan.lead.entity.LeadEntExt;
import com.loan.lead.mapper.LeadEntExtMapper;
import com.loan.lead.mapper.LeadMapper;
import com.loan.lead.service.LeadService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 渠道新增线索与本人全状态可见测试。 */
class MiniLeadServiceChannelVisibilityTest {

    private LeadService leadService;
    private LeadMapper leadMapper;
    private LeadEntExtMapper extensionMapper;
    private MiniLeadService service;
    private LoanUser channel;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        TableInfoHelper.initTableInfo(assistant, Lead.class);
        TableInfoHelper.initTableInfo(assistant, LeadEntExt.class);
    }

    @BeforeEach
    void setUp() {
        leadService = mock(LeadService.class);
        leadMapper = mock(LeadMapper.class);
        extensionMapper = mock(LeadEntExtMapper.class);
        service = new MiniLeadService(leadService, leadMapper, extensionMapper);
        channel = new LoanUser();
        channel.setUserType(LoanUser.TYPE_CHANNEL);
        channel.setUserNo("channel-a");
        channel.setName("渠道甲");
    }

    @Test
    void submitForcesChannelSourceRecorderAndPendingApproval() {
        when(leadService.create(any(Lead.class), eq("channel-a"), eq("渠道甲"))).thenReturn("lead-new");
        Map<String, String> body = new HashMap<>();
        body.put("contactName", "张三");
        body.put("phone", "13800138000");
        body.put("leadType", "ENTERPRISE");

        Map<String, Object> result = service.submit(body, channel);

        ArgumentCaptor<Lead> captor = ArgumentCaptor.forClass(Lead.class);
        verify(leadService).create(captor.capture(), eq("channel-a"), eq("渠道甲"));
        Lead created = captor.getValue();
        assertEquals("CHANNEL", created.getSource());
        assertEquals("PENDING_APPROVAL", created.getFollowStatus());
        assertTrue(created.getExtJson().contains("\"recorderChannelNo\":\"channel-a\""));
        assertEquals("lead-new", result.get("leadNo"));
        assertEquals(false, result.get("duplicated"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void ownListReturnsPendingApprovedAndRejectedAndUsesOneExtensionQuery() {
        Lead pending = lead(1L, "lead-pending", "PENDING_APPROVAL");
        Lead approved = lead(2L, "lead-approved", "NEW");
        Lead rejected = lead(3L, "lead-rejected", "REJECTED");
        when(leadMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenAnswer(invocation -> {
            Page<Lead> page = invocation.getArgument(0);
            page.setRecords(Arrays.asList(pending, approved, rejected));
            page.setTotal(3);
            return page;
        });
        LeadEntExt extension = new LeadEntExt();
        extension.setLeadId(1L);
        extension.setCompanyName("待审企业");
        when(extensionMapper.selectList(any())).thenReturn(Collections.singletonList(extension));

        PageResult<Map<String, Object>> result = service.myLeads(1, 10, channel);

        assertEquals(3, result.getRecords().size());
        assertEquals("PENDING_APPROVAL", result.getRecords().get(0).get("followStatus"));
        assertEquals("NEW", result.getRecords().get(1).get("followStatus"));
        assertEquals("REJECTED", result.getRecords().get(2).get("followStatus"));
        assertEquals("待审企业", result.getRecords().get(0).get("entName"));
        verify(extensionMapper).selectList(any());

        ArgumentCaptor<LambdaQueryWrapper<Lead>> queryCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(leadMapper).selectPage(any(Page.class), queryCaptor.capture());
        String sql = queryCaptor.getValue().getCustomSqlSegment();
        assertTrue(sql.contains("source"));
        assertTrue(sql.contains("recorder_staff_code"));
        assertTrue(sql.contains("recorderChannelNo"));
        assertTrue(sql.contains("ORDER BY created_at DESC"));
        assertNull(result.getRecords().get(0).get("clientCode"));
    }

    private Lead lead(Long id, String leadNo, String status) {
        Lead lead = new Lead();
        lead.setId(id);
        lead.setLeadNo(leadNo);
        lead.setContactName("渠道客户");
        lead.setPhone("");
        lead.setFollowStatus(status);
        lead.setCreatedAt(LocalDateTime.now());
        return lead;
    }
}
