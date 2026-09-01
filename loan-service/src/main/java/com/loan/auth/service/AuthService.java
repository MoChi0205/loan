package com.loan.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loan.auth.dto.LoginRequest;
import com.loan.auth.dto.LoginResponse;
import com.loan.channel.entity.ChannelUser;
import com.loan.channel.mapper.ChannelUserMapper;
import com.loan.common.ResultCode;
import com.loan.context.LoanUser;
import com.loan.exception.BusinessException;
import com.loan.infrastructure.security.AesUtils;
import com.loan.infrastructure.security.JwtService;
import com.loan.infrastructure.security.LoginRsaCrypto;
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
 * 认证服务：登录（SSO 模拟）/ 登出 / 会话（Redis）。
 *
 * <p>阶段一设计：
 * <ul>
 *   <li>员工登录走 SSO 模拟：前端传 crmUserId，后端映射 t_staff → 构建 LoanUser → 签发 JWT + Redis 存完整 User。</li>
 *   <li>正式环境此接口由「CRM SSO 回调」替代（SSO 返回员工身份 → loan 按 crmUserId 映射）。</li>
 *   <li>JWT 轻量（userId/userType/userNo/roleCode），完整用户存 Redis（可踢下线：删 key）。</li>
 * </ul>
 *
 * @author loan-platform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    /** Redis 会话 key 前缀 */
    private static final String SESSION_KEY_PREFIX = "loan:session:";

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

    /**
     * 登录（阶段一 SSO 模拟：按 crmUserId 映射员工）。
     *
     * @param request 登录请求
     * @return 登录响应（token + 用户）
     */
    public LoginResponse login(LoginRequest request) {
        String crmUserId = request == null ? null : request.getCrmUserId();
        if (!StringUtils.hasText(crmUserId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "缺少 crmUserId（SSO 身份）");
        }

        // 1. 按 crmUserId 映射员工（SSO 已验身份，loan 侧只做映射）
        Staff staff = staffMapper.selectOne(
                new LambdaQueryWrapper<Staff>().eq(Staff::getCrmUserId, crmUserId));
        if (staff == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "员工不存在或未绑定 CRM 身份");
        }
        if (!"ACTIVE".equalsIgnoreCase(staff.getStatus())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "员工已离职，账号停用");
        }

        // 2. 构建 LoanUser
        LoanUser user = buildStaffUser(staff);

        // 3. 签发 JWT + Redis 会话
        String token = jwtService.generateToken(user.getUserId(), user.getUserType(),
                user.getUserNo(), user.getRoleCode());
        saveSession(user.getUserId(), user);

        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setExpireMillis(86400000L);
        response.setUser(user);
        return response;
    }

    /**
     * 登出：删除 Redis 会话（踢下线）。
     *
     * @param userId 用户 ID
     */
    public void logout(Long userId) {
        if (userId == null) {
            return;
        }
        stringRedisTemplate.delete(SESSION_KEY_PREFIX + userId);
    }

    /**
     * 按 userId 从 Redis 加载会话用户（供认证过滤器）。
     *
     * @param userId 用户 ID
     * @return LoanUser，不存在返回 null
     */
    public LoanUser loadSession(Long userId) {
        if (userId == null) {
            return null;
        }
        try {
            String json = stringRedisTemplate.opsForValue().get(SESSION_KEY_PREFIX + userId);
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
    public void renewSession(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            stringRedisTemplate.expire(SESSION_KEY_PREFIX + userId, SESSION_TTL);
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
        // 阶段一联调模拟旁路（与员工 SSO 模拟"密码暂不校验"一致，Login.vue 同语义）：
        // 密码字段等于约定模拟串 loan-sim-pwd 时，跳过 RSA 解密与 BCrypt 校验（账号仍须存在且 ACTIVE）。
        // ⚠️ 上线前必须移除该旁路，恢复纯 RSA 解密 + BCrypt 校验（T11/D21 渠道 Web 沙箱验收用）。
        boolean sim = "loan-sim-pwd".equals(rsaEncryptedPassword);
        // 1. RSA 解密密码（模拟旁路跳过：明文无法作为 RSA 密文解密）
        String plainPassword = sim ? rsaEncryptedPassword : loginRsaCrypto.decryptBase64(rsaEncryptedPassword);
        if (!sim && plainPassword == null) {
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
        // 3. BCrypt 校验密码（模拟旁路跳过）
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        if (!sim && !encoder.matches(plainPassword, channelUser.getPassword())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "账号或密码错误");
        }
        // 4. 更新最后登录时间（精确更新仅 last_login_time；禁止 updateById 全字段写回——
        //    库中 phone 为 AES 密文且随加密密钥版本长度可变，全字段写回会 Data too long（D28 实测），
        //    且模拟旁路阶段不应触碰密码/手机号等敏感字段）
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
            stringRedisTemplate.opsForValue().set(SESSION_KEY_PREFIX + userId, json, SESSION_TTL);
        } catch (Exception e) {
            log.error("保存会话失败", e);
        }
    }
}
