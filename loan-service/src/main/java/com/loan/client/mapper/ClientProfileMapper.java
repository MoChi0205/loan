package com.loan.client.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.loan.client.entity.ClientProfile;
import com.loan.common.util.MapperAccess;
import com.loan.lead.entity.Lead;
import com.loan.staff.entity.Staff;
import com.loan.serviceops.entity.ClientAppointment;
import com.loan.order.entity.ServiceOrder;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** Wrapper 数据访问，联动数据批量读取，禁止注解 SQL。 */
@Mapper
public interface ClientProfileMapper extends BaseMapper<ClientProfile> {
    default List<Map<String, Object>> selectDynamicTags(Collection<String> codes) {
        if (codes == null || codes.isEmpty()) return Collections.emptyList();
        Set<String> appointed = new HashSet<>(), visited = new HashSet<>(), dealt = new HashSet<>();
        List<ClientAppointment> appointments = MapperAccess.query(ClientAppointment.class, mapper ->
            mapper.selectList(new LambdaQueryWrapper<ClientAppointment>().select(ClientAppointment::getClientCode,
                ClientAppointment::getStatus, ClientAppointment::getActualArrivedAt).in(ClientAppointment::getClientCode, codes)));
        for (ClientAppointment item : appointments) {
            if (Arrays.asList("REQUESTED", "CONFIRMED", "ARRIVED", "SERVING", "COMPLETED").contains(item.getStatus())) appointed.add(item.getClientCode());
            if (item.getActualArrivedAt() != null || Arrays.asList("ARRIVED", "SERVING", "COMPLETED").contains(item.getStatus())) visited.add(item.getClientCode());
        }
        List<ServiceOrder> orders = MapperAccess.query(ServiceOrder.class, mapper ->
            mapper.selectList(new LambdaQueryWrapper<ServiceOrder>().select(ServiceOrder::getClientProfileCode)
                .in(ServiceOrder::getClientProfileCode, codes).eq(ServiceOrder::getStatus, "DEAL")));
        orders.forEach(item -> dealt.add(item.getClientProfileCode()));
        List<Map<String, Object>> result = new ArrayList<>();
        for (String code : codes) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("clientCode", code); row.put("appointed", appointed.contains(code) ? 1 : 0);
            row.put("visited", visited.contains(code) ? 1 : 0); row.put("deal", dealt.contains(code) ? 1 : 0);
            result.add(row);
        }
        return result;
    }

    /** 分批轻量扫描；列表与数量使用相同的互斥标签优先级。 */
    default Map<String, Object> selectTagCounts(String ownerStaffCode, String ownerDeptCode, boolean assignedOnly) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", 0L);
        for (String key : Arrays.asList("dealCount", "visitedCount", "appointedCount", "newCount", "intentionCount", "potentialCount", "noAnswerCount", "noNeedCount")) result.put(key, 0L);
        List<String> staff = StringUtils.hasText(ownerDeptCode) ? MapperAccess.query(Staff.class, mapper ->
            mapper.selectList(new LambdaQueryWrapper<Staff>().select(Staff::getStaffCode)
                .eq(Staff::getDeptCode, ownerDeptCode).eq(Staff::getStatus, "ACTIVE"))).stream()
            .map(Staff::getStaffCode).collect(Collectors.toList()) : Collections.emptyList();
        if (StringUtils.hasText(ownerDeptCode) && staff.isEmpty()) return result;
        String cursor = null;
        while (true) {
            LambdaQueryWrapper<ClientProfile> q = new LambdaQueryWrapper<ClientProfile>()
                .select(ClientProfile::getClientCode, ClientProfile::getCustomerTag)
                .eq(StringUtils.hasText(ownerStaffCode), ClientProfile::getOwnerStaffCode, ownerStaffCode)
                .in(!staff.isEmpty(), ClientProfile::getOwnerStaffCode, staff)
                .isNotNull(assignedOnly, ClientProfile::getOwnerStaffCode)
                .gt(cursor != null, ClientProfile::getClientCode, cursor).orderByAsc(ClientProfile::getClientCode).last("LIMIT 500");
            List<ClientProfile> clients = selectList(q);
            if (clients.isEmpty()) break;
            Map<String, Map<String, Object>> tags = selectDynamicTags(clients.stream().map(ClientProfile::getClientCode).collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(row -> String.valueOf(row.get("clientCode")), row -> row));
            for (ClientProfile client : clients) {
                Map<String, Object> flags = tags.get(client.getClientCode());
                String key = Integer.valueOf(1).equals(flags.get("deal")) ? "dealCount" :
                    Integer.valueOf(1).equals(flags.get("visited")) ? "visitedCount" :
                    Integer.valueOf(1).equals(flags.get("appointed")) ? "appointedCount" :
                    tagCountKey(client.getCustomerTag());
                result.put("total", (Long) result.get("total") + 1);
                if (key != null) result.put(key, (Long) result.get(key) + 1);
            }
            cursor = clients.get(clients.size() - 1).getClientCode();
        }
        return result;
    }
    default String tagCountKey(String tag) {
        if (!StringUtils.hasText(tag) || "NEW".equals(tag)) return "newCount";
        if ("INTENTION".equals(tag)) return "intentionCount";
        if ("POTENTIAL".equals(tag)) return "potentialCount";
        if ("NO_ANSWER".equals(tag)) return "noAnswerCount";
        if ("NO_NEED".equals(tag)) return "noNeedCount";
        if ("DEAL".equals(tag)) return "dealCount";
        if ("VISITED".equals(tag)) return "visitedCount";
        if ("APPOINTED".equals(tag)) return "appointedCount";
        return "newCount";
    }
    /** select=false 字段仅此受控读取可显式选择。 */
    default String selectPhonePlain(String clientCode) {
        ClientProfile profile = selectOne(new LambdaQueryWrapper<ClientProfile>().select(ClientProfile::getPhonePlain)
            .eq(ClientProfile::getClientCode, clientCode));
        return profile == null ? null : profile.getPhonePlain();
    }
    default int backfillPhonePlain(String clientCode, String phonePlain) {
        return update(null, new LambdaUpdateWrapper<ClientProfile>().eq(ClientProfile::getClientCode, clientCode)
            .and(w -> w.isNull(ClientProfile::getPhonePlain).or().eq(ClientProfile::getPhonePlain, ""))
            .set(ClientProfile::getPhonePlain, phonePlain));
    }

    /** 渠道归属严格以录入主体业务编号识别，不采用可伪造的客户端参数。 */
    default List<ClientProfile> channelOwnedClients(String channelNo) {
        if (!StringUtils.hasText(channelNo)) return Collections.emptyList();
        List<Lead> leads = MapperAccess.query(Lead.class, mapper -> mapper.selectList(new LambdaQueryWrapper<Lead>()
            .select(Lead::getLeadNo, Lead::getClientProfileCode, Lead::getPhoneHash, Lead::getLeadType,
                Lead::getRecorderStaffCode, Lead::getExtJson)
            .eq(Lead::getSource, "CHANNEL").notIn(Lead::getFollowStatus, "PENDING_APPROVAL", "REJECTED")
            .and(w -> w.eq(Lead::getRecorderStaffCode, channelNo).or().isNull(Lead::getRecorderStaffCode).or().eq(Lead::getRecorderStaffCode, ""))));
        leads = leads.stream().filter(lead -> channelNo.equals(lead.getRecorderStaffCode()) || legacyChannel(lead, channelNo)).collect(Collectors.toList());
        if (leads.isEmpty()) return Collections.emptyList();
        Set<String> clientCodes = leads.stream().map(Lead::getClientProfileCode).filter(StringUtils::hasText).collect(Collectors.toSet());
        Set<String> leadNos = leads.stream().map(Lead::getLeadNo).filter(StringUtils::hasText).collect(Collectors.toSet());
        Set<String> hashes = leads.stream().map(Lead::getPhoneHash).filter(StringUtils::hasText).collect(Collectors.toSet());
        if (clientCodes.isEmpty() && leadNos.isEmpty() && hashes.isEmpty()) return Collections.emptyList();
        List<ClientProfile> candidates = selectList(new LambdaQueryWrapper<ClientProfile>().and(w -> {
            w.in(!clientCodes.isEmpty(), ClientProfile::getClientCode, clientCodes);
            w.or().in(!leadNos.isEmpty(), ClientProfile::getLeadNo, leadNos);
            w.or().in(!hashes.isEmpty(), ClientProfile::getPhoneHash, hashes);
        }));
        Set<String> natural = leads.stream().map(l -> l.getPhoneHash() + "#" + l.getLeadType()).collect(Collectors.toSet());
        return candidates.stream().filter(c -> clientCodes.contains(c.getClientCode()) || leadNos.contains(c.getLeadNo())
            || (StringUtils.hasText(c.getPhoneHash()) && natural.contains(c.getPhoneHash() + "#" + c.getCustomerGroup()))).collect(Collectors.toList());
    }
    default boolean legacyChannel(Lead lead, String channelNo) {
        if (!StringUtils.hasText(lead.getExtJson())) return false;
        try { return channelNo.equals(new com.fasterxml.jackson.databind.ObjectMapper().readTree(lead.getExtJson()).path("recorderChannelNo").asText()); }
        catch (Exception ignored) { return false; }
    }
    default Page<ClientProfile> selectChannelOwnedPage(Page<ClientProfile> page, String channelNo, String keyword, String phoneHash, String orderDir) {
        List<String> codes = channelOwnedClients(channelNo).stream().map(ClientProfile::getClientCode).collect(Collectors.toList());
        if (codes.isEmpty()) { page.setTotal(0); page.setRecords(Collections.emptyList()); return page; }
        LambdaQueryWrapper<ClientProfile> q = new LambdaQueryWrapper<ClientProfile>().in(ClientProfile::getClientCode, codes);
        if (StringUtils.hasText(keyword)) q.and(w -> w.like(ClientProfile::getContactName, keyword)
            .or().like(ClientProfile::getEnterpriseName, keyword).or().eq(StringUtils.hasText(phoneHash), ClientProfile::getPhoneHash, phoneHash));
        q.orderBy(true, "asc".equalsIgnoreCase(orderDir), ClientProfile::getCreatedAt);
        return selectPage(page, q);
    }
    default List<ClientProfile> selectChannelOwnedByCodes(String channelNo, Collection<String> clientCodes) {
        if (clientCodes == null || clientCodes.isEmpty()) return Collections.emptyList();
        return channelOwnedClients(channelNo).stream().filter(c -> clientCodes.contains(c.getClientCode())).collect(Collectors.toList());
    }
    default int countChannelOwnedClient(String channelNo, String clientCode) {
        return (int) channelOwnedClients(channelNo).stream().filter(c -> clientCode.equals(c.getClientCode())).count();
    }
    default LambdaUpdateWrapper<ClientProfile> ownerUpdate(String clientCode, String staffCode, String updatedBy, LocalDateTime updatedAt) {
        return new LambdaUpdateWrapper<ClientProfile>().eq(ClientProfile::getClientCode, clientCode)
            .set(ClientProfile::getOwnerStaffCode, staffCode).set(ClientProfile::getSeaLevel, null)
            .set(ClientProfile::getSeaDeptCode, null).set(ClientProfile::getUpdatedBy, updatedBy).set(ClientProfile::getUpdatedAt, updatedAt);
    }
    default int assignOwnerIfUnassigned(String clientCode, String staffCode, String updatedBy, LocalDateTime updatedAt) {
        return update(null, ownerUpdate(clientCode, staffCode, updatedBy, updatedAt).isNull(ClientProfile::getOwnerStaffCode));
    }
    default int assignOwnerIfUnchanged(String clientCode, String staffCode, String expectedOwner, String updatedBy, LocalDateTime updatedAt) {
        LambdaUpdateWrapper<ClientProfile> q = ownerUpdate(clientCode, staffCode, updatedBy, updatedAt);
        if (expectedOwner == null) q.isNull(ClientProfile::getOwnerStaffCode); else q.eq(ClientProfile::getOwnerStaffCode, expectedOwner);
        return update(null, q);
    }
    default int transferOwnerIfUnchanged(String clientCode, String staffCode, String expectedOwner, String updatedBy, LocalDateTime updatedAt) {
        return update(null, ownerUpdate(clientCode, staffCode, updatedBy, updatedAt).eq(ClientProfile::getOwnerStaffCode, expectedOwner));
    }
    default ClientProfile selectByWxOpenidHash(String hash) {
        return !StringUtils.hasText(hash) ? null : selectOne(new LambdaQueryWrapper<ClientProfile>().eq(ClientProfile::getWxOpenidHash, hash));
    }
}
