package com.loan.auth.controller;

import com.loan.auth.dto.LoginRequest;
import com.loan.auth.dto.LoginResponse;
import com.loan.auth.service.AuthService;
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
 * 认证接口（登录 / 登出 / 当前用户 / 健康检查 / RSA 公钥）。
 *
 * <p>阶段一员工走 SSO 模拟登录（crmUserId 映射）；RSA 公钥接口预留供渠道端密码登录。
 *
 * @author loan-platform
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final LoginRsaCrypto loginRsaCrypto;

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

    /**
     * 登录（阶段一 SSO 模拟：crmUserId 映射员工）。
     *
     * @param request 登录请求
     * @return token + 用户信息
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    /**
     * 渠道端账号密码登录（BCrypt + RSA 解密）。
     *
     * @param body { phone, password(RSA加密Base64) }
     * @return token + 用户信息
     */
    @PostMapping("/channel-login")
    public Result<LoginResponse> channelLogin(@RequestBody Map<String, String> body) {
        return Result.ok(authService.channelLogin(body.get("phone"), body.get("password")));
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
