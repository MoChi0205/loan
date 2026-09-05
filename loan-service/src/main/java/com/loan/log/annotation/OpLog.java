package com.loan.log.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解（标注在 Controller 写操作方法上，切面自动落库 t_operation_log）。
 *
 * @author loan-platform
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OpLog {

    /** 业务类型（产品/规则/计划/配置变更/渠道操作/线索等） */
    String bizType();

    /** 操作动作（CREATE/UPDATE/DELETE/APPROVE/ENABLE 等） */
    String action();
}
