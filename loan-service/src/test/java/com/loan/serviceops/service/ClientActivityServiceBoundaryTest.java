package com.loan.serviceops.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.loan.serviceops.entity.ClientActivityEvent;
import com.loan.serviceops.mapper.ClientActivityEventMapper;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClientActivityServiceBoundaryTest {

    @Test
    void customerTimelineFiltersVisibilityAndHidesStaffCode() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "serviceops-test"),
                ClientActivityEvent.class);
        ClientActivityEventMapper mapper = mock(ClientActivityEventMapper.class);
        ClientActivityEvent event = new ClientActivityEvent();
        event.setEventNo("event01");
        event.setEventType("FOLLOW_RECORDED");
        event.setSummary("请准备经营资料");
        event.setVisibility("CUSTOMER");
        event.setStaffCode("staff-secret");
        event.setActorType("STAFF");
        event.setHappenedAt(LocalDateTime.now());
        Page<ClientActivityEvent> result = new Page<>(1, 20);
        result.setRecords(Collections.singletonList(event));
        result.setTotal(1);
        when(mapper.selectPage(any(), any())).thenReturn(result);

        ClientActivityService service = new ClientActivityService(mapper);
        assertNull(service.timeline("client01", true, 1, 20).getRecords().get(0).getStaffCode());
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper<ClientActivityEvent>> wrapper =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(mapper).selectPage(any(), wrapper.capture());
        assertTrue(wrapper.getValue().getSqlSegment().contains("visibility"));
    }
}
