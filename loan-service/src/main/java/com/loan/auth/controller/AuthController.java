package com.loan.auth.controller;

import com.loan.auth.dto.LoginResponse;
import com.loan.auth.service.AuthService;
import com.loan.auth.service.AuthCaptchaService;
import com.loan.common.Result;
import com.loan.context.CurrentUser;
import com.loan.context.LoanUser;
import com.loan.context.UserContext;
import com.loan.infrastructure.security.LoginRsaCrypto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证接口（全角色验证码/密码登录、密码找回、登出、当前用户、RSA 公钥）。
 *
 * <p>员工、渠道、客户按账号类型隔离；密码在浏览器使用 RSA 公钥加密后提交。
 *
 * @author loan-platform
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final LoginRsaCrypto loginRsaCrypto;
    private final AuthCaptchaService authCaptchaService;

    /**
     * 服务健康检查（不鉴权）。
     *
     * @return 成功标记
     */
    @GetMapping("/health")
    public Result<String> health() {
        return Result.ok("ok");
    }

    /**
     * 登录页拉取 RSA 公钥（前端加密密码用，预留）。
     *
     * @return RSA 公钥
     */
    @GetMapping("/public-key")
    public Result<Map<String, Object>> publicKey() {
        Map<String, Object> data = new HashMap<>(2);
        data.put("algorithm", "RSA");
        data.put("publicKey", loginRsaCrypto.getPublicKeyBase64());
        return Result.ok(data);
    }

    /** 获取一次性随机 4 位验证码（Base64 PNG，答案只留在服务端）。 */
    @GetMapping("/captcha")
    public Result<Map<String, String>> captcha() {
        return Result.ok(authCaptchaService.create());
    }

    @PostMapping("/code-login")
    public Result<LoginResponse> codeLogin(@RequestBody Map<String, String> body) {
        return Result.ok(authService.loginByPhoneCode(body.get("phone"), body.get("code"), body.get("accountType")));
    }

    @PostMapping("/password-login")
    public Result<LoginResponse> passwordLogin(@RequestBody Map<String, String> body) {
        authCaptchaService.verify(body.get("captchaId"), body.get("captchaCode"));
        return Result.ok(authService.passwordLogin(body.get("phone"), body.get("password"), body.get("accountType")));
    }

    @PostMapping("/reset-password")
    public Result<String> resetPassword(@RequestBody Map<String, String> body) {
        authService.resetPassword(body.get("phone"), body.get("code"), body.get("password"), body.get("accountType"));
        return Result.ok("ok");
    }

    /**
     * 渠道端账号密码登录（BCrypt + RSA 解密）。
     *
     * @param body { phone, password(RSA加密Base64) }
     * @return token + 用户信息
     */
    @PostMapping("/channel-login")
    public Result<LoginResponse> channelLogin(@RequestBody Map<String, String> body) {
        authCaptchaService.verify(body.get("captchaId"), body.get("captchaCode"));
        return Result.ok(authService.passwordLogin(body.get("phone"), body.get("password"), "CHANNEL"));
    }

    /**
     * 登出（删除 Redis 会话，踢下线）。
     *
     * @return 成功标记
     */
    @PostMapping("/logout")
    public Result<String> logout() {
        LoanUser user = UserContext.getUser();
        if (user != null) {
            authService.logout(user.getUserType(), user.getUserId());
        }
        return Result.ok("ok");
    }

    /**
     * 当前登录用户（@CurrentUser 注入完整 LoanUser）。
     *
     * @param user 当前登录用户
     * @return 当前用户信息
     */
    @GetMapping("/me")
    public Result<LoanUser> me(@CurrentUser LoanUser user) {
        return Result.ok(user);
    }
}
