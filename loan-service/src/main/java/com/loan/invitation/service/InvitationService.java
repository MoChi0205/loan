package com.loan.invitation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.loan.api.dto.PageResult;
import com.loan.common.ResultCode;
import com.loan.exception.BusinessException;
import com.loan.invitation.entity.Invitation;
import com.loan.invitation.mapper.InvitationMapper;
import com.loan.lead.entity.Lead;
import com.loan.lead.mapper.LeadMapper;
import com.loan.lead.service.LeadService;
import com.loan.client.entity.ClientProfile;
import com.loan.client.mapper.ClientProfileMapper;
import com.loan.infrastructure.security.AesUtils;
import com.loan.staff.entity.Staff;
import com.loan.staff.mapper.StaffMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 邀请凭证服务（阶段三小程序：邀请绑定 / 我的邀请码 / 邀请记录）。
 *
 * <p>客户推荐（referrer_type=CUSTOMER）的邀请码进入奖励结算链路；
 * 员工/渠道引荐只记归属不产生奖励。
 *
 * <p>P0-2 增强：绑定成功后回写 {@code t_client_profile.owner_staff_code}
 * （按引荐人类型取顾问工号，仅员工引荐 ADVISER/BOSS 生效），并生成归属线索
 * （source=INVITE，去重：同 phone_hash + 客户编码）。
 *
 * @author loan-platform
 */
@Service
@RequiredArgsConstructor
public class InvitationService {

    private final InvitationMapper invitationMapper;
    private final ClientProfileMapper clientProfileMapper;
    private final StaffMapper staffMapper;
    private final LeadMapper leadMapper;
    private final LeadService leadService;

    /**
     * 客户绑定邀请码（注册/登录时使用）。
     *
     * <p>校验：邀请码存在 / ACTIVE / 未使用 / 未过期；绑定后置 used_flag=1。
     * 绑定成功后：员工引荐回写客户档案归属顾问 + 生成归属线索（source=INVITE，去重）。
     *
     * @param inviteCode 邀请码
     * @param clientCode 使用者客户编码
     * @param clientId   使用者客户档案内部 ID
     * @return 绑定结果（referrerType / referrerClientCode / referrerName，供小程序展示）
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> bind(String inviteCode, String clientCode, Long clientId) {
        if (!StringUtils.hasText(inviteCode)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "邀请码必填");
        }
        Invitation inv = invitationMapper.selectOne(new LambdaQueryWrapper<Invitation>()
                .eq(Invitation::getInvitationCode, inviteCode.trim()));
        if (inv == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "邀请码不存在");
        }
        if (!"ACTIVE".equals(inv.getStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "邀请码已作废");
        }
        if (inv.getUsedFlag() != null && inv.getUsedFlag() == 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "邀请码已被使用");
        }
        if (inv.getExpireAt() != null && inv.getExpireAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "邀请码已过期");
        }
        if (inv.getUsedByClientCode() != null && inv.getUsedByClientCode().equals(clientCode)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不能使用自己的邀请码");
        }
        inv.setUsedFlag(1);
        inv.setUsedByClientId(clientId);
        inv.setUsedByClientCode(clientCode);
        inv.setUsedAt(LocalDateTime.now());
        invitationMapper.updateById(inv);

        // P0-2：员工引荐 → 回写归属顾问 + 生成归属线索（去重）
        String ownerStaffCode = resolveStaffCode(inv);
        String referrerName = resolveReferrerName(inv, ownerStaffCode);
        ClientProfile client = clientProfileMapper.selectOne(new LambdaQueryWrapper<ClientProfile>()
                .eq(ClientProfile::getClientCode, clientCode));
        if (ownerStaffCode != null && client != null) {
            if (!StringUtils.hasText(client.getOwnerStaffCode())) {
                client.setOwnerStaffCode(ownerStaffCode);
                client.setUpdatedBy(referrerName == null ? "mini" : referrerName);
                clientProfileMapper.updateById(client);
            }
            createInviteLead(inv, client, ownerStaffCode, referrerName);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("referrerType", inv.getReferrerType());
        result.put("referrerId", inv.getReferrerId());
        result.put("referrerClientCode", inv.getReferrerClientCode());
        result.put("referrerName", referrerName);
        result.put("inviteType", inv.getInviteType());
        return result;
    }

    /**
     * 按引荐人类型解析顾问工号（仅员工引荐 ADVISER/BOSS 生效，其余返回 null）。
     *
     * @param inv 邀请凭证
     * @return 顾问工号或 null
     */
    private String resolveStaffCode(Invitation inv) {
        if (inv.getReferrerId() == null) {
            return null;
        }
        if ("ADVISER".equals(inv.getReferrerType()) || "BOSS".equals(inv.getReferrerType())) {
            Staff staff = staffMapper.selectById(inv.getReferrerId());
            return staff == null ? null : staff.getStaffCode();
        }
        return null;
    }

    /**
     * 引荐人展示名（员工引荐返回顾问姓名，其余取引荐人客户编码）。
     *
     * @param inv            邀请凭证
     * @param ownerStaffCode 已解析的顾问工号
     * @return 展示名
     */
    private String resolveReferrerName(Invitation inv, String ownerStaffCode) {
        if (ownerStaffCode != null) {
            Staff staff = staffMapper.selectOne(new LambdaQueryWrapper<Staff>()
                    .eq(Staff::getStaffCode, ownerStaffCode).last("limit 1"));
            return staff == null ? ownerStaffCode : staff.getStaffName();
        }
        return inv.getReferrerClientCode();
    }

