package com.loan.auth.service;

import com.loan.common.ResultCode;
import com.loan.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 登录随机验证码：4 位数字、答案只留服务端、一次性消费。 */
class AuthCaptchaServiceTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private AuthCaptchaService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        service = new AuthCaptchaService(redis);
    }

    @Test
    void createReturnsFourDigitCodeWithPngImage() {
        Map<String, String> result = service.create();

        assertTrue(result.get("captchaId").length() > 16);
        assertTrue(result.get("imageBase64").startsWith("iVBOR"), "验证码应为 PNG 图片");
        assertEquals("300", result.get("expireSeconds"));

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(keyCaptor.capture(), valueCaptor.capture(), any(Duration.class));
        assertTrue(keyCaptor.getValue().startsWith("loan:auth:captcha:"));
        assertTrue(valueCaptor.getValue().matches("\\d{4}"), "验证码必须是 4 位数字");
    }

    @Test
    void verifyConsumesCodeOnceAndRejectsWrongAnswer() {
        Map<String, String> result = service.create();
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(keyCaptor.capture(), valueCaptor.capture(), any(Duration.class));
        String key = keyCaptor.getValue();
        String code = valueCaptor.getValue();

        when(valueOps.get(key)).thenReturn(code);
        service.verify(result.get("captchaId"), code);
        // 无论成功失败都立即失效，防止重放（delete 走 StringRedisTemplate，不是 opsForValue）。
        verify(redis).delete(key);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.verify(result.get("captchaId"), "0000"));
        assertEquals(ResultCode.CAPTCHA_ERROR.getCode(), error.getCode());
    }

    @Test
    void verifyRejectsMissingInput() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.verify(null, "1234"));
        assertEquals(ResultCode.CAPTCHA_ERROR.getCode(), error.getCode());
        assertEquals(ResultCode.CAPTCHA_ERROR.getCode(),
                assertThrows(BusinessException.class, () -> service.verify("any", " ")).getCode());
    }
}
