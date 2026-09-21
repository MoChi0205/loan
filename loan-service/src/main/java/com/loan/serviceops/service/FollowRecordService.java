package com.loan.serviceops.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.loan.client.entity.ClientProfile;
import com.loan.client.mapper.ClientProfileMapper;
import com.loan.common.ResultCode;
import com.loan.common.util.BizIdGenerator;
import com.loan.context.LoanUser;
import com.loan.exception.BusinessException;
import com.loan.order.entity.ServiceOrder;
import com.loan.order.mapper.ServiceOrderMapper;
import com.loan.serviceops.dto.FollowRecordCreateRequest;
import com.loan.serviceops.entity.ClientAppointment;
import com.loan.serviceops.entity.ClientFollowRecord;
import com.loan.serviceops.mapper.ClientAppointmentMapper;
import com.loan.serviceops.mapper.ClientFollowRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Arrays;

/** 客户级跟进写入，同时刷新客户最后跟进时间并追加回放事件。 */
@Service
@RequiredArgsConstructor
public class FollowRecordService {

    private final ClientFollowRecordMapper followMapper;
    private final ClientProfileMapper clientProfileMapper;
    private final ClientAppointmentMapper appointmentMapper;
    private final ServiceOrderMapper orderMapper;
    private final ServiceOperationScopeService scopeService;
    private final ClientActivityService activityService;

    @Transactional(rollbackFor = Exception.class)
    public String create(String clientCode, FollowRecordCreateRequest request, LoanUser user) {
        ClientProfile client = scopeService.requireClient(clientCode);
        scopeService.requireClientVisibleToStaff(user, client);
        // 写操作比列表范围更窄：只允许当前归属顾问本人记录跟进。
        if (!user.getUserNo().equals(client.getOwnerStaffCode())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅当前归属顾问可以新增跟进记录");
        }
        validate(request);
        validateReferences(clientCode, request);
        String visibility = StringUtils.hasText(request.getVisibility())
                ? request.getVisibility().trim().toUpperCase() : ClientActivityService.VISIBILITY_STAFF_ONLY;
        if (!Arrays.asList(ClientActivityService.VISIBILITY_STAFF_ONLY,
                ClientActivityService.VISIBILITY_CUSTOMER).contains(visibility)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "跟进可见范围不合法");
        }
        if (ClientActivityService.VISIBILITY_CUSTOMER.equals(visibility)
                && !StringUtils.hasText(request.getCustomerVisibleSummary())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "客户可见跟进必须填写客户摘要");
        }

        LocalDateTime now = LocalDateTime.now();
        ClientFollowRecord record = new ClientFollowRecord();
        record.setFollowNo(BizIdGenerator.generate("follow"));
        record.setClientCode(clientCode);
        record.setOrderNo(trimToNull(request.getOrderNo()));
        record.setAppointmentNo(trimToNull(request.getAppointmentNo()));
        record.setStaffCode(user.getUserNo());
        record.setChannelType(request.getChannelType().trim().toUpperCase());
        record.setResultCode(request.getResultCode().trim());
        record.setContent(request.getContent().trim());
        record.setCustomerVisibleSummary(trimToNull(request.getCustomerVisibleSummary()));
        record.setNextAction(trimToNull(request.getNextAction()));
        record.setNextFollowAt(request.getNextFollowAt());
        record.setVisibility(visibility);
        record.setCreatedBy(operatorName(user));
        record.setCreatedAt(now);
        followMapper.insert(record);

        clientProfileMapper.update(null, new LambdaUpdateWrapper<ClientProfile>()
                .eq(ClientProfile::getClientCode, clientCode)
                .set(ClientProfile::getLastFollowedAt, now)
                .set(ClientProfile::getUpdatedBy, operatorName(user))
                .set(ClientProfile::getUpdatedAt, now));

        String summary = ClientActivityService.VISIBILITY_CUSTOMER.equals(visibility)
                ? record.getCustomerVisibleSummary() : "员工已记录客户跟进";
        activityService.append(clientCode, user.getUserNo(), "FOLLOW_RECORDED", "FOLLOW",
                record.getFollowNo(), summary, visibility,
                LoanUser.TYPE_STAFF, user.getUserNo(), null);
        return record.getFollowNo();
    }

    private void validate(FollowRecordCreateRequest request) {
        if (request == null || !StringUtils.hasText(request.getChannelType())
                || !StringUtils.hasText(request.getResultCode()) || !StringUtils.hasText(request.getContent())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "跟进渠道、结果和内容必填");
        }
        String channel = request.getChannelType().trim().toUpperCase();
        if (!Arrays.asList("PHONE", "COMPANY_ON_SITE", "HOME_VISIT",
                "VIDEO_MEETING", "WECOM", "OTHER").contains(channel)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "跟进渠道不合法");
        }
    }

    private void validateReferences(String clientCode, FollowRecordCreateRequest request) {
        if (StringUtils.hasText(request.getAppointmentNo())) {
            Long count = appointmentMapper.selectCount(new LambdaQueryWrapper<ClientAppointment>()
                    .eq(ClientAppointment::getAppointmentNo, request.getAppointmentNo().trim())
                    .eq(ClientAppointment::getClientCode, clientCode));
            if (count == null || count != 1) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "关联预约不存在或不属于该客户");
            }
        }
        if (StringUtils.hasText(request.getOrderNo())) {
            Long count = orderMapper.selectCount(new LambdaQueryWrapper<ServiceOrder>()
                    .eq(ServiceOrder::getOrderNo, request.getOrderNo().trim())
                    .eq(ServiceOrder::getClientProfileCode, clientCode));
            if (count == null || count != 1) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "关联工单不存在或不属于该客户");
            }
        }
    }

    private String operatorName(LoanUser user) {
        return StringUtils.hasText(user.getName()) ? user.getName() : user.getUserNo();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
