package com.loan.serviceops.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loan.api.dto.PageResult;
import com.loan.common.ResultCode;
import com.loan.common.util.BizIdGenerator;
import com.loan.context.LoanUser;
import com.loan.exception.BusinessException;
import com.loan.infrastructure.security.AesUtils;
import com.loan.serviceops.dto.LocationCheckInRequest;
import com.loan.serviceops.dto.OutingCreateRequest;
import com.loan.serviceops.dto.StaffOutingDTO;
import com.loan.serviceops.entity.ClientAppointment;
import com.loan.serviceops.entity.StaffOuting;
import com.loan.serviceops.mapper.StaffOutingMapper;
import com.loan.serviceops.model.AppointmentStatus;
import com.loan.serviceops.model.ServiceMethod;
import com.loan.serviceops.security.ServiceOperationAccessPolicy;
import com.loan.staff.entity.Staff;
import com.loan.staff.mapper.StaffMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 上门拜访外出记录：无审批，必须出发/返回双打卡。 */
@Service
@RequiredArgsConstructor
public class OutingService {

    private final StaffOutingMapper outingMapper;
    private final StaffMapper staffMapper;
    private final AppointmentService appointmentService;
    private final ServiceOperationScopeService scopeService;
    private final ClientActivityService activityService;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public String create(OutingCreateRequest request, LoanUser user) {
        scopeService.requireStaffListAccess(user);
        if (request == null || !StringUtils.hasText(request.getAppointmentNo())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "上门预约不能为空");
        }
        ClientAppointment appointment = appointmentService.requireAppointment(request.getAppointmentNo());
        if (!ServiceMethod.HOME_VISIT.name().equals(appointment.getAppointmentType())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "只有上门拜访预约可以创建外出记录");
        }
        if (!ServiceOperationAccessPolicy.canOperateAppointment(
                user, appointment.getClientCode(), appointment.getHostStaffCode())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅主服务顾问可以创建本人外出记录");
        }
        if (!AppointmentStatus.CONFIRMED.name().equals(appointment.getStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "预约确认后才能创建外出记录");
        }
        if (!StringUtils.hasText(request.getDestination()) || !StringUtils.hasText(request.getPurpose())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "目的地和拜访目的必填");
        }
        Long existed = outingMapper.selectCount(new LambdaQueryWrapper<StaffOuting>()
                .eq(StaffOuting::getAppointmentNo, appointment.getAppointmentNo()));
        if (existed != null && existed > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "该预约已存在外出记录");
        }

        StaffOuting outing = new StaffOuting();
        outing.setOutingNo(BizIdGenerator.generate("outing"));
        outing.setStaffCode(appointment.getHostStaffCode());
        outing.setClientCode(appointment.getClientCode());
        outing.setAppointmentNo(appointment.getAppointmentNo());
        outing.setOrderNo(appointment.getOrderNo());
        outing.setOutingType(ServiceMethod.HOME_VISIT.name());
        outing.setPlannedStart(appointment.getScheduledStart());
        outing.setPlannedEnd(appointment.getScheduledEnd());
        outing.setDestination(request.getDestination().trim());
        outing.setPurpose(request.getPurpose().trim());
        outing.setStatus("READY");
        outing.setInternalNote(trimToNull(request.getInternalNote()));
        outing.setCreatedBy(operatorName(user));
        outing.setUpdatedBy(operatorName(user));
        outing.setCreatedAt(LocalDateTime.now());
        outing.setUpdatedAt(LocalDateTime.now());
        outingMapper.insert(outing);
        activityService.append(outing.getClientCode(), outing.getStaffCode(),
                "OUTING_READY", "OUTING", outing.getOutingNo(), "上门服务已准备",
                ClientActivityService.VISIBILITY_STAFF_ONLY,
                LoanUser.TYPE_STAFF, user.getUserNo(), null);
        return outing.getOutingNo();
    }

    @Transactional(rollbackFor = Exception.class)
    public void depart(String outingNo, LocationCheckInRequest request, LoanUser user) {
        StaffOuting outing = requireOuting(outingNo);
        requireOwner(user, outing);
        LocalDateTime now = LocalDateTime.now();
        String ciphertext = encryptLocation(request, now);
        int changed = outingMapper.depart(outingNo, now, ciphertext, operatorName(user));
        if (changed != 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "当前状态不能出发打卡，请刷新后重试");
        }
        // HOME_VISIT 从 CONFIRMED 直接进入 SERVING，不产生 ARRIVED。
        appointmentService.startService(outing.getAppointmentNo(), user);
        activityService.append(outing.getClientCode(), outing.getStaffCode(),
                "OUTING_DEPARTED", "OUTING", outing.getOutingNo(), "员工已出发上门服务",
                ClientActivityService.VISIBILITY_STAFF_ONLY,
                LoanUser.TYPE_STAFF, user.getUserNo(), null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void returnFromOuting(String outingNo, LocationCheckInRequest request, LoanUser user) {
        StaffOuting outing = requireOuting(outingNo);
        requireOwner(user, outing);
        LocalDateTime now = LocalDateTime.now();
        String ciphertext = encryptLocation(request, now);
        int changed = outingMapper.returnFromOuting(outingNo, now, ciphertext, operatorName(user));
        if (changed != 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请先完成出发打卡，或刷新当前状态");
        }
        appointmentService.complete(outing.getAppointmentNo(), user);
        activityService.append(outing.getClientCode(), outing.getStaffCode(),
                "OUTING_RETURNED", "OUTING", outing.getOutingNo(), "员工已返回，上门服务完成",
                ClientActivityService.VISIBILITY_STAFF_ONLY,
                LoanUser.TYPE_STAFF, user.getUserNo(), null);
    }

    public PageResult<StaffOutingDTO> day(LocalDate date, String status, LoanUser user, int page, int size) {
        LocalDate day = date == null ? LocalDate.now() : date;
        LambdaQueryWrapper<StaffOuting> wrapper = new LambdaQueryWrapper<StaffOuting>()
                .ge(StaffOuting::getPlannedStart, day.atStartOfDay())
                .lt(StaffOuting::getPlannedStart, day.plusDays(1).atStartOfDay())
                .orderByAsc(StaffOuting::getPlannedStart);
        if (StringUtils.hasText(status)) {
            String value = status.trim().toUpperCase();
            if (!java.util.Arrays.asList("DRAFT", "READY", "IN_PROGRESS", "COMPLETED", "CANCELLED").contains(value)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "外出状态不合法");
            }
            wrapper.eq(StaffOuting::getStatus, value);
        }
        scopeService.applyOutingListScope(wrapper, user);
        Page<StaffOuting> result = outingMapper.selectPage(new Page<>(page, size), wrapper);
        Map<String, Staff> staff = staffByCodes(result.getRecords().stream()
                .map(StaffOuting::getStaffCode).collect(Collectors.toSet()));
        List<StaffOutingDTO> records = result.getRecords().stream().map(item -> {
            StaffOutingDTO dto = new StaffOutingDTO();
            dto.setOutingNo(item.getOutingNo());
            dto.setStaffCode(item.getStaffCode());
            Staff member = staff.get(item.getStaffCode());
            if (member != null) {
                dto.setStaffName(member.getStaffName());
                dto.setDeptCode(member.getDeptCode());
            }
            dto.setClientCode(item.getClientCode());
            dto.setAppointmentNo(item.getAppointmentNo());
            dto.setOrderNo(item.getOrderNo());
            dto.setPlannedStart(item.getPlannedStart());
            dto.setPlannedEnd(item.getPlannedEnd());
            dto.setActualDepartedAt(item.getActualDepartedAt());
            dto.setActualReturnedAt(item.getActualReturnedAt());
            dto.setDestination(item.getDestination());
            dto.setPurpose(item.getPurpose());
            dto.setStatus(item.getStatus());
            dto.setDepartureCheckInCompleted(item.getActualDepartedAt() != null);
            dto.setReturnCheckInCompleted(item.getActualReturnedAt() != null);
            return dto;
        }).collect(Collectors.toList());
        return PageResult.build(page, size, result.getTotal(), records);
    }

    private StaffOuting requireOuting(String outingNo) {
        StaffOuting outing = outingMapper.selectOne(new LambdaQueryWrapper<StaffOuting>()
                .eq(StaffOuting::getOutingNo, outingNo));
        if (outing == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "外出记录不存在");
        }
        return outing;
    }

    private void requireOwner(LoanUser user, StaffOuting outing) {
        if (!ServiceOperationAccessPolicy.canCheckOuting(user, outing.getStaffCode())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅外出员工本人可以打卡");
        }
    }

    private String encryptLocation(LocationCheckInRequest request, LocalDateTime now) {
        if (request == null || request.getLatitude() == null || request.getLongitude() == null
                || request.getAccuracyMeters() == null || !StringUtils.hasText(request.getLocationText())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "打卡必须提供单点位置、精度和位置文本");
        }
        if (request.getLatitude().compareTo(new BigDecimal("-90")) < 0
                || request.getLatitude().compareTo(new BigDecimal("90")) > 0
                || request.getLongitude().compareTo(new BigDecimal("-180")) < 0
                || request.getLongitude().compareTo(new BigDecimal("180")) > 0
                || request.getAccuracyMeters().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "定位坐标或精度不合法");
        }
        LocalDateTime collectedAt = request.getCollectedAt() == null ? now : request.getCollectedAt();
        if (Math.abs(Duration.between(collectedAt, now).toMinutes()) > 10) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "定位信息已过期，请重新获取当前位置");
        }
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("latitude", request.getLatitude());
        payload.put("longitude", request.getLongitude());
        payload.put("accuracyMeters", request.getAccuracyMeters());
        payload.put("locationText", request.getLocationText().trim());
        payload.put("collectedAt", collectedAt.toString());
        try {
            String ciphertext = AesUtils.encrypt(objectMapper.writeValueAsString(payload));
            if (!StringUtils.hasText(ciphertext)) {
                throw new BusinessException(ResultCode.INTERNAL_ERROR, "定位信息加密失败");
            }
            return ciphertext;
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "定位信息处理失败");
        }
    }

    private Map<String, Staff> staffByCodes(Set<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return Collections.emptyMap();
        }
        return staffMapper.selectList(new LambdaQueryWrapper<Staff>().in(Staff::getStaffCode, codes)).stream()
                .collect(Collectors.toMap(Staff::getStaffCode, item -> item, (a, b) -> a));
    }

    private String operatorName(LoanUser user) {
        return StringUtils.hasText(user.getName()) ? user.getName() : user.getUserNo();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
