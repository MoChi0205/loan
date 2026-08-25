package com.loan.common;

import lombok.Getter;

/**
 * 统一错误码（分段约定，与 CRM 1020/1021 语义对齐）。
 *
 * <p>1xxx 通用 · 2xxx 认证 · 3xxx 匹配 · 4xxx 产品规则配置 · 5xxx 内部。
 *
 * @author loan-platform
 */
@Getter
public enum ResultCode {

    /** 成功 */
    SUCCESS(0, "成功"),

    /** 通用错误 */
    COMMON_ERROR(1000, "系统繁忙，请稍后重试"),
    /** 参数校验失败 */
    PARAM_ERROR(1001, "参数校验失败"),
    /** 数据不存在 */
    DATA_NOT_FOUND(1002, "数据不存在"),
    /** 操作过于频繁 */
    TOO_FREQUENT(1003, "操作过于频繁，请稍后重试"),

    /** 未登录 / 会话过期 */
    UNAUTHORIZED(2000, "未登录或会话已过期"),
    /** 无权限 */
    FORBIDDEN(2001, "无权限访问"),
    /** 账号被锁定 */
    ACCOUNT_LOCKED(2002, "账号已锁定，请稍后重试"),
    /** 验证码错误 */
    CAPTCHA_ERROR(2003, "验证码错误"),
    /** 登录连续失败锁定 */
    LOGIN_LOCKED(2004, "连续登录失败，账号锁定 30 分钟"),

    /** 匹配执行失败 */
    MATCH_EXECUTE_ERROR(3000, "匹配执行失败"),
    /** 客群串访 */
    SEGMENT_MISMATCH(3001, "客群不匹配"),
    /** 匹配结果不存在 */
    MATCH_NOT_FOUND(3002, "匹配结果不存在"),

    /** 规则/产品配置错误 */
    RULE_CONFIG_ERROR(4000, "规则配置错误"),
    /** 产品未上线 */
    PRODUCT_NOT_ONLINE(4001, "产品未上线"),
    /** 产品已到期 */
    PRODUCT_EXPIRED(4002, "产品已到期"),

    /** 内部错误 */
    INTERNAL_ERROR(5000, "系统内部错误"),
    /** 第三方服务调用失败 */
    THIRD_PARTY_ERROR(5001, "第三方服务调用失败");

    /** 错误码 */
    private final int code;

    /** 默认提示 */
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
