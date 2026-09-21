package com.loan.serviceops.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.loan.api.dto.PageResult;
import com.loan.common.util.BizIdGenerator;
import com.loan.serviceops.dto.ActivityTimelineDTO;
import com.loan.serviceops.entity.ClientActivityEvent;
import com.loan.serviceops.mapper.ClientActivityEventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/** 追加式活动事件写入与角色裁剪读取。 */
@Service
@RequiredArgsConstructor
public class ClientActivityService {

    public static final String VISIBILITY_STAFF_ONLY = "STAFF_ONLY";
    public static final String VISIBILITY_CUSTOMER = "CUSTOMER";

    private final ClientActivityEventMapper eventMapper;

    public void append(String clientCode, String staffCode, String eventType,
                       String sourceType, String sourceNo, String summary, String visibility,
                       String actorType, String actorCode, String metadataJson) {
        ClientActivityEvent event = new ClientActivityEvent();
        event.setEventNo(BizIdGenerator.generate("event"));
        event.setClientCode(clientCode);
        event.setStaffCode(staffCode);
        event.setEventType(eventType);
        event.setSourceType(sourceType);
        event.setSourceNo(sourceNo);
        event.setHappenedAt(LocalDateTime.now());
        event.setSummary(summary);
        event.setVisibility(visibility);
        event.setActorType(actorType);
        event.setActorCode(actorCode);
        event.setMetadataJson(metadataJson);
        event.setCreatedAt(LocalDateTime.now());
        eventMapper.insert(event);
    }

    public PageResult<ActivityTimelineDTO> timeline(String clientCode, boolean customerView,
                                                    int page, int size) {
        LambdaQueryWrapper<ClientActivityEvent> wrapper = new LambdaQueryWrapper<ClientActivityEvent>()
                .eq(ClientActivityEvent::getClientCode, clientCode)
                .orderByDesc(ClientActivityEvent::getHappenedAt)
                .orderByDesc(ClientActivityEvent::getId);
        if (customerView) {
            wrapper.eq(ClientActivityEvent::getVisibility, VISIBILITY_CUSTOMER);
        }
        Page<ClientActivityEvent> result = eventMapper.selectPage(new Page<>(page, size), wrapper);
        List<ActivityTimelineDTO> records = result.getRecords().stream()
                .map(event -> toDto(event, customerView))
                .collect(Collectors.toList());
        return PageResult.build(page, size, result.getTotal(), records);
    }

    private ActivityTimelineDTO toDto(ClientActivityEvent event, boolean customerView) {
        ActivityTimelineDTO dto = new ActivityTimelineDTO();
        dto.setEventNo(event.getEventNo());
        dto.setEventType(event.getEventType());
        dto.setSummary(event.getSummary());
        dto.setVisibility(event.getVisibility());
        dto.setHappenedAt(event.getHappenedAt());
        if (!customerView) {
            dto.setStaffCode(event.getStaffCode());
            dto.setActorType(event.getActorType());
        }
        return dto;
    }
}
