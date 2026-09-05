package com.loan.infrastructure.security;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * 登录 RSA 加解密（对齐 tse LoginRsaCrypto）。
 *
 * <p>登录页拉取公钥加密密码后提交，服务端用私钥解密，避免密码明文出现在请求体。
 * 阶段一员工走 SSO 模拟登录（无密码），RSA 预留供渠道端/本地密码登录场景使用。
 * 密钥对启动时随机生成（后续接入 Nacos 固定密钥对可扩展）。
 *
 * @author loan-platform
 */
@Slf4j
@Component
public class LoginRsaCrypto {

    /** 私钥 */
    private PrivateKey privateKey;

    /** 公钥 Base64（下发前端） */
    @Getter
    private String publicKeyBase64;

    /**
     * 初始化：启动时生成 2048 位 RSA 密钥对。
     */
    @PostConstruct
    public void init() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            this.privateKey = keyPair.getPrivate();
            this.publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
            log.info("登录 RSA 密钥对已生成（启动时随机）");
        } catch (Exception e) {
            throw new IllegalStateException("初始化登录 RSA 密钥失败", e);
        }
    }

    /**
     * 解密 Base64 密文（前端用公钥加密后提交）。
     *
     * @param cipherBase64 Base64 密文
     * @return 明文，失败返回 null
     */
    public String decryptBase64(String cipherBase64) {
        if (cipherBase64 == null || cipherBase64.isEmpty()) {
            return null;
        }
        try {
            byte[] encrypted = Base64.getDecoder().decode(cipherBase64);
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, privateKey);
            byte[] plain = cipher.doFinal(encrypted);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("登录 RSA 解密失败: {}", e.getMessage());
            return null;
        }
    }
}
