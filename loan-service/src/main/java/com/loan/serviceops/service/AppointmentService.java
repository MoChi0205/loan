package com.loan.serviceops.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.loan.api.dto.PageResult;
import com.loan.client.entity.ClientProfile;
import com.loan.common.ResultCode;
import com.loan.common.util.BizIdGenerator;
import com.loan.context.LoanUser;
import com.loan.exception.BusinessException;
import com.loan.infrastructure.security.AesUtils;
import com.loan.order.entity.ServiceOrder;
import com.loan.order.mapper.ServiceOrderMapper;
import com.loan.serviceops.dto.AppointmentChangeRequest;
import com.loan.serviceops.dto.AppointmentCreateRequest;
import com.loan.serviceops.dto.CustomerAppointmentDTO;
import com.loan.serviceops.dto.StaffAppointmentDTO;
import com.loan.serviceops.entity.ClientAppointment;
import com.loan.serviceops.mapper.ClientAppointmentMapper;
import com.loan.serviceops.model.AppointmentStateMachine;
import com.loan.serviceops.model.AppointmentStatus;
import com.loan.serviceops.model.ServiceMethod;
import com.loan.serviceops.security.ServiceOperationAccessPolicy;
import com.loan.staff.entity.Staff;
import com.loan.staff.mapper.StaffMapper;
import com.loan.utils.DesensitizeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 预约创建、变更、履约与三端视角查询。 */
@Service
@RequiredArgsConstructor
public class AppointmentService {

    private static final Set<String> OPEN_STATUSES = java.util.Collections.unmodifiableSet(
            new java.util.HashSet<>(Arrays.asList("REQUESTED", "CONFIRMED", "ARRIVED", "SERVING")));

    /** 客户自助签到的时间窗：允许提前到店的分钟数。 */
    private static final int CHECK_IN_EARLY_MINUTES = 30;

    private final ClientAppointmentMapper appointmentMapper;
    private final StaffMapper staffMapper;
    private final ServiceOrderMapper orderMapper;
    private final ServiceOperationScopeService scopeService;
    private final ClientActivityService activityService;

