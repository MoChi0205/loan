package com.loan.client.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.loan.client.entity.ClientLifecycleEvent;
import com.loan.client.entity.ClientProfile;
import com.loan.common.util.MapperAccess;
import org.apache.ibatis.annotations.Mapper;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/** 生命周期的查询条件用 Wrapper 表达，持续时间在业务 Java 中计算。 */
@Mapper
public interface ClientLifecycleEventMapper extends BaseMapper<ClientLifecycleEvent> {
    default int insertFirstFollowIgnore(String clientCode, String staffCode, Integer episodeNo, LocalDateTime eventAt, String operatorStaffCode) {
        ClientLifecycleEvent event = new ClientLifecycleEvent();
        event.setClientCode(clientCode); event.setStaffCode(staffCode); event.setEpisodeNo(episodeNo);
        event.setEventAt(eventAt); event.setOperatorStaffCode(operatorStaffCode); event.setEventType("FIRST_FOLLOW");
        try { return insert(event); } catch (org.springframework.dao.DuplicateKeyException duplicate) {
            if (selectCount(new LambdaQueryWrapper<ClientLifecycleEvent>().eq(ClientLifecycleEvent::getClientCode, clientCode)
                .eq(ClientLifecycleEvent::getEpisodeNo, episodeNo).eq(ClientLifecycleEvent::getEventType, "FIRST_FOLLOW")) == 0) throw duplicate;
            return 0;
        }
    }
    default List<Map<String, Object>> firstFollowDurations(LocalDateTime from, LocalDateTime to, Collection<String> staffCodes) {
        return durations(from, to, staffCodes, Collections.singletonList("OWNER_ASSIGNED"), "FIRST_FOLLOW", false);
    }
    default List<Map<String, Object>> seaStayDurations(LocalDateTime from, LocalDateTime to, Collection<String> staffCodes) {
        return durations(from, to, staffCodes, Arrays.asList("ENTER_COMPANY_SEA", "ENTER_TEAM_SEA"), "EXIT_SEA", true);
    }
    default List<Map<String, Object>> durations(LocalDateTime from, LocalDateTime to, Collection<String> staffCodes,
            List<String> types, String target, boolean eitherStaff) {
        LambdaQueryWrapper<ClientLifecycleEvent> q = new LambdaQueryWrapper<ClientLifecycleEvent>()
            .in(ClientLifecycleEvent::getEventType, types).ge(ClientLifecycleEvent::getEventAt, from).lt(ClientLifecycleEvent::getEventAt, to);
        if (!eitherStaff && staffCodes != null && !staffCodes.isEmpty()) q.in(ClientLifecycleEvent::getStaffCode, staffCodes);
        List<ClientLifecycleEvent> entries = selectList(q);
        if (entries.isEmpty()) return Collections.emptyList();
        Map<String, List<ClientLifecycleEvent>> exits = selectList(new LambdaQueryWrapper<ClientLifecycleEvent>()
            .eq(ClientLifecycleEvent::getEventType, target).in(ClientLifecycleEvent::getClientCode,
                entries.stream().map(ClientLifecycleEvent::getClientCode).distinct().collect(Collectors.toList())))
            .stream().collect(Collectors.groupingBy(e -> e.getClientCode() + "#" + e.getEpisodeNo()));
        List<Map<String, Object>> result = new ArrayList<>();
        for (ClientLifecycleEvent entry : entries) {
            Optional<ClientLifecycleEvent> exit = exits.getOrDefault(entry.getClientCode() + "#" + entry.getEpisodeNo(), Collections.emptyList())
                .stream().filter(e -> e.getEventAt() != null && !e.getEventAt().isBefore(entry.getEventAt()))
                .filter(e -> !eitherStaff || staffCodes == null || staffCodes.isEmpty()
                    || staffCodes.contains(entry.getStaffCode()) || staffCodes.contains(e.getStaffCode()))
                .min(Comparator.comparing(ClientLifecycleEvent::getEventAt));
            if (!exit.isPresent()) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("clientCode", entry.getClientCode()); row.put("episodeNo", entry.getEpisodeNo());
            row.put("seaLevel", entry.getSeaLevel());
            row.put("hours", Duration.between(entry.getEventAt(), exit.get().getEventAt()).getSeconds() / 3600.0);
            result.add(row);
        }
        return result;
    }
    default Map<String, Object> averageCurrentPoolAgeHours(String seaLevel, String seaDeptCode) {
        List<String> codes = MapperAccess.query(ClientProfile.class, mapper -> mapper.selectList(
            new LambdaQueryWrapper<ClientProfile>().select(ClientProfile::getClientCode).isNull(ClientProfile::getOwnerStaffCode)))
            .stream().map(ClientProfile::getClientCode).collect(Collectors.toList());
        if (codes.isEmpty()) return Collections.singletonMap("hours", null);
        List<ClientLifecycleEvent> entries = selectList(new LambdaQueryWrapper<ClientLifecycleEvent>()
            .in(ClientLifecycleEvent::getEventType, "ENTER_COMPANY_SEA", "ENTER_TEAM_SEA")
            .in(ClientLifecycleEvent::getClientCode, codes)
            .eq(seaLevel != null && !seaLevel.isEmpty(), ClientLifecycleEvent::getSeaLevel, seaLevel)
            .eq(seaDeptCode != null && !seaDeptCode.isEmpty(), ClientLifecycleEvent::getSeaDeptCode, seaDeptCode));
        Map<String, List<ClientLifecycleEvent>> exits = selectList(new LambdaQueryWrapper<ClientLifecycleEvent>()
            .eq(ClientLifecycleEvent::getEventType, "EXIT_SEA").in(ClientLifecycleEvent::getClientCode, codes)).stream()
            .collect(Collectors.groupingBy(e -> e.getClientCode() + "#" + e.getEpisodeNo()));
        double total = 0; int count = 0;
        LocalDateTime now = LocalDateTime.now();
        for (ClientLifecycleEvent entry : entries) {
            if (entry.getEventAt() == null) continue;
            boolean closed = exits.getOrDefault(entry.getClientCode() + "#" + entry.getEpisodeNo(), Collections.emptyList()).stream()
                .anyMatch(e -> e.getEventAt() != null && !e.getEventAt().isBefore(entry.getEventAt()));
            if (!closed) { total += Duration.between(entry.getEventAt(), now).getSeconds() / 3600.0; count++; }
        }
        return Collections.singletonMap("hours", count == 0 ? null : total / count);
    }
}
