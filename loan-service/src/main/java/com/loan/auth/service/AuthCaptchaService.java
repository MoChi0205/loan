package com.loan.auth.service;

import com.loan.common.ResultCode;
import com.loan.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * 登录随机验证码：4 位数字 + 干扰线与噪点，服务端保存答案，前端只拿到一次性 ID 与图片。
 *
 * <p>参照 tse {@code CaptchaServiceImpl} 的做法：验证码图片在服务端渲染为 Base64 PNG，
 * 答案只留在服务端（Redis），一次性消费、5 分钟有效，避免答案明文出网。
 *
 * <p>与旧版「算术题 + 文本 challenge」的差异：不再把答案随接口返回，前端改为展示图片并点击刷新。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthCaptchaService {

    private static final String KEY_PREFIX = "loan:auth:captcha:";
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final int CODE_LENGTH = 4;
    private static final int WIDTH = 116;
    private static final int HEIGHT = 40;

    private final Random random = new Random();
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 生成一张新的验证码图片。
     *
     * @return {@code captchaId} 一次性 ID、{@code imageBase64} 图片、{@code expireSeconds} 有效期
     */
    public Map<String, String> create() {
        String code = randomCode();
        String captchaId = UUID.randomUUID().toString().replace("-", "");
        stringRedisTemplate.opsForValue().set(KEY_PREFIX + captchaId, code, TTL);
        Map<String, String> result = new HashMap<>(4);
        result.put("captchaId", captchaId);
        result.put("imageBase64", renderBase64Png(code));
        result.put("expireSeconds", String.valueOf(TTL.getSeconds()));
        return result;
    }

    /** 验证码无论成功失败均立即失效，防止重放和枚举。 */
    public void verify(String captchaId, String captchaCode) {
        if (!StringUtils.hasText(captchaId) || !StringUtils.hasText(captchaCode)) {
            throw new BusinessException(ResultCode.CAPTCHA_ERROR, "请输入随机验证码");
        }
        String key = KEY_PREFIX + captchaId;
        String expected = stringRedisTemplate.opsForValue().get(key);
        stringRedisTemplate.delete(key);
        if (expected == null || !expected.equals(captchaCode.trim())) {
            throw new BusinessException(ResultCode.CAPTCHA_ERROR, "随机验证码错误或已过期");
        }
    }

    /** 固定 4 位数字（1000–9999），避免前导 0 带来的输入歧义。 */
    private String randomCode() {
        int bound = (int) Math.pow(10, CODE_LENGTH);
        int min = bound / 10;
        return String.valueOf(random.nextInt(bound - min) + min);
    }

    private String renderBase64Png(String code) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(245, 240, 230));
            g.fillRect(0, 0, WIDTH, HEIGHT);
            g.setStroke(new BasicStroke(1.2f));

            for (int i = 0; i < 6; i++) {
                g.setColor(randomColor(120, 190));
                g.drawLine(random.nextInt(WIDTH), random.nextInt(HEIGHT),
                        random.nextInt(WIDTH), random.nextInt(HEIGHT));
            }
            for (int i = 0; i < 45; i++) {
                g.setColor(randomColor(130, 210));
                g.fillRect(random.nextInt(WIDTH), random.nextInt(HEIGHT), 2, 2);
            }

            int charWidth = WIDTH / (CODE_LENGTH + 1);
            for (int i = 0; i < code.length(); i++) {
                g.setColor(randomColor(20, 110));
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24 + random.nextInt(4)));
                double angle = (random.nextDouble() - 0.5) * 0.5;
                int x = charWidth * (i + 1) - 10;
                int y = 29 + random.nextInt(5);
                g.rotate(angle, x, y);
                g.drawString(String.valueOf(code.charAt(i)), x, y);
                g.rotate(-angle, x, y);
            }
        } finally {
            g.dispose();
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            log.error("[Auth] 渲染登录验证码失败", e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "验证码生成失败，请重试");
        }
    }

    private Color randomColor(int min, int max) {
        int r = min + random.nextInt(max - min);
        int g = min + random.nextInt(max - min);
        int b = min + random.nextInt(max - min);
        return new Color(r, g, b);
    }
}
