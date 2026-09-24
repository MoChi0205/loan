package com.loan.serviceops.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loan.api.dto.PageResult;
import com.loan.client.entity.ClientProfile;
import com.loan.common.ResultCode;
import com.loan.common.util.BizIdGenerator;
import com.loan.context.LoanUser;
import com.loan.exception.BusinessException;
import com.loan.infrastructure.oss.OssObjectOpenResult;
import com.loan.infrastructure.oss.OssStorageService;
import com.loan.infrastructure.security.AesUtils;
import com.loan.serviceops.dto.LocationCheckInRequest;
import com.loan.serviceops.dto.OutingCreateRequest;
import com.loan.serviceops.dto.StaffOutingDTO;
import com.loan.serviceops.entity.ClientAppointment;
import com.loan.serviceops.entity.StaffOuting;
import com.loan.serviceops.mapper.StaffOutingMapper;
import com.loan.serviceops.model.AppointmentStatus;
import com.loan.serviceops.model.OutingStateMachine;
import com.loan.serviceops.model.OutingStatus;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 员工外出：<b>本人提交申请 → 主管审核 → 出发/返回双打卡（现场照片 + 单点定位）</b>。
 *
 * <p>三条硬约束（2026-09-21 与用户确认）：
 * <ol>
 *   <li><b>关联预约不接受他人代录</b>：只有预约的主服务顾问本人能创建；无预约普通外出由员工本人提交；</li>
 *   <li><b>必须审核通过才能打卡</b>：待审核状态下 depart 条件更新不会命中；</li>
 *   <li><b>打卡必须同时有照片与定位</b>：缺任一项直接拒绝，避免无凭证打卡。</li>
 * </ol>
 *
 * <p>照片只存 fileKey（{@code att}+32 位随机），实际文件在 OSS；预览走受控接口，
 * 不把存储路径暴露给前端。
 */
@Service
@RequiredArgsConstructor
public class OutingService {

    /** 打卡照片允许的扩展名（与上传接口一致，仅图片）。 */
    private static final String[] PHOTO_EXTS = {".jpg", ".jpeg", ".png", ".webp"};

    /** 照片 fileKey 格式：att + 32 位十六进制（沿用项目业务 ID 约定）。 */
    private static final String PHOTO_KEY_PATTERN = "att[0-9a-fA-F]{32}";

    private final StaffOutingMapper outingMapper;
    private final StaffMapper staffMapper;
    private final AppointmentService appointmentService;
    private final ServiceOperationScopeService scopeService;
    private final ClientActivityService activityService;
    private final ObjectMapper objectMapper;
    private final OssStorageService ossStorageService;

