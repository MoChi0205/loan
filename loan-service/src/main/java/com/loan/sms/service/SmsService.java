package com.loan.sms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.loan.common.ResultCode;
import com.loan.exception.BusinessException;
import com.loan.sms.entity.SmsRecord;
import com.loan.sms.entity.SmsTemplate;
import com.loan.sms.mapper.SmsRecordMapper;
import com.loan.sms.mapper.SmsTemplateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 短信服务：发送验证码（模板渲染 + Redis 校验 + 频控）+ 校验验证码。
 *
 * <p>阶段一发送走「模拟通道」（落记录，不接腾讯云）；Redis 校验：60s 重发间隔 + 单号日上限 5 条。
 * 多通道策略工厂（腾讯云/预留）后续迭代扩展（对齐 tse SmsStrategyFactory）。
 *
 * @author loan-platform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmsService {

    /** 验证码 Redis key 前缀 */
    private static final String CODE_KEY_PREFIX = "loan:sms:code:";
    /** 发送间隔 key 前缀 */
    private static final String INTERVAL_KEY_PREFIX = "loan:sms:interval:";
    /** 日上限 key 前缀 */
    private static final String DAILY_KEY_PREFIX = "loan:sms:daily:";
    /** 单号日上限 */
    private static final int DAILY_LIMIT = 5;

    private final SmsTemplateMapper smsTemplateMapper;
    private final SmsRecordMapper smsRecordMapper;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 发送验证码（LOGIN_VERIFY 模板）。
     *
     * @param phone 手机号
     */
    public void sendVerifyCode(String phone) {
        if (!StringUtils.hasText(phone)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "手机号必填");
        }
        // 60s 重发间隔校验
        String intervalKey = INTERVAL_KEY_PREFIX + phone;
        String last = stringRedisTemplate.opsForValue().get(intervalKey);
        if (last != null) {
            throw new BusinessException(ResultCode.TOO_FREQUENT, "验证码 60 秒内只能发送一次");
        }
        // 单号日上限校验
        String dailyKey = DAILY_KEY_PREFIX + phone;
        String daily = stringRedisTemplate.opsForValue().get(dailyKey);
        int dailyCount = daily == null ? 0 : Integer.parseInt(daily);
        if (dailyCount >= DAILY_LIMIT) {
            throw new BusinessException(ResultCode.TOO_FREQUENT, "验证码发送超当日上限");
        }

        // 查模板
        SmsTemplate template = smsTemplateMapper.selectOne(
                new LambdaQueryWrapper<SmsTemplate>().eq(SmsTemplate::getSmsType, "LOGIN_VERIFY"));
        if (template == null || template.getEnabled() == null || template.getEnabled() != 1) {
            throw new BusinessException(ResultCode.RULE_CONFIG_ERROR, "验证码模板未配置或已停用");
        }

        // 生成 6 位验证码 + 渲染模板
        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1000000));
        String content = template.getContent().replace("${code}", code);

        // 模拟发送：落记录
        SmsRecord record = new SmsRecord();
        record.setPhone(phone);
        record.setPhoneHash(sha256(phone));
        record.setSmsType("LOGIN_VERIFY");
        record.setTemplateCode(template.getTemplateCode());
        record.setContent(content);
        record.setChannelCode("MOCK");
        record.setStatus("SUCCESS");
        record.setRetryCount(0);
        record.setSendTime(LocalDateTime.now());
        record.setCreatedAt(LocalDateTime.now());
        smsRecordMapper.insert(record);

        // Redis：验证码 5 分钟有效 + 60s 间隔 + 日上限
        stringRedisTemplate.opsForValue().set(CODE_KEY_PREFIX + phone, code, Duration.ofMinutes(5));
        stringRedisTemplate.opsForValue().set(intervalKey, "1", Duration.ofSeconds(60));
        stringRedisTemplate.opsForValue().set(dailyKey, String.valueOf(dailyCount + 1), Duration.ofDays(1));
        log.info("验证码已发送（模拟通道） phone={} code={}", phone, code);
    }

    /**
     * 校验验证码（成功删除，一次性）。
     *
     * @param phone 手机号
     * @param code  验证码
     * @return true 校验通过
     */
    public boolean verifyCode(String phone, String code) {
        if (!StringUtils.hasText(phone) || !StringUtils.hasText(code)) {
            return false;
        }
        String saved = stringRedisTemplate.opsForValue().get(CODE_KEY_PREFIX + phone);
        if (saved == null) {
            throw new BusinessException(ResultCode.CAPTCHA_ERROR, "验证码已过期，请重新获取");
        }
        if (!saved.equals(code)) {
            throw new BusinessException(ResultCode.CAPTCHA_ERROR, "验证码错误");
        }
        // 一次性：删除
        stringRedisTemplate.delete(CODE_KEY_PREFIX + phone);
        return true;
    }

    /** 手机号 SHA-256 哈希。 */
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
}
