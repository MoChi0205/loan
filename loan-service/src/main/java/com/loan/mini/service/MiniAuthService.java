package com.loan.mini.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loan.auth.dto.LoginResponse;
import com.loan.auth.util.SessionKeyUtils;
import com.loan.common.ResultCode;
import com.loan.common.util.BizIdGenerator;
import com.loan.context.LoanUser;
import com.loan.exception.BusinessException;
import com.loan.client.entity.ClientProfile;
import com.loan.client.mapper.ClientProfileMapper;
import com.loan.config.entity.ConfigItem;
import com.loan.config.mapper.ConfigItemMapper;
import com.loan.infrastructure.security.AesUtils;
import com.loan.infrastructure.security.HashUtils;
import com.loan.infrastructure.security.JwtService;
import com.loan.infrastructure.wechat.WxCode2SessionService;
import com.loan.staff.entity.Staff;
import com.loan.staff.mapper.StaffMapper;
import com.loan.utils.DesensitizeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 小程序端服务：微信登录 / 企业认证 / 个人认证 / 客户资料 / 企微活码。
 *
 * @author loan-platform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MiniAuthService {

    /** Redis 会话 TTL（2 小时，与 AuthService 一致） */
    private static final Duration SESSION_TTL = Duration.ofHours(2);

    private final ClientProfileMapper clientProfileMapper;
    private final ConfigItemMapper configItemMapper;
    private final WxCode2SessionService wxCode2SessionService;
    private final JwtService jwtService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final com.loan.invitation.service.InvitationService invitationService;
    private final com.loan.personal.service.PersonalProfileService personalProfileService;
    private final StaffMapper staffMapper;

    /**
     * 微信登录（Q3 方案 A 主通道）：wx.login code → openid → 按 hash 找/建档案 → 签发 JWT。
     *
     * <p>首次登录自动创建客户档案（source=MINI、customerGroup=PERSONAL 占位，认证后锁定）；
     * 携带邀请码时自动绑定推荐关系（referrer_type=CUSTOMER 进入奖励链路）。
     *
     * @param code       wx.login 临时凭证
     * @param nickname   昵称（可选，用于占位联系人）
     * @param avatar     头像（可选）
     * @param inviteCode 邀请码（可选）
     * @return 登录响应（token + 客户 LoanUser）
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse wxLogin(String code, String nickname, String avatar, String inviteCode) {
        String openid = wxCode2SessionService.code2Session(code);
        String openidHash = HashUtils.sha256Hex(openid);
        ClientProfile client = clientProfileMapper.selectByWxOpenidHash(openidHash);
        if (client == null) {
            client = new ClientProfile();
            client.setClientCode(BizIdGenerator.generate("client"));
            client.setCustomerGroup("PERSONAL");
            client.setContactName(StringUtils.hasText(nickname) ? nickname : "微信客户");
            client.setWxOpenid(openid);
            client.setWxOpenidHash(openidHash);
            client.setSource("MINI");
            client.setStatus("ACTIVE");
            client.setInvitedFlag(0);
            client.setWecomAdded(0);
            client.setCreatedBy("mini");
            client.setCreatedAt(LocalDateTime.now());
            clientProfileMapper.insert(client);
        }
        // 绑定邀请码只记录分享引荐关系，服务顾问由独立分配审批流程产生。
        String referrerNo = null;
        if (StringUtils.hasText(inviteCode)) {
            Map<String, Object> bind = invitationService.bind(inviteCode, client.getClientCode(), client.getId());
            if ("CUSTOMER".equals(bind.get("referrerType"))) {
                referrerNo = (String) bind.get("referrerClientCode");
                if (client.getInvitedFlag() == null || client.getInvitedFlag() == 0) {
                    client.setInvitedFlag(1);
                    clientProfileMapper.updateById(client);
                }
            }
        }
        // 签发 JWT + Redis 会话（LoanUser.userNo = client_code，@CurrentUser 覆盖全部 mini 接口）
        LoanUser user = new LoanUser();
        user.setUserId(client.getId());
        user.setUserNo(client.getClientCode());
        user.setName(client.getContactName());
        user.setUserType(LoanUser.TYPE_CUSTOMER);
        user.setRegion(client.getExtJson());
        user.setAvatar(avatar);
        user.setReferrerNo(referrerNo);
        user.setInvitedFlag(client.getInvitedFlag() != null && client.getInvitedFlag() == 1);

        String token = jwtService.generateToken(user.getUserId(), user.getUserType(), user.getUserNo(), null);
        saveSession(user.getUserId(), user);

        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setExpireMillis(86400000L);
        response.setUser(user);
        return response;
    }

    /**
     * 企业认证：更新客户档案为 ENTERPRISE（营业执照信息）。
     *
     * @param clientCode    客户编码
     * @param creditCode    统一社会信用代码
     * @param enterpriseName 企业名称
     * @param contactName   联系人姓名
     * @return 更新后的档案信息
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> enterpriseAuth(String clientCode, String creditCode,
                                              String enterpriseName, String contactName) {
        if (!StringUtils.hasText(creditCode) || !StringUtils.hasText(enterpriseName)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "统一社会信用代码与企业名称必填");
        }
        ClientProfile client = clientProfileMapper.selectOne(new LambdaQueryWrapper<ClientProfile>()
                .eq(ClientProfile::getClientCode, clientCode));
        if (client == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "客户档案不存在");
        }
        // 信用代码唯一性校验（hash 查重，排除自己）
        String hash = sha256(creditCode);
        Long dup = clientProfileMapper.selectCount(new LambdaQueryWrapper<ClientProfile>()
                .eq(ClientProfile::getCreditCodeHash, hash)
                .ne(ClientProfile::getClientCode, clientCode));
        if (dup != null && dup > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "该统一社会信用代码已认证");
        }
        client.setCustomerGroup("ENTERPRISE");
        client.setCreditCode(creditCode);
        client.setCreditCodeHash(hash);
        client.setEnterpriseName(enterpriseName);
        if (StringUtils.hasText(contactName)) {
            client.setContactName(contactName);
        }
        client.setUpdatedBy("mini");
        clientProfileMapper.updateById(client);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("clientCode", client.getClientCode());
        m.put("customerGroup", client.getCustomerGroup());
        m.put("enterpriseName", client.getEnterpriseName());
        m.put("contactName", client.getContactName());
        return m;
    }

    /**
     * 个人认证入口（委托 {@link PersonalProfileService}，Mock 三要素 + 落库留痕）。
     *
     * @param clientCode 客户编码
     * @param req        认证请求
     * @return 认证结果（脱敏）
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> personalAuth(String clientCode,
                                            com.loan.personal.dto.PersonalAuthRequest req) {
        return personalProfileService.personalAuth(clientCode, req);
    }

    /**
     * 客户资料摘要（小程序-我的，敏感字段强制脱敏）。
     *
     * @param clientCode 客户编码
     * @return 资料摘要
     */
    public Map<String, Object> myProfile(String clientCode) {
        ClientProfile client = clientProfileMapper.selectOne(new LambdaQueryWrapper<ClientProfile>()
                .eq(ClientProfile::getClientCode, clientCode));
        Map<String, Object> m = new LinkedHashMap<>();
        if (client == null) {
            return m;
        }
        m.put("clientCode", client.getClientCode());
        m.put("customerGroup", client.getCustomerGroup());
        m.put("enterpriseName", client.getEnterpriseName());
        m.put("contactName", client.getContactName());
        m.put("phone", DesensitizeUtils.phone(AesUtils.decrypt(client.getPhone())));
        m.put("invitedFlag", client.getInvitedFlag());
        m.put("vipLevel", client.getVipLevel());
        m.put("vipExpireAt", client.getVipExpireAt());
        m.put("creditCode", DesensitizeUtils.creditCode(client.getCreditCode()));
        m.put("ownerStaffCode", client.getOwnerStaffCode());
        m.put("ownerStaffName", ownerStaffName(client.getOwnerStaffCode()));
        m.put("referrerName", invitationService.boundAttribution(clientCode).get("referrerName"));
        m.put("wxBound", client.getWxOpenidHash() != null ? 1 : 0);
        m.put("authenticated", isAuthenticated(client));
        return m;
    }

    private String ownerStaffName(String staffCode) {
        if (!StringUtils.hasText(staffCode)) {
            return null;
        }
        Staff staff = staffMapper.selectOne(new LambdaQueryWrapper<Staff>()
                .eq(Staff::getStaffCode, staffCode).last("limit 1"));
        return staff == null ? null : staff.getStaffName();
    }

    /**
     * 客户是否已通过身份认证（企业认证明文信用代码 / 个人认证落库）。
     *
     * @param client 客户档案
     * @return true 已认证
     */
    private boolean isAuthenticated(ClientProfile client) {
        if (client == null) {
            return false;
        }
        if (StringUtils.hasText(client.getCreditCodeHash()) || StringUtils.hasText(client.getEnterpriseName())) {
            return true;
        }
        return personalProfileService.hasAuthenticated(client.getClientCode());
    }

    /**
     * 企微客服活码（t_config WECOM 组，可后台配置）。
     *
     * @return 活码 URL；未配置返回 null
     */
    public String wecomQrCode() {
        ConfigItem item = configItemMapper.selectOne(new LambdaQueryWrapper<ConfigItem>()
                .eq(ConfigItem::getConfigGroup, "WECOM")
                .eq(ConfigItem::getConfigKey, "qrcode")
                .eq(ConfigItem::getEnabled, 1)
                .last("limit 1"));
        return item == null ? null : item.getConfigValue();
    }

    /**
     * SHA-256 摘要（信用代码查重）。
     *
     * @param raw 原始串
     * @return 摘要 hex
     */
    private String sha256(String raw) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 失败", e);
        }
    }

    /**
     * 保存会话到 Redis（2 小时过期，与 AuthService 同一 key 空间）。
     *
     * @param userId 用户 ID
     * @param user   用户对象
     */
    private void saveSession(Long userId, LoanUser user) {
        try {
            String json = objectMapper.writeValueAsString(user);
            stringRedisTemplate.opsForValue().set(
                    SessionKeyUtils.key(user.getUserType(), userId), json, SESSION_TTL);
            stringRedisTemplate.delete(SessionKeyUtils.legacyKey(userId));
        } catch (Exception e) {
            log.error("保存会话失败", e);
        }
    }
}
