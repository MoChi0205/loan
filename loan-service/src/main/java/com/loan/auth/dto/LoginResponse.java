package com.loan.auth.dto;

import com.loan.context.LoanUser;
import lombok.Data;

import java.io.Serializable;

/**
 * 登录响应。
 *
 * @author loan-platform
 */
@Data
public class LoginResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** JWT 访问令牌 */
    private String token;

    /** 令牌过期时间（毫秒） */
    private long expireMillis;

    /** 登录用户信息 */
    private LoanUser user;
}
