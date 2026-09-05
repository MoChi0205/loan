package com.loan.sms.controller;

import com.loan.common.Result;
import com.loan.sms.service.SmsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 短信 HTTP 接口。
 *
 * @author loan-platform
 */
@RestController
@RequestMapping("/api/sms")
@RequiredArgsConstructor
public class SmsController {

    private final SmsService smsService;

    /**
     * 发送验证码。
     *
     * @param body { phone }
     * @return 成功标记
     */
    @PostMapping("/send-code")
    public Result<String> sendCode(@RequestBody Map<String, String> body) {
        smsService.sendVerifyCode(body.get("phone"));
        return Result.ok("ok");
    }

    /**
     * 校验验证码。
     *
     * @param body { phone, code }
     * @return 校验结果
     */
    @PostMapping("/verify-code")
    public Result<Boolean> verifyCode(@RequestBody Map<String, String> body) {
        return Result.ok(smsService.verifyCode(body.get("phone"), body.get("code")));
    }
}
