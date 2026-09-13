package com.loan.exception;

import com.loan.common.Result;
import com.loan.common.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.ThreadContext;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.validation.ConstraintViolationException;

/**
 * 全局异常处理器（统一转换为 {@link Result} 响应）。
 *
 * @author loan-platform
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常。
     *
     * @param e 业务异常
     * @return 失败结果
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return fail(e.getCode(), e.getMessage());
    }

    /**
     * 参数校验异常（@Valid 对象）。
     *
     * @param e 校验异常
     * @return 失败结果
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValid(MethodArgumentNotValidException e) {
        String msg = firstFieldError(e.getBindingResult().getFieldErrors());
        return fail(ResultCode.PARAM_ERROR.getCode(), msg);
    }

    /**
     * 参数绑定异常（表单绑定）。
     *
     * @param e 绑定异常
     * @return 失败结果
     */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBind(BindException e) {
        String msg = firstFieldError(e.getFieldErrors());
        return fail(ResultCode.PARAM_ERROR.getCode(), msg);
    }

    /**
     * 约束校验异常（方法参数 @Validated）。
     *
     * @param e 约束异常
     * @return 失败结果
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraint(ConstraintViolationException e) {
        return fail(ResultCode.PARAM_ERROR.getCode(), e.getMessage());
    }

    /**
     * 兜底异常。
     *
     * @param e 未知异常
     * @return 失败结果
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return fail(ResultCode.INTERNAL_ERROR.getCode(), ResultCode.INTERNAL_ERROR.getMessage());
    }

    /** 所有异常响应统一携带当前请求链路 ID，便于客户端直接关联 access/error 日志。 */
    private Result<Void> fail(int code, String message) {
        return Result.build(code, message, null, traceId());
    }

    private String traceId() {
        String trace = ThreadContext.get("traceId");
        return trace == null || trace.trim().isEmpty() ? null : trace;
    }

    /**
     * 取首个字段校验错误提示。
     *
     * @param fieldErrors 字段错误列表
     * @return 错误提示（字段名 + 默认消息）
     */
    private String firstFieldError(java.util.List<FieldError> fieldErrors) {
        if (fieldErrors == null || fieldErrors.isEmpty()) {
            return ResultCode.PARAM_ERROR.getMessage();
        }
        FieldError error = fieldErrors.get(0);
        return error.getField() + " " + error.getDefaultMessage();
    }
}
