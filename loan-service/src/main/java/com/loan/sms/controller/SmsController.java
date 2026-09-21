package com.loan.sms.controller;

import com.loan.common.Result;
import com.loan.sms.service.SmsService;
import com.loan.auth.service.AuthCaptchaService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.HashMap;

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
    private final AuthCaptchaService authCaptchaService;

    /** 仅本地/测试模拟短信通道开启；生产必须为 false。 */
    @Value("${loan.auth.dev-sms-code-visible:false}")
    private boolean devSmsCodeVisible;

    /**
     * 发送验证码。
     *
     * @param body { phone, scene, captchaId, captchaCode }
     * @return 成功标记
     */
    @PostMapping("/send-code")
    public Result<Map<String, String>> sendCode(@RequestBody Map<String, String> body) {
        authCaptchaService.verify(body.get("captchaId"), body.get("captchaCode"));
        String code = smsService.sendVerifyCode(body.get("phone"), body.get("scene"));
        Map<String, String> data = new HashMap<>();
        data.put("status", "ok");
        if (devSmsCodeVisible) {
            data.put("devCode", code);
        }
        return Result.ok(data);
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
