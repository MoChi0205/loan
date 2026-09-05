package com.loan.api.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应体（Dubbo 跨系统契约，对齐 loan 内部 {@code com.loan.common.Result} 约定）。
 *
 * <p>前端 / 其他系统只看 {@code code}：code=0 成功，非 0 失败；错误码分段见后端 ResultCode。
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
     * 成功（带数据）。
     *
     * @param data 业务数据
     * @param <T>  数据泛型
     * @return 成功结果
     */
    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.setCode(0);
        r.setMessage("成功");
        r.setData(data);
        return r;
    }

    /**
     * 失败。
     *
     * @param code    错误码
     * @param message 提示信息
     * @param <T>     数据泛型
     * @return 失败结果
     */
    public static <T> Result<T> fail(int code, String message) {
        Result<T> r = new Result<>();
        r.setCode(code);
        r.setMessage(message);
        return r;
    }

    /**
     * 是否成功。
     *
     * @return true 成功
     */
    public boolean isSuccess() {
        return this.code == 0;
    }
}
