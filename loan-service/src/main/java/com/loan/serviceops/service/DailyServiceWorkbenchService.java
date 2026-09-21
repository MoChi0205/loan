package com.loan.serviceops.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.loan.api.dto.PageResult;
import com.loan.client.entity.ClientProfile;
import com.loan.context.LoanUser;
import com.loan.order.entity.ServiceOrder;
import com.loan.order.mapper.ServiceOrderMapper;
import com.loan.serviceops.dto.DailyFollowDTO;
import com.loan.serviceops.dto.DailyOrderDTO;
import com.loan.serviceops.dto.DailyServiceWorkbenchDTO;
import com.loan.serviceops.entity.ClientFollowRecord;
import com.loan.serviceops.mapper.ClientFollowRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** P1 今日服务台直接聚合；P4 迁移至聚合表和 Redis 后保持 DTO 不变。 */
@Service
@RequiredArgsConstructor
public class DailyServiceWorkbenchService {

    private final AppointmentService appointmentService;
    private final OutingService outingService;
    private final ClientFollowRecordMapper followMapper;
    private final ServiceOrderMapper orderMapper;
    private final ServiceOperationScopeService scopeService;

    public DailyServiceWorkbenchDTO daily(LocalDate date, LoanUser user, int page, int size) {
        scopeService.requireStaffListAccess(user);
        LocalDate day = date == null ? LocalDate.now() : date;
        DailyServiceWorkbenchDTO dto = new DailyServiceWorkbenchDTO();
        dto.setDate(day);
        dto.setCompanyVisits(appointmentService.day(
                day, "COMPANY_ON_SITE", null, user, page, size));
        dto.setStaffOutings(outingService.day(day, null, user, page, size));
        dto.setPendingFollows(pendingFollows(day, user, page, size));
        dto.setActiveOrders(activeOrders(user, page, size));
        return dto;
    }

    private PageResult<DailyFollowDTO> pendingFollows(LocalDate day, LoanUser user, int page, int size) {
        LambdaQueryWrapper<ClientFollowRecord> wrapper = new LambdaQueryWrapper<ClientFollowRecord>()
                .ge(ClientFollowRecord::getNextFollowAt, day.atStartOfDay())
                .lt(ClientFollowRecord::getNextFollowAt, day.plusDays(1).atStartOfDay())
                .orderByAsc(ClientFollowRecord::getNextFollowAt);
        scopeService.applyFollowListScope(wrapper, user);
        Page<ClientFollowRecord> result = followMapper.selectPage(new Page<>(page, size), wrapper);
        Map<String, ClientProfile> clients = scopeService.clientsByCodes(result.getRecords().stream()
                .map(ClientFollowRecord::getClientCode).collect(Collectors.toSet()));
        return PageResult.build(page, size, result.getTotal(), result.getRecords().stream().map(item -> {
            DailyFollowDTO row = new DailyFollowDTO();
            row.setFollowNo(item.getFollowNo());
            row.setClientCode(item.getClientCode());
            ClientProfile client = clients.get(item.getClientCode());
            row.setCustomerName(displayName(client));
            row.setStaffCode(item.getStaffCode());
            row.setNextAction(item.getNextAction());
            row.setNextFollowAt(item.getNextFollowAt());
            return row;
        }).collect(Collectors.toList()));
    }

    private PageResult<DailyOrderDTO> activeOrders(LoanUser user, int page, int size) {
        LambdaQueryWrapper<ServiceOrder> wrapper = new LambdaQueryWrapper<ServiceOrder>()
                .in(ServiceOrder::getStatus, Arrays.asList(ServiceOrder.STATUS_NEW, ServiceOrder.STATUS_IN_SERVICE))
                .orderByDesc(ServiceOrder::getUpdatedAt)
                .orderByDesc(ServiceOrder::getCreatedAt);
        scopeService.applyOrderListScope(wrapper, user);
        Page<ServiceOrder> result = orderMapper.selectPage(new Page<>(page, size), wrapper);
        Map<String, ClientProfile> clients = scopeService.clientsByCodes(result.getRecords().stream()
                .map(ServiceOrder::getClientProfileCode).collect(Collectors.toSet()));
        return PageResult.build(page, size, result.getTotal(), result.getRecords().stream().map(item -> {
            DailyOrderDTO row = new DailyOrderDTO();
            row.setOrderNo(item.getOrderNo());
            row.setClientCode(item.getClientProfileCode());
            row.setCustomerName(displayName(clients.get(item.getClientProfileCode())));
            row.setOwnerStaffCode(item.getOwnerStaffCode());
            row.setStatus(item.getStatus());
            row.setUpdatedAt(item.getUpdatedAt() == null ? item.getCreatedAt() : item.getUpdatedAt());
            return row;
        }).collect(Collectors.toList()));
    }

    private String displayName(ClientProfile client) {
        if (client == null) {
            return null;
        }
        return client.getEnterpriseName() == null || client.getEnterpriseName().trim().isEmpty()
                ? client.getContactName() : client.getEnterpriseName();
    }
}
