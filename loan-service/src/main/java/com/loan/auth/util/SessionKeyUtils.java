package com.loan.auth.util;

import org.springframework.util.StringUtils;

/** Redis 登录会话键工具，按用户类型隔离不同账号表中的同值主键。 */
public final class SessionKeyUtils {

    private static final String PREFIX = "loan:session:";

    private SessionKeyUtils() {
    }

    /**
     * 构造类型隔离的会话键，例如 {@code loan:session:STAFF:1}。
     *
     * @param userType STAFF / CHANNEL / CUSTOMER
     * @param userId 用户表主键
     * @return Redis key
     */
    public static String key(String userType, Long userId) {
        if (!StringUtils.hasText(userType) || userId == null) {
            throw new IllegalArgumentException("用户类型和用户ID不能为空");
        }
        return PREFIX + userType.trim().toUpperCase() + ":" + userId;
    }

    /** 旧版未按用户类型隔离的 key，仅用于登录/登出时清理。 */
    public static String legacyKey(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        return PREFIX + userId;
    }
}
