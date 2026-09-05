package com.loan.context;

/**
 * 当前请求线程的用户上下文（ThreadLocal 持有 {@link LoanUser} 对象）。
 *
 * <p>参考 tse {@code EnterpriseContext} 升级为持有完整对象；认证过滤器解析 JWT → Redis 取 User → set → 请求结束 clear。
 *
 * @author loan-platform
 */
public final class UserContext {

    private static final ThreadLocal<LoanUser> CURRENT_USER = new ThreadLocal<>();

    private UserContext() {
    }

    /**
     * 设置当前用户。
     *
     * @param user 当前登录用户
     */
    public static void setUser(LoanUser user) {
        CURRENT_USER.set(user);
    }

    /**
     * 获取当前用户（未登录返回 null）。
     *
     * @return 当前登录用户，未登录为 null
     */
    public static LoanUser getUser() {
        return CURRENT_USER.get();
    }

    /**
     * 获取当前用户 ID（未登录返回 null）。
     *
     * @return 用户 ID
     */
    public static Long getUserId() {
        LoanUser user = CURRENT_USER.get();
        return user == null ? null : user.getUserId();
    }

    /**
     * 获取当前用户编号（未登录返回 null）。
     *
     * @return 用户编号
     */
    public static String getUserNo() {
        LoanUser user = CURRENT_USER.get();
        return user == null ? null : user.getUserNo();
    }

    /**
     * 清除上下文（请求结束后必须调用，防止线程复用串数据）。
     */
    public static void clear() {
        CURRENT_USER.remove();
    }
}
