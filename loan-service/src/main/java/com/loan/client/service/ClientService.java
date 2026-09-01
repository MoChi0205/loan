package com.loan.client.service;
import com.loan.common.util.PageOrder;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.loan.api.dto.PageResult;
import com.loan.client.entity.ClientProfile;
import com.loan.client.mapper.ClientProfileMapper;
import com.loan.client.model.ClientUpdateRequest;
import com.loan.common.ResultCode;
import com.loan.exception.BusinessException;
import com.loan.infrastructure.security.AesUtils;
import com.loan.infrastructure.security.HashUtils;
import com.loan.invitation.entity.Invitation;
import com.loan.invitation.mapper.InvitationMapper;
import com.loan.personal.entity.PersonalProfile;
import com.loan.personal.mapper.PersonalProfileMapper;
import com.loan.personal.service.PersonalProfileService;
import com.loan.staff.entity.Staff;
import com.loan.staff.mapper.StaffMapper;
import com.loan.utils.DesensitizeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 客户档案服务（薄切片：建单客户下拉 / 关键字搜索 / P0-6 档案详情与编辑）。
 *
 * <p>手机号出参统一脱敏；明文需经敏感查看授权流程获取。
 * <p>P0-6 档案详情按 clientCode 聚合企业 + 个人 + 认证 + VIP + 邀请链，编辑合并更新并走
 * {@code CustomMetaObjectHandler} 审计留痕（updated_by 存操作人姓名）。
 *
 * @author loan-platform
 */
@Service
@RequiredArgsConstructor
public class ClientService {

    /** 允许排序字段（白名单，防注入） */
    private static final java.util.Map<String, com.baomidou.mybatisplus.core.toolkit.support.SFunction<ClientProfile, ?>> ORDER_FIELDS =
            new java.util.HashMap<>();
    static {
        ORDER_FIELDS.put("createdAt", ClientProfile::getCreatedAt);
    }

    private final ClientProfileMapper clientProfileMapper;
    private final PersonalProfileMapper personalProfileMapper;
    private final PersonalProfileService personalProfileService;
    private final InvitationMapper invitationMapper;
    private final StaffMapper staffMapper;

    /**
     * 客户轻量分页（建单下拉 / 客户选择）。
     *
     * @param keyword 关键字：客户编码 / 联系人 / 企业名称 / 手机号（精确）
     * @param page    页码
     * @param size    每页大小
     * @return 客户轻量列表
     */
    public PageResult<Map<String, Object>> pageLite(String keyword, int page, int size, String orderBy, String orderDir) {
        LambdaQueryWrapper<ClientProfile> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            // 以关键字查询客户：姓名 / 企业名 / 手机号（SHA-256 哈希精确）；不再按客户内部编码匹配。
            wrapper.and(w -> w.like(ClientProfile::getContactName, kw)
                    .or().like(ClientProfile::getEnterpriseName, kw)
                    .or().eq(ClientProfile::getPhoneHash, sha256(kw)));
        }
        PageOrder.apply(wrapper, orderBy, orderDir, ORDER_FIELDS, ClientProfile::getCreatedAt);
        Page<ClientProfile> result = clientProfileMapper.selectPage(new Page<>(page, size), wrapper);

