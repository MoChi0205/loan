package com.loan.common.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.loan.client.entity.ClientProfile;
import com.loan.client.mapper.ClientProfileMapper;
import com.loan.invitation.entity.Invitation;
import com.loan.product.entity.BankProduct;
import com.loan.product.mapper.BankProductMapper;
import com.loan.staff.entity.Staff;
import com.loan.staff.mapper.StaffMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 业务名称批量装配服务。
 *
 * <p>统一按员工、客户、产品业务编码批量加载名称，供审批、客户、公海、工单等列表复用；
 * 单条名称解析同样复用批量入口，避免页面转换过程中产生 N+1 查询。</p>
 */
@Service
@RequiredArgsConstructor
public class BusinessNameService {

    private final StaffMapper staffMapper;
    private final ClientProfileMapper clientProfileMapper;
    private final BankProductMapper bankProductMapper;

    /** 按员工业务编码批量加载姓名。 */
    public Map<String, String> staffNames(Collection<String> staffCodes) {
        Set<String> codes = normalizeCodes(staffCodes);
        if (codes.isEmpty()) return Collections.emptyMap();
        return staffMapper.selectList(new LambdaQueryWrapper<Staff>()
                        .in(Staff::getStaffCode, codes)).stream()
                .filter(staff -> StringUtils.hasText(staff.getStaffCode()))
                .collect(Collectors.toMap(Staff::getStaffCode, Staff::getStaffName,
                        (left, right) -> left));
    }

    /** 按客户业务编码批量加载名称，企业名称优先、联系人姓名兜底。 */
    public Map<String, String> clientNames(Collection<String> clientCodes) {
        Set<String> codes = normalizeCodes(clientCodes);
        if (codes.isEmpty()) return Collections.emptyMap();
        return clientProfileMapper.selectList(new LambdaQueryWrapper<ClientProfile>()
                        .in(ClientProfile::getClientCode, codes)).stream()
                .filter(client -> StringUtils.hasText(client.getClientCode()))
                .collect(Collectors.toMap(ClientProfile::getClientCode,
                        client -> StringUtils.hasText(client.getEnterpriseName())
                                ? client.getEnterpriseName() : client.getContactName(),
                        (left, right) -> left));
    }

    /** 按产品业务编码批量加载产品名称。 */
    public Map<String, String> productNames(Collection<String> productCodes) {
        Set<String> codes = normalizeCodes(productCodes);
        if (codes.isEmpty()) return Collections.emptyMap();
        return bankProductMapper.selectList(new LambdaQueryWrapper<BankProduct>()
                        .in(BankProduct::getProductCode, codes)).stream()
                .filter(product -> StringUtils.hasText(product.getProductCode()))
                .collect(Collectors.toMap(BankProduct::getProductCode, BankProduct::getProductName,
                        (left, right) -> left));
    }

    /**
     * 批量装配邀请记录中的引荐人姓名。
     *
     * <p>客户引荐按 {@code referrerClientCode} 查询；历史员工/BOSS 引荐仍按内部员工 ID
     * 批量兼容读取，但只返回姓名，不向调用端暴露物理 ID。</p>
     */
    public Map<String, String> referrerNames(Collection<Invitation> invitations) {
        if (invitations == null || invitations.isEmpty()) return Collections.emptyMap();
        Set<String> clientCodes = invitations.stream().map(Invitation::getReferrerClientCode)
                .filter(StringUtils::hasText).collect(Collectors.toSet());
        Set<Long> staffIds = invitations.stream().filter(this::isStaffReferrer)
                .map(Invitation::getReferrerId).filter(id -> id != null).collect(Collectors.toSet());
        Map<String, String> clientNames = clientNames(clientCodes);
        Map<Long, String> staffNames = staffIds.isEmpty() ? Collections.emptyMap()
                : staffMapper.selectBatchIds(staffIds).stream()
                .filter(staff -> staff.getId() != null)
                .collect(Collectors.toMap(Staff::getId, Staff::getStaffName,
                        (left, right) -> left));
        return invitations.stream()
                .filter(invitation -> StringUtils.hasText(invitation.getInvitationCode()))
                .collect(Collectors.toMap(Invitation::getInvitationCode,
                        invitation -> resolveReferrerName(invitation, clientNames, staffNames),
                        (left, right) -> left));
    }

    /** 单条解析引荐人姓名，内部仍复用批量入口。 */
    public String referrerName(Invitation invitation) {
        if (invitation == null || !StringUtils.hasText(invitation.getInvitationCode())) return null;
        return referrerNames(Collections.singletonList(invitation)).get(invitation.getInvitationCode());
    }

    private String resolveReferrerName(Invitation invitation, Map<String, String> clientNames,
                                       Map<Long, String> staffNames) {
        if (StringUtils.hasText(invitation.getReferrerClientCode())) {
            String name = clientNames.get(invitation.getReferrerClientCode());
            if (StringUtils.hasText(name)) return name;
        }
        return isStaffReferrer(invitation) ? staffNames.get(invitation.getReferrerId()) : null;
    }

    private boolean isStaffReferrer(Invitation invitation) {
        return invitation != null && ("ADVISER".equals(invitation.getReferrerType())
                || "BOSS".equals(invitation.getReferrerType()));
    }

    private Set<String> normalizeCodes(Collection<String> codes) {
        if (codes == null || codes.isEmpty()) return Collections.emptySet();
        return codes.stream().filter(StringUtils::hasText).map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