    /**
     * 提交外出申请（本人）。
     *
     * @param request 申请内容（可选预约号 / 计划时间 / 目的地 / 外出目的）
     * @param user    当前用户，必须是该预约的主服务顾问本人
     * @return 外出业务编号
     */
    @Transactional(rollbackFor = Exception.class)
    public String create(OutingCreateRequest request, LoanUser user) {
        scopeService.requireStaffListAccess(user);
        if (request == null) throw new BusinessException(ResultCode.PARAM_ERROR, "外出信息不能为空");
        ClientAppointment appointment = null;
        if (StringUtils.hasText(request.getAppointmentNo())) {
            appointment = appointmentService.requireAppointment(request.getAppointmentNo());
            if (!ServiceMethod.HOME_VISIT.name().equals(appointment.getAppointmentType())) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "只有上门拜访预约可以创建关联外出");
            }
            requireApplicant(user, appointment);
            if (!AppointmentStatus.CONFIRMED.name().equals(appointment.getStatus())) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "预约确认后才能提交外出申请");
            }
        } else {
            if (request.getPlannedStart() == null || request.getPlannedEnd() == null
                    || !request.getPlannedEnd().isAfter(request.getPlannedStart())) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "无关联客户外出必须填写有效计划时间");
            }
            if (!"GENERAL".equalsIgnoreCase(request.getOutingType())) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "无关联客户外出类型必须为普通外出");
            }
        }
        if (!StringUtils.hasText(request.getDestination()) || !StringUtils.hasText(request.getPurpose())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "目的地和拜访目的必填");
        }
        Long existed = appointment == null ? 0L : outingMapper.selectCount(new LambdaQueryWrapper<StaffOuting>()
                .eq(StaffOuting::getAppointmentNo, appointment.getAppointmentNo()));
        if (existed != null && existed > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "该预约已存在外出记录");
        }

        LocalDateTime now = LocalDateTime.now();
        StaffOuting outing = new StaffOuting();
        outing.setOutingNo(BizIdGenerator.generate("outing"));
        outing.setStaffCode(appointment == null ? user.getUserNo() : appointment.getHostStaffCode());
        outing.setClientCode(appointment == null ? null : appointment.getClientCode());
        outing.setAppointmentNo(appointment == null ? null : appointment.getAppointmentNo());
        outing.setOrderNo(appointment == null ? null : appointment.getOrderNo());
        outing.setOutingType(appointment == null ? "GENERAL" : ServiceMethod.HOME_VISIT.name());
        outing.setPlannedStart(appointment == null ? request.getPlannedStart() : appointment.getScheduledStart());
        outing.setPlannedEnd(appointment == null ? request.getPlannedEnd() : appointment.getScheduledEnd());
        outing.setSubmittedAt(now);
        outing.setDestination(request.getDestination().trim());
        outing.setPurpose(request.getPurpose().trim());
        outing.setStatus(OutingStatus.PENDING_REVIEW.name());
        outing.setInternalNote(trimToNull(request.getInternalNote()));
        outing.setCreatedBy(operatorName(user));
        outing.setUpdatedBy(operatorName(user));
        outing.setCreatedAt(now);
        outing.setUpdatedAt(now);
        outingMapper.insert(outing);
        appendStaffActivity(outing, "OUTING_SUBMITTED", appointment == null ? "提交普通外出申请，待主管审核" : "提交上门服务外出申请，待主管审核", user);
        return outing.getOutingNo();
    }

    /**
     * 审核通过：待审核 → 待出发。
     *
     * @param outingNo 外出业务编号
     * @param remark   审核意见（可空）
     * @param user     审核人（不可自审；部门经理限本部门）
     */
    @Transactional(rollbackFor = Exception.class)
    public void approve(String outingNo, String remark, LoanUser user) {
        StaffOuting outing = requireOuting(outingNo);
        requireReviewer(user, outing);
        requirePendingReview(outing);
        String reviewer = operatorName(user);
        int changed = outingMapper.approve(outingNo, user.getUserNo(), reviewer,
                trimToNull(remark), LocalDateTime.now());
        if (changed != 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "该申请已被处理，请刷新后重试");
        }
        appendStaffActivity(outing, "OUTING_APPROVED", "外出申请已通过审核", user);
    }

    /**
     * 审核驳回：待审核 → 已驳回（可修改后重提）。
     *
     * @param outingNo 外出业务编号
     * @param remark   驳回原因（必填）
     * @param user     审核人
     */
    @Transactional(rollbackFor = Exception.class)
    public void reject(String outingNo, String remark, LoanUser user) {
        if (!StringUtils.hasText(remark)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "驳回原因必填");
        }
        StaffOuting outing = requireOuting(outingNo);
        requireReviewer(user, outing);
        requirePendingReview(outing);
        String reviewer = operatorName(user);
        int changed = outingMapper.reject(outingNo, user.getUserNo(), reviewer,
                remark.trim(), LocalDateTime.now());
        if (changed != 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "该申请已被处理，请刷新后重试");
        }
        appendStaffActivity(outing, "OUTING_REJECTED", "外出申请被驳回：" + remark.trim(), user);
    }

    /**
     * 驳回后修改并重新提交：已驳回 → 待审核。
     *
     * @param outingNo 外出业务编号
     * @param request  可选的修改内容（目的地 / 拜访目的 / 备注，为空则沿用原值）
     * @param user     申请人本人
     */
    @Transactional(rollbackFor = Exception.class)
    public void resubmit(String outingNo, OutingCreateRequest request, LoanUser user) {
        StaffOuting outing = requireOuting(outingNo);
        if (!ServiceOperationAccessPolicy.canResubmitOuting(user, outing.getStaffCode())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有申请人本人可以重新提交");
        }
        if (statusOf(outing) != OutingStatus.REJECTED) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "只有被驳回的申请可以重新提交");
        }
        LocalDateTime now = LocalDateTime.now();
        if (request != null && (StringUtils.hasText(request.getDestination())
                || StringUtils.hasText(request.getPurpose())
                || StringUtils.hasText(request.getInternalNote()))) {
            StaffOuting patch = new StaffOuting();
            patch.setId(outing.getId());
            if (StringUtils.hasText(request.getDestination())) {
                patch.setDestination(request.getDestination().trim());
            }
            if (StringUtils.hasText(request.getPurpose())) {
                patch.setPurpose(request.getPurpose().trim());
            }
            if (StringUtils.hasText(request.getInternalNote())) {
                patch.setInternalNote(request.getInternalNote().trim());
            }
            patch.setUpdatedBy(operatorName(user));
            patch.setUpdatedAt(now);
            outingMapper.updateById(patch);
        }
        int changed = outingMapper.resubmit(outingNo, now, operatorName(user));
        if (changed != 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "该申请状态已变化，请刷新后重试");
        }
        appendStaffActivity(outing, "OUTING_SUBMITTED", "重新提交外出申请，待主管审核", user);
    }

    /**
     * 出发打卡：待出发 → 外出中。必须带定位与现场照片。
     */
    @Transactional(rollbackFor = Exception.class)
    public void depart(String outingNo, LocationCheckInRequest request, LoanUser user) {
        StaffOuting outing = requireOuting(outingNo);
        requireOwner(user, outing);
        LocalDateTime now = LocalDateTime.now();
        String ciphertext = encryptLocation(request, now);
        String photoKey = requireStoredPhoto(request);
        int changed = outingMapper.depart(outingNo, now, ciphertext, photoKey, operatorName(user));
        if (changed != 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "当前状态不能出发打卡（需审核通过），请刷新后重试");
        }
        // HOME_VISIT 从 CONFIRMED 直接进入 SERVING，不产生 ARRIVED。
        if (StringUtils.hasText(outing.getAppointmentNo())) {
            appointmentService.startService(outing.getAppointmentNo(), user);
        }
        appendStaffActivity(outing, "OUTING_DEPARTED", "员工已出发外出服务", user);
    }

    /**
     * 返回打卡：外出中 → 已完成。必须带定位与现场照片。
     */
    @Transactional(rollbackFor = Exception.class)
    public void returnFromOuting(String outingNo, LocationCheckInRequest request, LoanUser user) {
        StaffOuting outing = requireOuting(outingNo);
        requireOwner(user, outing);
        LocalDateTime now = LocalDateTime.now();
        String ciphertext = encryptLocation(request, now);
        String photoKey = requireStoredPhoto(request);
        int changed = outingMapper.returnFromOuting(outingNo, now, ciphertext, photoKey, operatorName(user));
        if (changed != 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请先完成出发打卡，或刷新当前状态");
        }
        if (StringUtils.hasText(outing.getAppointmentNo())) {
            appointmentService.complete(outing.getAppointmentNo(), user);
        }
        appendStaffActivity(outing, "OUTING_RETURNED", "员工已返回，外出服务完成", user);
    }

    /**
     * 校验当前用户可上传该外出的打卡照片（申请人本人），返回外出记录。
     *
     * @param outingNo 外出业务编号
     * @param user     当前用户
     * @return 外出记录
     */
    public StaffOuting requireCheckInUploader(String outingNo, LoanUser user) {
        StaffOuting outing = requireOuting(outingNo);
        requireOwner(user, outing);
        return outing;
    }

    /**
     * 受控读取打卡照片（申请人本人，或对该外出有审核权的角色）。
     *
     * @param outingNo 外出业务编号
     * @param phase    {@code DEPART} 出发 / {@code RETURN} 返回
     * @param user     当前用户
     * @return 可流式回传的对象
     */
    public OssObjectOpenResult openCheckInPhoto(String outingNo, String phase, LoanUser user) {
        StaffOuting outing = requireOuting(outingNo);
        Staff applicant = scopeService.findStaff(outing.getStaffCode());
        String deptCode = applicant == null ? null : applicant.getDeptCode();
        boolean self = ServiceOperationAccessPolicy.canCheckOuting(user, outing.getStaffCode());
        boolean reviewer = ServiceOperationAccessPolicy.canReviewOuting(user, outing.getStaffCode(), deptCode);
        if (!self && !reviewer) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查看该外出的打卡照片");
        }
        boolean returnPhase = "RETURN".equalsIgnoreCase(phase);
        String fileKey = returnPhase ? outing.getReturnedPhotoKey() : outing.getDepartedPhotoKey();
        if (!StringUtils.hasText(fileKey)) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "该阶段尚未打卡，没有照片");
        }
        String objectKey = resolvePhotoObjectKey(fileKey);
        if (objectKey == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "打卡照片不存在");
        }
        return ossStorageService.openObject(objectKey);
    }

    /**
     * 当日外出名单（含待审核）。
     *
     * @param date   业务日期（默认今天）
     * @param status 状态过滤（可选，取值来自 {@link OutingStatus}）
     * @param user   当前用户
     * @param page   页码
     * @param size   每页条数
     * @return 分页结果
     */
    public PageResult<StaffOutingDTO> day(LocalDate date, String status, LoanUser user, int page, int size) {
        LocalDate day = date == null ? LocalDate.now() : date;
        LambdaQueryWrapper<StaffOuting> wrapper = new LambdaQueryWrapper<StaffOuting>()
                .ge(StaffOuting::getPlannedStart, day.atStartOfDay())
                .lt(StaffOuting::getPlannedStart, day.plusDays(1).atStartOfDay())
                .orderByAsc(StaffOuting::getPlannedStart);
        if (StringUtils.hasText(status)) {
            String value = status.trim().toUpperCase();
            Set<String> allowed = Arrays.stream(OutingStatus.values()).map(Enum::name).collect(Collectors.toSet());
            if (!allowed.contains(value)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "外出状态不合法");
            }
            wrapper.eq(StaffOuting::getStatus, value);
        }
        scopeService.applyOutingListScope(wrapper, user);
        Page<StaffOuting> result = outingMapper.selectPage(new Page<>(page, size), wrapper);
        Map<String, Staff> staff = staffByCodes(result.getRecords().stream()
                .map(StaffOuting::getStaffCode).collect(Collectors.toSet()));
        Map<String, String> departmentNames = scopeService.departmentNames(staff.values().stream()
                .map(Staff::getDeptCode)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet()));
        Map<String, ClientProfile> clients = scopeService.clientsByCodes(result.getRecords().stream()
                .map(StaffOuting::getClientCode)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet()));
        List<StaffOutingDTO> records = result.getRecords().stream().map(item -> {
            StaffOutingDTO dto = new StaffOutingDTO();
            dto.setOutingNo(item.getOutingNo());
            dto.setStaffCode(item.getStaffCode());
            Staff member = staff.get(item.getStaffCode());
            if (member != null) {
                dto.setStaffName(member.getStaffName());
                dto.setDeptCode(member.getDeptCode());
                dto.setDeptName(departmentNames.get(member.getDeptCode()));
            }
            dto.setClientCode(item.getClientCode());
            ClientProfile client = clients.get(item.getClientCode());
            if (client != null) {
                dto.setCustomerName(StringUtils.hasText(client.getEnterpriseName())
                        ? client.getEnterpriseName() : client.getContactName());
            }
            dto.setAppointmentNo(item.getAppointmentNo());
            dto.setOrderNo(item.getOrderNo());
            dto.setPlannedStart(item.getPlannedStart());
            dto.setPlannedEnd(item.getPlannedEnd());
            dto.setSubmittedAt(item.getSubmittedAt());
            dto.setActualDepartedAt(item.getActualDepartedAt());
            dto.setActualReturnedAt(item.getActualReturnedAt());
            dto.setDestination(item.getDestination());
            dto.setPurpose(item.getPurpose());
            dto.setStatus(item.getStatus());
            dto.setDepartureCheckInCompleted(item.getActualDepartedAt() != null);
            dto.setReturnCheckInCompleted(item.getActualReturnedAt() != null);
            dto.setReviewerStaffCode(item.getReviewerStaffCode());
            dto.setReviewerName(item.getReviewerName());
            dto.setReviewedAt(item.getReviewedAt());
            dto.setReviewRemark(item.getReviewRemark());
            dto.setDepartedPhotoKey(item.getDepartedPhotoKey());
            dto.setReturnedPhotoKey(item.getReturnedPhotoKey());
            dto.setOutingType(item.getOutingType());
            return dto;
        }).collect(Collectors.toList());
        return PageResult.build(page, size, result.getTotal(), records);
    }

    /**
     * 外出待审批列表：不受“当天”限制，按当前角色数据范围返回全部待审核申请。
     * 部门经理仅本部门，运营/老板/超管为公司范围；服务端审批时仍会再次校验并禁止自审。
     */
    public PageResult<StaffOutingDTO> pending(LoanUser user, int page, int size) {
        LambdaQueryWrapper<StaffOuting> wrapper = new LambdaQueryWrapper<StaffOuting>()
                .eq(StaffOuting::getStatus, OutingStatus.PENDING_REVIEW.name())
                .ne(StaffOuting::getStaffCode, user == null ? null : user.getUserNo())
                .orderByAsc(StaffOuting::getSubmittedAt)
                .orderByAsc(StaffOuting::getPlannedStart);
        scopeService.applyOutingListScope(wrapper, user);
        Page<StaffOuting> result = outingMapper.selectPage(new Page<>(page, size), wrapper);
        return toPageResult(result, page, size);
    }

    /** 当前员工本人提交的外出申请，用于“我的申请”聚合。 */
    public List<StaffOuting> myApplications(String staffCode, int limit) {
        if (!StringUtils.hasText(staffCode)) {
            return java.util.Collections.emptyList();
        }
        return outingMapper.selectList(new LambdaQueryWrapper<StaffOuting>()
                .eq(StaffOuting::getStaffCode, staffCode)
                .orderByDesc(StaffOuting::getCreatedAt)
                .last("LIMIT " + Math.max(1, Math.min(limit, 100))));
    }

    private PageResult<StaffOutingDTO> toPageResult(Page<StaffOuting> result, int page, int size) {
        Map<String, Staff> staff = staffByCodes(result.getRecords().stream()
                .map(StaffOuting::getStaffCode).collect(Collectors.toSet()));
        Map<String, String> departmentNames = scopeService.departmentNames(staff.values().stream()
                .map(Staff::getDeptCode).filter(StringUtils::hasText).collect(Collectors.toSet()));
        Map<String, ClientProfile> clients = scopeService.clientsByCodes(result.getRecords().stream()
                .map(StaffOuting::getClientCode).filter(StringUtils::hasText).collect(Collectors.toSet()));
        List<StaffOutingDTO> records = result.getRecords().stream()
                .map(item -> toDto(item, staff, departmentNames, clients)).collect(Collectors.toList());
        return PageResult.build(page, size, result.getTotal(), records);
    }

    private StaffOutingDTO toDto(StaffOuting item, Map<String, Staff> staff,
                                 Map<String, String> departmentNames,
                                 Map<String, ClientProfile> clients) {
        StaffOutingDTO dto = new StaffOutingDTO();
        dto.setOutingNo(item.getOutingNo());
        Staff member = staff.get(item.getStaffCode());
        if (member != null) {
            dto.setStaffName(member.getStaffName());
            dto.setDeptName(departmentNames.get(member.getDeptCode()));
        }
        ClientProfile client = clients.get(item.getClientCode());
        if (client != null) {
            dto.setCustomerName(StringUtils.hasText(client.getEnterpriseName())
                    ? client.getEnterpriseName() : client.getContactName());
        }
        dto.setPlannedStart(item.getPlannedStart());
        dto.setPlannedEnd(item.getPlannedEnd());
        dto.setSubmittedAt(item.getSubmittedAt());
        dto.setActualDepartedAt(item.getActualDepartedAt());
        dto.setActualReturnedAt(item.getActualReturnedAt());
        dto.setDestination(item.getDestination());
        dto.setPurpose(item.getPurpose());
        dto.setStatus(item.getStatus());
        dto.setDepartureCheckInCompleted(item.getActualDepartedAt() != null);
        dto.setReturnCheckInCompleted(item.getActualReturnedAt() != null);
        dto.setReviewerName(item.getReviewerName());
        dto.setReviewedAt(item.getReviewedAt());
        dto.setReviewRemark(item.getReviewRemark());
        dto.setDepartedPhotoKey(item.getDepartedPhotoKey());
        dto.setReturnedPhotoKey(item.getReturnedPhotoKey());
        dto.setOutingType(item.getOutingType());
        return dto;
    }

    private StaffOuting requireOuting(String outingNo) {
        StaffOuting outing = outingMapper.selectOne(new LambdaQueryWrapper<StaffOuting>()
                .eq(StaffOuting::getOutingNo, outingNo));
        if (outing == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "外出记录不存在");
        }
        return outing;
    }

    /** 关联预约时不允许他人代录：必须是预约的主服务顾问本人；普通外出由本人提交。 */
    private void requireApplicant(LoanUser user, ClientAppointment appointment) {
        boolean self = user != null
                && LoanUser.TYPE_STAFF.equalsIgnoreCase(user.getUserType())
                && StringUtils.hasText(user.getUserNo())
                && user.getUserNo().equals(appointment.getHostStaffCode());
        if (!self) {
            throw new BusinessException(ResultCode.FORBIDDEN,
                    "外出申请只能由预约的主服务顾问本人提交，不接受他人代录");
        }
    }

    private void requireOwner(LoanUser user, StaffOuting outing) {
        if (!ServiceOperationAccessPolicy.canCheckOuting(user, outing.getStaffCode())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅外出员工本人可以操作");
        }
    }

    private void requireReviewer(LoanUser user, StaffOuting outing) {
        Staff applicant = scopeService.findStaff(outing.getStaffCode());
        String deptCode = applicant == null ? null : applicant.getDeptCode();
        if (!ServiceOperationAccessPolicy.canReviewOuting(user, outing.getStaffCode(), deptCode)) {
            throw new BusinessException(ResultCode.FORBIDDEN,
                    "无审核权限：不能审核本人提交的申请，部门经理仅可审核本部门");
        }
    }

    private void requirePendingReview(StaffOuting outing) {
        if (!OutingStateMachine.canReview(statusOf(outing))) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "当前状态不可审核，请刷新后重试");
        }
    }

    private OutingStatus statusOf(StaffOuting outing) {
        try {
            return OutingStatus.valueOf(outing.getStatus());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "外出状态异常：" + outing.getStatus());
        }
    }

    private Map<String, Staff> staffByCodes(Set<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return Collections.emptyMap();
        }
        return staffMapper.selectList(new LambdaQueryWrapper<Staff>().in(Staff::getStaffCode, codes)).stream()
                .collect(Collectors.toMap(Staff::getStaffCode, item -> item, (a, b) -> a));
    }

    /** 校验并返回已被 OSS 落盘的打卡照片 fileKey（照片 + 定位缺一不可）。 */
    private String requireStoredPhoto(LocationCheckInRequest request) {
        if (request == null || !StringUtils.hasText(request.getPhotoFileKey())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "打卡必须上传现场照片");
        }
        String fileKey = request.getPhotoFileKey().trim();
        if (!fileKey.matches(PHOTO_KEY_PATTERN)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "打卡照片标识不合法");
        }
        if (resolvePhotoObjectKey(fileKey) == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "打卡照片不存在或上传未完成，请重新上传");
        }
        return fileKey;
    }

    /** 按扩展名定位 OSS 对象；找不到返回 null。 */
    private String resolvePhotoObjectKey(String fileKey) {
        for (String ext : PHOTO_EXTS) {
            if (ossStorageService.objectExists(fileKey + ext)) {
                return fileKey + ext;
            }
        }
        return null;
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
        Map<String, Object> payload = new LinkedHashMap<>();
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

    private String operatorName(LoanUser user) {
        return StringUtils.hasText(user.getName()) ? user.getName() : user.getUserNo();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void appendStaffActivity(StaffOuting outing, String eventType, String summary, LoanUser user) {
        if (StringUtils.hasText(outing.getClientCode())) {
            activityService.append(outing.getClientCode(), outing.getStaffCode(), eventType, "OUTING",
                    outing.getOutingNo(), summary, ClientActivityService.VISIBILITY_STAFF_ONLY,
                    LoanUser.TYPE_STAFF, user.getUserNo(), null);
        }
    }
}