        List<Map<String, Object>> records = result.getRecords().stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("clientCode", c.getClientCode());
            m.put("customerGroup", c.getCustomerGroup());
            m.put("contactName", c.getContactName());
            m.put("enterpriseName", c.getEnterpriseName());
            m.put("phone", DesensitizeUtils.phone(decryptPlain(c.getPhone())));
            m.put("ownerStaffCode", c.getOwnerStaffCode());
            m.put("status", c.getStatus());
            return m;
        }).collect(Collectors.toList());
        return PageResult.build(page, size, result.getTotal(), records);
    }

    /**
     * 档案合并视图（P0-6 管理端/顾问）。
     *
     * <p>按 clientCode 聚合：基础信息（手机号脱敏，未绑定显示「未绑定」）、企业信息（信用代码脱敏）、
     * 个人档案（脱敏）、认证状态、邀请链、VIP 与审计字段。
     *
     * @param clientCode 客户编码
     * @return 档案合并视图
     */
    public Map<String, Object> getClientDetail(String clientCode) {
        ClientProfile client = requireClient(clientCode);
        Map<String, Object> detail = new LinkedHashMap<>();
        // —— 基础信息 ——
        detail.put("clientCode", client.getClientCode());
        detail.put("name", client.getContactName());
        String phonePlain = decryptPlain(client.getPhone());
        detail.put("phone", StringUtils.hasText(phonePlain) ? DesensitizeUtils.phone(phonePlain) : "未绑定");
        detail.put("source", client.getSource());
        detail.put("ownerStaffCode", client.getOwnerStaffCode());
        detail.put("ownerStaffName", resolveStaffName(client.getOwnerStaffCode()));
        detail.put("customerGroup", client.getCustomerGroup());
        detail.put("status", client.getStatus());
        detail.put("invitedFlag", client.getInvitedFlag());
        detail.put("wxBound", StringUtils.hasText(client.getWxOpenidHash()) ? 1 : 0);
        // —— 认证状态（企业认证 / 个人认证 / 未认证） ——
        detail.put("authStatus", resolveAuthStatus(client));
        // —— 企业信息 ——
        Map<String, Object> enterprise = new LinkedHashMap<>();
        enterprise.put("enterpriseName", client.getEnterpriseName());
        enterprise.put("creditCode", DesensitizeUtils.creditCode(decryptPlain(client.getCreditCode())));
        detail.put("enterprise", enterprise);
        // —— 个人档案（复用 PersonalProfileService 脱敏读取） ——
        detail.put("personal", personalProfileService.getByClientCode(clientCode));
        // —— 邀请链（按 t_invitation 关联读：该客户使用的邀请码对应的引荐人） ——
        detail.put("referrer", resolveReferrer(clientCode));
        // —— VIP（已有字段直接带出） ——
        detail.put("vipLevel", client.getVipLevel());
        detail.put("vipExpireAt", client.getVipExpireAt());
        // —— 审计字段 ——
        detail.put("createdBy", client.getCreatedBy());
        detail.put("updatedBy", client.getUpdatedBy());
        detail.put("createdAt", client.getCreatedAt());
        detail.put("updatedAt", client.getUpdatedAt());
        return detail;
    }

    /**
     * 档案编辑（P0-6）：基础信息 + 个人档案字段合并更新。
     *
     * <p>敏感字段（phone / idCardNo / creditCode）写入时 AES 加密 + SHA-256 哈希更新；
     * 更新走 {@code CustomMetaObjectHandler} 审计留痕（updated_by 取操作人姓名）。
     *
     * @param clientCode 客户编码
     * @param req        编辑请求
     * @return 更新后的档案合并视图
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> updateClientDetail(String clientCode, ClientUpdateRequest req) {
        if (req == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "编辑内容不能为空");
        }
        ClientProfile client = requireClient(clientCode);
        boolean changed = false;
        // —— 基础信息合并更新 ——
        if (StringUtils.hasText(req.getContactName())
                && !req.getContactName().equals(client.getContactName())) {
            client.setContactName(req.getContactName());
            changed = true;
        }
        if (StringUtils.hasText(req.getPhone())) {
            String phone = req.getPhone().trim();
            client.setPhone(phone);                      // AesTypeHandler 落库加密
            client.setPhoneHash(HashUtils.sha256Hex(phone));
            changed = true;
        }
        if (req.getRemark() != null && !req.getRemark().equals(client.getRemark())) {
            client.setRemark(req.getRemark());
            changed = true;
        }
        if (StringUtils.hasText(req.getEnterpriseName())
                && !req.getEnterpriseName().equals(client.getEnterpriseName())) {
            client.setEnterpriseName(req.getEnterpriseName());
            changed = true;
        }
        if (StringUtils.hasText(req.getCreditCode())) {
            String creditCode = req.getCreditCode().trim();
            String creditHash = HashUtils.sha256Hex(creditCode);
            Long dup = clientProfileMapper.selectCount(new LambdaQueryWrapper<ClientProfile>()
                    .eq(ClientProfile::getCreditCodeHash, creditHash)
                    .ne(ClientProfile::getClientCode, clientCode));
            if (dup != null && dup > 0) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "该统一社会信用代码已认证");
            }
            client.setCreditCode(creditCode);            // AesTypeHandler 落库加密
            client.setCreditCodeHash(creditHash);
            changed = true;
        }
        if (changed) {
            clientProfileMapper.updateById(client);
        }
        // —— 个人档案合并更新（任一字段有值则 upsert） ——
        upsertPersonal(clientCode, req);
        return getClientDetail(clientCode);
    }

    /**
     * 个人档案 upsert（合并更新，1:1）。
     *
     * @param clientCode 客户编码
     * @param req        编辑请求
     */
    private void upsertPersonal(String clientCode, ClientUpdateRequest req) {
        boolean hasContent = StringUtils.hasText(req.getRealName())
                || StringUtils.hasText(req.getIdCardNo())
                || StringUtils.hasText(req.getCity())
                || req.getAge() != null
                || req.getHouseFlag() != null
                || req.getCarFlag() != null
                || req.getSocialSecurityFlag() != null
                || req.getFundFlag() != null;
        if (!hasContent) {
            return;
        }
        PersonalProfile personal = personalProfileMapper.selectOne(new LambdaQueryWrapper<PersonalProfile>()
                .eq(PersonalProfile::getClientProfileCode, clientCode));
        boolean isNew = personal == null;
        if (isNew) {
            personal = new PersonalProfile();
            personal.setClientProfileCode(clientCode);
        }
        if (StringUtils.hasText(req.getRealName())) {
            personal.setRealName(req.getRealName());
        }
        if (StringUtils.hasText(req.getIdCardNo())) {
            String idCardNo = req.getIdCardNo().trim();
            String idCardHash = HashUtils.sha256Hex(idCardNo);
            Long dup = personalProfileMapper.selectCount(new LambdaQueryWrapper<PersonalProfile>()
                    .eq(PersonalProfile::getIdCardHash, idCardHash)
                    .ne(PersonalProfile::getClientProfileCode, clientCode));
            if (dup != null && dup > 0) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "该身份证号已认证");
            }
            personal.setIdCardNo(idCardNo);              // AesTypeHandler 落库加密
            personal.setIdCardHash(idCardHash);
        }
        if (StringUtils.hasText(req.getCity())) {
            personal.setCity(req.getCity());
        }
        if (req.getAge() != null) {
            personal.setAge(req.getAge());
        }
        if (req.getHouseFlag() != null) {
            personal.setHouseFlag(req.getHouseFlag());
        }
        if (req.getCarFlag() != null) {
            personal.setCarFlag(req.getCarFlag());
        }
        if (req.getSocialSecurityFlag() != null) {
            personal.setSocialSecurityFlag(req.getSocialSecurityFlag());
        }
        if (req.getFundFlag() != null) {
            personal.setFundFlag(req.getFundFlag());
        }
        if (isNew) {
            personalProfileMapper.insert(personal);
        } else {
            personalProfileMapper.updateById(personal);
        }
    }

    /**
     * 认证状态：企业认证优先（客户档案已锁客群/信用代码），其次个人认证，未认证返回 UNAUTHED。
     *
     * @param client 客户档案
     * @return ENTERPRISE_AUTHED / PERSONAL_AUTHED / UNAUTHED
     */
    private String resolveAuthStatus(ClientProfile client) {
        if (StringUtils.hasText(client.getCreditCodeHash())
                || StringUtils.hasText(client.getEnterpriseName())) {
            return "ENTERPRISE_AUTHED";
        }
        if (personalProfileService.hasAuthenticated(client.getClientCode())) {
            return "PERSONAL_AUTHED";
        }
        return "UNAUTHED";
    }

    /**
     * 邀请链：按 t_invitation.used_by_client_code 关联读引荐人信息。
     *
     * @param clientCode 客户编码
     * @return 引荐人信息，无邀请关系返回 null
     */
    private Map<String, Object> resolveReferrer(String clientCode) {
        Invitation inv = invitationMapper.selectOne(new LambdaQueryWrapper<Invitation>()
                .eq(Invitation::getUsedByClientCode, clientCode)
                .orderByDesc(Invitation::getUsedAt)
                .last("limit 1"));
        if (inv == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("invitationCode", inv.getInvitationCode());
        m.put("referrerType", inv.getReferrerType());
        m.put("referrerClientCode", inv.getReferrerClientCode());
        m.put("referrerName", resolveReferrerName(inv));
        m.put("usedAt", inv.getUsedAt());
        return m;
    }

    /**
     * 引荐人展示名：客户引荐取客户联系人，员工引荐（ADVISER/BOSS）取顾问姓名。
     *
     * @param inv 邀请凭证
     * @return 引荐人展示名，无则 null
     */
    private String resolveReferrerName(Invitation inv) {
        if (StringUtils.hasText(inv.getReferrerClientCode())) {
            ClientProfile ref = clientProfileMapper.selectOne(new LambdaQueryWrapper<ClientProfile>()
                    .eq(ClientProfile::getClientCode, inv.getReferrerClientCode())
                    .last("limit 1"));
            if (ref != null) {
                return ref.getContactName();
            }
        }
        if (("ADVISER".equals(inv.getReferrerType()) || "BOSS".equals(inv.getReferrerType()))
                && inv.getReferrerId() != null) {
            Staff staff = staffMapper.selectById(inv.getReferrerId());
            if (staff != null) {
                return staff.getStaffName();
            }
        }
        return null;
    }

    /**
     * 按工号查顾问姓名。
     *
     * @param ownerStaffCode 归属顾问工号
     * @return 顾问姓名，无则 null
     */
    private String resolveStaffName(String ownerStaffCode) {
        if (!StringUtils.hasText(ownerStaffCode)) {
            return null;
        }
        Staff staff = staffMapper.selectOne(new LambdaQueryWrapper<Staff>()
                .eq(Staff::getStaffCode, ownerStaffCode)
                .last("limit 1"));
        return staff == null ? null : staff.getStaffName();
    }

    /**
     * 查客户档案，不存在抛业务异常。
     *
     * @param clientCode 客户编码
     * @return 客户档案
     */
    private ClientProfile requireClient(String clientCode) {
        if (!StringUtils.hasText(clientCode)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "客户编码必填");
        }
        ClientProfile client = clientProfileMapper.selectOne(new LambdaQueryWrapper<ClientProfile>()
                .eq(ClientProfile::getClientCode, clientCode));
        if (client == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "客户档案不存在");
        }
        return client;
    }

    /**
     * 密文转明文：AesTypeHandler 未介入的读取直接解密；已解密过的原样返回。
     *
     * @param stored 存储值（可能是密文或明文）
     * @return 明文，空返回 null
     */
    private String decryptPlain(String stored) {
        if (!StringUtils.hasText(stored)) {
            return null;
        }
        String plain = AesUtils.decrypt(stored);
        return plain != null ? plain : stored;
    }

    /**
     * 手机号 SHA-256 哈希。
     */
    private String sha256(String raw) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 失败", e);
        }
    }
}
