package com.loan.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loan.auth.dto.LoginResponse;
import com.loan.channel.entity.ChannelUser;
import com.loan.channel.mapper.ChannelUserMapper;
import com.loan.common.ResultCode;
import com.loan.context.LoanUser;
import com.loan.client.entity.ClientLifecycleEvent;
import com.loan.client.mapper.ClientLifecycleEventMapper;
import com.loan.exception.BusinessException;
import com.loan.infrastructure.security.AesUtils;
import com.loan.infrastructure.security.JwtService;
import com.loan.infrastructure.security.LoginRsaCrypto;
import com.loan.auth.util.SessionKeyUtils;
import com.loan.product.entity.BankChannel;
import com.loan.product.mapper.BankChannelMapper;
import com.loan.staff.entity.Staff;
import com.loan.staff.mapper.StaffMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 认证服务：全角色验证码登录 / 密码登录 / 验证码重置密码 / 登出 / 会话（Redis）。
 *
 * <p>JWT 保持轻量，完整用户存 Redis；密码统一通过 RSA 传输、BCrypt 存储，
 * 短信验证码按登录和重置密码场景隔离。
 *
 * @author loan-platform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    /** Redis 会话 TTL（2 小，登录/续期共用同一常量） */
    private static final Duration SESSION_TTL = Duration.ofHours(2);

    private final StaffMapper staffMapper;
    private final ChannelUserMapper channelUserMapper;
    private final BankChannelMapper bankChannelMapper;
    private final JwtService jwtService;
    private final LoginRsaCrypto loginRsaCrypto;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final com.loan.client.mapper.ClientProfileMapper clientProfileMapper;
    private final ClientLifecycleEventMapper lifecycleEventMapper;
    private final com.loan.sms.service.SmsService smsService;
    private final com.loan.invitation.service.InvitationService invitationService;

    /**
     * 客户登录（阶段三小程序：手机号 + 短信验证码）。
     *
     * <p>首次登录自动创建客户档案（customerGroup=PERSONAL，企业认证后更新为 ENTERPRISE）；
     * 携带邀请码时自动绑定推荐关系（referrer_type=CUSTOMER 进入奖励链路）。
     *
     * @param phone      手机号
     * @param code       短信验证码
     * @param inviteCode 邀请码（可选）
     * @return 登录响应（token + 客户 LoanUser）
     */
    public LoginResponse customerLogin(String phone, String code, String inviteCode) {
        if (!StringUtils.hasText(phone) || !StringUtils.hasText(code)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "手机号与验证码必填");
        }
        if (!smsService.verifyCode(phone, code)) {
            throw new BusinessException(ResultCode.CAPTCHA_ERROR, "验证码错误或已过期");
        }
        return customerLoginAfterSmsVerified(phone, inviteCode);
    }

    private LoginResponse customerLoginAfterSmsVerified(String phone, String inviteCode) {
        // 1. 按 phone_hash 查客户档案，不存在则创建
        String phoneHash = sha256(phone);
        com.loan.client.entity.ClientProfile client = clientProfileMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.loan.client.entity.ClientProfile>()
                        .eq(com.loan.client.entity.ClientProfile::getPhoneHash, phoneHash)
                        .last("limit 1"));
        if (client == null) {
            client = new com.loan.client.entity.ClientProfile();
            client.setClientCode(com.loan.common.util.BizIdGenerator.generate("client"));
            client.setCustomerGroup("PERSONAL");
            client.setContactName(desensitizePhone(phone));
            client.setPhone(com.loan.infrastructure.security.AesUtils.encrypt(phone));
            client.setPhoneHash(phoneHash);
            client.setSource("MINI");
            client.setStatus("ACTIVE");
            client.setInvitedFlag(0);
            client.setWecomAdded(0);
            client.setCreatedBy("mini");
            client.setCreatedAt(LocalDateTime.now());
            clientProfileMapper.insert(client);
            ClientLifecycleEvent event = new ClientLifecycleEvent();
            event.setClientCode(client.getClientCode());
            event.setEventType("ENTER_COMPANY_SEA");
            event.setSeaLevel("ENTERPRISE");
            event.setEpisodeNo(1);
            event.setEventAt(client.getCreatedAt());
            event.setReasonCode("CUSTOMER_SELF_REGISTER");
            lifecycleEventMapper.insert(event);
        }
        // 2. 绑定邀请码（可选）
        String referrerNo = null;
        String referrerName = null;
        if (StringUtils.hasText(inviteCode)) {
            Map<String, Object> bind = invitationService.bind(inviteCode, client.getClientCode(), client.getId());
            if ("CUSTOMER".equals(bind.get("referrerType"))) {
                referrerNo = (String) bind.get("referrerClientCode");
                // 受邀用户标记（受邀免费 VIP + 独享推荐奖励）
                if (client.getInvitedFlag() == null || client.getInvitedFlag() == 0) {
                    client.setInvitedFlag(1);
                    clientProfileMapper.updateById(client);
                }
            }
        }
        // 3. 构建客户 LoanUser + 签发 JWT
        LoanUser user = new LoanUser();
        user.setUserId(client.getId());
        user.setUserNo(client.getClientCode());
        user.setPhone(phone);
        user.setName(client.getContactName());
        user.setUserType(LoanUser.TYPE_CUSTOMER);
        user.setRegion(client.getExtJson());
        user.setReferrerNo(referrerNo);
        user.setInvitedFlag(client.getInvitedFlag() != null && client.getInvitedFlag() == 1);

        String token = jwtService.generateToken(user.getUserId(), user.getUserType(),
                user.getUserNo(), null);
        saveSession(user.getUserId(), user);

        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setExpireMillis(86400000L);
        response.setUser(user);
        return response;
    }

    /**
     * 手机号脱敏（138****0001）。
     *
     * @param phone 手机号
     * @return 脱敏串
     */
    private String desensitizePhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /** 手机验证码登录：员工或渠道账号按手机号登录。 */
    public LoginResponse loginByPhoneCode(String phone, String code) {
        return loginByPhoneCode(phone, code, null);
    }

    /** 手机验证码登录：可指定 STAFF/CHANNEL/CUSTOMER，未指定时兼容员工优先。 */
    public LoginResponse loginByPhoneCode(String phone, String code, String accountType) {
        if (!StringUtils.hasText(phone) || !StringUtils.hasText(code)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "手机号与验证码必填");
        }
        if (!smsService.verifyCode(phone, code)) {
            throw new BusinessException(ResultCode.CAPTCHA_ERROR, "验证码错误或已过期");
        }
        String hash = sha256(phone);
        Staff staff = "CHANNEL".equalsIgnoreCase(accountType) || "CUSTOMER".equalsIgnoreCase(accountType) ? null
                : staffMapper.selectOne(new LambdaQueryWrapper<Staff>().eq(Staff::getPhoneHash, hash).last("limit 1"));
        if (staff != null) {
            if (!"ACTIVE".equalsIgnoreCase(staff.getStatus())) throw new BusinessException(ResultCode.FORBIDDEN, "员工账号已停用");
            LoanUser user = buildStaffUser(staff);
            return issue(user);
        }
        ChannelUser channel = "STAFF".equalsIgnoreCase(accountType) || "CUSTOMER".equalsIgnoreCase(accountType) ? null
                : channelUserMapper.selectOne(new LambdaQueryWrapper<ChannelUser>().eq(ChannelUser::getPhoneHash, hash).last("limit 1"));
        if (channel != null) {
            if (!"ACTIVE".equalsIgnoreCase(channel.getStatus())) throw new BusinessException(ResultCode.FORBIDDEN, "渠道账号已停用");
            LoanUser user = new LoanUser(); user.setUserId(channel.getId()); user.setUserNo(hash); user.setPhone(phone);
            user.setName(channel.getName()); user.setUserType(LoanUser.TYPE_CHANNEL); user.setBankChannelId(channel.getBankChannelId());
            return issue(user);
        }
        if ("CUSTOMER".equalsIgnoreCase(accountType)) {
            return customerLoginAfterSmsVerified(phone, null);
        }
        throw new BusinessException(ResultCode.DATA_NOT_FOUND, "手机号未绑定对应账号");
    }

    /** 全角色密码登录，accountType 必须为 STAFF / CHANNEL / CUSTOMER。 */
    public LoginResponse passwordLogin(String phone, String rsaEncryptedPassword, String accountType) {
        if (!StringUtils.hasText(phone) || !StringUtils.hasText(rsaEncryptedPassword)
                || !StringUtils.hasText(accountType)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "手机号、密码与账号类型必填");
        }
        String plainPassword = decryptPassword(rsaEncryptedPassword);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String hash = sha256(phone);
        if ("STAFF".equalsIgnoreCase(accountType)) {
            Staff staff = staffMapper.selectOne(new LambdaQueryWrapper<Staff>()
                    .select(Staff::getId, Staff::getStaffCode, Staff::getStaffName,
                            Staff::getDeptCode, Staff::getRoleCode, Staff::getPhone,
                            Staff::getStatus, Staff::getPassword)
                    .eq(Staff::getPhoneHash, hash).last("limit 1"));
            requireActiveStaff(staff);
            requirePassword(encoder, plainPassword, staff.getPassword());
            return issue(buildStaffUser(staff));
        }
        if ("CHANNEL".equalsIgnoreCase(accountType)) {
            ChannelUser channel = channelUserMapper.selectOne(new LambdaQueryWrapper<ChannelUser>()
                    .eq(ChannelUser::getPhoneHash, hash).last("limit 1"));
            requireActiveChannel(channel);
            requirePassword(encoder, plainPassword, channel.getPassword());
            channelUserMapper.update(null, new LambdaUpdateWrapper<ChannelUser>()
                    .eq(ChannelUser::getId, channel.getId())
                    .set(ChannelUser::getLastLoginTime, LocalDateTime.now()));
            return issue(buildChannelUser(channel, phone));
        }
        if ("CUSTOMER".equalsIgnoreCase(accountType)) {
            com.loan.client.entity.ClientProfile client = clientProfileMapper.selectOne(
                    new LambdaQueryWrapper<com.loan.client.entity.ClientProfile>()
                            .select(com.loan.client.entity.ClientProfile::getId,
                                    com.loan.client.entity.ClientProfile::getClientCode,
                                    com.loan.client.entity.ClientProfile::getContactName,
                                    com.loan.client.entity.ClientProfile::getStatus,
                                    com.loan.client.entity.ClientProfile::getExtJson,
                                    com.loan.client.entity.ClientProfile::getInvitedFlag,
                                    com.loan.client.entity.ClientProfile::getPassword)
                            .eq(com.loan.client.entity.ClientProfile::getPhoneHash, hash).last("limit 1"));
            if (client == null || !"ACTIVE".equalsIgnoreCase(client.getStatus())) {
                throw new BusinessException(ResultCode.UNAUTHORIZED, "账号或密码错误");
            }
            requirePassword(encoder, plainPassword, client.getPassword());
            return issue(buildCustomerUser(client, phone));
        }
        throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的账号类型");
    }

    /** 验证短信后为任一角色设置新密码；不会自动登录。 */
    public void resetPassword(String phone, String code, String rsaEncryptedPassword, String accountType) {
        if (!StringUtils.hasText(phone) || !StringUtils.hasText(code) || !StringUtils.hasText(accountType)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "手机号、验证码与账号类型必填");
        }
        String plainPassword = decryptPassword(rsaEncryptedPassword);
        if (plainPassword.length() < 8 || plainPassword.length() > 64
                || !plainPassword.matches(".*[A-Za-z].*") || !plainPassword.matches(".*\\d.*")) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "密码须为8-64位且同时包含字母和数字");
        }
        if (!smsService.verifyCode(phone, code, "RESET_PASSWORD")) {
            throw new BusinessException(ResultCode.CAPTCHA_ERROR, "验证码错误或已过期");
        }
        String encoded = new BCryptPasswordEncoder().encode(plainPassword);
        String hash = sha256(phone);
        int changed;
        if ("STAFF".equalsIgnoreCase(accountType)) {
            changed = staffMapper.update(null, new LambdaUpdateWrapper<Staff>()
                    .eq(Staff::getPhoneHash, hash).eq(Staff::getStatus, "ACTIVE")
                    .set(Staff::getPassword, encoded).set(Staff::getUpdatedAt, LocalDateTime.now()));
        } else if ("CHANNEL".equalsIgnoreCase(accountType)) {
            changed = channelUserMapper.update(null, new LambdaUpdateWrapper<ChannelUser>()
                    .eq(ChannelUser::getPhoneHash, hash).eq(ChannelUser::getStatus, "ACTIVE")
                    .set(ChannelUser::getPassword, encoded).set(ChannelUser::getUpdatedAt, LocalDateTime.now()));
        } else if ("CUSTOMER".equalsIgnoreCase(accountType)) {
            changed = clientProfileMapper.update(null,
                    new LambdaUpdateWrapper<com.loan.client.entity.ClientProfile>()
                            .eq(com.loan.client.entity.ClientProfile::getPhoneHash, hash)
                            .eq(com.loan.client.entity.ClientProfile::getStatus, "ACTIVE")
                            .set(com.loan.client.entity.ClientProfile::getPassword, encoded)
                            .set(com.loan.client.entity.ClientProfile::getUpdatedAt, LocalDateTime.now()));
        } else {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的账号类型");
        }
        if (changed != 1) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "手机号未绑定对应账号或账号已停用");
        }
    }

    private String decryptPassword(String encryptedPassword) {
        String password = loginRsaCrypto.decryptBase64(encryptedPassword);
        if (!StringUtils.hasText(password)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "密码解密失败");
        }
        return password;
    }

    private void requirePassword(BCryptPasswordEncoder encoder, String plain, String encoded) {
        if (!StringUtils.hasText(encoded)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "尚未设置密码，请使用验证码找回密码进行设置");
        }
        if (!encoder.matches(plain, encoded)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "账号或密码错误");
        }
    }

    private void requireActiveStaff(Staff staff) {
        if (staff == null) throw new BusinessException(ResultCode.UNAUTHORIZED, "账号或密码错误");
        if (!"ACTIVE".equalsIgnoreCase(staff.getStatus())) throw new BusinessException(ResultCode.FORBIDDEN, "员工账号已停用");
    }

    private void requireActiveChannel(ChannelUser channel) {
        if (channel == null) throw new BusinessException(ResultCode.UNAUTHORIZED, "账号或密码错误");
        if (!"ACTIVE".equalsIgnoreCase(channel.getStatus())) throw new BusinessException(ResultCode.FORBIDDEN, "渠道账号已停用");
    }

    private LoanUser buildChannelUser(ChannelUser channel, String phone) {
        LoanUser user = new LoanUser();
        user.setUserId(channel.getId()); user.setUserNo(channel.getPhoneHash()); user.setPhone(phone);
        user.setName(channel.getName()); user.setUserType(LoanUser.TYPE_CHANNEL);
        user.setBankChannelId(channel.getBankChannelId());
        if (channel.getBankChannelId() != null) {
            BankChannel bank = bankChannelMapper.selectById(channel.getBankChannelId());
            if (bank != null) user.setBankChannelCode(bank.getChannelCode());
        }
        return user;
    }

    private LoanUser buildCustomerUser(com.loan.client.entity.ClientProfile client, String phone) {
        LoanUser user = new LoanUser();
        user.setUserId(client.getId()); user.setUserNo(client.getClientCode()); user.setPhone(phone);
        user.setName(client.getContactName()); user.setUserType(LoanUser.TYPE_CUSTOMER);
        user.setRegion(client.getExtJson()); user.setInvitedFlag(Integer.valueOf(1).equals(client.getInvitedFlag()));
        return user;
    }

    private LoginResponse issue(LoanUser user) {
        String token = jwtService.generateToken(user.getUserId(), user.getUserType(), user.getUserNo(), user.getRoleCode());
        saveSession(user.getUserId(), user);
        LoginResponse response = new LoginResponse(); response.setToken(token); response.setExpireMillis(86400000L); response.setUser(user); return response;
    }

    /**
     * 登出：删除 Redis 会话（踢下线）。
     *
     * @param userId 用户 ID
     */
    public void logout(String userType, Long userId) {
        if (!StringUtils.hasText(userType) || userId == null) {
            return;
        }
        stringRedisTemplate.delete(java.util.Arrays.asList(
                SessionKeyUtils.key(userType, userId), SessionKeyUtils.legacyKey(userId)));
    }

    /**
     * 按 userId 从 Redis 加载会话用户（供认证过滤器）。
     *
     * @param userId 用户 ID
     * @return LoanUser，不存在返回 null
     */
    public LoanUser loadSession(String userType, Long userId) {
        if (!StringUtils.hasText(userType) || userId == null) {
            return null;
        }
        try {
            String json = stringRedisTemplate.opsForValue().get(SessionKeyUtils.key(userType, userId));
            if (json == null) {
                return null;
            }
            return objectMapper.readValue(json, LoanUser.class);
        } catch (Exception e) {
            log.warn("加载会话失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 会话滑动续期：认证成功的请求将 Redis 会话 TTL 重置为 2 小时，
     * 避免"活跃用户仍在操作却被会话过期踢下线"（JWT 24h 内有效）。
     *
     * <p>续期失败不影响本次请求（尽力而为）。
     *
     * @param userId 用户 ID
     */
    public void renewSession(String userType, Long userId) {
        if (!StringUtils.hasText(userType) || userId == null) {
            return;
        }
        try {
            stringRedisTemplate.expire(SessionKeyUtils.key(userType, userId), SESSION_TTL);
        } catch (Exception e) {
            log.warn("会话续期失败: {}", e.getMessage());
        }
    }

    /**
     * 构建员工 LoanUser。
     *
     * @param staff 员工
     * @return LoanUser
     */
    private LoanUser buildStaffUser(Staff staff) {
        LoanUser user = new LoanUser();
        user.setUserId(staff.getId());
        user.setUserNo(staff.getStaffCode());
        user.setPhone(AesUtils.decrypt(staff.getPhone()));
        user.setName(staff.getStaffName());
        user.setUserType(LoanUser.TYPE_STAFF);
        user.setRoleCode(staff.getRoleCode());
        user.setDeptCode(staff.getDeptCode());
        return user;
    }

    /**
     * 渠道端账号密码登录（BCrypt 校验 + RSA 解密密码）。
     *
     * @param phone            手机号（明文，前端提交后按 phone_hash 查账号）
     * @param rsaEncryptedPassword RSA 加密后的密码（Base64）
     * @return 登录响应
     */
    public LoginResponse channelLogin(String phone, String rsaEncryptedPassword) {
        if (!StringUtils.hasText(phone) || !StringUtils.hasText(rsaEncryptedPassword)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "手机号与密码必填");
        }
        // 1. RSA 解密密码；禁止任何固定口令或明文旁路。
        String plainPassword = loginRsaCrypto.decryptBase64(rsaEncryptedPassword);
        if (plainPassword == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "密码解密失败");
        }
        // 2. 按 phone_hash 查账号
        ChannelUser channelUser = channelUserMapper.selectOne(
                new LambdaQueryWrapper<ChannelUser>().eq(ChannelUser::getPhoneHash, sha256(phone)));
        if (channelUser == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "渠道账号不存在");
        }
        if (!"ACTIVE".equalsIgnoreCase(channelUser.getStatus())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "渠道账号已停用");
        }
        // 3. BCrypt 校验密码
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        if (!encoder.matches(plainPassword, channelUser.getPassword())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "账号或密码错误");
        }
        // 4. 更新最后登录时间（精确更新仅 last_login_time；禁止 updateById 全字段写回——
        //    库中 phone 为 AES 密文且随加密密钥版本长度可变，全字段写回会 Data too long（D28 实测），
        //    且不应触碰密码/手机号等敏感字段）
        channelUserMapper.update(null, new LambdaUpdateWrapper<ChannelUser>()
                .eq(ChannelUser::getId, channelUser.getId())
                .set(ChannelUser::getLastLoginTime, LocalDateTime.now()));

        // 5. 构建 LoanUser（CHANNEL 类型）+ 签发 JWT + Redis 会话
        LoanUser user = new LoanUser();
        user.setUserId(channelUser.getId());
        user.setUserNo(channelUser.getPhoneHash());
        user.setPhone(AesUtils.decrypt(channelUser.getPhone()));
        user.setName(channelUser.getName());
        user.setUserType(LoanUser.TYPE_CHANNEL);
        user.setRegion(null);
        // 渠道数据范围硬隔离：填充所属银行渠道编码（T11/D28，产品只读分页按本行过滤）
        user.setBankChannelId(channelUser.getBankChannelId());
        if (channelUser.getBankChannelId() != null) {
            BankChannel bankChannel = bankChannelMapper.selectById(channelUser.getBankChannelId());
            if (bankChannel != null) {
                user.setBankChannelCode(bankChannel.getChannelCode());
            }
        }

        String token = jwtService.generateToken(user.getUserId(), user.getUserType(),
                user.getUserNo(), null);
        saveSession(user.getUserId(), user);

        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setExpireMillis(86400000L);
        response.setUser(user);
        return response;
    }

    /**
     * SHA-256 哈希（手机号查重/登录）。
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

    /**
     * 保存会话到 Redis（2 小时过期）。
     *
     * @param userId 用户 ID
     * @param user   用户对象
     */
    private void saveSession(Long userId, LoanUser user) {
        try {
            String json = objectMapper.writeValueAsString(user);
            stringRedisTemplate.opsForValue().set(
                    SessionKeyUtils.key(user.getUserType(), userId), json, SESSION_TTL);
            // 新登录完成后移除旧版无类型 key，防止历史串号数据继续被误用。
            stringRedisTemplate.delete(SessionKeyUtils.legacyKey(userId));
        } catch (Exception e) {
            log.error("保存会话失败", e);
        }
    }
}
