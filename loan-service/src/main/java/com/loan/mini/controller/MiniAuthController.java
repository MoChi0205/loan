package com.loan.mini.controller;

import com.loan.auth.dto.LoginResponse;
import com.loan.auth.service.AuthService;
import com.loan.common.Result;
import com.loan.context.CurrentUser;
import com.loan.context.LoanUser;
import com.loan.mini.service.MiniAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 小程序端：登录 / 我的资料 / 企业认证 / 企微活码。
 *
 * @author loan-platform
 */
@RestController
@RequestMapping("/api/mini")
@RequiredArgsConstructor
public class MiniAuthController {

    private final AuthService authService;
    private final MiniAuthService miniAuthService;

    @Autowired
    private Environment environment;

    /**
     * 客户登录（Q3 方案 A 主通道：wx.login code 换 openid 签发 token）。
     *
     * <p>请求体：{code, nickname?, avatar?, inviteCode?}。
     * 兼容开关 {@code mini.auth.phone-compat=true}（Nacos 可覆盖）时，
     * 传入 {phone, code(短信验证码), inviteCode?} 仍走手机号验证码登录（管理端手动建档场景）。
     *
     * @param body 登录请求
     * @return token + 客户信息
     */
    @PostMapping("/auth/login")
    public Result<LoginResponse> login(@RequestBody Map<String, String> body) {
        String phone = body.get("phone");
        if (StringUtils.hasText(phone) && phoneCompatEnabled()) {
            // 兼容开关开启且带手机号：走短信验证码通道（管理端手动建档用）
            return Result.ok(authService.customerLogin(phone, body.get("code"), body.get("inviteCode")));
        }
        return Result.ok(miniAuthService.wxLogin(body.get("code"), body.get("nickname"),
                body.get("avatar"), body.get("inviteCode")));
    }

    /**
     * 我的资料摘要（含角色信息，供小程序做角色化菜单与权限判定）。
     *
     * <p>除客户档案外，额外返回 {@code roleInfo}：
     * <ul>
     *   <li>{@code userType}：CUSTOMER / CHANNEL / STAFF</li>
     *   <li>{@code roleCode}：ADVISER / DEPT_MANAGER / BOSS / OPERATOR / SUPER_ADMIN（仅 STAFF）</li>
     *   <li>{@code role}：前端角色标识（customer / channel / adviser / deptmgr / boss / operator / super）</li>
     * </ul>
     * 用于替代纯本地缓存的角色判定，在清缓存 / 换设备登录时也能拿到正确角色。
     *
     * @param user 当前登录用户（客户 / 渠道 / 员工）
     * @return 资料摘要 + roleInfo
     */
    @GetMapping("/me")
    public Result<Map<String, Object>> me(@CurrentUser LoanUser user) {
        Map<String, Object> profile = miniAuthService.myProfile(user == null ? null : user.getUserNo());
        if (profile == null) {
            profile = new java.util.LinkedHashMap<>();
        }
        profile.put("roleInfo", resolveRoleInfo(user));
        return Result.ok(profile);
    }

    /**
     * 依据 LoanUser 组装角色信息（前端角色标识与后端枚举的映射中心）。
     *
     * <p>映射规则与前端 {@code store/user.js resolveRole()} 保持一致，
     * 修改任一侧时务必同步另一侧。
     *
     * @param user 当前登录用户
     * @return { userType, roleCode, role }
     */
    private Map<String, Object> resolveRoleInfo(LoanUser user) {
        Map<String, Object> roleInfo = new java.util.LinkedHashMap<>();
        if (user == null) {
            roleInfo.put("userType", LoanUser.TYPE_CUSTOMER);
            roleInfo.put("roleCode", "");
            roleInfo.put("role", "customer");
            return roleInfo;
        }
        String userType = user.getUserType() == null ? LoanUser.TYPE_CUSTOMER : user.getUserType();
        String roleCode = user.getRoleCode() == null ? "" : user.getRoleCode();
        roleInfo.put("userType", userType);
        roleInfo.put("roleCode", roleCode);

        // 与前端 resolveRole() 对齐的映射
        String role = "customer";
        if (LoanUser.TYPE_CHANNEL.equals(userType)) {
            role = "channel";
        } else if (LoanUser.TYPE_STAFF.equals(userType)) {
            String code = roleCode.toUpperCase();
            if ("ADVISER".equals(code)) {
                role = "adviser";
            } else if ("DEPT_MANAGER".equals(code)) {
                role = "deptmgr";
            } else if ("BOSS".equals(code)) {
                role = "boss";
            } else if ("OPERATOR".equals(code)) {
                role = "operator";
            } else if ("SUPER_ADMIN".equals(code) || "SUPER".equals(code)) {
                role = "super";
            } else {
                role = "adviser"; // 员工但未识别细分角色：最小可用权限
            }
        }
        roleInfo.put("role", role);
        return roleInfo;
    }

    /**
     * 企业认证（营业执照信息）。
     *
     * @param body {creditCode, enterpriseName, contactName?}
     * @param user 当前客户
     * @return 认证结果
     */
    @PostMapping("/auth/enterprise")
    public Result<Map<String, Object>> enterpriseAuth(@RequestBody Map<String, String> body, @CurrentUser LoanUser user) {
        String clientCode = user == null ? null : user.getUserNo();
        if (clientCode == null) {
            throw new com.loan.exception.BusinessException(
                    com.loan.common.ResultCode.UNAUTHORIZED, "请先登录");
        }
        return Result.ok(miniAuthService.enterpriseAuth(clientCode, body.get("creditCode"),
                body.get("enterpriseName"), body.get("contactName")));
    }

    /**
     * 企微客服活码。
     *
     * @return 活码 URL
     */
    @GetMapping("/wecom/qrcode")
    public Result<String> wecomQrCode() {
        return Result.ok(miniAuthService.wecomQrCode());
    }

    /**
     * 手机号验证码登录兼容开关（Nacos 可覆盖；默认关闭，仅 wx.login 主通道）。
     *
     * @return true 开启
     */
    private boolean phoneCompatEnabled() {
        return Boolean.parseBoolean(environment.getProperty("mini.auth.phone-compat", "false"));
    }
}