    /**
     * 生成归属线索（source=INVITE，归属顾问），去重：同客户编码 + source=INVITE 只建一次。
     *
     * @param inv            邀请凭证
     * @param client         客户档案
     * @param ownerStaffCode 归属顾问工号
     * @param referrerName   引荐人展示名
     */
    private void createInviteLead(Invitation inv, ClientProfile client, String ownerStaffCode, String referrerName) {
        if (client.getPhoneHash() == null) {
            return; // 尚未绑定手机号的微信档案，先不建线索（防空手机号）
        }
        Long dup = leadMapper.selectCount(new LambdaQueryWrapper<Lead>()
                .eq(Lead::getSource, "INVITE")
                .eq(Lead::getPhoneHash, client.getPhoneHash())
                .eq(Lead::getClientProfileCode, client.getClientCode()));
        if (dup != null && dup > 0) {
            return;
        }
        Lead lead = new Lead();
        lead.setContactName(client.getContactName());
        lead.setPhone(AesUtils.decrypt(client.getPhone()));
        lead.setLeadType(StringUtils.hasText(client.getCustomerGroup()) ? client.getCustomerGroup() : "ENTERPRISE");
        lead.setSource("INVITE");
        lead.setFollowStatus("NEW");
        lead.setClientProfileCode(client.getClientCode());
        lead.setExtJson("{\"inviteCode\":\"" + inv.getInvitationCode()
                + "\",\"referrerType\":\"" + (inv.getReferrerType() == null ? "" : inv.getReferrerType()) + "\"}");
        leadService.create(lead, ownerStaffCode, referrerName);
    }

    /**
     * 为客户生成专属邀请码（referrer_type=CUSTOMER，7 天有效，可用于推荐有礼）。
     *
     * @param clientCode 客户编码
     * @param clientId   客户档案内部 ID
     * @param operator   操作人
     * @return 邀请码
     */
    @Transactional(rollbackFor = Exception.class)
    public String generateForClient(String clientCode, Long clientId, String operator) {
        // 幂等：已存在未使用的本人邀请码直接复用
        Invitation exist = invitationMapper.selectOne(new LambdaQueryWrapper<Invitation>()
                .eq(Invitation::getReferrerType, "CUSTOMER")
                .eq(Invitation::getReferrerId, clientId)
                .eq(Invitation::getUsedFlag, 0)
                .eq(Invitation::getStatus, "ACTIVE")
                .last("limit 1"));
        if (exist != null && exist.getExpireAt() != null
                && exist.getExpireAt().isAfter(LocalDateTime.now())) {
            return exist.getInvitationCode();
        }
        Invitation inv = new Invitation();
        inv.setInvitationCode(genInviteCode(clientCode));
        inv.setInviteType("PERSONAL");
        inv.setReferrerType("CUSTOMER");
        inv.setReferrerId(clientId);
        inv.setReferrerClientCode(clientCode);
        inv.setExpireAt(LocalDateTime.now().plusDays(7));
        inv.setUsedFlag(0);
        inv.setStatus("ACTIVE");
        inv.setCreatedBy(operator == null ? "client" : operator);
        inv.setCreatedAt(LocalDateTime.now());
        invitationMapper.insert(inv);
        return inv.getInvitationCode();
    }

    /**
     * 我的邀请记录（通过我的邀请码注册的客户）。
     *
     * @param clientCode 客户编码
     * @param page       页码
     * @param size       每页大小
     * @return 记录分页（含被邀请人客户名/时间）
     */
    public PageResult<Map<String, Object>> myRecords(String clientCode, int page, int size) {
        LambdaQueryWrapper<Invitation> wrapper = new LambdaQueryWrapper<Invitation>()
                .eq(Invitation::getReferrerType, "CUSTOMER")
                .eq(Invitation::getReferrerClientCode, clientCode)
                .orderByDesc(Invitation::getCreatedAt);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Invitation> result =
                invitationMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size), wrapper);
        List<Map<String, Object>> records = result.getRecords().stream().map(inv -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("invitationCode", inv.getInvitationCode());
            m.put("usedFlag", inv.getUsedFlag());
            m.put("usedByClientCode", inv.getUsedByClientCode());
            m.put("usedAt", inv.getUsedAt());
            m.put("expireAt", inv.getExpireAt());
            return m;
        }).collect(Collectors.toList());
        return PageResult.build(page, size, result.getTotal(), records);
    }

    /**
     * 我的邀请码（推荐有礼首页展示，幂等生成）。
     *
     * @param clientCode 客户编码
     * @param clientId   客户档案内部 ID
     * @return 邀请码字符串
     */
    public String myInviteCode(String clientCode, Long clientId) {
        return generateForClient(clientCode, clientId, "client");
    }

    /**
     * 生成邀请码：客户编码后 6 位 + 4 位随机（可读性好）。
     *
     * @param clientCode 客户编码
     * @return 邀请码
     */
    private String genInviteCode(String clientCode) {
        String suffix = clientCode.length() > 6 ? clientCode.substring(clientCode.length() - 6) : clientCode;
        return "INV" + suffix + String.format("%04d", (int) (Math.random() * 10000));
    }
}
