package com.loan.exception;

import com.loan.common.ResultCode;
import lombok.Getter;

/**
 * 业务异常。
 *
 * <p>业务层主动抛出，由 {@link GlobalExceptionHandler} 统一捕获转换为 {@code Result} 响应。
 *
 * @author loan-platform
 */
@Getter
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 错误码 */
    private final int code;

    /**
     * 构造业务异常（默认提示）。
     *
     * @param resultCode 错误码枚举
     */
    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    /**
     * 构造业务异常（自定义提示）。
     *
     * @param resultCode 错误码枚举
     * @param message    自定义提示信息
     */
    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    /**
     * 构造业务异常（自定义错误码与提示）。
     *
     * @param code    错误码
     * @param message 提示信息
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
