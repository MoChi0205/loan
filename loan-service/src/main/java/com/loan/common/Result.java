package com.loan.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应体（对齐 tse Result）。
 *
 * <p>前端只看 {@code code}：code=0 成功，非 0 失败；错误码分段见 {@link ResultCode}。
 *
 * @param <T> 数据泛型
 * @author loan-platform
 */
@Data
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 业务码：0 成功，1xxx 通用 / 2xxx 认证 / 3xxx 匹配 / 4xxx 配置 / 5xxx 内部 */
    private int code;

    /** 提示信息 */
    private String message;

    /** 业务数据 */
    private T data;

    /** 链路追踪 ID（响应头 X-Trace-Id + body 双通道） */
    private String traceUuid;

    /**
     * 成功（无数据）。
     *
     * @param <T> 数据泛型
     * @return 成功结果
     */
    public static <T> Result<T> ok() {
        return build(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null, null);
    }

    /**
     * 成功（带数据）。
     *
     * @param data 业务数据
     * @param <T>  数据泛型
     * @return 成功结果
     */
    public static <T> Result<T> ok(T data) {
        return build(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data, null);
    }

    /**
     * 失败（指定错误码）。
     *
     * @param resultCode 错误码枚举
     * @param <T>        数据泛型
     * @return 失败结果
     */
    public static <T> Result<T> fail(ResultCode resultCode) {
        return build(resultCode.getCode(), resultCode.getMessage(), null, null);
    }

    /**
     * 失败（自定义提示）。
     *
     * @param code    错误码
     * @param message 提示信息
     * @param <T>     数据泛型
     * @return 失败结果
     */
    public static <T> Result<T> fail(int code, String message) {
        return build(code, message, null, null);
    }

    /**
     * 构建结果。
     *
     * @param code      业务码
     * @param message   提示信息
     * @param data      业务数据
     * @param traceUuid 链路追踪 ID
     * @param <T>       数据泛型
     * @return 结果对象
     */
    public static <T> Result<T> build(int code, String message, T data, String traceUuid) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        result.setData(data);
        result.setTraceUuid(traceUuid);
        return result;
    }

    /**
     * 是否成功。
     *
     * @return true 成功，false 失败
     */
    public boolean isSuccess() {
        return this.code == ResultCode.SUCCESS.getCode();
    }
}
