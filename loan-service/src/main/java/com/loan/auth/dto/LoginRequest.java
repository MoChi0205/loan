package com.loan.auth.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 登录请求。
 *
 * <p>阶段一员工走 SSO 模拟登录：crmUserId 为 SSO 返回的员工身份（正式环境由 CRM SSO 回调产生）。
 * 渠道端本地密码登录走 password（RSA 加密）+ 验证码，后续迭代接入。
 *
 * @author loan-platform
 */
@Data
public class LoginRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** CRM 员工 ID（SSO 映射键，员工登录用） */
    private String crmUserId;

    /** 账号（渠道端登录用，预留） */
    private String account;

    /** 密码（RSA 加密后 Base64，渠道端登录用，预留） */
    private String password;

    /** 图形验证码（预留） */
    private String captcha;

    /** 验证码会话 key（预留） */
    private String captchaKey;
}
