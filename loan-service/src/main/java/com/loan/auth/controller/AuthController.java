package com.loan.auth.controller;

import com.loan.common.Result;
import com.loan.context.CurrentUser;
import com.loan.context.LoanUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证域健康检查与当前用户示例（骨架验证用）。
 *
 * @author loan-platform
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

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
     * 当前登录用户示例（演示 {@code @CurrentUser} 注解注入完整 LoanUser）。
     *
     * @param user 当前登录用户（未接入登录前为 null）
     * @return 当前用户信息
     */
    @GetMapping("/me")
    public Result<LoanUser> me(@CurrentUser LoanUser user) {
        return Result.ok(user);
    }
}