    @Transactional(rollbackFor = Exception.class)
    public String createForCustomer(AppointmentCreateRequest request, LoanUser user, String sourceTerminal) {
        requireCustomer(user);
        ClientProfile client = scopeService.requireClient(user.getUserNo());
        if (!StringUtils.hasText(client.getOwnerStaffCode())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "客户暂未分配服务顾问，无法预约");
        }
        AppointmentCreateRequest safeRequest = copyForCustomer(request, client);
        return create(safeRequest, user, sourceTerminal, "CONFIRMED");
    }

    @Transactional(rollbackFor = Exception.class)
    public String createForStaff(AppointmentCreateRequest request, LoanUser user) {
        scopeService.requireStaffListAccess(user);
        ClientProfile client = scopeService.requireClient(request == null ? null : request.getClientCode());
        scopeService.requireClientVisibleToStaff(user, client);
        String host = StringUtils.hasText(request.getHostStaffCode())
                ? request.getHostStaffCode().trim() : user.getUserNo();
        requireAssignableHost(user, host);
        request.setHostStaffCode(host);
        return create(request, user, "WEB", "PENDING");
    }

    private String create(AppointmentCreateRequest request, LoanUser user, String sourceTerminal,
                          String customerConfirmStatus) {
        ValidatedAppointment validated = validateCreate(request);
        requireNoConflict(validated.host.getStaffCode(), request.getScheduledStart(), request.getScheduledEnd(), null);

        ClientAppointment appointment = new ClientAppointment();
        appointment.setAppointmentNo(BizIdGenerator.generate("appt"));
        appointment.setClientCode(validated.client.getClientCode());
        appointment.setOrderNo(trimToNull(request.getOrderNo()));
        appointment.setHostStaffCode(validated.host.getStaffCode());
        appointment.setAppointmentType(validated.method.name());
        appointment.setScheduledStart(request.getScheduledStart());
        appointment.setScheduledEnd(request.getScheduledEnd());
        appointment.setLocationName(trimToNull(request.getLocationName()));
        appointment.setLocationDetail(trimToNull(request.getLocationDetail()));
        appointment.setStatus(AppointmentStatus.REQUESTED.name());
        appointment.setCustomerConfirmStatus(customerConfirmStatus);
        appointment.setCustomerVisibleNote(trimToNull(request.getCustomerVisibleNote()));
        appointment.setInternalNote(LoanUser.TYPE_STAFF.equals(user.getUserType())
                ? trimToNull(request.getInternalNote()) : null);
        appointment.setCreatedByType(user.getUserType());
        appointment.setCreatedByCode(user.getUserNo());
        appointment.setSourceTerminal(normalizeTerminal(sourceTerminal));
        appointment.setCreatedBy(operatorName(user));
        appointment.setUpdatedBy(operatorName(user));
        appointment.setCreatedAt(LocalDateTime.now());
        appointment.setUpdatedAt(LocalDateTime.now());
        appointmentMapper.insert(appointment);

        activityService.append(appointment.getClientCode(), appointment.getHostStaffCode(),
                "APPOINTMENT_CREATED", "APPOINTMENT", appointment.getAppointmentNo(),
                "预约已创建，等待确认", ClientActivityService.VISIBILITY_CUSTOMER,
                user.getUserType(), user.getUserNo(), null);
        return appointment.getAppointmentNo();
    }

    @Transactional(rollbackFor = Exception.class)
    public void confirmByStaff(String appointmentNo, LoanUser user) {
        ClientAppointment appointment = requireAppointment(appointmentNo);
        requireHost(user, appointment);
        if (!"CONFIRMED".equals(appointment.getCustomerConfirmStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "员工代建预约需客户先确认");
        }
        transition(appointment, AppointmentStatus.CONFIRMED, user, null, "APPOINTMENT_CONFIRMED", "预约已确认");
    }

    @Transactional(rollbackFor = Exception.class)
    public void confirmByCustomer(String appointmentNo, LoanUser user) {
        requireCustomer(user);
        ClientAppointment appointment = requireAppointment(appointmentNo);
        requireCustomerOwner(user, appointment);
        if (!AppointmentStatus.REQUESTED.name().equals(appointment.getStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "当前预约状态不可确认");
        }
        if ("CONFIRMED".equals(appointment.getCustomerConfirmStatus())) {
            return;
        }
        ClientAppointment update = new ClientAppointment();
        update.setId(appointment.getId());
        update.setCustomerConfirmStatus("CONFIRMED");
        update.setUpdatedBy(operatorName(user));
        update.setUpdatedAt(LocalDateTime.now());
        appointmentMapper.updateById(update);
        activityService.append(appointment.getClientCode(), appointment.getHostStaffCode(),
                "CUSTOMER_CONFIRMED", "APPOINTMENT", appointment.getAppointmentNo(),
                "客户已确认预约信息", ClientActivityService.VISIBILITY_CUSTOMER,
                LoanUser.TYPE_CUSTOMER, user.getUserNo(), null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void arrive(String appointmentNo, LoanUser user) {
        ClientAppointment appointment = requireAppointment(appointmentNo);
        requireHost(user, appointment);
        transition(appointment, AppointmentStatus.ARRIVED, user, null,
                "CUSTOMER_ARRIVED", "客户已到公司现场");
    }

    /**
     * 客户自助到店签到（小程序/H5）。
     *
     * <p>三重约束：只有预约客户本人、仅公司现场预约、且处于「开始前 30 分钟至预约结束」时间窗内才能签到；
     * 二维码与定位只作为辅助凭证，不替代这里的服务端校验。
     */
    @Transactional(rollbackFor = Exception.class)
    public void checkInByCustomer(String appointmentNo, LoanUser user) {
        ClientAppointment appointment = requireAppointment(appointmentNo);
        if (user == null || !LoanUser.TYPE_CUSTOMER.equals(user.getUserType())
                || !user.getUserNo().equals(appointment.getClientCode())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能为本人预约签到");
        }
        if (!ServiceMethod.COMPANY_ON_SITE.name().equals(appointment.getAppointmentType())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "仅公司现场到店需要签到");
        }
        LocalDateTime now = LocalDateTime.now();
        if (appointment.getScheduledStart() != null
                && now.isBefore(appointment.getScheduledStart().minusMinutes(CHECK_IN_EARLY_MINUTES))) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "距预约开始还有较长时间，请到店后再签到");
        }
        if (appointment.getScheduledEnd() != null && now.isAfter(appointment.getScheduledEnd())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "预约时间已过，请联系服务顾问处理");
        }
        transition(appointment, AppointmentStatus.ARRIVED, user, null,
                "CUSTOMER_CHECKED_IN", "客户已签到到店");
    }

    @Transactional(rollbackFor = Exception.class)
    public void startService(String appointmentNo, LoanUser user) {
        ClientAppointment appointment = requireAppointment(appointmentNo);
        requireHost(user, appointment);
        transition(appointment, AppointmentStatus.SERVING, user, null,
                "SERVICE_STARTED", "服务已开始");
    }

    @Transactional(rollbackFor = Exception.class)
    public void complete(String appointmentNo, LoanUser user) {
        ClientAppointment appointment = requireAppointment(appointmentNo);
        requireHost(user, appointment);
        transition(appointment, AppointmentStatus.COMPLETED, user, null,
                "SERVICE_COMPLETED", "本次服务已完成");
    }

    @Transactional(rollbackFor = Exception.class)
    public void markNoShow(String appointmentNo, LoanUser user) {
        ClientAppointment appointment = requireAppointment(appointmentNo);
        requireHost(user, appointment);
        if (LocalDateTime.now().isBefore(appointment.getScheduledStart())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "预约尚未开始，不能标记未到场");
        }
        transition(appointment, AppointmentStatus.NO_SHOW, user, "客户未按预约到场",
                "APPOINTMENT_NO_SHOW", "客户未按预约到场");
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancel(String appointmentNo, String reason, LoanUser user, boolean exceptionFlow) {
        ClientAppointment appointment = requireAppointment(appointmentNo);
        requireParticipant(user, appointment);
        if (!exceptionFlow && !AppointmentStateMachine.canSelfChange(
                appointment.getScheduledStart(), LocalDateTime.now())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "距离预约开始不足5分钟，请联系服务顾问处理");
        }
        if (exceptionFlow) {
            requireHost(user, appointment);
            if (!StringUtils.hasText(reason)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "异常取消原因必填");
            }
        }
        transition(appointment, AppointmentStatus.CANCELLED, user, trimToNull(reason),
                "APPOINTMENT_CANCELLED", "预约已取消");
    }

    @Transactional(rollbackFor = Exception.class)
    public String reschedule(String appointmentNo, AppointmentChangeRequest request,
                             LoanUser user, boolean exceptionFlow) {
        ClientAppointment old = requireAppointment(appointmentNo);
        requireParticipant(user, old);
        if (!exceptionFlow && !AppointmentStateMachine.canSelfChange(old.getScheduledStart(), LocalDateTime.now())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "距离预约开始不足5分钟，请联系服务顾问处理");
        }
        if (exceptionFlow) {
            requireHost(user, old);
            if (request == null || !StringUtils.hasText(request.getReason())) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "异常改期原因必填");
            }
        }
        AppointmentCreateRequest create = new AppointmentCreateRequest();
        create.setClientCode(old.getClientCode());
        create.setHostStaffCode(old.getHostStaffCode());
        create.setOrderNo(old.getOrderNo());
        create.setServiceMethod(old.getAppointmentType());
        create.setScheduledStart(request == null ? null : request.getScheduledStart());
        create.setScheduledEnd(request == null ? null : request.getScheduledEnd());
        create.setLocationName(request != null && StringUtils.hasText(request.getLocationName())
                ? request.getLocationName() : old.getLocationName());
        create.setLocationDetail(request != null && StringUtils.hasText(request.getLocationDetail())
                ? request.getLocationDetail() : old.getLocationDetail());
        ValidatedAppointment validated = validateCreate(create);
        requireNoConflict(old.getHostStaffCode(), create.getScheduledStart(), create.getScheduledEnd(), old.getAppointmentNo());

        int changed = appointmentMapper.transition(old.getAppointmentNo(), old.getStatus(),
                AppointmentStatus.RESCHEDULED.name(), null, null,
                trimToNull(request == null ? null : request.getReason()), operatorName(user), LocalDateTime.now());
        if (changed != 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "预约状态已变化，请刷新后重试");
        }

        ClientAppointment next = new ClientAppointment();
        next.setAppointmentNo(BizIdGenerator.generate("appt"));
        next.setClientCode(validated.client.getClientCode());
        next.setOrderNo(old.getOrderNo());
        next.setHostStaffCode(validated.host.getStaffCode());
        next.setAppointmentType(validated.method.name());
        next.setScheduledStart(create.getScheduledStart());
        next.setScheduledEnd(create.getScheduledEnd());
        next.setLocationName(trimToNull(create.getLocationName()));
        next.setLocationDetail(trimToNull(create.getLocationDetail()));
        next.setStatus(AppointmentStatus.REQUESTED.name());
        next.setCustomerConfirmStatus(LoanUser.TYPE_CUSTOMER.equals(user.getUserType()) ? "CONFIRMED" : "PENDING");
        next.setCustomerVisibleNote(old.getCustomerVisibleNote());
        next.setInternalNote(old.getInternalNote());
        next.setCreatedByType(user.getUserType());
        next.setCreatedByCode(user.getUserNo());
        next.setSourceTerminal(LoanUser.TYPE_CUSTOMER.equals(user.getUserType()) ? old.getSourceTerminal() : "WEB");
        next.setRescheduledFromNo(old.getAppointmentNo());
        next.setCreatedBy(operatorName(user));
        next.setUpdatedBy(operatorName(user));
        next.setCreatedAt(LocalDateTime.now());
        next.setUpdatedAt(LocalDateTime.now());
        appointmentMapper.insert(next);
        activityService.append(old.getClientCode(), old.getHostStaffCode(),
                "APPOINTMENT_RESCHEDULED", "APPOINTMENT", next.getAppointmentNo(),
                "预约已改期，等待重新确认", ClientActivityService.VISIBILITY_CUSTOMER,
                user.getUserType(), user.getUserNo(), null);
        return next.getAppointmentNo();
    }

    public PageResult<CustomerAppointmentDTO> mine(LoanUser user, int page, int size) {
        requireCustomer(user);
        Page<ClientAppointment> result = appointmentMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<ClientAppointment>()
                        .eq(ClientAppointment::getClientCode, user.getUserNo())
                        .orderByDesc(ClientAppointment::getScheduledStart));
        Map<String, Staff> staff = staffByCodes(result.getRecords().stream()
                .map(ClientAppointment::getHostStaffCode).collect(Collectors.toSet()));
        List<CustomerAppointmentDTO> records = result.getRecords().stream()
                .map(item -> toCustomerDto(item, staff.get(item.getHostStaffCode())))
                .collect(Collectors.toList());
        return PageResult.build(page, size, result.getTotal(), records);
    }

    public PageResult<StaffAppointmentDTO> day(LocalDate date, String serviceMethod, String status,
                                               LoanUser user, int page, int size) {
        scopeService.requireStaffListAccess(user);
        LocalDate day = date == null ? LocalDate.now() : date;
        LambdaQueryWrapper<ClientAppointment> wrapper = new LambdaQueryWrapper<ClientAppointment>()
                .ge(ClientAppointment::getScheduledStart, day.atStartOfDay())
                .lt(ClientAppointment::getScheduledStart, day.plusDays(1).atStartOfDay())
                .orderByAsc(ClientAppointment::getScheduledStart);
        if (StringUtils.hasText(serviceMethod)) {
            parseMethod(serviceMethod);
            wrapper.eq(ClientAppointment::getAppointmentType, serviceMethod.trim().toUpperCase());
        }
        if (StringUtils.hasText(status)) {
            parseStatus(status);
            wrapper.eq(ClientAppointment::getStatus, status.trim().toUpperCase());
        }
        scopeService.applyAppointmentListScope(wrapper, user);
        Page<ClientAppointment> result = appointmentMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.build(page, size, result.getTotal(), toStaffDtos(result.getRecords()));
    }

    public ClientAppointment requireAppointment(String appointmentNo) {
        if (!StringUtils.hasText(appointmentNo)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "预约编号不能为空");
        }
        ClientAppointment appointment = appointmentMapper.selectOne(new LambdaQueryWrapper<ClientAppointment>()
                .eq(ClientAppointment::getAppointmentNo, appointmentNo));
        if (appointment == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "预约不存在");
        }
        return appointment;
    }

    private void transition(ClientAppointment appointment, AppointmentStatus target, LoanUser user,
                            String reason, String eventType, String summary) {
        AppointmentStatus current = parseStatus(appointment.getStatus());
        ServiceMethod method = parseMethod(appointment.getAppointmentType());
        if (!AppointmentStateMachine.canTransition(method, current, target)) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "当前服务方式不允许从" + current.name() + "流转到" + target.name());
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime arrivedAt = target == AppointmentStatus.ARRIVED ? now : null;
        LocalDateTime leftAt = target == AppointmentStatus.COMPLETED ? now : null;
        int changed = appointmentMapper.transition(appointment.getAppointmentNo(), current.name(), target.name(),
                arrivedAt, leftAt, reason, operatorName(user), now);
        if (changed != 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "预约状态已变化，请刷新后重试");
        }
        activityService.append(appointment.getClientCode(), appointment.getHostStaffCode(),
                eventType, "APPOINTMENT", appointment.getAppointmentNo(), summary,
                ClientActivityService.VISIBILITY_CUSTOMER, user.getUserType(), user.getUserNo(), null);
    }

    private ValidatedAppointment validateCreate(AppointmentCreateRequest request) {
        if (request == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "预约信息不能为空");
        }
        ClientProfile client = scopeService.requireClient(request.getClientCode());
        Staff host = scopeService.requireActiveStaff(request.getHostStaffCode());
        ServiceMethod method = parseMethod(request.getServiceMethod());
        validateOrder(request.getOrderNo(), client.getClientCode());
        if (request.getScheduledStart() == null || request.getScheduledEnd() == null
                || !request.getScheduledEnd().isAfter(request.getScheduledStart())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "预约结束时间必须晚于开始时间");
        }
        if (!request.getScheduledStart().isAfter(LocalDateTime.now())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "预约开始时间必须晚于当前时间");
        }
        if ((method == ServiceMethod.COMPANY_ON_SITE || method == ServiceMethod.HOME_VISIT)
                && !StringUtils.hasText(request.getLocationName())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "现场或上门服务必须填写地点");
        }
        return new ValidatedAppointment(client, host, method);
    }

    private void requireNoConflict(String hostStaffCode, LocalDateTime start, LocalDateTime end,
                                   String excludeAppointmentNo) {
        LambdaQueryWrapper<ClientAppointment> wrapper = new LambdaQueryWrapper<ClientAppointment>()
                .eq(ClientAppointment::getHostStaffCode, hostStaffCode)
                .in(ClientAppointment::getStatus, OPEN_STATUSES)
                .lt(ClientAppointment::getScheduledStart, end)
                .gt(ClientAppointment::getScheduledEnd, start);
        if (StringUtils.hasText(excludeAppointmentNo)) {
            wrapper.ne(ClientAppointment::getAppointmentNo, excludeAppointmentNo);
        }
        Long count = appointmentMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "该服务顾问在所选时间已有预约");
        }
    }

    private void requireAssignableHost(LoanUser user, String hostStaffCode) {
        Staff host = scopeService.requireActiveStaff(hostStaffCode);
        switch (ServiceOperationAccessPolicy.listScope(user)) {
            case SELF:
                if (!user.getUserNo().equals(hostStaffCode)) {
                    throw new BusinessException(ResultCode.FORBIDDEN, "顾问只能为本人创建预约");
                }
                break;
            case DEPARTMENT:
                if (!StringUtils.hasText(user.getDeptCode()) || !user.getDeptCode().equals(host.getDeptCode())) {
                    throw new BusinessException(ResultCode.FORBIDDEN, "部门经理只能选择本部门服务顾问");
                }
                break;
            case COMPANY:
                break;
            default:
                throw new BusinessException(ResultCode.FORBIDDEN, "无权创建预约");
        }
    }

    private void requireParticipant(LoanUser user, ClientAppointment appointment) {
        if (!ServiceOperationAccessPolicy.canOperateAppointment(
                user, appointment.getClientCode(), appointment.getHostStaffCode())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能操作本人预约或本人负责的预约");
        }
    }

    private void requireHost(LoanUser user, ClientAppointment appointment) {
        if (user == null || !LoanUser.TYPE_STAFF.equals(user.getUserType())
                || !user.getUserNo().equals(appointment.getHostStaffCode())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅主服务顾问可以执行该操作");
        }
    }

    private void requireCustomerOwner(LoanUser user, ClientAppointment appointment) {
        if (!ServiceOperationAccessPolicy.canReadCustomerAppointment(user, appointment.getClientCode())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能操作本人的预约");
        }
    }

    private void requireCustomer(LoanUser user) {
        if (user == null || !LoanUser.TYPE_CUSTOMER.equals(user.getUserType())
                || !StringUtils.hasText(user.getUserNo())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅客户可以访问本人预约");
        }
    }

    private AppointmentCreateRequest copyForCustomer(AppointmentCreateRequest source, ClientProfile client) {
        if (source == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "预约信息不能为空");
        }
        AppointmentCreateRequest target = new AppointmentCreateRequest();
        target.setClientCode(client.getClientCode());
        target.setHostStaffCode(client.getOwnerStaffCode());
        target.setServiceMethod(source.getServiceMethod());
        target.setScheduledStart(source.getScheduledStart());
        target.setScheduledEnd(source.getScheduledEnd());
        target.setLocationName(source.getLocationName());
        target.setLocationDetail(source.getLocationDetail());
        target.setCustomerVisibleNote(source.getCustomerVisibleNote());
        return target;
    }

    private List<StaffAppointmentDTO> toStaffDtos(List<ClientAppointment> appointments) {
        if (appointments == null || appointments.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Staff> staff = staffByCodes(appointments.stream()
                .map(ClientAppointment::getHostStaffCode).collect(Collectors.toSet()));
        Map<String, ClientProfile> clients = scopeClients(appointments.stream()
                .map(ClientAppointment::getClientCode).collect(Collectors.toSet()));
        List<StaffAppointmentDTO> records = new ArrayList<>();
        for (ClientAppointment item : appointments) {
            ClientProfile client = clients.get(item.getClientCode());
            Staff host = staff.get(item.getHostStaffCode());
            StaffAppointmentDTO dto = new StaffAppointmentDTO();
            dto.setAppointmentNo(item.getAppointmentNo());
            dto.setClientCode(item.getClientCode());
            if (client != null) {
                dto.setCustomerGroup(client.getCustomerGroup());
                dto.setCustomerName(StringUtils.hasText(client.getEnterpriseName())
                        ? client.getEnterpriseName() : client.getContactName());
                dto.setContactName(client.getContactName());
                dto.setContactPhoneMasked(DesensitizeUtils.phone(AesUtils.decrypt(client.getPhone())));
            }
            dto.setHostStaffCode(item.getHostStaffCode());
            if (host != null) {
                dto.setHostStaffName(host.getStaffName());
                dto.setHostDeptCode(host.getDeptCode());
            }
            dto.setServiceMethod(item.getAppointmentType());
            dto.setScheduledStart(item.getScheduledStart());
            dto.setScheduledEnd(item.getScheduledEnd());
            dto.setLocationName(item.getLocationName());
            dto.setLocationDetail(item.getLocationDetail());
            dto.setStatus(item.getStatus());
            dto.setCustomerConfirmStatus(item.getCustomerConfirmStatus());
            dto.setCustomerVisibleNote(item.getCustomerVisibleNote());
            dto.setInternalNote(item.getInternalNote());
            dto.setCreatedByType(item.getCreatedByType());
            dto.setCreatedByCode(item.getCreatedByCode());
            dto.setSourceTerminal(item.getSourceTerminal());
            dto.setRescheduledFromNo(item.getRescheduledFromNo());
            dto.setActualArrivedAt(item.getActualArrivedAt());
            dto.setActualLeftAt(item.getActualLeftAt());
            records.add(dto);
        }
        return records;
    }

    private CustomerAppointmentDTO toCustomerDto(ClientAppointment item, Staff host) {
        CustomerAppointmentDTO dto = new CustomerAppointmentDTO();
        dto.setAppointmentNo(item.getAppointmentNo());
        dto.setServiceMethod(item.getAppointmentType());
        dto.setServiceMethodName(methodName(item.getAppointmentType()));
        dto.setScheduledStart(item.getScheduledStart());
        dto.setScheduledEnd(item.getScheduledEnd());
        dto.setAdviserName(host == null ? null : host.getStaffName());
        dto.setLocationName(item.getLocationName());
        dto.setLocationDetail(item.getLocationDetail());
        dto.setStatus(item.getStatus());
        dto.setStatusName(statusName(item.getStatus()));
        dto.setNextAction(item.getCustomerVisibleNote());
        dto.setChangeAllowed((AppointmentStatus.REQUESTED.name().equals(item.getStatus())
                || AppointmentStatus.CONFIRMED.name().equals(item.getStatus()))
                && AppointmentStateMachine.canSelfChange(item.getScheduledStart(), LocalDateTime.now()));
        return dto;
    }

    private Map<String, Staff> staffByCodes(Set<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return Collections.emptyMap();
        }
        return staffMapper.selectList(new LambdaQueryWrapper<Staff>().in(Staff::getStaffCode, codes)).stream()
                .collect(Collectors.toMap(Staff::getStaffCode, item -> item, (a, b) -> a));
    }

    private Map<String, ClientProfile> scopeClients(Set<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return Collections.emptyMap();
        }
        // 查询范围已在预约主查询中完成，这里只批量补充展示字段。
        return scopeService.clientsByCodes(codes);
    }

    private void validateOrder(String orderNo, String clientCode) {
        if (!StringUtils.hasText(orderNo)) {
            return;
        }
        Long count = orderMapper.selectCount(new LambdaQueryWrapper<ServiceOrder>()
                .eq(ServiceOrder::getOrderNo, orderNo.trim())
                .eq(ServiceOrder::getClientProfileCode, clientCode));
        if (count == null || count != 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "关联工单不存在或不属于该客户");
        }
    }

    private ServiceMethod parseMethod(String value) {
        try {
            return ServiceMethod.valueOf(value == null ? "" : value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "服务方式不合法");
        }
    }

    private AppointmentStatus parseStatus(String value) {
        try {
            return AppointmentStatus.valueOf(value == null ? "" : value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "预约状态不合法");
        }
    }

    private String normalizeTerminal(String terminal) {
        String normalized = StringUtils.hasText(terminal) ? terminal.trim().toUpperCase() : "MINI_APP";
        if (!Arrays.asList("WEB", "MINI_APP", "H5").contains(normalized)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "来源端不合法");
        }
        return normalized;
    }

    private String operatorName(LoanUser user) {
        return StringUtils.hasText(user.getName()) ? user.getName() : user.getUserNo();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String methodName(String value) {
        Map<String, String> names = new HashMap<>();
        names.put("COMPANY_ON_SITE", "公司现场");
        names.put("HOME_VISIT", "上门拜访");
        names.put("VIDEO_MEETING", "视频会议");
        names.put("PHONE_CONSULT", "电话咨询");
        return names.getOrDefault(value, value);
    }

    private String statusName(String value) {
        Map<String, String> names = new HashMap<>();
        names.put("REQUESTED", "待确认");
        names.put("CONFIRMED", "已预约");
        names.put("ARRIVED", "已到店");
        names.put("SERVING", "服务中");
        names.put("COMPLETED", "已完成");
        names.put("CANCELLED", "已取消");
        names.put("NO_SHOW", "未到场");
        names.put("RESCHEDULED", "已改期");
        return names.getOrDefault(value, value);
    }

    private static final class ValidatedAppointment {
        private final ClientProfile client;
        private final Staff host;
        private final ServiceMethod method;

        private ValidatedAppointment(ClientProfile client, Staff host, ServiceMethod method) {
            this.client = client;
            this.host = host;
            this.method = method;
        }
    }
}
